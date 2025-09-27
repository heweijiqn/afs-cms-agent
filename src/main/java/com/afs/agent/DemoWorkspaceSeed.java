package com.afs.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@ConditionalOnProperty(name = "afs.demo.seed", havingValue = "true")
@Order(100)
class DemoWorkspaceSeed implements ApplicationRunner {
    private static final String MIGRATION = "synthetic-workspace-v1";
    private static final String SYNTHETIC_JSON = "{\"synthetic\":true}";
    private static final int SITE_COUNT = 12;
    private static final int CAMPAIGNS_PER_SITE = 20;
    private static final int DAYS = 14;
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> TOPICS = List.of("Everyday fitness", "Smart home", "Weekend travel",
            "Meal planning", "Desk setup", "Outdoor essentials", "Home storage", "Learning tools",
            "Personal budgeting", "Garden care", "Mobile accessories", "Seasonal style");
    private static final List<String> ANGLES = List.of("Buying guide", "Starter checklist", "Product comparison",
            "Budget picks", "Common mistakes");
    private static final List<String> COUNTRIES = List.of("US", "GB", "CA", "AU", "DE", "FR");
    private static final List<String> PLATFORMS = List.of("TikTok", "Taboola", "Google", "Facebook");
    private static final List<String> PERFORMANCE_TABLES = List.of("tiktok_data", "taboola_data", "google_data", "meta_data");
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ChannelCredentialController vault;
    private final OptimizationController optimizer;

