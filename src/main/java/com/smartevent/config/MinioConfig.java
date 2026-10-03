package com.smartevent.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Configuration
public class MinioConfig {

    @Value("${app.storage.minio.endpoint}")
    private String endpoint;

    @Value("${app.storage.minio.public-endpoint:}")
    private String publicEndpoint;

    @Value("${app.storage.minio.access-key}")
    private String accessKey;

    @Value("${app.storage.minio.secret-key}")
    private String secretKey;

    @Value("${app.storage.minio.bucket}")
    private String defaultBucket;

    @Bean
    @Primary
    public MinioClient minioClient() {
        // 1. Tạo MinioClient kết nối tới MinIO Server
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // 2. Kiểm tra bucket đã tồn tại chưa, nếu chưa thì tạo mới
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(defaultBucket).build()
            );
            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(defaultBucket).build()
                );
                log.info("Đã tạo MinIO bucket: {}", defaultBucket);
            }
        } catch (Exception e) {
            log.warn("Không thể kiểm tra/tạo MinIO bucket: {}", e.getMessage());
        }

        return client;
    }

    @Bean("presignedMinioClient")
    public MinioClient presignedMinioClient(@Qualifier("minioClient") MinioClient internalClient) {
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            return internalClient;
        }
        // Signature V4 covers the request host. Generate browser URLs using the public host.
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
