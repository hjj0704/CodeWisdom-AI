package com.codewisdom.agent.config;

import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmClient.FallbackLlmProvider;
import com.codewisdom.agent.client.llm.LlmClient.LlmClientProperties;
import com.codewisdom.agent.client.llm.LlmClient.MockLlmProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestLlmClientConfig {

    @Bean
    LlmClient llmClient() {
        return new LlmClient(
                new MockLlmProvider("test", null),
                new FallbackLlmProvider("test-fallback"),
                LlmClientProperties.defaults());
    }
}
