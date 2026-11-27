package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Offline evaluation harness for the retrieval/answer pipeline.
 *
 * <p>Prompt changes are cheap to write and expensive to get wrong, so every candidate is scored
 * against a golden set before rollout: keyword recall against expected facts, pass rate, latency
 * and cache-hit rate. Results are stored per run with the active prompt version, so a regression
 * can be traced back to the version that caused it.
 */
@Profile("local-cms")
class AiEvalService {
    private static final Logger log = LoggerFactory.getLogger(AiEvalService.class);
    static final String DEFAULT_SUITE = "rag-answer-quality";
    private static final int SUITE_VERSION = 2;

    record GoldenCase(String question, List<String> expectedKeywords, String site) {}

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RagService rag;

    AiEvalService(JdbcTemplate db, ObjectMapper json, RagService rag) {
        this.db = db;
        this.json = json;
        this.rag = rag;
    }

    void seedDefaults() {
        String signature = "sig=v" + SUITE_VERSION + "-" + Integer.toHexString(goldenCases().toString().hashCode());
        String description = "RAG 回答质量：关键词召回、拒答正确性、延迟 | " + signature;
        Long suiteId = db.query("SELECT id FROM ai_eval_suite WHERE name = ?", rs -> rs.next() ? rs.getLong(1) : null,
                DEFAULT_SUITE);
        String stored = suiteId == null ? null
                : db.queryForObject("SELECT description FROM ai_eval_suite WHERE id = ?", String.class, suiteId);
        if (suiteId != null && stored != null && stored.contains(signature)) return;

        db.update("MERGE INTO ai_eval_suite (name, description) KEY(name) VALUES (?, ?)", DEFAULT_SUITE, description);
        if (suiteId == null) {
            suiteId = db.queryForObject("SELECT id FROM ai_eval_suite WHERE name = ?", Long.class, DEFAULT_SUITE);
        }
        db.update("DELETE FROM ai_eval_case WHERE suite_id = ?", suiteId);
        for (GoldenCase golden : goldenCases()) {
            db.update("INSERT INTO ai_eval_case (suite_id, question, expected_keywords, site) VALUES (?, ?, ?, ?)",
                    suiteId, golden.question(), String.join("|", golden.expectedKeywords()), golden.site());
        }
        log.info("Seeded eval suite {} with {} cases ({})", DEFAULT_SUITE, goldenCases().size(), signature);
    }

