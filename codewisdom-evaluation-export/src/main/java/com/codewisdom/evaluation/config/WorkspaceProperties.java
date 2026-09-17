package com.codewisdom.evaluation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codewisdom.workspace")
public class WorkspaceProperties {

    private String root = "./data/repos";
}
