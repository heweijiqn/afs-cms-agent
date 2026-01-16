package com.afs.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import com.afs.controller.filter.RootAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/cms/console/channel-credentials")
public class ChannelCredentialController {
    private final JdbcTemplate jdbc;
    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();
    private final HttpClient http = createHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    static HttpClient.Builder createHttpClientBuilder() {
        var builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10));
        String proxy = System.getenv("HTTPS_PROXY");
        if (proxy == null || proxy.isBlank()) {
            proxy = System.getenv("HTTP_PROXY");
        }
        if (proxy != null && !proxy.isBlank()) {
            URI uri = URI.create(proxy.strip());
            if (uri.getHost() != null) {
                var proxySelector = ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort() < 0 ? 80 : uri.getPort()));
                builder.proxy(new ProxySelector() {
                    @Override
                    public List<java.net.Proxy> select(URI endpoint) {
                        String host = endpoint.getHost();
                        if ("127.0.0.1".equals(host) || "[::1]".equals(host) || "localhost".equalsIgnoreCase(host)
                                || AiPlatformSettings.isPrivateIpv4(host)) {
                            return List.of(java.net.Proxy.NO_PROXY);
                        }
                        return proxySelector.select(endpoint);
                    }

                    @Override
                    public void connectFailed(URI endpoint, java.net.SocketAddress address, java.io.IOException failure) {
                        proxySelector.connectFailed(endpoint, address, failure);
                    }
                });
            }
        }
        return builder;
    }

    static HttpClient createHttpClient() {
        return createHttpClientBuilder().build();
    }

    ChannelCredentialController(JdbcTemplate jdbc,
                                @Value("${cms.channel-vault.key-file:}") String configuredPath) {
        this.jdbc = jdbc;
        String localAppData = System.getenv("LOCALAPPDATA");
        Path stateDirectory = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".afs-cms")
                : Path.of(localAppData, "AFS-CMS");
        this.keyFile = configuredPath.isBlank() ? stateDirectory.resolve("channel-vault.key") : Path.of(configuredPath);
    }

    @GetMapping
    List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                SELECT provider AS "provider", alias AS "alias", updated_at AS "updatedAt"
                FROM channel_credential ORDER BY provider, alias
                """);
    }

    @RootAuth
    @PostMapping(consumes = "application/json")
    Map<String, String> save(@Valid @RequestBody Credential request) throws Exception {
        String secretToStore = request.secret();
        if (AiPlatformSettings.PROVIDER.equals(request.provider())) {
            try {
                if (!AiPlatformSettings.ALIAS.equals(request.alias())) {
                    throw new IllegalArgumentException("AI 平台配置的别名须为 openai-ai");
                }
                var node = json.readTree(secretToStore);
                if (node != null && node.isObject()
                        && node.path("apiKey").asText("").isBlank()
                        && node.path("accessToken").asText("").isBlank()) {
                    var existing = aiSettings();
                    if (existing != null) {
                        var merged = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                        merged.put("apiKey", existing.apiKey());
                        secretToStore = json.writeValueAsString(merged);
                    }
                }
                AiPlatformSettings.parse(json, secretToStore);
            } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException failure) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, failure.getMessage());
            }
        }
        jdbc.update("""
                MERGE INTO channel_credential (provider, alias, encrypted, updated_at)
                KEY (provider, alias) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, request.provider(), request.alias(), encrypt(request.provider(), request.alias(), secretToStore));
        return Map.of("provider", request.provider(), "alias", request.alias());
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public com.afs.utils.ResponseUtils.Result invalidCredential() {
        return com.afs.utils.ResponseUtils.error(400, "请检查凭据类型、别名和输入长度", null, 400);
    }

    @RootAuth
    @GetMapping("/ai")
    Map<String, Object> aiConfiguration() throws Exception {
        var settings = aiSettings();
        if (settings == null) return Map.of("configured", false);
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("configured", true);
        result.put("baseUrl", settings.baseUrl().toString());
        result.put("chatModel", settings.chatModel());
        result.put("imageModel", settings.imageModel());
        result.put("fallbackChatModel", settings.fallbackChatModel());
        if (settings.temperature() != null) {
            result.put("temperature", settings.temperature());
        }
        return result;
    }

    AiPlatformSettings aiSettings() throws Exception {
        return AiPlatformSettings.parse(json, readSecret(AiPlatformSettings.PROVIDER, AiPlatformSettings.ALIAS));
    }

    void renameFixtureAlias(String provider, String oldAlias, String newAlias) throws Exception {
        String secret = readSecret(provider, oldAlias);
        if (secret == null || !secret.startsWith("DEMO-ONLY-")) return;
        jdbc.update("UPDATE channel_credential SET alias = ?, encrypted = ? WHERE provider = ? AND alias = ?",
                newAlias, encrypt(provider, newAlias, secret), provider, oldAlias);
    }

    private String readSecret(String provider, String alias) throws Exception {
        var stored = jdbc.queryForList("SELECT CAST(encrypted AS VARCHAR) FROM channel_credential WHERE provider = ? AND alias = ?",
                String.class, provider, alias);
        if (stored.isEmpty()) return null;
        String[] parts = stored.get(0).split(":", 2);
        var decoder = Base64.getDecoder();
        Cipher cipher = cipher(Cipher.DECRYPT_MODE, provider, alias, decoder.decode(parts[0]));
        return new String(cipher.doFinal(decoder.decode(parts[1])), java.nio.charset.StandardCharsets.UTF_8);
    }

    public PlatformCredential forPlatform(String provider, String alias) throws Exception {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("请选择已保存的渠道凭据");
        String saved = readSecret(provider, alias);
        if (saved == null) throw new IllegalArgumentException("找不到该平台的已保存凭据");
        String token = saved.trim();
        String developerToken = "";
        String managerId = "";
        if (token.startsWith("{")) {
            var fields = json.readTree(token);
            token = fields.path("accessToken").asText(fields.path("access_token").asText(fields.path("token").asText("")));
            developerToken = fields.path("developerToken").asText(fields.path("developer_token").asText(""));
            managerId = fields.path("loginCustomerId").asText(fields.path("login_customer_id").asText(""));
        }
        if (token.isBlank()) throw new IllegalArgumentException("该凭据缺少 access token");
        return new PlatformCredential(token, developerToken, managerId);
    }

    public record PlatformCredential(String accessToken, String developerToken, String loginCustomerId) {
        public String redact(String text) {
            String sanitized = text.replace(accessToken, "[凭据已隐藏]");
            return developerToken.isBlank() ? sanitized : sanitized.replace(developerToken, "[凭据已隐藏]");
        }
    }

    @RootAuth
    @PostMapping("/check")
    Map<String, Object> check(@RequestBody Probe probe) throws Exception {
        if (AiPlatformSettings.PROVIDER.equals(probe.provider())) return checkAi(probe.alias());
        String url = switch (probe.provider()) {
            case "TIKTOK" -> "https://business-api.tiktok.com/open_api/v1.3/advertiser/info/";
            case "GOOGLE" -> "https://googleads.googleapis.com/v26/customers:listAccessibleCustomers";
            case "META" -> "https://graph.facebook.com/v27.0/me/adaccounts?fields=id&limit=1";
            case "TABOOLA" -> "https://backstage.taboola.com/backstage/api/1.0/users/current/account";
            default -> throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "未知广告平台");
        };
        PlatformCredential credential;
        try {
            credential = forPlatform(probe.provider(), probe.alias());
        } catch (IllegalArgumentException failure) {
            return Map.of("httpStatus", 0, "body", failure.getMessage());
        }
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json");
        if ("TIKTOK".equals(probe.provider())) request.header("Access-Token", credential.accessToken());
        else request.header("Authorization", "Bearer " + credential.accessToken());
        if ("GOOGLE".equals(probe.provider()) && !credential.developerToken().isBlank()) {
            request.header("developer-token", credential.developerToken());
        }
        try {
            var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
            return Map.of("httpStatus", response.statusCode(), "body", credential.redact(response.body()));
        } catch (java.io.IOException failure) {
            return Map.of("httpStatus", 0, "body", failure.toString());
        }
    }

    private Map<String, Object> checkAi(String alias) throws Exception {
        if (!AiPlatformSettings.ALIAS.equals(alias)) return Map.of("httpStatus", 0, "body", "找不到该 AI 平台配置");
        var settings = aiSettings();
        if (settings == null) return Map.of("httpStatus", 0, "body", "请先保存 AI 平台配置");
        var request = HttpRequest.newBuilder(settings.endpoint("models")).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json").header("Authorization", "Bearer " + settings.apiKey()).GET().build();
        String body;
        int status;
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            body = settings.redact(response.body());
            if (status >= 200 && status < 300) {
                var data = json.readTree(response.body());
                if (data != null && data.path("data").isArray()) {
                    var available = new java.util.HashSet<String>();
                    data.path("data").forEach(model -> available.add(model.path("id").asText()));
                    var missing = java.util.stream.Stream.of(settings.chatModel(), settings.fallbackChatModel(), settings.imageModel())
                            .filter(model -> !model.isBlank() && !available.contains(model)).distinct().toList();
                    body = missing.isEmpty() ? "平台连接成功，已配置模型均在模型列表中。"
                            : "平台连接成功，模型列表未包含：" + settings.redact(String.join("、", missing));
                } else {
                    body = "平台已响应，但未返回可识别的模型列表。";
                }
            }
            if (body.isBlank()) body = "平台响应为空";
            body = body.substring(0, Math.min(body.length(), 2000));
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            return Map.of("httpStatus", 0, "body", "平台未返回可识别的模型列表");
        } catch (java.io.IOException failure) {
            return Map.of("httpStatus", 0, "body", "无法连接 AI 平台：" + failure.getClass().getSimpleName());
        }
        if (status < 200 || status >= 300) return Map.of("httpStatus", status, "body", body);
        if (settings.chatModel().isBlank()) {
            return Map.of("httpStatus", status, "body", body + "\n未配置聊天模型，跳过对话接口检测。");
        }
        return chatPing(settings, body);
    }

    /**
     * 模型列表能通不代表分析能跑。这里真的发一次 chat/completions，
     * 把状态码和耗时一起返回，避免出现「验证连接成功但分析全是 RULES」。
     */
    private Map<String, Object> chatPing(AiPlatformSettings settings, String modelsBody) throws Exception {
        // 兼容思考/推理型模型（如 DeepSeek 推理版本会先输出 reasoning 再输出 content），预留足够 token 避免误报不可用。
        var payload = Map.of("model", settings.chatModel(), "stream", false, "max_tokens", 256,
                "messages", List.of(Map.of("role", "user", "content", "只回复两个字符：OK")));
        var request = HttpRequest.newBuilder(settings.endpoint("chat/completions")).timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json").header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
        long started = System.nanoTime();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            String reply = "";
            String reasoning = "";
            try {
                var msg = json.readTree(response.body()).at("/choices/0/message");
                reply = msg.path("content").asText("").strip();
                reasoning = msg.path("reasoning").asText(msg.path("reasoning_content").asText("")).strip();
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // 非 JSON 响应按失败处理，下面统一给出原始内容。
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300 && (!reply.isBlank() || !reasoning.isBlank())) {
                String preview = !reply.isBlank() ? reply : "（推理模型已正常响应）" + reasoning;
                return Map.of("httpStatus", response.statusCode(), "body", modelsBody
                        + "\n对话接口可用：HTTP " + response.statusCode() + "，耗时 " + elapsed + " ms，回复："
                        + settings.redact(abbreviate(preview)));
            }
            String detail = settings.redact(response.body());
            return Map.of("httpStatus", response.statusCode(), "body", modelsBody
                    + "\n对话接口不可用：HTTP " + response.statusCode() + "，耗时 " + elapsed + " ms，响应："
                    + (detail.isBlank() ? "空响应" : abbreviate(detail)));
        } catch (java.io.IOException failure) {
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            return Map.of("httpStatus", 0, "body", modelsBody + "\n对话接口不可用：等待 " + elapsed + " ms 后失败（"
                    + failure.getClass().getSimpleName() + "）");
        }
    }

    private static String abbreviate(String value) {
        return value.length() <= 600 ? value : value.substring(0, 597) + "...";
    }

    record Probe(String provider, String alias) {}

    private String encrypt(String provider, String alias, String secret) throws Exception {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, provider, alias, nonce)
                .doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var encoder = Base64.getEncoder();
        return encoder.encodeToString(nonce) + ":" + encoder.encodeToString(encrypted);
    }

    private Cipher cipher(int mode, String provider, String alias, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(loadKey(), "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD((provider + ":" + alias).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return cipher;
    }

    private synchronized byte[] loadKey() throws java.io.IOException {
        if (Files.notExists(keyFile)) {
            Files.createDirectories(keyFile.toAbsolutePath().getParent());
            byte[] generated = new byte[32];
            random.nextBytes(generated);
            try {
                var options = java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                var permissions = keyFile.getFileSystem().supportedFileAttributeViews().contains("posix")
                        ? new java.nio.file.attribute.FileAttribute<?>[]{
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                        : new java.nio.file.attribute.FileAttribute<?>[0];
                try (var output = Files.newByteChannel(keyFile, options, permissions)) {
                    ByteBuffer buffer = ByteBuffer.wrap(generated);
                    while (buffer.hasRemaining()) output.write(buffer);
                }
            } catch (FileAlreadyExistsException ignored) {
                // Another local instance initialized the key.
            }
        }
        byte[] key = Files.readAllBytes(keyFile);
        if (key.length != 32) throw new IllegalStateException("Invalid local channel vault key");
        return key;
    }

    record Credential(@NotBlank @Pattern(regexp = "TIKTOK|GOOGLE|META|TABOOLA|OPENAI") String provider,
                      @NotBlank @Size(max = 80) String alias,
                      @NotBlank @Size(max = 24000) String secret) {}
}

