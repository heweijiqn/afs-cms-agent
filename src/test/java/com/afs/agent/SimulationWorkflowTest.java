package com.afs.agent;

import com.afs.Application;
import com.afs.service.HttpService;
import com.afs.task.AdResourceRuleTask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("agent-sim")
@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:agent-regression", "cms.agent.local-model-port=0"}
)
class SimulationWorkflowTest {
    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    @Autowired
    ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aggregatesCmsEvidenceAndRoutesThreeScenarios() throws Exception {
        assertThat(context.getBeansOfType(HttpService.class)).isEmpty();
        assertThat(context.getBeansOfType(AdResourceRuleTask.class)).isEmpty();
        JsonNode healthy = scenario("healthy", UUID.randomUUID(), 201);
        assertThat(healthy.at("/plan/metrics/roas").doubleValue()).isEqualTo(1.7);
        assertThat(healthy.at("/plan/proposal/changePercent").intValue()).isEqualTo(10);
        assertThat(healthy.at("/plan/evidence/daily").size()).isEqualTo(3);
        assertThat(healthy.at("/plan/findings").size()).isEqualTo(5);
        assertThat(scenario("underperforming", UUID.randomUUID(), 201).at("/plan/proposal/action").asText())
                .isEqualTo("REDUCE");
        assertThat(scenario("low-volume", UUID.randomUUID(), 201).at("/plan/proposal/action").asText())
                .isEqualTo("HOLD");
    }

    @Test
    void retriesReuseTheSamePlanAndCannotChangeScenario() throws Exception {
        UUID key = UUID.randomUUID();
        var request = request("/scenarios/healthy", "{}", key);
        var first = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        assertThat(body(first.get(), 201).path("id").asText())
                .isEqualTo(body(second.get(), 201).path("id").asText());
        scenario("underperforming", key, 409);
    }

    @Test
    void approvesOnceAndRecordsAuditedBudgetSimulation() throws Exception {
        String id = scenario("healthy", UUID.randomUUID(), 201).path("id").asText();
        String base = "/" + id;
        String budget = "{\"currentBudget\":100}";
        post(base + "/simulate", budget, 409);
        var approval = request(base + "/review", "{\"decision\":\"APPROVED\",\"note\":\"reviewed sample\"}", null);
        var first = http.sendAsync(approval, HttpResponse.BodyHandlers.ofString());
        var second = http.sendAsync(approval, HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(first.get().statusCode(), second.get().statusCode())).containsExactlyInAnyOrder(200, 409);
        post(base + "/simulate", "{\"currentBudget\":110}", 409);
        assertThat(post(base + "/simulate", budget, 200).at("/simulation/projectedBudget").doubleValue())
                .isEqualTo(110.0);
        post(base + "/simulate", budget, 409);
        var audit = body(http.send(request(base + "/audit", null, null), HttpResponse.BodyHandlers.ofString()), 200);
        assertThat(audit).extracting(event -> event.path("stage").asText())
                .containsExactly("PENDING", "APPROVED", "SIMULATED");
    }

    @Test
    void rejectedPlansCannotSimulate() throws Exception {
        String path = "/" + scenario("underperforming", UUID.randomUUID(), 201).path("id").asText();
        post(path + "/review", "{\"decision\":\"REJECTED\",\"note\":\"reject sample\"}", 200);
        post(path + "/simulate", "{\"currentBudget\":100}", 409);
    }

    private JsonNode scenario(String name, UUID key, int status) throws Exception {
        return body(http.send(request("/scenarios/" + name, "{}", key), HttpResponse.BodyHandlers.ofString()), status);
    }

    private JsonNode post(String path, String payload, int status) throws Exception {
        return body(http.send(request(path, payload, null), HttpResponse.BodyHandlers.ofString()), status);
    }

    private HttpRequest request(String path, String payload, UUID key) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/cms/agent/runs" + path))
                .timeout(Duration.ofSeconds(10));
        if (key != null) {
            request.header("Idempotency-Key", key.toString());
        }
        if (payload != null) {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload));
        }
        return request.build();
    }

    private JsonNode body(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json.readTree(response.body());
    }
}


