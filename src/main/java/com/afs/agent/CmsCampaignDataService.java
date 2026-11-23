package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Grounded read-only campaign, daily performance, geo, creative, rule, peer-benchmark,
 * anomaly-scanning, and episodic-memory data provider for the LangGraph4j agents.
 * Works seamlessly in both {@code local-cms} (real CMS database) and {@code agent-sim} (synthetic scenarios).
 */
@Service
class CmsCampaignDataService {
    private static final Logger log = LoggerFactory.getLogger(CmsCampaignDataService.class);

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final CmsReportSource scenarios;
    private final boolean localWorkspace;

    CmsCampaignDataService(JdbcTemplate db, ObjectMapper json, CmsReportSource scenarios,
                           @Value("${afs.local-workspace:false}") boolean localWorkspace) {
        this.db = db;
        this.json = json;
        this.scenarios = scenarios;
        this.localWorkspace = localWorkspace;
    }

    boolean isLocalWorkspace() {
        return localWorkspace;
    }

    boolean hasPlatformObservation(CampaignSummary campaign, LocalDate date) {
        if(!localWorkspace) return scenarios.campaigns().stream().anyMatch(c->c.campaignId().equals(campaign.campaignId()) && c.daily().stream().anyMatch(d->d.date().equals(date)));
        String table=switch(campaign.platform()==null?"":campaign.platform()) {
            case "TikTok" -> "tiktok_data"; case "Google" -> "google_data";
            case "Facebook","Meta" -> "meta_data"; case "Taboola" -> "taboola_data";default -> "";
        };
        if(table.isEmpty())return false;
        Integer rows=db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE `date`=? AND (campaign_id=? OR (?<>'' AND channel_id=?)) AND conversion IS NOT NULL",Integer.class,date.toString(),campaign.campaignId(),campaign.channel()==null?"":campaign.channel(),campaign.channel()==null?"":campaign.channel());
        return rows!=null&&rows>0;
    }

