package com.codewisdom.evaluation.service;

import java.util.List;
import java.util.Objects;

/**
 * 重型项目本地部署指引生成（T-802）。
 */
public class DeployGuideGenerator {

    public String generate(String projectName, List<String> middlewareDependencies) {
        Objects.requireNonNull(projectName, "projectName");
        List<String> deps = middlewareDependencies == null ? List.of() : middlewareDependencies;

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(projectName).append(" 本地部署指引\n\n");
        sb.append("## 依赖清单\n");
        if (deps.isEmpty()) {
            sb.append("- （未检测到中间件依赖，请人工确认）\n");
        } else {
            for (String dep : deps) {
                sb.append("- ").append(dep).append('\n');
            }
        }
        sb.append("\n## 启动步骤\n");
        sb.append("1. 安装 JDK 17 与 Maven 3.9+\n");
        sb.append("2. 启动所需中间件（见上表）\n");
        sb.append("3. 配置 `application-local.yml` 中的连接信息\n");
        sb.append("4. 执行 `mvn clean package -DskipTests`\n");
        sb.append("5. 按模块 README 启动各服务\n\n");
        sb.append("## 注意事项\n");
        sb.append("- 本指引为辅助分析结果，需人工确认\n");
        sb.append("- 生产环境请单独配置安全组、凭据与备份\n");
        return sb.toString();
    }
}