    DemoWorkspaceSeed(JdbcTemplate db, ObjectMapper json, ChannelCredentialController vault,
                      OptimizationController optimizer) {
        this.db = db;
        this.json = json;
        this.vault = vault;
        this.optimizer = optimizer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        if (db.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, MIGRATION) > 0) return;
        LocalDate end = LocalDate.now(REPORT_ZONE).minusDays(1);
        LocalDate start = end.minusDays(DAYS - 1);
        for (int index = 0; index < SITE_COUNT; index++) {
            Site site = new Site(index);
            seedSite(site);
            for (int slot = 0; slot < CAMPAIGNS_PER_SITE; slot++) {
                seedCampaign(new Campaign(site, slot), start, end);
            }
        }
        for (String provider : List.of("TIKTOK", "GOOGLE", "META", "TABOOLA")) {
            String alias = "demo-" + provider.toLowerCase(Locale.ROOT);
            if (db.queryForObject("SELECT COUNT(*) FROM channel_credential WHERE provider = ? AND alias = ?",
                    Integer.class, provider, alias) == 0) {
                vault.save(new ChannelCredentialController.Credential(provider, alias, "DEMO-ONLY-" + UUID.randomUUID()));
            }
        }
        add("distributed_lock", "lock_key,status,expire_time", "demo:fixture", 0, Timestamp.valueOf(start.atStartOfDay()));
        add("local_migration", "name", MIGRATION);
    }

    private void seedSite(Site site) throws JsonProcessingException {
        List<String> articleIds = java.util.stream.IntStream.range(0, CAMPAIGNS_PER_SITE)
                .mapToObj(slot -> new Campaign(site, slot).articleId()).toList();
        config(site, "homeList", List.of(Map.of("title", "Demo reading list", "list", articleIds)));
        List<String> categories = java.util.stream.IntStream.range(0, CAMPAIGNS_PER_SITE)
                .mapToObj(slot -> "demo-topic-" + new Campaign(site, slot).number()).toList();
        config(site, "homeCategoryList", categories);
        config(site, "homeCategoryContentList", java.util.stream.IntStream.range(0, categories.size()).boxed()
                .collect(Collectors.toMap(categories::get, i -> List.of(articleIds.get(i)))));
        config(site, "homeHotWords", List.of(site.topic(), "comparison", "starter guide"));
        config(site, "feedList", articleIds);
        add("adsense_account", "pub_id,site", site.publisher(), site.code());
        add("app_info", "app_id,pname,name,os,ratio,is_on_app_store", site.appId(), "test.afs.demo." + site.number(),
                "[演示] " + site.topic() + " Reader", site.index() % 2 == 0 ? "Android" : "iOS", 1.0, 0);
        for (int slot = 0; slot < CAMPAIGNS_PER_SITE; slot++) {
            Campaign campaign = new Campaign(site, slot);
            add("config_item", "name,value,desc,site", "demo-placement-" + campaign.number(),
                    json.writeValueAsString(Map.of("sample", true, "placement", campaign.adId())), "[演示] 广告位配置", site.code());
        }
    }

    private void config(Site site, String name, Object value) throws JsonProcessingException {
        add("config_item", "name,value,desc,site", name, json.writeValueAsString(value), "[演示] 内容站点配置", site.code());
    }

    private void seedCampaign(Campaign campaign, LocalDate start, LocalDate end) throws JsonProcessingException {
        Site site = campaign.site();
        String number = campaign.number();
        String headline = campaign.title();
        String title = "[演示] " + headline;
        String owner = "demo-operator-" + number;
        String category = "demo-topic-" + number;
        String purpose = ANGLES.get(campaign.slot() % ANGLES.size());
        String keyword = site.topic().toLowerCase(Locale.ROOT) + " " + purpose.toLowerCase(Locale.ROOT);
        String ownerSites = json.writeValueAsString(List.of(site.code()));
        String account = "demo-account-" + number;
        String materialId = "demo-material-" + number;

        add("user", "name,password,root,status,partner,desc,site_list,ad_account_list",
                owner, "disabled-demo-" + UUID.randomUUID(), 0, 0, "demo", "[演示] " + site.topic(), ownerSites, "[]");
        add("user_access", "user_name,app_id,access", owner, site.appId(), 1);
        add("ad_account_info", "name,user_name,partner,account_id,ad_platform,info",
                "[演示] " + site.platform() + " " + number, owner, "demo", account, site.platform(), "{}");
        Long accountId = db.queryForObject("SELECT id FROM ad_account_info WHERE account_id = ?", Long.class, account);
        db.update("UPDATE `user` SET ad_account_list = ? WHERE name = ?", "[" + accountId + "]", owner);
        add("category", "value,label,site", category, "[演示] " + headline, site.code());
        add("category_child", "value,value_child,label_child,site", category, category + "-guide", purpose, site.code());
        add("author_category_map", "value,label,author,category,site", category, headline, owner, category, site.code());
        String lead = "This demo guide compares " + site.topic().toLowerCase(Locale.ROOT)
                + " options by price, ease of use, and the trade-offs that matter for " + campaign.country() + " readers.";
        String articleBody = json.writeValueAsString(List.of(lead,
                "Start with a budget range and list the features you will use each week.",
                "Compare two options, then check delivery terms and independent user reviews."));
        add("article_info", "article_id,title,terms,category,first_part,text_list,lang,state,site,partner,other_info",
                campaign.articleId(), title, keyword, category, lead, articleBody, "en", "PASS", site.code(), "demo",
                json.writeValueAsString(Map.of("author", owner, "views", 40 + campaign.sequence(), "synthetic", true)));
        add("crawl_content", "fid,content,source,category", "demo-crawl-" + number, articleBody, "demo", category);
        add("keyword_report_data", "pd,article_id,keyword", end.toString(), campaign.articleId(), keyword);
        add("google_bigquery_keyword_data", "pd,article_id,key_word,count",
                end.toString(), campaign.articleId(), keyword, 8 + campaign.sequence() % 93);
        add("ad_material_category", "value,label,site", category, title, site.code());
        add("ad_material_category_child", "value,value_child,label_child", category, category + "-static", "Static creative");
        add("ad_material", "ad_material_id,name,user_name,country,lang,type,dimension,ratio,category,template,url,size",
                materialId, title, owner, campaign.country(), "en", "IMAGE", "1080x1080", "1:1",
                category, "demo-static", "https://" + site.code() + ".test/creative/" + number, 82_000 + campaign.sequence() * 137);
        add("ad_text", "name,ad_text", title, json.writeValueAsString(List.of(headline, "Compare your options before you buy.")));
        add("ad_info", "ad_id,app_id,name,ratio,type,on_off", campaign.adId(), site.appId(), title, 1.0, 1, 1);
        add("adsense_channel", "pub_id,channel_id,state", site.publisher(), campaign.channel(), "ONLINE");
        String settings = json.writeValueAsString(Map.of("source", "synthetic", "campaignId", campaign.id(), "currency", "USD"));
        add("report_data_config", "channel,value,desc,site", campaign.channel(), settings, "[演示] 逐日报表", site.code());
        add("local_api_setting", "kind,site,name,setting_value", "report", site.code(), campaign.channel(), settings);
        String ip = "192.0.2." + (campaign.sequence() + 1);
        List<String> feedbackTexts = List.of("Filters are easy to use.", "The report loaded quickly.",
                "It would help to see the date range on exports.", "The comparison table was useful.");
        String feedback = "[演示] " + feedbackTexts.get(campaign.slot() % feedbackTexts.size());
        Timestamp recent = Timestamp.valueOf(end.atTime(9 + campaign.slot() % 10, campaign.sequence() % 60));
        add("feedback", "site,rate,locale,value,ctime", site.code(), 3 + campaign.slot() % 3, "en-US", feedback, recent);
        add("ip_info", "ip,site,ctime", ip, site.code(), recent);
        add("local_api_event", "kind,site,payload", "feedback", site.code(),
                json.writeValueAsString(Map.of("synthetic", true, "value", feedback, "rate", 3 + campaign.slot() % 3)));

        String ruleName = "[演示] ROAS 阈值 " + number;
        var action = Map.of("action", "BUDGET", "condition", "ROAS < 1.2",
                "budgetAction", "DECREASE", "budgetPercent", 10);
        var rule = Map.of("dayStart", 1, "dayEnd", DAYS, "detailList", List.of(action));
        add("ad_resource_rule", "name,creator,type,action_cycle,rule,budget_change",
                ruleName, owner, "BUDGET", "REPEAT", json.writeValueAsString(List.of(rule)), 10);
        long ruleId = db.queryForObject("SELECT id FROM ad_resource_rule WHERE name = ?", Long.class, ruleName);
        Totals totals = seedDaily(campaign, start);
        add("ad_resource", "ad_resource_id,article_id,channel_id,campaign,title,land_page_url,ad_platform,ad_desc,"
                        + "state,partner,rule_id,country,keyword,ad_account,account_id,ad_platform_state,media_list,am,"
                        + "ad_spend,gross_revenue,profit,roi,ctime,utime",
                campaign.id(), campaign.articleId(), campaign.channel(), title, title, campaign.landingPage(),
                json.writeValueAsString(List.of(site.platform())), "Synthetic, local-only campaign",
                "PASS", "demo", ruleId, campaign.country(), keyword, account, account, "LOCAL_ONLY",
                json.writeValueAsString(List.of(materialId)), owner, money(totals.spend), money(totals.revenue),
                money(totals.revenue - totals.spend), (double) totals.revenue / totals.spend,
                Timestamp.valueOf(start.atTime(9, 0)), recent);
        String group = "demo-group-" + number;
        if (site.platformIndex() == 0) {
            add("tiktok_pixel_info", "pixel_id,pixel_code", "demo-pixel-" + number, "demo-pixel-code-" + number);
            add("tiktok_ad_group_info", "ad_resource_id,channel_id,ad_group_id,ad_group_name,campaign_id,campaign_name,advertiser,"
                            + "ad_group_status,budget,conversion,gross_revenue,ad_spend,profit,roi",
                    campaign.id(), campaign.channel(), group, title, campaign.id(), title, account, "DISABLE", 50,
                    totals.conversions, money(totals.revenue), money(totals.spend),
                    money(totals.revenue - totals.spend), (double) totals.revenue / totals.spend);
        }
        add("ad_resource_rule_log", "ad_resource_id,ad_group_id,ad_group_name,campaign_id,campaign_name,rule_id,rule_name,"
                        + "action,day_num,condition,httpBody,httpResponse,ctime",
                campaign.id(), group, title, campaign.id(), title, ruleId, ruleName, "PREVIEW", DAYS,
                "ROAS < 1.2", SYNTHETIC_JSON, "{\"platformRequest\":false}", recent);
        seedAnalysis(campaign, start, end, totals);
    }

    private Totals seedDaily(Campaign campaign, LocalDate start) {
        Site site = campaign.site();
        Random random = new Random(12017L + campaign.sequence());
        Totals aggregate = new Totals();
        for (int day = 0; day < DAYS; day++) {
            LocalDate date = start.plusDays(day);
            int impressions = 700 + campaign.sequence() % 17 * 190 + day * 45 + random.nextInt(3400);
            int clicks = Math.max(1, (int) (impressions * (0.006 + random.nextDouble() * 0.018)));
            int conversions = Math.min(clicks, (int) (clicks * (0.018 + random.nextDouble() * 0.075)));
            long spend = clicks * (28L + random.nextInt(55));
            long revenue = conversions * (820L + campaign.sequence() % 19 * 82L + random.nextInt(1550));
            int contentViews = Math.max(clicks, impressions / (5 + random.nextInt(4)));
            int contentAdClicks = (int) Math.ceil(contentViews * (0.008 + (campaign.sequence() + day) % 19 / 1000.0));
            String platformAccountColumn = switch (site.platformIndex()) {
                case 0, 3 -> "advertiser";
                default -> "account_name";
            };
            add(PERFORMANCE_TABLES.get(site.platformIndex()), "date,country_code,campaign_id,campaign_name,landing_url,"
                            + "channel_id,clicks,spend,impressions,conversion," + platformAccountColumn,
                    date.toString(), campaign.country(), campaign.id(), "[演示] " + campaign.title(),
                    campaign.landingPage(), campaign.channel(), clicks, money(spend), impressions, conversions,
                    "demo-account-" + campaign.number());
            add("ad_data", "app_id,ad_id,day_time,country,pv,total,ecpm,page_site,page_pv,click,page_ecpm,other",
                    site.appId(), campaign.adId(), date.atStartOfDay(REPORT_ZONE).toInstant().toEpochMilli(),
                    campaign.country(), contentViews, money(revenue), (double) revenue * 10 / contentViews,
                    site.code(), contentViews, clicks, (double) revenue * 10 / contentViews, SYNTHETIC_JSON);
            String prefix = switch (site.platformIndex()) {
                case 0 -> "tt";
                case 1 -> "tb";
                default -> null;
            };
            String cols = "date,product_code,country_code,custom_channel_id,ad_resource_id,adsense_data,estimated_earnings,"
                    + "impressions,individual_ad_impressions,funnel_impressions,funnel_clicks,clicks";
            Object[] values = {date.toString(), "AFS", campaign.country(), "partner-" + site.publisher() + ":" + campaign.channel(),
                    campaign.id(), SYNTHETIC_JSON, money(revenue), contentViews, contentViews, contentViews,
                    contentAdClicks, contentAdClicks};
            if (prefix == null) {
                add("adsense_data", cols, values);
            } else {
                Object[] withPlatform = Arrays.copyOf(values, values.length + 4);
                withPlatform[values.length] = clicks;
                withPlatform[values.length + 1] = money(spend);
                withPlatform[values.length + 2] = impressions;
                withPlatform[values.length + 3] = conversions;
                add("adsense_data", cols + "," + prefix + "_clicks," + prefix + "_spend," + prefix + "_impressions,"
                        + prefix + "_conversion", withPlatform);
            }
            aggregate.impressions += impressions;
            aggregate.clicks += clicks;
            aggregate.conversions += conversions;
            aggregate.spend += spend;
            aggregate.revenue += revenue;
        }
        return aggregate;
    }

    private void seedAnalysis(Campaign campaign, LocalDate start, LocalDate end, Totals totals) throws JsonProcessingException {
        var input = new OptimizationWorkflow.Input(campaign.site().code(), campaign.channel(), campaign.id(), "USD", start, end,
                money(totals.spend), money(totals.revenue), totals.impressions, totals.clicks, totals.conversions,
                BigDecimal.valueOf(1.2), BigDecimal.valueOf(20), 30, 10);
        // 种子数据只跑规则，不触发模型调用（否则 240 条演示记录会打满本地模型）。
        var run = optimizer.create(input, () -> "demo-operator", false);
        int scenario = campaign.sequence() % 4;
        if (scenario == 0) return;
        var decision = scenario == 1 ? OptimizationController.Decision.REJECTED : OptimizationController.Decision.APPROVED;
        optimizer.review(run.id(), new OptimizationController.Review(decision, "[演示] 本地测试审批"), () -> "demo-reviewer");
        if (scenario == 3) {
            var budget = money(totals.spend).divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP);
            optimizer.simulate(run.id(), new OptimizationController.SimulationRequest(budget), () -> "demo-operator");
        }
    }

    private void add(String table, String columns, Object... values) {
        // Table and column names come exclusively from the fixture definitions above.
        String identifiers = Arrays.stream(columns.split(",")).map(column -> "`" + column + "`")
                .collect(Collectors.joining(", "));
        String placeholders = String.join(", ", java.util.Collections.nCopies(values.length, "?"));
        db.update("INSERT INTO `" + table + "` (" + identifiers + ") VALUES (" + placeholders + ")", values);
    }

    private static BigDecimal money(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private static final class Totals {
        long impressions;
        long clicks;
        long conversions;
        long spend;
        long revenue;
    }

    private record Site(int index) {
        String number() { return "%02d".formatted(index + 1); }
        String code() { return "afs-demo-" + number(); }
        String publisher() { return "pub-demo-" + number(); }
        String appId() { return "demo-app-" + number(); }
        String topic() { return TOPICS.get(index); }
        int platformIndex() { return index % PLATFORMS.size(); }
        String platform() { return PLATFORMS.get(platformIndex()); }
    }

    private record Campaign(Site site, int slot) {
        int sequence() { return site.index() * CAMPAIGNS_PER_SITE + slot; }
        String number() { return "%03d".formatted(sequence() + 1); }
        String id() { return "demo-campaign-" + number(); }
        String country() { return COUNTRIES.get(slot % COUNTRIES.size()); }
        String articleId() { return "demo-article-" + number(); }
        String adId() { return "demo-ad-" + number(); }
        String channel() { return Integer.toString(890_001 + sequence()); }
        String title() { return site.topic() + " · " + ANGLES.get(slot % ANGLES.size()) + " · " + country() + " " + number(); }
        String landingPage() { return "https://" + site.code() + ".test/read/" + number() + "?channel=" + channel(); }
    }
}
