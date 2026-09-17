package com.codewisdom.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codewisdom.downstream")
public class DownstreamProperties {

    /** 直连 project-resource（同机部署时用 8081）。 */
    private String projectResourceBaseUrl = "http://127.0.0.1:8081";
}
