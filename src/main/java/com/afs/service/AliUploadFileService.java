package com.afs.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AliUploadFileService {
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucket;
    private final String publicBaseUrl;

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

    public Map<String, String> uploadFile(MultipartFile file, String key) throws IOException {
        if (endpoint.isBlank() || accessKeyId.isBlank() || accessKeySecret.isBlank()
                || bucket.isBlank() || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("CMS OSS connector is not configured");
        }

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (var input = file.getInputStream()) {
            client.putObject(bucket, key, input, metadata);
        } finally {
            client.shutdown();
        }
        return Map.of("url", publicBaseUrl.replaceFirst("/+$", "") + "/" + key);
    }
}