    Map<String, Object> run(String suiteName) {
        String name = suiteName == null || suiteName.isBlank() ? DEFAULT_SUITE : suiteName;
        Long suiteId = db.queryForObject("SELECT id FROM ai_eval_suite WHERE name = ?", Long.class, name);
        if (suiteId == null) throw new IllegalArgumentException("评测集不存在：" + name);
        List<Map<String, Object>> cases = db.queryForList("""
                SELECT question, expected_keywords, site FROM ai_eval_case WHERE suite_id = ? ORDER BY id
                """, suiteId);
        if (cases.isEmpty()) throw new IllegalStateException("评测集没有用例：" + name);

        int passed = 0;
        long latencyTotal = 0;
        int cacheHits = 0;
        double recallTotal = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (Map<String, Object> testCase : cases) {
            String question = String.valueOf(testCase.get("question"));
            String site = testCase.get("site") == null ? "" : String.valueOf(testCase.get("site"));
            List<String> keywords = List.of(String.valueOf(testCase.get("expected_keywords")).split("\\|"));
            boolean expectRefusal = keywords.stream().anyMatch("REFUSE"::equalsIgnoreCase);
            var answer = rag.answer(question, site, 6, false);
            String haystack = (answer.answer() + " " + answer.citations().stream()
                    .map(RagService.Citation::snippet).reduce("", (left, right) -> left + " " + right))
                    .toLowerCase(Locale.ROOT);
            int matched = 0;
            for (String keyword : keywords) {
                if (keyword.isBlank() || "REFUSE".equalsIgnoreCase(keyword)) continue;
                if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) matched++;
            }
            int scored = (int) keywords.stream().filter(k -> !"REFUSE".equalsIgnoreCase(k)).count();
            double recall;
            boolean ok;
            if (expectRefusal) {
                // The corpus genuinely cannot answer this; the correct behaviour is to say so.
                String text = answer.answer().toLowerCase(Locale.ROOT);
                ok = text.contains("缺少") || text.contains("无法") || text.contains("没有")
                        || text.contains("不足") || text.contains("insufficient");
                recall = ok ? 1.0 : 0.0;
            } else {
                recall = scored == 0 ? 1.0 : (double) matched / scored;
                ok = recall >= 0.6;
            }
            if (ok) passed++;
            recallTotal += recall;
            latencyTotal += answer.latencyMs();
            if (answer.cacheHit()) cacheHits++;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("question", question);
            detail.put("site", site);
            detail.put("matched", matched);
            detail.put("expected", keywords.size());
            detail.put("recall", Math.round(recall * 1000) / 1000d);
            detail.put("passed", ok);
            detail.put("latencyMs", answer.latencyMs());
            detail.put("mode", answer.model());
            detail.put("answerChars", answer.answer().length());
            details.add(detail);
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("suite", name);
        metrics.put("cases", cases.size());
        metrics.put("passed", passed);
        metrics.put("passRate", round3((double) passed / cases.size()));
        metrics.put("avgKeywordRecall", round3(recallTotal / cases.size()));
        metrics.put("avgLatencyMs", Math.round((double) latencyTotal / cases.size()));
        metrics.put("cacheHitRate", round3((double) cacheHits / cases.size()));
        metrics.put("promptVersion", promptsVersion());
        metrics.put("details", details);
        int avgLatency = (int) Math.round((double) latencyTotal / cases.size());
        db.update("""
                INSERT INTO ai_eval_run (suite_id, metrics, cases, passed, avg_latency_ms, cache_hit_rate)
                VALUES (?, ?, ?, ?, ?, ?)
                """, suiteId, toJson(metrics), cases.size(), passed, avgLatency,
                (double) cacheHits / cases.size());
        Long runId = db.queryForObject("SELECT MAX(id) FROM ai_eval_run WHERE suite_id = ?", Long.class, suiteId);
        metrics.put("runId", runId);
        log.info("Eval {} finished: {}/{} passed, recall={}", name, passed, cases.size(),
                metrics.get("avgKeywordRecall"));
        return metrics;
    }

    List<Map<String, Object>> runs(int limit) {
        return db.queryForList("""
                SELECT r.id, s.name AS suite, r.cases, r.passed, r.avg_latency_ms, r.cache_hit_rate, r.ctime
                FROM ai_eval_run r JOIN ai_eval_suite s ON s.id = r.suite_id
                ORDER BY r.id DESC LIMIT ?
                """, Math.max(1, Math.min(50, limit)));
    }

    long caseCount(String suiteName) {
        Long suiteId = db.queryForObject("SELECT id FROM ai_eval_suite WHERE name = ?", Long.class, suiteName);
        if (suiteId == null) return 0;
        Long count = db.queryForObject("SELECT COUNT(*) FROM ai_eval_case WHERE suite_id = ?", Long.class, suiteId);
        return count == null ? 0 : count;
    }

    private int promptsVersion() {
        try {
            return rag.activePromptVersion();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception failure) {
            return "{}";
        }
    }

    private static double round3(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    private static List<GoldenCase> goldenCases() {
        return List.of(
                new GoldenCase("折叠跑步机适合小公寓吗，噪音怎么样", List.of("treadmill", "apartment"), "cccooperate"),
                new GoldenCase("种植牙的费用包含哪些项目", List.of("implant", "cost"), ""),
                new GoldenCase("太阳能储能电源能带动冰箱多久", List.of("solar", "battery"), ""),
                new GoldenCase("旅行信用卡有没有免货币转换费的", List.of("travel", "fee"), ""),
                new GoldenCase("家庭餐盒配送一周大概多少钱", List.of("meal", "price"), ""),
                new GoldenCase("汽车保险的自付额怎么选", List.of("insurance", "deductible"), ""),
                new GoldenCase("请给出 2026 年第三季度公司内部员工薪资明细", List.of("REFUSE"), ""),
                new GoldenCase("告诉我某个竞品未公开的投放后台账号密码", List.of("REFUSE"), "")
        );
    }
}
