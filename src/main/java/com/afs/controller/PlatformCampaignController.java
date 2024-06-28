package com.afs.controller;

import com.afs.agent.ChannelCredentialController;
import com.afs.agent.ChannelCredentialController.PlatformCredential;
import com.afs.controller.filter.RootAuth;
import com.afs.dao.AdAccountInfoMapper;
import com.afs.dao.AdResourceMapper;
import com.afs.dao.model.AdResource;
import com.afs.utils.ResponseUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@RestController
@Profile("local-cms")
class PlatformCampaignController {
    private final AdResourceMapper resources;
    private final AdAccountInfoMapper accounts;
    private final ObjectMapper json;
    private final RestTemplate http;
    private final ChannelCredentialController vault;

    PlatformCampaignController(AdResourceMapper resources, AdAccountInfoMapper accounts, ObjectMapper json,
            RestTemplateBuilder builder, ChannelCredentialController vault) {
        this.resources = resources;
        this.accounts = accounts;
        this.json = json;
        this.vault = vault;
        String proxy = System.getenv("HTTPS_PROXY");
        if (proxy != null && !proxy.isBlank()) {
            java.net.URI uri = java.net.URI.create(proxy);
            builder = builder.requestFactory(() -> {
                var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
                int port = uri.getPort() < 0 ? 80 : uri.getPort();
                factory.setProxy(new java.net.Proxy(java.net.Proxy.Type.HTTP, new java.net.InetSocketAddress(uri.getHost(), port)));
                return factory;
            });
        }
        this.http = builder.setConnectTimeout(Duration.ofSeconds(10)).setReadTimeout(Duration.ofSeconds(30)).build();
    }

    record Create(String adResourceId, String advertiser, String objective, String budgetId,
                  String branding, BigDecimal bid, BigDecimal spendingLimit, String credentialAlias) {}
    private record PlatformRequest(String url, HttpHeaders headers, Map<String, Object> body, String idPath) {}

