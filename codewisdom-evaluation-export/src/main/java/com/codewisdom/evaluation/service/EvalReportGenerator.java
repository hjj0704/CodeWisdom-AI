package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.domain.EvalErrorTrace.TraceEntry;
import com.codewisdom.evaluation.domain.EvalMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

/**
 * 评测报告生成（T-904）：输出 Markdown + JSON，可入库比对。
 */
public class EvalReportGenerator {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ErrorTraceService traceService = new ErrorTraceService();

    public ReportPayload generate(
            String datasetVersion,
            String sampleId,
            EvalMetrics metrics,
            List<TraceEntry> trace,
            String status) {
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(status, "status");
        String metricsJson = toJson(metrics);
        String traceJson = trace == null || trace.isEmpty() ? "[]" : traceService.toJson(trace);
        String markdown = buildMarkdown(datasetVersion, sampleId, metrics, trace, status);
        return new ReportPayload(metricsJson, traceJson, markdown, status);
    }

    public record ReportPayload(
            String metricsJson,
            String traceJson,
            String reportMarkdown,
            String status
    ) {
    }

    private String buildMarkdown(
            String datasetVersion,
            String sampleId,
            EvalMetrics metrics,
            List<TraceEntry> trace,
            String status) {
        StringBuilder sb = new StringBuilder();
        sb.append("# CodeWisdom 评测报告\n\n");
        sb.append("- 数据集版本：").append(datasetVersion == null ? "-" : datasetVersion).append('\n');
        sb.append("- 样例 ID：").append(sampleId == null ? "-" : sampleId).append('\n');
        sb.append("- 状态：").append(status).append("\n\n");
        sb.append("## 指标\n\n");
        sb.append("| 指标 | 值 |\n|---|---|\n");
        sb.append("| Bug 召回率 | ").append(format(metrics.bugRecall())).append(" |\n");
        sb.append("| 误报率 | ").append(format(metrics.falsePositiveRate())).append(" |\n");
        sb.append("| 架构识别准确率 | ").append(format(metrics.architectureAccuracy())).append(" |\n");
        sb.append("| 规则匹配率 | ").append(format(metrics.ruleMatchRate())).append(" |\n");
        sb.append("| 文档评分 | ").append(format(metrics.documentationScore())).append(" |\n\n");
        sb.append("## 错误溯源\n\n");
        if (trace == null || trace.isEmpty()) {
            sb.append("无失败节点。\n");
        } else {
            for (TraceEntry entry : trace) {
                sb.append("- **").append(entry.node()).append("**：")
                        .append(entry.message());
                if (!entry.detail().isBlank()) {
                    sb.append("（").append(entry.detail()).append(')');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
