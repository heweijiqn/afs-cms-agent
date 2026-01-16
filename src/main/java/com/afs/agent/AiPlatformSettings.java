package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;

record AiPlatformSettings(URI baseUrl, String apiKey, String chatModel, String imageModel,
                          String fallbackChatModel, Double temperature) {
    static final String PROVIDER = "OPENAI";
    static final String ALIAS = "openai-ai";

    AiPlatformSettings(URI baseUrl, String apiKey, String chatModel, String imageModel) {
        this(baseUrl, apiKey, chatModel, imageModel, "", null);
    }

    static AiPlatformSettings parse(ObjectMapper json, String saved) {
        if (saved == null) return null;
        try {
            var fields = json.readTree(saved);
            if (fields == null || !fields.isObject()) throw new IllegalArgumentException("AI 平台配置须为 JSON 对象");
            Double temp = null;
            var tempNode = fields.path("temperature");
            if (tempNode.isNumber()) {
                temp = tempNode.asDouble();
            } else if (tempNode.isTextual() && !tempNode.asText("").isBlank()) {
                try {
                    temp = Double.parseDouble(tempNode.asText().strip());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Temperature 须为 0 到 2 之间的数字");
                }
            }
            if (temp != null && (temp < 0.0 || temp > 2.0)) {
                throw new IllegalArgumentException("Temperature 须在 0.0 至 2.0 之间");
            }
            return new AiPlatformSettings(
                    normalizeBase(fields.path("baseUrl").asText("").strip()),
                    fields.path("accessToken").asText(fields.path("apiKey").asText("")).strip(),
                    fields.path("chatModel").asText("").strip(),
                    fields.path("imageModel").asText("").strip(),
                    fields.path("fallbackChatModel").asText("").strip(),
                    temp);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("AI 平台配置 JSON 格式无效");
        }
    }

    AiPlatformSettings {
        if (apiKey == null || apiKey.isBlank() || apiKey.chars().anyMatch(ch -> ch < 33 || ch > 126)) {
            throw new IllegalArgumentException("请输入有效的 AI API Key");
        }
        chatModel = chatModel == null ? "" : chatModel.strip();
        imageModel = imageModel == null ? "" : imageModel.strip();
        fallbackChatModel = fallbackChatModel == null ? "" : fallbackChatModel.strip();
        if (chatModel.isBlank() && imageModel.isBlank()) {
            throw new IllegalArgumentException("请至少填写一个 AI 模型");
        }
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("Temperature 须在 0.0 至 2.0 之间");
        }
    }

    static URI normalizeBase(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("AI API Base URL 格式无效");
        }
        String host = uri.getHost();
        boolean loopback = "127.0.0.1".equals(host) || "[::1]".equals(host) || "localhost".equalsIgnoreCase(host);
        boolean privateNetwork = isPrivateIpv4(host);
        if (host == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme())
                || ("http".equalsIgnoreCase(uri.getScheme()) && (loopback || privateNetwork)))) {
            throw new IllegalArgumentException("AI API Base URL 须使用 HTTPS；本机或内网服务可使用 HTTP");
        }
        String base = uri.toASCIIString().replaceFirst("/+$", "");
        return URI.create(uri.getRawPath().replaceFirst("/+$", "").isEmpty() ? base + "/v1" : base);
    }

    static boolean isPrivateIpv4(String host) {
        if (host == null || !host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) return false;
        String[] octets = host.split("\\.");
        int a = Integer.parseInt(octets[0]);
        int b = Integer.parseInt(octets[1]);
        int c = Integer.parseInt(octets[2]);
        int d = Integer.parseInt(octets[3]);
        if (a > 255 || b > 255 || c > 255 || d > 255) return false;
        return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168);
    }

    URI endpoint(String path) {
        return URI.create(baseUrl + "/" + path);
    }

    String redact(String message) {
        return message == null ? null : message.replace(apiKey, "[凭据已隐藏]");
    }
}
