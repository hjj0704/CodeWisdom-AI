package com.codewisdom.evaluation.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 量化评测数据集模型（T-901）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalDataset(
        String version,
        List<EvalSample> samples
) {
    public EvalDataset {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version 不能为空");
        }
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("samples 不能为空");
        }
        samples = List.copyOf(samples);
    }

    public enum Category {
        NORMAL,
        DEFECT,
        CONFLICT,
        NO_DOC
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExpectedAnnotations(
            List<String> requiredRuleIds,
            List<String> forbiddenRuleIds,
            List<String> expectedLayers,
            boolean documentationExpected,
            int minIssueCount,
            int maxIssueCount
    ) {
        public ExpectedAnnotations {
            requiredRuleIds = requiredRuleIds == null ? List.of() : List.copyOf(requiredRuleIds);
            forbiddenRuleIds = forbiddenRuleIds == null ? List.of() : List.copyOf(forbiddenRuleIds);
            expectedLayers = expectedLayers == null ? List.of() : List.copyOf(expectedLayers);
            if (minIssueCount < 0) {
                throw new IllegalArgumentException("minIssueCount 不能为负");
            }
            if (maxIssueCount < minIssueCount) {
                throw new IllegalArgumentException("maxIssueCount 不能小于 minIssueCount");
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvalSample(
            String id,
            Category category,
            String title,
            String description,
            ExpectedAnnotations expected
    ) {
        public EvalSample {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id 不能为空");
            }
            if (category == null) {
                throw new IllegalArgumentException("category 不能为空");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title 不能为空");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description 不能为空");
            }
            if (expected == null) {
                throw new IllegalArgumentException("expected 不能为空");
            }
        }
    }
}
