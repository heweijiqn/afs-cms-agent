package com.afs.agent;

import com.afs.Application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("agent-sim")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:agent-guardrails-test",
                "cms.agent.local-model-port=0"
        })
class AgentGuardrailsAndReplayTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void enforcesFingerprintIdempotencyKillSwitchRollbackAndOfflineReplay() throws Exception {
        // 1. Create a healthy scenario run and verify 8-signal scorecard + SHA-256 fingerprint
        var createdResp = post("/cms/agent/runs/scenarios/healthy", "{}", UUID.randomUUID().toString());
        assertThat(createdResp.statusCode()).isEqualTo(201);
        JsonNode created = json.readTree(createdResp.body());
        String runId = created.path("id").asText();
        String proposalHash = created.path("proposalHash").asText();
        int revision = created.path("proposalRevision").asInt();

        assertThat(proposalHash).hasSize(64);
        assertThat(revision).isEqualTo(1);
        assertThat(created.at("/plan/scorecard/signals")).hasSize(8);
        assertThat(created.at("/plan/scorecard/verdict").asText()).isEqualTo("SCALE");

        // 2. Tampered fingerprint must be rejected with 409 Conflict
        var staleReview = post("/cms/agent/runs/" + runId + "/review",
                "{\"decision\":\"APPROVED\",\"note\":\"stale check\",\"expectedHash\":\"deadbeef\",\"expectedRevision\":1}",
                null);
        assertThat(staleReview.statusCode()).isEqualTo(409);

        // 3. Exact fingerprint + revision approval succeeds
        var okReview = post("/cms/agent/runs/" + runId + "/review",
                "{\"decision\":\"APPROVED\",\"note\":\"verified fingerprint\",\"expectedHash\":\""
                        + proposalHash + "\",\"expectedRevision\":" + revision + "}",
                null);
        assertThat(okReview.statusCode()).isEqualTo(200);
        assertThat(json.readTree(okReview.body()).path("state").asText()).isEqualTo("APPROVED");

        double budget = created.at("/plan/evidence/observedBudget").asDouble();
        String execBody = "{\"currentBudget\":" + budget + ",\"expectedHash\":\""
                + proposalHash + "\",\"expectedRevision\":" + revision + "}";

        // 4. Kill Switch blocks execution with 423 Locked
        assertThat(post("/cms/agent/safety/kill-switch", "{\"active\":true,\"reason\":\"incident drill\"}", null)
                .statusCode()).isEqualTo(200);
        assertThat(post("/cms/agent/runs/" + runId + "/execute", execBody, UUID.randomUUID().toString())
                .statusCode()).isEqualTo(423);
        assertThat(post("/cms/agent/safety/kill-switch", "{\"active\":false,\"reason\":\"\"}", null)
                .statusCode()).isEqualTo(200);

        // 5. SHADOW mode blocks execution with 409 Conflict
        assertThat(post("/cms/agent/safety", "{\"safetyMode\":\"SHADOW\"}", null).statusCode()).isEqualTo(200);
        assertThat(post("/cms/agent/runs/" + runId + "/execute", execBody, UUID.randomUUID().toString())
                .statusCode()).isEqualTo(409);
        assertThat(post("/cms/agent/safety", "{\"safetyMode\":\"COPILOT\",\"dailyBudgetDeltaCap\":500}", null)
                .statusCode()).isEqualTo(200);

        // 6. Idempotent execution: first call commits, identical replay returns 200 without duplicate ledger row
        String idemKey = UUID.randomUUID().toString();
        var exec1 = post("/cms/agent/runs/" + runId + "/execute", execBody, idemKey);
        assertThat(exec1.statusCode()).isEqualTo(200);
        assertThat(json.readTree(exec1.body()).path("state").asText()).isEqualTo("EXECUTED");

        var execReplay = post("/cms/agent/runs/" + runId + "/execute", execBody, idemKey);
        assertThat(execReplay.statusCode()).isEqualTo(200);

        // Same Idempotency-Key with different body must be rejected (409)
        var execConflict = post("/cms/agent/runs/" + runId + "/execute",
                "{\"currentBudget\":999.99,\"expectedHash\":\"" + proposalHash + "\",\"expectedRevision\":1}",
                idemKey);
        assertThat(execConflict.statusCode()).isEqualTo(409);

        JsonNode ledgerAfterExec = json.readTree(get("/cms/agent/runs/" + runId + "/ledger").body());
        assertThat(ledgerAfterExec).hasSize(1);
        assertThat(ledgerAfterExec.get(0).path("actionType").asText()).isEqualTo("EXECUTE");

        // 7. One-click Saga Rollback restores beforeBudget and writes compensating ledger row
        var rolledBack = post("/cms/agent/runs/" + runId + "/rollback", "{\"reason\":\"post-check revert\"}", null);
        assertThat(rolledBack.statusCode()).isEqualTo(200);
        assertThat(json.readTree(rolledBack.body()).path("state").asText()).isEqualTo("ROLLED_BACK");

        JsonNode ledgerAfterRollback = json.readTree(get("/cms/agent/runs/" + runId + "/ledger").body());
        assertThat(ledgerAfterRollback).hasSize(2);
        assertThat(ledgerAfterRollback.get(1).path("actionType").asText()).isEqualTo("ROLLBACK");

        // 8. Offline Counterfactual Replay + 4-Gate Verification works deterministically with zero LLM keys
        var replayResp = post("/cms/agent/replay",
                "{\"targetRoas\":1.25,\"minimumSpend\":30,\"maxChangePercent\":10,\"limit\":20}", null);
        assertThat(replayResp.statusCode()).isEqualTo(200);
        JsonNode replay = json.readTree(replayResp.body());
        assertThat(replay.path("gates")).hasSize(4);
        assertThat(replay.at("/summary/evaluatedCampaigns").asInt()).isGreaterThan(0);

        // 9. 5-Agent Closed-Loop (Monitor + Audience + Creative + Bidding + Optimize/Convex Allocation)
        var fiveResp = post("/cms/agent/five-agent/run",
                "{\"targetRoas\":1.25,\"maxChangePercent\":15,\"useLlmForCreative\":false,\"iteration\":1}", null);
        assertThat(fiveResp.statusCode()).isEqualTo(200);
        JsonNode five = json.readTree(fiveResp.body());
        assertThat(five.at("/monitor/healthScore").asInt()).isBetween(5, 100);
        assertThat(five.at("/audience/topSegments").size()).isGreaterThan(0);
        assertThat(five.at("/audience/lookalikePlans")).hasSize(3);
        assertThat(five.at("/creative/variants").size()).isBetween(3, 5);
        double targetCpa = five.at("/bidding/targetCpa").asDouble();
        double recBid = five.at("/bidding/recommendedBid").asDouble();
        assertThat(recBid).isLessThanOrEqualTo(targetCpa * 0.80 + 0.01);
        double curSum = five.at("/optimize/totalCurrentBudget").asDouble();
        double optSum = five.at("/optimize/totalOptimalBudget").asDouble();
        assertThat(Math.abs(optSum - curSum)).isLessThan(0.10);
    }

    private HttpResponse<String> post(String path, String body, String idemKey) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idemKey != null) {
            builder.header("Idempotency-Key", idemKey);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
