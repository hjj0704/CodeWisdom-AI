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

    /** 单次审计/架构扫描纳入的 Java 文件上限（按路径排序取前 N 个）。 */
    private int maxJavaFiles = 500;
}
