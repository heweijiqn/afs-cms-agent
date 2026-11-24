package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commercial-grade read-only LangChain4j tool suite bound to a single campaign analysis run.
 * Executed natively inside LangGraph4j {@code ToolNode}. Every method is strictly read-only
 * and parameterized, so models can deeply inspect real CMS data without mutating budgets.
 */
public final class AdvisoryTools {
    private static final int MAX_DAYS = 40;

    private final ObjectMapper json;
    private final OptimizationWorkflow.Input input;
    private final CmsReportSource.Campaign report;
    private final LegacyContext.Snapshot context;
    private final CmsReportSource reports;
    private final CmsCampaignDataService cmsData;

    AdvisoryTools(ObjectMapper json, OptimizationWorkflow.Input input, CmsReportSource.Campaign report,
                  LegacyContext.Snapshot context, CmsReportSource reports, CmsCampaignDataService cmsData) {
        this.json = json;
        this.input = input;
        this.report = report;
        this.context = context;
        this.reports = reports;
        this.cmsData = cmsData;
    }

    @Tool(name = "dailyReport", value = "读取当前活动的逐日投放明细（日期、花费、收入、ROAS、展示、点击、转化）以及受众/创意分组样本，用于判断趋势斜率、波动性与样本成熟度。")
    public String dailyReport() {
        CmsReportSource.Campaign source = report;
        if (source == null && cmsData != null) {
            source = cmsData.loadCampaignEvidence(input.campaignId(), input.site(), input.channel(),
                    input.start(), input.end());
        }
        if (source == null) {
            return "{\"available\":false,\"reason\":\"这次分析只有汇总数据，没有日度报表、受众分组或创意分组明细\"}";
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("available", true);
        payload.put("scenario", source.id());
        payload.put("campaignId", source.campaignId());
        payload.put("currency", source.currency());
        payload.put("currentBudget", source.currentBudget());
        payload.put("days", source.daily().stream().limit(MAX_DAYS).map(day -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("date", day.date().toString());
            m.put("spend", day.spend());
            m.put("revenue", day.revenue());
            m.put("roas", day.spend().signum() > 0
                    ? day.revenue().divide(day.spend(), 4, RoundingMode.HALF_UP) : null);
            m.put("impressions", day.impressions());
            m.put("clicks", day.clicks());
            m.put("conversions", day.conversions());
            return m;
        }).toList());
        payload.put("activeDays", source.daily().stream().filter(day -> day.spend().signum() > 0).count());
        payload.put("segments", source.segments().stream().map(segment -> Map.of(
                "name", segment.name(), "impressions", segment.impressions(), "clicks", segment.clicks(),
                "conversions", segment.conversions(), "spend", segment.spend())).toList());
        payload.put("creative", source.creative().stream().map(creative -> Map.of(
                "name", creative.name(), "impressions", creative.impressions(), "clicks", creative.clicks())).toList());
        return write(payload);
    }

    @Tool(name = "countryBreakdown", value = "读取当前活动按国家或受众维度拆分的消耗、收入、净利、ROAS、点击和 CPA，用于识别亏损国家或高潜放量市场。")
    public String countryBreakdown() {
        if (cmsData == null) {
            return "{\"available\":false,\"reason\":\"当前模式未提供国家维度拆分\"}";
        }
        var rows = cmsData.getCountryBreakdown(input.campaignId(), input.channel(), input.start(), input.end());
        if (rows.isEmpty()) {
            return "{\"available\":false,\"reason\":\"当前活动暂无国家或受众细分记录\"}";
        }
        return write(Map.of("available", true, "campaignId", input.campaignId(), "breakdown", rows));
    }

    @Tool(name = "creativeAndLandingContext", value = "读取活动关联的落地页文章、关键词、广告素材、广告文案、规则执行日志及站点用户反馈，用于设计精准的创意与落地页实验。")
    public String creativeAndLandingContext() {
        if (cmsData == null) {
            return "{\"available\":false,\"reason\":\"当前模式未连接 CMS 资料库\"}";
        }
        return write(cmsData.getCampaignDeepProfile(input.campaignId()));
    }

