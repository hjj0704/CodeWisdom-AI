package com.codewisdom.evaluation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codewisdom.downstream")
public class DownstreamProperties {

    private String codeAnalysisBaseUrl = "http://127.0.0.1:8082";
}
