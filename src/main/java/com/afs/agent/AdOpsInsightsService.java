package com.afs.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Commercial AdOps insights, budget pacing firewall, multi-channel alert rules,
 * 7x24 dayparting schedules, and batch campaign operations.
 */
@Service
class AdOpsInsightsService {

    private final JdbcTemplate db;
    private final CmsCampaignDataService cmsData;
    private final ObjectMapper json;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    AdOpsInsightsService(JdbcTemplate db, CmsCampaignDataService cmsData, ObjectMapper json) {
        this.db = db;
        this.cmsData = cmsData;
        this.json = json;
    }

    private record CacheEntry(long expiresAt, Map<String, Object> value) {}

    private Map<String, Object> cached(String key, long ttlMs, Supplier<Map<String, Object>> supplier) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.expiresAt > now) {
            return json.convertValue(entry.value, new TypeReference<Map<String, Object>>() {});
        }
        Map<String, Object> value = supplier.get();
        cache.put(key, new CacheEntry(now + ttlMs, value));
        return json.convertValue(value, new TypeReference<Map<String, Object>>() {});
    }

    void clearCache() {
        cache.clear();
    }

    // =========================================================================
    // 1. Conversion Funnel & Winners/Bleeders Matrix
    // =========================================================================

    Map<String, Object> buildFunnelAndMatrix(String platform) {
        String key = "funnel|" + (platform == null || platform.isBlank() ? "ALL" : platform.toUpperCase(Locale.ROOT));
        return cached(key, 30_000L, () -> computeFunnelAndMatrix(platform));
    }

    private Map<String, Object> computeFunnelAndMatrix(String platform) {
        List<CmsCampaignDataService.CampaignSummary> campaigns = cmsData.listCampaigns("", platform, 200);
        long totalImpressions = 0L;
        long totalClicks = 0L;
        long totalConversions = 0L;
        BigDecimal totalSpend = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        Map<String, double[]> byPlatform = new LinkedHashMap<>();
        Map<String,double[]> byCountry = new LinkedHashMap<>();
        List<Map<String, Object>> winners = new ArrayList<>();
        List<Map<String, Object>> bleeders = new ArrayList<>();

        for (CmsCampaignDataService.CampaignSummary c : campaigns) {
            for(var segment:cmsData.getCountryBreakdown(c.campaignId(),c.channel(),null,null)) {
                String country=String.valueOf(segment.getOrDefault("country",segment.getOrDefault("segment","UNKNOWN")));
                double[] totals=byCountry.computeIfAbsent(country,k->new double[3]);
                totals[0]+=OverseasDataService.num(segment,"spend");totals[1]+=OverseasDataService.num(segment,"impressions");totals[2]+=OverseasDataService.num(segment,"clicks");
            }
            BigDecimal spend = nz(c.spend());
            BigDecimal rev = nz(c.revenue());
            BigDecimal profit = rev.subtract(spend).setScale(2, RoundingMode.HALF_UP);
            double roas = spend.signum() > 0 ? rev.divide(spend, 4, RoundingMode.HALF_UP).doubleValue() : 0.0;

            totalSpend = totalSpend.add(spend);
            totalRevenue = totalRevenue.add(rev);

            var evidence = cmsData.loadCampaignEvidence(c.campaignId(),c.site(),c.channel(),null,null);
            long clicks=evidence==null?0:evidence.daily().stream().mapToLong(CmsReportSource.Day::clicks).sum();
            long imps=evidence==null?0:evidence.daily().stream().mapToLong(CmsReportSource.Day::impressions).sum();
            long convs=evidence==null?0:evidence.daily().stream().mapToLong(CmsReportSource.Day::conversions).sum();
            totalImpressions += imps;
            totalClicks += clicks;
            totalConversions += convs;

            String pKey = c.platform() == null || c.platform().isBlank() ? "其他" : c.platform();
            double[] acc = byPlatform.computeIfAbsent(pKey, k -> new double[5]);
            acc[0] += spend.doubleValue();
            acc[1] += rev.doubleValue();
            acc[2] += imps;
            acc[3] += clicks;
            acc[4] += convs;

            if (spend.doubleValue() >= 15.0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("campaignId", c.campaignId());
                row.put("name", c.name());
                row.put("platform", pKey);
                row.put("spend", spend.setScale(2, RoundingMode.HALF_UP));
                row.put("revenue", rev.setScale(2, RoundingMode.HALF_UP));
                row.put("profit", profit);
                row.put("roas", round(roas, 2));
                row.put("ruleBound", c.ruleBound());
                if (roas >= 1.15 && profit.signum() > 0) {
                    winners.add(row);
                } else if (profit.signum() < 0 || roas < 1.0) {
                    bleeders.add(row);
                }
            }
        }

        // Engagement is not present in platform daily reports. Do not fabricate it from clicks.
        long engagedVisits = 0;
        double ctr = totalImpressions > 0 ? (double) totalClicks / totalImpressions : 0.0;
        double engageRate = totalClicks > 0 ? (double) engagedVisits / totalClicks : 0.0;
        double cvr = totalClicks > 0 ? (double) totalConversions / totalClicks : 0.0;
        double overallRoas = totalSpend.signum() > 0 ? totalRevenue.divide(totalSpend, 4, RoundingMode.HALF_UP).doubleValue() : 0.0;
        double cpa = totalConversions > 0 ? totalSpend.doubleValue() / totalConversions : 0.0;

        List<Map<String, Object>> stages = List.of(
                funnelStage("曝光展示", "Impressions", totalImpressions, 1.0,
                        "覆盖触达规模", "info"),
                funnelStage("广告点击", "Clicks (CTR)", totalClicks, ctr,
                        ctr < 0.015 ? "CTR 偏低，建议替换高点击素材或缩窄受众" : "点击率表现健康", ctr < 0.015 ? "warn" : "teal"),
                funnelStage("落地页有效互动（暂无观测）", "Engaged Visits", engagedVisits, engageRate,
                        "请导入站点访问观测后分析承接效果", "info"),
                funnelStage("变现转化", "Conversions (CVR)", totalConversions, cvr,
                        cvr < 0.06 ? "转化承接偏弱，建议检查关键词与出价策略" : "变现转化表现良好", cvr < 0.06 ? "warn" : "teal")
        );

        winners.sort(Comparator.comparingDouble((Map<String, Object> m) -> ((BigDecimal) m.get("profit")).doubleValue()).reversed());
        bleeders.sort(Comparator.comparingDouble((Map<String, Object> m) -> ((BigDecimal) m.get("profit")).doubleValue()));

        List<Map<String, Object>> platformStats = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : byPlatform.entrySet()) {
            double[] v = entry.getValue();
            double pRoas = v[0] > 0 ? v[1] / v[0] : 0.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("platform", entry.getKey());
            item.put("spend", round(v[0], 2));
            item.put("revenue", round(v[1], 2));
            item.put("profit", round(v[1] - v[0], 2));
            item.put("roas", round(pRoas, 2));
            item.put("ctr", round(v[2] > 0 ? v[3] / v[2] : 0.0, 4));
            item.put("cvr", round(v[3] > 0 ? v[4] / v[3] : 0.0, 4));
            platformStats.add(item);
        }
        platformStats.sort(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("spend")).reversed());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataSource", cmsData.isLocalWorkspace()?"PLATFORM_DAILY_REPORTS":"SIMULATION");
        result.put("engagementAvailable", false);
        result.put("platform", platform == null || platform.isBlank() ? "ALL" : platform);
        result.put("campaignCount", campaigns.size());
        result.put("totalSpend", totalSpend.setScale(2, RoundingMode.HALF_UP));
        result.put("totalRevenue", totalRevenue.setScale(2, RoundingMode.HALF_UP));
        result.put("totalProfit", totalRevenue.subtract(totalSpend).setScale(2, RoundingMode.HALF_UP));
        result.put("roas", round(overallRoas, 2));
        result.put("ctr", round(ctr, 4));
        result.put("cvr", round(cvr, 4));
        result.put("cpa", round(cpa, 2));
        result.put("stages", stages);
        result.put("platformBreakdown", platformStats);
        result.put("countryBreakdown",byCountry.entrySet().stream().map(entry->{
            var m=new LinkedHashMap<String,Object>();double[] v=entry.getValue();m.put("country",entry.getKey());
            m.put("regionGroup", marketGroup(entry.getKey()));
            m.put("spend",v[0]);m.put("cpc",v[2]>0?v[0]/v[2]:null);m.put("cpm",v[1]>0?v[0]/v[1]*1000:null);return m;
        }).toList());
        result.put("winners", winners.stream().limit(5).toList());
        result.put("bleeders", bleeders.stream().limit(5).toList());
        return result;
    }

    /**
     * Real media plans group markets by region and CPM tier, not into a binary
     * "Tier 1 / everything else". The mapping below follows the usual AFS buying tiers:
     * Tier 1 English markets, Tier 2 Western Europe / JP-KR, Tier 3 volume markets.
     */
    static String marketGroup(String country) {
        String code = country == null ? "" : country.trim().toUpperCase(Locale.ROOT);
        String region;
        int tier;
        switch (code) {
            case "US", "CA" -> { region = "北美"; tier = 1; }
            case "MX" -> { region = "北美"; tier = 2; }
            case "GB", "IE" -> { region = "欧洲"; tier = 1; }
            case "DE", "FR", "IT", "ES", "NL", "BE", "AT", "CH", "SE", "NO", "DK", "FI" -> { region = "欧洲"; tier = 2; }
            case "PL", "CZ", "RO", "HU", "TR", "RU", "UA" -> { region = "欧洲"; tier = 3; }
            case "AU", "NZ", "SG" -> { region = "亚太"; tier = 1; }
            case "JP", "KR", "TW", "HK" -> { region = "亚太"; tier = 2; }
            case "IN", "PH", "ID", "VN", "TH", "MY" -> { region = "亚太"; tier = 3; }
            case "BR", "AR", "CL", "CO" -> { region = "拉美"; tier = 2; }
            case "AE", "SA", "ZA" -> { region = "中东非"; tier = 2; }
            default -> { region = "其他"; tier = 3; }
        }
        return region + " · Tier " + tier;
    }

    // =========================================================================
    // 2. Budget Pacing & Spend Firewall
    // =========================================================================

    Map<String, Object> buildBudgetPacing(String platform) {
        String key = "pacing|" + (platform == null || platform.isBlank() ? "ALL" : platform.toUpperCase(Locale.ROOT));
        return cached(key, 30_000L, () -> computeBudgetPacing(platform));
    }

    private Map<String, Object> computeBudgetPacing(String platform) {
        List<CmsCampaignDataService.CampaignSummary> campaigns = cmsData.listCampaigns("", platform, 200);
        int currentHour = LocalTime.now().getHour();
        double dayProgress = Math.max(0.18, Math.min(0.95, (currentHour + 0.5) / 24.0));

        BigDecimal totalDailyBudget = BigDecimal.ZERO;
        BigDecimal totalTodaySpend = BigDecimal.ZERO;
        int overspendCount = 0;
        int lossBreakerCount = 0;
        int underspendCount = 0;
        int healthyCount = 0;

        List<Map<String, Object>> items = new ArrayList<>();
        for (CmsCampaignDataService.CampaignSummary c : campaigns) {
            BigDecimal spend = nz(c.spend());
            if (spend.signum() <= 0) continue;

            BigDecimal rev = nz(c.revenue());
            BigDecimal profit = rev.subtract(spend).setScale(2, RoundingMode.HALF_UP);
            double roas = rev.divide(spend, 4, RoundingMode.HALF_UP).doubleValue();

            var evidence=cmsData.loadCampaignEvidence(c.campaignId(),c.site(),c.channel(),java.time.LocalDate.now(),java.time.LocalDate.now());
            if(evidence==null || !cmsData.hasPlatformObservation(c,java.time.LocalDate.now())) continue;
            BigDecimal dailyBudget=nz(c.observedBudget());
            if(dailyBudget.signum()<=0) continue;
            BigDecimal todaySpend=evidence.daily().stream().map(CmsReportSource.Day::spend).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal remaining=dailyBudget.subtract(todaySpend).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);
            double spendProgress=todaySpend.doubleValue()/dailyBudget.doubleValue();
            double pacingRatio=spendProgress/dayProgress;

            String firewallStatus;
            String firewallLabel;
            int severityRank;
            if (roas < 0.88 && profit.doubleValue() < -40.0) {
                firewallStatus = "LOSS_BREAKER";
                firewallLabel = "亏损熔断预警";
                severityRank = 0;
                lossBreakerCount++;
            } else if (pacingRatio > 1.20) {
                firewallStatus = "OVERSPEND_ALERT";
                firewallLabel = "消耗过快";
                severityRank = 1;
                overspendCount++;
            } else if (pacingRatio < 0.70) {
                firewallStatus = "UNDERSPEND";
                firewallLabel = "消耗偏慢";
                severityRank = 2;
                underspendCount++;
            } else {
                firewallStatus = "HEALTHY";
                firewallLabel = "平滑正常";
                severityRank = 3;
                healthyCount++;
            }

            totalDailyBudget = totalDailyBudget.add(dailyBudget);
            totalTodaySpend = totalTodaySpend.add(todaySpend);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("campaignId", c.campaignId());
            item.put("name", c.name());
            item.put("platform", c.platform());
            item.put("dailyBudget", dailyBudget);
            item.put("todaySpend", todaySpend);
            item.put("remainingBudget", remaining);
            item.put("spendPct", round(spendProgress * 100.0, 1));
            item.put("pacingRatio", round(pacingRatio, 2));
            item.put("roas", round(roas, 2));
            item.put("profit", profit);
            item.put("firewallStatus", firewallStatus);
            item.put("firewallLabel", firewallLabel);
            item.put("severityRank", severityRank);
            items.add(item);
        }

        items.sort(Comparator
                .comparingInt((Map<String, Object> m) -> (Integer) m.get("severityRank"))
                .thenComparing(Comparator.comparingDouble((Map<String, Object> m) -> ((BigDecimal) m.get("todaySpend")).doubleValue()).reversed()));

        double overallSpendPct = totalDailyBudget.signum() > 0
                ? totalTodaySpend.divide(totalDailyBudget, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;
        double overallPacing = totalDailyBudget.signum() > 0
                ? (totalTodaySpend.doubleValue() / totalDailyBudget.doubleValue()) / dayProgress
                : 1.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dayProgressPct", round(dayProgress * 100.0, 1));
        summary.put("totalDailyBudget", totalDailyBudget.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalTodaySpend", totalTodaySpend.setScale(2, RoundingMode.HALF_UP));
        summary.put("overallSpendPct", round(overallSpendPct, 1));
        summary.put("overallPacingRatio", round(overallPacing, 2));
        summary.put("lossBreakerCount", lossBreakerCount);
        summary.put("overspendCount", overspendCount);
        summary.put("underspendCount", underspendCount);
        summary.put("healthyCount", healthyCount);
        summary.put("observedCampaigns", items.size());
        summary.put("dataAvailable", !items.isEmpty());
        summary.put("firewallHealth", items.isEmpty() ? "NO_DATA"
                : (lossBreakerCount > 0 ? "ALERT" : (overspendCount > 0 ? "WATCH" : "SAFE")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("items", items.stream().limit(15).toList());
        result.put("dataAvailable", !items.isEmpty());
        return result;
    }

    // =========================================================================
    // 3. Real-time Alert Rules & Event History
    // =========================================================================

    void ensureDefaultAlertRules() {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM ad_alert_rule", Integer.class);
        if (count != null && count > 0) return;
        db.update("""
                INSERT INTO ad_alert_rule(name, metric, operator, threshold, severity, channel, webhook_url, enabled, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "ROAS 跌破止损红线", "ROAS", "LT", new BigDecimal("1.0000"), "CRITICAL", "FEISHU",
                "https://open.feishu.cn/open-apis/bot/v2/hook/afs-ops-alert", true, "system");
        db.update("""
                INSERT INTO ad_alert_rule(name, metric, operator, threshold, severity, channel, webhook_url, enabled, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "单活动累计亏损超阈值", "LOSS", "GT", new BigDecimal("80.0000"), "CRITICAL", "FEISHU",
                "https://open.feishu.cn/open-apis/bot/v2/hook/afs-ops-alert", true, "system");
        db.update("""
                INSERT INTO ad_alert_rule(name, metric, operator, threshold, severity, channel, webhook_url, enabled, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "资金防火墙：预算超速消耗", "PACING", "GT", new BigDecimal("1.2000"), "WARN", "DINGTALK",
                "https://oapi.dingtalk.com/robot/send?access_token=afs-pacing-guard", true, "system");
        db.update("""
                INSERT INTO ad_alert_rule(name, metric, operator, threshold, severity, channel, webhook_url, enabled, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "高潜放量明星提醒", "ROAS", "GTE", new BigDecimal("1.3500"), "INFO", "WECOM",
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=afs-scale-notify", true, "system");
    }

    List<Map<String, Object>> listAlertRules() {
        ensureDefaultAlertRules();
        return db.query("""
                SELECT id, name, metric, operator, threshold, severity, channel, webhook_url, enabled, updated_by, updated_at
                FROM ad_alert_rule ORDER BY id ASC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("metric", rs.getString("metric"));
            m.put("operator", rs.getString("operator"));
            m.put("threshold", rs.getBigDecimal("threshold"));
            m.put("severity", rs.getString("severity"));
            m.put("channel", rs.getString("channel"));
            m.put("webhookUrl", rs.getString("webhook_url"));
            m.put("enabled", rs.getBoolean("enabled"));
            m.put("updatedBy", rs.getString("updated_by"));
            m.put("updatedAt", String.valueOf(rs.getObject("updated_at")));
            return m;
        });
    }

    Map<String, Object> saveAlertRule(Long id, String name, String metric, String operator,
                                      BigDecimal threshold, String severity, String channel,
                                      String webhookUrl, boolean enabled, String actor) {
        ensureDefaultAlertRules();
        String safeName = name == null || name.isBlank() ? "自定义投放告警" : name.strip();
        String safeMetric = requireEnum(metric, List.of("ROAS", "LOSS", "PACING", "SPEND"), "metric");
        String safeOp = requireEnum(operator, List.of("LT", "LTE", "GT", "GTE"), "operator");
        String safeSev = requireEnum(severity, List.of("CRITICAL", "WARN", "INFO"), "severity");
        String safeChan = requireEnum(channel, List.of("FEISHU", "DINGTALK", "WECOM", "WEBHOOK", "INBOX"), "channel");
        BigDecimal safeThreshold = threshold == null ? BigDecimal.ONE : threshold;
        String safeWebhook = webhookUrl == null ? "" : webhookUrl.strip();

        if (id != null && id > 0) {
            int updated = db.update("""
                    UPDATE ad_alert_rule
                    SET name=?, metric=?, operator=?, threshold=?, severity=?, channel=?, webhook_url=?, enabled=?, updated_by=?, updated_at=CURRENT_TIMESTAMP
                    WHERE id=?
                    """, safeName, safeMetric, safeOp, safeThreshold, safeSev, safeChan, safeWebhook, enabled, actor, id);
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警规则不存在");
            }
        } else {
            db.update("""
                    INSERT INTO ad_alert_rule(name, metric, operator, threshold, severity, channel, webhook_url, enabled, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, safeName, safeMetric, safeOp, safeThreshold, safeSev, safeChan, safeWebhook, enabled, actor);
        }
        return Map.of("saved", true, "rules", listAlertRules());
    }

    void deleteAlertRule(long id) {
        db.update("DELETE FROM ad_alert_rule WHERE id = ?", id);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> scanAndTriggerAlerts(String actor) {
        ensureDefaultAlertRules();
        List<Map<String, Object>> rules = listAlertRules().stream()
                .filter(r -> Boolean.TRUE.equals(r.get("enabled")))
                .toList();
        Map<String, Object> pacingData = buildBudgetPacing("");
        List<Map<String, Object>> pacingItems = (List<Map<String, Object>>) pacingData.get("items");

        int triggered = 0;
        for (Map<String, Object> rule : rules) {
            long ruleId = ((Number) rule.get("id")).longValue();
            String ruleName = String.valueOf(rule.get("name"));
            String metric = String.valueOf(rule.get("metric"));
            String op = String.valueOf(rule.get("operator"));
            double threshold = ((BigDecimal) rule.get("threshold")).doubleValue();
            String severity = String.valueOf(rule.get("severity"));
            String channel = String.valueOf(rule.get("channel"));

            for (Map<String, Object> item : pacingItems) {
                String cid = String.valueOf(item.get("campaignId"));
                String cname = String.valueOf(item.get("name"));
                String plat = String.valueOf(item.get("platform"));
                double roas = ((Number) item.get("roas")).doubleValue();
                double profit = ((BigDecimal) item.get("profit")).doubleValue();
                double loss = profit < 0 ? Math.abs(profit) : 0.0;
                double pacing = ((Number) item.get("pacingRatio")).doubleValue();
                double todaySpend = ((BigDecimal) item.get("todaySpend")).doubleValue();

                double val = switch (metric) {
                    case "ROAS" -> roas;
                    case "LOSS" -> loss;
                    case "PACING" -> pacing;
                    case "SPEND" -> todaySpend;
                    default -> roas;
                };

                if (!matchesCondition(val, op, threshold)) continue;

                // Deduplicate active open events for same rule + campaign
                Integer exists = db.queryForObject(
                        "SELECT COUNT(*) FROM ad_alert_event WHERE rule_id = ? AND campaign_id = ? AND status = 'OPEN'",
                        Integer.class, ruleId, cid);
                if (exists != null && exists > 0) continue;

                String msg = String.format(Locale.ROOT, "[%s] 活动 %s (%s) 触发「%s」：当前 %s=%.2f（阈值 %s %.2f）",
                        severity, cname, plat, ruleName, metric, val, opSymbol(op), threshold);
                String delivery = "INBOX".equals(channel) ? "INBOX_ONLY" : "NOT_SENT";
                db.update("""
                        INSERT INTO ad_alert_event(rule_id, rule_name, campaign_id, campaign_name, platform, severity,
                                                   metric_value, threshold, message, channel, delivery_status, status, handled_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)
                        """, ruleId, ruleName, cid, cname, plat, severity,
                        BigDecimal.valueOf(round(val, 4)), BigDecimal.valueOf(round(threshold, 4)),
                        msg, channel, delivery, actor);
                triggered++;
                if (triggered >= 12) break;
            }
        }

        List<Map<String, Object>> events = listAlertEvents("ALL");
        long openCount = events.stream().filter(e -> "OPEN".equals(e.get("status"))).count();
        boolean dataAvailable = pacingData.get("dataAvailable") == null || Boolean.TRUE.equals(pacingData.get("dataAvailable"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scannedRules", rules.size());
        result.put("newlyTriggered", triggered);
        result.put("openCount", openCount);
        result.put("dataAvailable", dataAvailable);
        if (!dataAvailable) {
            result.put("message", "今日尚无平台观测数据，已跳过告警评估（不做 SAFE 结论）");
        }
        result.put("scannedAt", OffsetDateTime.now().toString());
        result.put("events", events);
        return result;
    }

    List<Map<String, Object>> listAlertEvents(String statusFilter) {
        ensureDefaultAlertRules();
        String sql = "SELECT id, rule_id, rule_name, campaign_id, campaign_name, platform, severity, metric_value, threshold, message, channel, delivery_status, status, handled_by, created_at FROM ad_alert_event";
        List<Object> params = new ArrayList<>();
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sql += " WHERE status = ?";
            params.add(statusFilter.toUpperCase(Locale.ROOT));
        }
        sql += " ORDER BY id DESC LIMIT 60";
        return db.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("ruleId", rs.getObject("rule_id"));
            m.put("ruleName", rs.getString("rule_name"));
            m.put("campaignId", rs.getString("campaign_id"));
            m.put("campaignName", rs.getString("campaign_name"));
            m.put("platform", rs.getString("platform"));
            m.put("severity", rs.getString("severity"));
            m.put("metricValue", rs.getBigDecimal("metric_value"));
            m.put("threshold", rs.getBigDecimal("threshold"));
            m.put("message", rs.getString("message"));
            m.put("channel", rs.getString("channel"));
            m.put("deliveryStatus", rs.getString("delivery_status"));
            m.put("status", rs.getString("status"));
            m.put("handledBy", rs.getString("handled_by"));
            m.put("createdAt", String.valueOf(rs.getObject("created_at")));
            return m;
        }, params.toArray());
    }

    Map<String, Object> acknowledgeAlertEvent(long id, String status, String actor) {
        String nextStatus = normalizeEnum(status, List.of("RESOLVED", "IGNORED", "OPEN"), "RESOLVED");
        int updated = db.update("UPDATE ad_alert_event SET status = ?, handled_by = ? WHERE id = ?", nextStatus, actor, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "告警记录不存在");
        }
        return Map.of("id", id, "status", nextStatus, "handledBy", actor);
    }

    // =========================================================================
    // 4. 7x24 Dayparting Schedule Matrix
    // =========================================================================

    void ensureDefaultSchedules() {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM ad_dayparting_schedule", Integer.class);
        if (count != null && count > 0) return;
        db.update("""
                INSERT INTO ad_dayparting_schedule(scope_key, label, platform, timezone, matrix_bits, preset_name, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "global:default", "全局默认投放排期", "ALL", "UTC+8", buildPresetBits("AI_OPTIMAL"), "AI 推荐黄金时段", "system");
        db.update("""
                INSERT INTO ad_dayparting_schedule(scope_key, label, platform, timezone, matrix_bits, preset_name, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "platform:Google", "Google 搜索与展示排期", "Google", "UTC-5 (美东)", buildPresetBits("WORKDAY_PRIME"), "工作日黄金时段", "system");
        db.update("""
                INSERT INTO ad_dayparting_schedule(scope_key, label, platform, timezone, matrix_bits, preset_name, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "platform:Facebook", "Meta 信息流投放排期", "Facebook", "UTC-5 (美东)", buildPresetBits("EVENING_PEAK"), "欧美晚高峰", "system");
    }

    List<Map<String, Object>> listDaypartingSchedules() {
        ensureDefaultSchedules();
        return db.query("""
                SELECT scope_key, label, platform, timezone, matrix_bits, preset_name, updated_by, updated_at
                FROM ad_dayparting_schedule ORDER BY scope_key ASC
                """, (rs, i) -> {
            String bits = rs.getString("matrix_bits");
            int activeHours = (int) bits.chars().filter(ch -> ch == '1').count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scopeKey", rs.getString("scope_key"));
            m.put("label", rs.getString("label"));
            m.put("platform", rs.getString("platform"));
            m.put("timezone", rs.getString("timezone"));
            m.put("matrixBits", bits);
            m.put("activeHours", activeHours);
            m.put("presetName", rs.getString("preset_name"));
            m.put("updatedBy", rs.getString("updated_by"));
            m.put("updatedAt", String.valueOf(rs.getObject("updated_at")));
            return m;
        });
    }

    Map<String, Object> saveDaypartingSchedule(String scopeKey, String label, String platform,
                                               String timezone, String matrixBits, String presetName, String actor) {
        ensureDefaultSchedules();
        String key = scopeKey == null || scopeKey.isBlank() ? "global:default" : scopeKey.strip();
        String cleanBits = matrixBits == null ? "" : matrixBits.replaceAll("[^01]", "");
        if (cleanBits.length() != 168) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排期矩阵必须为 168 位（7天×24小时）0/1 序列");
        }
        String safeLabel = label == null || label.isBlank() ? key : label.strip();
        String safePlat = platform == null || platform.isBlank() ? "ALL" : platform.strip();
        String safeTz = timezone == null || timezone.isBlank() ? "UTC+8" : timezone.strip();
        String safePreset = presetName == null ? "自定义排期" : presetName.strip();

        int updated = db.update("""
                UPDATE ad_dayparting_schedule
                SET label=?, platform=?, timezone=?, matrix_bits=?, preset_name=?, updated_by=?, updated_at=CURRENT_TIMESTAMP
                WHERE scope_key=?
                """, safeLabel, safePlat, safeTz, cleanBits, safePreset, actor, key);
        if (updated == 0) {
            db.update("""
                    INSERT INTO ad_dayparting_schedule(scope_key, label, platform, timezone, matrix_bits, preset_name, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, key, safeLabel, safePlat, safeTz, cleanBits, safePreset, actor);
        }
        int activeHours = (int) cleanBits.chars().filter(ch -> ch == '1').count();
        return Map.of("saved", true, "scopeKey", key, "activeHours", activeHours, "schedules", listDaypartingSchedules());
    }

    static String buildPresetBits(String preset) {
        StringBuilder sb = new StringBuilder(168);
        for (int day = 0; day < 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                boolean on = switch (preset) {
                    case "ALL_DAY" -> true;
                    case "WORKDAY_PRIME" -> day < 5 && hour >= 9 && hour <= 21;
                    case "EVENING_PEAK" -> (hour >= 18 && hour <= 23) || (day >= 5 && hour >= 10 && hour <= 23);
                    case "AI_OPTIMAL" -> (hour >= 8 && hour <= 23) && !(day == 0 && hour < 10);
                    default -> false;
                };
                sb.append(on ? '1' : '0');
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // 5. Batch Campaign Operations & Quick Clone
    // =========================================================================

    Map<String, Object> batchOperateCampaigns(List<String> adResourceIds, String action, Integer ruleId, String state, String actor) {
        if (adResourceIds == null || adResourceIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少选择一个广告活动");
        }
        if (!cmsData.isLocalWorkspace()) {
            return Map.of("affected", adResourceIds.size(), "action", action, "simulated", true);
        }
        int affected = 0;
        String op = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        for (String id : adResourceIds) {
            if (id == null || id.isBlank()) continue;
            switch (op) {
                case "BIND_RULE" -> {
                    if (ruleId == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要绑定的投放规则");
                    }
                    affected += db.update("UPDATE ad_resource SET rule_id = ?, utime = CURRENT_TIMESTAMP WHERE ad_resource_id = ? AND status = 1", ruleId, id.strip());
                }
                case "UNBIND_RULE" -> {
                    affected += db.update("UPDATE ad_resource SET rule_id = 0, utime = CURRENT_TIMESTAMP WHERE ad_resource_id = ? AND status = 1", id.strip());
                }
                case "SET_STATE" -> {
                    String safeState = normalizeEnum(state, List.of("PASS", "AUDITING", "ARCHIVE", "REJECT"), "PASS");
                    affected += db.update("UPDATE ad_resource SET state = ?, utime = CURRENT_TIMESTAMP WHERE ad_resource_id = ? AND status = 1", safeState, id.strip());
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的批量操作类型: " + action);
            }
        }
        return Map.of("affected", affected, "action", op, "actor", actor);
    }

    Map<String, Object> cloneCampaign(String adResourceId, String actor) {
        if (adResourceId == null || adResourceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少源活动标识");
        }
        if (!cmsData.isLocalWorkspace()) {
            String newId = adResourceId + "_COPY";
            return Map.of("cloned", true, "sourceId", adResourceId, "newAdResourceId", newId, "simulated", true);
        }
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM ad_resource WHERE ad_resource_id = ? AND status = 1 LIMIT 1", adResourceId.strip());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到源广告活动: " + adResourceId);
        }
        Map<String, Object> src = rows.get(0);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
        String newResId = (adResourceId.length() > 20 ? adResourceId.substring(0, 20) : adResourceId) + "_C" + suffix;
        String origCampaign = String.valueOf(src.getOrDefault("CAMPAIGN", src.getOrDefault("TITLE", adResourceId)));
        String newCampaign = origCampaign + "_COPY_" + suffix;

        db.update("""
                INSERT INTO ad_resource(account_id, ad_resource_id, title, article_id, channel_id, campaign, am,
                                        ad_platform, land_page_url, ad_desc, audit_msg, partner, media_list, state,
                                        status, country, keyword, ad_account, ad_spend, gross_revenue, profit, roi,
                                        rule_id, ad_platform_info, ad_platform_state, ad_platform_msg)
                VALUES (?, ?, ?, ?, '', ?, ?, ?, '', ?, ?, ?, ?, 'AUDITING', 1, ?, ?, ?, 0, 0, 0, 0, NULL, '', 'LOCAL_ONLY', ?)
                """,
                src.get("ACCOUNT_ID"), newResId, newCampaign, src.get("ARTICLE_ID"),
                newCampaign, src.get("AM"), src.get("AD_PLATFORM"),
                src.get("AD_DESC"), "由 " + actor + " 从 " + adResourceId + " 一键复制，需重新绑定独立 channel", src.get("PARTNER"),
                src.get("MEDIA_LIST"), src.get("COUNTRY"), src.get("KEYWORD"), src.get("AD_ACCOUNT"),
                "已复制待绑定新渠道");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cloned", true);
        result.put("sourceId", adResourceId);
        result.put("newAdResourceId", newResId);
        result.put("campaignName", newCampaign);
        return result;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Map<String, Object> funnelStage(String name, String enName, long value, double rate, String insight, String tone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("enName", enName);
        m.put("value", value);
        m.put("rate", round(rate, 4));
        m.put("insight", insight);
        m.put("tone", tone);
        return m;
    }

    private static boolean matchesCondition(double value, String op, double threshold) {
        return switch (op) {
            case "LT" -> value < threshold;
            case "LTE" -> value <= threshold;
            case "GT" -> value > threshold;
            case "GTE" -> value >= threshold;
            default -> false;
        };
    }

    private static String opSymbol(String op) {
        return switch (op) {
            case "LT" -> "<";
            case "LTE" -> "≤";
            case "GT" -> ">";
            case "GTE" -> "≥";
            default -> op;
        };
    }

    private static String normalizeEnum(String val, List<String> allowed, String fallback) {
        if (val == null || val.isBlank()) return fallback;
        String upper = val.strip().toUpperCase(Locale.ROOT);
        return allowed.contains(upper) ? upper : fallback;
    }

    private static String requireEnum(String val, List<String> allowed, String field) {
        if (val == null || val.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 " + field);
        }
        String upper = val.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的 " + field + "：" + val + "，可选值 " + allowed);
        }
        return upper;
    }

    private static double estimateCpc(String platform) {
        if (platform == null) return 0.42;
        String p = platform.toLowerCase(Locale.ROOT);
        if (p.contains("google")) return 0.48;
        if (p.contains("facebook") || p.contains("meta")) return 0.36;
        if (p.contains("tiktok")) return 0.28;
        return 0.40;
    }

    private static double estimateCtr(String platform) {
        if (platform == null) return 0.024;
        String p = platform.toLowerCase(Locale.ROOT);
        if (p.contains("google")) return 0.032;
        if (p.contains("facebook") || p.contains("meta")) return 0.021;
        if (p.contains("tiktok")) return 0.018;
        return 0.024;
    }

    private static double estimateCvr(String platform, double roas) {
        double base = 0.085;
        if (roas >= 1.3) base = 0.125;
        else if (roas >= 1.0) base = 0.092;
        else if (roas > 0) base = 0.055;
        return base;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static double round(double v, int scale) {
        if (!Double.isFinite(v)) return 0.0;
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
