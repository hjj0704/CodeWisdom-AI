package com.codewisdom.evaluation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T-802 部署指引生成")
class DeployGuideTest {

    private final DeployGuideGenerator generator = new DeployGuideGenerator();

    @Test
    @DisplayName("输出含依赖清单与启动步骤")
    void containsDepsAndSteps() {
        String guide = generator.generate("codewisdom-demo", List.of("MySQL 8", "Redis 7", "RabbitMQ"));

        assertThat(guide).contains("## 依赖清单");
        assertThat(guide).contains("MySQL 8");
        assertThat(guide).contains("## 启动步骤");
        assertThat(guide).contains("mvn clean package");
        assertThat(guide).contains("## 注意事项");
    }
}
