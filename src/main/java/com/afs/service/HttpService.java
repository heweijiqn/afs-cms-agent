package com.afs.service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class HttpService {
    private final RestTemplate http;
    private final String openAiKey;

    public HttpService(RestTemplate http, @Value("${cms.connectors.openai-api-key:}") String openAiKey) {
        this.http = http;
        this.openAiKey = openAiKey;
    }

    public String get(String url) {
        return get(url, Map.of());
    }

    public String get(String url, Map<String, String> headers) {
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(headers)), String.class).getBody();
    }

    public String get(URI url, Map<String, String> headers) {
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(headers)), String.class).getBody();
    }

    public String getDzenRequest(String url) {
        return get(url, Map.of(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE));
    }

    public String post(Map<String, String> headers, String body, String url) {
        HttpHeaders requestHeaders = headers(headers);
        if (requestHeaders.getContentType() == null) {
            requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.postForObject(url, new HttpEntity<>(body, requestHeaders), String.class);
    }

    public String postGpt(String body, String url) {
        if (openAiKey.isBlank()) {
            throw new IllegalStateException("CMS OpenAI connector credentials are not configured");
        }
        return post(Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + openAiKey), body, url);
    }

    public String postGoogleImg(String body, String url, String token) {
        return post(Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + token), body, url);
    }

    public String getTaboolaToken(String url, String clientId, String clientSecret) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return http.postForObject(url, new HttpEntity<>(form, headers), String.class);
    }

    public String postFileForTikTok(Map<String, String> headers, MultiValueMap<String, Object> parts, String url) {
        HttpHeaders requestHeaders = headers(headers);
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForObject(url, new HttpEntity<>(parts, requestHeaders), String.class);
    }

    public void postImg(FileSystemResource file, String path, String url) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", file);
        parts.add("path", path);
        postFileForTikTok(Map.of(), parts, url);
    }

    public void downloadFile(String url, String destination) {
        Path target = Path.of(destination).toAbsolutePath();
        http.execute(url, HttpMethod.GET, null, response -> {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".download-", ".tmp");
            try {
                Files.copy(response.getBody(), temporary, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return null;
        });
    }

    private HttpHeaders headers(Map<String, String> input) {
        HttpHeaders headers = new HttpHeaders();
        input.forEach(headers::set);
        return headers;
    }
}

