package com.codewisdom.evaluation.eval;

import com.codewisdom.evaluation.domain.EvalErrorTrace;
import com.codewisdom.evaluation.domain.EvalErrorTrace.PipelineNode;
import com.codewisdom.evaluation.domain.EvalMetrics;
import com.codewisdom.evaluation.service.EvalReportGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T-904 评测报告生成")
class EvalReportTest {

    private final EvalReportGenerator generator = new EvalReportGenerator();

    @Test
    @DisplayName("报告含全部指标与溯源明细，JSON 可解析比对")
    void reportContainsMetricsAndTrace() {
        EvalMetrics metrics = new EvalMetrics(0.8, 0.1, 0.9, 0.85, 1.0);
        var trace = new EvalErrorTrace.Builder()
                .fail(PipelineNode.AUDIT, "规则引擎异常", "CW-NULL-001")
                .build();

        EvalReportGenerator.ReportPayload payload = generator.generate(
                "1.0", "defect-001", metrics, trace, "FAILED");

        assertThat(payload.metricsJson()).contains("0.8").contains("bugRecall");
        assertThat(payload.traceJson()).contains("AUDIT");
        assertThat(payload.reportMarkdown())
                .contains("Bug 召回率")
                .contains("0.80")
                .contains("AUDIT")
                .contains("规则引擎异常");
        assertThat(payload.status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("成功样例无溯源时 Markdown 标明无失败")
    void successWithoutTrace() {
        EvalMetrics metrics = new EvalMetrics(1.0, 0.0, 1.0, 1.0, 1.0);

        EvalReportGenerator.ReportPayload payload = generator.generate(
                "1.0", "normal-001", metrics, List.of(), "COMPLETED");

        assertThat(payload.reportMarkdown()).contains("无失败节点");
        assertThat(payload.traceJson()).isEqualTo("[]");
    }
}
