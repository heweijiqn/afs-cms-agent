package com.afs.agent;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Commercial observability & live SSE progress bus for LangGraph4j runs and Copilot sessions.
 */
@Service
class AgentTelemetryService {
    private static final Logger log = LoggerFactory.getLogger(AgentTelemetryService.class);

    private final JdbcTemplate jdbc;
    private final Map<String, List<SseEmitter>> runStreams = new ConcurrentHashMap<>();

    AgentTelemetryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void recordNode(String runId, String sessionId, String graphName, String nodeName, String status,
                    String modelName, int inputTokens, int outputTokens, int toolCalls,
                    long durationMs, String summary) {
        int total = inputTokens + outputTokens;
        try {
            jdbc.update("""
                    INSERT INTO agent_execution_trace
                        (run_id, session_id, graph_name, node_name, status, model_name,
                         input_tokens, output_tokens, total_tokens, tool_calls, duration_ms, summary)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    runId, sessionId, graphName, nodeName, status, modelName,
                    inputTokens, outputTokens, total, toolCalls, durationMs, abbreviate(summary, 2000));
        } catch (Exception ex) {
            log.warn("Failed to record agent telemetry node={}: {}", nodeName, ex.getMessage());
        }
        if (runId != null && !runId.isBlank()) {
            var event = new LinkedHashMap<String, Object>();
            event.put("type", "node");
            event.put("runId", runId);
            event.put("graphName", graphName);
            event.put("node", nodeName);
            event.put("status", status);
            event.put("model", modelName == null ? "" : modelName);
            event.put("inputTokens", inputTokens);
            event.put("outputTokens", outputTokens);
            event.put("totalTokens", total);
            event.put("toolCalls", toolCalls);
            event.put("durationMs", durationMs);
            event.put("summary", summary == null ? "" : summary);
            publish(runId, "node", event);
        }
    }

    SseEmitter subscribeRun(UUID runId) {
        String key = runId.toString();
        SseEmitter emitter = new SseEmitter(180_000L);
        runStreams.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> {
            List<SseEmitter> list = runStreams.get(key);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) runStreams.remove(key, list);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        // Replay existing nodes already recorded for this run so late subscribers catch up immediately.
        try {
            for (TraceRow existing : tracesForRun(key)) {
                var event = new LinkedHashMap<String, Object>();
                event.put("type", "node");
                event.put("runId", key);
                event.put("graphName", existing.graphName());
                event.put("node", existing.nodeName());
                event.put("status", existing.status());
                event.put("model", existing.modelName() == null ? "" : existing.modelName());
                event.put("inputTokens", existing.inputTokens());
                event.put("outputTokens", existing.outputTokens());
                event.put("totalTokens", existing.totalTokens());
                event.put("toolCalls", existing.toolCalls());
                event.put("durationMs", existing.durationMs());
                event.put("summary", existing.summary() == null ? "" : existing.summary());
                emitter.send(SseEmitter.event().name("node").data(event));
            }
        } catch (IOException ex) {
            remove.run();
        }
        return emitter;
    }

    void completeRunStream(UUID runId, String source, String status) {
        String key = runId.toString();
        var payload = Map.of("type", "done", "runId", key, "source", source == null ? "" : source,
                "status", status == null ? "DONE" : status);
        publish(key, "done", payload);
        List<SseEmitter> emitters = runStreams.remove(key);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // Client disconnected.
                }
            }
        }
    }

    private void publish(String runId, String eventName, Object payload) {
        List<SseEmitter> emitters = runStreams.get(runId);
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception ex) {
                emitters.remove(emitter);
            }
        }
    }

    List<TraceRow> tracesForRun(String runId) {
        return jdbc.query("""
                SELECT id, run_id, session_id, graph_name, node_name, status, model_name,
                       input_tokens, output_tokens, total_tokens, tool_calls, duration_ms, summary, created_at
                FROM agent_execution_trace
                WHERE run_id = ?
                ORDER BY id ASC
                """, (row, index) -> new TraceRow(
                row.getLong("id"),
                row.getString("run_id"),
                row.getString("session_id"),
                row.getString("graph_name"),
                row.getString("node_name"),
                row.getString("status"),
                row.getString("model_name"),
                row.getInt("input_tokens"),
                row.getInt("output_tokens"),
                row.getInt("total_tokens"),
                row.getInt("tool_calls"),
                row.getLong("duration_ms"),
                row.getString("summary"),
                row.getObject("created_at", OffsetDateTime.class)), runId);
    }

    Map<String, Object> summary() {
        var result = new LinkedHashMap<String, Object>();
        var totals = jdbc.queryForMap("""
                SELECT COUNT(*) AS "nodeExecutions",
                       COUNT(DISTINCT run_id) AS "tracedRuns",
                       COUNT(DISTINCT session_id) AS "tracedSessions",
                       COALESCE(SUM(input_tokens), 0) AS "inputTokens",
                       COALESCE(SUM(output_tokens), 0) AS "outputTokens",
                       COALESCE(SUM(total_tokens), 0) AS "totalTokens",
                       COALESCE(SUM(tool_calls), 0) AS "totalToolCalls",
                       COALESCE(AVG(duration_ms), 0) AS "avgNodeDurationMs"
                FROM agent_execution_trace
                """);
        var runStats = jdbc.queryForMap("""
                SELECT COUNT(*) AS "totalRuns",
                       COALESCE(SUM(CASE WHEN state = 'PENDING' THEN 1 ELSE 0 END), 0) AS "pendingRuns",
                       COALESCE(SUM(CASE WHEN state = 'APPROVED' THEN 1 ELSE 0 END), 0) AS "approvedRuns",
                       COALESCE(SUM(CASE WHEN state = 'SIMULATED' THEN 1 ELSE 0 END), 0) AS "simulatedRuns",
                       COALESCE(SUM(CASE WHEN plan LIKE '%LANGCHAIN4J_%' THEN 1 ELSE 0 END), 0) AS "aiPoweredRuns"
                FROM optimization_run
                """);
        var byNode = jdbc.queryForList("""
                SELECT node_name AS "nodeName",
                       graph_name AS "graphName",
                       COUNT(*) AS "executions",
                       COALESCE(AVG(duration_ms), 0) AS "avgDurationMs",
                       COALESCE(SUM(total_tokens), 0) AS "totalTokens",
                       COALESCE(SUM(tool_calls), 0) AS "toolCalls"
                FROM agent_execution_trace
                GROUP BY graph_name, node_name
                ORDER BY COUNT(*) DESC
                """);
        var recent = jdbc.query("""
                SELECT id, run_id, session_id, graph_name, node_name, status, model_name,
                       input_tokens, output_tokens, total_tokens, tool_calls, duration_ms, summary, created_at
                FROM agent_execution_trace
                ORDER BY id DESC
                LIMIT 30
                """, (row, index) -> new TraceRow(
                row.getLong("id"),
                row.getString("run_id"),
                row.getString("session_id"),
                row.getString("graph_name"),
                row.getString("node_name"),
                row.getString("status"),
                row.getString("model_name"),
                row.getInt("input_tokens"),
                row.getInt("output_tokens"),
                row.getInt("total_tokens"),
                row.getInt("tool_calls"),
                row.getLong("duration_ms"),
                row.getString("summary"),
                row.getObject("created_at", OffsetDateTime.class)));
        result.put("totals", totals);
        result.put("runs", runStats);
        result.put("byNode", byNode);
        result.put("recentTraces", recent);
        return result;
    }

    private static String abbreviate(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
    }

    record TraceRow(long id, String runId, String sessionId, String graphName, String nodeName,
                    String status, String modelName, int inputTokens, int outputTokens,
                    int totalTokens, int toolCalls, long durationMs, String summary,
                    OffsetDateTime createdAt) {}
}
