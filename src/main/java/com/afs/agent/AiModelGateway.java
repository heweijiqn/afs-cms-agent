package com.afs.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Commercial LLM gateway built on LangChain4j {@link OpenAiChatModel}.
 * Handles dynamic vault resolution without restart, proxy-aware JDK HttpClient,
 * transient retry, fallback model routing, token accounting, and credential redaction.
 */
@Component
class AiModelGateway {
    static final String MODEL_SOURCE = "LANGCHAIN4J_OPENAI_COMPATIBLE";
    static final String LOCAL_SOURCE = "LANGCHAIN4J_LOCAL";
    static final String LANGCHAIN4J_VERSION = "1.0.1";
    static final String LANGGRAPH4J_VERSION = "1.5.14";

    private static final Logger log = LoggerFactory.getLogger(AiModelGateway.class);

    private final ChannelCredentialController vault;
    private final boolean localModel;
    private final String defaultBaseUrl;
    private final String defaultApiKey;
    private final String defaultModelName;
    private final String defaultFallbackModel;
    private final long timeoutSeconds;

    AiModelGateway(@Value("${cms.agent.local-model-port:0}") int port,
                   @Value("${cms.agent.ai.model-name:${cms.agent.local-model-name:/models/DeepSeek-V4.1-Flash-1017}}")
                   String modelName,
                   @Value("${cms.agent.ai.fallback-model-name:}") String fallbackModelName,
                   @Value("${cms.agent.ai.base-url:}") String configuredBaseUrl,
                   @Value("${cms.agent.ai.api-key:}") String configuredApiKey,
                   @Value("${cms.agent.ai.timeout-seconds:90}") long timeoutSeconds,
                   ChannelCredentialController vault) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid loopback model port");
        }
        if (timeoutSeconds < 5 || timeoutSeconds > 600) {
            throw new IllegalArgumentException("cms.agent.ai.timeout-seconds must be between 5 and 600");
        }
        this.vault = vault;
        this.timeoutSeconds = timeoutSeconds;
        this.localModel = port > 0;
        this.defaultModelName = modelName == null ? "" : modelName.strip();
        this.defaultFallbackModel = fallbackModelName == null ? "" : fallbackModelName.strip();
        this.defaultBaseUrl = localModel
                ? "http://127.0.0.1:" + port + "/v1"
                : (configuredBaseUrl == null ? "" : configuredBaseUrl.strip());
        this.defaultApiKey = localModel
                ? "local-simulation"
                : (configuredApiKey == null ? "" : configuredApiKey.strip());
    }

    boolean isConfigured() {
        try {
            return resolve() != null;
        } catch (Exception ignored) {
            return !defaultBaseUrl.isBlank() && !defaultApiKey.isBlank() && !defaultModelName.isBlank();
        }
    }

    ResolvedModel resolve() throws Exception {
        if (localModel) {
            if (defaultModelName.isBlank()) return null;
            return new ResolvedModel(
                    AiPlatformSettings.normalizeBase(defaultBaseUrl).toString(),
                    defaultApiKey,
                    defaultModelName,
                    defaultFallbackModel,
                    0.2,
                    LOCAL_SOURCE,
                    null);
        }
        AiPlatformSettings settings = vault.aiSettings();
        if (settings != null && !settings.chatModel().isBlank()) {
            return new ResolvedModel(
                    settings.baseUrl().toString(),
                    settings.apiKey(),
                    settings.chatModel(),
                    settings.fallbackChatModel(),
                    settings.temperature(),
                    MODEL_SOURCE,
                    settings);
        }
        if (!defaultBaseUrl.isBlank() && !defaultApiKey.isBlank() && !defaultModelName.isBlank()) {
            return new ResolvedModel(
                    AiPlatformSettings.normalizeBase(defaultBaseUrl).toString(),
                    defaultApiKey,
                    defaultModelName,
                    defaultFallbackModel,
                    0.2,
                    MODEL_SOURCE,
                    null);
        }
        return null;
    }

    Map<String, Object> status() {
        var result = new LinkedHashMap<String, Object>();
        result.put("engine", "LangGraph4j + LangChain4j");
        result.put("langGraphVersion", LANGGRAPH4J_VERSION);
        result.put("langChainVersion", LANGCHAIN4J_VERSION);
        result.put("timeoutSeconds", timeoutSeconds);
        try {
            ResolvedModel resolved = resolve();
            if (resolved == null) {
                result.put("configured", false);
                result.put("source", "RULES_ONLY");
                result.put("chatModel", "");
                result.put("fallbackChatModel", "");
                result.put("baseUrlHost", "");
                return result;
            }
            result.put("configured", true);
            result.put("source", resolved.source());
            result.put("chatModel", resolved.chatModel());
            result.put("fallbackChatModel", resolved.fallbackChatModel());
            URI uri = URI.create(resolved.baseUrl());
            result.put("baseUrlHost", uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
            if (resolved.temperature() != null) {
                result.put("temperature", resolved.temperature());
            }
            return result;
        } catch (Exception failure) {
            result.put("configured", false);
            result.put("source", "CONFIG_ERROR");
            result.put("error", describe(failure));
            return result;
        }
    }

    /**
     * Executes a LangChain4j chat completion with optional tool specifications, transient retry,
     * and automatic fallback model failover when configured.
     */
    ChatCallResult chat(ResolvedModel resolved, List<ChatMessage> messages,
                        List<ToolSpecification> toolSpecifications) {
        long started = System.nanoTime();
        RuntimeException primaryError;
        try {
            ChatResponse response = invokeWithRetry(resolved, resolved.chatModel(), messages, toolSpecifications);
            return toResult(resolved, resolved.chatModel(), response, false, millis(started));
        } catch (RuntimeException failure) {
            primaryError = failure;
        }

        if (resolved.fallbackChatModel() != null && !resolved.fallbackChatModel().isBlank()
                && !resolved.fallbackChatModel().equals(resolved.chatModel())) {
            log.warn("主模型 {} 调用失败（{}），切换至备选模型 {}",
                    resolved.chatModel(), redact(resolved, describe(primaryError)), resolved.fallbackChatModel());
            try {
                ChatResponse fallbackResponse = invokeWithRetry(
                        resolved, resolved.fallbackChatModel(), messages, toolSpecifications);
                return toResult(resolved, resolved.fallbackChatModel(), fallbackResponse, true, millis(started));
            } catch (RuntimeException fallbackFailure) {
                throw new IllegalStateException("主模型失败：" + redact(resolved, describe(primaryError))
                        + "；备选模型失败：" + redact(resolved, describe(fallbackFailure)), fallbackFailure);
            }
        }
        throw primaryError;
    }

    private ChatResponse invokeWithRetry(ResolvedModel resolved, String modelName,
                                         List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications) {
        OpenAiChatModel model = buildChatModel(resolved.baseUrl(), resolved.apiKey(), modelName,
                resolved.temperature(), timeoutSeconds);
        ChatRequest.Builder reqBuilder = ChatRequest.builder().messages(messages);
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            reqBuilder.toolSpecifications(toolSpecifications);
        }
        ChatRequest request = reqBuilder.build();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                ChatResponse response = model.chat(request);
                if (response == null || response.aiMessage() == null) {
                    throw new IllegalStateException("模型未返回有效消息");
                }
                return response;
            } catch (RuntimeException failure) {
                last = failure;
                if (attempt == 1 && isTransient(failure)) {
                    log.warn("模型 {} 遇到瞬态错误，准备重试：{}", modelName, redact(resolved, describe(failure)));
                    try {
                        Thread.sleep(600L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw failure;
                    }
                    continue;
                }
                throw failure;
            }
        }
        throw last != null ? last : new IllegalStateException("模型调用失败");
    }

    private static OpenAiChatModel buildChatModel(String baseUrl, String apiKey, String modelName,
                                                  Double temperature, long timeoutSeconds) {
        var httpBuilder = new JdkHttpClientBuilder()
                .httpClientBuilder(ChannelCredentialController.createHttpClientBuilder())
                .connectTimeout(Duration.ofSeconds(Math.min(15L, timeoutSeconds)))
                .readTimeout(Duration.ofSeconds(timeoutSeconds));
        var builder = OpenAiChatModel.builder()
                .httpClientBuilder(httpBuilder)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(0);
        if (temperature != null) {
            builder.temperature(temperature);
        }
        return builder.build();
    }

    private static ChatCallResult toResult(ResolvedModel resolved, String usedModel, ChatResponse response,
                                           boolean fallbackUsed, long durationMs) {
        AiMessage aiMessage = response.aiMessage();
        TokenUsage usage = response.tokenUsage();
        int input = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
        int output = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
        int total = usage != null && usage.totalTokenCount() != null ? usage.totalTokenCount() : (input + output);
        return new ChatCallResult(aiMessage, usedModel, resolved.source(), input, output, total,
                durationMs, fallbackUsed);
    }

    private static boolean isTransient(Throwable failure) {
        String text = describe(failure).toLowerCase(java.util.Locale.ROOT);
        return text.contains("429") || text.contains("502") || text.contains("503")
                || text.contains("504") || text.contains("rate limit")
                || text.contains("connection reset") || text.contains("temporarily");
    }

    static String redact(ResolvedModel resolved, String text) {
        if (text == null) return null;
        if (resolved == null) return text;
        if (resolved.settings() != null) return resolved.settings().redact(text);
        return resolved.apiKey() == null || resolved.apiKey().isBlank()
                ? text : text.replace(resolved.apiKey(), "[凭据已隐藏]");
    }

    static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root
                && (root.getMessage() == null || root.getMessage().isBlank())) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String text = root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() <= 260 ? text : text.substring(0, 257) + "...";
    }

    private static long millis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    record ResolvedModel(String baseUrl, String apiKey, String chatModel, String fallbackChatModel,
                         Double temperature, String source, AiPlatformSettings settings) {}

    record ChatCallResult(AiMessage message, String modelName, String source,
                          int inputTokens, int outputTokens, int totalTokens,
                          long durationMs, boolean fallbackUsed) {}
}
