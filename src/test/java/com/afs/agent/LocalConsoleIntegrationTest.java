package com.afs.agent;

import com.afs.Application;
import com.afs.service.LocalMediaStore;
import com.afs.service.OpDailyReportService;
import com.afs.utils.Coder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local-cms")
@AutoConfigureMockMvc
@SpringBootTest(classes = Application.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-platform;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE,USER",
        "cms.agent.ai.base-url=", "cms.agent.ai.api-key=", "afs.demo.seed=false"
})
class LocalConsoleIntegrationTest {
    private static final String VAULT = "/cms/console/channel-credentials";
    private static final String IMAGE = "/cms/ai/image/create";
    private static final String SIGNING_KEY = "test-cms-signing-key";
    private static final String PROVIDER_KEY = "test-provider-key";
    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN1sAAAAASUVORK5CYII=";

    @Autowired MockMvc cms;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockBean LocalWorkspaceData startup;
    @MockBean OpDailyReportService externalDailyReport;

    @DynamicPropertySource
    static void testVault(DynamicPropertyRegistry properties) {
        String path = "target/ai-platform-test/" + UUID.randomUUID() + ".key";
        properties.add("cms.channel-vault.key-file", () -> path);
    }

    @Test
    void configuredProviderWorksForImageAndAgentWithoutRestarting() throws Exception {
        insertTestAdmin();
        cms.perform(post(IMAGE).header("admin-name", "local-admin")
                .contentType("application/json").content("{\"desc\":\"no signature\"}")).andExpect(status().isUnauthorized());
        var missing = call(post(IMAGE), "POST", IMAGE, Map.of("desc", "hello"), 200);
        assertThat(missing.path("msg").asText()).contains("配置图片模型");

        var provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/custom/v1/", this::answer);
        provider.start();
        try {
            String baseUrl = "http://127.0.0.1:" + provider.getAddress().getPort() + "/custom/v1";
            var configuration = Map.of("baseUrl", baseUrl, "apiKey", PROVIDER_KEY,
                    "chatModel", "test-chat", "imageModel", "test-image");
            var credential = Map.of("provider", "OPENAI", "alias", "openai-ai", "secret", json.writeValueAsString(configuration));
            assertThat(call(post(VAULT), "POST", VAULT, credential, 200).path("code").asInt()).isZero();
            assertThat(db.queryForObject("SELECT CAST(encrypted AS VARCHAR) FROM channel_credential WHERE provider='OPENAI'",
                    String.class)).doesNotContain(PROVIDER_KEY);
            var settings = call(get(VAULT + "/ai"), "GET", VAULT + "/ai", null, 200);
            assertThat(settings.at("/data/baseUrl").asText()).isEqualTo(baseUrl);
            assertThat(settings.toString()).doesNotContain(PROVIDER_KEY);
            var check = call(post(VAULT + "/check"), "POST", VAULT + "/check",
                    Map.of("provider", "OPENAI", "alias", "openai-ai"), 200);
            assertThat(check.at("/data/httpStatus").asInt()).isEqualTo(200);
            assertThat(check.at("/data/body").asText()).contains("均在模型列表");

            var image = call(post(IMAGE), "POST", IMAGE, Map.of("desc", "portrait \"quoted\""), 200);
            assertThat(image.path("code").asInt()).isZero();
            String url = image.at("/data/url").asText();
            assertThat(url).matches("/media/[0-9a-f-]+\\.png");
            var saved = LocalMediaStore.directory().resolve(url.substring("/media/".length()));
            try {
                var downloaded = cms.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse();
                assertThat(downloaded.getContentAsByteArray()).isEqualTo(Base64.getDecoder().decode(PNG));
            } finally {
                Files.deleteIfExists(saved);
            }

            String scenario = "/cms/agent/runs/scenarios/healthy";
            var created = call(post(scenario).header("Idempotency-Key", UUID.randomUUID().toString()),
                    "POST", scenario, Map.of(), 201);
            assertThat(created.at("/data/plan/explanation/source").asText()).isEqualTo("PENDING");
            String runPath = "/cms/agent/runs/" + created.path("data").path("id").asText();
            var run = awaitAnalysis(runPath);
            assertThat(run.at("/data/plan/explanation/source").asText()).isEqualTo("LANGCHAIN4J_OPENAI_COMPATIBLE");
            assertThat(run.at("/data/plan/explanation/text").asText()).isEqualTo("Provider generated analysis.");
            assertThat(run.at("/data/plan/explanation/model").asText()).isEqualTo("test-chat");
            assertThat(run.at("/data/plan/explanation/durationMs").asLong()).isGreaterThanOrEqualTo(0);

            var rejected = call(post(IMAGE), "POST", IMAGE, Map.of("desc", "reject"), 200);
            assertThat(rejected.path("code").asInt()).isEqualTo(-1);
            assertThat(rejected.path("msg").asText()).contains("provider rejection").doesNotContain(PROVIDER_KEY);
        } finally {
            provider.stop(0);
        }
    }

