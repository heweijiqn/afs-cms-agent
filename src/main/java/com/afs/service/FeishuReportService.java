package com.afs.service;

import com.afs.utils.JsonUtils;
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

    public static String mAuditAlertUrl = System.getenv().getOrDefault("AFS_FEISHU_AUDIT_WEBHOOK", "");
    public static String mErrorAlertUrl = System.getenv().getOrDefault("AFS_FEISHU_ERROR_WEBHOOK", "");
    public static String mDailyOpReportUrl = System.getenv().getOrDefault("AFS_FEISHU_DAILY_WEBHOOK", "");

    @Autowired
    private RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Value("${env:}")
    private String environment;

    public void reportUserAlert(String content, String url) {
        if ("local".equals(environment) || url == null || url.trim().isEmpty()) return;
        Map<String, Object> map = new HashMap<>();
        map.put("msg_type", "text");
        Map<String, Object> ctMap = new HashMap<>();
        ctMap.put("text", content);
        map.put("content", ctMap);

        String json = JsonUtils.toJsonString(map);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Accept", "*/*");
        HttpEntity<String> requestEntity = new HttpEntity<String>(json, headers);
        new Thread(() -> {
            ResponseEntity < String > responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            log.info("responseEntity={}", responseEntity);
        }).start();
    }

    public String reportUserAlertWaitResponse(String content, String url) {
        if ("local".equals(environment) || url == null || url.trim().isEmpty()) return "";
        Map<String, Object> map = new HashMap<>();
        map.put("msg_type", "text");
        Map<String, Object> ctMap = new HashMap<>();
        ctMap.put("text", content);
        map.put("content", ctMap);

        String json = JsonUtils.toJsonString(map);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Accept", "*/*");
        HttpEntity<String> requestEntity = new HttpEntity<String>(json, headers);
        ResponseEntity <String> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        log.info("responseEntity={}", responseEntity);
        return responseEntity.getBody();
    }
}
