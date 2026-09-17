package com.codewisdom.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 骨架验收：网关上下文可加载（不依赖 Nacos），且静态路由已装配。
 */
@SpringBootTest
class GatewayApplicationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    @DisplayName("上下文加载成功且静态路由已注册（含 T-103 验收路由）")
    void contextLoadsWithStaticRoutes() {
        var routeIds = routeDefinitionLocator.getRouteDefinitions()
                .map(def -> def.getId())
                .collectList()
                .block();

        assertThat(routeIds)
                .isNotNull()
                .containsExactlyInAnyOrder(
                        "codewisdom-project-resource",
                        "codewisdom-code-analysis",
                        "codewisdom-agent-orchestration",
                        "codewisdom-evaluation-export",
                        "codewisdom-code-analysis-actuator");
    }
}
