package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Order(400)
class WorkspaceCampaignIds implements ApplicationRunner {
    private static final String MIGRATION = "local-platform-format-campaign-ids-v4";
    private final JdbcTemplate db;
    private final ObjectMapper json;

    WorkspaceCampaignIds(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        if (!hasMarker("synthetic-report-identifiers-v3") || hasMarker(MIGRATION)) return;
        var campaigns = db.query("""
                SELECT ad_resource_id, ad_platform, ad_platform_info
                FROM ad_resource WHERE ad_resource_id LIKE 'afs-campaign-%' AND partner = 'AFS Media'
                ORDER BY ad_resource_id
                """, (row, index) -> new Campaign(row.getString(1), row.getString(2), row.getString(3)));
        for (Campaign campaign : campaigns) migrate(campaign);
        db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
    }

    private void migrate(Campaign campaign) throws Exception {
        String oldId = campaign.id();
        if (!oldId.matches("afs-campaign-[0-9]{3}")) return;
        long offset = Long.parseLong(oldId.substring("afs-campaign-".length())) * 7919;
        long base = switch (json.readTree(campaign.platform()).path(0).asText()) {
            case "TikTok" -> 1_850_127_310_500_000_000L;
            case "Facebook" -> 120_249_718_590_000_000L;
            case "Google" -> 24_508_000_000L;
            case "Taboola" -> 38_600_000L;
            default -> throw new IllegalStateException("Unsupported fixture platform for " + oldId);
        };
        String newId = Long.toString(base + offset);
        ObjectNode info = campaign.info() == null || campaign.info().isBlank() ? json.createObjectNode()
                : (ObjectNode) json.readTree(campaign.info());
        info.put("localCampaignId", newId);
        db.update("UPDATE ad_resource SET ad_resource_id = ?, ad_platform_info = ? WHERE ad_resource_id = ?",
                newId, info.toString(), oldId);
        for (String table : List.of("adsense_data", "tiktok_ad_group_info", "ad_resource_rule_log")) {
            replaceColumn(table, "ad_resource_id", oldId, newId);
        }
        for (String table : List.of("tiktok_data", "meta_data", "google_data", "taboola_data",
                "tiktok_ad_group_info", "ad_resource_rule_log")) {
            replaceColumn(table, "campaign_id", oldId, newId);
        }
        replaceJson("optimization_run", "plan", oldId, newId);
        replaceJson("optimization_run", "simulation", oldId, newId);
        replaceJson("report_data_config", "value", oldId, newId);
        replaceJson("local_api_setting", "setting_value", oldId, newId);
        replaceJson("local_api_event", "payload", oldId, newId);
    }

    private void replaceColumn(String table, String column, String oldId, String newId) {
        db.update("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?", newId, oldId);
    }

    private void replaceJson(String table, String column, String oldId, String newId) {
        String identifier = "`" + column + "`";
        String token = "\"" + oldId + "\"";
        db.update("UPDATE " + table + " SET " + identifier + " = REPLACE(" + identifier + ", ?, ?) WHERE "
                + identifier + " LIKE ?", token, "\"" + newId + "\"", "%" + token + "%");
    }

    private boolean hasMarker(String name) {
        return db.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, name) > 0;
    }

    private record Campaign(String id, String platform, String info) {}
}
