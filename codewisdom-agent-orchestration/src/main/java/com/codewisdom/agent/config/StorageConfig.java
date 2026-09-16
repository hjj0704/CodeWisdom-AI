package com.codewisdom.agent.config;

import com.codewisdom.agent.storage.ArtifactStorage;
import com.codewisdom.agent.storage.InMemoryArtifactStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    /** 无 MinIO 时（test 档 / 未启用 minio）回退到内存存储，保证上下文可启动。 */
    @Bean
    @ConditionalOnMissingBean(ArtifactStorage.class)
    ArtifactStorage inMemoryArtifactStorage() {
        return new InMemoryArtifactStorage();
    }
}
