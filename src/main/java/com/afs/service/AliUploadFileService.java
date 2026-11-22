package com.afs.service;

import com.afs.agent.AliyunOssSettings;
import com.afs.agent.ChannelCredentialController;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class AliUploadFileService {
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucket;
    private final String publicBaseUrl;

    @Autowired(required = false)
    private ChannelCredentialController channelCredentials;

    public AliUploadFileService(@Value("${cms.connectors.oss.endpoint:}") String endpoint,
                                @Value("${cms.connectors.oss.access-key-id:}") String accessKeyId,
                                @Value("${cms.connectors.oss.access-key-secret:}") String accessKeySecret,
                                @Value("${cms.connectors.oss.bucket:}") String bucket,
                                @Value("${cms.connectors.oss.public-base-url:}") String publicBaseUrl) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean isConfigured() {
        return resolveSettings() != null;
    }

    private AliyunOssSettings resolveSettings() {
        if (channelCredentials != null) {
            try {
                AliyunOssSettings saved = channelCredentials.aliyunOssSettings();
                if (saved != null) {
                    return saved;
                }
            } catch (Exception e) {
                log.warn("Failed to load encrypted Aliyun OSS settings from channel vault", e);
            }
        }
        if (endpoint.isBlank() || accessKeyId.isBlank() || accessKeySecret.isBlank()
                || bucket.isBlank() || publicBaseUrl.isBlank()) {
            return null;
        }
        return new AliyunOssSettings(endpoint, accessKeyId, accessKeySecret, bucket, publicBaseUrl);
    }

    public Map<String, String> uploadFile(MultipartFile file, String key) throws IOException {
        AliyunOssSettings settings = resolveSettings();
        if (settings == null) {
            throw new IllegalStateException("CMS OSS connector is not configured");
        }

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        OSS client = new OSSClientBuilder().build(settings.endpoint(), settings.accessKeyId(), settings.accessKeySecret());
        try (var input = file.getInputStream()) {
            client.putObject(settings.bucket(), key, input, metadata);
        } finally {
            client.shutdown();
        }
        return Map.of("url", settings.publicBaseUrl().replaceFirst("/+$", "") + "/" + key);
    }
}

