package com.afs.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic 8-signal decision scorecard inspired by Drip's "rules decide, LLM explains" paradigm.
 * Every signal is evaluated purely in Java with GREEN / YELLOW / RED status and an auditable rule chain.
 */
final class SignalScorecard {

    private SignalScorecard() {}

    record Signal(String code, String label, String value, String benchmark, String status, String detail) {}

    record Scorecard(String verdict, double confidence, int greenCount, int yellowCount, int redCount,
                     List<Signal> signals, List<String> ruleChain) {}

    static Scorecard evaluate(OptimizationWorkflow.Input input,
                              OptimizationWorkflow.Metrics metrics,
                              LegacyContext.Snapshot context,
                              CmsReportSource.Campaign evidence,
                              OptimizationWorkflow.Proposal proposal) {
        List<Signal> signals = new ArrayList<>(8);
        List<String> ruleChain = new ArrayList<>();

        BigDecimal targetRoas = input.targetRoas();
        BigDecimal roas = metrics.roas();
        BigDecimal scaleThreshold = targetRoas.multiply(new BigDecimal("1.20"));
        BigDecimal bleedThreshold = targetRoas.multiply(new BigDecimal("0.75"));

        // 1. ROAS
        String roasStatus;
        String roasDetail;
        if (roas == null) {
            roasStatus = "YELLOW";
            roasDetail = "暂无有效花费，无法计算 ROAS";
        } else if (roas.compareTo(scaleThreshold) >= 0) {
            roasStatus = "GREEN";
            roasDetail = "ROAS 超出目标 20% 以上，具备放量安全边际";
            ruleChain.add("R1[ROAS_STRONG]: " + fmt(roas) + "x >= " + fmt(scaleThreshold) + "x");
        } else if (roas.compareTo(targetRoas) >= 0) {
            roasStatus = "GREEN";
            roasDetail = "ROAS 达标，整体盈利健康";
            ruleChain.add("R1[ROAS_OK]: " + fmt(roas) + "x >= " + fmt(targetRoas) + "x");
        } else if (roas.compareTo(bleedThreshold) >= 0) {
            roasStatus = "YELLOW";
            roasDetail = "ROAS 略低于目标，需收缩低效版位或小幅降预算";
            ruleChain.add("R1[ROAS_SOFT]: " + fmt(roas) + "x < " + fmt(targetRoas) + "x");
        } else {
            roasStatus = "RED";
            roasDetail = "ROAS 显著低于目标 75% 警戒线，存在持续亏损风险";
            ruleChain.add("R1[ROAS_BLEED]: " + fmt(roas) + "x < " + fmt(bleedThreshold) + "x");
        }
        signals.add(new Signal("ROAS", "投产比 (ROAS)",
                roas == null ? "—" : fmt(roas) + "×",
                "目标 ≥ " + fmt(targetRoas) + "×", roasStatus, roasDetail));

        // 2. CPA / 转化成本
        BigDecimal cpa = metrics.cpa();
        BigDecimal revenuePerConv = input.conversions() > 0
                ? input.revenue().divide(BigDecimal.valueOf(input.conversions()), 4, RoundingMode.HALF_UP)
                : null;
        BigDecimal maxHealthyCpa = revenuePerConv != null && targetRoas.signum() > 0
                ? revenuePerConv.divide(targetRoas, 2, RoundingMode.HALF_UP) : null;
        String cpaStatus;
        String cpaDetail;
        if (cpa == null || input.conversions() == 0) {
            cpaStatus = input.spend().compareTo(input.minimumSpend()) >= 0 ? "RED" : "YELLOW";
            cpaDetail = input.spend().compareTo(input.minimumSpend()) >= 0
                    ? "已达到最低花费样本但零转化，建议排查落地页或止损"
                    : "尚无转化记录，继续积累样本";
        } else if (maxHealthyCpa != null && cpa.compareTo(maxHealthyCpa) <= 0) {
            cpaStatus = "GREEN";
            cpaDetail = "单次转化成本低于盈亏平衡上限";
        } else if (maxHealthyCpa != null && cpa.compareTo(maxHealthyCpa.multiply(new BigDecimal("1.25"))) <= 0) {
            cpaStatus = "YELLOW";
            cpaDetail = "CPA 略高于目标上限，需优化出价与受众";
        } else {
            cpaStatus = "RED";
            cpaDetail = "CPA 严重超标，拉低整体利润率";
        }
        signals.add(new Signal("CPA", "转化成本 (CPA)",
                cpa == null ? "—" : input.currency() + " " + fmt(cpa),
                maxHealthyCpa == null ? "需有效转化" : "≤ " + input.currency() + " " + fmt(maxHealthyCpa),
                cpaStatus, cpaDetail));

        // 3. CVR
        BigDecimal cvr = metrics.cvr();
        String cvrStatus;
        String cvrDetail;
        if (cvr == null) {
            cvrStatus = "YELLOW";
            cvrDetail = "暂无点击数据";
        } else if (cvr.compareTo(new BigDecimal("0.04")) >= 0) {
            cvrStatus = "GREEN";
            cvrDetail = "点击到变现/转化承接效率优秀";
        } else if (cvr.compareTo(new BigDecimal("0.015")) >= 0) {
            cvrStatus = "YELLOW";
            cvrDetail = "转化率处于行业均值区间，可通过落地页测试提升";
        } else {
            cvrStatus = "RED";
            cvrDetail = "转化率偏低，建议核查关键词意图与落地页匹配度";
        }
        signals.add(new Signal("CVR", "转化率 (CVR)",
                cvr == null ? "—" : pct(cvr), "参考 ≥ 4.0%", cvrStatus, cvrDetail));

        // 4. CTR
        BigDecimal ctr = metrics.ctr();
        String ctrStatus;
        String ctrDetail;
        if (ctr == null) {
            ctrStatus = "YELLOW";
            ctrDetail = "暂无展示数据";
        } else if (ctr.compareTo(new BigDecimal("0.015")) >= 0) {
            ctrStatus = "GREEN";
            ctrDetail = "创意点击吸引力健康，暂未出现明显素材疲劳";
        } else if (ctr.compareTo(new BigDecimal("0.008")) >= 0) {
            ctrStatus = "YELLOW";
            ctrDetail = "CTR 偏平，建议准备新一轮 A/B 创意素材";
        } else {
            ctrStatus = "RED";
            ctrDetail = "CTR 过低，存在严重素材疲劳或受众错配";
            ruleChain.add("R4[CREATIVE_FATIGUE]: CTR " + pct(ctr) + " < 0.8%");
        }
        signals.add(new Signal("CTR", "点击率 (CTR)",
                ctr == null ? "—" : pct(ctr), "参考 ≥ 1.5%", ctrStatus, ctrDetail));

        // 5. Spend Sample
        boolean spendReady = input.spend().compareTo(input.minimumSpend()) >= 0;
        signals.add(new Signal("SPEND_SAMPLE", "花费样本量",
                input.currency() + " " + fmt(input.spend()),
                "阈值 ≥ " + input.currency() + " " + fmt(input.minimumSpend()),
                spendReady ? "GREEN" : "YELLOW",
                spendReady ? "花费样本充足，满足统计决策门槛" : "花费低于最小样本阈值，决策自动保守化"));
        if (!spendReady) ruleChain.add("R5[SAMPLE_GUARD]: spend < minSpend -> HOLD");

        // 6. Click Sample
        boolean clicksReady = input.clicks() >= input.minimumClicks();
        signals.add(new Signal("CLICK_SAMPLE", "点击样本量",
                String.valueOf(input.clicks()),
                "阈值 ≥ " + input.minimumClicks(),
                clicksReady ? "GREEN" : "YELLOW",
                clicksReady ? "点击样本充足，置信度可靠" : "点击量不足，避免过早调价重置学习期"));
        if (!clicksReady) ruleChain.add("R6[CLICK_GUARD]: clicks < minClicks -> HOLD");

        // 7. Daily Stability
        int activeDays = evidence == null || evidence.daily() == null ? 0 : evidence.daily().size();
        String stabilityStatus;
        String stabilityDetail;
        if (activeDays >= 5) {
            long healthyDays = evidence.daily().stream()
                    .filter(d -> d.spend().signum() > 0 && d.revenue().compareTo(d.spend().multiply(targetRoas)) >= 0)
                    .count();
            double ratio = (double) healthyDays / activeDays;
            if (ratio >= 0.6) {
                stabilityStatus = "GREEN";
                stabilityDetail = activeDays + " 天中有 " + healthyDays + " 天 ROAS 达标，趋势稳定";
            } else if (ratio >= 0.3) {
                stabilityStatus = "YELLOW";
                stabilityDetail = activeDays + " 天中仅 " + healthyDays + " 天达标，日度波动较大";
            } else {
                stabilityStatus = "RED";
                stabilityDetail = activeDays + " 天中仅 " + healthyDays + " 天达标，持续承压";
            }
        } else if (activeDays >= 2) {
            stabilityStatus = "YELLOW";
            stabilityDetail = "仅有 " + activeDays + " 天日度记录，稳定性待进一步观察";
        } else {
            stabilityStatus = "YELLOW";
            stabilityDetail = "缺少多日趋势证据，保守评估";
        }
        signals.add(new Signal("DAILY_STABILITY", "日度趋势稳定性",
                activeDays > 0 ? activeDays + " 天证据" : "汇总快照",
                "建议 ≥ 5 天连续数据", stabilityStatus, stabilityDetail));

        // 8. Budget Headroom & Guardrail
        int effectiveCap = Math.min(input.maxChangePercent(), context.budgetChangeCap());
        String guardrailStatus;
        String guardrailDetail;
        if (!context.channelConfigured()) {
            guardrailStatus = "RED";
            guardrailDetail = "报表渠道未配置，禁止自动调预算";
            ruleChain.add("R8[CHANNEL_UNCONFIGURED]: channel missing -> HOLD");
        } else if (effectiveCap <= 0) {
            guardrailStatus = "RED";
            guardrailDetail = "未绑定可调预算规则，需先绑定预算护栏";
            ruleChain.add("R8[RULE_UNBOUND]: budgetCap=0 -> HOLD");
        } else if (effectiveCap <= 20) {
            guardrailStatus = "GREEN";
            guardrailDetail = "已受规则护栏约束（单次上限 ±" + effectiveCap + "%），保护学习期不重置";
            ruleChain.add("R8[LEARNING_GUARD]: cap ±" + effectiveCap + "%");
        } else {
            guardrailStatus = "YELLOW";
            guardrailDetail = "规则变动上限偏大，已收敛至 ±20%";
        }
        signals.add(new Signal("BUDGET_GUARDRAIL", "预算护栏与学习期",
                effectiveCap > 0 ? "±" + effectiveCap + "%" : "未绑定",
                "安全范围 ±1%~20%", guardrailStatus, guardrailDetail));

        int green = 0, yellow = 0, red = 0;
        for (Signal s : signals) {
            switch (s.status()) {
                case "GREEN" -> green++;
                case "YELLOW" -> yellow++;
                default -> red++;
            }
        }

        // Map deterministic verdict
        String verdict;
        if (!spendReady || !clicksReady || !context.channelConfigured() || effectiveCap <= 0) {
            verdict = "HOLD";
        } else if (proposal.action() == OptimizationWorkflow.Action.INCREASE) {
            verdict = "SCALE";
        } else if (proposal.action() == OptimizationWorkflow.Action.REDUCE) {
            verdict = ("RED".equals(roasStatus) && "RED".equals(cpaStatus)) ? "PAUSE" : "REDUCE";
        } else if ("RED".equals(ctrStatus)) {
            verdict = "REFRESH";
        } else {
            verdict = "HOLD";
        }

        double confidence = 0.50 + (spendReady ? 0.12 : -0.10) + (clicksReady ? 0.12 : -0.10)
                + (activeDays >= 5 ? 0.14 : activeDays >= 2 ? 0.06 : 0.0)
                + (effectiveCap > 0 ? 0.08 : -0.08);
        confidence = Math.max(0.25, Math.min(0.96, Math.round(confidence * 100.0) / 100.0));
        ruleChain.add("VERDICT[" + verdict + "]: proposal=" + proposal.action() + " " + proposal.changePercent() + "%");

        return new Scorecard(verdict, confidence, green, yellow, red, List.copyOf(signals), List.copyOf(ruleChain));
    }

    static String proposalFingerprint(OptimizationWorkflow.Input input, OptimizationWorkflow.Proposal proposal) {
        String canonical = String.join("|",
                safe(input.site()),
                safe(input.channel()),
                safe(input.campaignId()),
                safe(input.start()),
                safe(input.end()),
                safe(input.targetRoas()),
                String.valueOf(input.maxChangePercent()),
                safe(proposal.action()),
                String.valueOf(proposal.changePercent()),
                safe(proposal.reason()));
        return sha256Hex(canonical);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String safe(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String pct(BigDecimal val) {
        return val.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