    /** Lists campaigns available for 1-click AI optimization or Copilot search. */
    List<CampaignSummary> listCampaigns(String query, String platform, int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 30 : limit, 250));
        if (!localWorkspace) {
            return scenarios.campaigns().stream()
                    .map(this::fromScenario)
                    .filter(item -> matches(item, query, platform))
                    .limit(safeLimit)
                    .toList();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT a.ad_resource_id, a.campaign, a.title, a.channel_id, a.ad_platform, a.country,
                       a.state, a.rule_id, a.ad_spend, a.gross_revenue, a.profit, a.roi,
                       COALESCE(r.name, '') AS rule_name, COALESCE(r.budget_change, 0) AS budget_change,
                       COALESCE((SELECT MIN(rc.site) FROM report_data_config rc WHERE rc.channel = a.channel_id),
                                (SELECT MIN(ar.site) FROM article_info ar WHERE ar.article_id = a.article_id),
                                'local') AS site_code,
                       COALESCE((SELECT MAX(t.budget) FROM tiktok_ad_group_info t WHERE t.ad_resource_id = a.ad_resource_id), 0) AS observed_budget
                FROM ad_resource a
                LEFT JOIN ad_resource_rule r ON a.rule_id = r.id AND r.status = 1
                WHERE a.status = 1
                """);
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String like = "%" + query.strip().toLowerCase(Locale.ROOT) + "%";
            sql.append(" AND (LOWER(a.ad_resource_id) LIKE ? OR LOWER(a.campaign) LIKE ? OR LOWER(a.channel_id) LIKE ? OR LOWER(a.keyword) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (platform != null && !platform.isBlank()) {
            sql.append(" AND LOWER(a.ad_platform) LIKE ?");
            params.add("%" + platform.strip().toLowerCase(Locale.ROOT) + "%");
        }
        sql.append(" ORDER BY a.ad_spend DESC, a.id DESC LIMIT ?");
        params.add(safeLimit);
        String dataDate = latestDataDate();
        return db.query(sql.toString(), (row, index) -> {
            BigDecimal spend = bd(row.getDouble("ad_spend"));
            BigDecimal revenue = bd(row.getDouble("gross_revenue"));
            BigDecimal profit = revenue.subtract(spend).setScale(2, RoundingMode.HALF_UP);
            BigDecimal roas = spend.signum() > 0 ? revenue.divide(spend, 4, RoundingMode.HALF_UP) : null;
            BigDecimal obsBudget = bd(row.getDouble("observed_budget"));
            return new CampaignSummary(
                    row.getString("ad_resource_id"),
                    firstNonBlank(row.getString("campaign"), row.getString("title"), row.getString("ad_resource_id")),
                    row.getString("site_code"),
                    row.getString("channel_id"),
                    cleanPlatform(row.getString("ad_platform")),
                    row.getString("country"),
                    row.getString("state"),
                    "USD",
                    spend,
                    revenue,
                    profit,
                    roas,
                    obsBudget.signum() > 0 ? obsBudget : null,
                    row.getObject("rule_id") != null && row.getString("rule_name") != null
                            && !row.getString("rule_name").isBlank(),
                    row.getString("rule_name"),
                    (int) Math.min(20, Math.floor(Math.abs(row.getDouble("budget_change")))),
                    dataDate);
        }, params.toArray());
    }

    private volatile String cachedDataDate = "";
    private volatile long cachedDataDateAt = 0L;

    /** Latest platform report date across the workspace, used only as a UI window label. */
    private String latestDataDate() {
        long now = System.currentTimeMillis();
        if (now - cachedDataDateAt < 60_000L) return cachedDataDate;
        try {
            String value = db.queryForObject("SELECT MAX(`date`) FROM adsense_data", String.class);
            cachedDataDate = value == null ? "" : value;
        } catch (Exception ignored) {
            cachedDataDate = "";
        }
        cachedDataDateAt = now;
        return cachedDataDate;
    }

    /** Lists active campaigns that have spend above threshold yet are not bound to any active automation rule. */
    List<CampaignSummary> listUnboundCampaigns(BigDecimal minSpend, String platform, int limit) {
        BigDecimal floor = minSpend == null ? BigDecimal.ZERO : minSpend;
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 25 : limit, 60));
        return listCampaigns(null, platform, 250).stream()
                .filter(c -> !c.ruleBound())
                .filter(c -> c.state() == null || "PASS".equalsIgnoreCase(c.state()))
                .filter(c -> c.spend().compareTo(floor) >= 0)
                .sorted(Comparator.comparing(CampaignSummary::spend).reversed())
                .limit(safeLimit)
                .toList();
    }

    /**
     * Loads real daily/segment/creative evidence for a campaign (by campaignId or channel).
     * Returns null when no daily rows exist for the requested campaign.
     */
    CmsReportSource.Campaign loadCampaignEvidence(String campaignId, String site, String channel,
                                                  LocalDate start, LocalDate end) {
        if (!localWorkspace) {
            return scenarios.campaigns().stream()
                    .filter(c -> c.campaignId().equals(campaignId) || c.id().equals(campaignId))
                    .findFirst().orElse(null);
        }
        if ((campaignId == null || campaignId.isBlank()) && (channel == null || channel.isBlank())) {
            return null;
        }
        var metaRows = db.queryForList("""
                SELECT a.ad_resource_id, a.channel_id, a.article_id, a.media_list, a.ad_platform, a.country,
                       COALESCE((SELECT MIN(rc.site) FROM report_data_config rc WHERE rc.channel = a.channel_id),
                                (SELECT MIN(ar.site) FROM article_info ar WHERE ar.article_id = a.article_id),
                                ?) AS site_code,
                       COALESCE((SELECT MAX(t.budget) FROM tiktok_ad_group_info t WHERE t.ad_resource_id = a.ad_resource_id), 0) AS obs_budget
                FROM ad_resource a
                WHERE a.status = 1
                  AND ((? <> '' AND a.ad_resource_id = ?)
                       OR (? = '' AND ? <> '' AND a.channel_id = ?))
                LIMIT 1
                """, site == null ? "local" : site,
                campaignId == null ? "" : campaignId, campaignId == null ? "" : campaignId,
                campaignId == null ? "" : campaignId, channel == null ? "" : channel,
                channel == null ? "" : channel);
        String resolvedId = campaignId;
        String resolvedChannel = channel == null ? "" : channel;
        String resolvedSite = site == null ? "local" : site;
        BigDecimal observedBudget = null;
        if (!metaRows.isEmpty()) {
            var meta = metaRows.get(0);
            resolvedId = str(meta.get("ad_resource_id"), resolvedId);
            resolvedChannel = firstNonBlank(str(meta.get("channel_id"), ""), resolvedChannel);
            resolvedSite = firstNonBlank(str(meta.get("site_code"), ""), resolvedSite);
            double obs = num(meta.get("obs_budget"));
            if (obs > 0) observedBudget = bd(obs);
        } else if (campaignId != null && !campaignId.isBlank()) {
            // A campaign-specific request must not borrow another campaign's rows through a shared channel.
            return null;
        }
        List<CmsReportSource.Day> daily = loadDailyRows(resolvedId, resolvedChannel, start, end);
        if (daily.isEmpty()) {
            return null;
        }
        List<CmsReportSource.Segment> segments = loadCountrySegments(resolvedId, resolvedChannel, start, end);
        List<CmsReportSource.Creative> creatives = loadCreativeMetrics(resolvedId);
        return new CmsReportSource.Campaign("cms:" + resolvedId, resolvedId, resolvedSite, resolvedChannel,
                "USD", observedBudget, daily, segments, creatives);
    }

    /** Builds a full Input + real Evidence package for 1-click campaign optimization. */
    CampaignPackage buildPackageForCampaign(String campaignId, BigDecimal targetRoas,
                                            BigDecimal minimumSpend, Integer minimumClicks,
                                            Integer maxChangePercent) {
        BigDecimal roasTarget = targetRoas == null ? new BigDecimal("1.25") : targetRoas;
        BigDecimal minSpend = minimumSpend == null ? new BigDecimal("20.00") : minimumSpend;
        int minClicks = minimumClicks == null ? 30 : minimumClicks;
        int maxPct = maxChangePercent == null ? 10 : Math.max(1, Math.min(20, maxChangePercent));

        if (!localWorkspace) {
            CmsReportSource.Campaign found = scenarios.campaigns().stream()
                    .filter(c -> c.id().equals(campaignId) || c.campaignId().equals(campaignId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到活动或场景：" + campaignId));
            OptimizationWorkflow.Input base = found.toInput();
            var input = new OptimizationWorkflow.Input(base.site(), base.channel(), base.campaignId(), base.currency(),
                    base.start(), base.end(), base.spend(), base.revenue(), base.impressions(), base.clicks(),
                    base.conversions(), roasTarget, minSpend, minClicks, maxPct);
            return new CampaignPackage(input, found);
        }

        CmsReportSource.Campaign evidence = loadCampaignEvidence(campaignId, null, null, null, null);
        if (evidence != null && !evidence.daily().isEmpty()) {
            List<CmsReportSource.Day> sorted = evidence.daily().stream()
                    .sorted(Comparator.comparing(CmsReportSource.Day::date)).toList();
            BigDecimal spend = sorted.stream().map(CmsReportSource.Day::spend).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal revenue = sorted.stream().map(CmsReportSource.Day::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
            long impressions = sorted.stream().mapToLong(CmsReportSource.Day::impressions).sum();
            long clicks = sorted.stream().mapToLong(CmsReportSource.Day::clicks).sum();
            long conversions = sorted.stream().mapToLong(CmsReportSource.Day::conversions).sum();
            clicks = Math.min(clicks, impressions);
            conversions = Math.min(conversions, clicks);
            var input = new OptimizationWorkflow.Input(
                    firstNonBlank(evidence.site(), "local"),
                    firstNonBlank(evidence.channel(), "unmapped"),
                    evidence.campaignId(),
                    "USD",
                    sorted.get(0).date(),
                    sorted.get(sorted.size() - 1).date(),
                    spend.setScale(2, RoundingMode.HALF_UP),
                    revenue.setScale(2, RoundingMode.HALF_UP),
                    impressions, clicks, conversions,
                    roasTarget, minSpend, minClicks, maxPct);
            return new CampaignPackage(input, evidence);
        }

        // Fall back to summary row on ad_resource if no daily breakdown rows exist yet.
        var summaries = listCampaigns(campaignId, null, 20).stream()
                .filter(c -> c.campaignId().equals(campaignId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到活动：" + campaignId));
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusDays(13);
        long[] estFunnel = estimateSummaryFunnel(summaries.platform(), summaries.spend(), summaries.revenue());
        var input = new OptimizationWorkflow.Input(
                firstNonBlank(summaries.site(), "local"),
                firstNonBlank(summaries.channel(), "unmapped"),
                summaries.campaignId(), "USD", start, end,
                summaries.spend(), summaries.revenue(), estFunnel[0], estFunnel[1], estFunnel[2],
                roasTarget, minSpend, minClicks, maxPct);
        var fallbackEvidence = new CmsReportSource.Campaign(
                "cms-summary:" + summaries.campaignId(), summaries.campaignId(),
                firstNonBlank(summaries.site(), "local"), firstNonBlank(summaries.channel(), "unmapped"),
                "USD", summaries.observedBudget(), List.of(), List.of(), List.of());
        return new CampaignPackage(input, fallbackEvidence);
    }

    /**
     * Derives platform-benchmarked funnel counts (impressions, clicks, conversions) for legacy summary-only
     * campaigns that have spend/revenue on ad_resource without daily breakdown rows.
     */
    private static long[] estimateSummaryFunnel(String platform, BigDecimal spend, BigDecimal revenue) {
        if (spend == null || spend.signum() <= 0) return new long[]{0L, 0L, 0L};
        double s = spend.doubleValue();
        String p = platform == null ? "" : platform.toLowerCase(Locale.ROOT);
        double cpc;
        double ctr;
        double cvr;
        if (p.contains("google")) {
            cpc = 0.42; ctr = 0.034; cvr = 0.095;
        } else if (p.contains("meta") || p.contains("facebook")) {
            cpc = 0.32; ctr = 0.0185; cvr = 0.068;
        } else if (p.contains("tiktok")) {
            cpc = 0.24; ctr = 0.0165; cvr = 0.072;
        } else {
            // Taboola / native content arbitrage benchmark
            cpc = 0.18; ctr = 0.0115; cvr = 0.085;
        }
        long clicks = Math.max(1L, Math.round(s / cpc));
        long impressions = Math.max(clicks, Math.round(clicks / ctr));
        double revFactor = (revenue != null && spend.signum() > 0)
                ? Math.max(0.5, Math.min(1.6, revenue.doubleValue() / s)) : 1.0;
        long conversions = Math.max(1L, Math.min(clicks, Math.round(clicks * cvr * revFactor)));
        return new long[]{impressions, clicks, conversions};
    }

    /** Deep multi-table profile for LLM specialist tools & Copilot. */
    Map<String, Object> getCampaignDeepProfile(String campaignId) {
        var profile = new LinkedHashMap<String, Object>();
        if (!localWorkspace) {
            CmsReportSource.Campaign c = scenarios.campaigns().stream()
                    .filter(item -> item.id().equals(campaignId) || item.campaignId().equals(campaignId))
                    .findFirst().orElse(null);
            if (c == null) return Map.of("available", false, "reason", "未找到活动或场景：" + campaignId);
            profile.put("available", true);
            profile.put("mode", "SIMULATION");
            profile.put("campaignId", c.campaignId());
            profile.put("scenarioId", c.id());
            profile.put("site", c.site());
            profile.put("channel", c.channel());
            profile.put("currency", c.currency());
            profile.put("currentBudget", c.currentBudget());
            profile.put("daily", c.daily());
            profile.put("segments", c.segments());
            profile.put("creatives", c.creative());
            return profile;
        }
        var rows = db.queryForList("""
                SELECT a.ad_resource_id, a.campaign, a.title, a.channel_id, a.article_id, a.land_page_url,
                       a.ad_platform, a.ad_desc, a.state, a.country, a.keyword, a.ad_account, a.am,
                       a.rule_id, a.media_list, a.ad_spend, a.gross_revenue, a.profit, a.roi,
                       r.name AS rule_name, r.type AS rule_type, r.action_cycle, r.budget_change, r.rule AS rule_json
                FROM ad_resource a
                LEFT JOIN ad_resource_rule r ON a.rule_id = r.id AND r.status = 1
                WHERE a.ad_resource_id = ? AND a.status = 1
                LIMIT 1
                """, campaignId);
        if (rows.isEmpty()) {
            return Map.of("available", false, "reason", "本地 CMS 数据库中未找到活动：" + campaignId);
        }
        var row = rows.get(0);
        String channel = str(row.get("channel_id"), "");
        String articleId = str(row.get("article_id"), "");
        profile.put("available", true);
        profile.put("mode", "LOCAL_CMS");
        profile.put("campaignId", campaignId);
        profile.put("name", firstNonBlank(str(row.get("campaign"), ""), str(row.get("title"), ""), campaignId));
        profile.put("channel", channel);
        profile.put("platform", cleanPlatform(str(row.get("ad_platform"), "")));
        profile.put("country", str(row.get("country"), ""));
        profile.put("keyword", str(row.get("keyword"), ""));
        profile.put("state", str(row.get("state"), ""));
        profile.put("landingPageUrl", str(row.get("land_page_url"), ""));
        profile.put("owner", str(row.get("am"), ""));
        profile.put("account", str(row.get("ad_account"), ""));
        profile.put("summaryMetrics", Map.of(
                "spend", bd(num(row.get("ad_spend"))),
                "revenue", bd(num(row.get("gross_revenue"))),
                "profit", bd(num(row.get("profit"))),
                "roas", bd(num(row.get("roi")))));
        if (row.get("rule_id") != null) {
            profile.put("boundRule", Map.of(
                    "ruleId", row.get("rule_id"),
                    "name", str(row.get("rule_name"), ""),
                    "type", str(row.get("rule_type"), ""),
                    "cycle", str(row.get("action_cycle"), ""),
                    "budgetChangeCap", Math.min(20, (int) Math.floor(Math.abs(num(row.get("budget_change"))))),
                    "definition", str(row.get("rule_json"), "")));
        } else {
            profile.put("boundRule", Map.of("bound", false));
        }

        // Linked article & site feedback
        String siteCode = "";
        if (!articleId.isBlank()) {
            var articles = db.queryForList("""
                    SELECT article_id, title, terms, category, lang, site, first_part
                    FROM article_info WHERE article_id = ? LIMIT 1
                    """, articleId);
            if (!articles.isEmpty()) {
                var article = articles.get(0);
                siteCode = str(article.get("site"), "");
                profile.put("article", Map.of(
                        "articleId", str(article.get("article_id"), ""),
                        "title", str(article.get("title"), ""),
                        "terms", str(article.get("terms"), ""),
                        "category", str(article.get("category"), ""),
                        "lang", str(article.get("lang"), ""),
                        "site", siteCode,
                        "lead", abbreviate(str(article.get("first_part"), ""), 280)));
            }
            var keywords = db.queryForList("""
                    SELECT key_word AS "keyword", SUM(`count`) AS "hits"
                    FROM google_bigquery_keyword_data
                    WHERE article_id = ?
                    GROUP BY key_word
                    ORDER BY SUM(`count`) DESC
                    LIMIT 6
                    """, articleId);
            if (!keywords.isEmpty()) profile.put("topKeywords", keywords);
        }

        // Linked ad materials & texts
        List<String> mediaIds = parseStringArray(str(row.get("media_list"), ""));
        if (!mediaIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(mediaIds.size(), "?"));
            var materials = db.queryForList("""
                    SELECT ad_material_id AS "id", name AS "name", type AS "type", dimension AS "dimension",
                           ratio AS "ratio", country AS "country", template AS "template"
                    FROM ad_material WHERE ad_material_id IN (""" + placeholders + ") AND status = 1",
                    mediaIds.toArray());
            profile.put("materials", materials);
        }
        String campName = str(row.get("campaign"), "");
        if (!campName.isBlank()) {
            var texts = db.queryForList("""
                    SELECT name AS "name", ad_text AS "adText"
                    FROM ad_text WHERE status = 1 AND name = ? LIMIT 3
                    """, campName);
            if (!texts.isEmpty()) profile.put("adTexts", texts);
        }

        // Ad groups & rule execution logs
        var adGroups = db.queryForList("""
                SELECT ad_group_id AS "adGroupId", ad_group_status AS "status", budget AS "budget",
                       ad_spend AS "spend", gross_revenue AS "revenue", roi AS "roas", conversion AS "conversions"
                FROM tiktok_ad_group_info WHERE ad_resource_id = ? AND status = 1 LIMIT 5
                """, campaignId);
        if (!adGroups.isEmpty()) profile.put("adGroups", adGroups);

        var ruleLogs = db.queryForList("""
                SELECT rule_name AS "ruleName", action AS "action", day_num AS "days", condition AS "condition", ctime AS "time"
                FROM ad_resource_rule_log WHERE ad_resource_id = ? ORDER BY id DESC LIMIT 5
                """, campaignId);
        if (!ruleLogs.isEmpty()) profile.put("recentRuleLogs", ruleLogs);

        if (!siteCode.isBlank()) {
            var feedbacks = db.queryForList("""
                    SELECT rate AS "rate", locale AS "locale", `value` AS "content", ctime AS "time"
                    FROM feedback WHERE site = ? ORDER BY id DESC LIMIT 5
                    """, siteCode);
            if (!feedbacks.isEmpty()) profile.put("siteFeedback", feedbacks);
        }

        // Daily series & country breakdown
        List<CmsReportSource.Day> daily = loadDailyRows(campaignId, channel, null, null);
        profile.put("daily", daily.stream().map(this::dayMap).toList());
        profile.put("countryBreakdown", loadCountryMaps(campaignId, channel, null, null));
        return profile;
    }

    /** Country breakdown specifically for the countryBreakdown() tool. */
    List<Map<String, Object>> getCountryBreakdown(String campaignId, String channel, LocalDate start, LocalDate end) {
        if (!localWorkspace) {
            CmsReportSource.Campaign c = scenarios.campaigns().stream()
                    .filter(item -> item.campaignId().equals(campaignId) || item.id().equals(campaignId))
                    .findFirst().orElse(null);
            if (c == null) return List.of();
            return c.segments().stream().map(seg -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("segment", seg.name());
                m.put("impressions", seg.impressions());
                m.put("clicks", seg.clicks());
                m.put("conversions", seg.conversions());
                m.put("spend", seg.spend());
                m.put("cpa", seg.conversions() > 0
                        ? seg.spend().divide(BigDecimal.valueOf(seg.conversions()), 2, RoundingMode.HALF_UP) : null);
                return (Map<String, Object>) m;
            }).toList();
        }
        return loadCountryMaps(campaignId, channel, start, end);
    }

    /** Peer benchmark comparison for a site & platform. */
    Map<String, Object> getPeerBenchmarks(String site, String campaignId) {
        if (!localWorkspace) {
            var all = scenarios.campaigns();
            List<Map<String, Object>> peers = new ArrayList<>();
            for (var c : all) {
                if (c.campaignId().equals(campaignId)) continue;
                var in = c.toInput();
                BigDecimal roas = in.spend().signum() > 0
                        ? in.revenue().divide(in.spend(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                peers.add(Map.of("campaignId", c.campaignId(), "scenario", c.id(),
                        "spend", in.spend(), "revenue", in.revenue(), "roas", roas));
            }
            return Map.of("available", !peers.isEmpty(), "scope", "SIMULATION_SCENARIOS", "peers", peers);
        }
        var peers = db.queryForList("""
                SELECT a.ad_resource_id AS "campaignId", a.campaign AS "name", a.ad_platform AS "platform",
                       a.country AS "country", a.ad_spend AS "spend", a.gross_revenue AS "revenue", a.roi AS "roas"
                FROM ad_resource a
                LEFT JOIN report_data_config rc ON rc.channel = a.channel_id
                WHERE a.status = 1 AND a.ad_spend > 0 AND a.ad_resource_id <> ?
                  AND (? = '' OR rc.site = ?)
                ORDER BY a.roi DESC
                LIMIT 10
                """, campaignId == null ? "" : campaignId, site == null ? "" : site, site == null ? "" : site);
        if (peers.isEmpty()) {
            peers = db.queryForList("""
                    SELECT ad_resource_id AS "campaignId", campaign AS "name", ad_platform AS "platform",
                           country AS "country", ad_spend AS "spend", gross_revenue AS "revenue", roi AS "roas"
                    FROM ad_resource
                    WHERE status = 1 AND ad_spend > 0 AND ad_resource_id <> ?
                    ORDER BY roi DESC
                    LIMIT 10
                    """, campaignId == null ? "" : campaignId);
        }
        double totalSpend = peers.stream().mapToDouble(p -> num(p.get("spend"))).sum();
        double totalRev = peers.stream().mapToDouble(p -> num(p.get("revenue"))).sum();
        var result = new LinkedHashMap<String, Object>();
        result.put("available", !peers.isEmpty());
        result.put("peerCount", peers.size());
        result.put("peerWeightedRoas", totalSpend > 0 ? bd(totalRev / totalSpend) : null);
        result.put("topPeers", peers.stream().limit(5).toList());
        return result;
    }

    /** Episodic memory: previous agent optimization runs & reviewer decisions for a campaign. */
    List<Map<String, Object>> getHistoricalRuns(String campaignId, int limit) {
        int safe = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 20));
        var rows = db.queryForList("""
                SELECT id, created_at, plan, state, reviewer, review_note
                FROM optimization_run
                ORDER BY created_at DESC
                LIMIT 100
                """);
        List<Map<String, Object>> matched = new ArrayList<>();
        for (var row : rows) {
            if (matched.size() >= safe) break;
            try {
                JsonNode plan = json.readTree(str(row.get("plan"), "{}"));
                String cid = plan.at("/input/campaignId").asText("");
                if (campaignId != null && !campaignId.isBlank() && !campaignId.equals(cid)) continue;
                var item = new LinkedHashMap<String, Object>();
                item.put("runId", str(row.get("id"), ""));
                item.put("campaignId", cid);
                item.put("createdAt", String.valueOf(row.get("created_at")));
                item.put("state", str(row.get("state"), ""));
                item.put("action", plan.at("/proposal/action").asText(""));
                item.put("changePercent", plan.at("/proposal/changePercent").asInt(0));
                item.put("reason", plan.at("/proposal/reason").asText(""));
                item.put("roas", plan.at("/metrics/roas").asText(""));
                item.put("source", plan.at("/explanation/source").asText(""));
                item.put("reviewer", str(row.get("reviewer"), ""));
                item.put("reviewNote", str(row.get("review_note"), ""));
                matched.add(item);
            } catch (Exception ignored) {
                // Skip malformed legacy rows.
            }
        }
        return matched;
    }

    /** Account-wide portfolio summary for Copilot & Overview. */
    Map<String, Object> getPortfolioOverview() {
        List<CampaignSummary> all = listCampaigns(null, null, 100);
        BigDecimal totalSpend = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        Map<String, double[]> byPlatform = new LinkedHashMap<>();
        int ruleBound = 0;
        for (var c : all) {
            totalSpend = totalSpend.add(c.spend());
            totalRevenue = totalRevenue.add(c.revenue());
            if (c.ruleBound()) ruleBound++;
            byPlatform.computeIfAbsent(firstNonBlank(c.platform(), "Unknown"), k -> new double[3]);
            double[] stats = byPlatform.get(firstNonBlank(c.platform(), "Unknown"));
            stats[0] += c.spend().doubleValue();
            stats[1] += c.revenue().doubleValue();
            stats[2] += 1;
        }
        BigDecimal profit = totalRevenue.subtract(totalSpend).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roas = totalSpend.signum() > 0
                ? totalRevenue.divide(totalSpend, 4, RoundingMode.HALF_UP) : null;
        List<Map<String, Object>> platformRows = byPlatform.entrySet().stream().map(e -> {
            double sp = e.getValue()[0];
            double rv = e.getValue()[1];
            var m = new LinkedHashMap<String, Object>();
            m.put("platform", e.getKey());
            m.put("campaigns", (int) e.getValue()[2]);
            m.put("spend", bd(sp));
            m.put("revenue", bd(rv));
            m.put("profit", bd(rv - sp));
            m.put("roas", sp > 0 ? bd(rv / sp) : null);
            return (Map<String, Object>) m;
        }).toList();
        List<CampaignSummary> topWinners = all.stream()
                .filter(c -> c.spend().doubleValue() >= 20 && c.roas() != null)
                .sorted(Comparator.comparing(CampaignSummary::profit).reversed())
                .limit(5).toList();
        List<CampaignSummary> topLosers = all.stream()
                .filter(c -> c.spend().doubleValue() >= 20)
                .sorted(Comparator.comparing(CampaignSummary::profit))
                .limit(5).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("campaignCount", all.size());
        result.put("ruleBoundCount", ruleBound);
        result.put("totalSpend", totalSpend.setScale(2, RoundingMode.HALF_UP));
        result.put("totalRevenue", totalRevenue.setScale(2, RoundingMode.HALF_UP));
        result.put("totalProfit", profit);
        result.put("blendedRoas", roas);
        result.put("byPlatform", platformRows);
        result.put("topWinners", topWinners);
        result.put("topLosers", topLosers);
        return result;
    }

    /** Autonomous portfolio anomaly scanner for the Intelligent Scan workbench & Copilot tool. */
    Map<String, Object> scanAnomalies(BigDecimal targetRoas, BigDecimal minSpend) {
        BigDecimal target = targetRoas == null ? new BigDecimal("1.25") : targetRoas;
        BigDecimal floor = minSpend == null ? new BigDecimal("30.00") : minSpend;
        List<CampaignSummary> all = listCampaigns(null, null, 250).stream()
                .filter(c -> c.state() == null || "PASS".equalsIgnoreCase(c.state()))
                .toList();
        List<AnomalyItem> criticalItems = new ArrayList<>();
        List<AnomalyItem> warningItems = new ArrayList<>();
        List<AnomalyItem> opportunityItems = new ArrayList<>();
        List<AnomalyItem> unboundItems = new ArrayList<>();

        for (var c : all) {
            if (c.spend().compareTo(floor) < 0) continue;
            BigDecimal roas = c.roas() == null ? BigDecimal.ZERO : c.roas();
            if (roas.compareTo(target.multiply(new BigDecimal("0.70"))) < 0) {
                criticalItems.add(new AnomalyItem("CRITICAL_BLEED", "HIGH", c.campaignId(), c.name(), c.site(),
                        c.channel(), c.platform(), c.spend(), c.revenue(), c.profit(), roas,
                        "ROAS " + roas.setScale(2, RoundingMode.HALF_UP) + "× 严重低于目标 " + target + "×，亏损 $"
                                + c.profit().abs().setScale(2, RoundingMode.HALF_UP),
                        "REDUCE"));
            } else if (roas.compareTo(target) < 0) {
                warningItems.add(new AnomalyItem("UNDERPERFORMING", "MEDIUM", c.campaignId(), c.name(), c.site(),
                        c.channel(), c.platform(), c.spend(), c.revenue(), c.profit(), roas,
                        "ROAS " + roas.setScale(2, RoundingMode.HALF_UP) + "× 低于目标 " + target + "×，需排查国家与创意损耗",
                        "REDUCE"));
            } else if (roas.compareTo(target.multiply(new BigDecimal("1.25"))) >= 0) {
                opportunityItems.add(new AnomalyItem("SCALE_OPPORTUNITY", "OPPORTUNITY", c.campaignId(), c.name(), c.site(),
                        c.channel(), c.platform(), c.spend(), c.revenue(), c.profit(), roas,
                        "ROAS " + roas.setScale(2, RoundingMode.HALF_UP) + "× 高于目标 25%+，净利 $"
                                + c.profit().setScale(2, RoundingMode.HALF_UP) + "，具备放量潜力",
                        "INCREASE"));
            }
            if (!c.ruleBound() && c.spend().compareTo(floor.multiply(BigDecimal.valueOf(2))) >= 0) {
                unboundItems.add(new AnomalyItem("RULE_UNBOUND", "MEDIUM", c.campaignId(), c.name(), c.site(),
                        c.channel(), c.platform(), c.spend(), c.revenue(), c.profit(), roas,
                        "高消耗活动尚未绑定预算护栏规则，建议补充自动化风控规则",
                        "HOLD"));
            }
        }
        Comparator<AnomalyItem> bySpendDesc = (a, b) -> b.spend().compareTo(a.spend());
        criticalItems.sort(bySpendDesc);
        opportunityItems.sort(bySpendDesc);
        unboundItems.sort(bySpendDesc);
        warningItems.sort(bySpendDesc);

        // Balance across all 4 categories so RULE_UNBOUND / UNDERPERFORMING are never starved by a flat slice.
        List<AnomalyItem> balanced = new ArrayList<>();
        balanced.addAll(criticalItems.stream().limit(15).toList());
        balanced.addAll(unboundItems.stream().limit(15).toList());
        balanced.addAll(opportunityItems.stream().limit(15).toList());
        balanced.addAll(warningItems.stream().limit(15).toList());

        var result = new LinkedHashMap<String, Object>();
        result.put("scannedCampaigns", all.size());
        result.put("targetRoas", target);
        result.put("minimumSpend", floor);
        result.put("counts", Map.of(
                "criticalBleed", criticalItems.size(),
                "underperforming", warningItems.size(),
                "scaleOpportunity", opportunityItems.size(),
                "ruleUnbound", unboundItems.size()));
        result.put("items", balanced);
        return result;
    }

    /** Lists all active rules in ad_resource_rule for Copilot inspection. */
    List<Map<String, Object>> listActiveRules() {
        if (!localWorkspace) {
            return List.of(Map.of("name", "Simulation Default Rule", "budgetChange", 10, "type", "BUDGET"));
        }
        return db.queryForList("""
                SELECT id AS "id", name AS "name", creator AS "creator", type AS "type",
                       action_cycle AS "actionCycle", budget_change AS "budgetChange", rule AS "rule"
                FROM ad_resource_rule WHERE status = 1 ORDER BY id DESC LIMIT 30
                """);
    }

    private List<CmsReportSource.Day> loadDailyRows(String campaignId, String channel, LocalDate start, LocalDate end) {
        try {
            // Merge AdSense revenue + platform spend/clicks/impressions/conversions per day.
            var byDate = new LinkedHashMap<LocalDate, DailyAccumulator>();
            var platformRows = db.queryForList("""
                    SELECT `date`, SUM(spend) AS spend, SUM(impressions) AS impressions,
                           SUM(clicks) AS clicks, SUM(conversion) AS conversions
                    FROM (
                        SELECT `date`, spend, impressions, clicks, conversion FROM tiktok_data WHERE campaign_id = ? OR (? <> '' AND channel_id = ?)
                        UNION ALL
                        SELECT `date`, spend, impressions, clicks, conversion FROM taboola_data WHERE campaign_id = ? OR (? <> '' AND channel_id = ?)
                        UNION ALL
                        SELECT `date`, spend, impressions, clicks, conversion FROM google_data WHERE campaign_id = ? OR (? <> '' AND channel_id = ?)
                        UNION ALL
                        SELECT `date`, spend, impressions, clicks, conversion FROM meta_data WHERE campaign_id = ? OR (? <> '' AND channel_id = ?)
                    ) p
                    WHERE (? IS NULL OR `date` >= ?) AND (? IS NULL OR `date` <= ?)
                    GROUP BY `date`
                    ORDER BY `date` ASC
                    """,
                    campaignId, channel, channel,
                    campaignId, channel, channel,
                    campaignId, channel, channel,
                    campaignId, channel, channel,
                    start == null ? null : start.toString(), start == null ? null : start.toString(),
                    end == null ? null : end.toString(), end == null ? null : end.toString());
            for (var row : platformRows) {
                LocalDate d = parseDate(str(row.get("date"), ""));
                if (d == null) continue;
                var acc = byDate.computeIfAbsent(d, k -> new DailyAccumulator());
                acc.spend += num(row.get("spend"));
                acc.impressions += Math.round(num(row.get("impressions")));
                acc.clicks += Math.round(num(row.get("clicks")));
                acc.conversions += Math.round(num(row.get("conversions")));
            }
            String channelSuffix = channel == null || channel.isBlank() ? "__NO_CHANNEL__" : "%:" + channel;
            var adsenseRows = db.queryForList("""
                    SELECT `date`, SUM(estimated_earnings) AS revenue,
                           SUM(COALESCE(tt_spend, 0) + COALESCE(tb_spend, 0) + COALESCE(gg_spend, 0) + COALESCE(fb_spend, 0)) AS fallback_spend,
                           SUM(COALESCE(tt_impressions, 0) + COALESCE(tb_impressions, 0) + COALESCE(gg_impressions, 0) + COALESCE(fb_impressions, 0)) AS fallback_imp,
                           SUM(COALESCE(tt_clicks, 0) + COALESCE(tb_clicks, 0) + COALESCE(gg_clicks, 0) + COALESCE(fb_clicks, 0)) AS fallback_clicks,
                           SUM(COALESCE(tt_conversion, 0) + COALESCE(tb_conversion, 0) + COALESCE(gg_conversion, 0) + COALESCE(fb_conversion, 0)) AS fallback_conv,
                           SUM(COALESCE(individual_ad_impressions, 0)) AS adsense_imp,
                           SUM(COALESCE(funnel_clicks, 0)) AS adsense_funnel_clicks,
                           SUM(COALESCE(clicks, 0)) AS adsense_clicks
                    FROM adsense_data
                    WHERE status = 1
                      AND (ad_resource_id = ? OR custom_channel_id LIKE ?)
                      AND (? IS NULL OR `date` >= ?) AND (? IS NULL OR `date` <= ?)
                    GROUP BY `date`
                    ORDER BY `date` ASC
                    """,
                    campaignId, channelSuffix,
                    start == null ? null : start.toString(), start == null ? null : start.toString(),
                    end == null ? null : end.toString(), end == null ? null : end.toString());
            for (var row : adsenseRows) {
                LocalDate d = parseDate(str(row.get("date"), ""));
                if (d == null) continue;
                var acc = byDate.computeIfAbsent(d, k -> new DailyAccumulator());
                acc.revenue += num(row.get("revenue"));
                if (acc.spend <= 0 && num(row.get("fallback_spend")) > 0) {
                    acc.spend = num(row.get("fallback_spend"));
                    acc.impressions = Math.max(acc.impressions, Math.round(num(row.get("fallback_imp"))));
                    acc.clicks = Math.max(acc.clicks, Math.round(num(row.get("fallback_clicks"))));
                    acc.conversions = Math.max(acc.conversions, Math.round(num(row.get("fallback_conv"))));
                }
                // Publisher ad clicks are not acquisition-platform conversions or clicks.
            }
            return byDate.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> {
                        var acc = entry.getValue();
                        long clicks = Math.min(acc.clicks, Math.max(acc.impressions, acc.clicks));
                        long conv = Math.min(acc.conversions, clicks);
                        return new CmsReportSource.Day(entry.getKey(), bd(acc.spend), bd(acc.revenue),
                                Math.max(acc.impressions, clicks), clicks, conv);
                    }).toList();
        } catch (Exception ex) {
            log.warn("Failed loading daily rows for campaign={}: {}", campaignId, ex.getMessage());
            return List.of();
        }
    }

    private List<CmsReportSource.Segment> loadCountrySegments(String campaignId, String channel,
                                                              LocalDate start, LocalDate end) {
        return loadCountryMaps(campaignId, channel, start, end).stream()
                .map(row -> new CmsReportSource.Segment(
                        str(row.get("country"), "UNKNOWN"),
                        ((Number) row.getOrDefault("impressions", 0L)).longValue(),
                        ((Number) row.getOrDefault("clicks", 0L)).longValue(),
                        ((Number) row.getOrDefault("conversions", 0L)).longValue(),
                        (BigDecimal) row.getOrDefault("spend", BigDecimal.ZERO)))
                .toList();
    }

    private List<Map<String, Object>> loadCountryMaps(String campaignId, String channel,
                                                      LocalDate start, LocalDate end) {
        try {
            var byCountry = new LinkedHashMap<String, DailyAccumulator>();
            var platformRows = db.queryForList("""
                    SELECT country_code, SUM(spend) AS spend, SUM(impressions) AS impressions,
                           SUM(clicks) AS clicks, SUM(conversion) AS conversions
                    FROM (
                        SELECT country_code, `date`, spend, impressions, clicks, conversion FROM tiktok_data WHERE campaign_id = ? OR (? <> '' AND channel_id = ?)
                        UNION ALL
                        SELECT country_code, `date`, spend, impressions, clicks, conversion FROM taboola_data WHERE campaign_id = ? OR (? <> '' AND channel_id = ?)
                        UNION ALL
                        SELECT country_code, `date`, spend, impressions, clicks, conversion FROM google_data WHERE campaign_id = ? OR (? <> '' AND channel_id = ?)
                        UNION ALL
                        SELECT country_code, `date`, spend, impressions, clicks, conversion FROM meta_data WHERE campaign_id = ? OR (? <> '' AND channel_id = ?)
                    ) p
                    WHERE (? IS NULL OR `date` >= ?) AND (? IS NULL OR `date` <= ?)
                    GROUP BY country_code
                    """,
                    campaignId, channel, channel,
                    campaignId, channel, channel,
                    campaignId, channel, channel,
                    campaignId, channel, channel,
                    start == null ? null : start.toString(), start == null ? null : start.toString(),
                    end == null ? null : end.toString(), end == null ? null : end.toString());
            for (var row : platformRows) {
                String country = firstNonBlank(str(row.get("country_code"), ""), "UNKNOWN");
                var acc = byCountry.computeIfAbsent(country, k -> new DailyAccumulator());
                acc.spend += num(row.get("spend"));
                acc.impressions += Math.round(num(row.get("impressions")));
                acc.clicks += Math.round(num(row.get("clicks")));
                acc.conversions += Math.round(num(row.get("conversions")));
            }
            String channelSuffix = channel == null || channel.isBlank() ? "__NO_CHANNEL__" : "%:" + channel;
            var revenueRows = db.queryForList("""
                    SELECT country_code, SUM(estimated_earnings) AS revenue
                    FROM adsense_data
                    WHERE status = 1 AND (ad_resource_id = ? OR custom_channel_id LIKE ?)
                      AND (? IS NULL OR `date` >= ?) AND (? IS NULL OR `date` <= ?)
                    GROUP BY country_code
                    """,
                    campaignId, channelSuffix,
                    start == null ? null : start.toString(), start == null ? null : start.toString(),
                    end == null ? null : end.toString(), end == null ? null : end.toString());
            for (var row : revenueRows) {
                String country = firstNonBlank(str(row.get("country_code"), ""), "UNKNOWN");
                byCountry.computeIfAbsent(country, k -> new DailyAccumulator()).revenue += num(row.get("revenue"));
            }
            return byCountry.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue().spend, a.getValue().spend))
                    .map(entry -> {
                        var acc = entry.getValue();
                        BigDecimal sp = bd(acc.spend);
                        BigDecimal rv = bd(acc.revenue);
                        var map = new LinkedHashMap<String, Object>();
                        map.put("country", entry.getKey());
                        map.put("spend", sp);
                        map.put("revenue", rv);
                        map.put("profit", rv.subtract(sp).setScale(2, RoundingMode.HALF_UP));
                        map.put("roas", sp.signum() > 0 ? rv.divide(sp, 4, RoundingMode.HALF_UP) : null);
                        map.put("impressions", acc.impressions);
                        map.put("clicks", acc.clicks);
                        map.put("conversions", acc.conversions);
                        map.put("cpa", acc.conversions > 0
                                ? sp.divide(BigDecimal.valueOf(acc.conversions), 2, RoundingMode.HALF_UP) : null);
                        return (Map<String, Object>) map;
                    }).toList();
        } catch (Exception ex) {
            log.warn("Failed loading country breakdown for campaign={}: {}", campaignId, ex.getMessage());
            return List.of();
        }
    }

    private List<CmsReportSource.Creative> loadCreativeMetrics(String campaignId) {
        try {
            var rows = db.queryForList("SELECT media_list FROM ad_resource WHERE ad_resource_id = ? LIMIT 1", campaignId);
            if (rows.isEmpty()) return List.of();
            List<String> ids = parseStringArray(str(rows.get(0).get("media_list"), ""));
            if (ids.isEmpty()) return List.of();
            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            return db.query("SELECT name FROM ad_material WHERE ad_material_id IN (" + placeholders + ") AND status = 1",
                    (row, idx) -> new CmsReportSource.Creative(row.getString("name"), 0, 0), ids.toArray());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private CampaignSummary fromScenario(CmsReportSource.Campaign c) {
        var in = c.toInput();
        BigDecimal profit = in.revenue().subtract(in.spend()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roas = in.spend().signum() > 0 ? in.revenue().divide(in.spend(), 4, RoundingMode.HALF_UP) : null;
        return new CampaignSummary(c.campaignId(), c.id() + " · " + c.campaignId(), c.site(), c.channel(),
                "Simulation", "GLOBAL", "PASS", c.currency(), in.spend(), in.revenue(), profit, roas,
                c.currentBudget(), true, "Simulation Rule (±10%)", 10, "");
    }

    private static boolean matches(CampaignSummary item, String query, String platform) {
        if (platform != null && !platform.isBlank()
                && !item.platform().toLowerCase(Locale.ROOT).contains(platform.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (query == null || query.isBlank()) return true;
        String q = query.strip().toLowerCase(Locale.ROOT);
        return item.campaignId().toLowerCase(Locale.ROOT).contains(q)
                || item.name().toLowerCase(Locale.ROOT).contains(q)
                || item.site().toLowerCase(Locale.ROOT).contains(q);
    }

    private Map<String, Object> dayMap(CmsReportSource.Day day) {
        var m = new LinkedHashMap<String, Object>();
        m.put("date", day.date().toString());
        m.put("spend", day.spend());
        m.put("revenue", day.revenue());
        m.put("roas", day.spend().signum() > 0 ? day.revenue().divide(day.spend(), 4, RoundingMode.HALF_UP) : null);
        m.put("impressions", day.impressions());
        m.put("clicks", day.clicks());
        m.put("conversions", day.conversions());
        return m;
    }

    private List<String> parseStringArray(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode node = json.readTree(raw);
            if (!node.isArray()) return List.of();
            List<String> list = new ArrayList<>();
            node.forEach(item -> {
                String text = item.asText("").strip();
                if (!text.isEmpty()) list.add(text);
            });
            return list;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String cleanPlatform(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown";
        return raw.replace("[", "").replace("]", "").replace("\"", "").trim();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.length() < 10) return null;
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BigDecimal bd(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static double num(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String abbreviate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private static final class DailyAccumulator {
        double spend;
        double revenue;
        long impressions;
        long clicks;
        long conversions;
    }

    record CampaignPackage(OptimizationWorkflow.Input input, CmsReportSource.Campaign evidence) {}

    record CampaignSummary(String campaignId, String name, String site, String channel, String platform,
                           String country, String state, String currency, BigDecimal spend, BigDecimal revenue,
                           BigDecimal profit, BigDecimal roas, BigDecimal observedBudget,
                           boolean ruleBound, String ruleName, int ruleBudgetCap, String dataDate) {}

    record AnomalyItem(String category, String severity, String campaignId, String name, String site,
                       String channel, String platform, BigDecimal spend, BigDecimal revenue,
                       BigDecimal profit, BigDecimal roas, String diagnosis, String suggestedAction) {}
}