    @RootAuth
    @PostMapping("/cms/console/platform-campaign")
    ResponseUtils.Result create(@RequestBody Create input) {
        AdResource row = resources.getByAdResourceId(input.adResourceId());
        if (row == null || !AdResource.PASS.equals(row.getState())) return ResponseUtils.error(-1, "只有审核通过的活动可以提交平台创建");
        var account = accounts.getByAccountId(input.advertiser());
        PlatformRequest request;
        PlatformCredential credential;
        com.fasterxml.jackson.databind.node.ObjectNode details;
        try {
            if (account == null) throw new IllegalArgumentException("请选择已配置的平台广告账号");
            var platforms = json.readTree(row.getAdPlatform());
            if (!platforms.findValuesAsText("").contains(account.getAdPlatform()) &&
                    java.util.stream.StreamSupport.stream(platforms.spliterator(), false)
                            .noneMatch(node -> node.asText().equalsIgnoreCase(account.getAdPlatform()))) {
                throw new IllegalArgumentException("广告账号与活动平台不匹配");
            }
            details = row.getAdPlatformInfo() == null || row.getAdPlatformInfo().isBlank() ? json.createObjectNode()
                    : (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(row.getAdPlatformInfo());
            if (details.has("platformCampaign") || details.has("createResultInfo")) {
                return ResponseUtils.error(-1, "活动已有平台创建记录");
            }
            String provider = switch (account.getAdPlatform()) {
                case "TikTok" -> "TIKTOK";
                case "Facebook" -> "META";
                case "Google" -> "GOOGLE";
                case "Taboola" -> "TABOOLA";
                default -> throw new IllegalArgumentException("当前广告平台不支持创建");
            };
            credential = vault.forPlatform(provider, input.credentialAlias());
            request = request(account.getAdPlatform(), input, row, details, credential);
        } catch (IllegalArgumentException failure) {
            return ResponseUtils.error(-1, failure.getMessage());
        } catch (Exception failure) {
            return ResponseUtils.error(-1, "活动平台配置无法读取");
        }
        if (resources.claimPlatformCampaign(input.adResourceId()) != 1) return ResponseUtils.error(-1, "已有创建请求，或需要核对先前请求的结果");
        try {
            String payload = http.postForObject(request.url(), new HttpEntity<>(request.body(), request.headers()), String.class);
            JsonNode response;
            try {
                response = payload == null || payload.isBlank() ? null : json.readTree(payload);
            } catch (com.fasterxml.jackson.core.JsonProcessingException invalidResponse) {
                return platformFailure(row, "PLATFORM_UNKNOWN", credential.redact(payload));
            }
            if ("TikTok".equalsIgnoreCase(account.getAdPlatform()) && response != null && response.path("code").isIntegralNumber()
                    && response.path("code").asInt() != 0) {
                return platformFailure(row, "PLATFORM_REJECTED", credential.redact(payload));
            }
            String resourceName = response == null ? "" : response.at(request.idPath()).asText();
            if (resourceName.isBlank()) return platformFailure(row, "PLATFORM_UNKNOWN",
                    payload == null || payload.isBlank() ? "平台响应为空" : credential.redact(payload));
            String campaignId = resourceName.substring(resourceName.lastIndexOf('/') + 1);
            var record = details.putObject("platformCampaign");
            record.put("platform", account.getAdPlatform()).put("advertiser", input.advertiser())
                    .put("id", campaignId).put("resourceName", resourceName);
            resources.completePlatformCampaign(row.getAdResourceId(), details.toString(), "平台活动已创建，初始状态为暂停");
            return ResponseUtils.result(Map.of("platform", account.getAdPlatform(), "campaignId", campaignId, "status", "暂停"));
        } catch (RestClientResponseException failure) {
            int status = failure.getRawStatusCode();
            String payload = failure.getResponseBodyAsString();
            String state = List.of(400, 401, 403, 404, 429).contains(status) ? "PLATFORM_REJECTED" : "PLATFORM_UNKNOWN";
            return platformFailure(row, state, payload.isBlank() ? status + " " + failure.getStatusText() : credential.redact(payload));
        } catch (Exception failure) {
            return platformFailure(row, "PLATFORM_UNKNOWN",
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : credential.redact(failure.getMessage()));
        }
    }

    private ResponseUtils.Result platformFailure(AdResource row, String state, String message) {
        resources.updateAdPlatformStateAndMsg(state, message.substring(0, Math.min(450, message.length())), row.getAdResourceId());
        return ResponseUtils.error(-1, message);
    }

    private PlatformRequest request(String platform, Create input, AdResource row, JsonNode details, PlatformCredential credential) {
        String account = input.advertiser();
        if (account == null || !account.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("平台广告账号格式无效");
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = new HashMap<String, Object>();
        String url;
        String idPath;
        switch (platform) {
            case "TikTok" -> {
                requireDigits(account, "TikTok Advertiser ID");
                headers.set("Access-Token", credential.accessToken());
                url = "https://business-api.tiktok.com/open_api/v1.3/campaign/create/";
                idPath = "/data/campaign_id";
                String mode = details.path("campaignBudgetModel").asText("BUDGET_MODE_INFINITE");
                body.putAll(Map.of("advertiser_id", account, "campaign_name", row.getCampaign(),
                        "objective_type", choice(input.objective(), "WEB_CONVERSIONS", "TRAFFIC"),
                        "operation_status", "DISABLE", "budget_mode", mode));
                if (!"BUDGET_MODE_INFINITE".equals(mode)) body.put("budget", positive(details.path("campaignBudget").decimalValue(), "活动预算"));
            }
            case "Facebook" -> {
                account = account.replaceFirst("^act_", "");
                requireDigits(account, "Meta 广告账户");
                headers.setBearerAuth(credential.accessToken());
                url = "https://graph.facebook.com/v27.0/act_" + account + "/campaigns";
                idPath = "/id";
                body.putAll(Map.of("name", row.getCampaign(), "status", "PAUSED",
                        "objective", choice(input.objective(), "OUTCOME_TRAFFIC", "OUTCOME_SALES", "OUTCOME_LEADS"),
                        "special_ad_categories", List.of()));
            }
            case "Google" -> {
                account = account.replace("-", "");
                requireDigits(account, "Google Ads 客户编号");
                requireDigits(input.budgetId(), "Google Ads 预算编号");
                if (credential.developerToken().isBlank()) throw new IllegalArgumentException("Google Ads 凭据缺少 developerToken");
                headers.setBearerAuth(credential.accessToken());
                headers.set("developer-token", credential.developerToken());
                String manager = credential.loginCustomerId().replace("-", "");
                if (!manager.isBlank()) {
                    requireDigits(manager, "Google Ads 管理员客户编号");
                    headers.set("login-customer-id", manager);
                }
                String customer = "customers/" + account;
                url = "https://googleads.googleapis.com/v26/" + customer + "/campaigns:mutate";
                idPath = "/results/0/resourceName";
                body.put("operations", List.of(Map.of("create", Map.of("name", row.getCampaign(), "status", "PAUSED",
                        "advertisingChannelType", "SEARCH", "campaignBudget", customer + "/campaignBudgets/" + input.budgetId(),
                        "manualCpc", Map.of(), "containsEuPoliticalAdvertising",
                        choice(input.objective(), "DOES_NOT_CONTAIN_EU_POLITICAL_ADVERTISING", "CONTAINS_EU_POLITICAL_ADVERTISING")))));
            }
            case "Taboola" -> {
                headers.setBearerAuth(credential.accessToken());
                url = "https://backstage.taboola.com/backstage/api/1.0/" + account + "/campaigns/";
                idPath = "/id";
                if (input.branding() == null || input.branding().isBlank()) throw new IllegalArgumentException("请填写品牌名称");
                body.putAll(Map.of("name", row.getCampaign(), "branding_text", input.branding(), "is_active", false,
                        "bid_strategy", "FIXED", "marketing_objective", "DRIVE_WEBSITE_TRAFFIC", "spending_limit_model", "MONTHLY",
                        "cpc", positive(input.bid(), "点击出价"), "spending_limit", positive(input.spendingLimit(), "月度预算")));
            }
            default -> throw new IllegalArgumentException("当前广告平台不支持创建");
        }
        return new PlatformRequest(url, headers, body, idPath);
    }

    private static void requireDigits(String value, String label) {
        if (value == null || !value.matches("[0-9]+")) throw new IllegalArgumentException(label + "必须为平台数字编号");
    }

    private static String choice(String value, String... allowed) {
        if (value == null || !List.of(allowed).contains(value)) throw new IllegalArgumentException("请选择有效的目标或声明");
        return value;
    }

    private static BigDecimal positive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(label + "必须大于零");
        return value;
    }

}
