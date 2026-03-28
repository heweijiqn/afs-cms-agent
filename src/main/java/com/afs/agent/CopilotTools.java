package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Interactive LangChain4j tool suite for the Multi-Turn AdOps Copilot LangGraph agent.
 * Gives the Copilot live query access to CMS campaigns, portfolio metrics, anomaly scanning,
 * rules, historical runs, budget projections, and safe Human-in-the-Loop run creation.
 */
public final class CopilotTools {
    private final ObjectMapper json;
    private final CmsCampaignDataService cmsData;
    private final BiFunction<String, String, Map<String, Object>> optimizationLauncher;
    private final Principal actor;

    CopilotTools(ObjectMapper json, CmsCampaignDataService cmsData,
                 BiFunction<String, String, Map<String, Object>> optimizationLauncher,
                 Principal actor) {
        this.json = json;
        this.cmsData = cmsData;
        this.optimizationLauncher = optimizationLauncher;
        this.actor = actor;
    }

    @Tool(name = "getPortfolioOverview", value = "获取当前工作区全部在投活动的整体投放概览：总花费、总收入、总净利、综合 ROAS、各广告平台汇总、盈利 Top 5 与亏损 Top 5。")
    public String getPortfolioOverview() {
        return write(cmsData.getPortfolioOverview());
    }

    @Tool(name = "scanAnomalies", value = "全盘巡检所有活动，识别严重亏损止损项、ROAS 偏低活动、高潜放量机会以及未绑定预算护栏规则的高消耗活动。")
    public String scanAnomalies(
            @P("目标 ROAS 阈值，例如 1.25") double targetRoas,
            @P("最低花费过滤阈值（美元），例如 30") double minSpend) {
        BigDecimal target = targetRoas > 0 ? BigDecimal.valueOf(targetRoas) : new BigDecimal("1.25");
        BigDecimal floor = minSpend >= 0 ? BigDecimal.valueOf(minSpend) : new BigDecimal("30");
        return write(cmsData.scanAnomalies(target, floor));
    }

    @Tool(name = "listUnboundCampaigns", value = "专门查询全盘在投且尚未绑定任何自动化规则的高消耗活动（按花费从高到低排序，一次性返回完整清单，无需分平台翻页）。")
    public String listUnboundCampaigns(
            @P("最低花费过滤阈值（美元），例如 50；传 0 表示全部未绑规则在投活动") double minSpend,
            @P("广告平台过滤（TikTok/Taboola/Google/Facebook），留空表示全平台") String platform,
            @P("返回条数上限，默认 25") int limit) {
        BigDecimal floor = minSpend > 0 ? BigDecimal.valueOf(minSpend) : BigDecimal.ZERO;
        var list = cmsData.listUnboundCampaigns(floor, platform, limit <= 0 ? 25 : Math.min(limit, 50));
        return write(Map.of("count", list.size(), "campaigns", list));
    }

    @Tool(name = "searchCampaigns", value = "按关键词、活动 ID 或广告平台（如 TikTok、Taboola、Google、Facebook）搜索活动列表及其花费/收入/ROAS/规则绑定状态。")
    public String searchCampaigns(
            @P("搜索关键词，可留空查全部") String query,
            @P("广告平台名称过滤，可留空") String platform,
            @P("返回条数上限，默认 20") int limit) {
        var list = cmsData.listCampaigns(query, platform, limit <= 0 ? 20 : Math.min(limit, 40));
        return write(Map.of("count", list.size(), "campaigns", list));
    }

    @Tool(name = "getCampaignDeepDive", value = "深入查询单个活动的完整档案：逐日花费/收入/ROAS、国家拆分、绑定规则、落地页文章、素材、文案、规则日志和站点用户反馈。")
    public String getCampaignDeepDive(@P("活动 ID，例如 demo-campaign-001 或 demo-growth") String campaignId) {
        if (campaignId == null || campaignId.isBlank()) {
            return "{\"available\":false,\"reason\":\"请提供有效的 campaignId\"}";
        }
        return write(cmsData.getCampaignDeepProfile(campaignId.strip()));
    }

    @Tool(name = "getRuleCatalog", value = "查询当前已启用的投放预算规则列表及预算调整上限。")
    public String getRuleCatalog() {
        var rules = cmsData.listActiveRules();
        return write(Map.of("count", rules.size(), "rules", rules));
    }

    @Tool(name = "getRecentOptimizationRuns", value = "查询最近的智能体优化分析记录、AI 建议动作及审批状态，可按活动 ID 过滤。")
    public String getRecentOptimizationRuns(
            @P("活动 ID，留空则查全部活动") String campaignId,
            @P("返回条数上限，默认 8") int limit) {
        var runs = cmsData.getHistoricalRuns(campaignId, limit <= 0 ? 8 : Math.min(limit, 20));
        return write(Map.of("count", runs.size(), "runs", runs));
    }

    @Tool(name = "simulateBudgetProjection", value = "试算预算调整后的新日预算及日变动额（纯计算，不会直接修改线上预算）。")
    public String simulateBudgetProjection(
            @P("当前日预算") double currentBudget,
            @P("动作：INCREASE / REDUCE / HOLD") String action,
            @P("调整百分比（-20 到 20）") int changePercent) {
        String act = action == null ? "HOLD" : action.trim().toUpperCase(java.util.Locale.ROOT);
        int bounded = "HOLD".equals(act) ? 0 : Math.max(-20, Math.min(20, changePercent));
        BigDecimal cur = BigDecimal.valueOf(Math.max(0.0, currentBudget)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal next = cur.multiply(BigDecimal.valueOf(100L + bounded)).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        var result = new LinkedHashMap<String, Object>();
        result.put("action", act);
        result.put("appliedPercent", bounded);
        result.put("currentBudget", cur);
        result.put("projectedBudget", next);
        result.put("dailyDelta", next.subtract(cur).setScale(2, RoundingMode.HALF_UP));
        return write(result);
    }

    @Tool(name = "launchOptimizationAnalysis", value = "为指定活动创建一条正式的 LangGraph 优化分析与审批工单（仍需人工审批，不会直接修改投放预算）。当用户要求“对某活动生成正式分析/审批建议”时调用。")
    public String launchOptimizationAnalysis(
            @P("活动 ID，例如 demo-campaign-001") String campaignId,
            @P("分析模式：STANDARD（标准）或 DEEP（深度多智能体协作）") String mode) {
        if (campaignId == null || campaignId.isBlank()) {
            return "{\"created\":false,\"reason\":\"缺少 campaignId\"}";
        }
        try {
            String resolvedMode = "DEEP".equalsIgnoreCase(mode) ? "DEEP" : "STANDARD";
            return write(optimizationLauncher.apply(campaignId.strip(), resolvedMode));
        } catch (Exception ex) {
            return write(Map.of("created", false, "reason", ex.getMessage() == null ? "创建失败" : ex.getMessage()));
        }
    }

    String actorName() {
        return actor == null ? "local-operator" : actor.getName();
    }

    private String write(Object payload) {
        try {
            String raw = json.writeValueAsString(payload);
            return raw.length() <= 12000 ? raw : raw.substring(0, 11980) + "...(已截断)";
        } catch (Exception ex) {
            return "{\"error\":\"序列化失败\"}";
        }
    }
}
