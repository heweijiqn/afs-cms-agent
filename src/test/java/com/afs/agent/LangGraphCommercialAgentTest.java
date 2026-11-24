package com.afs.agent;

import com.afs.Application;
import com.afs.service.OpDailyReportService;
import com.afs.utils.Coder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local-cms")
@AutoConfigureMockMvc
@SpringBootTest(classes = Application.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:langgraph-commercial-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE,USER",
        "cms.agent.ai.base-url=", "cms.agent.ai.api-key=", "afs.demo.seed=false",
        "afs.workspace.realistic-data=false"
})
class LangGraphCommercialAgentTest {
    private static final String VAULT = "/cms/console/channel-credentials";
    private static final String SIGNING_KEY = "test-langgraph-signing-key";
    private static final String PROVIDER_KEY = "sk-test-langgraph-secret";
    private static final String CAMPAIGN_ID = "lg-camp-001";
    private static final String CHANNEL_ID = "990011";
    private static final String SITE = "afs-lg-site";

    @Autowired MockMvc cms;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockBean LocalWorkspaceData startup;
    @MockBean OpDailyReportService externalDailyReport;

    @DynamicPropertySource
    static void testVault(DynamicPropertyRegistry properties) {
        String path = "target/langgraph-test/" + UUID.randomUUID() + ".key";
        properties.add("cms.channel-vault.key-file", () -> path);
    }

