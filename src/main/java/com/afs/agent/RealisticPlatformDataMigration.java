package com.afs.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the legacy robotic synthetic workspace fixture with platform-authentic,
 * mathematically reconciled overseas AFS (TikTok / Meta / Google / Taboola + AdSense)
 * campaign, creative, article, rule, account, operator, feedback, and daily performance data.
 *
 * <p>Preserves {@code local-admin}, archived operators, imported archive articles/metrics,
 * {@code channel_credential} secrets, {@code adsense_account} publisher bindings,
 * {@code public_ad_case_metric}, and user/AI-analyzed {@code optimization_run} records.
 */
@Order(600)
class RealisticPlatformDataMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RealisticPlatformDataMigration.class);
    static final String MIGRATION = "realistic-platform-data-v1";
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final OptimizationController optimizer;

    RealisticPlatformDataMigration(JdbcTemplate db, ObjectMapper json, OptimizationController optimizer) {
        this.db = db;
        this.json = json;
        this.optimizer = optimizer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        if (!hasMarker("controller-site-bindings-v5") || hasMarker(MIGRATION)) {
            return;
        }
        List<FixtureCampaign> fixtures = loadFixtureCampaigns();
        if (fixtures.isEmpty()) {
            db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
            return;
        }

        LocalDate end = LocalDate.now(REPORT_ZONE).minusDays(1);
        LocalDate start = end.minusDays(PlatformEconomics.ACTIVE_DAYS - 1);
        log.info("Migrating {} local workspace campaigns to platform-authentic AFS data ({}..{})",
                fixtures.size(), start, end);

        Map<Integer, Long> accountDbIds = rebuildOperatorsAndAccounts();
        Map<String, Long> ruleIds = rebuildReusableRules(fixtures);
        rebuildCategories();
        clearLegacyFixtureDailyRows(fixtures);

        Map<Integer, List<String>> siteArticles = new LinkedHashMap<>();
        List<SeedRunCandidate> runCandidates = new ArrayList<>();

        for (FixtureCampaign fx : fixtures) {
            int siteIndex = fx.sequence() / PlatformEconomics.SLOTS_PER_SITE;
            int slot = fx.sequence() % PlatformEconomics.SLOTS_PER_SITE;
            if (siteIndex < 0 || siteIndex >= PlatformEconomics.SITE_COUNT) continue;

            String site = PlatformEconomics.siteCode(siteIndex);
            var vertical = PlatformEconomics.vertical(siteIndex);
            var account = PlatformEconomics.account(siteIndex);
            var country = PlatformEconomics.countryForSlot(slot);
            var subtopic = PlatformEconomics.subtopic(siteIndex, slot);
            String operator = PlatformEconomics.operatorForCampaign(siteIndex, slot);
            String campaignName = PlatformEconomics.campaignName(siteIndex, slot);
            String articleTitle = PlatformEconomics.articleTitle(siteIndex, slot);
            String landingUrl = PlatformEconomics.landingPageUrl(siteIndex, slot, fx.channelId());
            String state = PlatformEconomics.campaignState(slot);
            String ruleKey = PlatformEconomics.ruleKeyForCampaign(siteIndex, slot);
            Long ruleId = ruleKey == null ? null : ruleIds.get(ruleKey);
            var creative = PlatformEconomics.creativeForCampaign(siteIndex, slot);
            String materialId = "afs-material-%03d".formatted(fx.sequence() + 1);
            String adId = "afs-ad-%03d".formatted(fx.sequence() + 1);
            String appId = "afs-app-%02d".formatted(siteIndex + 1);
            String publisher = "pub-" + (7_420_581_639_025_100L + (siteIndex + 1) * 137L);

            siteArticles.computeIfAbsent(siteIndex, k -> new ArrayList<>()).add(fx.articleId());

            // 1. Article & keywords & crawl content
            String lead = PlatformEconomics.articleLead(siteIndex, slot);
            String bodyJson = json.writeValueAsString(PlatformEconomics.articleParagraphs(siteIndex, slot));
            int views = 680 + (fx.sequence() * 137) % 4200;
            String otherInfo = json.writeValueAsString(Map.of(
                    "author", operator, "views", views, "vertical", vertical.categorySlug(), "country", country.code()));
            db.update("""
                    UPDATE article_info
                    SET title = ?, terms = ?, category = ?, first_part = ?, text_list = ?,
                        lang = ?, state = ?, site = ?, partner = ?, other_info = ?, utime = ?
                    WHERE article_id = ?
                    """, articleTitle, subtopic.keyword(), vertical.categorySlug(), lead, bodyJson,
                    country.lang(), state, site, PlatformEconomics.PARTNER, otherInfo,
                    Timestamp.valueOf(end.atTime(10 + slot % 8, (fx.sequence() * 7) % 60)), fx.articleId());

            db.update("UPDATE crawl_content SET content = ?, category = ? WHERE fid = ?",
                    bodyJson, vertical.categorySlug(), "afs-crawl-%03d".formatted(fx.sequence() + 1));

            db.update("INSERT INTO keyword_report_data (pd, article_id, keyword) VALUES (?, ?, ?)",
                    end.toString(), fx.articleId(), subtopic.keyword());
            db.update("INSERT INTO google_bigquery_keyword_data (pd, article_id, key_word, `count`) VALUES (?, ?, ?, ?)",
                    end.toString(), fx.articleId(), subtopic.keyword(), 45 + (fx.sequence() * 29) % 380);

            // 2. Platform-accurate creative material & ad copy & ad placement
            db.update("""
                    UPDATE ad_material
                    SET name = ?, user_name = ?, country = ?, lang = ?, type = ?, dimension = ?, ratio = ?,
                        category = ?, template = ?, cover = ?, url = ?, size = ?, duration = ?, utime = ?
                    WHERE ad_material_id = ?
                    """, campaignName, operator, country.code(), country.lang(), creative.type(),
                    creative.dimension(), creative.ratio(), vertical.categorySlug(), creative.template(),
                    PlatformEconomics.materialCover(siteIndex, materialId, creative),
                    PlatformEconomics.materialUrl(siteIndex, materialId, creative),
                    creative.sizeBytes(), creative.duration(),
                    Timestamp.valueOf(end.atTime(9 + slot % 9, (fx.sequence() * 11) % 60)), materialId);

            db.update("INSERT INTO ad_text (name, ad_text) VALUES (?, ?)",
                    campaignName, json.writeValueAsString(PlatformEconomics.adCopy(siteIndex, slot)));

            db.update("UPDATE ad_info SET name = ? WHERE ad_id = ?", campaignName, adId);

            // 3. Daily performance & reconciled ad_resource totals
            PlatformEconomics.CampaignTotals totals;
            String platformState;
            String auditMsg = "";
            ObjectNode platformInfo = parseOrCreateObject(fx.platformInfo());
            platformInfo.put("localCampaignId", fx.campaignId());
            platformInfo.remove("platformCampaign");

            if (PlatformEconomics.isRunningActive(slot)) {
                List<PlatformEconomics.DailyRow> series = PlatformEconomics.generateDailySeries(
                        siteIndex, slot, start, PlatformEconomics.ACTIVE_DAYS);
                totals = PlatformEconomics.summarize(series);
                insertDailyMetrics(fx, siteIndex, slot, site, appId, adId, publisher,
                        vertical.platform(), account.accountId(), country.code(), campaignName, landingUrl, series);
                platformState = "PLATFORM_ACTIVE";
                ObjectNode synced = json.createObjectNode();
                synced.put("id", fx.campaignId());
                synced.put("platform", vertical.platform());
                synced.put("status", "ACTIVE");
                synced.put("dailyBudget", totals.suggestedDailyBudget());
                synced.put("syncedAt", start.atTime(8, 30).toString());
                platformInfo.set("platformCampaign", synced);
                platformInfo.put("campaignBudget", totals.suggestedDailyBudget());
                platformInfo.put("optimizationGoal", "CONVERSION");
                platformInfo.put("optimizationEvent", "SEARCH_AD_CLICK");

                if (ruleId != null) {
                    insertRealisticRuleLogs(fx, campaignName, ruleKey, ruleId, series);
                }
                if (slot == 0 || slot == 2 || slot == 6 || slot == 12) {
                    runCandidates.add(new SeedRunCandidate(site, fx.channelId(), fx.campaignId(),
                            vertical.platform(), operator, start, end, totals, series));
                }
            } else if ("ARCHIVE".equals(state)) {
                List<PlatformEconomics.DailyRow> historical = PlatformEconomics.generateDailySeries(
                        siteIndex, slot, start.minusDays(21), PlatformEconomics.ACTIVE_DAYS);
                totals = PlatformEconomics.summarize(historical);
                platformState = "PLATFORM_PAUSED";
                ObjectNode synced = json.createObjectNode();
                synced.put("id", fx.campaignId());
                synced.put("platform", vertical.platform());
                synced.put("status", "PAUSED");
                synced.put("dailyBudget", totals.suggestedDailyBudget());
                synced.put("syncedAt", start.minusDays(21).atTime(9, 0).toString());
                platformInfo.set("platformCampaign", synced);
            } else if ("REJECT".equals(state)) {
                totals = new PlatformEconomics.CampaignTotals(0, 0, 0,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, BigDecimal.ZERO);
                platformState = "PLATFORM_REJECTED";
                auditMsg = PlatformEconomics.REJECTION_REASONS.get(siteIndex % PlatformEconomics.REJECTION_REASONS.size());
            } else {
                // Ready-to-publish PASS (slot 16) or AUDITING (slot 17)
                totals = new PlatformEconomics.CampaignTotals(0, 0, 0,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, BigDecimal.ZERO);
                platformState = "LOCAL_ONLY";
            }

            String desc = "%s %s %s 投放活动 · %s".formatted(
                    vertical.platform(), country.code(), vertical.categoryLabel(), subtopic.keyword());
            Timestamp createdAt = Timestamp.valueOf(start.minusDays(slot == 19 ? 21 : 2).atTime(9 + slot % 6, (fx.sequence() * 5) % 60));
            Timestamp updatedAt = Timestamp.valueOf(end.atTime(14 + slot % 8, (fx.sequence() * 13) % 60));

            db.update("""
                    UPDATE ad_resource
                    SET campaign = ?, title = ?, land_page_url = ?, ad_desc = ?, audit_msg = ?, state = ?,
                        rule_id = ?, country = ?, keyword = ?, ad_account = ?, account_id = ?,
                        ad_platform_state = ?, ad_platform_info = ?, am = ?,
                        ad_spend = ?, gross_revenue = ?, profit = ?, roi = ?, ctime = ?, utime = ?
                    WHERE ad_resource_id = ?
                    """, campaignName, articleTitle, landingUrl, desc, auditMsg, state,
                    ruleId, country.code(), subtopic.keyword(), account.accountId(), account.accountId(),
                    platformState, platformInfo.toString(), operator,
                    totals.adSpend().doubleValue(), totals.grossRevenue().doubleValue(),
                    totals.profit().doubleValue(), totals.roas(), createdAt, updatedAt, fx.campaignId());

            if ("TikTok".equals(vertical.platform())) {
                String adGroupStatus = PlatformEconomics.isRunningActive(slot) ? "ENABLE" : "DISABLE";
                double budget = totals.suggestedDailyBudget().signum() > 0
                        ? totals.suggestedDailyBudget().doubleValue() : 120.0;
                db.update("""
                        UPDATE tiktok_ad_group_info
                        SET ad_group_name = ?, campaign_name = ?, advertiser = ?, ad_group_status = ?,
                            budget = ?, conversion = ?, gross_revenue = ?, ad_spend = ?, profit = ?, roi = ?
                        WHERE ad_resource_id = ?
                        """, campaignName + "_AG1", campaignName, account.accountId(), adGroupStatus,
                        budget, totals.adConversions(), totals.grossRevenue().doubleValue(),
                        totals.adSpend().doubleValue(), totals.profit().doubleValue(), totals.roas(),
                        fx.campaignId());
            }
        }

        updateSiteAppsAndConfigs(siteArticles, accountDbIds);
        rebuildLocalizedFeedback(end);
        refreshSeededOptimizationRuns(runCandidates);

        db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
        log.info("Completed {} migration cleanly.", MIGRATION);
    }

    private boolean hasMarker(String name) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, name);
        return count != null && count > 0;
    }

    private List<FixtureCampaign> loadFixtureCampaigns() {
        return db.query("""
                SELECT ad_resource_id, article_id, channel_id, campaign, ad_platform_info
                FROM ad_resource
                WHERE article_id LIKE 'afs-article-%' AND partner = ?
                ORDER BY article_id
                """, (rs, idx) -> {
            String articleId = rs.getString("article_id");
            int seq = Integer.parseInt(articleId.substring("afs-article-".length())) - 1;
            return new FixtureCampaign(seq, rs.getString("ad_resource_id"), articleId,
                    rs.getString("channel_id"), rs.getString("campaign"), rs.getString("ad_platform_info"));
        }, PlatformEconomics.PARTNER);
    }

    private Map<Integer, Long> rebuildOperatorsAndAccounts() throws JsonProcessingException {
        // Remove robotic disabled afs-operator-001..240 rows while preserving local-admin and archived users.
        db.update("DELETE FROM user_access WHERE user_name LIKE 'afs-operator-%'");
        db.update("DELETE FROM `user` WHERE name LIKE 'afs-operator-%' AND partner = ?", PlatformEconomics.PARTNER);

        // Replace 240 robotic ad accounts with 12 clean platform ad accounts.
        db.update("""
                DELETE FROM ad_account_info
                WHERE partner = ?
                  AND (user_name LIKE 'afs-operator-%'
                       OR account_id LIKE '7100000000000000%'
                       OR account_id LIKE '987650000%'
                       OR account_id LIKE '700000000%'
                       OR account_id LIKE '1208497000000%')
                """, PlatformEconomics.PARTNER);

        Map<Integer, Long> accountDbIds = new LinkedHashMap<>();
        Map<String, List<Long>> siteAccountIds = new LinkedHashMap<>();
        for (var acc : PlatformEconomics.AD_ACCOUNTS) {
            Integer exists = db.queryForObject("SELECT COUNT(*) FROM ad_account_info WHERE account_id = ?",
                    Integer.class, acc.accountId());
            if (exists == null || exists == 0) {
                String info = json.writeValueAsString(Map.of(
                        "currency", "USD", "timezone", "America/Los_Angeles", "site", acc.site(), "tier", "Agency"));
                db.update("""
                        INSERT INTO ad_account_info (name, user_name, partner, account_id, ad_platform, info, status)
                        VALUES (?, ?, ?, ?, ?, ?, 1)
                        """, acc.name(), acc.operator(), PlatformEconomics.PARTNER,
                        acc.accountId(), acc.platform(), info);
            }
            Long dbId = db.queryForObject("SELECT id FROM ad_account_info WHERE account_id = ? LIMIT 1",
                    Long.class, acc.accountId());
            accountDbIds.put(acc.siteIndex(), dbId);
            siteAccountIds.computeIfAbsent(acc.site(), k -> new ArrayList<>()).add(dbId);
        }

        for (var op : PlatformEconomics.OPERATORS) {
            List<Long> opAccounts = new ArrayList<>();
            for (String site : op.sites()) {
                opAccounts.addAll(siteAccountIds.getOrDefault(site, List.of()));
            }
            String sitesJson = json.writeValueAsString(op.sites());
            String accountsJson = json.writeValueAsString(opAccounts);
            Integer exists = db.queryForObject("SELECT COUNT(*) FROM `user` WHERE name = ?", Integer.class, op.name());
            if (exists == null || exists == 0) {
                db.update("""
                        INSERT INTO `user` (name, password, partner, root, status, `desc`,
                                            auto_article, auto_campaign, show_material, site_list, ad_account_list)
                        VALUES (?, ?, ?, 0, 1, ?, TRUE, TRUE, TRUE, ?, ?)
                        """, op.name(), "ops-" + UUID.randomUUID(), PlatformEconomics.PARTNER,
                        op.desc(), sitesJson, accountsJson);
            } else {
                db.update("""
                        UPDATE `user` SET partner = ?, status = 1, `desc` = ?,
                                          site_list = ?, ad_account_list = ?
                        WHERE name = ?
                        """, PlatformEconomics.PARTNER, op.desc(), sitesJson, accountsJson, op.name());
            }
            for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
                if (op.sites().contains(PlatformEconomics.siteCode(siteIndex))) {
                    String appId = "afs-app-%02d".formatted(siteIndex + 1);
                    db.update("MERGE INTO user_access (user_name, app_id, access) KEY(user_name, app_id) VALUES (?, ?, 1)",
                            op.name(), appId);
                }
            }
        }
        return accountDbIds;
    }

    private Map<String, Long> rebuildReusableRules(List<FixtureCampaign> fixtures) throws JsonProcessingException {
        for (FixtureCampaign fx : fixtures) {
            db.update("UPDATE ad_resource SET rule_id = NULL WHERE ad_resource_id = ?", fx.campaignId());
        }
        db.update("DELETE FROM ad_resource_rule WHERE creator LIKE 'afs-operator-%'");

        Map<String, Long> ids = new LinkedHashMap<>();
        for (var spec : PlatformEconomics.RULES) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("action", spec.action());
            detail.put("condition", spec.condition());
            if (!spec.budgetAction().isEmpty()) {
                detail.put("budgetAction", spec.budgetAction());
                detail.put("budgetPercent", spec.budgetPercent());
            }
            String ruleJson = json.writeValueAsString(List.of(Map.of(
                    "dayStart", 1, "dayEnd", 30, "detailList", List.of(detail))));
            Integer exists = db.queryForObject("SELECT COUNT(*) FROM ad_resource_rule WHERE name = ?",
                    Integer.class, spec.name());
            if (exists == null || exists == 0) {
                db.update("""
                        INSERT INTO ad_resource_rule (name, creator, type, action_cycle, action_time, rule, budget_change, status)
                        VALUES (?, ?, 'REPEAT', 'REPEAT', ?, ?, ?, 1)
                        """, spec.name(), spec.creator(), spec.actionTime(), ruleJson, spec.budgetPercent());
            } else {
                db.update("""
                        UPDATE ad_resource_rule SET creator = ?, type = 'REPEAT', action_cycle = 'REPEAT',
                               action_time = ?, rule = ?, budget_change = ?, status = 1
                        WHERE name = ?
                        """, spec.creator(), spec.actionTime(), ruleJson, spec.budgetPercent(), spec.name());
            }
            Long id = db.queryForObject("SELECT id FROM ad_resource_rule WHERE name = ?", Long.class, spec.name());
            ids.put(spec.key(), id);
        }
        return ids;
    }

    private void rebuildCategories() {
        db.update("DELETE FROM category WHERE `value` LIKE 'afs-topic-%'");
        db.update("DELETE FROM category_child WHERE `value` LIKE 'afs-topic-%'");
        db.update("DELETE FROM author_category_map WHERE author LIKE 'afs-operator-%' OR category LIKE 'afs-topic-%'");
        db.update("DELETE FROM ad_material_category WHERE `value` LIKE 'afs-topic-%'");
        db.update("DELETE FROM ad_material_category_child WHERE `value` LIKE 'afs-topic-%'");

        for (var v : PlatformEconomics.VERTICALS) {
            String site = PlatformEconomics.siteCode(v.siteIndex());
            String leadOp = PlatformEconomics.account(v.siteIndex()).operator();
            db.update("MERGE INTO category (site, `value`, label) KEY(site, `value`) VALUES (?, ?, ?)",
                    site, v.categorySlug(), v.categoryLabel());
            db.update("MERGE INTO ad_material_category (`value`, label, site) KEY(`value`) VALUES (?, ?, ?)",
                    v.categorySlug(), v.categoryLabel(), site);
            db.update("""
                    INSERT INTO author_category_map (`value`, label, author, category, site)
                    SELECT ?, ?, ?, ?, ?
                    WHERE NOT EXISTS (SELECT 1 FROM author_category_map WHERE author = ? AND category = ? AND site = ?)
                    """, v.categorySlug(), v.categoryLabel(), leadOp, v.categorySlug(), site,
                    leadOp, v.categorySlug(), site);
            for (var sub : v.subtopics()) {
                String childSlug = sub.slug().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
                db.update("MERGE INTO category_child (site, `value`, value_child, label_child) KEY(site, `value`, value_child) VALUES (?, ?, ?, ?)",
                        site, v.categorySlug(), childSlug, sub.slug());
                db.update("MERGE INTO ad_material_category_child (`value`, value_child, label_child) KEY(`value`, value_child) VALUES (?, ?, ?)",
                        v.categorySlug(), childSlug, sub.slug());
            }
        }
    }

    private void clearLegacyFixtureDailyRows(List<FixtureCampaign> fixtures) {
        for (FixtureCampaign fx : fixtures) {
            db.update("DELETE FROM tiktok_data WHERE campaign_id = ?", fx.campaignId());
            db.update("DELETE FROM taboola_data WHERE campaign_id = ?", fx.campaignId());
            db.update("DELETE FROM google_data WHERE campaign_id = ?", fx.campaignId());
            db.update("DELETE FROM meta_data WHERE campaign_id = ?", fx.campaignId());
            db.update("DELETE FROM adsense_data WHERE ad_resource_id = ? OR custom_channel_id LIKE ?",
                    fx.campaignId(), "%:" + fx.channelId());
            db.update("DELETE FROM ad_resource_rule_log WHERE ad_resource_id = ?", fx.campaignId());
            db.update("DELETE FROM keyword_report_data WHERE article_id = ?", fx.articleId());
            db.update("DELETE FROM google_bigquery_keyword_data WHERE article_id = ?", fx.articleId());
            if (fx.oldCampaignName() != null && !fx.oldCampaignName().isBlank()) {
                db.update("DELETE FROM ad_text WHERE name = ?", fx.oldCampaignName());
            }
        }
        // Only delete fixture ad_data rows, preserving all imported historical archive ad_data!
        db.update("DELETE FROM ad_data WHERE ad_id LIKE 'afs-ad-%'");
    }

    private void insertDailyMetrics(FixtureCampaign fx, int siteIndex, int slot, String site, String appId,
                                    String adId, String publisher, String platform, String accountId,
                                    String country, String campaignName, String landingUrl,
                                    List<PlatformEconomics.DailyRow> series) {
        String customChannel = "partner-" + publisher + ":" + fx.channelId();
        String sourceTag = "{\"source\":\"platform-sync\",\"platform\":\"" + platform + "\"}";

        for (var day : series) {
            String dateStr = day.date().toString();
            double spend = day.adSpend().doubleValue();
            double revenue = day.grossRevenue().doubleValue();

            switch (platform) {
                case "TikTok" -> db.update("""
                        INSERT INTO tiktok_data (`date`, country_code, campaign_id, campaign_name, landing_url,
                                                 channel_id, clicks, spend, impressions, conversion, advertiser)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, dateStr, country, fx.campaignId(), campaignName, landingUrl,
                        fx.channelId(), day.adClicks(), spend, day.adImpressions(), day.adConversions(), accountId);
                case "Taboola" -> db.update("""
                        INSERT INTO taboola_data (`date`, country_code, campaign_id, campaign_name, landing_url,
                                                  channel_id, clicks, spend, impressions, conversion, account_name)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, dateStr, country, fx.campaignId(), campaignName, landingUrl,
                        fx.channelId(), day.adClicks(), spend, day.adImpressions(), day.adConversions(), accountId);
                case "Google" -> db.update("""
                        INSERT INTO google_data (`date`, country_code, campaign_id, campaign_name, landing_url,
                                                 channel_id, clicks, spend, impressions, conversion, advertiser, account_name)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, dateStr, country, fx.campaignId(), campaignName, landingUrl,
                        fx.channelId(), day.adClicks(), spend, day.adImpressions(), day.adConversions(), accountId, accountId);
                default -> db.update("""
                        INSERT INTO meta_data (`date`, country_code, campaign_id, campaign_name, landing_url,
                                               channel_id, clicks, spend, impressions, conversion, advertiser)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, dateStr, country, fx.campaignId(), campaignName, landingUrl,
                        fx.channelId(), day.adClicks(), spend, day.adImpressions(), day.adConversions(), accountId);
            }

            double ttC = 0, ttS = 0, ttI = 0, ttCv = 0;
            double tbC = 0, tbS = 0, tbI = 0, tbCv = 0;
            double ggC = 0, ggS = 0, ggI = 0, ggCv = 0;
            double fbC = 0, fbS = 0, fbI = 0, fbCv = 0;
            switch (platform) {
                case "TikTok" -> { ttC = day.adClicks(); ttS = spend; ttI = day.adImpressions(); ttCv = day.adConversions(); }
                case "Taboola" -> { tbC = day.adClicks(); tbS = spend; tbI = day.adImpressions(); tbCv = day.adConversions(); }
                case "Google" -> { ggC = day.adClicks(); ggS = spend; ggI = day.adImpressions(); ggCv = day.adConversions(); }
                default -> { fbC = day.adClicks(); fbS = spend; fbI = day.adImpressions(); fbCv = day.adConversions(); }
            }

            db.update("""
                    INSERT INTO adsense_data (`date`, product_code, country_code, custom_channel_id, ad_resource_id,
                        adsense_data, estimated_earnings, impressions, ad_requests, matched_ad_requests,
                        individual_ad_impressions, funnel_requests, funnel_impressions, funnel_clicks,
                        clicks, cost_per_click, impressions_ctr, funnel_rpm,
                        tt_clicks, tt_spend, tt_impressions, tt_conversion,
                        tb_clicks, tb_spend, tb_impressions, tb_conversion,
                        gg_clicks, gg_spend, gg_impressions, gg_conversion,
                        fb_clicks, fb_spend, fb_impressions, fb_conversion)
                    VALUES (?, 'AFS', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, dateStr, country, customChannel, fx.campaignId(), sourceTag,
                    revenue, day.serpImpressions(), day.funnelImpressions(), day.serpImpressions(),
                    day.serpImpressions(), day.funnelImpressions(), day.funnelImpressions(), day.funnelClicks(),
                    day.afsClicks(), day.afsRpc(), day.afsCtr(), day.funnelRpm(),
                    ttC, ttS, ttI, ttCv, tbC, tbS, tbI, tbCv, ggC, ggS, ggI, ggCv, fbC, fbS, fbI, fbCv);

            long dayEpoch = day.date().atStartOfDay(REPORT_ZONE).toInstant().toEpochMilli();
            db.update("""
                    INSERT INTO ad_data (app_id, ad_id, day_time, country, pv, total, ecpm, page_site, page_pv, click, page_ecpm, other)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, appId, adId, dayEpoch, country, day.funnelImpressions(), revenue, day.funnelRpm(),
                    site, day.funnelImpressions(), day.afsClicks(), day.funnelRpm(), sourceTag);
        }
    }

    private void insertRealisticRuleLogs(FixtureCampaign fx, String campaignName, String ruleKey, long ruleId,
                                         List<PlatformEconomics.DailyRow> series) {
        var ruleSpec = PlatformEconomics.RULES.stream().filter(r -> r.key().equals(ruleKey)).findFirst().orElse(null);
        if (ruleSpec == null || series.isEmpty()) return;

        // Only log actual threshold crossings during the last 5 days so rule logs match real campaign behavior.
        int startIdx = Math.max(0, series.size() - 5);
        int logged = 0;
        for (int i = series.size() - 1; i >= startIdx && logged < 2; i--) {
            var day = series.get(i);
            double spend = day.adSpend().doubleValue();
            double rev = day.grossRevenue().doubleValue();
            double profit = rev - spend;
            double roas = spend > 0 ? rev / spend : 0.0;
            boolean triggered = switch (ruleKey) {
                case "tt-stop-loss" -> spend >= 80 && roas < 0.85;
                case "tt-scale" -> spend >= 100 && roas >= 1.45;
                case "fb-reduce" -> spend >= 90 && roas < 0.95;
                case "fb-scale" -> spend >= 120 && roas >= 1.40;
                case "fb-profit-guard" -> spend >= 80 && profit <= -35;
                case "gg-scale" -> spend >= 120 && roas >= 1.45;
                case "gg-reduce" -> spend >= 100 && roas < 1.00;
                case "tb-reduce" -> spend >= 65 && roas < 0.90;
                case "tb-scale" -> spend >= 80 && roas >= 1.38;
                case "cold-start" -> spend >= 50 && roas < 0.95;
                default -> false;
            };
            if (!triggered) continue;
            String body = "{\"campaignId\":\"%s\",\"spend\":%.2f,\"revenue\":%.2f,\"roas\":%.2f,\"budgetAction\":\"%s\",\"percent\":%d}"
                    .formatted(fx.campaignId(), spend, rev, roas, ruleSpec.budgetAction(), ruleSpec.budgetPercent());
            String resp = "{\"code\":0,\"status\":\"APPLIED\",\"message\":\"已按规则完成预算调整\"}";
            db.update("""
                    INSERT INTO ad_resource_rule_log (ad_resource_id, ad_group_id, ad_group_name, campaign_id,
                        campaign_name, rule_id, rule_name, action, day_num, `condition`, httpBody, httpResponse, ctime)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, fx.campaignId(), "afs-group-%03d".formatted(fx.sequence() + 1), campaignName + "_AG1",
                    fx.campaignId(), campaignName, ruleId, ruleSpec.name(), ruleSpec.action(),
                    i + 1, ruleSpec.condition(), body, resp,
                    Timestamp.valueOf(day.date().atTime(ruleSpec.actionTime(), 15 + (fx.sequence() % 40))));
            logged++;
        }
    }

    private void updateSiteAppsAndConfigs(Map<Integer, List<String>> siteArticles, Map<Integer, Long> accountDbIds)
            throws JsonProcessingException {
        for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
            String site = PlatformEconomics.siteCode(siteIndex);
            var vertical = PlatformEconomics.vertical(siteIndex);
            String appId = "afs-app-%02d".formatted(siteIndex + 1);
            db.update("UPDATE app_info SET name = ? WHERE app_id = ?",
                    site + " · " + vertical.categoryLabel(), appId);

            List<String> articles = siteArticles.getOrDefault(siteIndex, List.of());
            List<String> subSlugs = vertical.subtopics().stream()
                    .map(s -> s.slug().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT)).toList();
            List<String> hotWords = vertical.subtopics().stream().map(PlatformEconomics.Subtopic::keyword).toList();

            db.update("UPDATE config_item SET `value` = ? WHERE site = ? AND name = 'homeCategoryList'",
                    json.writeValueAsString(subSlugs), site);
            db.update("UPDATE config_item SET `value` = ? WHERE site = ? AND name = 'homeHotWords'",
                    json.writeValueAsString(hotWords), site);
            Map<String, List<String>> catContent = new LinkedHashMap<>();
            for (int i = 0; i < subSlugs.size(); i++) {
                int idx = i;
                List<String> group = new ArrayList<>();
                for (int j = idx; j < articles.size(); j += subSlugs.size()) group.add(articles.get(j));
                catContent.put(subSlugs.get(i), group);
            }
            db.update("UPDATE config_item SET `value` = ? WHERE site = ? AND name = 'homeCategoryContentList'",
                    json.writeValueAsString(catContent), site);
        }
    }

    private void rebuildLocalizedFeedback(LocalDate end) throws JsonProcessingException {
        // Delete only the old robotic fixture admin sentences.
        db.update("""
                DELETE FROM feedback
                WHERE CAST(`value` AS VARCHAR) IN (
                    'Filters are easy to use.',
                    'The report loaded quickly.',
                    'It would help to see the date range on exports.',
                    'The comparison table was useful.'
                )
                """);
        db.update("DELETE FROM local_api_event WHERE kind = 'feedback' AND CAST(payload AS VARCHAR) LIKE '%\"synthetic\":true%'");

        Random rng = new Random(20261121L);
        List<String[]> locales = List.of(
                new String[]{"en", "en-US"}, new String[]{"en", "en-GB"},
                new String[]{"en", "en-CA"}, new String[]{"en", "en-AU"},
                new String[]{"de", "de-DE"}, new String[]{"fr", "fr-FR"}
        );
        int globalIdx = 0;
        for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
            String site = PlatformEconomics.siteCode(siteIndex);
            // 4 natural localized reader reviews per site (48 total) spaced organically across the past 14 days.
            for (int k = 0; k < 4; k++) {
                String[] loc = locales.get((siteIndex + k) % locales.size());
                var fb = PlatformEconomics.feedbackFor(loc[0], globalIdx);
                int daysAgo = rng.nextInt(14);
                int hour = 7 + rng.nextInt(16);
                int minute = rng.nextInt(60);
                int second = rng.nextInt(60);
                LocalDateTime when = end.minusDays(daysAgo).atTime(hour, minute, second);
                Timestamp ts = Timestamp.valueOf(when);
                db.update("INSERT INTO feedback (site, rate, locale, `value`, ctime, utime) VALUES (?, ?, ?, ?, ?, ?)",
                        site, Integer.toString(fb.rate()), loc[1], fb.text(), ts, ts);
                db.update("INSERT INTO local_api_event (kind, site, payload, created_at) VALUES ('feedback', ?, ?, ?)",
                        site, json.writeValueAsString(Map.of("rate", fb.rate(), "locale", loc[1], "value", fb.text())), ts);
                globalIdx++;
            }
        }
    }

    private void refreshSeededOptimizationRuns(List<SeedRunCandidate> candidates) throws JsonProcessingException {
        // Preserve ALL AI-analyzed runs (LANGCHAIN4J) and any runs created/reviewed by local-admin!
        List<String> staleRunIds = db.queryForList("""
                SELECT id FROM optimization_run
                WHERE CAST(plan AS VARCHAR) NOT LIKE '%LANGCHAIN4J%'
                  AND COALESCE(reviewer, '') <> 'local-admin'
                  AND (COALESCE(reviewer, '') IN ('', 'afs-reviewer', 'demo-reviewer')
                       OR CAST(plan AS VARCHAR) LIKE '%\"RULES\"%')
                """, String.class);
        for (String id : staleRunIds) {
            db.update("DELETE FROM optimization_audit WHERE run_id = ?", id);
            db.update("DELETE FROM optimization_run WHERE id = ?", id);
        }

        // Seed 16 crisp recent optimization runs from the NEW realistic campaign metrics (4 per platform).
        List<SeedRunCandidate> picked = new ArrayList<>();
        Map<String, Integer> perPlatform = new LinkedHashMap<>();
        for (var c : candidates) {
            int count = perPlatform.getOrDefault(c.platform(), 0);
            if (count < 4) {
                picked.add(c);
                perPlatform.put(c.platform(), count + 1);
            }
        }
        String[] reviewNotes = {
                "已核对近 14 天分国家 ROAS 与 AFS RPC，同意按 10% 步长执行预算调整",
                "当前素材进入轻微衰减期，先驳回扩量建议，待本周替换 2 组新素材后复审",
                "已完成风控阈值复核，同意先通过预算模拟验证次日利润影响",
                "连续 5 天 ROAS 低于盈亏平衡线，批准降预算止损并保留核心 US 流量"
        };
        for (int i = 0; i < picked.size(); i++) {
            var c = picked.get(i);
            var input = new OptimizationWorkflow.Input(
                    c.site(), c.channel(), c.campaignId(), "USD", c.start(), c.end(),
                    c.totals().adSpend(), c.totals().grossRevenue(),
                    c.totals().adImpressions(), c.totals().adClicks(), c.totals().adConversions(),
                    new BigDecimal("1.25"), new BigDecimal("50.00"), 30, 10);
            var run = optimizer.create(input, c::operator, false);
            int scenario = i % 4;
            if (scenario == 0) continue; // Keep PENDING for immediate human review!
            String reviewer = PlatformEconomics.OPERATORS.stream()
                    .map(PlatformEconomics.OperatorSpec::name)
                    .filter(name -> !name.equals(c.operator()))
                    .skip(i % (PlatformEconomics.OPERATORS.size() - 1))
                    .findFirst()
                    .orElse("ryan.li");
            var decision = scenario == 1
                    ? OptimizationController.Decision.REJECTED
                    : OptimizationController.Decision.APPROVED;
            optimizer.review(run.id(), new OptimizationController.Review(decision, reviewNotes[scenario]), () -> reviewer);
            if (scenario == 3) {
                BigDecimal currentDailyBudget = c.totals().suggestedDailyBudget().signum() > 0
                        ? c.totals().suggestedDailyBudget()
                        : c.totals().adSpend().divide(BigDecimal.valueOf(PlatformEconomics.ACTIVE_DAYS), 2, RoundingMode.HALF_UP);
                optimizer.simulate(run.id(), new OptimizationController.SimulationRequest(currentDailyBudget), c::operator);
            }
        }
    }

    private ObjectNode parseOrCreateObject(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                var node = json.readTree(raw);
                if (node instanceof ObjectNode obj) return obj;
            } catch (Exception ignored) {
                // Create a clean object node.
            }
        }
        return json.createObjectNode();
    }

    private record FixtureCampaign(int sequence, String campaignId, String articleId, String channelId,
                                   String oldCampaignName, String platformInfo) {}

    private record SeedRunCandidate(String site, String channel, String campaignId, String platform,
                                    String operator, LocalDate start, LocalDate end,
                                    PlatformEconomics.CampaignTotals totals,
                                    List<PlatformEconomics.DailyRow> series) {}
}
