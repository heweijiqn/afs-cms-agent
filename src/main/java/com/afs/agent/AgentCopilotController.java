package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.PreDestroy;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST & SSE controller for the Multi-Turn Commercial AdOps Copilot powered by
 * {@link AdOpsCopilotGraph} (LangGraph4j + LangChain4j) with persistent SQL chat memory.
 */
@RestController
@RequestMapping("/cms/agent/chat")
class AgentCopilotController {
    private static final Logger log = LoggerFactory.getLogger(AgentCopilotController.class);
    private static final int MEMORY_WINDOW = 16;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AdOpsCopilotGraph copilot;
    private final OptimizationController optimizer;

    private final ExecutorService chatPool = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "copilot-stream-worker");
        thread.setDaemon(true);
        return thread;
    });

    AgentCopilotController(JdbcTemplate jdbc, ObjectMapper json, AdOpsCopilotGraph copilot,
                           OptimizationController optimizer) {
        this.jdbc = jdbc;
        this.json = json;
        this.copilot = copilot;
        this.optimizer = optimizer;
    }

    @PreDestroy
    void shutdown() {
        chatPool.shutdownNow();
    }

    @GetMapping("/sessions")
    List<SessionView> listSessions() {
        return jdbc.query("""
                SELECT id, title, campaign_id, run_id, actor, created_at, updated_at
                FROM agent_chat_session
                ORDER BY updated_at DESC, created_at DESC
                LIMIT 30
                """, (row, index) -> new SessionView(
                row.getString("id"),
                row.getString("title"),
                row.getString("campaign_id"),
                row.getString("run_id"),
                row.getString("actor"),
                row.getObject("created_at", OffsetDateTime.class),
                row.getObject("updated_at", OffsetDateTime.class)));
    }

    @PostMapping(path = "/sessions", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    SessionDetail createSession(@RequestBody(required = false) CreateSessionRequest request, Principal principal) {
        String id = UUID.randomUUID().toString();
        String campaignId = request == null || request.campaignId() == null ? null : request.campaignId().strip();
        String runId = request == null || request.runId() == null ? null : request.runId().strip();
        String title = request != null && request.title() != null && !request.title().isBlank()
                ? request.title().strip()
                : (campaignId != null && !campaignId.isBlank() ? "活动诊断 · " + campaignId : "新投放对话");
        if (title.length() > 180) title = title.substring(0, 177) + "...";
        jdbc.update("""
                INSERT INTO agent_chat_session (id, title, campaign_id, run_id, actor)
                VALUES (?, ?, ?, ?, ?)
                """, id, title, blankToNull(campaignId), blankToNull(runId), actor(principal));
        return getSession(id);
    }

    @GetMapping("/sessions/{id}")
    SessionDetail getSession(@PathVariable String id) {
        SessionView session = findSession(id);
        List<MessageView> messages = loadMessages(id);
        return new SessionDetail(session, messages);
    }

    @DeleteMapping("/sessions/{id}")
    Map<String, Object> deleteSession(@PathVariable String id) {
        findSession(id);
        jdbc.update("DELETE FROM agent_chat_message WHERE session_id = ?", id);
        jdbc.update("DELETE FROM agent_chat_session WHERE id = ?", id);
        return Map.of("deleted", true, "id", id);
    }

    /** Synchronous JSON turn for standard clients and integration tests. */
    @PostMapping(path = "/sessions/{id}/messages", consumes = "application/json")
    Map<String, Object> sendMessage(@PathVariable String id,
                                    @Valid @RequestBody ChatPrompt prompt,
                                    Principal principal) {
        SessionView session = findSession(id);
        String text = prompt.content().strip();
        List<AdOpsCopilotGraph.HistoryEntry> history = recentHistory(id);
        saveMessage(id, "user", text, null);
        maybeUpdateTitle(session, text);

        AdOpsCopilotGraph.CopilotReply reply = copilot.chatTurn(
                id,
                session.campaignId(),
                loadRunSummary(session.runId()),
                history,
                text,
                principal,
                (cid, mode) -> optimizer.launchFromCopilot(cid, mode, principal),
                null);

        Map<String, Object> meta = replyMeta(reply);
        saveMessage(id, "assistant", reply.content(), meta);
        jdbc.update("UPDATE agent_chat_session SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);

        var out = new LinkedHashMap<String, Object>();
        out.put("sessionId", id);
        out.put("content", reply.content());
        out.put("meta", meta);
        return out;
    }

    /** Streaming Server-Sent Events turn emitting live LangGraph tool calls and answer. */
    @PostMapping(path = "/sessions/{id}/stream", consumes = "application/json",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@PathVariable String id,
                                    @Valid @RequestBody ChatPrompt prompt,
                                    Principal principal) {
        SessionView session = findSession(id);
        String text = prompt.content().strip();
        List<AdOpsCopilotGraph.HistoryEntry> history = recentHistory(id);
        saveMessage(id, "user", text, null);
        maybeUpdateTitle(session, text);

        SseEmitter emitter = new SseEmitter(180_000L);
        chatPool.execute(() -> {
            try {
                AdOpsCopilotGraph.CopilotReply reply = copilot.chatTurn(
                        id,
                        session.campaignId(),
                        loadRunSummary(session.runId()),
                        history,
                        text,
                        principal,
                        (cid, mode) -> optimizer.launchFromCopilot(cid, mode, principal),
                        (eventName, payload) -> sendEvent(emitter, eventName, payload));
                Map<String, Object> meta = replyMeta(reply);
                saveMessage(id, "assistant", reply.content(), meta);
                jdbc.update("UPDATE agent_chat_session SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
                sendEvent(emitter, "done", Map.of("sessionId", id, "meta", meta));
                emitter.complete();
            } catch (Exception failure) {
                log.warn("Copilot stream failed session={}: {}", id, failure.getMessage());
                sendEvent(emitter, "error", Map.of("message", failure.getMessage() == null ? "对话流中断" : failure.getMessage()));
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // Ignore already completed emitters.
                }
            }
        });
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String name, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().name(name).data(payload));
        } catch (IOException | IllegalStateException ignored) {
            // Client closed stream.
        }
    }

    private SessionView findSession(String id) {
        return jdbc.query("""
                SELECT id, title, campaign_id, run_id, actor, created_at, updated_at
                FROM agent_chat_session WHERE id = ?
                """, (row, index) -> new SessionView(
                row.getString("id"),
                row.getString("title"),
                row.getString("campaign_id"),
                row.getString("run_id"),
                row.getString("actor"),
                row.getObject("created_at", OffsetDateTime.class),
                row.getObject("updated_at", OffsetDateTime.class)), id)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到对话会话"));
    }

    private List<MessageView> loadMessages(String sessionId) {
        return jdbc.query("""
                SELECT id, role, content, meta_json, created_at
                FROM agent_chat_message
                WHERE session_id = ?
                ORDER BY id ASC
                """, (row, index) -> new MessageView(
                row.getLong("id"),
                row.getString("role"),
                row.getString("content"),
                parseJson(row.getString("meta_json")),
                row.getObject("created_at", OffsetDateTime.class)), sessionId);
    }

    private List<AdOpsCopilotGraph.HistoryEntry> recentHistory(String sessionId) {
        List<AdOpsCopilotGraph.HistoryEntry> all = jdbc.query("""
                SELECT role, content FROM agent_chat_message
                WHERE session_id = ?
                ORDER BY id ASC
                """, (row, index) -> new AdOpsCopilotGraph.HistoryEntry(
                row.getString("role"), row.getString("content")), sessionId);
        if (all.size() <= MEMORY_WINDOW) return all;
        return all.subList(all.size() - MEMORY_WINDOW, all.size());
    }

    private void saveMessage(String sessionId, String role, String content, Map<String, Object> meta) {
        String metaJson = null;
        if (meta != null) {
            try {
                metaJson = json.writeValueAsString(meta);
            } catch (Exception ignored) {
                metaJson = null;
            }
        }
        jdbc.update("""
                INSERT INTO agent_chat_message (session_id, role, content, meta_json)
                VALUES (?, ?, ?, ?)
                """, sessionId, role, content, metaJson);
    }

    private void maybeUpdateTitle(SessionView session, String firstPrompt) {
        if (!"新投放对话".equals(session.title())) return;
        String compact = firstPrompt.replaceAll("\\s+", " ").trim();
        String next = compact.length() <= 32 ? compact : compact.substring(0, 31) + "…";
        jdbc.update("UPDATE agent_chat_session SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                next, session.id());
    }

    private String loadRunSummary(String runId) {
        if (runId == null || runId.isBlank()) return null;
        var rows = jdbc.queryForList("SELECT plan, state, review_note FROM optimization_run WHERE id = ?", runId);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        return "关联分析单 ID=" + runId + ", 状态=" + row.get("state")
                + ", 审批备注=" + row.get("review_note")
                + "\n计划 JSON=" + row.get("plan");
    }

    private Map<String, Object> replyMeta(AdOpsCopilotGraph.CopilotReply reply) {
        var meta = new LinkedHashMap<String, Object>();
        meta.put("model", reply.model());
        meta.put("source", reply.source());
        meta.put("inputTokens", reply.inputTokens());
        meta.put("outputTokens", reply.outputTokens());
        meta.put("totalTokens", reply.inputTokens() + reply.outputTokens());
        meta.put("toolCalls", reply.toolCalls());
        meta.put("tools", reply.tools());
        meta.put("graphNodes", reply.graphNodes());
        meta.put("durationMs", reply.durationMs());
        meta.put("engine", OptimizationWorkflow.ENGINE);
        return meta;
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return json.readTree(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String actor(Principal principal) {
        return principal == null ? "local-operator" : principal.getName();
    }

    record CreateSessionRequest(@Size(max = 180) String title,
                                @Size(max = 128) String campaignId,
                                @Size(max = 36) String runId) {}
    record ChatPrompt(@NotBlank @Size(max = 4000) String content) {}
    record SessionView(String id, String title, String campaignId, String runId, String actor,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    record MessageView(long id, String role, String content, JsonNode meta, OffsetDateTime createdAt) {}
    record SessionDetail(SessionView session, List<MessageView> messages) {}
}
