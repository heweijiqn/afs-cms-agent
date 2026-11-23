package com.afs.agent;

import com.afs.Application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("agent-sim")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:adops-insights-test",
                "cms.agent.local-model-port=0"
        })
class AdOpsInsightsTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void verifiesFunnelPacingAlertsDaypartingAndBatchOps() throws Exception {
        // 1. Conversion funnel & winners/bleeders matrix
        var funnelResp = get("/cms/agent/ops/funnel");
        assertThat(funnelResp.statusCode()).isEqualTo(200);
        JsonNode funnel = json.readTree(funnelResp.body());
        assertThat(funnel.path("stages")).hasSize(4);
        assertThat(funnel.path("campaignCount").asInt()).isGreaterThan(0);

        // 2. Budget pacing & spend firewall
        var pacingResp = get("/cms/agent/ops/pacing");
        assertThat(pacingResp.statusCode()).isEqualTo(200);
        JsonNode pacing = json.readTree(pacingResp.body());
        assertThat(pacing.path("summary").path("firewallHealth").asText()).isIn("SAFE", "WATCH", "ALERT");
        assertThat(pacing.path("items").isArray()).isTrue();

        // 3. Alert rules CRUD + real-time scan + acknowledge
        var rulesResp = get("/cms/agent/ops/alerts/rules");
        assertThat(rulesResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(rulesResp.body()).size()).isGreaterThanOrEqualTo(4);

        var addRuleResp = post("/cms/agent/ops/alerts/rules", """
                {"name":"测试高消耗提醒","metric":"SPEND","operator":"GTE","threshold":1.0,"severity":"WARN","channel":"FEISHU","enabled":true}
                """);
        assertThat(addRuleResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(addRuleResp.body()).path("saved").asBoolean()).isTrue();

        var scanResp = post("/cms/agent/ops/alerts/scan", "{}");
        assertThat(scanResp.statusCode()).isEqualTo(200);
        JsonNode scan = json.readTree(scanResp.body());
        assertThat(scan.path("scannedRules").asInt()).isGreaterThanOrEqualTo(5);
        // Historical scenarios have no current-day observations: no fabricated alerts.
        assertThat(scan.path("events").size()).isZero();

        // 4. 7x24 Dayparting schedule save & 168-bit validation
        var dpListResp = get("/cms/agent/ops/dayparting");
        assertThat(dpListResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(dpListResp.body()).size()).isGreaterThanOrEqualTo(3);

        String bits168 = "1".repeat(84) + "0".repeat(84);
        var dpSaveResp = post("/cms/agent/ops/dayparting", """
                {"scopeKey":"global:default","label":"全局默认投放排期","platform":"ALL","timezone":"UTC+8","matrixBits":"%s","presetName":"自定义排期"}
                """.formatted(bits168));
        assertThat(dpSaveResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(dpSaveResp.body()).path("activeHours").asInt()).isEqualTo(84);

        var dpBadResp = post("/cms/agent/ops/dayparting", """
                {"scopeKey":"global:default","matrixBits":"10101"}
                """);
        assertThat(dpBadResp.statusCode()).isEqualTo(400);

        // 5. Batch campaign operation & quick clone
        var batchResp = post("/cms/agent/ops/campaigns/batch", """
                {"adResourceIds":["demo-1","demo-2"],"action":"SET_STATE","state":"PASS"}
                """);
        assertThat(batchResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(batchResp.body()).path("affected").asInt()).isEqualTo(2);

