package com.codewisdom.evaluation.dto;

public record ProjectScoreView(
        double overall,
        double bugRecall,
        double falsePositiveRate,
        double architectureAccuracy,
        double ruleMatchRate,
        double documentationScore,
        String summary
) {
}
