package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.domain.EvalDataset;
import com.codewisdom.evaluation.domain.EvalMetrics;

import java.util.List;
import java.util.Set;

/**
 * 量化指标计算（T-902）。
 *
 * <ul>
 *   <li><b>Bug 召回率</b>：应检出的规则命中数 / 应检出规则数</li>
 *   <li><b>误报率</b>：检出中命中禁止规则的比例 = |检出 ∩ 禁止| / |检出|</li>
 *   <li><b>架构识别准确率</b>：|检出分层 ∩ 期望分层| / |期望分层|</li>
 *   <li><b>规则匹配率</b>：(应检出命中 + 禁止未误报) / (应检出 + 禁止)</li>
 *   <li><b>文档评分</b>：期望与实测一致为 1，否则 0</li>
 * </ul>
 */
public class EvalMetricsCalculator {

    public EvalMetrics compute(EvalDataset.ExpectedAnnotations expected, EvalMetrics.Observation observation) {
        double recall = bugRecall(expected.requiredRuleIds(), observation.detectedRuleIds());
        double falsePositive = falsePositiveRate(expected.forbiddenRuleIds(), observation.detectedRuleIds());
        double arch = architectureAccuracy(expected.expectedLayers(), observation.detectedLayers());
        double ruleMatch = ruleMatchRate(
                expected.requiredRuleIds(), expected.forbiddenRuleIds(), observation.detectedRuleIds());
        double doc = documentationScore(expected.documentationExpected(), observation.hasDocumentation());
        return new EvalMetrics(recall, falsePositive, arch, ruleMatch, doc);
    }

    static double bugRecall(List<String> required, Set<String> detected) {
        if (required.isEmpty()) {
            return 1.0;
        }
        long hits = required.stream().filter(detected::contains).count();
        return hits / (double) required.size();
    }

    static double falsePositiveRate(List<String> forbidden, Set<String> detected) {
        if (detected.isEmpty()) {
            return 0.0;
        }
        long falseHits = forbidden.stream().filter(detected::contains).count();
        return falseHits / (double) detected.size();
    }

    static double architectureAccuracy(List<String> expectedLayers, Set<String> detectedLayers) {
        if (expectedLayers.isEmpty()) {
            return 1.0;
        }
        long hits = expectedLayers.stream().filter(detectedLayers::contains).count();
        return hits / (double) expectedLayers.size();
    }

    static double ruleMatchRate(List<String> required, List<String> forbidden, Set<String> detected) {
        int denominator = required.size() + forbidden.size();
        if (denominator == 0) {
            return 1.0;
        }
        long requiredHits = required.stream().filter(detected::contains).count();
        long forbiddenCorrect = forbidden.stream().filter(id -> !detected.contains(id)).count();
        return (requiredHits + forbiddenCorrect) / (double) denominator;
    }

    static double documentationScore(boolean expected, boolean actual) {
        return expected == actual ? 1.0 : 0.0;
    }

    /** 多样例宏平均（每样例权重相同）。 */
    public EvalMetrics macroAverage(List<EvalMetrics> perSample) {
        if (perSample == null || perSample.isEmpty()) {
            throw new IllegalArgumentException("perSample 不能为空");
        }
        double recall = 0;
        double fp = 0;
        double arch = 0;
        double rule = 0;
        double doc = 0;
        for (EvalMetrics m : perSample) {
            recall += m.bugRecall();
            fp += m.falsePositiveRate();
            arch += m.architectureAccuracy();
            rule += m.ruleMatchRate();
            doc += m.documentationScore();
        }
        int n = perSample.size();
        return new EvalMetrics(recall / n, fp / n, arch / n, rule / n, doc / n);
    }
}
