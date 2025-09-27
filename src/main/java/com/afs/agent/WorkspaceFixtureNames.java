package com.afs.agent;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Order(200)
class WorkspaceFixtureNames implements ApplicationRunner {
    private static final String SEED = "synthetic-workspace-v1";
    private static final String MIGRATION = "synthetic-workspace-names-v2";
    private static final List<Replacement> NAMES = List.of(
            new Replacement("afs-demo-", "afs-site-"),
            new Replacement("pub-demo-", "pub-afs-"),
            new Replacement("demo-", "afs-"),
            new Replacement("test.afs.demo.", "com.afs.reader."),
            new Replacement("[演示] ", ""),
            new Replacement("Demo reading list", "Featured reading"),
            new Replacement("This demo guide", "This guide"),
            new Replacement("Synthetic, local-only campaign", "Content acquisition campaign"),
            new Replacement("本地测试审批", "投放指标已复核"));

    private final JdbcTemplate db;
    private final ChannelCredentialController vault;

    WorkspaceFixtureNames(JdbcTemplate db, ChannelCredentialController vault) {
        this.db = db;
        this.vault = vault;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments arguments) throws Exception {
        if (!migrated(SEED) || migrated(MIGRATION)) return;

        // Only rows carrying the original fixture identifiers are in scope.
        rename("optimization_audit", "run_id IN (SELECT id FROM optimization_run WHERE plan LIKE '%demo-campaign-%')", "actor");
        rename("optimization_run", "plan LIKE '%demo-campaign-%'", "plan,reviewer,review_note,simulation");
        rename("article_info", "article_id LIKE 'demo-article-%'", "article_id,title,category,first_part,text_list,site,other_info");
        rename("category", "value LIKE 'demo-topic-%' AND site LIKE 'afs-demo-%'", "value,label,site");
        rename("category_child", "value LIKE 'demo-topic-%' AND site LIKE 'afs-demo-%'", "value,value_child,label_child,site");
        rename("author_category_map", "author LIKE 'demo-operator-%' AND site LIKE 'afs-demo-%'", "value,author,category,site");
        rename("config_item", "site LIKE 'afs-demo-%'", "name,value,desc,site");
        rename("report_data_config", "site LIKE 'afs-demo-%'", "value,desc,site");
        rename("app_info", "app_id LIKE 'demo-app-%'", "app_id,pname,name");
        rename("ad_info", "ad_id LIKE 'demo-ad-%'", "app_id,ad_id,name");
        rename("ad_data", "ad_id LIKE 'demo-ad-%' AND page_site LIKE 'afs-demo-%'", "app_id,ad_id,page_site");
        rename("adsense_account", "pub_id LIKE 'pub-demo-%' AND site LIKE 'afs-demo-%'", "pub_id,site");
        rename("adsense_channel", "pub_id LIKE 'pub-demo-%'", "pub_id");
        rename("adsense_data", "ad_resource_id LIKE 'demo-campaign-%'", "ad_resource_id,custom_channel_id");
        rename("ad_account_info", "account_id LIKE 'demo-account-%'", "account_id,name,user_name");
        rename("user", "name LIKE 'demo-operator-%' AND partner = 'demo'", "name,desc,site_list");
        rename("user_access", "user_name LIKE 'demo-operator-%' AND app_id LIKE 'demo-app-%'", "user_name,app_id");
        rename("ad_material_category", "value LIKE 'demo-topic-%' AND site LIKE 'afs-demo-%'", "value,label,site");
        rename("ad_material_category_child", "value LIKE 'demo-topic-%'", "value,value_child,label_child");
        rename("ad_material", "ad_material_id LIKE 'demo-material-%'",
                "ad_material_id,name,user_name,category,template,url,cover");
        rename("ad_text", "name LIKE '[演示] %' AND EXISTS (SELECT 1 FROM ad_resource WHERE ad_resource.campaign = ad_text.name"
                + " AND ad_resource.ad_resource_id LIKE 'demo-campaign-%')", "name,ad_text");
        rename("ad_resource", "ad_resource_id LIKE 'demo-campaign-%'",
                "ad_resource_id,article_id,campaign,title,land_page_url,ad_desc,ad_account,account_id,media_list,am,ad_platform_info");
        rename("ad_resource_rule", "creator LIKE 'demo-operator-%' AND name LIKE '[演示] %'", "name,creator,rule");
        rename("ad_resource_rule_log", "ad_resource_id LIKE 'demo-campaign-%'",
                "ad_resource_id,ad_group_id,ad_group_name,campaign_id,campaign_name,rule_name");
        rename("tiktok_ad_group_info", "ad_group_id LIKE 'demo-group-%' AND ad_resource_id LIKE 'demo-campaign-%'",
                "ad_resource_id,ad_group_id,ad_group_name,campaign_id,campaign_name,advertiser");
        rename("tiktok_pixel_info", "pixel_id LIKE 'demo-pixel-%' AND pixel_code LIKE 'demo-pixel-code-%'", "pixel_id,pixel_code");
        rename("tiktok_data", "campaign_id LIKE 'demo-campaign-%'", "campaign_id,campaign_name,landing_url,advertiser");
        rename("meta_data", "campaign_id LIKE 'demo-campaign-%'", "campaign_id,campaign_name,landing_url,advertiser");
        rename("google_data", "campaign_id LIKE 'demo-campaign-%'", "campaign_id,campaign_name,landing_url,account_name");
        rename("taboola_data", "campaign_id LIKE 'demo-campaign-%'", "campaign_id,campaign_name,landing_url,account_name");
        rename("crawl_content", "fid LIKE 'demo-crawl-%' AND source = 'demo'", "fid,content,category");
        rename("keyword_report_data", "article_id LIKE 'demo-article-%'", "article_id");
        rename("google_bigquery_keyword_data", "article_id LIKE 'demo-article-%'", "article_id");
        rename("feedback", "site LIKE 'afs-demo-%'", "site,value");
        rename("ip_info", "site LIKE 'afs-demo-%'", "site");
        rename("local_api_event", "site LIKE 'afs-demo-%'", "site,payload");
        rename("local_api_setting", "site LIKE 'afs-demo-%'", "site,setting_value");
        db.update("UPDATE distributed_lock SET lock_key = 'afs:fixture' WHERE lock_key = 'demo:fixture'");
        db.update("UPDATE crawl_content SET source = 'editorial' WHERE fid LIKE 'afs-crawl-%' AND source = 'demo'");
        db.update("UPDATE `user` SET partner = 'AFS Media' WHERE name LIKE 'afs-operator-%' AND partner = 'demo'");
        db.update("UPDATE article_info SET partner = 'AFS Media' WHERE article_id LIKE 'afs-article-%' AND partner = 'demo'");
        db.update("UPDATE ad_resource SET partner = 'AFS Media' WHERE ad_resource_id LIKE 'afs-campaign-%' AND partner = 'demo'");
        db.update("UPDATE ad_account_info SET partner = 'AFS Media' WHERE account_id LIKE 'afs-account-%' AND partner = 'demo'");
        for (String provider : List.of("TIKTOK", "GOOGLE", "META", "TABOOLA")) {
            String channel = provider.toLowerCase(java.util.Locale.ROOT);
            vault.renameFixtureAlias(provider, "demo-" + channel, channel + "-operations");
        }
        db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
    }

    private boolean migrated(String name) {
        return db.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, name) > 0;
    }

    private void rename(String table, String where, String columns) {
        var parameters = new java.util.ArrayList<Object>();
        String assignments = Arrays.stream(columns.split(",")).map(column -> {
            String identifier = "`" + column + "`";
            String expression = identifier;
            for (Replacement replacement : NAMES) {
                expression = "REPLACE(" + expression + ", ?, ?)";
                parameters.add(replacement.from());
                parameters.add(replacement.to());
            }
            return identifier + " = " + expression;
        }).collect(Collectors.joining(", "));
        db.update("UPDATE `" + table + "` SET " + assignments + " WHERE " + where, parameters.toArray());
    }

    private record Replacement(String from, String to) {}
}
