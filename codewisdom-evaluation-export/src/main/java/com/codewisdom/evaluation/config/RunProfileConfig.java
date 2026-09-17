package com.codewisdom.evaluation.config;

import com.codewisdom.evaluation.service.DeployGuideGenerator;
import com.codewisdom.evaluation.service.RunCapabilityJudge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RunProfileConfig {

    @Bean
    RunCapabilityJudge runCapabilityJudge() {
        return new RunCapabilityJudge();
    }

    @Bean
    DeployGuideGenerator deployGuideGenerator() {
        return new DeployGuideGenerator();
    }
}
