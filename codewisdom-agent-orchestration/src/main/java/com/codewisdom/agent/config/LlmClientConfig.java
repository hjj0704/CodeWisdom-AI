package com.codewisdom.agent.config;

import com.codewisdom.agent.client.llm.DeepSeekLlmProvider;
import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmClient.FallbackLlmProvider;
import com.codewisdom.agent.client.llm.LlmClient.LlmClientProperties;
import com.codewisdom.agent.client.llm.LlmClient.MockLlmProvider;
import com.codewisdom.agent.client.llm.LlmProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(LlmClientConfig.LlmSettings.class)
public class LlmClientConfig {

    @Bean
    LlmClientProperties llmClientProperties(LlmSettings settings) {
        return new LlmClientProperties(
                settings.enabled(),
                settings.timeoutMs(),
                settings.maxRetries(),
                settings.retryBackoffMs(),
                settings.fallbackMessage(),
                settings.disabledMessage());
    }

    @Bean
    LlmClient llmClient(LlmSettings settings, LlmClientProperties properties) {
        LlmProvider primary = resolvePrimary(settings);
        LlmProvider fallback = new FallbackLlmProvider(settings.fallbackMessage());
        return new LlmClient(primary, fallback, properties);
    }

    private static LlmProvider resolvePrimary(LlmSettings settings) {
        if ("deepseek".equalsIgnoreCase(settings.provider())) {
            return new DeepSeekLlmProvider(
                    settings.deepseek().apiKey(),
                    settings.deepseek().baseUrl(),
                    settings.deepseek().model());
        }
        return new MockLlmProvider("mock", null);
    }

    @ConfigurationProperties(prefix = "codewisdom.llm")
    public record LlmSettings(
            boolean enabled,
            long timeoutMs,
            int maxRetries,
            long retryBackoffMs,
            String fallbackMessage,
            String disabledMessage,
            String provider,
            DeepSeekSettings deepseek
    ) {
        public LlmSettings {
            if (deepseek == null) {
                deepseek = new DeepSeekSettings("https://api.deepseek.com", "deepseek-chat", "");
            }
        }
    }

    public record DeepSeekSettings(String baseUrl, String model, String apiKey) {
    }
}
