package com.codewisdom.analysis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codewisdom.workspace")
public class WorkspaceProperties {

    /** 与 project-resource 的 codewisdom.import.workspace-root 保持一致。 */
    private String root = "./data/repos";
}