    @Tool(name = "ruleContext", value = "读取当前活动的规则上下文：报表渠道配置状态、绑定规则数量、预算调整上限百分比以及样本和 ROAS 阈值。")
    public String ruleContext() {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("source", context.source());
        payload.put("channelConfigured", context.channelConfigured());
        payload.put("availableRules", context.exampleRuleCount());
        payload.put("budgetChangeCapPercent", context.budgetChangeCap());
        payload.put("maxChangePercent", input.maxChangePercent());
        payload.put("effectiveLimitPercent", effectiveLimit());
        payload.put("minimumSpend", input.minimumSpend());
        payload.put("minimumClicks", input.minimumClicks());
        payload.put("targetRoas", input.targetRoas());
        return write(payload);
    }

    @Tool(name = "peerBenchmarks", value = "对比同站点/同工作区其他在投活动的 ROAS 和消耗表现，评估当前活动处于领先还是落后梯队。")
    public String peerBenchmarks() {
        if (cmsData == null) {
            return "{\"available\":false,\"reason\":\"暂无同站点基准数据\"}";
        }
        return write(cmsData.getPeerBenchmarks(input.site(), input.campaignId()));
    }

    @Tool(name = "historicalRuns", value = "读取该活动过往的智能体分析记录与人工审批意见（情景记忆），避免重复给出已被驳回的同类建议。")
    public String historicalRuns() {
        if (cmsData == null) {
            return "{\"available\":false,\"runs\":[]}";
        }
        var history = cmsData.getHistoricalRuns(input.campaignId(), 5);
        return write(Map.of("available", !history.isEmpty(), "count", history.size(), "runs", history));
    }

    @Tool(name = "scenarioCatalog", value = "列出可交叉参考的报表场景清单，包含场景 ID、活动 ID、币种和当前预算。")
    public String scenarioCatalog() {
        return write(Map.of("scenarios", reports.scenarios().stream().map(scenario -> Map.of(
                "id", scenario.id(), "campaignId", scenario.campaignId(),
                "currency", scenario.currency(), "currentBudget", scenario.currentBudget())).toList()));
    }

    @Tool(name = "simulateBudget", value = "按百分比试算预算调整结果与日预算增量。只做计算，不写入数据库，也不修改线上投放。")
    public String simulateBudget(
            @P("预算动作，取 HOLD、REDUCE 或 INCREASE") String action,
            @P("调整百分比：INCREASE 用正数，REDUCE 用负数，HOLD 为 0，绝对值不超过上限") int changePercent,
            @P("当前日预算金额") double currentBudget) {
        String normalized = action == null ? "" : action.trim().toUpperCase(java.util.Locale.ROOT);
        int percent = "HOLD".equals(normalized) ? 0 : changePercent;
        int bound = effectiveLimit();
        int applied = Math.max(-bound, Math.min(bound, percent));
        BigDecimal base = BigDecimal.valueOf(Math.max(0.0, currentBudget)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal projected = base.multiply(BigDecimal.valueOf(100L + applied))
                .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal delta = projected.subtract(base).setScale(2, RoundingMode.HALF_UP);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("action", normalized);
        payload.put("requestedPercent", percent);
        payload.put("appliedPercent", applied);
        payload.put("limitPercent", bound);
        payload.put("currentBudget", base);
        payload.put("projectedBudget", projected);
        payload.put("dailyDelta", delta);
        payload.put("note", applied == percent ? "仅计算，未写入数据库" : "请求幅度超过规则上限，已按 ±" + bound + "% 收紧");
        return write(payload);
    }

    int effectiveLimit() {
        return Math.max(0, Math.min(input.maxChangePercent(), context.budgetChangeCap()));
    }

    static List<String> toolNames() {
        return List.of("dailyReport", "countryBreakdown", "creativeAndLandingContext",
                "ruleContext", "peerBenchmarks", "historicalRuns", "scenarioCatalog", "simulateBudget");
    }

    private String write(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            return "{\"available\":false,\"reason\":\"工具结果无法序列化\"}";
        }
    }
}
