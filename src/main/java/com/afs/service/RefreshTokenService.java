package com.afs.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

@Slf4j
@Service
public class RefreshTokenService {

    @Value("${env}")
    String env;

    GoogleCredentials credentials;

    static String token;

    @PostConstruct
    public void init() {
        if (!env.equals("prod")) {
            return;
        }
        String credentialPath = System.getenv().getOrDefault("AFS_VERTEX_SERVICE_ACCOUNT_FILE", "");
        if (credentialPath.isBlank()) return;
        try (InputStream serviceAccountStream = java.nio.file.Files.newInputStream(java.nio.file.Path.of(credentialPath))) {
            credentials = ServiceAccountCredentials.fromStream(serviceAccountStream).createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
        } catch (IOException e) {
            log.error("initCount error.", e);
        }
    }

    @Async
    @Scheduled(fixedDelay = 33 * 60 * 1000)
    public void refreshToken() {
        if (credentials == null) return;
        if (!env.equals("prod")) {
            return;
        }
        try {
            credentials.refresh();
            AccessToken accessToken = credentials.getAccessToken();
            token = accessToken.getTokenValue();
        } catch (IOException e) {
            log.error("initCount error.", e);
        }
    }

    public static String getToken() {
        return token;
    }
}
