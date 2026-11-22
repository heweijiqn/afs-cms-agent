package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Locale;

public record AliyunOssSettings(
        String endpoint,
        String accessKeyId,
        String accessKeySecret,
        String bucket,
        String publicBaseUrl) {

    public static final String PROVIDER = "ALIYUN_OSS";
    public static final String ALIAS = "aliyun-oss";

    public static AliyunOssSettings parse(ObjectMapper json, String secret) throws Exception {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        var node = json.readTree(secret);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("阿里云 OSS 配置格式不正确");
        }
        String rawEndpoint = node.path("endpoint").asText("").strip();
        String accessKeyId = node.path("accessKeyId").asText("").strip();
        String accessKeySecret = node.path("accessKeySecret").asText("").strip();
        String bucket = node.path("bucket").asText("").strip();
        String rawPublicBaseUrl = node.path("publicBaseUrl").asText("").strip();

        if (rawEndpoint.isBlank()) {
            throw new IllegalArgumentException("请填写 OSS Endpoint");
        }
        String normalizedEndpoint = normalizeUrl(rawEndpoint, "OSS Endpoint");
        if (accessKeyId.isBlank() || hasInvalidSecretChars(accessKeyId)) {
            throw new IllegalArgumentException("请填写有效的 AccessKey ID");
        }
        if (accessKeySecret.isBlank() || hasInvalidSecretChars(accessKeySecret)) {
            throw new IllegalArgumentException("请填写有效的 AccessKey Secret");
        }
        if (bucket.isBlank() || bucket.chars().anyMatch(ch -> ch <= 32 || ch > 126 || ch == '/' || ch == '\\')) {
            throw new IllegalArgumentException("请填写有效的 Bucket 名称");
        }

        String publicBaseUrl;
        if (rawPublicBaseUrl.isBlank()) {
            URI endpointUri = URI.create(normalizedEndpoint);
            String scheme = endpointUri.getScheme() == null ? "https" : endpointUri.getScheme();
            publicBaseUrl = scheme + "://" + bucket + "." + endpointUri.getHost();
        } else {
            publicBaseUrl = normalizeUrl(rawPublicBaseUrl, "公网访问域名");
        }

        return new AliyunOssSettings(normalizedEndpoint, accessKeyId, accessKeySecret, bucket, publicBaseUrl);
    }

    private static String normalizeUrl(String raw, String fieldName) {
        String candidate = raw.contains("://") ? raw : "https://" + raw;
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " 格式无效");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!"http".equals(scheme) && !"https".equals(scheme)) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(fieldName + " 须为有效的 HTTP/HTTPS 地址");
        }
        return candidate.replaceFirst("/+$", "");
    }

    private static boolean hasInvalidSecretChars(String value) {
        return value.chars().anyMatch(ch -> ch < 33 || ch > 126);
    }

    public String maskedAccessKeyId() {
        if (accessKeyId.length() <= 6) {
            return "****";
        }
        return accessKeyId.substring(0, 4) + "****" + accessKeyId.substring(accessKeyId.length() - 4);
    }

    public String redact(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = text.replace(accessKeySecret, "[凭据已隐藏]");
        return sanitized.replace(accessKeyId, "[凭据已隐藏]");
    }
}
