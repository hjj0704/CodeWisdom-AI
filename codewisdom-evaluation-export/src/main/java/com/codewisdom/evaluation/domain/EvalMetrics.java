package com.codewisdom.evaluation.domain;

import java.util.Set;

/**
 * 单样例量化评测结果（T-902）。各指标取值范围 [0, 1]。
 */
public record EvalMetrics(
        double bugRecall,
        double falsePositiveRate,
        double architectureAccuracy,
        double ruleMatchRate,
        double documentationScore
) {
    public EvalMetrics {
        validateRate(bugRecall, "bugRecall");
        validateRate(falsePositiveRate, "falsePositiveRate");
        validateRate(architectureAccuracy, "architectureAccuracy");
        validateRate(ruleMatchRate, "ruleMatchRate");
        validateRate(documentationScore, "documentationScore");
    }

    private static void validateRate(double value, String name) {
        if (value < 0 || value > 1 || Double.isNaN(value)) {
            throw new IllegalArgumentException(name + " 必须在 [0,1] 内");
        }
    }

    /** 流水线对单样例的实际观测（与标注比对用）。 */
    public record Observation(
            Set<String> detectedRuleIds,
            Set<String> detectedLayers,
            int issueCount,
            boolean hasDocumentation
    ) {
        public Observation {
            detectedRuleIds = detectedRuleIds == null ? Set.of() : Set.copyOf(detectedRuleIds);
            detectedLayers = detectedLayers == null ? Set.of() : Set.copyOf(detectedLayers);
            if (issueCount < 0) {
                throw new IllegalArgumentException("issueCount 不能为负");
            }
        }
    }
}
