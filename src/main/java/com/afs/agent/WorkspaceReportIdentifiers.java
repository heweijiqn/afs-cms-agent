package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Order(300)
class WorkspaceReportIdentifiers implements ApplicationRunner {
    private static final String MIGRATION = "synthetic-report-identifiers-v3";
    private final JdbcTemplate db;
    private final ObjectMapper json;

    WorkspaceReportIdentifiers(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        if (hasMarker(MIGRATION) || !hasMarker("synthetic-workspace-names-v2")) return;
        for (int site = 1; site <= 12; site++) {
            String oldPublisher = "pub-afs-%02d".formatted(site);
            String publisher = "pub-" + (7_420_581_639_025_100L + site * 137);
            db.update("UPDATE adsense_account SET pub_id = ? WHERE pub_id = ? AND site = ?",
                    publisher, oldPublisher, "afs-site-%02d".formatted(site));
            for (int slot = 0; slot < 20; slot++) {
                int index = (site - 1) * 20 + slot;
                String campaign = "afs-campaign-%03d".formatted(index + 1);
                String oldChannel = Integer.toString(890_001 + index);
                String channel = Long.toString(4_630_219_184L + index * 73L);
                renameChannel(campaign, oldPublisher, publisher, oldChannel, channel);
                configureRule(campaign, index);
            }
        }
        db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
    }

    private boolean hasMarker(String name) {
        return db.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, name) > 0;
    }

    private void renameChannel(String campaign, String oldPublisher, String publisher, String oldChannel, String channel) {
        db.update("UPDATE adsense_channel SET pub_id = ?, channel_id = ? WHERE pub_id = ? AND channel_id = ?",
                publisher, channel, oldPublisher, oldChannel);
        db.update("UPDATE adsense_data SET custom_channel_id = ? WHERE ad_resource_id = ? AND custom_channel_id = ?",
                "partner-" + publisher + ":" + channel, campaign, "partner-" + oldPublisher + ":" + oldChannel);
        db.update("UPDATE ad_resource SET channel_id = ?, land_page_url = REPLACE(land_page_url, ?, ?) "
                        + "WHERE ad_resource_id = ? AND channel_id = ?",
                channel, "channel=" + oldChannel, "channel=" + channel, campaign, oldChannel);
        for (String table : List.of("tiktok_data", "google_data", "meta_data", "taboola_data")) {
            db.update("UPDATE " + table + " SET channel_id = ?, landing_url = REPLACE(landing_url, ?, ?) "
                            + "WHERE campaign_id = ? AND channel_id = ?",
                    channel, "channel=" + oldChannel, "channel=" + channel, campaign, oldChannel);
        }
        db.update("UPDATE tiktok_ad_group_info SET channel_id = ? WHERE ad_resource_id = ? AND channel_id = ?",
                channel, campaign, oldChannel);
        db.update("UPDATE report_data_config SET channel = ? WHERE channel = ? AND site LIKE 'afs-site-%'", channel, oldChannel);
        db.update("UPDATE local_api_setting SET name = ? WHERE kind = 'report' AND name = ? AND site LIKE 'afs-site-%'",
                channel, oldChannel);
        db.update("UPDATE optimization_run SET plan = REPLACE(plan, ?, ?) WHERE plan LIKE ?",
                channelField(oldChannel), channelField(channel), "%\"campaignId\":\"" + campaign + "\"%");
    }

    private static String channelField(String channel) {
        return "\"channel\":\"" + channel + "\"";
    }

    private void configureRule(String campaign, int index) throws Exception {
        int threshold = 35 + index % 10 * 5;
        int percent = 5 + index % 6;
        String label;
        Map<String, Object> action;
        switch (index % 4) {
            case 0 -> {
                label = "高回报扩量";
                action = Map.of("action", "CHANGE_BUDGET", "condition", "AD_SPEND >= " + threshold + " && ROI >= 1.8",
                        "budgetAction", "INCREASE", "budgetPercent", percent);
            }
            case 1 -> {
                label = "低回报降预算";
                action = Map.of("action", "CHANGE_BUDGET", "condition", "AD_SPEND >= " + threshold + " && ROI < 1.1",
                        "budgetAction", "DECREASE", "budgetPercent", percent);
            }
            case 2 -> {
                label = "无收益止损";
                action = Map.of("action", "CLOSE_CAMPAIGN", "condition", "AD_SPEND >= " + threshold + " && REVENUE <= 0");
            }
            default -> {
                label = "亏损保护";
                action = Map.of("action", "CHANGE_BUDGET", "condition", "AD_SPEND >= " + threshold
                        + " && PROFIT <= -" + (15 + index % 7 * 5), "budgetAction", "DECREASE", "budgetPercent", percent);
            }
        }
        String window = json.writeValueAsString(List.of(Map.of("dayStart", index % 3 + 1, "dayEnd", 30, "detailList", List.of(action))));
        db.update("""
                UPDATE ad_resource_rule SET name = ?, type = 'REPEAT', action_cycle = 'REPEAT',
                    action_time = ?, rule = ?, budget_change = ?
                WHERE id = (SELECT rule_id FROM ad_resource WHERE ad_resource_id = ?)
                  AND name = ? AND type = 'BUDGET' AND CAST(rule AS VARCHAR) LIKE '%ROAS < 1.2%'
                """, "%s · %03d".formatted(label, index + 1), 8 + index % 8, window,
                index % 4 == 2 ? 0 : percent, campaign, "ROAS 阈值 %03d".formatted(index + 1));
    }
}
