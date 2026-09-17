package com.codewisdom.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * T-103：网关 Sentinel 流控（按 routeId 限流，默认 100 QPS/路由）。
 */
@Configuration
public class SentinelGatewayConfig {

    private static final int DEFAULT_QPS = 100;

    private static final List<String> ROUTE_IDS = List.of(
            "codewisdom-project-resource",
            "codewisdom-code-analysis",
            "codewisdom-agent-orchestration",
            "codewisdom-evaluation-export",
            "codewisdom-code-analysis-actuator");

    @PostConstruct
    public void initGatewayFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        for (String routeId : ROUTE_IDS) {
            rules.add(new GatewayFlowRule(routeId)
                    .setCount(DEFAULT_QPS)
                    .setIntervalSec(1));
        }
        GatewayRuleManager.loadRules(rules);
    }
}