        var cloneResp = post("/cms/agent/ops/campaigns/demo-1/clone", "{}");
        assertThat(cloneResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(cloneResp.body()).path("cloned").asBoolean()).isTrue();
    }

    @Test
    void verifiesWebhookTestPatrolBriefCsvCreativeBridgeAndAttribution() throws Exception {
        // 1. Webhook test on seeded placeholder rule returns clear PLACEHOLDER_URL status
        var rules = json.readTree(get("/cms/agent/ops/alerts/rules").body());
        long ruleId = rules.get(0).path("id").asLong();
        var testResp = post("/cms/agent/ext/alerts/rules/" + ruleId + "/test", "{}");
        assertThat(testResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(testResp.body()).path("status").asText()).isEqualTo("PLACEHOLDER_URL");

        // 2. Patrol config & Daily Executive Brief
        var patrolSave = post("/cms/agent/ext/patrol", """
                {"enabled":true,"intervalMinutes":30,"dailyBriefHour":9,"channel":"FEISHU","webhookUrl":"","autoPropose":false}
                """);
        assertThat(patrolSave.statusCode()).isEqualTo(200);
        assertThat(json.readTree(patrolSave.body()).path("intervalMinutes").asInt()).isEqualTo(30);

        var briefResp = post("/cms/agent/ext/brief/generate", "{\"pushWebhook\":false}");
        assertThat(briefResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(briefResp.body()).path("summaryMarkdown").asText()).contains("全盘经营概览");

        // 3. External CSV Ad Report Analyzer
        String csv = "Campaign Name,Amount Spent,Conversion Value,Impressions,Clicks,Conversions,Frequency\\n"
                + "FB_Win,200,360,20000,600,70,1.9\\n"
                + "FB_Bleed,150,90,30000,200,10,4.5";
        var csvResp = post("/cms/agent/ext/csv-analyze", "{\"platform\":\"Facebook\",\"csvText\":\"" + csv + "\"}");
        assertThat(csvResp.statusCode()).isEqualTo(200);
        JsonNode csvNode = json.readTree(csvResp.body());
        assertThat(csvNode.path("summary").path("rowCount").asInt()).isEqualTo(2);
        assertThat(csvNode.path("summary").path("winnerCount").asInt()).isEqualTo(1);
        assertThat(csvNode.path("summary").path("bleederCount").asInt()).isEqualTo(1);

        // 4. Creative Fatigue & Copy/Script Lab
        var fatResp = get("/cms/agent/ext/creatives/fatigue");
        assertThat(fatResp.statusCode()).isEqualTo(200);
        var genResp = post("/cms/agent/ext/creatives/generate", """
                {"campaignId":"demo-1","campaignName":"Dental Plan","platform":"TikTok","style":"PAIN_POINT"}
                """);
        assertThat(genResp.statusCode()).isEqualTo(200);
        JsonNode genNode = json.readTree(genResp.body());
        assertThat(genNode.path("generated").asInt()).isEqualTo(3);
        long draftId = genNode.path("drafts").get(0).path("id").asLong();
        var pubResp = post("/cms/agent/ext/creatives/drafts/" + draftId + "/status", "{\"status\":\"AB_TESTING\"}");
        assertThat(pubResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(pubResp.body()).path("status").asText()).isEqualTo("AB_TESTING");

        // 5. Five-Agent -> One-click OptimizationRun Proposal Bridge
        var camps = json.readTree(get("/cms/agent/campaigns?limit=5").body());
        String firstCid = camps.get(0).path("campaignId").asText();
        var propResp = post("/cms/agent/ext/five-agent/propose", """
                {"campaignId":"%s","action":"INCREASE_BUDGET","changePercent":15,"targetRoas":1.25,"rationale":"五智能体测试转审批"}
                """.formatted(firstCid));
        assertThat(propResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(propResp.body()).path("state").asText()).isEqualTo("PENDING");
        assertThat(json.readTree(propResp.body()).path("proposalHash").asText()).hasSize(64);

        // 6. Post-execution attribution & decision memory
        var attrResp = get("/cms/agent/ext/attribution");
        assertThat(attrResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(attrResp.body()).path("lessons").size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void verifiesOverseasLocalizationCohortAndSmartAutomation() throws Exception {
        // 1. Currencies exchange rates & symbols
        var currResp = get("/cms/agent/overseas/currencies");
        assertThat(currResp.statusCode()).isEqualTo(200);
        JsonNode curr = json.readTree(currResp.body());
        assertThat(curr.path("rates").path("USD").asDouble()).isEqualTo(1.0);
        assertThat(curr.path("rates").has("CNY")).isFalse();
        assertThat(curr.path("symbols").path("EUR").asText()).isEqualTo("€");

        // Missing AI config must not fabricate localized copy.
        var genResp = post("/cms/agent/overseas/localization/generate", """
                {"campaignId":"demo-1","topic":"Solar Power Battery","targetCountry":"DE","targetLanguage":"de-DE","platform":"Google"}
                """);
        assertThat(genResp.statusCode()).isEqualTo(400);

        // 3. Policy compliance radar audit on clean vs violating copy
        var cleanAudit = post("/cms/agent/overseas/localization/audit", """
                {"text":"Certified solar inverter solution with transparent pricing and online calculator.","platform":"Google","targetCountry":"US"}
                """);
        assertThat(cleanAudit.statusCode()).isEqualTo(200);
        assertThat(json.readTree(cleanAudit.body()).path("riskScore").asInt()).isGreaterThanOrEqualTo(80);

        var badAudit = post("/cms/agent/overseas/localization/audit", """
                {"text":"100% guaranteed profit! Are you broke? BUY NOW RIGHT HERE!!!!!!","platform":"Meta","targetCountry":"US"}
                """);
        assertThat(badAudit.statusCode()).isEqualTo(200);
        JsonNode badNode = json.readTree(badAudit.body());
        assertThat(badNode.path("riskScore").asInt()).isLessThan(80);
        assertThat(badNode.path("flaggedTokens").size()).isGreaterThan(0);
        assertThat(badNode.path("aiRevisionSuggestion").asText()).isNotBlank();

        // 4. Cohort Retention & D0-D30 Payback Analyzer
        var cohortResp = get("/cms/agent/overseas/cohort?days=14&currency=USD");
        assertThat(cohortResp.statusCode()).isEqualTo(200);
        JsonNode cohortNode = json.readTree(cohortResp.body());
        assertThat(cohortNode.path("cohorts").size()).isZero();
        assertThat(cohortNode.path("summary").path("avgPaybackDays").isNull()).isTrue();
        assertThat(cohortNode.path("summary").path("ltvCacRatio").isNull()).isTrue();
        assertThat(cohortNode.path("summary").path("narrative").asText()).contains("Cohort");

        // 5. Autonomous smart automation rules CRUD & evaluation
        var rulesResp = get("/cms/agent/overseas/automation/rules");
        assertThat(rulesResp.statusCode()).isEqualTo(200);
        JsonNode rules = json.readTree(rulesResp.body());
        assertThat(rules.size()).isGreaterThanOrEqualTo(4);

        var evalResp = post("/cms/agent/overseas/automation/evaluate", "{}");
        assertThat(evalResp.statusCode()).isEqualTo(200);
        JsonNode evalNode = json.readTree(evalResp.body());
        assertThat(evalNode.path("evaluatedRules").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(evalNode.path("events").isArray()).isTrue();

        var logsResp = get("/cms/agent/overseas/automation/logs?limit=10");
        assertThat(logsResp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(logsResp.body()).isArray()).isTrue();
    }


    private HttpResponse<String> get(String path) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
