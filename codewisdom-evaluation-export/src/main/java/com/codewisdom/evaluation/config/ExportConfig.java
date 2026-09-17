package com.codewisdom.evaluation.config;

import com.codewisdom.evaluation.service.ProjectExportService;
import com.codewisdom.evaluation.storage.ExportStorage;
import com.codewisdom.evaluation.storage.ExportStorage.InMemoryStore;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExportConfig.MinioProperties.class)
public class ExportConfig {

    @Bean
    @ConditionalOnProperty(prefix = "codewisdom.minio", name = "enabled", havingValue = "true")
    MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ExportStorage.Store.class)
    ExportStorage.Store inMemoryExportStore() {
        return new InMemoryStore();
    }

    @Bean
    ProjectExportService projectExportService(ExportStorage.Store store) {
        return new ProjectExportService(store);
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
