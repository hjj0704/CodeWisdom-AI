package com.codewisdom.evaluation.eval;

import com.codewisdom.evaluation.domain.EvalDataset;
import com.codewisdom.evaluation.domain.EvalMetrics;
import com.codewisdom.evaluation.service.EvalMetricsCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("T-902 量化指标计算")
class MetricsTest {

    private final EvalMetricsCalculator calculator = new EvalMetricsCalculator();

    @Test
    @DisplayName("缺陷样例：应检出 1 条且命中 → 召回 1.0，无禁止误报")
    void defectSampleAllHits() {
        var expected = new EvalDataset.ExpectedAnnotations(
                List.of("CW-NULL-001"),
                List.of(),
                List.of("service"),
                true,
                1,
                5);
        var obs = new EvalMetrics.Observation(
                Set.of("CW-NULL-001"),
                Set.of("service", "controller"),
                2,
                true);

        EvalMetrics m = calculator.compute(expected, obs);

        assertThat(m.bugRecall()).isEqualTo(1.0);
        assertThat(m.falsePositiveRate()).isEqualTo(0.0);
        assertThat(m.architectureAccuracy()).isEqualTo(1.0);
        assertThat(m.ruleMatchRate()).isEqualTo(1.0);
        assertThat(m.documentationScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("误报：检出 2 条含 1 条禁止规则 → 误报率 0.5，规则匹配率 0.5")
    void falsePositiveOnForbiddenRule() {
        var expected = new EvalDataset.ExpectedAnnotations(
                List.of("CW-NULL-001"),
                List.of("CW-DEP-CONFLICT-001"),
                List.of(),
                false,
                1,
                3);
        var obs = new EvalMetrics.Observation(
                Set.of("CW-NULL-001", "CW-DEP-CONFLICT-001"),
                Set.of(),
                2,
                false);

        EvalMetrics m = calculator.compute(expected, obs);

        assertThat(m.bugRecall()).isEqualTo(1.0);
        assertThat(m.falsePositiveRate()).isCloseTo(0.5, within(1e-9));
        assertThat(m.ruleMatchRate()).isCloseTo(0.5, within(1e-9));
        assertThat(m.documentationScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("漏报：应检出未命中 → 召回 0，规则匹配率下降")
    void missedRequiredRule() {
        var expected = new EvalDataset.ExpectedAnnotations(
                List.of("CW-NULL-001", "CW-SECRET-001"),
                List.of(),
                List.of("controller"),
                false,
                2,
                4);
        var obs = new EvalMetrics.Observation(
                Set.of("CW-NULL-001"),
                Set.of("service"),
                1,
                false);

        EvalMetrics m = calculator.compute(expected, obs);

        assertThat(m.bugRecall()).isCloseTo(0.5, within(1e-9));
        assertThat(m.architectureAccuracy()).isCloseTo(0.0, within(1e-9));
        assertThat(m.ruleMatchRate()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("手算宏平均：两样例指标算术平均")
    void macroAverageHandCalculated() {
        EvalMetrics a = new EvalMetrics(1.0, 0.0, 1.0, 1.0, 1.0);
        EvalMetrics b = new EvalMetrics(0.5, 0.5, 0.0, 0.5, 0.0);

        EvalMetrics avg = calculator.macroAverage(List.of(a, b));

        assertThat(avg.bugRecall()).isCloseTo(0.75, within(1e-9));
        assertThat(avg.falsePositiveRate()).isCloseTo(0.25, within(1e-9));
        assertThat(avg.architectureAccuracy()).isCloseTo(0.5, within(1e-9));
        assertThat(avg.ruleMatchRate()).isCloseTo(0.75, within(1e-9));
        assertThat(avg.documentationScore()).isCloseTo(0.5, within(1e-9));
    }
}
