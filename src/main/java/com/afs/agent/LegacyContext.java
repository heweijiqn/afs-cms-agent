package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class LegacyContext {
    private final JsonNode channelFixture;
    private final JsonNode ruleFixture;
    private final JdbcTemplate database;
    private final boolean localWorkspace;

    LegacyContext(ObjectMapper json, JdbcTemplate database, @Value("${afs.local-workspace:false}") boolean localWorkspace)
            throws IOException {
        this.database = database;
        this.localWorkspace = localWorkspace;
        try (var source = new ClassPathResource("agent/simulation/afs-api-channel.json").getInputStream()) {
            channelFixture = json.readTree(source).path("data");
        }
        try (var source = new ClassPathResource("agent/simulation/afs-cms-rules.json").getInputStream()) {
            ruleFixture = json.readTree(source).path("data");
        }
    }

    Snapshot load(String site, String channel, String campaignId) {
        if (localWorkspace) {
            int channels = database.queryForObject("SELECT COUNT(*) FROM report_data_config WHERE site = ? AND channel = ?",
                    Integer.class, site, channel);
            int rules = database.queryForObject("SELECT COUNT(*) FROM ad_resource_rule WHERE status = 1", Integer.class);
            int cap = database.query("""
                    SELECT r.budget_change FROM ad_resource a JOIN ad_resource_rule r ON a.rule_id = r.id
                    WHERE a.ad_resource_id = ? AND r.status = 1
                    """, (row, index) -> row.getDouble(1), campaignId).stream().findFirst()
                    .map(change -> (int) Math.min(20, Math.floor(Math.abs(change)))).orElse(0);
            return new Snapshot("LOCAL_CMS", channels > 0, rules, cap);
        }
        boolean channelConfigured = site.equals(channelFixture.path("site").asText())
                && channel.equals(channelFixture.path("channel").asText());
        int changeCap = ruleFixture.path("list").path(0).path("budgetChange").asInt();
        return new Snapshot("SIMULATED", channelConfigured, ruleFixture.path("totalSize").asInt(), changeCap);
    }

    JsonNode exampleRules() {
        return ruleFixture.path("list").deepCopy();
    }

    JsonNode exampleChannel() {
        return channelFixture.deepCopy();
    }

    record Snapshot(String source, boolean channelConfigured, int exampleRuleCount, int budgetChangeCap) {}
}

