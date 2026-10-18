package com.afs.agent;

import com.afs.Application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
                "spring.datasource.url=jdbc:h2:mem:agent-staff-test",
                "cms.agent.local-model-port=0",
                "cms.agent.require-staff-login=true",
                "cms.agent.operator-password=test-operator-pass",
                "cms.agent.reviewer-password=test-reviewer-pass"
        })
class StaffWorkflowTest {
    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    @Test
    void staffRolesControlApprovalAndIdentifyAuditActors() throws Exception {
        var client = HttpClient.newHttpClient();
        UUID key = UUID.randomUUID();
        String scenario = "/scenarios/healthy";
        assertThat(client.send(post(scenario, "{}", key, null), HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(401);
        assertThat(client.send(post(scenario, "{}", key, "reviewer"), HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(403);
        var created = client.send(post(scenario, "{}", key, "operator"), HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        String path = "/" + json.readTree(created.body()).path("id").asText();
        String review = "{\"decision\":\"APPROVED\",\"note\":\"staff check\"}";
        assertThat(client.send(post(path + "/review", review, null, "operator"), HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(403);
        assertThat(client.send(post(path + "/review", review, null, "reviewer"), HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(200);
        var audit = client.send(request(path + "/audit", "reviewer").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(audit.statusCode()).isEqualTo(200);
        assertThat(json.readTree(audit.body())).extracting(event -> event.path("actor").asText())
                .containsExactly("operator", "reviewer");
    }

    private HttpRequest post(String path, String body, UUID key, String user) {
        var request = request(path, user).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (key != null) {
            request.header("Idempotency-Key", key.toString());
        }
        return request.build();
    }

    private HttpRequest.Builder request(String path, String user) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/cms/agent/runs" + path));
        if (user != null) {
            String credentials = user + ":test-" + user + "-pass";
            request.header("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return request;
    }
}


