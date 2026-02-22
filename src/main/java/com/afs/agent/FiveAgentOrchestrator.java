package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 5-Agent closed-loop ad optimization orchestrator:
 *   1. MonitorAgent   — 0-100 health score & threshold alerts (CTR < 0.5%, high CPA, ROAS < 1.0)
 *   2. AudienceAgent  — High-value segment ranking & Lookalike expansion plan
 *   3. CreativeAgent  — 3-5 structured copy variants (headline + description + CTA + emotion) via LLM or fallback
 *   4. BiddingAgent   — eCPM = pCTR * pCVR * TargetCPA * 1000, dynamic multiplier (x1.3 / x0.6), <= 80% TargetCPA cap
 *   5. OptimizeAgent  — Pause low-score creatives (<40), convex budget reallocation (KKT water-filling),
 *                       ProcessMonitor emergency check, A/B test start/stop & bounded loop control
 */
@Service
class FiveAgentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(FiveAgentOrchestrator.class);

    private final CmsCampaignDataService cmsData;
    private final CmsReportSource scenarios;
    private final AiModelGateway gateway;
    private final AgentSafetyPolicyService safetyPolicy;
    private final AgentTelemetryService telemetry;
    private final ObjectMapper json;

    FiveAgentOrchestrator(CmsCampaignDataService cmsData, CmsReportSource scenarios,
                          AiModelGateway gateway, AgentSafetyPolicyService safetyPolicy,
                          AgentTelemetryService telemetry,
                          ObjectMapper json) {
        this.cmsData = cmsData;
        this.scenarios = scenarios;
        this.gateway = gateway;
        this.safetyPolicy = safetyPolicy;
        this.telemetry = telemetry;
        this.json = json;
    }

    // ---- Typed Agent Output Contracts ----
    record Alert(String code, String severity, String message) {}

    record MonitorReport(int healthScore, String grade, double ctrPct, double cvrPct,
                         double cpa, double roas, double targetCpa, List<Alert> alerts, String summary) {}

    record AudienceSegment(int rank, String segmentName, String region, String deviceHint, String ageGroup,
                           double spend, double conversions, double cpa, double roas, int valueScore, String recommendation) {}

    record LookalikePlan(String tier, String seedAudience, String estimatedReach, String bidStrategy) {}

    record AudienceReport(List<AudienceSegment> topSegments, List<LookalikePlan> lookalikePlans, String summary) {}

    record CreativeVariant(String id, String headline, String description, String cta,
                           String emotion, String angle, int predictedScore) {}

    record CreativeReport(String strategy, String triggerReason, String source,
                          List<CreativeVariant> variants) {}

    record BiddingReport(double pCtr, double pCvr, double targetCpa, double baseEcpm,
                         double dynamicMultiplier, String multiplierReason,
                         double rawBid, double maxAllowedBid, double recommendedBid,
                         boolean capApplied, double adjustedEcpm, String formulaText) {}

    record CreativeAuditItem(String creativeName, long impressions, long clicks, double ctrPct,
                             int score, String action, String reason) {}

    record BudgetAllocation(String campaignId, String name, String platform,
                            double currentBudget, double optimalBudget, double deltaBudget,
                            double deltaPct, double historicalRoas, String action) {}

    record AbTestAction(String testName, String status, String action, String rationale) {}

    record OptimizeReport(List<CreativeAuditItem> pausedCreatives,
                          List<BudgetAllocation> budgetAllocations,
                          double totalCurrentBudget, double totalOptimalBudget,
                          double expectedRevenueUpliftPct,
                          String processMonitorStatus, String processMonitorNote,
                          List<AbTestAction> abTestActions,
                          boolean shouldContinue, int currentIteration, int maxIterations, String loopReason) {}

    Map<String, Object> runClosedLoop(String campaignId, BigDecimal targetRoas, BigDecimal targetCpaOverride,
                                      boolean useLlmForCreative, int maxChangePct, int iteration) {
        BigDecimal tRoas = targetRoas == null ? new BigDecimal("1.25") : targetRoas;
        int cap = Math.max(5, Math.min(20, maxChangePct <= 0 ? 15 : maxChangePct));
        int curIter = Math.max(1, Math.min(3, iteration <= 0 ? 1 : iteration));

        CmsCampaignDataService.CampaignPackage pkg = resolvePackage(campaignId, tRoas, cap);
        var input = pkg.input();
        var evidence = pkg.evidence();

        // 1. MonitorAgent
        MonitorReport monitor = runMonitorAgent(input, tRoas, targetCpaOverride);

        // 2. AudienceAgent
        AudienceReport audience = runAudienceAgent(input, evidence, monitor);

        // 3. CreativeAgent (LLM with deterministic fallback)
        CreativeReport creative = runCreativeAgent(input, monitor, useLlmForCreative);

        // 4. BiddingAgent (eCPM + dynamic multiplier + 80% TargetCPA cap)
        BiddingReport bidding = runBiddingAgent(input, monitor, tRoas);

        // 5. OptimizeAgent (pause <40 creatives + convex budget reallocation + ProcessMonitor + A/B + loop)
        OptimizeReport optimize = runOptimizeAgent(input, evidence, monitor, bidding, cap, curIter);

        // Enrich all 5 agents with live LLM campaign-specific synthesis when configured
        if (useLlmForCreative && gateway.isConfigured()) {
            LlmFiveAgentSynthesis synth = synthesizeFiveAgentsWithLlm(input, monitor, audience, creative, bidding, optimize);
            if (synth != null) {
                if (synth.monitorSummary != null && !synth.monitorSummary.isBlank()) {
                    monitor = new MonitorReport(monitor.healthScore(), monitor.grade(), monitor.ctrPct(),
                            monitor.cvrPct(), monitor.cpa(), monitor.roas(), monitor.targetCpa(),
                            monitor.alerts(), synth.monitorSummary);
                }
                if (!synth.segments.isEmpty()) {
                    audience = new AudienceReport(synth.segments,
                            synth.lookalikes.isEmpty() ? audience.lookalikePlans() : synth.lookalikes,
                            synth.audienceSummary != null && !synth.audienceSummary.isBlank() ? synth.audienceSummary : audience.summary());
                }
                if (!synth.variants.isEmpty()) {
                    creative = new CreativeReport(creative.strategy(), creative.triggerReason(), "LLM_GENERATED", synth.variants);
                }
                if (synth.optimizeNote != null && !synth.optimizeNote.isBlank()) {
                    optimize = new OptimizeReport(
                            synth.audits.isEmpty() ? optimize.pausedCreatives() : synth.audits,
                            optimize.budgetAllocations(), optimize.totalCurrentBudget(), optimize.totalOptimalBudget(),
                            optimize.expectedRevenueUpliftPct(), optimize.processMonitorStatus(),
                            synth.optimizeNote, optimize.abTestActions(), optimize.shouldContinue(),
                            optimize.currentIteration(), optimize.maxIterations(), optimize.loopReason());
                }
            }
        }

        Map<String, Object> campInfo = new LinkedHashMap<>();
        campInfo.put("campaignId", input.campaignId());
        campInfo.put("site", input.site());
        campInfo.put("channel", input.channel());
        campInfo.put("currency", input.currency());
        campInfo.put("start", input.start().toString());
        campInfo.put("end", input.end().toString());
        campInfo.put("spend", input.spend());
        campInfo.put("revenue", input.revenue());
        campInfo.put("impressions", input.impressions());
        campInfo.put("clicks", input.clicks());
        campInfo.put("conversions", input.conversions());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("campaign", campInfo);
        out.put("monitor", monitor);
        out.put("audience", audience);
        out.put("creative", creative);
        out.put("bidding", bidding);
        out.put("optimize", optimize);
        out.put("safety", safetyPolicy.summary());
        return out;
    }

    /** Standalone convex budget reallocation across active portfolio campaigns. */
    Map<String, Object> solvePortfolioAllocation(BigDecimal totalBudgetOverride, int maxChangePct, int limit) {
        int cap = Math.max(5, Math.min(25, maxChangePct <= 0 ? 15 : maxChangePct));
        int maxItems = Math.max(3, Math.min(20, limit <= 0 ? 10 : limit));
        List<BudgetAllocation> allocations = computeConvexAllocations(totalBudgetOverride, cap, maxItems);
        double curSum = allocations.stream().mapToDouble(BudgetAllocation::currentBudget).sum();
        double optSum = allocations.stream().mapToDouble(BudgetAllocation::optimalBudget).sum();
        double upliftPct = estimateConvexUpliftPct(allocations);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("solver", "Convex Projected Water-Filling (Concave √B Revenue Model)");
        out.put("maxChangePct", cap);
        out.put("totalCurrentBudget", round2(curSum));
        out.put("totalOptimalBudget", round2(optSum));
        out.put("expectedRevenueUpliftPct", round2(upliftPct));
        out.put("allocations", allocations);
        return out;
    }

    // =========================================================================
    // Agent 1: MonitorAgent (0-100 Health Score + Alerts)
    // =========================================================================
    private MonitorReport runMonitorAgent(OptimizationWorkflow.Input input, BigDecimal targetRoas,
                                          BigDecimal targetCpaOverride) {
        double spend = input.spend().doubleValue();
        double revenue = input.revenue().doubleValue();
        long imp = Math.max(0L, input.impressions());
        long clicks = Math.max(0L, input.clicks());
        long conv = Math.max(0L, input.conversions());

        double ctr = imp > 0 ? (double) clicks / imp : 0.0;
        double cvr = clicks > 0 ? (double) conv / clicks : 0.0;
        double cpa = conv > 0 ? spend / conv : (spend > 0 ? spend : 0.0);
        double roas = spend > 0 ? revenue / spend : 0.0;
        double revPerConv = conv > 0 ? revenue / conv : 4.0;
        double targetCpa = targetCpaOverride != null && targetCpaOverride.signum() > 0
                ? targetCpaOverride.doubleValue()
                : Math.max(0.50, revPerConv / Math.max(0.5, targetRoas.doubleValue()));

        List<Alert> alerts = new ArrayList<>();
        int score = 100;

        // Rule 1: CTR < 0.5%
        if (ctr < 0.005) {
            score -= 22;
            alerts.add(new Alert("LOW_CTR", "HIGH",
                    "CTR " + round2(ctr * 100) + "% < 0.5% 警戒线，存在明显素材疲劳或首图吸引力不足"));
        } else if (ctr < 0.012) {
            score -= 10;
            alerts.add(new Alert("SOFT_CTR", "MEDIUM",
                    "CTR " + round2(ctr * 100) + "% 低于行业健康线 (1.2%)，建议启动紧迫感/好奇心新文案"));
        }

        // Rule 2: High CPA (above target CPA)
        if (cpa > targetCpa * 1.25) {
            score -= 24;
            alerts.add(new Alert("HIGH_CPA", "HIGH",
                    "当前 CPA $" + round2(cpa) + " 超出目标上限 ($" + round2(targetCpa) + ") 25% 以上"));
        } else if (cpa > targetCpa) {
            score -= 12;
            alerts.add(new Alert("ELEVATED_CPA", "MEDIUM",
                    "当前 CPA $" + round2(cpa) + " 略高于目标 CPA ($" + round2(targetCpa) + ")"));
        }

        // Rule 3: ROAS < 1.0
        if (roas < 1.0) {
            score -= 30;
            alerts.add(new Alert("LOW_ROAS", "HIGH",
                    "ROAS " + round2(roas) + "× < 1.00×，处于净亏损状态，需立即降价控本或触发止损"));
        } else if (roas < targetRoas.doubleValue()) {
            score -= 14;
            alerts.add(new Alert("BELOW_TARGET_ROAS", "MEDIUM",
                    "ROAS " + round2(roas) + "× 低于目标 " + round2(targetRoas.doubleValue()) + "×"));
        }

        // Rule 4: Low CVR
        if (cvr < 0.02) {
            score -= 14;
            alerts.add(new Alert("LOW_CVR", "MEDIUM",
                    "CVR " + round2(cvr * 100) + "% 偏低，落地页承接或受众意图匹配需优化"));
        }

        score = Math.max(5, Math.min(100, score));
        String grade = score >= 85 ? "S" : score >= 70 ? "A" : score >= 55 ? "B" : score >= 40 ? "C" : "D";
        String summary = "综合健康度 " + score + "/100 (" + grade + " 级) · 触发 " + alerts.size() + " 项告警";

        return new MonitorReport(score, grade, round2(ctr * 100), round2(cvr * 100),
                round2(cpa), round2(roas), round2(targetCpa), alerts, summary);
    }

    // =========================================================================
    // Agent 2: AudienceAgent (High-Value Segment Ranking + Lookalike Expansion)
    // =========================================================================
    private AudienceReport runAudienceAgent(OptimizationWorkflow.Input input,
                                            CmsReportSource.Campaign evidence,
                                            MonitorReport monitor) {
        List<AudienceSegment> segments = new ArrayList<>();
        double totalSpend = Math.max(1.0, input.spend().doubleValue());
        double totalRev = input.revenue().doubleValue();
        double avgRevPerConv = input.conversions() > 0 ? totalRev / input.conversions() : 3.5;

        if (evidence != null && evidence.segments() != null && !evidence.segments().isEmpty()) {
            List<CmsReportSource.Segment> raw = new ArrayList<>(evidence.segments());
            raw.sort(Comparator.comparing(CmsReportSource.Segment::spend).reversed());
            String[] devices = {"iOS 17+ / 高端移动端", "Android 旗舰机", "Desktop + Tablet", "全端移动流量"};
            String[] ages = {"25-44 岁高消费群体", "35-54 岁决策群体", "18-34 岁高频互动群", "45-64 岁精准搜索群"};
            int idx = 0;
            for (var s : raw.subList(0, Math.min(5, raw.size()))) {
                double segSpend = s.spend().doubleValue();
                long segConv = Math.max(0L, s.conversions());
                double segCpa = segConv > 0 ? segSpend / segConv : segSpend;
                double segRoas = segSpend > 0 ? (segConv * avgRevPerConv) / segSpend : monitor.roas();
                int valScore = (int) Math.max(25, Math.min(98, Math.round(50 + (segRoas - 1.0) * 35)));
                String rec = segRoas >= 1.25 ? "核心放量人群（提高预算权重）"
                        : segRoas >= 1.0 ? "稳定维持并做 Lookalike 种子"
                        : "收缩出价或排除低转化设备";
                segments.add(new AudienceSegment(0, s.name() + " · " + ages[idx % ages.length],
                        s.name(), devices[idx % devices.length], ages[idx % ages.length],
                        round2(segSpend), segConv, round2(segCpa), round2(segRoas), valScore, rec));
                idx++;
            }
        } else {
            // Deterministic cohort synthesis when only campaign summary is present
            double[][] weights = {{0.42, 1.18}, {0.28, 1.04}, {0.18, 0.91}, {0.12, 0.74}};
            String[][] meta = {
                    {"US 核心搜索意图群", "US", "iOS 17+ 移动端", "25-44 岁高客单"},
                    {"CA/UK 英语高净值群", "CA/UK", "Desktop + iOS", "35-54 岁决策群"},
                    {"AU/DE 跨端兴趣群", "AU/DE", "Android 旗舰", "25-44 岁职场群"},
                    {"泛流量冷启动受众", "Tier-2", "全端移动流量", "18-24 岁泛兴趣"}
            };
            for (int i = 0; i < weights.length; i++) {
                double segSpend = totalSpend * weights[i][0];
                double segRoas = monitor.roas() * weights[i][1];
                long segConv = Math.max(1L, Math.round((segSpend * segRoas) / Math.max(0.5, avgRevPerConv)));
                double segCpa = segSpend / segConv;
                int valScore = (int) Math.max(25, Math.min(98, Math.round(50 + (segRoas - 1.0) * 35)));
                String rec = segRoas >= 1.25 ? "核心放量人群（优先扩量）"
                        : segRoas >= 1.0 ? "维持投放并提取 1% 种子"
                        : "降价 20% 或排除低效版位";
                segments.add(new AudienceSegment(0, meta[i][0], meta[i][1], meta[i][2], meta[i][3],
                        round2(segSpend), segConv, round2(segCpa), round2(segRoas), valScore, rec));
            }
        }

        segments.sort(Comparator.comparingInt(AudienceSegment::valueScore).reversed());
        List<AudienceSegment> ranked = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            var s = segments.get(i);
            ranked.add(new AudienceSegment(i + 1, s.segmentName(), s.region(), s.deviceHint(), s.ageGroup(),
                    s.spend(), s.conversions(), s.cpa(), s.roas(), s.valueScore(), s.recommendation()));
        }

        String topSeed = ranked.isEmpty() ? "高 ROAS 转化用户" : ranked.get(0).segmentName();
        List<LookalikePlan> lookalikes = List.of(
                new LookalikePlan("1% Lookalike (高精核心)", topSeed + "（近 30 天 Top 20% 变现用户）",
                        "120万 ~ 240万", "优先出价 +10%，承接高确定性放量"),
                new LookalikePlan("3% Lookalike (均衡扩量)", topSeed + " + 深度浏览停留 > 45s 访客",
                        "350万 ~ 680万", "目标 CPA 出价，配合利益点创意测试"),
                new LookalikePlan("5% Lookalike (广泛探索)", "全站 AdSense 点击高 RPC 用户池",
                        "800万+", "保守出价（上限 70% TargetCPA），仅分配 15% 探索预算")
        );

        String summary = "已按 ROI 与转化效率排序 " + ranked.size() + " 个高价值人群段，推荐以「" + topSeed + "」为种子扩展 1%/3%/5% Lookalike。";
        return new AudienceReport(ranked, lookalikes, summary);
    }

    // =========================================================================
    // Agent 3: CreativeAgent (CTR/CVR-Driven Strategy + 3-5 Copy Variants)
    // =========================================================================
    private CreativeReport runCreativeAgent(OptimizationWorkflow.Input input,
                                            MonitorReport monitor,
                                            boolean useLlm) {
        String strategy;
        String trigger;
        if (monitor.ctrPct() < 1.2) {
            strategy = "紧迫感 + 好奇心驱动 (Urgency & Curiosity)";
            trigger = "当前 CTR (" + monitor.ctrPct() + "%) 偏低，优先通过稀缺性、反常识提问与强视觉钩子拉升点击率";
        } else if (monitor.cvrPct() < 3.5) {
            strategy = "核心利益点 + 信任感背书 (Benefit & Trust)";
            trigger = "当前 CVR (" + monitor.cvrPct() + "%) 偏低，优先强化明确省钱/收益量化、权威对比与零风险保障";
        } else {
            strategy = "场景共鸣 + 社会认同扩量 (Social Proof & Scale)";
            trigger = "CTR (" + monitor.ctrPct() + "%) 与 CVR (" + monitor.cvrPct() + "%) 均衡健康，生成多角度变体支撑放量 A/B 测试";
        }

        if (useLlm && gateway.isConfigured()) {
            // Handled by unified synthesizeFiveAgentsWithLlm in runClosedLoop to avoid duplicate LLM round-trips
        }

        List<CreativeVariant> fallback = buildDeterministicVariants(input, monitor);
        return new CreativeReport(strategy, trigger, "DETERMINISTIC_FALLBACK", fallback);
    }

    private record LlmFiveAgentSynthesis(
            String monitorSummary,
            String audienceSummary,
            List<AudienceSegment> segments,
            List<LookalikePlan> lookalikes,
            List<CreativeVariant> variants,
            List<CreativeAuditItem> audits,
            String optimizeNote) {}

    private LlmFiveAgentSynthesis synthesizeFiveAgentsWithLlm(OptimizationWorkflow.Input input,
                                                              MonitorReport monitor,
                                                              AudienceReport baseAud,
                                                              CreativeReport baseCr,
                                                              BiddingReport bidding,
                                                              OptimizeReport baseOpt) {
        long start = System.currentTimeMillis();
        try {
            var resolved = gateway.resolve();
            if (resolved == null) return null;
            var campSummary = cmsData.listCampaigns(input.campaignId(), "", 5).stream()
                    .filter(c -> input.campaignId().equals(c.campaignId()))
                    .findFirst().orElse(null);
            String campName = campSummary != null && campSummary.name() != null ? campSummary.name() : input.campaignId();
            String platform = campSummary != null && campSummary.platform() != null ? campSummary.platform() : input.channel();
            String country = campSummary != null && campSummary.country() != null ? campSummary.country() : "US";

            String prompt = """
                    你是五智能体广告优化引擎（Monitor/Audience/Creative/Bidding/Optimize）。请针对以下真实在投广告活动，输出定制化的五智能体分析 JSON（严格只输出 JSON 对象，不要 Markdown 代码块）：
                    - 活动ID：%s，活动名称：%s，投放平台：%s，国家/地区：%s
                    - 花费：$%.2f，收入：$%.2f，CTR：%.2f%%，CVR：%.2f%%，CPA：$%.2f，ROAS：%.2fx，健康分：%d/100
                    - 创意策略：%s，建议 eCPM 出价：$%.2f（倍率 ×%.2f）
                    请紧密围绕「%s」（注意识别名称中的地区如 US/AU/CA/DE/GB/FR 和业务品类如 DentalImplants/LasikVision/HearingAids/ClearAligners/TravelCard/HighYieldSavings 等），输出以下 JSON 结构：
                    {
                      "monitorSummary": "针对该活动具体指标与品类的1句精准监控诊断（50字内）",
                      "audienceSummary": "针对该地区与品类的受众分层结论（50字内）",
                      "segments": [
                        {"segmentName":"结合该品类与国家的具体人群名（共4个）","region":"%s","deviceHint":"终端设备","ageGroup":"年龄层","recommendation":"具体投放建议"}
                      ],
                      "lookalikes": [
                        {"tier":"1%% Lookalike (高精核心)","seedAudience":"结合该品类的具体种子人群描述","estimatedReach":"覆盖规模","bidStrategy":"出价策略"},
                        {"tier":"3%% Lookalike (均衡扩量)","seedAudience":"结合该品类的扩展人群描述","estimatedReach":"覆盖规模","bidStrategy":"出价策略"},
                        {"tier":"5%% Lookalike (广泛探索)","seedAudience":"结合该品类的探索人群描述","estimatedReach":"覆盖规模","bidStrategy":"出价策略"}
                      ],
                      "variants": [
                        {"id":"CR-01","headline":"结合该商品品类的专属广告标题（共4条）","description":"高转化广告正文描述","cta":"行动号召","emotion":"情绪标签","angle":"切入角度","predictedScore":88}
                      ],
                      "audits": [
                        {"creativeName":"结合该品类的在投素材名（共3条，其中第3条为低分需暂停素材）","ActionHint":"KEEP或PAUSE","reason":"具体保留或暂停原因"}
                      ],
                      "optimizeNote": "结合出价与预算再分配的1句综合执行建议（60字内）"
                    }
                    """.formatted(input.campaignId(), campName, platform, country,
                    input.spend().doubleValue(), input.revenue().doubleValue(),
                    monitor.ctrPct(), monitor.cvrPct(), monitor.cpa(), monitor.roas(), monitor.healthScore(),
                    baseCr.strategy(), bidding.recommendedBid(), bidding.dynamicMultiplier(),
                    campName, country);

            var call = gateway.chat(resolved, List.of(UserMessage.from(prompt)), List.of());
            long dur = System.currentTimeMillis() - start;
            String raw = (call.message() == null || call.message().text() == null) ? "" : call.message().text();
            telemetry.recordNode(null, null, "afs_five_agent_loop", "five_agent_llm_synthesis", "OK",
                    resolved.chatModel(), call.inputTokens(), call.outputTokens(), 0, dur,
                    "五智能体实时生成：" + campName);

            int sIdx = raw.indexOf('{');
            int eIdx = raw.lastIndexOf('}');
            if (sIdx < 0 || eIdx <= sIdx) return null;
            JsonNode root = json.readTree(raw.substring(sIdx, eIdx + 1));

            List<AudienceSegment> segs = new ArrayList<>();
            JsonNode segArr = root.path("segments");
            List<AudienceSegment> baseSegs = baseAud.topSegments();
            if (segArr.isArray()) {
                for (int i = 0; i < Math.min(segArr.size(), baseSegs.size()); i++) {
                    JsonNode n = segArr.get(i);
                    AudienceSegment b = baseSegs.get(i);
                    String sName = n.path("segmentName").asText("").trim();
                    if (sName.isEmpty()) continue;
                    segs.add(new AudienceSegment(
                            i + 1, sName,
                            n.path("region").asText(b.region()),
                            n.path("deviceHint").asText(b.deviceHint()),
                            n.path("ageGroup").asText(b.ageGroup()),
                            b.spend(), b.conversions(), b.cpa(), b.roas(), b.valueScore(),
                            n.path("recommendation").asText(b.recommendation())));
                }
            }

            List<LookalikePlan> lals = new ArrayList<>();
            JsonNode lalArr = root.path("lookalikes");
            if (lalArr.isArray()) {
                for (JsonNode n : lalArr) {
                    String seed = n.path("seedAudience").asText("").trim();
                    if (seed.isEmpty()) continue;
                    lals.add(new LookalikePlan(
                            n.path("tier").asText("1% Lookalike"),
                            seed,
                            n.path("estimatedReach").asText("150万 ~ 300万"),
                            n.path("bidStrategy").asText("目标 CPA 出价")));
                }
            }

            List<CreativeVariant> vars = new ArrayList<>();
            JsonNode varArr = root.path("variants");
            if (varArr.isArray()) {
                int idx = 1;
                for (JsonNode n : varArr) {
                    String hl = n.path("headline").asText("").trim();
                    String desc = n.path("description").asText("").trim();
                    if (hl.isEmpty() || desc.isEmpty()) continue;
                    vars.add(new CreativeVariant(
                            n.path("id").asText("CR-" + idx), hl, desc,
                            n.path("cta").asText("立即查看"),
                            n.path("emotion").asText("高转化"),
                            n.path("angle").asText(baseCr.strategy()),
                            Math.max(55, Math.min(96, n.path("predictedScore").asInt(85)))));
                    idx++;
                }
            }

            List<CreativeAuditItem> audits = new ArrayList<>();
            JsonNode audArr = root.path("audits");
            List<CreativeAuditItem> baseAudits = baseOpt.pausedCreatives();
            if (audArr.isArray()) {
                for (int i = 0; i < Math.min(audArr.size(), baseAudits.size()); i++) {
                    JsonNode n = audArr.get(i);
                    CreativeAuditItem b = baseAudits.get(i);
                    String cName = n.path("creativeName").asText("").trim();
                    if (cName.isEmpty()) continue;
                    audits.add(new CreativeAuditItem(
                            cName, b.impressions(), b.clicks(), b.ctrPct(), b.score(), b.action(),
                            n.path("reason").asText(b.reason())));
                }
            }

            return new LlmFiveAgentSynthesis(
                    root.path("monitorSummary").asText(""),
                    root.path("audienceSummary").asText(""),
                    segs, lals, vars, audits,
                    root.path("optimizeNote").asText(""));
        } catch (Exception ex) {
            log.warn("FiveAgent LLM synthesis fallback: {}", ex.getMessage());
            return null;
        }
    }

    private List<CreativeVariant> generateVariantsWithLlm(OptimizationWorkflow.Input input,
                                                          MonitorReport monitor,
                                                          String strategy) throws Exception {
        String prompt = """
                你是海外广告创意专家（Creative Agent）。请针对以下广告活动生成 4 条高转化广告文案变体，严格输出 JSON 数组，不要 Markdown 代码块。
                活动 ID：%s，站点：%s，当前 CTR：%.2f%%，CVR：%.2f%%，ROAS：%.2fx。
                指定创意策略：%s。
                每条元素必须包含字段：id, headline, description, cta, emotion, angle, predictedScore(60-95整数)。
                """.formatted(input.campaignId(), input.site(), monitor.ctrPct(), monitor.cvrPct(), monitor.roas(), strategy);
        var resolved = gateway.resolve();
        if (resolved == null) return List.of();
        var call = gateway.chat(resolved, List.of(UserMessage.from(prompt)), List.of());
        String raw = (call.message() == null || call.message().text() == null) ? "" : call.message().text();
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) return List.of();
        JsonNode arr = json.readTree(raw.substring(start, end + 1));
        List<CreativeVariant> list = new ArrayList<>();
        int idx = 1;
        for (JsonNode n : arr) {
            String headline = n.path("headline").asText("").trim();
            String desc = n.path("description").asText("").trim();
            if (headline.isEmpty() || desc.isEmpty()) continue;
            list.add(new CreativeVariant(
                    n.path("id").asText("CR-" + idx),
                    headline,
                    desc,
                    n.path("cta").asText("立即查看"),
                    n.path("emotion").asText("好奇心"),
                    n.path("angle").asText(strategy),
                    Math.max(55, Math.min(96, n.path("predictedScore").asInt(82)))
            ));
            idx++;
            if (list.size() >= 5) break;
        }
        return list;
    }

    private List<CreativeVariant> buildDeterministicVariants(OptimizationWorkflow.Input input, MonitorReport monitor) {
        String site = input.site();
        if (monitor.ctrPct() < 1.2) {
            return List.of(
                    new CreativeVariant("CR-URG-01", "最后 48 小时：为什么 87% 的人都在悄悄换掉旧方案？",
                            "针对 " + site + " 热门趋势的 2026 实测对比，3 分钟看清省下 40% 成本的关键细节。",
                            "立即查看对比", "紧迫感", "稀缺窗口 + 痛点钩子", 88),
                    new CreativeVariant("CR-CUR-02", "别再盲选了！内行人绝不会告诉你的 3 个隐藏选项",
                            "深度拆解真实用户反馈与费率陷阱，点击解锁完整榜单与避坑清单。",
                            "解锁完整清单", "好奇心", "反常识悬念 + 信息差", 85),
                    new CreativeVariant("CR-URG-03", "2026 新规生效前必看：现在申请立享专属资格",
                            "实时更新各地区最新政策与精选推荐，名额有限，点击秒查是否符合条件。",
                            "立即测算资格", "紧迫感", "时效政策 + 零门槛动作", 83),
                    new CreativeVariant("CR-CUR-04", "同样预算差距 2 倍？实测这 5 个高性价比方案",
                            "一图看懂核心参数与真实口碑评分，帮你 60 秒做出最优选择。",
                            "查看 Top 5 榜单", "好奇心", "数据反差 + 快速决策", 81)
            );
        } else if (monitor.cvrPct() < 3.5) {
            return List.of(
                    new CreativeVariant("CR-BEN-01", "透明报价零隐藏费用：一步锁定年度最高性价比方案",
                            "精选 " + site + " 官方核验合作资源，清晰对比每一项权益与真实节省金额。",
                            "免费获取报价", "利益点", "量化收益 + 价格透明", 89),
                    new CreativeVariant("CR-TRU-02", "超 120,000 名真实用户 4.9★ 推荐的安心之选",
                            "第三方独立评测机构背书，全程隐私加密保护，不满意随时可取消。",
                            "查看真实评价", "信任感", "权威背书 + 风险反转", 86),
                    new CreativeVariant("CR-BEN-03", "3 步极速匹配：为您量身筛选最省钱的本地精选",
                            "无需繁琐注册，输入基本需求即可实时比价，平均节省 32% 支出。",
                            "立即免费匹配", "利益点", "极简路径 + 即时回报", 84),
                    new CreativeVariant("CR-TRU-04", "2026 权威评测榜：哪些服务商真正值得长期信赖？",
                            "基于真实理赔/售后速度与费用透明度综合打分，助您零踩坑决策。",
                            "查看权威榜单", "信任感", "专业评测 + 理性说服", 82)
            );
        }
        return List.of(
                new CreativeVariant("CR-SCL-01", "2026 口碑爆款榜：大家都在选的 Top 5 高分方案",
                        "汇聚 " + site + " 高转化爆款主题，真实用户实测体验与省钱攻略一次看全。",
                        "立即探索榜单", "社会认同", "爆款趋势 + 群体信任", 90),
                new CreativeVariant("CR-SCL-02", "只需 2 分钟：找到最适合您的专属升级方案",
                        "结合最新价格趋势与专家建议，快速锁定高回报选择。",
                        "马上开始对比", "场景共鸣", "高效省心 + 专属定制", 87),
                new CreativeVariant("CR-SCL-03", "还在纠结选哪家？这份 2026 终极指南帮你直接省心",
                        "逐项拆解核心优缺点与适用人群，点击即看完整对比表。",
                        "获取对比指南", "信任感", "干货指南 + 决策减负", 85),
                new CreativeVariant("CR-SCL-04", "限时精选福利：本季度最值得关注的 3 大新选择",
                        "编辑团队实测筛选，覆盖高性价比与高服务评分两大维度。",
                        "查看精选详情", "紧迫感", "季度上新 + 精选稀缺", 83)
        );
    }

    // =========================================================================
    // Agent 4: BiddingAgent (eCPM = pCTR * pCVR * TargetCPA * 1000 + 80% Cap)
    // =========================================================================
    private BiddingReport runBiddingAgent(OptimizationWorkflow.Input input,
                                          MonitorReport monitor,
                                          BigDecimal targetRoas) {
        double pCtr = Math.max(0.002, monitor.ctrPct() / 100.0);
        double pCvr = Math.max(0.005, monitor.cvrPct() / 100.0);
        double targetCpa = monitor.targetCpa();
        double baseEcpm = pCtr * pCvr * targetCpa * 1000.0;

        double roas = monitor.roas();
        double tRoas = targetRoas.doubleValue();
        double multiplier;
        String multReason;
        if (roas >= tRoas * 1.20) {
            multiplier = 1.30;
            multReason = "ROAS (" + round2(roas) + "×) 超出目标 20%+ → 提价争量 (×1.30)";
        } else if (roas < tRoas * 0.85 || roas < 1.0) {
            multiplier = 0.60;
            multReason = "ROAS (" + round2(roas) + "×) 显著低于目标/亏损 → 降价控本 (×0.60)";
        } else if (roas < tRoas) {
            multiplier = 0.85;
            multReason = "ROAS (" + round2(roas) + "×) 略低于目标 → 温和压价 (×0.85)";
        } else {
            multiplier = 1.00;
            multReason = "ROAS (" + round2(roas) + "×) 达标平稳 → 维持基准出价 (×1.00)";
        }

        double rawBid = targetCpa * 0.65 * multiplier;
        double maxAllowedBid = targetCpa * 0.80; // Hard constraint: Bid <= 80% of TargetCPA
        boolean capApplied = rawBid > maxAllowedBid;
        double recommendedBid = Math.min(rawBid, maxAllowedBid);
        double adjustedEcpm = pCtr * pCvr * (recommendedBid / 0.65) * 1000.0;

        String formula = String.format(Locale.US,
                "eCPM = %.4f (pCTR) × %.4f (pCVR) × $%.2f (TargetCPA) × 1000 = $%.2f → 动态倍率 ×%.2f → 建议出价 $%.2f（硬上限 ≤ 80%% TargetCPA = $%.2f）",
                pCtr, pCvr, targetCpa, baseEcpm, multiplier, recommendedBid, maxAllowedBid);

        return new BiddingReport(round4(pCtr), round4(pCvr), round2(targetCpa), round2(baseEcpm),
                multiplier, multReason, round2(rawBid), round2(maxAllowedBid), round2(recommendedBid),
                capApplied, round2(adjustedEcpm), formula);
    }

    // =========================================================================
    // Agent 5: OptimizeAgent (Pause <40 + Convex Budget Allocation + ProcessMonitor + A/B + Loop)
    // =========================================================================
    private OptimizeReport runOptimizeAgent(OptimizationWorkflow.Input input,
                                            CmsReportSource.Campaign evidence,
                                            MonitorReport monitor,
                                            BiddingReport bidding,
                                            int maxChangePct,
                                            int curIter) {
        // 5.1 Creative Audit (pause creatives with composite score < 40)
        List<CreativeAuditItem> creativeAudits = new ArrayList<>();
        if (evidence != null && evidence.creative() != null && !evidence.creative().isEmpty()) {
            for (var c : evidence.creative()) {
                double cCtr = c.impressions() > 0 ? (double) c.clicks() / c.impressions() : 0.0;
                int cScore = (int) Math.max(15, Math.min(95, Math.round((cCtr / 0.015) * 65 + (monitor.roas() >= 1.0 ? 20 : 5))));
                boolean pause = cScore < 40;
                creativeAudits.add(new CreativeAuditItem(c.name(), c.impressions(), c.clicks(),
                        round2(cCtr * 100), cScore,
                        pause ? "PAUSE" : "KEEP",
                        pause ? "综合评分 " + cScore + " < 40 分，建议立即暂停并替换为新变体" : "表现达标，继续投放"));
            }
        } else {
            long baseImp = Math.max(5000L, input.impressions());
            long baseClk = Math.max(50L, input.clicks());
            double baseCtr = (double) baseClk / baseImp;
            Object[][] samples = {
                    {"主图变体 A · 场景对比图", 0.52, 1.25},
                    {"主图变体 B · 利益点卡片", 0.31, 0.92},
                    {"旧版素材 C · 泛通用图文", 0.17, 0.38}
            };
            for (Object[] s : samples) {
                long imp = Math.round(baseImp * (double) s[1]);
                long clk = Math.max(1L, Math.round(imp * baseCtr * (double) s[2]));
                double cCtr = (double) clk / Math.max(1L, imp);
                int cScore = (int) Math.max(18, Math.min(95, Math.round((cCtr / 0.015) * 60 + 18 * (double) s[2])));
                boolean pause = cScore < 40;
                creativeAudits.add(new CreativeAuditItem((String) s[0], imp, clk, round2(cCtr * 100), cScore,
                        pause ? "PAUSE" : "KEEP",
                        pause ? "综合评分 " + cScore + " < 40 分（严重衰减），建议立即暂停" : "点击效率健康，保留在投"));
            }
        }

        // 5.2 Convex Budget Reallocation (Concave Revenue Maximization via Projected KKT Water-Filling)
        List<BudgetAllocation> allocations = computeConvexAllocations(null, maxChangePct, 8);
        double totalCur = allocations.stream().mapToDouble(BudgetAllocation::currentBudget).sum();
        double totalOpt = allocations.stream().mapToDouble(BudgetAllocation::optimalBudget).sum();
        double upliftPct = estimateConvexUpliftPct(allocations);

        // 5.3 ProcessMonitor (Emergency response)
        var safety = safetyPolicy.current();
        String pmStatus;
        String pmNote;
        if (safety.killSwitch()) {
            pmStatus = "EMERGENCY_LOCKED";
            pmNote = "全局 Kill Switch 已处于熔断锁定状态，所有预算写操作已中止。";
        } else if (monitor.healthScore() < 40 || monitor.roas() < 0.75) {
            pmStatus = "CRITICAL_ALERT";
            pmNote = "触发紧急止损预警（健康度 " + monitor.healthScore() + " / ROAS " + monitor.roas()
                    + "×），建议立即降价 ×" + bidding.dynamicMultiplier() + " 或开启熔断保护。";
        } else if (monitor.healthScore() < 65) {
            pmStatus = "WATCH";
            pmNote = "指标轻度波动，建议按建议出价 $" + bidding.recommendedBid() + " 观察 24 小时。";
        } else {
            pmStatus = "HEALTHY";
            pmNote = "投放进程健康，处于安全护栏内，可按凸优化方案稳步推进。";
        }

        // 5.4 A/B Test Start / Stop Management
        List<AbTestAction> abActions = new ArrayList<>();
        boolean hasPaused = creativeAudits.stream().anyMatch(c -> "PAUSE".equals(c.action()));
        if (hasPaused) {
            abActions.add(new AbTestAction("EXP-CREATIVE-REFRESH", "READY_TO_START", "START",
                    "检测到评分 < 40 的低效素材，启动 20% 流量 A/B 测试导入 CreativeAgent 生成的新文案变体"));
        }
        abActions.add(new AbTestAction("EXP-BID-DYNAMIC-" + input.campaignId(),
                bidding.dynamicMultiplier() != 1.0 ? "READY_TO_START" : "OBSERVING",
                bidding.dynamicMultiplier() != 1.0 ? "START" : "HOLD",
                "出价倍率 ×" + bidding.dynamicMultiplier() + "（目标出价 $" + bidding.recommendedBid() + "）对照原出价分组验证 72h"));
        if (monitor.roas() < 0.85) {
            abActions.add(new AbTestAction("EXP-BROAD-AUDIENCE", "BLEEDING", "STOP",
                    "当前整体 ROAS 低于 0.85×，立即停止宽泛受众探索实验，回笼预算至 1% Lookalike 核心组"));
        }

        // 5.5 Closed-Loop Iteration Controller (Bounded: max 3 iterations)
        int maxIterations = 3;
        boolean hasHighAlerts = monitor.alerts().stream().anyMatch(a -> "HIGH".equals(a.severity()));
        boolean shouldContinue = hasHighAlerts && curIter < maxIterations && !safety.killSwitch();
        String loopReason = safety.killSwitch()
                ? "全局熔断已开启，终止自动闭环迭代"
                : !hasHighAlerts
                ? "无高危告警（健康度 " + monitor.healthScore() + " 分），当前轮次收敛结束"
                : curIter >= maxIterations
                ? "已达最大有界迭代轮次 (" + maxIterations + "/" + maxIterations + ")，转交人工审批决策"
                : "存在高危告警，执行本轮调优并观察 24h 后进入第 " + (curIter + 1) + "/" + maxIterations + " 轮闭环复检";

        return new OptimizeReport(creativeAudits, allocations, round2(totalCur), round2(totalOpt),
                round2(upliftPct), pmStatus, pmNote, abActions, shouldContinue, curIter, maxIterations, loopReason);
    }

    /**
     * Solves the convex budget reallocation problem:
     *   maximize   Σ_i  w_i * sqrt(B_i)
     *   subject to Σ_i  B_i = TotalBudget
     *              (1 - cap) * B_i0 <= B_i <= (1 + cap) * B_i0
     * where w_i = roas_i * sqrt(B_i0) captures each campaign's marginal revenue productivity.
     * Exact KKT solution via bisection on Lagrange multiplier λ: B_i*(λ) = clip((w_i / (2λ))^2, low_i, high_i).
     */
    private List<BudgetAllocation> computeConvexAllocations(BigDecimal totalBudgetOverride, int maxChangePct, int limit) {
        record Cand(String id, String name, String platform, double b0, double roas, double weight, double lo, double hi) {}
        List<Cand> cands = new ArrayList<>();
        double capFrac = maxChangePct / 100.0;

        if (cmsData.isLocalWorkspace()) {
            for (var c : cmsData.listCampaigns(null, null, limit * 2)) {
                double b0 = c.observedBudget() != null && c.observedBudget().signum() > 0
                        ? c.observedBudget().doubleValue()
                        : Math.max(20.0, c.spend().doubleValue() / 14.0);
                double roas = c.roas() != null ? Math.max(0.25, c.roas().doubleValue()) : 1.0;
                double w = roas * Math.sqrt(b0);
                cands.add(new Cand(c.campaignId(), c.name(), c.platform(), b0, roas, w,
                        b0 * (1.0 - capFrac), b0 * (1.0 + capFrac)));
                if (cands.size() >= limit) break;
            }
        } else {
            for (var c : scenarios.campaigns()) {
                double b0 = c.currentBudget() != null ? c.currentBudget().doubleValue() : 100.0;
                var inp = c.toInput();
                double roas = inp.spend().signum() > 0 ? inp.revenue().doubleValue() / inp.spend().doubleValue() : 1.0;
                double w = Math.max(0.25, roas) * Math.sqrt(b0);
                cands.add(new Cand(c.campaignId(), c.id(), "Simulation", b0, roas, w,
                        b0 * (1.0 - capFrac), b0 * (1.0 + capFrac)));
            }
        }
        if (cands.isEmpty()) return List.of();

        double sumB0 = cands.stream().mapToDouble(Cand::b0).sum();
        double minTotal = cands.stream().mapToDouble(Cand::lo).sum();
        double maxTotal = cands.stream().mapToDouble(Cand::hi).sum();
        double targetTotal = totalBudgetOverride != null && totalBudgetOverride.signum() > 0
                ? Math.max(minTotal, Math.min(maxTotal, totalBudgetOverride.doubleValue()))
                : sumB0;

        // Bisection on Lagrange multiplier λ > 0 (monotone decreasing sum(B_i*(λ)))
        double loLam = 1e-6;
        double hiLam = 1e6;
        for (int iter = 0; iter < 60; iter++) {
            double mid = 0.5 * (loLam + hiLam);
            double sum = 0.0;
            for (Cand c : cands) {
                double opt = Math.pow(c.weight() / (2.0 * mid), 2.0);
                sum += Math.max(c.lo(), Math.min(c.hi(), opt));
            }
            if (sum > targetTotal) loLam = mid;
            else hiLam = mid;
        }
        double lam = 0.5 * (loLam + hiLam);

        List<BudgetAllocation> results = new ArrayList<>(cands.size());
        for (Cand c : cands) {
            double opt = Math.max(c.lo(), Math.min(c.hi(), Math.pow(c.weight() / (2.0 * lam), 2.0)));
            double delta = opt - c.b0();
            double deltaPct = c.b0() > 0 ? (delta / c.b0()) * 100.0 : 0.0;
            String action = deltaPct > 1.5 ? "SCALE" : deltaPct < -1.5 ? "REDUCE" : "HOLD";
            results.add(new BudgetAllocation(c.id(), c.name(), c.platform(),
                    round2(c.b0()), round2(opt), round2(delta), round2(deltaPct), round2(c.roas()), action));
        }
        results.sort(Comparator.comparingDouble(BudgetAllocation::deltaBudget).reversed());
        return results;
    }

    private double estimateConvexUpliftPct(List<BudgetAllocation> allocations) {
        double revBefore = 0.0;
        double revAfter = 0.0;
        for (var a : allocations) {
            double b0 = Math.max(0.01, a.currentBudget());
            double b1 = Math.max(0.01, a.optimalBudget());
            double w = a.historicalRoas() * Math.sqrt(b0);
            revBefore += w * Math.sqrt(b0);
            revAfter += w * Math.sqrt(b1);
        }
        return revBefore > 0 ? ((revAfter - revBefore) / revBefore) * 100.0 : 0.0;
    }

    private CmsCampaignDataService.CampaignPackage resolvePackage(String campaignId, BigDecimal targetRoas, int maxChangePct) {
        if (campaignId != null && !campaignId.isBlank()) {
            return cmsData.buildPackageForCampaign(campaignId, targetRoas, new BigDecimal("20.00"), 30, maxChangePct);
        }
        var list = cmsData.listCampaigns(null, null, 1);
        if (!list.isEmpty()) {
            return cmsData.buildPackageForCampaign(list.get(0).campaignId(), targetRoas, new BigDecimal("20.00"), 30, maxChangePct);
        }
        var first = scenarios.campaigns().get(0);
        return new CmsCampaignDataService.CampaignPackage(first.toInput(), first);
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
