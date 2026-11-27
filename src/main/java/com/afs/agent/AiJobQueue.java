package com.afs.agent;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Durable async job queue used by the AI platform (index rebuilds, eval runs, cache sweeps).
 *
 * <p>Why a table instead of {@code @Async}: index rebuilds take minutes on a 2.7k-row corpus and
 * must survive a restart. The queue gives us at-least-once delivery, exponential backoff, a dead
 * letter state and idempotency keys, which is what makes retries safe.
 */
@Profile("local-cms")
class AiJobQueue {
    private static final Logger log = LoggerFactory.getLogger(AiJobQueue.class);
    private static final int MAX_ATTEMPTS = 3;

    private final JdbcTemplate db;
    private final RagService rag;
    private final AiEvalService eval;
    private final SemanticCache cache;

    AiJobQueue(JdbcTemplate db, RagService rag, AiEvalService eval, SemanticCache cache) {
        this.db = db;
        this.rag = rag;
        this.eval = eval;
        this.cache = cache;
    }

    Map<String, Object> enqueue(String type, Map<String, Object> payload, String idempotencyKey) {
        String body = payload == null || payload.isEmpty() ? "{}" : toJson(payload);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Integer exists = db.queryForObject("SELECT COUNT(*) FROM ai_job WHERE idempotency_key = ?",
                    Integer.class, idempotencyKey);
            if (exists != null && exists > 0) {
                Long id = db.queryForObject("SELECT id FROM ai_job WHERE idempotency_key = ?", Long.class,
                        idempotencyKey);
                return Map.of("id", id, "deduplicated", true);
            }
        }
        db.update("""
                INSERT INTO ai_job (job_type, payload, idempotency_key, status, max_attempts, run_after)
                VALUES (?, ?, ?, 'QUEUED', ?, ?)
                """, type, body, idempotencyKey, MAX_ATTEMPTS, Timestamp.from(Instant.now()));
        Long id = db.queryForObject("SELECT MAX(id) FROM ai_job WHERE job_type = ?", Long.class, type);
        return Map.of("id", id, "deduplicated", false);
    }

    @Scheduled(fixedDelay = 3_000L, initialDelay = 25_000L)
    void poll() {
        try {
            runOne();
        } catch (Exception failure) {
            log.warn("AI job poll failed: {}", failure.getMessage());
        }
    }

    /** Claims and executes a single queued job; returns the executed type or null when idle. */
    String runOne() {
        String worker = "ai-worker-" + UUID.randomUUID().toString().substring(0, 8);
        int claimed = db.update("""
                UPDATE ai_job SET status = 'RUNNING', attempts = attempts + 1, locked_by = ?, utime = ?
                WHERE id = (SELECT id FROM ai_job WHERE status = 'QUEUED' AND run_after <= ?
                            ORDER BY id LIMIT 1)
                """, worker, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        if (claimed == 0) return null;
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT id, job_type, payload, attempts, max_attempts FROM ai_job
                WHERE locked_by = ? AND status = 'RUNNING' ORDER BY id LIMIT 1
                """, worker);
        if (rows.isEmpty()) return null;
        Map<String, Object> job = rows.get(0);
        long id = ((Number) job.get("id")).longValue();
        String type = String.valueOf(job.get("job_type"));
        int attempts = ((Number) job.get("attempts")).intValue();
        String payload = String.valueOf(job.get("payload"));
        try {
            execute(type, payload);
            db.update("""
                    UPDATE ai_job SET status = 'SUCCEEDED', locked_by = NULL, last_error = NULL, utime = ?
                    WHERE id = ?
                    """, Timestamp.from(Instant.now()), id);
            log.info("AI job #{} ({}) succeeded", id, type);
            return type;
        } catch (Exception failure) {
            boolean dead = attempts >= MAX_ATTEMPTS;
            Timestamp next = Timestamp.from(Instant.now().plus(5L * (1L << Math.min(attempts, 5)), ChronoUnit.SECONDS));
            db.update("""
                    UPDATE ai_job SET status = ?, locked_by = NULL, last_error = ?, run_after = ?, utime = ?
                    WHERE id = ?
                    """, dead ? "DEAD" : "QUEUED", abbreviate(failure.getMessage()), next,
                    Timestamp.from(Instant.now()), id);
            log.warn("AI job #{} ({}) failed on attempt {}: {}", id, type, attempts, failure.getMessage());
            return type;
        }
    }

    private void execute(String type, String payload) {
        switch (type) {
            case "rag.rebuild" -> rag.rebuildIndex();
            case "cache.purge" -> cache.purgeExpired();
            case "eval.run" -> eval.run(suiteFrom(payload));
            default -> throw new IllegalArgumentException("未知任务类型：" + type);
        }
    }

    private static String suiteFrom(String payload) {
        if (payload == null || payload.isBlank()) return AiEvalService.DEFAULT_SUITE;
        int at = payload.indexOf("\"suite\"");
        if (at < 0) return AiEvalService.DEFAULT_SUITE;
        int colon = payload.indexOf(':', at);
        int quote = payload.indexOf('"', colon + 1);
        int end = payload.indexOf('"', quote + 1);
        return quote < 0 || end < 0 ? AiEvalService.DEFAULT_SUITE : payload.substring(quote + 1, end);
    }

    Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        for (String status : new String[]{"QUEUED", "RUNNING", "SUCCEEDED", "DEAD"}) {
            Integer count = db.queryForObject("SELECT COUNT(*) FROM ai_job WHERE status = ?", Integer.class, status);
            stats.put(status.toLowerCase(), count == null ? 0 : count);
        }
        Integer total = db.queryForObject("SELECT COUNT(*) FROM ai_job", Integer.class);
        stats.put("total", total == null ? 0 : total);
        stats.put("recent", db.queryForList("""
                SELECT id, job_type, status, attempts, last_error, ctime, utime FROM ai_job
                ORDER BY id DESC LIMIT 10
                """));
        return stats;
    }

    private String toJson(Map<String, Object> payload) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append('"').append(entry.getKey()).append("\":\"")
                    .append(String.valueOf(entry.getValue()).replace("\"", "\\\"")).append('"');
        }
        return out.append('}').toString();
    }

    private static String abbreviate(String value) {
        if (value == null) return "";
        return value.length() <= 900 ? value : value.substring(0, 900);
    }
}
