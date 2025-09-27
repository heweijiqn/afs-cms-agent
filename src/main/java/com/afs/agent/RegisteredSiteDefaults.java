package com.afs.agent;

import com.afs.controller.SiteController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Profile("local-cms")
class RegisteredSiteDefaults implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    RegisteredSiteDefaults(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        for (String site : SiteController.sites()) {
            for (String key : List.of("homeList", "homeCategoryList", "homeHotWords", "feedList")) {
                jdbc.update("""
                        INSERT INTO config_item (site, name, `value`, `desc`)
                        SELECT ?, ?, '[]', '内容列表配置'
                        WHERE NOT EXISTS (SELECT 1 FROM config_item WHERE site = ? AND name = ?)
                        """, site, key, site, key);
            }
            jdbc.update("""
                    INSERT INTO config_item (site, name, `value`, `desc`)
                    SELECT ?, 'homeCategoryContentList', '{}', '分类内容配置'
                    WHERE NOT EXISTS (SELECT 1 FROM config_item WHERE site = ? AND name = 'homeCategoryContentList')
                    """, site, site);
            jdbc.update("""
                    INSERT INTO report_data_config (site, channel, `value`, `desc`)
                    SELECT ?, 'site-overview', ?, '站点日报配置'
                    WHERE NOT EXISTS (SELECT 1 FROM report_data_config WHERE site = ? AND channel = 'site-overview')
                    """, site, json.writeValueAsString(Map.of("site", site, "grain", "daily",
                    "metrics", List.of("impressions", "clicks", "conversions", "spend", "revenue"))), site);
        }
    }
}