    @Test
    void fullCommercialLangGraphWorkflowToolsReflectionCheckpointsFailoverAndCopilot() throws Exception {
        seedCmsCampaign();

        AtomicInteger optimizationCallCount = new AtomicInteger();
        AtomicInteger copilotCallCount = new AtomicInteger();
        AtomicInteger forcePrimaryFailure = new AtomicInteger(0);

        HttpServer mockLlm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockLlm.createContext("/v1/", exchange -> handleLlmRequest(
                exchange, optimizationCallCount, copilotCallCount, forcePrimaryFailure));
        mockLlm.start();

        try {
            String baseUrl = "http://127.0.0.1:" + mockLlm.getAddress().getPort() + "/v1";
            var aiConfig = Map.of(
                    "baseUrl", baseUrl,
                    "apiKey", PROVIDER_KEY,
                    "chatModel", "primary-chat-model",
                    "fallbackChatModel", "fallback-chat-model",
                    "temperature", 0.2);
            call(post(VAULT), "POST", VAULT,
                    Map.of("provider", "OPENAI", "alias", "openai-ai", "secret", json.writeValueAsString(aiConfig)), 200);

            // 1. Verify /cms/agent/status exposes real LangGraph4j + LangChain4j metadata & Mermaid topology
            JsonNode statusNode = call(get("/cms/agent/status"), "GET", "/cms/agent/status", null, 200);
            assertThat(statusNode.at("/data/configured").asBoolean()).isTrue();
            assertThat(statusNode.at("/data/langGraphVersion").asText()).isEqualTo("1.5.14");
            assertThat(statusNode.at("/data/langChainVersion").asText()).isEqualTo("1.0.1");
            assertThat(statusNode.at("/data/chatModel").asText()).isEqualTo("primary-chat-model");
            assertThat(statusNode.at("/data/fallbackChatModel").asText()).isEqualTo("fallback-chat-model");
            assertThat(statusNode.at("/data/graphs/optimization").asText()).contains("intake_context").contains("guardrail_critic");
            assertThat(statusNode.at("/data/graphs/copilot").asText()).contains("copilot_agent").contains("copilot_tools");

            // 1b. Verify updating AI settings with blank apiKey preserves existing encrypted key
            var updatedAi = Map.of(
                    "baseUrl", baseUrl,
                    "apiKey", "",
                    "chatModel", "primary-chat-model",
                    "fallbackChatModel", "fallback-chat-model",
                    "temperature", 0.3);
            call(post(VAULT), "POST", VAULT,
                    Map.of("provider", "OPENAI", "alias", "openai-ai", "secret", json.writeValueAsString(updatedAi)), 200);
            JsonNode statusAfterKeep = call(get("/cms/agent/status"), "GET", "/cms/agent/status", null, 200);
            assertThat(statusAfterKeep.at("/data/configured").asBoolean()).isTrue();
            assertThat(statusAfterKeep.at("/data/temperature").asDouble()).isEqualTo(0.3);

            // 2. Verify real CMS campaign listing & portfolio anomaly scanner
            JsonNode campaigns = call(get("/cms/agent/campaigns"), "GET", "/cms/agent/campaigns", null, 200);
            assertThat(campaigns.at("/data/list").size()).isGreaterThanOrEqualTo(1);
            assertThat(campaigns.at("/data/list/0/campaignId").asText()).isEqualTo(CAMPAIGN_ID);
            assertThat(campaigns.at("/data/list/0/ruleBound").asBoolean()).isTrue();

            JsonNode scan = call(post("/cms/agent/scan"), "POST", "/cms/agent/scan",
                    Map.of("targetRoas", 1.25, "minimumSpend", 20), 200);
            assertThat(scan.at("/data/counts/scaleOpportunity").asInt()).isGreaterThanOrEqualTo(1);

            // 3. Launch 1-click DEEP multi-agent LangGraph4j optimization on the real CMS campaign
            String launchPath = "/cms/agent/runs/campaigns/" + CAMPAIGN_ID;
            JsonNode created = call(post(launchPath), "POST", launchPath,
                    Map.of("mode", "DEEP", "targetRoas", 1.25, "maxChangePercent", 10), 201);
            String runId = created.at("/data/id").asText();
            assertThat(created.at("/data/plan/explanation/source").asText()).isEqualTo("PENDING");
            assertThat(created.at("/data/plan/evidence/daily").size()).isEqualTo(4);

            JsonNode completed = awaitAnalysis("/cms/agent/runs/" + runId);
            JsonNode explanation = completed.at("/data/plan/explanation");
            assertThat(explanation.path("source").asText()).isEqualTo("LANGCHAIN4J_OPENAI_COMPATIBLE");
            assertThat(explanation.path("mode").asText()).isEqualTo("DEEP");
            assertThat(explanation.path("model").asText()).isEqualTo("primary-chat-model");
            assertThat(explanation.path("toolCalls").asInt()).isEqualTo(2);
            assertThat(explanation.path("revisions").asInt()).isEqualTo(1);
            assertThat(explanation.path("confidence").asDouble()).isEqualTo(0.88);
            assertThat(explanation.path("riskLevel").asText()).isEqualTo("LOW");
            assertThat(explanation.path("inputTokens").asInt()).isGreaterThan(0);
            assertThat(explanation.path("outputTokens").asInt()).isGreaterThan(0);
            assertThat(explanation.path("actionItems").size()).isGreaterThanOrEqualTo(2);
            assertThat(explanation.path("experiments").size()).isGreaterThanOrEqualTo(1);
            assertThat(completed.at("/data/plan/proposal/action").asText()).isEqualTo("INCREASE");
            assertThat(completed.at("/data/plan/proposal/changePercent").asInt()).isEqualTo(10);

            // 4. Verify persisted LangGraph4j execution traces and JDBC checkpoints
            JsonNode trace = call(get("/cms/agent/runs/" + runId + "/trace"), "GET",
                    "/cms/agent/runs/" + runId + "/trace", null, 200);
            assertThat(trace.at("/data/nodes").size()).isGreaterThanOrEqualTo(6);
            assertThat(trace.at("/data/checkpoints").size()).isGreaterThanOrEqualTo(5);

            // 5. Verify automatic fallback model failover via /reanalyze when primary model returns 500
            forcePrimaryFailure.set(1);
            String reanalyzePath = "/cms/agent/runs/" + runId + "/reanalyze";
            JsonNode reanalyzing = call(post(reanalyzePath), "POST", reanalyzePath, Map.of("mode", "STANDARD"), 200);
            assertThat(reanalyzing.at("/data/plan/explanation/source").asText()).isEqualTo("PENDING");
            JsonNode afterFailover = awaitAnalysis("/cms/agent/runs/" + runId);
            assertThat(afterFailover.at("/data/plan/explanation/source").asText()).isEqualTo("LANGCHAIN4J_OPENAI_COMPATIBLE");
            assertThat(afterFailover.at("/data/plan/explanation/model").asText()).isEqualTo("fallback-chat-model");
            assertThat(afterFailover.at("/data/plan/explanation/note").asText()).contains("备选模型");
            forcePrimaryFailure.set(0);

            // 6. Verify Multi-Turn AdOps Copilot LangGraph4j + LangChain4j tool calling + SQL memory
            JsonNode sessionCreated = call(post("/cms/agent/chat/sessions"), "POST", "/cms/agent/chat/sessions",
                    Map.of("title", "测试对话", "campaignId", CAMPAIGN_ID, "runId", runId), 201);
            String sessionId = sessionCreated.at("/data/session/id").asText();

            String msgPath = "/cms/agent/chat/sessions/" + sessionId + "/messages";
            JsonNode turn1 = call(post(msgPath), "POST", msgPath,
                    Map.of("content", "请帮我巡检当前全盘异常活动并给出重点建议"), 200);
            assertThat(turn1.at("/data/content").asText()).contains(CAMPAIGN_ID);
            assertThat(turn1.at("/data/meta/toolCalls").asInt()).isEqualTo(1);
            assertThat(turn1.at("/data/meta/tools/0").asText()).contains("scanAnomalies");

            JsonNode turn2 = call(post(msgPath), "POST", msgPath,
                    Map.of("content", "继续基于刚才的巡检结果给出下一步动作"), 200);
            assertThat(turn2.at("/data/content").asText()).contains("已结合上一轮记忆");

            JsonNode detail = call(get("/cms/agent/chat/sessions/" + sessionId), "GET",
                    "/cms/agent/chat/sessions/" + sessionId, null, 200);
            assertThat(detail.at("/data/messages").size()).isEqualTo(4);

            // 7. Verify commercial telemetry summary
            JsonNode telemetry = call(get("/cms/agent/telemetry"), "GET", "/cms/agent/telemetry", null, 200);
            assertThat(telemetry.at("/data/totals/totalTokens").asInt()).isGreaterThan(0);
            assertThat(telemetry.at("/data/totals/totalToolCalls").asInt()).isGreaterThanOrEqualTo(3);

            // Clean up chat session via DELETE
            JsonNode deleted = call(delete("/cms/agent/chat/sessions/" + sessionId), "DELETE",
                    "/cms/agent/chat/sessions/" + sessionId, null, 200);
            assertThat(deleted.at("/data/deleted").asBoolean()).isTrue();
        } finally {
            mockLlm.stop(0);
        }
    }

