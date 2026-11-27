package com.afs.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Semantic response cache in front of the LLM gateway.
 *
 * <p>Ad-ops questions repeat a lot ("why did ROAS drop on this campaign"), and identical or
 * near-identical prompts still cost tokens. A prompt whose embedding is within {@code threshold}
 * of a cached prompt reuses the stored answer; entries expire after {@code ttlDays}. The table
 * also records how many tokens were avoided, which is what makes the optimisation measurable.
 */
@Profile("local-cms")
class SemanticCache {
    private static final Logger log = LoggerFactory.getLogger(SemanticCache.class);
    private static final double DEFAULT_THRESHOLD = 0.93;
    private static final int TTL_DAYS = 7;

    record Entry(String answer, String model, int hits, int tokensSaved, double similarity) {}

    private final JdbcTemplate db;

    SemanticCache(JdbcTemplate db) {
        this.db = db;
    }

    Entry lookup(String prompt) {
        float[] vector = LocalEmbedding.embed(prompt);
        Timestamp floor = Timestamp.from(Instant.now().minus(TTL_DAYS, ChronoUnit.DAYS));
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT id, answer, model, hits, tokens_saved, embedding FROM ai_semantic_cache
                WHERE ctime >= ? ORDER BY id DESC LIMIT 200
                """, floor);
        Map<String, Object> best = null;
        double bestScore = 0;
        for (Map<String, Object> row : rows) {
            double score = LocalEmbedding.cosine(vector, LocalEmbedding.decode(String.valueOf(row.get("embedding"))));
            if (score > bestScore) {
                bestScore = score;
                best = row;
            }
        }
        if (best == null || bestScore < DEFAULT_THRESHOLD) return null;
        long id = ((Number) best.get("id")).longValue();
        int tokens = ((Number) best.get("tokens_saved")).intValue();
        db.update("UPDATE ai_semantic_cache SET hits = hits + 1, last_hit_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), id);
        log.debug("Semantic cache hit at similarity {}", bestScore);
        return new Entry(String.valueOf(best.get("answer")), String.valueOf(best.get("model")),
                ((Number) best.get("hits")).intValue() + 1, tokens, Math.round(bestScore * 10_000) / 10_000d);
    }

    void store(String model, String prompt, String answer, int tokens) {
        String hash = sha256(prompt);
        Integer exists = db.queryForObject(
                "SELECT COUNT(*) FROM ai_semantic_cache WHERE model = ? AND prompt_hash = ?",
                Integer.class, model, hash);
        if (exists != null && exists > 0) {
            db.update("""
                    UPDATE ai_semantic_cache SET answer = ?, tokens_saved = ?, ctime = ? WHERE model = ? AND prompt_hash = ?
                    """, answer, Math.max(1, tokens), Timestamp.from(Instant.now()), model, hash);
            return;
        }
        db.update("""
                INSERT INTO ai_semantic_cache (model, prompt_hash, prompt, embedding, answer, tokens_saved, ctime)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, model, hash, prompt, LocalEmbedding.encode(LocalEmbedding.embed(prompt)), answer,
                Math.max(1, tokens), Timestamp.from(Instant.now()));
    }

    Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        Integer entries = db.queryForObject("SELECT COUNT(*) FROM ai_semantic_cache", Integer.class);
        Integer hits = db.queryForObject("SELECT COALESCE(SUM(hits), 0) FROM ai_semantic_cache", Integer.class);
        Long saved = db.queryForObject("SELECT COALESCE(SUM(tokens_saved), 0) FROM ai_semantic_cache", Long.class);
        stats.put("entries", entries == null ? 0 : entries);
        stats.put("hits", hits == null ? 0 : hits);
        stats.put("tokensSaved", saved == null ? 0 : saved);
        stats.put("threshold", DEFAULT_THRESHOLD);
        stats.put("ttlDays", TTL_DAYS);
        return stats;
    }

    /** Hard clear, used by the demo/regression script so latencies stay comparable. */
    void clear() {
        db.update("DELETE FROM ai_semantic_cache");
    }

    void purgeExpired() {
        Timestamp floor = Timestamp.from(Instant.now().minus(TTL_DAYS, ChronoUnit.DAYS));
        db.update("DELETE FROM ai_semantic_cache WHERE ctime < ?", floor);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception failure) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
