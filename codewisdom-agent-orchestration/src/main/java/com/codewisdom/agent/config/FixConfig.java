package com.codewisdom.agent.config;

import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.service.doc.DocCommentGenerator.DocGenProperties;
import com.codewisdom.agent.service.fix.DiffGenerator;
import com.codewisdom.agent.service.fix.FixSuggestGenerator;
import com.codewisdom.agent.service.fix.HitlReviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FixConfig {

    @Bean
    DocGenProperties docGenProperties(
            @Value("${codewisdom.doc-gen.enabled:true}") boolean enabled) {
        return new DocGenProperties(enabled);
    }

    @Bean
    FixSuggestGenerator fixSuggestGenerator(LlmClient llmClient, DocGenProperties docGenProperties) {
        return new FixSuggestGenerator(llmClient, docGenProperties);
    }

    @Bean
    DiffGenerator diffGenerator() {
        return new DiffGenerator();
    }

    @Bean
    HitlReviewService hitlReviewService() {
        return new HitlReviewService();
    }
}
