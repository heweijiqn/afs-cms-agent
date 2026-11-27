package com.afs.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Versioned prompt registry.
 *
 * <p>Prompts used to be string literals scattered across the agent services, which makes an
 * A/B test or a rollback impossible. Every prompt now has a key, an immutable version row and
 * exactly one active version. Callers render the active version with named variables, and the
 * eval harness always records which version produced a score.
 */
@Profile("local-cms")
class PromptRegistry {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    record Prompt(String key, int version, String role, String content, String variables) {}

    private final JdbcTemplate db;

    PromptRegistry(JdbcTemplate db) {
        this.db = db;
    }

    void seedDefaults() {
        for (Prompt prompt : defaults()) {
            Integer exists = db.queryForObject(
                    "SELECT COUNT(*) FROM ai_prompt_template WHERE prompt_key = ? AND version = ?",
                    Integer.class, prompt.key(), prompt.version());
            if (exists != null && exists > 0) continue;
            db.update("""
                    INSERT INTO ai_prompt_template (prompt_key, version, role, content, variables, active, note)
                    VALUES (?, ?, ?, ?, ?, 1, ?)
                    """, prompt.key(), prompt.version(), prompt.role(), prompt.content(),
                    prompt.variables(), "seeded");
            // Only the newest seeded version stays active.
            db.update("""
                    UPDATE ai_prompt_template SET active = CASE WHEN version = ? THEN 1 ELSE 0 END
                    WHERE prompt_key = ?
                    """, prompt.version(), prompt.key());
        }
    }

    /** Renders the active version, substituting {{variable}} placeholders. */
    String render(String key, Map<String, Object> variables) {
        Prompt prompt = active(key);
        if (prompt == null) throw new IllegalArgumentException("未知提示词模板：" + key);
        String rendered = prompt.content();
        Matcher matcher = PLACEHOLDER.matcher(rendered);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = variables == null ? null : variables.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    Prompt active(String key) {
        var rows = db.query("""
                SELECT prompt_key, version, role, content, variables FROM ai_prompt_template
                WHERE prompt_key = ? AND active = 1 ORDER BY version DESC LIMIT 1
                """, (rs, index) -> new Prompt(rs.getString(1), rs.getInt(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    List<Map<String, Object>> list() {
        return db.query("""
                SELECT prompt_key, version, role, active, variables, note, ctime FROM ai_prompt_template
                ORDER BY prompt_key, version DESC
                """, (rs, index) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", rs.getString("prompt_key"));
            row.put("version", rs.getInt("version"));
            row.put("role", rs.getString("role"));
            row.put("active", rs.getInt("active") == 1);
            row.put("variables", rs.getString("variables"));
            row.put("note", rs.getString("note"));
            row.put("createdAt", rs.getTimestamp("ctime"));
            return row;
        });
    }

    Map<String, Object> activate(String key, int version) {
        Integer exists = db.queryForObject(
                "SELECT COUNT(*) FROM ai_prompt_template WHERE prompt_key = ? AND version = ?",
                Integer.class, key, version);
        if (exists == null || exists == 0) throw new IllegalArgumentException("提示词版本不存在：" + key + " v" + version);
        db.update("UPDATE ai_prompt_template SET active = CASE WHEN version = ? THEN 1 ELSE 0 END WHERE prompt_key = ?",
                version, key);
        return Map.of("key", key, "activeVersion", version);
    }

    int activeVersion(String key) {
        Prompt prompt = active(key);
        return prompt == null ? 0 : prompt.version();
    }

    private static List<Prompt> defaults() {
        return List.of(
                new Prompt("copilot.system", 1, "system", """
                        你是 AFS 商业广告投放智能副驾驶，连接 CMS 投放数据库完成诊断与预算建议。
                        规则：只依据工具返回的数据下结论；缺证据时明确说明；金额保留两位小数；不得编造平台接口结果。
                        """, ""),
                new Prompt("anomaly.scan", 1, "user", """
                        以下是近 14 天活动指标，请判断是否存在异常并给出归因：
                        {{metrics}}
                        """, "metrics"),
                new Prompt("creative.lab", 1, "user", """
                        为 {{vertical}} 垂类在 {{market}} 市场生成 3 条 {{format}} 创意脚本，突出 {{angle}}，避免绝对化表述。
                        """, "vertical,market,format,angle"),
                new Prompt("compliance.radar", 1, "user", """
                        检查以下广告文案是否符合 {{platform}} 政策，逐条列出违规点和可执行的修改建议：
                        {{copy}}
                        """, "platform,copy"),
                new Prompt("rag.answer", 2, "user", """
                        你是 AFS 内容与投放知识库助手。仅依据下面的检索片段回答，逐句标注引用编号 [1][2]。
                        如果片段不足以回答，直接说明缺少哪些数据，不要猜测。

                        问题：{{question}}

                        检索片段：
                        {{context}}
                        """, "question,context"),
                new Prompt("rag.answer.strict", 1, "user", """
                        只输出 JSON：{"answer": "...", "citations": [1,2], "confidence": 0.0-1.0}。
                        依据下列片段作答，未覆盖的内容写进 missing。
                        问题：{{question}}
                        片段：{{context}}
                        """, "question,context")
        );
    }
}
