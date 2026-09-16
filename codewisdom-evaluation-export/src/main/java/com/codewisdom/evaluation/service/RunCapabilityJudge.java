package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.domain.RunCapability;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 运行能力判定（T-801）：检测工程是否声明 Redis/MySQL/MQ 等中间件依赖。
 */
public class RunCapabilityJudge {

    private static final Set<String> HEAVY_MARKERS = Set.of(
            "redis", "mysql", "mariadb", "postgresql", "postgres",
            "rabbitmq", "spring-rabbit", "kafka", "nacos", "mongodb", "elasticsearch",
            "minio", "rocketmq", "activemq");

    public RunCapability judge(Collection<String> dependencyHints) {
        Objects.requireNonNull(dependencyHints, "dependencyHints");
        for (String hint : dependencyHints) {
            if (hint == null || hint.isBlank()) {
                continue;
            }
            String normalized = hint.toLowerCase(Locale.ROOT);
            for (String marker : HEAVY_MARKERS) {
                if (normalized.contains(marker)) {
                    return RunCapability.HEAVY;
                }
            }
        }
        return RunCapability.LIGHTWEIGHT;
    }
}
