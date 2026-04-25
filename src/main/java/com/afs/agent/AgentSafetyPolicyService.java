package com.afs.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Commercial 3-tier safety controller (SHADOW / COPILOT / AUTONOMOUS), global Kill Switch,
 * learning-phase single-change cap, daily cumulative budget delta cap, and daily LLM call budget.
 */
@Service
class AgentSafetyPolicyService {

    static final Set<String> ALLOWED_MODES = Set.of("SHADOW", "COPILOT", "AUTONOMOUS");

    private final JdbcTemplate jdbc;

    AgentSafetyPolicyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Policy(String safetyMode, boolean killSwitch, String killReason,
                  BigDecimal dailyBudgetDeltaCap, int maxSingleChangePct, int maxLlmCallsPerDay,
                  String updatedBy, OffsetDateTime updatedAt) {}

    Policy current() {
        ensureRow();
        return jdbc.query("""
                SELECT safety_mode, kill_switch, kill_reason, daily_budget_delta_cap,
                       max_single_change_pct, max_llm_calls_per_day, updated_by, updated_at
                FROM agent_safety_policy WHERE id = 1
                """, (rs, idx) -> new Policy(
                rs.getString("safety_mode"),
                rs.getBoolean("kill_switch"),
                rs.getString("kill_reason"),
                rs.getBigDecimal("daily_budget_delta_cap"),
                rs.getInt("max_single_change_pct"),
                rs.getInt("max_llm_calls_per_day"),
                rs.getString("updated_by"),
                rs.getObject("updated_at", OffsetDateTime.class)
        )).stream().findFirst().orElse(defaultPolicy());
    }

    Map<String, Object> summary() {
        Policy p = current();
        BigDecimal usedDeltaToday = todayCommittedBudgetDelta();
        int llmCallsToday = todayLlmCalls();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("safetyMode", p.safetyMode());
        map.put("killSwitch", p.killSwitch());
        map.put("killReason", p.killReason() == null ? "" : p.killReason());
        map.put("dailyBudgetDeltaCap", p.dailyBudgetDeltaCap());
        map.put("usedBudgetDeltaToday", usedDeltaToday);
        map.put("remainingBudgetDeltaToday", p.dailyBudgetDeltaCap().subtract(usedDeltaToday).max(BigDecimal.ZERO));
        map.put("maxSingleChangePct", p.maxSingleChangePct());
        map.put("maxLlmCallsPerDay", p.maxLlmCallsPerDay());
        map.put("llmCallsToday", llmCallsToday);
        map.put("updatedBy", p.updatedBy() == null ? "system" : p.updatedBy());
        map.put("updatedAt", p.updatedAt());
        return map;
    }

    @Transactional
    Policy update(String safetyMode, BigDecimal dailyBudgetDeltaCap, Integer maxSingleChangePct,
                  Integer maxLlmCallsPerDay, String actor) {
        ensureRow();
        Policy cur = current();
        String nextMode = safetyMode == null || safetyMode.isBlank()
                ? cur.safetyMode() : safetyMode.trim().toUpperCase();
        if (!ALLOWED_MODES.contains(nextMode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的安全模式：" + nextMode);
        }
        BigDecimal nextCap = dailyBudgetDeltaCap == null ? cur.dailyBudgetDeltaCap()
                : dailyBudgetDeltaCap.setScale(2, RoundingMode.HALF_UP);
        if (nextCap.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单日预算变动上限不能为负数");
        }
        int nextPct = maxSingleChangePct == null ? cur.maxSingleChangePct()
                : Math.max(1, Math.min(30, maxSingleChangePct));
        int nextLlm = maxLlmCallsPerDay == null ? cur.maxLlmCallsPerDay()
                : Math.max(10, Math.min(10000, maxLlmCallsPerDay));
        jdbc.update("""
                UPDATE agent_safety_policy
                SET safety_mode = ?, daily_budget_delta_cap = ?, max_single_change_pct = ?,
                    max_llm_calls_per_day = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """, nextMode, nextCap, nextPct, nextLlm, actor);
        return current();
    }

    @Transactional
    Policy setKillSwitch(boolean active, String reason, String actor) {
        ensureRow();
        String cleanReason = active ? (reason == null || reason.isBlank() ? "管理员手动触发全局熔断" : reason.trim()) : "";
        jdbc.update("""
                UPDATE agent_safety_policy
                SET kill_switch = ?, kill_reason = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """, active, cleanReason, actor);
        return current();
    }

    void assertExecutionAllowed(BigDecimal absDelta, int absChangePct) {
        Policy p = current();
        if (p.killSwitch()) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "全局熔断开关 (Kill Switch) 已开启，已拦截所有写操作"
                            + (p.killReason() == null || p.killReason().isBlank() ? "" : "：" + p.killReason()));
        }
        if ("SHADOW".equalsIgnoreCase(p.safetyMode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "当前处于 SHADOW（影子观察）模式，仅允许分析与试算；如需执行预算变更请切换至 COPILOT 或 AUTONOMOUS 模式");
        }
        if (absChangePct > p.maxSingleChangePct()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "本次调幅 ±" + absChangePct + "% 超出安全上限 ±" + p.maxSingleChangePct() + "%（防止重置广告平台学习期）");
        }
        BigDecimal usedToday = todayCommittedBudgetDelta();
        if (usedToday.add(absDelta).compareTo(p.dailyBudgetDeltaCap()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "本次变动 " + absDelta.setScale(2, RoundingMode.HALF_UP)
                            + " 将使今日累计变动超出上限 " + p.dailyBudgetDeltaCap().setScale(2, RoundingMode.HALF_UP));
        }
    }

    BigDecimal todayCommittedBudgetDelta() {
        try {
            BigDecimal sum = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(ABS(after_budget - before_budget)), 0)
                    FROM optimization_ledger
                    WHERE action_type = 'EXECUTE'
                      AND status = 'COMMITTED'
                      AND CAST(created_at AS DATE) = CURRENT_DATE
                    """, BigDecimal.class);
            return sum == null ? BigDecimal.ZERO : sum.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    int todayLlmCalls() {
        try {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM agent_execution_trace
                    WHERE total_tokens > 0
                      AND CAST(created_at AS DATE) = CURRENT_DATE
                    """, Integer.class);
            return count == null ? 0 : count;
        } catch (Exception ignored) {
            return 0;
        }
    }

    boolean isLlmBudgetExhausted() {
        Policy p = current();
        return todayLlmCalls() >= p.maxLlmCallsPerDay();
    }

    private void ensureRow() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM agent_safety_policy WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbc.update("""
                    INSERT INTO agent_safety_policy
                    (id, safety_mode, kill_switch, kill_reason, daily_budget_delta_cap, max_single_change_pct, max_llm_calls_per_day, updated_by)
                    VALUES (1, 'COPILOT', FALSE, '', 500.00, 20, 200, 'system')
                    """);
        }
    }

    private Policy defaultPolicy() {
        return new Policy("COPILOT", false, "", new BigDecimal("500.00"), 20, 200, "system", OffsetDateTime.now());
    }
}
