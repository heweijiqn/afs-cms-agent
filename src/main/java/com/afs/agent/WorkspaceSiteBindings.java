package com.afs.agent;

import com.afs.controller.SiteController;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Order(500)
class WorkspaceSiteBindings implements ApplicationRunner {
    private final JdbcTemplate db;
    private static final String MIGRATION = "controller-site-bindings-v5";

    WorkspaceSiteBindings(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (!marked("local-platform-format-campaign-ids-v4") || marked(MIGRATION)) return;
        for (int i = 0; i < 12; i++) {
            String previous = "afs-site-%02d".formatted(i + 1);
            String site = SiteController.sites().get(i);
            rebindConfig(previous, site);
            for (String table : List.of("article_info", "category", "category_child", "author_category_map",
                    "ad_material_category", "report_data_config", "feedback", "ip_info", "local_api_setting", "local_api_event")) {
                db.update("UPDATE " + table + " SET site = ? WHERE site = ?", site, previous);
            }
            db.update("UPDATE ad_data SET page_site = ? WHERE page_site = ?", site, previous);
            replace("adsense_account", "site", previous, site);
            replace("`user`", "site_list", "\"" + previous + "\"", "\"" + site + "\"");
            replace("ad_resource", "land_page_url", "://" + previous + ".test/", "://" + site + ".test/");
            for (String table : List.of("tiktok_data", "google_data", "meta_data", "taboola_data")) {
                replace(table, "landing_url", "://" + previous + ".test/", "://" + site + ".test/");
            }
            for (String column : List.of("url", "cover")) {
                replace("ad_material", column, "://" + previous + ".test/", "://" + site + ".test/");
            }
            for (String column : List.of("plan", "simulation")) {
                replace("optimization_run", column, "\"" + previous + "\"", "\"" + site + "\"");
            }
        }
        db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
    }

    private void rebindConfig(String previous, String site) {
        db.update("""
                DELETE FROM config_item target
                WHERE target.site = ?
                  AND ((target.name IN ('homeList', 'homeCategoryList', 'homeHotWords', 'feedList')
                        AND target.`desc` = '内容列表配置' AND CAST(target.`value` AS VARCHAR) = '[]')
                       OR (target.name = 'homeCategoryContentList'
                           AND target.`desc` = '分类内容配置' AND CAST(target.`value` AS VARCHAR) = '{}'))
                  AND EXISTS (SELECT 1 FROM config_item source WHERE source.site = ? AND source.name = target.name)
                """, site, previous);
        db.update("UPDATE config_item SET site = ? WHERE site = ?", site, previous);
    }

    private void replace(String table, String column, String previous, String updated) {
        db.update("UPDATE " + table + " SET " + column + " = REPLACE(" + column + ", ?, ?) WHERE "
                + column + " LIKE ?", previous, updated, "%" + previous + "%");
    }

    private boolean marked(String name) {
        return db.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, name) > 0;
    }
}
