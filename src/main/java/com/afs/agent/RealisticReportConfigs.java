package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfills {@code report_data_config} for every campaign channel in the realistic workspace.
 *
 * <p>The realistic seed (32 sites / 640 campaigns) only wrote a single {@code site-overview} row per
 * site, so {@link LegacyContext} reported {@code channelConfigured=false} for every real campaign and
 * the advisory guardrail nullified every model recommendation into {@code HOLD}. This runner derives
 * the owning site from the campaign's article and writes one idempotent row per channel.</p>
 */
@Component
@Order(700)
@ConditionalOnProperty(name = "afs.workspace.realistic-data", havingValue = "true")
class RealisticReportConfigs implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RealisticReportConfigs.class);
    static final String MIGRATION = "realistic-report-config-v1";

    private final JdbcTemplate db;
    private final ObjectMapper json;

    RealisticReportConfigs(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        if (hasMarker(MIGRATION)) return;
        List<Map<String, Object>> pending = db.queryForList("""
                SELECT DISTINCT ar.site AS site, a.channel_id AS channel
                FROM ad_resource a
                JOIN article_info ar ON ar.article_id = a.article_id
                WHERE a.status = 1
                  AND a.channel_id <> ''
                  AND ar.site IS NOT NULL AND ar.site <> ''
                  AND NOT EXISTS (SELECT 1 FROM report_data_config rc WHERE rc.channel = a.channel_id)
                ORDER BY ar.site, a.channel_id
                """);
        if (pending.isEmpty()) {
            db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
            return;
        }
        List<Object[]> rows = new ArrayList<>(pending.size());
        for (Map<String, Object> row : pending) {
            String site = String.valueOf(row.get("site"));
            String channel = String.valueOf(row.get("channel"));
            rows.add(new Object[]{site, channel, payload(site, channel), "渠道日报配置"});
        }
        db.batchUpdate("""
                INSERT INTO report_data_config (site, channel, `value`, `desc`)
                VALUES (?, ?, ?, ?)
                """, rows);
        db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
        log.info("Backfilled {} channel report configs for the realistic workspace", rows.size());
    }

    private String payload(String site, String channel) throws Exception {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("site", site);
        value.put("channel", channel);
        value.put("grain", "daily");
        value.put("metrics", List.of("impressions", "clicks", "conversions", "spend", "revenue"));
        value.put("source", "realistic-seed");
        return json.writeValueAsString(value);
    }

    private boolean hasMarker(String name) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, name);
        return count != null && count > 0;
    }
}
