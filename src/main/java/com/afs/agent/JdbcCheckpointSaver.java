package com.afs.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * JDBC-backed LangGraph4j {@link BaseCheckpointSaver} storing graph checkpoints in H2/MySQL
 * ({@code agent_checkpoint}) for durable execution history and Human-in-the-Loop traceability.
 */
@Component
class JdbcCheckpointSaver implements BaseCheckpointSaver {
    private static final Logger log = LoggerFactory.getLogger(JdbcCheckpointSaver.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcCheckpointSaver(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        String threadId = threadId(config);
        return jdbc.query("""
                SELECT checkpoint_id, node_id, next_node_id, state_json
                FROM agent_checkpoint
                WHERE thread_id = ?
                ORDER BY created_at DESC, checkpoint_id DESC
                """, (row, index) -> Checkpoint.builder()
                .id(row.getString("checkpoint_id"))
                .nodeId(row.getString("node_id"))
                .nextNodeId(row.getString("next_node_id"))
                .state(deserialize(row.getString("state_json")))
                .build(), threadId);
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        String threadId = threadId(config);
        Optional<String> checkpointId = config.checkPointId();
        List<Checkpoint> rows;
        if (checkpointId.isPresent()) {
            rows = jdbc.query("""
                    SELECT checkpoint_id, node_id, next_node_id, state_json
                    FROM agent_checkpoint
                    WHERE thread_id = ? AND checkpoint_id = ?
                    """, (row, index) -> Checkpoint.builder()
                    .id(row.getString("checkpoint_id"))
                    .nodeId(row.getString("node_id"))
                    .nextNodeId(row.getString("next_node_id"))
                    .state(deserialize(row.getString("state_json")))
                    .build(), threadId, checkpointId.get());
        } else {
            rows = jdbc.query("""
                    SELECT checkpoint_id, node_id, next_node_id, state_json
                    FROM agent_checkpoint
                    WHERE thread_id = ?
                    ORDER BY created_at DESC, checkpoint_id DESC
                    LIMIT 1
                    """, (row, index) -> Checkpoint.builder()
                    .id(row.getString("checkpoint_id"))
                    .nodeId(row.getString("node_id"))
                    .nextNodeId(row.getString("next_node_id"))
                    .state(deserialize(row.getString("state_json")))
                    .build(), threadId);
        }
        return rows.stream().findFirst();
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        String threadId = threadId(config);
        String id = checkpoint.getId() == null || checkpoint.getId().isBlank()
                ? UUID.randomUUID().toString() : checkpoint.getId();
        String stateJson = serialize(checkpoint.getState());
        jdbc.update("""
                MERGE INTO agent_checkpoint (thread_id, checkpoint_id, node_id, next_node_id, state_json, created_at)
                KEY (thread_id, checkpoint_id)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, threadId, id, safeNode(checkpoint.getNodeId()), checkpoint.getNextNodeId(), stateJson);
        return config.withCheckPointId(id);
    }

    @Override
    public Tag release(RunnableConfig config) {
        String threadId = threadId(config);
        Collection<Checkpoint> existing = list(config);
        jdbc.update("DELETE FROM agent_checkpoint WHERE thread_id = ?", threadId);
        return new Tag(threadId, existing);
    }

    List<CheckpointView> inspectThread(String threadId) {
        return jdbc.query("""
                SELECT checkpoint_id, node_id, next_node_id, created_at
                FROM agent_checkpoint
                WHERE thread_id = ?
                ORDER BY created_at ASC, checkpoint_id ASC
                """, (row, index) -> new CheckpointView(
                row.getString("checkpoint_id"),
                row.getString("node_id"),
                row.getString("next_node_id"),
                row.getObject("created_at", OffsetDateTime.class)), threadId);
    }

    private String threadId(RunnableConfig config) {
        return config.threadId().orElse(THREAD_ID_DEFAULT);
    }

    private static String safeNode(String nodeId) {
        return nodeId == null || nodeId.isBlank() ? "__START__" : nodeId;
    }

    private String serialize(Map<String, Object> state) {
        if (state == null) return "{}";
        try {
            return json.writeValueAsString(state);
        } catch (Exception ex) {
            // Fall back to stringified map entries if any transient value is not JSON serializable.
            var safe = new LinkedHashMap<String, Object>();
            state.forEach((k, v) -> safe.put(k, v == null ? null : String.valueOf(v)));
            try {
                return json.writeValueAsString(safe);
            } catch (Exception nested) {
                log.warn("Failed to serialize LangGraph checkpoint state", nested);
                return "{}";
            }
        }
    }

    private Map<String, Object> deserialize(String raw) {
        if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
        try {
            return json.readValue(raw, MAP_TYPE);
        } catch (Exception ex) {
            log.warn("Failed to deserialize LangGraph checkpoint state", ex);
            return new LinkedHashMap<>();
        }
    }

    record CheckpointView(String checkpointId, String nodeId, String nextNodeId, OffsetDateTime createdAt) {}
}
