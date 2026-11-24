package com.afs.agent;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Second data pass over the local workspace: brings the 20 remaining publishers online and
 * replaces the remaining template repetition in the first twelve.
 *
 * <p>Only generated rows are touched. The selection scope is the seed corpus itself
 * ({@code article_id LIKE 'afs-article-%' AND partner = 'AFS Media'}), so campaigns, articles,
 * materials, rules and users created by hand in the console are preserved. Everything written
 * here is derived deterministically from {@link PlatformEconomics} and {@link ContentLibrary},
 * which means each of the 640 campaigns gets its own article body, ad copy, creative spec,
 * keyword variants, rule binding and 14-day performance curve.
 */
@Order(650)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "afs.workspace.realistic-data", havingValue = "true")
class RealisticWorkspaceExpansion implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RealisticWorkspaceExpansion.class);
    static final String MIGRATION = "realistic-workspace-expansion-v34";
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final OptimizationController optimizer;

    RealisticWorkspaceExpansion(JdbcTemplate db, ObjectMapper json, OptimizationController optimizer) {
        this.db = db;
        this.json = json;
        this.optimizer = optimizer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        if (hasMarker(MIGRATION)) return;
        LocalDate end = LocalDate.now(REPORT_ZONE).minusDays(1);
        LocalDate start = end.minusDays(PlatformEconomics.ACTIVE_DAYS - 1);
        log.info("Expanding local workspace to {} publishers with unique editorial content ({}..{})",
                PlatformEconomics.SITE_COUNT, start, end);

        Map<Integer, Long> accountIds = rebuildAccountsAndAccess();
        Map<String, Long> rules = rebuildRules();
        rebuildCategories();
        ensurePublisherAssets();
        rebuildOperatorProfiles(accountIds);

        Map<Integer, List<CampaignRow>> bySite = loadCampaigns();
        List<SeedRunCandidate> runCandidates = new ArrayList<>();
        int created = 0;
        for (var entry : bySite.entrySet()) {
            int siteIndex = entry.getKey();
            List<CampaignRow> rows = entry.getValue();
            rows.sort(java.util.Comparator.comparingInt(CampaignRow::slot));
            for (CampaignRow row : rows) {
                if (!row.present()) {
                    insertGeneratedRows(siteIndex, row, start);
                    created++;
                }
                refreshCampaign(siteIndex, row, start, end);
                if (row.active()) {
                    runCandidates.add(new SeedRunCandidate(
                            PlatformEconomics.siteCode(siteIndex), row.channelId(), row.campaignId(),
                            PlatformEconomics.vertical(siteIndex).platform(),
                            PlatformEconomics.operatorForCampaign(siteIndex, row.slot()),
                            start, end, row.totals()));
                }
            }
        }

        // Campaign names are regenerated per account, so clean up copy rows that no longer
        // belong to a live campaign once every publisher has been refreshed.
        purgeStaleAdCopy();
        refreshSiteConfiguration(bySite);
        rebuildFeedback(end);
        createOptimizationRuns(runCandidates);
        diversifyReviewNotes();

        db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
        log.info("Completed {}: {} new campaigns and {} refreshed campaigns across {} publishers.",
                MIGRATION, created, bySite.values().stream().mapToInt(List::size).sum(), bySite.size());
    }

    private boolean hasMarker(String name) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, name);
        return count != null && count > 0;
    }

    // ------------------------------------------------------------------ accounts & access

    private Map<Integer, Long> rebuildAccountsAndAccess() throws Exception {
        db.update("DELETE FROM user_access WHERE user_name LIKE 'afs-operator-%'");
        db.update("DELETE FROM `user` WHERE name LIKE 'afs-operator-%' AND partner = ?", PlatformEconomics.PARTNER);

        Map<Integer, Long> accountIds = new LinkedHashMap<>();
        Map<String, List<Long>> bySite = new LinkedHashMap<>();
        for (var account : PlatformEconomics.AD_ACCOUNTS) {
            Integer exists = db.queryForObject("SELECT COUNT(*) FROM ad_account_info WHERE account_id = ?",
                    Integer.class, account.accountId());
            String info = json.writeValueAsString(Map.of(
                    "currency", "USD", "timezone", "America/Los_Angeles",
                    "site", account.site(), "tier", "Agency"));
            if (exists == null || exists == 0) {
                db.update("""
                        INSERT INTO ad_account_info (name, user_name, partner, account_id, ad_platform, info, status)
                        VALUES (?, ?, ?, ?, ?, ?, 1)
                        """, account.name(), account.operator(), PlatformEconomics.PARTNER,
                        account.accountId(), account.platform(), info);
            } else {
                db.update("""
                        UPDATE ad_account_info SET name = ?, user_name = ?, partner = ?, info = ?, status = 1
                        WHERE account_id = ?
                        """, account.name(), account.operator(), PlatformEconomics.PARTNER, info, account.accountId());
            }
            Long dbId = db.queryForObject("SELECT id FROM ad_account_info WHERE account_id = ? LIMIT 1",
                    Long.class, account.accountId());
            accountIds.put(account.siteIndex(), dbId);
            bySite.computeIfAbsent(account.site(), key -> new ArrayList<>()).add(dbId);
        }

        for (var operator : PlatformEconomics.OPERATORS) {
            List<Long> operatorAccounts = new ArrayList<>();
            for (String site : operator.sites()) operatorAccounts.addAll(bySite.getOrDefault(site, List.of()));
            String sitesJson = json.writeValueAsString(operator.sites());
            String accountsJson = json.writeValueAsString(operatorAccounts);
            Integer exists = db.queryForObject("SELECT COUNT(*) FROM `user` WHERE name = ?",
                    Integer.class, operator.name());
            if (exists == null || exists == 0) {
                db.update("""
                        INSERT INTO `user` (name, password, partner, root, status, `desc`,
                                            auto_article, auto_campaign, show_material, site_list, ad_account_list)
                        VALUES (?, ?, ?, 0, 1, ?, TRUE, TRUE, TRUE, ?, ?)
                        """, operator.name(), "ops-" + java.util.UUID.randomUUID(), PlatformEconomics.PARTNER,
                        operator.desc(), sitesJson, accountsJson);
            } else {
                db.update("""
                        UPDATE `user` SET partner = ?, status = 1, `desc` = ?, site_list = ?, ad_account_list = ?
                        WHERE name = ?
                        """, PlatformEconomics.PARTNER, operator.desc(), sitesJson, accountsJson, operator.name());
            }
            for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
                if (!operator.sites().contains(PlatformEconomics.siteCode(siteIndex))) continue;
                db.update("MERGE INTO user_access (user_name, app_id, access) KEY(user_name, app_id) VALUES (?, ?, 1)",
                        operator.name(), appId(siteIndex));
            }
        }
        return accountIds;
    }

    private void rebuildOperatorProfiles(Map<Integer, Long> accountIds) {
        // The console shows the account chips on a profile page, so keep every operator's
        // ad_account_list aligned with the accounts the workspace actually contains.
        for (var operator : PlatformEconomics.OPERATORS) {
            List<Object> ids = new ArrayList<>();
            for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
                if (!operator.sites().contains(PlatformEconomics.siteCode(siteIndex))) continue;
                if (accountIds.containsKey(siteIndex)) ids.add(accountIds.get(siteIndex));
            }
            try {
                db.update("UPDATE `user` SET ad_account_list = ? WHERE name = ?",
                        json.writeValueAsString(ids), operator.name());
            } catch (Exception ignored) {
                // Keep resilient across minimal test schemas.
            }
        }
    }

    // ------------------------------------------------------------------ rules & taxonomy

    private Map<String, Long> rebuildRules() throws Exception {
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
        }
        Map<String, Long> ids = new LinkedHashMap<>();
        for (var spec : PlatformEconomics.RULES) {
            ids.put(spec.key(), db.queryForObject("SELECT id FROM ad_resource_rule WHERE name = ?",
                    Long.class, spec.name()));
        }
        return ids;
    }

    private void rebuildCategories() {
        for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
            var vertical = PlatformEconomics.vertical(siteIndex);
            String site = PlatformEconomics.siteCode(siteIndex);
            String leadOperator = PlatformEconomics.AD_ACCOUNTS.get(siteIndex).operator();
            db.update("MERGE INTO category (site, `value`, label) KEY(site, `value`) VALUES (?, ?, ?)",
                    site, vertical.categorySlug(), vertical.categoryLabel());
            db.update("MERGE INTO ad_material_category (`value`, label, site) KEY(`value`) VALUES (?, ?, ?)",
                    vertical.categorySlug(), vertical.categoryLabel(), site);
            db.update("""
                    INSERT INTO author_category_map (`value`, label, author, category, site)
                    SELECT ?, ?, ?, ?, ?
                    WHERE NOT EXISTS (SELECT 1 FROM author_category_map WHERE author = ? AND category = ? AND site = ?)
                    """, vertical.categorySlug(), vertical.categoryLabel(), leadOperator, vertical.categorySlug(), site,
                    leadOperator, vertical.categorySlug(), site);
            for (var sub : vertical.subtopics()) {
                String child = slugify(sub.slug());
                db.update("MERGE INTO category_child (site, `value`, value_child, label_child) KEY(site, `value`, value_child) VALUES (?, ?, ?, ?)",
                        site, vertical.categorySlug(), child, sub.slug());
                db.update("MERGE INTO ad_material_category_child (`value`, value_child, label_child) KEY(`value`, value_child) VALUES (?, ?, ?)",
                        vertical.categorySlug(), child, sub.slug());
            }
        }
    }

    // ------------------------------------------------------------------ publisher scaffolding

    private void ensurePublisherAssets() {
        for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
            String site = PlatformEconomics.siteCode(siteIndex);
            var vertical = PlatformEconomics.vertical(siteIndex);
            String publisher = publisherId(siteIndex);
            db.update("""
                    MERGE INTO adsense_account (pub_id, site, status) KEY(pub_id) VALUES (?, ?, 1)
                    """, publisher, site);
            db.update("""
                    MERGE INTO app_info (app_id, pname, name, os, ratio, is_on_app_store, status)
                    KEY(app_id) VALUES (?, ?, ?, ?, 1.0, 0, 1)
                    """, appId(siteIndex), "com.afs." + site.replace("-", ""),
                    site + " · " + vertical.categoryLabel(), siteIndex % 2 == 0 ? "Android" : "iOS");
        }
    }

    private void refreshSiteConfiguration(Map<Integer, List<CampaignRow>> bySite) throws Exception {
        for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
            String site = PlatformEconomics.siteCode(siteIndex);
            var vertical = PlatformEconomics.vertical(siteIndex);
            List<CampaignRow> rows = bySite.getOrDefault(siteIndex, List.of());
            rows.sort(java.util.Comparator.comparingInt(CampaignRow::slot));
            List<String> articleIds = rows.stream().map(CampaignRow::articleId).toList();
            List<String> subSlugs = vertical.subtopics().stream().map(s -> slugify(s.slug())).toList();
            List<String> hotWords = vertical.subtopics().stream().map(PlatformEconomics.Subtopic::keyword).toList();

            config(site, "homeList", List.of(Map.of("title", "Latest reviews & buying guides", "list", articleIds)));
            config(site, "feedList", articleIds);
            config(site, "homeCategoryList", subSlugs);
            Map<String, List<String>> categoryContent = new LinkedHashMap<>();
            for (int i = 0; i < subSlugs.size(); i++) {
                List<String> group = new ArrayList<>();
                for (int j = i; j < rows.size(); j += subSlugs.size()) group.add(rows.get(j).articleId());
                categoryContent.put(subSlugs.get(i), group);
            }
            config(site, "homeCategoryContentList", categoryContent);
            config(site, "homeHotWords", hotWords);

            for (CampaignRow row : rows) {
                try {
                    config(site, "placement-" + row.adId().substring(row.adId().lastIndexOf('-') + 1),
                            Map.of("placement", row.adId(), "format", creativeFormat(siteIndex)));
                } catch (Exception ignored) {
                    // Placement naming is cosmetic; never fail the migration on it.
                }
            }
            pruneLegacyPlacementKeys(site);
        }
    }

    /** Drops the legacy {@code afs-placement-*} keys once the new {@code placement-*} key exists. */
    private void pruneLegacyPlacementKeys(String site) {
        db.update("""
                DELETE FROM config_item old
                WHERE old.site = ? AND old.name LIKE 'afs-placement-%'
                  AND EXISTS (SELECT 1 FROM config_item fresh
                              WHERE fresh.site = old.site
                                AND fresh.name = REPLACE(old.name, 'afs-', ''))
                """, site);
    }

    private void config(String site, String name, Object value) throws Exception {
        String serialized = json.writeValueAsString(value);
        String desc = switch (name) {
            case "homeList", "feedList" -> "首页与信息流文章列表";
            case "homeCategoryList", "homeCategoryContentList" -> "分类导航与分类内容";
            case "homeHotWords" -> "站内热搜词";
            default -> "广告位配置";
        };
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM config_item WHERE site = ? AND name = ?",
                Integer.class, site, name);
        if (exists == null || exists == 0) {
            db.update("INSERT INTO config_item (site, name, `value`, `desc`) VALUES (?, ?, ?, ?)",
                    site, name, serialized, desc);
        } else {
            db.update("UPDATE config_item SET `value` = ?, `desc` = ? WHERE site = ? AND name = ?",
                    serialized, desc, site, name);
        }
    }

    // ------------------------------------------------------------------ campaign corpus

    /**
     * Removes ad-copy rows that belong to the seed corpus but no longer match a live campaign,
     * so the earlier version of this fixture cannot leave orphaned duplicate copy behind.
     */
    private void purgeStaleAdCopy() {
        List<String> live = db.queryForList("""
                SELECT campaign FROM ad_resource
                WHERE article_id LIKE 'afs-article-%' AND partner = ?
                """, String.class, PlatformEconomics.PARTNER);
        var keep = new java.util.HashSet<>(live);
        List<String> orphaned = db.query("""
                SELECT name FROM ad_text WHERE name LIKE 'TT\\_%' OR name LIKE 'TB\\_%'
                   OR name LIKE 'GG\\_%' OR name LIKE 'FB\\_%'
                """, (rs, index) -> rs.getString("name")).stream()
                .filter(name -> !keep.contains(name))
                .toList();
        for (String name : orphaned) db.update("DELETE FROM ad_text WHERE name = ?", name);
    }

    private Map<Integer, List<CampaignRow>> loadCampaigns() {
        Map<Integer, List<CampaignRow>> bySite = new LinkedHashMap<>();
        for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
            bySite.put(siteIndex, new ArrayList<>());
        }
        var existing = db.query("""
                SELECT ad_resource_id, article_id, channel_id, campaign, ad_platform, ad_platform_info,
                       ad_platform_state, state, ctime
                FROM ad_resource
                WHERE article_id LIKE 'afs-article-%' AND partner = ?
                """, (rs, index) -> new CampaignRow(
                Integer.parseInt(rs.getString("article_id").substring("afs-article-".length())) - 1,
                rs.getString("ad_resource_id"), rs.getString("article_id"), rs.getString("channel_id"),
                rs.getString("campaign"), rs.getString("ad_platform"),
                rs.getString("ad_platform_info"), rs.getString("ad_platform_state"),
                rs.getString("state"), rs.getTimestamp("ctime"), true), PlatformEconomics.PARTNER);
        for (CampaignRow row : existing) {
            int siteIndex = row.sequence() / PlatformEconomics.SLOTS_PER_SITE;
            if (siteIndex < 0 || siteIndex >= PlatformEconomics.SITE_COUNT) continue;
            bySite.get(siteIndex).add(row.withSlot(row.sequence() % PlatformEconomics.SLOTS_PER_SITE));
        }
        // Add the 20 publishers that do not exist yet with their full 20-campaign build-out.
        for (int siteIndex = PlatformExpansion.FIRST_SITE_INDEX; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
            List<CampaignRow> rows = bySite.get(siteIndex);
            if (!rows.isEmpty()) continue; // already materialised by an earlier migration
            for (int slot = 0; slot < PlatformEconomics.SLOTS_PER_SITE; slot++) {
                int sequence = siteIndex * PlatformEconomics.SLOTS_PER_SITE + slot;
                rows.add(new CampaignRow(sequence, "afs-campaign-" + "%03d".formatted(sequence + 1),
                        "afs-article-%03d".formatted(sequence + 1), channelId(sequence),
                        null, null, null, null, null, null, false).withSlot(slot));
            }
        }
        return bySite;
    }

    /** Materialises article/category/material/resource rows for a publisher that is not seeded yet. */
    private void insertGeneratedRows(int siteIndex, CampaignRow row, LocalDate start) throws Exception {
        var vertical = PlatformEconomics.vertical(siteIndex);
        var sub = PlatformEconomics.subtopic(siteIndex, row.slot());
        var country = PlatformEconomics.countryForSiteSlot(siteIndex, row.slot());
        var content = content(siteIndex, row.slot());
        String site = PlatformEconomics.siteCode(siteIndex);
        String operator = PlatformEconomics.operatorForCampaign(siteIndex, row.slot());
        String child = slugify(sub.slug());

        String otherInfo = json.writeValueAsString(Map.of(
                "author", operator, "views", 320 + (row.sequence() * 137) % 5_800,
                "vertical", vertical.categorySlug(), "country", country.code(),
                "readingTimeMinutes", 5 + row.slot() % 6,
                "editorialAngle", content.angle()));
        db.update("""
                INSERT INTO article_info (article_id, title, terms, category, first_part, text_list, lang, state,
                    site, partner, other_info, online, inside_show, status, ctime, utime)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 1, ?, ?)
                """, row.articleId(), content.title(), PlatformEconomics.articleKeyword(siteIndex, row.slot()),
                vertical.categorySlug(), content.lead(), json.writeValueAsString(content.paragraphs()),
                country.lang(), PlatformEconomics.campaignState(siteIndex, row.slot()), site, PlatformEconomics.PARTNER,
                otherInfo, Timestamp.valueOf(start.atTime(8 + row.slot() % 9, (row.sequence() * 7) % 60)),
                Timestamp.valueOf(start.plusDays(2).atTime(10, (row.sequence() * 3) % 60)));

        db.update("INSERT INTO crawl_content (fid, content, source, category) VALUES (?, ?, 'editorial', ?)",
                "afs-crawl-%03d".formatted(row.sequence() + 1), json.writeValueAsString(content.paragraphs()),
                vertical.categorySlug());

        db.update("""
                INSERT INTO ad_material (ad_material_id, name, user_name, country, lang, type, dimension, ratio,
                    category, template, cover, url, size, duration, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, row.materialId(), assetName(siteIndex, row), operator, country.code(), country.lang(),
                row.creative().type(), row.creative().dimension(), row.creative().ratio(),
                vertical.categorySlug(), row.creative().template(),
                PlatformEconomics.materialCover(siteIndex, row.materialId(), row.creative()),
                PlatformEconomics.materialUrl(siteIndex, row.materialId(), row.creative()),
                row.creative().sizeBytes(), row.creative().duration());

        db.update("""
                INSERT INTO ad_info (ad_id, app_id, name, ratio, type, on_off, status)
                VALUES (?, ?, ?, 1.0, 1, 1, 1)
                """, row.adId(), appId(siteIndex), content.title());

        db.update("""
                INSERT INTO adsense_channel (pub_id, channel_id, state, status) VALUES (?, ?, ?, 1)
                """, publisherId(siteIndex), row.channelId(),
                PlatformEconomics.isRunningActive(siteIndex, row.slot()) ? "ONLINE" : "PAUSED");

        String settings = json.writeValueAsString(Map.of(
                "source", "platform-sync", "campaignId", row.campaignId(), "currency", "USD"));
        db.update("DELETE FROM local_api_setting WHERE kind = 'report' AND site = ? AND name = ?",
                site, row.channelId());
        db.update("""
                INSERT INTO local_api_setting (kind, site, name, setting_value) VALUES ('report', ?, ?, ?)
                """, site, row.channelId(), settings);
        db.update("""
                INSERT INTO ad_resource (ad_resource_id, article_id, channel_id, campaign, land_page_url, ad_platform,
                    ad_desc, partner, state, status, ctime, utime)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                """, row.campaignId(), row.articleId(), row.channelId(), "pending refresh",
                PlatformEconomics.landingPageUrl(siteIndex, row.slot(), row.channelId()),
                json.writeValueAsString(List.of(vertical.platform())),
                vertical.platform() + " " + country.code() + " " + sub.keyword(),
                PlatformEconomics.PARTNER, PlatformEconomics.campaignState(siteIndex, row.slot()),
                Timestamp.valueOf(start.minusDays(2 + row.sequence() % 24).atTime(9 + row.slot() % 6, (row.sequence() * 5) % 60)),
                Timestamp.valueOf(start.atTime(9, 0)));
    }

    /** Removes the previous generated performance rows for this campaign before re-inserting. */
    private void clearGeneratedDaily(CampaignRow row) {
        String campaignId = row.campaignId();
        db.update("DELETE FROM tiktok_data WHERE campaign_id = ?", campaignId);
        db.update("DELETE FROM taboola_data WHERE campaign_id = ?", campaignId);
        db.update("DELETE FROM google_data WHERE campaign_id = ?", campaignId);
        db.update("DELETE FROM meta_data WHERE campaign_id = ?", campaignId);
        db.update("DELETE FROM adsense_data WHERE ad_resource_id = ? OR custom_channel_id LIKE ?",
                campaignId, "%:" + row.channelId());
        db.update("DELETE FROM ad_resource_rule_log WHERE ad_resource_id = ?", campaignId);
    }

    private void refreshCampaign(int siteIndex, CampaignRow row, LocalDate start, LocalDate end) throws Exception {        var vertical = PlatformEconomics.vertical(siteIndex);
        var sub = PlatformEconomics.subtopic(siteIndex, row.slot());
        var country = PlatformEconomics.countryForSiteSlot(siteIndex, row.slot());
        var content = content(siteIndex, row.slot());
        var account = PlatformEconomics.AD_ACCOUNTS.get(siteIndex);
        var creative = row.creative();
        String site = PlatformEconomics.siteCode(siteIndex);
        String operator = PlatformEconomics.operatorForCampaign(siteIndex, row.slot());
        String campaignName = PlatformEconomics.campaignName(siteIndex, row.slot());
        String landingUrl = PlatformEconomics.landingPageUrl(siteIndex, row.slot(), row.channelId());
        String keyword = PlatformEconomics.articleKeyword(siteIndex, row.slot());
        String state = PlatformEconomics.campaignState(siteIndex, row.slot());
        String ruleKey = PlatformEconomics.ruleKeyForCampaign(siteIndex, row.slot());
        Long ruleId = ruleKey == null ? null : ruleId(ruleKey);
        String adId = row.adId();
        String materialId = row.materialId();
        String otherInfo = json.writeValueAsString(Map.of(
                "author", operator, "views", 320 + (row.sequence() * 137) % 5_800,
                "vertical", vertical.categorySlug(), "country", country.code(),
                "readingTimeMinutes", 5 + row.slot() % 6,
                "editorialAngle", content.angle()));

        // 1. editorial content
        db.update("""
                UPDATE article_info
                SET title = ?, terms = ?, category = ?, first_part = ?, text_list = ?, lang = ?, state = ?,
                    site = ?, partner = ?, other_info = ?, utime = ?
                WHERE article_id = ?
                """, content.title(), keyword, vertical.categorySlug(), content.lead(),
                json.writeValueAsString(content.paragraphs()), country.lang(), state, site,
                PlatformEconomics.PARTNER, otherInfo,
                Timestamp.valueOf(end.atTime(8 + (row.sequence() * 5) % 13, (row.sequence() * 7) % 60)), row.articleId());

        db.update("UPDATE crawl_content SET content = ?, category = ? WHERE fid = ?",
                json.writeValueAsString(content.paragraphs()), vertical.categorySlug(),
                "afs-crawl-%03d".formatted(row.sequence() + 1));
        db.update("DELETE FROM keyword_report_data WHERE article_id = ?", row.articleId());
        db.update("DELETE FROM google_bigquery_keyword_data WHERE article_id = ?", row.articleId());
        db.update("INSERT INTO keyword_report_data (pd, article_id, keyword) VALUES (?, ?, ?)",
                end.toString(), row.articleId(), keyword);
        db.update("INSERT INTO google_bigquery_keyword_data (pd, article_id, key_word, `count`) VALUES (?, ?, ?, ?)",
                end.toString(), row.articleId(), keyword, 45 + (row.sequence() * 29) % 380);

        // 2. creative & copy
        db.update("""
                UPDATE ad_material
                SET name = ?, user_name = ?, country = ?, lang = ?, type = ?, dimension = ?, ratio = ?,
                    category = ?, template = ?, cover = ?, url = ?, size = ?, duration = ?, utime = ?
                WHERE ad_material_id = ?
                """, assetName(siteIndex, row), operator, country.code(), country.lang(), creative.type(),
                creative.dimension(), creative.ratio(), vertical.categorySlug(), creative.template(),
                PlatformEconomics.materialCover(siteIndex, materialId, creative),
                PlatformEconomics.materialUrl(siteIndex, materialId, creative),
                creative.sizeBytes(), creative.duration(),
                Timestamp.valueOf(end.atTime(8 + row.slot() % 9, (row.sequence() * 11) % 60)), materialId);
        Integer textExists = db.queryForObject("SELECT COUNT(*) FROM ad_text WHERE name = ?",
                Integer.class, campaignName);
        String copy = json.writeValueAsString(content.adCopy());
        if (textExists == null || textExists == 0) {
            db.update("INSERT INTO ad_text (name, ad_text) VALUES (?, ?)", campaignName, copy);
        } else {
            db.update("UPDATE ad_text SET ad_text = ? WHERE name = ?", copy, campaignName);
        }
        db.update("UPDATE ad_info SET name = ? WHERE ad_id = ?", content.title(), adId);

        // 3. platform performance (previous generated rows are replaced, not duplicated)
        clearGeneratedDaily(row);
        PlatformEconomics.CampaignTotals totals;
        String platformState;
        String auditMsg = "";
        List<PlatformEconomics.DailyRow> series;
        if (PlatformEconomics.isRunningActive(siteIndex, row.slot())) {
            series = PlatformEconomics.generateDailySeries(siteIndex, row.slot(), start, PlatformEconomics.ACTIVE_DAYS);
            // Campaigns launched mid-window simply have fewer days of data; the aggregates and
            // rule logs below are computed from the trimmed series, not from a full 14-day run.
            int trim = Math.floorMod(row.sequence(), 5) == 0 ? 2 + Math.floorMod(row.sequence(), 3) : 0;
            if (trim > 0 && series.size() > trim + 3) series = series.subList(trim, series.size());
            totals = PlatformEconomics.summarize(series);
            row.totals(totals);
            platformState = "PLATFORM_ACTIVE";
            ObjectNode info = platformInfo(siteIndex, row, campaignName, totals, "ACTIVE",
                    start.atTime(8, 30).toString());
            db.update("UPDATE ad_resource SET ad_platform_info = ? WHERE ad_resource_id = ?",
                    info.toString(), row.campaignId());
            insertDailyMetrics(siteIndex, row, series, vertical.platform(), country.code(), campaignName, landingUrl);
            if (ruleId != null) insertRuleLogs(siteIndex, row, campaignName, ruleKey, ruleId, series);
        } else if ("ARCHIVE".equals(state)) {
            series = PlatformEconomics.generateDailySeries(siteIndex, row.slot(), start.minusDays(21),
                    PlatformEconomics.ACTIVE_DAYS);
            totals = PlatformEconomics.summarize(series);
            platformState = "PLATFORM_PAUSED";
            ObjectNode info = platformInfo(siteIndex, row, campaignName, totals, "PAUSED",
                    start.minusDays(21).atTime(9, 0).toString());
            db.update("UPDATE ad_resource SET ad_platform_info = ? WHERE ad_resource_id = ?",
                    info.toString(), row.campaignId());
        } else {
            totals = new PlatformEconomics.CampaignTotals(0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, BigDecimal.ZERO);
            if ("REJECT".equals(state)) {
                platformState = "PLATFORM_REJECTED";
                auditMsg = PlatformEconomics.REJECTION_REASONS.get(siteIndex % PlatformEconomics.REJECTION_REASONS.size());
            } else {
                platformState = "LOCAL_ONLY";
            }
            db.update("UPDATE ad_resource SET ad_platform_info = ? WHERE ad_resource_id = ?",
                    platformInfo(siteIndex, row, campaignName, totals, "LOCAL_ONLY", null).toString(), row.campaignId());
        }

        String desc = "%s %s %s 投放活动 · %s".formatted(
                vertical.platform(), country.code(), vertical.categoryLabel(), sub.keyword());
        db.update("""
                UPDATE ad_resource
                SET campaign = ?, title = ?, land_page_url = ?, ad_desc = ?, audit_msg = ?, state = ?, rule_id = ?,
                    country = ?, keyword = ?, ad_account = ?, account_id = ?, ad_platform_state = ?,
                    ad_platform = ?, media_list = ?, am = ?, ad_spend = ?, gross_revenue = ?, profit = ?, roi = ?,
                    utime = ?
                WHERE ad_resource_id = ?
                """, campaignName, content.title(), landingUrl, desc, auditMsg, state, ruleId,
                country.code(), keyword, account.accountId(), account.accountId(), platformState,
                json.writeValueAsString(List.of(vertical.platform())), json.writeValueAsString(List.of(materialId)),
                operator, totals.adSpend().doubleValue(), totals.grossRevenue().doubleValue(),
                totals.profit().doubleValue(), totals.roas(),
                Timestamp.valueOf(end.atTime(8 + (row.sequence() * 11) % 13, (row.sequence() * 13) % 60)), row.campaignId());

        if ("TikTok".equals(vertical.platform())) {
            String groupId = "afs-group-%03d".formatted(row.sequence() + 1);
            String status = PlatformEconomics.isRunningActive(siteIndex, row.slot()) ? "ENABLE" : "DISABLE";
            double budget = totals.suggestedDailyBudget().signum() > 0
                    ? totals.suggestedDailyBudget().doubleValue() : 120.0;
            Integer exists = db.queryForObject("SELECT COUNT(*) FROM tiktok_ad_group_info WHERE ad_group_id = ?",
                    Integer.class, groupId);
            if (exists == null || exists == 0) {
                db.update("""
                        INSERT INTO tiktok_ad_group_info (ad_resource_id, channel_id, ad_group_id, ad_group_name,
                            campaign_id, campaign_name, advertiser, ad_group_status, budget, conversion,
                            gross_revenue, ad_spend, profit, roi)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, row.campaignId(), row.channelId(), groupId, campaignName + "_AG1",
                        row.campaignId(), campaignName, account.accountId(), status, budget,
                        totals.adConversions(), totals.grossRevenue().doubleValue(),
                        totals.adSpend().doubleValue(), totals.profit().doubleValue(), totals.roas());
            } else {
                db.update("""
                        UPDATE tiktok_ad_group_info SET ad_resource_id = ?, channel_id = ?, ad_group_name = ?,
                            campaign_name = ?, advertiser = ?, ad_group_status = ?, budget = ?, conversion = ?,
                            gross_revenue = ?, ad_spend = ?, profit = ?, roi = ?
                        WHERE ad_group_id = ?
                        """, row.campaignId(), row.channelId(), campaignName + "_AG1", campaignName,
                        account.accountId(), status, budget, totals.adConversions(),
                        totals.grossRevenue().doubleValue(), totals.adSpend().doubleValue(),
                        totals.profit().doubleValue(), totals.roas(), groupId);
            }
            Integer pixel = db.queryForObject("SELECT COUNT(*) FROM tiktok_pixel_info WHERE pixel_id = ?",
                    Integer.class, "afs-pixel-%03d".formatted(row.sequence() + 1));
            if (pixel == null || pixel == 0) {
                db.update("INSERT INTO tiktok_pixel_info (pixel_id, pixel_code) VALUES (?, ?)",
                        "afs-pixel-%03d".formatted(row.sequence() + 1),
                        "C%04d%04d".formatted(row.sequence() % 10_000, (row.sequence() * 37) % 10_000));
            }
        }
    }

    private void insertDailyMetrics(int siteIndex, CampaignRow row, List<PlatformEconomics.DailyRow> series,
                                    String platform, String country, String campaignName, String landingUrl) {
        String campaignId = row.campaignId();
        String channelId = row.channelId();
        String site = PlatformEconomics.siteCode(siteIndex);
        String adId = row.adId();
        String accountId = PlatformEconomics.AD_ACCOUNTS.get(siteIndex).accountId();
        String publisher = publisherId(siteIndex);
        String customChannel = "partner-" + publisher + ":" + channelId;
        String sourceTag = "{\"source\":\"platform-sync\",\"platform\":\"" + platform + "\"}";

        for (var day : series) {
            String date = day.date().toString();
            double spend = day.adSpend().doubleValue();
            double revenue = day.grossRevenue().doubleValue();
            switch (platform) {
                case "TikTok" -> db.update("""
                        INSERT INTO tiktok_data (`date`, country_code, campaign_id, campaign_name, landing_url,
                                                 channel_id, clicks, spend, impressions, conversion, advertiser)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, date, country, campaignId, campaignName, landingUrl, channelId,
                        day.adClicks(), spend, day.adImpressions(), day.adConversions(), accountId);
                case "Taboola" -> db.update("""
                        INSERT INTO taboola_data (`date`, country_code, campaign_id, campaign_name, landing_url,
                                                  channel_id, clicks, spend, impressions, conversion, account_name)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, date, country, campaignId, campaignName, landingUrl, channelId,
                        day.adClicks(), spend, day.adImpressions(), day.adConversions(), accountId);
                case "Google" -> db.update("""
                        INSERT INTO google_data (`date`, country_code, campaign_id, campaign_name, landing_url,
                                                 channel_id, clicks, spend, impressions, conversion, advertiser, account_name)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, date, country, campaignId, campaignName, landingUrl, channelId,
                        day.adClicks(), spend, day.adImpressions(), day.adConversions(), accountId, accountId);
                default -> db.update("""
                        INSERT INTO meta_data (`date`, country_code, campaign_id, campaign_name, landing_url,
                                               channel_id, clicks, spend, impressions, conversion, advertiser)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, date, country, campaignId, campaignName, landingUrl, channelId,
                        day.adClicks(), spend, day.adImpressions(), day.adConversions(), accountId);
            }

            double ttClicks = 0, ttSpend = 0, ttImpressions = 0, ttConversions = 0;
            double tbClicks = 0, tbSpend = 0, tbImpressions = 0, tbConversions = 0;
            double ggClicks = 0, ggSpend = 0, ggImpressions = 0, ggConversions = 0;
            double fbClicks = 0, fbSpend = 0, fbImpressions = 0, fbConversions = 0;
            switch (platform) {
                case "TikTok" -> {
                    ttClicks = day.adClicks();
                    ttSpend = spend;
                    ttImpressions = day.adImpressions();
                    ttConversions = day.adConversions();
                }
                case "Taboola" -> {
                    tbClicks = day.adClicks();
                    tbSpend = spend;
                    tbImpressions = day.adImpressions();
                    tbConversions = day.adConversions();
                }
                case "Google" -> {
                    ggClicks = day.adClicks();
                    ggSpend = spend;
                    ggImpressions = day.adImpressions();
                    ggConversions = day.adConversions();
                }
                default -> {
                    fbClicks = day.adClicks();
                    fbSpend = spend;
                    fbImpressions = day.adImpressions();
                    fbConversions = day.adConversions();
                }
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
                    """, date, country, customChannel, campaignId, sourceTag, revenue,
                    day.serpImpressions(), day.funnelImpressions(), day.serpImpressions(),
                    day.serpImpressions(), day.funnelImpressions(), day.funnelImpressions(), day.funnelClicks(),
                    day.afsClicks(), day.afsRpc(), day.afsCtr(), day.funnelRpm(),
                    ttClicks, ttSpend, ttImpressions, ttConversions,
                    tbClicks, tbSpend, tbImpressions, tbConversions,
                    ggClicks, ggSpend, ggImpressions, ggConversions,
                    fbClicks, fbSpend, fbImpressions, fbConversions);

            long epoch = day.date().atStartOfDay(REPORT_ZONE).toInstant().toEpochMilli();
            db.update("""
                    INSERT INTO ad_data (app_id, ad_id, day_time, country, pv, total, ecpm, page_site, page_pv,
                                         click, page_ecpm, other)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, appId(siteIndex), adId, epoch, country, day.funnelImpressions(), revenue,
                    day.funnelRpm(), site, day.funnelImpressions(), day.afsClicks(), day.funnelRpm(), sourceTag);
        }
    }

    private void insertRuleLogs(int siteIndex, CampaignRow row, String campaignName, String ruleKey,
                                long ruleId, List<PlatformEconomics.DailyRow> series) {
        var spec = PlatformEconomics.RULES.stream().filter(r -> r.key().equals(ruleKey)).findFirst().orElse(null);
        if (spec == null || series.isEmpty()) return;
        int startIndex = Math.max(0, series.size() - 5);
        int logged = 0;
        for (int i = series.size() - 1; i >= startIndex && logged < 2; i--) {
            var day = series.get(i);
            double spend = day.adSpend().doubleValue();
            double revenue = day.grossRevenue().doubleValue();
            double profit = revenue - spend;
            double roas = spend > 0 ? revenue / spend : 0.0;
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
                    .formatted(row.campaignId(), spend, revenue, roas, spec.budgetAction(), spec.budgetPercent());
            String response = "{\"code\":0,\"status\":\"APPLIED\",\"message\":\"已按规则完成预算调整\"}";
            db.update("""
                    INSERT INTO ad_resource_rule_log (ad_resource_id, ad_group_id, ad_group_name, campaign_id,
                        campaign_name, rule_id, rule_name, action, day_num, `condition`, httpBody, httpResponse, ctime)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, row.campaignId(), "afs-group-%03d".formatted(row.sequence() + 1), campaignName + "_AG1",
                    row.campaignId(), campaignName, ruleId, spec.name(), spec.action(), i + 1,
                    spec.condition(), body, response,
                    Timestamp.valueOf(day.date().atTime(spec.actionTime(), 10 + (row.sequence() % 45))));
            logged++;
        }
    }

    // ------------------------------------------------------------------ feedback & runs

    private void rebuildFeedback(LocalDate end) throws Exception {
        db.update("""
                DELETE FROM feedback WHERE CAST(`value` AS VARCHAR) IN (
                    'The side-by-side spec table saved me hours of research before ordering.',
                    'Very clear cost breakdown—appreciated seeing the hidden fees listed upfront.',
                    'Helpful comparison guide. Would love to see a 6-month follow-up update too.',
                    'Checked the rebate section for my state and found an extra $600 credit.',
                    'Clean layout on mobile and fast loading without annoying popups.',
                    'Finally an article that explains warranty exclusions in plain English.',
                    'Good overview of budget vs flagship models; helped narrow it down to two picks.',
                    'Solid guide overall, though two of the prices went up slightly this month.',
                    'Shared this checklist with my parents—super easy to read and compare.',
                    'Clear pros and cons without feeling like a generic sales pitch.',
                    'Wish you included more regional providers for smaller rural zip codes.',
                    'Accurate numbers and realistic payback estimates—bookmarked for later.'
                )
                """);
        db.update("""
                DELETE FROM local_api_event
                WHERE kind = 'feedback' AND CAST(payload AS VARCHAR) LIKE '%\"source\":\"site-feedback\"%'
                """);
        // Reviews are re-seeded on every run, so drop the previous seed rows by their bodies
        // instead of letting a new set pile up on top of the old one.
        int removed = 0;
        for (String seeded : PlatformEconomics.feedbackPool()) {
            removed += db.update("DELETE FROM feedback WHERE CAST(`value` AS VARCHAR) = ?", seeded);
        }
        if (removed > 0) log.info("Removed {} previously seeded reviews before re-seeding.", removed);
        Random rng = new Random(20261124L);
        for (int siteIndex = 0; siteIndex < PlatformEconomics.SITE_COUNT; siteIndex++) {
            String site = PlatformEconomics.siteCode(siteIndex);
            var vertical = PlatformEconomics.vertical(siteIndex);
            // Reviewers come from the markets this publisher actually buys, so a US-only site
            // does not collect German reviews.
            List<String> markets = PlatformEconomics.marketsForSite(siteIndex);
            int reviews = 2 + Math.floorMod(siteIndex * 5 + 1, 5);
            for (int k = 0; k < reviews; k++) {
                String marketCode = markets.get(Math.floorMod(siteIndex + k * 3, markets.size()));
                String[] locale = localeOf(marketCode);
                var seed = PlatformEconomics.siteFeedback(siteIndex, locale[0], k);
                int daysAgo = rng.nextInt(PlatformEconomics.ACTIVE_DAYS);
                Timestamp when = Timestamp.valueOf(end.minusDays(daysAgo)
                        .atTime(7 + rng.nextInt(16), rng.nextInt(60), rng.nextInt(60)));
                db.update("INSERT INTO feedback (site, rate, locale, `value`, ctime, utime) VALUES (?, ?, ?, ?, ?, ?)",
                        site, Integer.toString(seed.rate()), locale[1], seed.text(), when, when);
                db.update("INSERT INTO local_api_event (kind, site, payload, created_at) VALUES ('feedback', ?, ?, ?)",
                        site, json.writeValueAsString(Map.of(
                                "rate", seed.rate(), "locale", locale[1], "value", seed.text(),
                                "vertical", vertical.categorySlug(), "source", "site-feedback")), when);
            }
        }
    }

    /**
     * Rewrites the seeded approval log so notes quote the run's own spend, revenue and
     * conversion figures instead of one of a handful of repeated sentences. Only notes written
     * by this fixture are touched; hand-written review notes are preserved.
     */
    private void diversifyReviewNotes() throws Exception {
        java.util.Set<String> templated = java.util.Set.of(
                "已核对近 14 天分国家 ROAS 与 AFS RPC，同意按 10% 步长执行预算调整",
                "已完成风控阈值复核，同意先通过预算模拟验证次日利润影响",
                "连续 5 天 ROAS 低于盈亏平衡线，批准降预算止损并保留核心 US 流量",
                "新增站点首周数据波动较大，先观察 3 天再决定是否放量",
                "当前素材进入轻微衰减期，先驳回扩量建议，待本周替换 2 组新素材后复审",
                "两个活动回收达标，批量批准",
                "数据链路核对完成，批准",
                "证据链核对完成，批准",
                "复核并发提案与预算上限后批准",
                "已复核风控阈值与执行闸门，批准执行",
                "近 7 天增量样本不足，驳回本次放量，补齐 D7 回收后再提",
                "落地页与素材相关性未达阈值，先驳回，优化承接页后重新提交",
                "预算余量不足以支撑本次调整，驳回并等待下周预算释放");
        var rows = db.query("SELECT id, state, review_note, CAST(plan AS VARCHAR) AS plan FROM optimization_run WHERE review_note IS NOT NULL",
                (rs, i) -> new Object[]{rs.getString("id"), rs.getString("state"), rs.getString("review_note"), rs.getString("plan")});
        var spendPattern = java.util.regex.Pattern.compile("\"spend\":([0-9.]+)");
        var revenuePattern = java.util.regex.Pattern.compile("\"revenue\":([0-9.]+)");
        var conversionPattern = java.util.regex.Pattern.compile("\"conversions\":([0-9]+)");
        var sitePattern = java.util.regex.Pattern.compile("\"site\":\"([^\"]+)\"");
        for (Object[] row : rows) {
            String note = (String) row[2];
            boolean seeded = templated.contains(note)
                    || note.matches(".*(ROAS [0-9.]+|转化 \\d+ 次|回收达标|样本不足|盈亏平衡|高于 1\\.25).*");
            if (!seeded) continue;
            String plan = row[3] == null ? "" : (String) row[3];
            double spend = number(spendPattern, plan, 0);
            double revenue = number(revenuePattern, plan, 0);
            long conversions = (long) number(conversionPattern, plan, 0);
            String site = text(sitePattern, plan, "该站点");
            double roas = spend > 0 ? revenue / spend : 0;
            boolean rejected = "REJECTED".equals(row[1]);
            String updated = composeReviewNote(rejected, roas, spend, conversions, site,
                    Math.floorMod(((String) row[0]).hashCode(), 5));
            db.update("UPDATE optimization_run SET review_note = ? WHERE id = ?", updated, row[0]);
        }
    }

    private static double number(java.util.regex.Pattern pattern, String text, double fallback) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : fallback;
    }

    private static String text(java.util.regex.Pattern pattern, String value, String fallback) {
        var matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    /** Market code to content locale, keeping copy and review language consistent. */
    private static String[] localeOf(String marketCode) {
        return switch (marketCode == null ? "" : marketCode.toUpperCase(Locale.ROOT)) {
            case "GB" -> new String[]{"en", "en-GB"};
            case "CA" -> new String[]{"en", "en-CA"};
            case "AU" -> new String[]{"en", "en-AU"};
            case "NZ" -> new String[]{"en", "en-NZ"};
            case "IE" -> new String[]{"en", "en-IE"};
            case "SG" -> new String[]{"en", "en-SG"};
            case "IN" -> new String[]{"en", "en-IN"};
            case "PH" -> new String[]{"en", "en-PH"};
            case "ZA" -> new String[]{"en", "en-ZA"};
            case "DE" -> new String[]{"de", "de-DE"};
            case "FR" -> new String[]{"fr", "fr-FR"};
            default -> new String[]{"en", "en-US"};
        };
    }

    private void createOptimizationRuns(List<SeedRunCandidate> candidates) throws Exception {
        // Keep every run that a human or the AI has touched; only seed site-level coverage for
        // publishers that have no run yet, so the console never shows the same plan 32 times.
        for (var candidate : candidates) {
            Integer existing = db.queryForObject("""
                    SELECT COUNT(*) FROM optimization_run
                    WHERE CAST(plan AS VARCHAR) LIKE ?
                    """, Integer.class, "%\"" + candidate.site() + "\"%");
            if (existing != null && existing > 0) continue;
            var input = new OptimizationWorkflow.Input(
                    candidate.site(), candidate.channel(), candidate.campaignId(), "USD",
                    candidate.start(), candidate.end(),
                    candidate.totals().adSpend(), candidate.totals().grossRevenue(),
                    candidate.totals().adImpressions(), candidate.totals().adClicks(),
                    candidate.totals().adConversions(),
                    new BigDecimal("1.25"), new BigDecimal("50.00"), 30, 10);
            var run = optimizer.create(input, () -> candidate.operator(), false);
            int scenario = Math.floorMod(candidate.campaignId().hashCode(), 4);
            if (scenario == 0) continue; // keep a pending run for human review
            String reviewer = PlatformEconomics.OPERATORS.stream()
                    .map(PlatformEconomics.OperatorSpec::name)
                    .filter(name -> !name.equals(candidate.operator()))
                    .skip(Math.floorMod(candidate.campaignId().hashCode(), PlatformEconomics.OPERATORS.size() - 1))
                    .findFirst().orElse("ryan.li");
            var decision = scenario == 1
                    ? OptimizationController.Decision.REJECTED
                    : OptimizationController.Decision.APPROVED;
            optimizer.review(run.id(), new OptimizationController.Review(decision,
                    reviewNote(decision, candidate)), () -> reviewer);
            if (scenario == 3) {
                BigDecimal dailyBudget = candidate.totals().suggestedDailyBudget().signum() > 0
                        ? candidate.totals().suggestedDailyBudget()
                        : candidate.totals().adSpend().divide(BigDecimal.valueOf(PlatformEconomics.ACTIVE_DAYS),
                        2, RoundingMode.HALF_UP);
                optimizer.simulate(run.id(), new OptimizationController.SimulationRequest(dailyBudget),
                        candidate::operator);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Creative naming the way an asset library actually looks: platform, market, concept and
     * ratio with a version suffix, instead of reusing the article headline as a file name.
     */
    private String assetName(int siteIndex, CampaignRow row) {
        PlatformEconomics.CreativeSpec creative = row.creative();
        String platform = PlatformEconomics.vertical(siteIndex).platform().toLowerCase(Locale.ROOT);
        String market = PlatformEconomics.countryForSiteSlot(siteIndex, row.slot()).code().toLowerCase(Locale.ROOT);
        String slug = PlatformEconomics.subtopic(siteIndex, row.slot()).slug()
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
        String ratio = creative.ratio().replace(":", "x");
        int version = 1 + Math.floorMod(row.sequence(), 4);
        String name = "%s_%s_%s_%s_v%d".formatted(platform, market, slug, ratio, version);
        return name.length() > 120 ? name.substring(0, 120) : name;
    }

    private ContentLibrary.CampaignContent content(int siteIndex, int slot) {
        return PlatformEconomics.contentFor(siteIndex, slot);
    }

    /**
     * Review notes quote the numbers the reviewer actually looked at, with different phrasings
     * per decision, so the approval log does not read like one template repeated 30 times.
     */
    private String reviewNote(OptimizationController.Decision decision, SeedRunCandidate candidate) {
        return composeReviewNote(decision == OptimizationController.Decision.REJECTED,
                candidate.totals().roas(), candidate.totals().adSpend().doubleValue(),
                candidate.totals().adConversions(), candidate.site(),
                Math.floorMod(candidate.campaignId().hashCode(), 5));
    }

    /**
     * Review notes have to agree with the numbers they quote. A rejected run with ROAS 1.4 cannot
     * say "below target", and an approved run with ROAS 1.0 cannot say "above target".
     */
    private static String composeReviewNote(boolean rejected, double roas, double spend, long conversions,
                                            String site, int variant) {
        if (rejected) {
            if (roas < 1.10) {
                return "近 14 天 ROAS %.2f、花费 $%.2f，低于盈亏平衡线，驳回扩量，先止损".formatted(roas, spend);
            }
            if (conversions < 300) {
                return "%s 转化仅 %d 次，样本不足，驳回本次放量，补齐 D7 回收后再提".formatted(site, conversions);
            }
            return variant % 2 == 0
                    ? "素材进入衰减期（ROAS %.2f，花费 $%.2f），驳回调整，本周替换素材后复审".formatted(roas, spend)
                    : "落地页与素材相关性未达阈值（ROAS %.2f），驳回，先优化承接页再提".formatted(roas, spend);
        }
        if (roas >= 1.30) {
            return "近 14 天 ROAS %.2f、花费 $%.2f，高于 1.25 目标，批准 +10%% 放量".formatted(roas, spend);
        }
        if (roas >= 1.10) {
            return "%s 回收接近目标（ROAS %.2f），先按 5%% 小步长执行并观察 3 天".formatted(site, roas);
        }
        if (roas >= 1.00) {
            return "ROAS %.2f 刚过盈亏平衡，仅批准小幅调整，不新增预算".formatted(roas);
        }
        return "ROAS %.2f 低于盈亏平衡，仅批准降预算止损，不扩量".formatted(roas);
    }

    private ObjectNode platformInfo(int siteIndex, CampaignRow row, String campaignName,
                                    PlatformEconomics.CampaignTotals totals, String status, String syncedAt) {
        ObjectNode info = json.createObjectNode();
        info.put("localCampaignId", row.campaignId());
        info.put("platform", PlatformEconomics.vertical(siteIndex).platform());
        ObjectNode synced = json.createObjectNode();
        synced.put("id", platformCampaignId(siteIndex, row.slot()));
        synced.put("name", campaignName);
        synced.put("platform", PlatformEconomics.vertical(siteIndex).platform());
        synced.put("status", status);
        if (totals.suggestedDailyBudget().signum() > 0) synced.put("dailyBudget", totals.suggestedDailyBudget());
        if (syncedAt != null) synced.put("syncedAt", syncedAt);
        info.set("platformCampaign", synced);
        if (totals.suggestedDailyBudget().signum() > 0) info.put("campaignBudget", totals.suggestedDailyBudget());
        info.put("optimizationGoal", "CONVERSION");
        info.put("optimizationEvent", "SEARCH_AD_CLICK");
        return info;
    }

    private String platformCampaignId(int siteIndex, int slot) {
        int seq = siteIndex * PlatformEconomics.SLOTS_PER_SITE + slot;
        return switch (PlatformEconomics.vertical(siteIndex).platform()) {
            case "Google" -> Long.toString(2_189_400_000L + seq * 137L);
            case "Taboola" -> Long.toString(4_612_800_000L + seq * 97L);
            case "Facebook" -> Long.toString(120_249_718_590_000_000L + seq * 19_001L);
            default -> Long.toString(1_850_127_310_500_000_000L + seq * 7_919L);
        };
    }

    private String publisherId(int siteIndex) {
        return "pub-" + (7_420_581_639_025_100L + (siteIndex + 1) * 137L);
    }

    private String appId(int siteIndex) {
        return "afs-app-%02d".formatted(siteIndex + 1);
    }

    private String channelId(int sequence) {
        return Long.toString(4_630_219_184L + sequence * 73L);
    }

    private Long ruleId(String key) {
        var spec = PlatformEconomics.RULES.stream().filter(r -> r.key().equals(key)).findFirst().orElse(null);
        if (spec == null) return null;
        return db.queryForObject("SELECT id FROM ad_resource_rule WHERE name = ?", Long.class, spec.name());
    }

    private String creativeFormat(int siteIndex) {
        return switch (PlatformEconomics.vertical(siteIndex).platform()) {
            case "TikTok" -> "video-9x16";
            case "Google" -> "display-rda";
            case "Taboola" -> "native-thumb";
            default -> "feed-image";
        };
    }

    private static String slugify(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private record SeedRunCandidate(String site, String channel, String campaignId, String platform,
                                    String operator, LocalDate start, LocalDate end,
                                    PlatformEconomics.CampaignTotals totals) {}

    private static final class CampaignRow {
        private final int sequence;
        private final String campaignId;
        private final String articleId;
        private final String channelId;
        private final String campaign;
        private final String adPlatform;
        private final String platformInfo;
        private final String platformState;
        private final String state;
        private final Timestamp createdAt;
        private final boolean present;
        private int slot;
        private PlatformEconomics.CampaignTotals totals = new PlatformEconomics.CampaignTotals(
                0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, BigDecimal.ZERO);

        private CampaignRow(int sequence, String campaignId, String articleId, String channelId,
                            String campaign, String adPlatform, String platformInfo, String platformState,
                            String state, Timestamp createdAt, boolean present) {
            this.sequence = sequence;
            this.campaignId = campaignId;
            this.articleId = articleId;
            this.channelId = channelId;
            this.campaign = campaign;
            this.adPlatform = adPlatform;
            this.platformInfo = platformInfo;
            this.platformState = platformState;
            this.state = state;
            this.createdAt = createdAt;
            this.present = present;
        }

        int sequence() { return sequence; }
        String campaignId() { return campaignId; }
        String articleId() { return articleId; }
        String channelId() { return channelId; }
        String campaign() { return campaign; }
        String adPlatform() { return adPlatform; }
        String platformInfo() { return platformInfo; }
        String platformState() { return platformState; }
        String state() { return state; }
        Timestamp createdAt() { return createdAt; }
        boolean present() { return present; }
        int slot() { return slot; }
        PlatformEconomics.CampaignTotals totals() { return totals; }

        CampaignRow withSlot(int value) {
            this.slot = value;
            return this;
        }

        void totals(PlatformEconomics.CampaignTotals value) {
            this.totals = value;
        }

        String materialId() {
            return "afs-material-%03d".formatted(sequence + 1);
        }

        String adId() {
            return "afs-ad-%03d".formatted(sequence + 1);
        }

        PlatformEconomics.CreativeSpec creative() {
            int siteIndex = sequence / PlatformEconomics.SLOTS_PER_SITE;
            return PlatformEconomics.creativeForCampaign(siteIndex, slot);
        }

        boolean active() {
            return slot <= 15;
        }
    }
}