    private void handleLlmRequest(HttpExchange exchange, AtomicInteger optCalls, AtomicInteger copilotCalls,
                                  AtomicInteger forcePrimaryFailure) throws IOException {
        try (exchange) {
            if (!("Bearer " + PROVIDER_KEY).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            JsonNode body = json.readTree(exchange.getRequestBody());
            String model = body.path("model").asText("");
            if (forcePrimaryFailure.get() == 1 && "primary-chat-model".equals(model)) {
                sendJson(exchange, 500, "{\"error\":{\"message\":\"simulated primary outage\"}}");
                return;
            }

            String systemPrompt = body.at("/messages/0/content").asText("");
            boolean isCopilot = systemPrompt.contains("智能副驾驶") || systemPrompt.contains("AdOps Copilot");
            boolean isResearch = systemPrompt.contains("Research Specialist");
            String lastRole = body.path("messages").get(body.path("messages").size() - 1).path("role").asText("");
            String lastContent = body.path("messages").get(body.path("messages").size() - 1).path("content").asText("");

            if (isCopilot) {
                int step = copilotCalls.incrementAndGet();
                if (step == 1 && body.path("tools").isArray() && body.path("tools").size() > 0) {
                    sendJson(exchange, 200, """
                            {"id":"cop-1","model":"%s","usage":{"prompt_tokens":120,"completion_tokens":35,"total_tokens":155},
                             "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                             "tool_calls":[{"id":"call_scan_1","type":"function","function":{"name":"scanAnomalies","arguments":"{\\"targetRoas\\":1.25,\\"minSpend\\":20}"}}]}}]}
                            """.formatted(model));
                    return;
                }
                if ("tool".equals(lastRole)) {
                    sendJson(exchange, 200, """
                            {"id":"cop-2","model":"%s","usage":{"prompt_tokens":240,"completion_tokens":80,"total_tokens":320},
                             "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant",
                             "content":"根据实时巡检结果，活动 `%s` 当前处于高潜放量机会区间，建议申请放量 10%%。"}}]}
                            """.formatted(model, CAMPAIGN_ID));
                    return;
                }
                boolean hasHistory = body.path("messages").size() >= 4;
                sendJson(exchange, 200, """
                        {"id":"cop-3","model":"%s","usage":{"prompt_tokens":180,"completion_tokens":60,"total_tokens":240},
                         "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant",
                         "content":"%s：建议先为 %s 发起正式审批单，并同步跟踪 US 地区次日 ROAS。"}}]}
                        """.formatted(model, hasHistory ? "已结合上一轮记忆" : "无历史", CAMPAIGN_ID));
                return;
            }

            if (isResearch) {
                if (!"tool".equals(lastRole)) {
                    sendJson(exchange, 200, """
                            {"id":"res-1","model":"%s","usage":{"prompt_tokens":150,"completion_tokens":45,"total_tokens":195},
                             "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                             "tool_calls":[
                               {"id":"call_daily","type":"function","function":{"name":"dailyReport","arguments":"{}"}},
                               {"id":"call_country","type":"function","function":{"name":"countryBreakdown","arguments":"{}"}}
                             ]}}]}
                            """.formatted(model));
                } else {
                    sendJson(exchange, 200, """
                            {"id":"res-2","model":"%s","usage":{"prompt_tokens":300,"completion_tokens":95,"total_tokens":395},
                             "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant",
                             "content":"多维证据摘要：近4日稳定消耗，ROAS 持续高于 1.8x，US 市场表现优异。"}}]}
                            """.formatted(model));
                }
                return;
            }

            int callNum = optCalls.incrementAndGet();
            if ("fallback-chat-model".equals(model)) {
                String safePlan = """
                        {"action":"INCREASE","changePercent":10,"confidence":0.84,"riskLevel":"LOW",
                         "reason":"备选模型确认 4 日 ROAS 稳定高于目标","evidenceRefs":["4日日度ROAS>1.8x"],
                         "actionItems":["保持 US 市场放量 10%"],"experiments":["测试新利益点标题"],
                         "explanation":"### 备选模型分析\\n活动回收表现健康，建议稳健放量 10%。"}
                        """;
                sendJson(exchange, 200, wrapContent(model, safePlan, 110, 90));
                return;
            }

            if (callNum == 1 && !lastContent.contains("护栏质检节点")) {
                // Intentionally emit out-of-bounds changePercent=25 to exercise LangGraph4j Critic -> self_reflect loop!
                String violatingPlan = """
                        {"action":"INCREASE","changePercent":25,"confidence":0.88,"riskLevel":"MEDIUM",
                         "reason":"ROAS 高于目标，建议激进放量 25%","evidenceRefs":["近4日ROAS 1.85x"],
                         "explanation":"激进放量草案"}
                        """;
                sendJson(exchange, 200, wrapContent(model, violatingPlan, 210, 75));
                return;
            }

            // Corrected strategy after self-reflection!
            String correctedPlan = """
                    {"action":"INCREASE","changePercent":10,"confidence":0.88,"riskLevel":"LOW",
                     "reason":"已按规则上限修正为稳健放量 +10%（4日ROAS持续>1.8x）",
                     "evidenceRefs":["4天日度花费$160/收入$300","US市场ROAS 1.88x","绑定规则上限±10%"],
                     "stageDiagnoses":[
                       {"stage":"MONITOR","status":"READY","evidence":"连续4日稳定消耗，转化24次，趋势向上"},
                       {"stage":"AUDIENCE","status":"OPPORTUNITY","evidence":"US受众ROAS 1.88x，可作为主力放量池"},
                       {"stage":"CREATIVE","status":"OPPORTUNITY","evidence":"现有素材点击率稳定，可并行单变量开头A/B测试"}
                     ],
                     "actionItems":["审批通过后日预算上调10%","持续观察48小时内US市场CPA波动","新建对照组测试对比型文案"],
                     "experiments":["创意A/B：保持落地页不变，仅替换前3秒利益点标题，以CTR与ROAS验收"],
                     "explanation":"### LangGraph4j 多智能体投放诊断报告\\n- **核心结论**：当前活动 4 日综合 ROAS 1.88×，远超目标 1.25×，经护栏自检后建议稳健放量 **+10%**。"}
                    """;
            sendJson(exchange, 200, wrapContent(model, correctedPlan, 260, 140));
        }
    }

    private String wrapContent(String model, String content, int inTok, int outTok) throws IOException {
        return json.writeValueAsString(Map.of(
                "id", "chat-" + UUID.randomUUID(),
                "model", model,
                "usage", Map.of("prompt_tokens", inTok, "completion_tokens", outTok, "total_tokens", inTok + outTok),
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", content)))));
    }

    private void sendJson(HttpExchange exchange, int code, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void seedCmsCampaign() {
        db.update("MERGE INTO `user` (name, password, root, status) KEY(name) VALUES ('local-admin', ?, 1, 1)", SIGNING_KEY);
        db.update("""
                INSERT INTO ad_resource_rule (name, creator, type, action_cycle, rule, budget_change, status)
                VALUES ('LG-Rule-10', 'local-admin', 'BUDGET', 'REPEAT', '[]', 10, 1)
                """);
        Long ruleId = db.queryForObject("SELECT id FROM ad_resource_rule WHERE name = 'LG-Rule-10'", Long.class);
        db.update("""
                INSERT INTO report_data_config (channel, `value`, `desc`, site)
                VALUES (?, '{"source":"test"}', 'LG Test Channel', ?)
                """, CHANNEL_ID, SITE);
        db.update("""
                INSERT INTO article_info (article_id, title, terms, category, first_part, text_list, lang, state, site, status)
                VALUES ('art-lg-1', 'Smart Fitness Guide', 'fitness guide', 'fitness', 'Compare top fitness gear.', '["Body"]', 'en', 'PASS', ?, 1)
                """, SITE);
        db.update("""
                INSERT INTO ad_resource (ad_resource_id, article_id, channel_id, campaign, title, land_page_url,
                    ad_platform, state, rule_id, country, keyword, ad_spend, gross_revenue, profit, roi, status)
                VALUES (?, 'art-lg-1', ?, 'Smart Fitness Growth', 'Smart Fitness Growth', 'https://example.test/?channel=990011',
                    '["TikTok"]', 'PASS', ?, 'US', 'fitness guide', 160.0, 300.0, 140.0, 1.875, 1)
                """, CAMPAIGN_ID, CHANNEL_ID, ruleId);
        db.update("""
                INSERT INTO tiktok_ad_group_info (ad_resource_id, channel_id, ad_group_id, ad_group_name,
                    campaign_id, campaign_name, advertiser, ad_group_status, budget, conversion, gross_revenue, ad_spend, profit, roi, status)
                VALUES (?, ?, 'grp-lg-1', 'US Group', ?, 'Smart Fitness Growth', 'adv-1', 'ENABLE', 40.0, 24, 300.0, 160.0, 140.0, 1.875, 1)
                """, CAMPAIGN_ID, CHANNEL_ID, CAMPAIGN_ID);
        LocalDate baseDay = LocalDate.of(2026, 11, 10);
        for (int i = 0; i < 4; i++) {
            String d = baseDay.plusDays(i).toString();
            db.update("""
                    INSERT INTO tiktok_data (`date`, country_code, campaign_id, campaign_name, channel_id, clicks, spend, impressions, conversion)
                    VALUES (?, 'US', ?, 'Smart Fitness Growth', ?, 120, 40.0, 6000, 6)
                    """, d, CAMPAIGN_ID, CHANNEL_ID);
            db.update("""
                    INSERT INTO adsense_data (`date`, product_code, country_code, custom_channel_id, ad_resource_id, estimated_earnings, status)
                    VALUES (?, 'AFS', 'US', ?, ?, 75.0, 1)
                    """, d, "partner-pub-1:" + CHANNEL_ID, CAMPAIGN_ID);
        }
    }

    private JsonNode awaitAnalysis(String path) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000L;
        JsonNode run = null;
        while (System.currentTimeMillis() < deadline) {
            run = call(get(path), "GET", path, null, 200);
            if (!"PENDING".equals(run.at("/data/plan/explanation/source").asText())) return run;
            Thread.sleep(150L);
        }
        throw new AssertionError("LangGraph analysis timed out for " + path + ": " + run);
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
