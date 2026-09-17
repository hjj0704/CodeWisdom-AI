package com.codewisdom.evaluation.dto;

public record EvalReportView(
        Long reportId,
        long projectId,
        String status,
        double overallScore,
        String metricsJson,
        String traceJson,
        String reportMarkdown
) {
}
