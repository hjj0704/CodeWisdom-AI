package com.codewisdom.resource.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioConfig.MinioProperties.class)
public class MinioConfig {

    @Bean
    @ConditionalOnProperty(prefix = "codewisdom.minio", name = "enabled", havingValue = "true")
    MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @ConfigurationProperties(prefix = "codewisdom.minio")
    public record MinioProperties(
            boolean enabled,
            String endpoint,
            String accessKey,
            String secretKey
    ) {
    }
}