    @Test
    void maintenanceReadsLocalDataAndRecalculatesOnlyTheSelectedDay() throws Exception {
        insertTestAdmin();
        String date = "2025-01-14";
        String channel = "7000000001";
        String reportChannel = "partner-pub-0000000000000001:" + channel;
        String base = "/cms/console/maintenance";
        db.update("""
                INSERT INTO ad_resource (ad_resource_id, campaign, channel_id, state)
                VALUES ('synthetic-activity', 'Test activity', ?, 'PASS')
                """, channel);
        for (var country : java.util.List.of("CA", "US")) {
            db.update("""
                    INSERT INTO adsense_data
                        (`date`, product_code, country_code, custom_channel_id, ad_resource_id, estimated_earnings, tt_spend)
                    VALUES (?, 'AFS', ?, ?, 'synthetic-activity', 20, 2)
                    """, date, country, reportChannel);
        }
        db.update("INSERT INTO tiktok_data (`date`, channel_id, spend) VALUES (?, ?, 7)", date, channel);
        db.update("INSERT INTO meta_data (`date`, channel_id, spend) VALUES (?, ?, 3)", date, channel);

        var options = call(get(base + "/options"), "GET", base + "/options", null, 200);
        assertThat(options.at("/data/channels/0/id").asText()).isEqualTo(reportChannel);
        assertThat(options.at("/data/dates/0").asText()).isEqualTo(date);
        var detail = call(get(base + "/adsense?reportChannel=" + reportChannel
                        + "&dateStart=" + date + "&dateEnd=" + date + "&countryCode=US"),
                "GET", base + "/adsense?reportChannel=" + reportChannel
                        + "&dateStart=" + date + "&dateEnd=" + date + "&countryCode=US", null, 200);
        assertThat(detail.at("/data/list").size()).isEqualTo(1);
        assertThat(detail.at("/data/list/0/revenue").asDouble()).isEqualTo(20);

        var daily = call(get("/daily/report"), "GET", "/daily/report", null, 200);
        assertThat(daily.at("/data/date").asText()).isEqualTo(date);
        assertThat(daily.at("/data/list/0/spend").asDouble()).isEqualTo(10);
        assertThat(daily.at("/data/list/0/revenue").asDouble()).isEqualTo(40);
        verifyNoInteractions(externalDailyReport);

        String recalculate = base + "/recalculate";
        var recalc = call(post(recalculate), "POST", recalculate, Map.of("date", date), 200);
        assertThat(recalc.at("/data/updated").asInt()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT roi FROM ad_resource WHERE ad_resource_id = 'synthetic-activity'",
                Double.class)).isEqualTo(4);
        var empty = call(post(recalculate), "POST", recalculate, Map.of("date", "2025-01-15"), 200);
        assertThat(empty.at("/data/updated").asInt()).isZero();
        assertThat(db.queryForObject("SELECT roi FROM ad_resource WHERE ad_resource_id = 'synthetic-activity'",
                Double.class)).isEqualTo(4);
    }

    /** The advisory agent now runs after the request commits; wait for the plan to be rewritten. */
    private JsonNode awaitAnalysis(String path) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000L;
        JsonNode run = null;
        while (System.currentTimeMillis() < deadline) {
            run = call(get(path), "GET", path, null, 200);
            if (!"PENDING".equals(run.at("/data/plan/explanation/source").asText())) return run;
            Thread.sleep(200L);
        }
        throw new AssertionError("analysis never finished for " + path + ": " + run);
    }

    private void insertTestAdmin() {
        db.update("MERGE INTO `user` (name, password, root, status) KEY(name) VALUES ('local-admin', ?, 1, 1)", SIGNING_KEY);
    }

    private void answer(HttpExchange exchange) throws IOException {
        try (exchange) {
            int code = 200;
            String reply;
            if (!("Bearer " + PROVIDER_KEY).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                code = 401;
                reply = "{}";
            } else if (exchange.getRequestURI().getPath().endsWith("/models") && "GET".equals(exchange.getRequestMethod())) {
                reply = "{\"data\":[{\"id\":\"test-chat\"},{\"id\":\"test-image\"}]}";
            } else {
                var body = json.readTree(exchange.getRequestBody());
                if (exchange.getRequestURI().getPath().endsWith("/images/generations") && body.path("model").asText().equals("test-image")) {
                    if (body.path("prompt").asText().equals("portrait \"quoted\"")) {
                        reply = json.writeValueAsString(Map.of("data", java.util.List.of(Map.of("b64_json", PNG))));
                    } else {
                        code = 400;
                        reply = json.writeValueAsString(Map.of("error", "provider rejection " + PROVIDER_KEY));
                    }
                } else if (exchange.getRequestURI().getPath().endsWith("/chat/completions") &&
                        body.path("model").asText().equals("test-chat") && body.path("messages").isArray()) {
                    reply = """
                            {"id":"test-chat-response","choices":[{"index":0,"finish_reason":"stop",
                            "message":{"role":"assistant","content":"Provider generated analysis."}}]}
                            """;
                } else {
                    code = 404;
                    reply = "{}";
                }
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String method, String path, Object body, int statusCode) throws Exception {
        byte[] payload = body == null ? null : json.writeValueAsBytes(body);
        var headers = new TreeMap<>(Map.of("admin-console", "1", "admin-name", "local-admin",
                "admin-nonce", UUID.randomUUID().toString(), "admin-timestamp", Long.toString(System.currentTimeMillis())));
        if (payload != null) headers.put("admin-body-md5", Coder.from(payload).md5().asHexLower());
        var signed = new StringBuilder(path).append("&").append(method).append("&");
        headers.forEach((name, value) -> {
            request.header(name, value);
            signed.append(name).append("=").append(value).append("&");
        });
        request.header("admin-signature", Coder.from(signed.append(SIGNING_KEY).append("&").toString()).md5().asHexLower());
        if (payload != null) request.contentType("application/json").content(payload);
        return json.readTree(cms.perform(request).andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsByteArray());
    }
}
