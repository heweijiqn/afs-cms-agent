package com.afs.publicapi.service;

import com.afs.publicapi.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class FeishuReportService {

    public static String mApiErrorAlertUrl = System.getenv().getOrDefault("AFS_FEISHU_API_ERROR_WEBHOOK", "");

    @Autowired
    private RestTemplate restTemplate;

    public void reportUserAlert(String content, String url) {
        if (url == null || url.trim().isEmpty()) return;
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("msg_type", "text");
        Map<String, Object> contentJsonMap = new HashMap<>();
        contentJsonMap.put("text", content);
        jsonMap.put("content", contentJsonMap);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Accept", "*/*");
        log.debug("reportUserAlert Json={}", jsonMap);
        HttpEntity<String> requestEntity = new HttpEntity<String>(JsonUtils.toJsonString(jsonMap), headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        log.debug("responseEntity={}", responseEntity);
    }
}
