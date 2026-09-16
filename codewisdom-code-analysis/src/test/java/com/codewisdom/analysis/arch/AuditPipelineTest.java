package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.arch.rules.DeadCodeRule;
import com.codewisdom.analysis.arch.rules.ExceptionHandlingRule;
import com.codewisdom.analysis.arch.rules.HardcodedValueRule;
import com.codewisdom.analysis.arch.rules.NullSafetyRule;
import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.domain.AuditReport;
import com.codewisdom.analysis.parser.CallGraph;
import com.codewisdom.analysis.parser.JavaDependencyExtractor;
import com.codewisdom.analysis.parser.JavaStructureExtractor;
import com.codewisdom.analysis.parser.LanguageRegistry;
import com.codewisdom.analysis.parser.ParseHandle;
import com.codewisdom.analysis.parser.SourceParser;
import com.codewisdom.analysis.parser.SourceStructure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-505 验收：风险分级与审计汇总。
 *
 * <h2>验收怎么算</h2>
 * {@code docs/acceptance.md} 阶段 5 的条目是「每条问题必须包含：文件路径、行号、问题描述、
 * 风险说明、触发场景、风险等级」。这一条在 {@code AuditIssue} 的构造函数里已经是硬约束，
 * 本卡要做的是把四路产出<b>汇总成一份可交付的报告并分级</b>，所以断言分三层：
 * <ol>
 *   <li><b>分级正确</b>：按等级计数、最高等级、含高危判定；</li>
 *   <li><b>分级不歧义</b>：三档恒定出现，「高危 0」与「压根没统计高危」必须能区分；</li>
 *   <li><b>端到端</b>：真实分析器（{@code AuditEngine}）的产出能被直接汇总，不用二次加工。</li>
 * </ol>
 *
 * <h2>不落库（本卡的边界）</h2>
 * 「问题模型入库」需要 code-analysis 先有持久化层，而「各服务接入 MySQL + MyBatis-Plus +
 * Flyway」是 <b>T-105（挂起中）</b>的职责。因此本卡只产出<b>内存中的报告</b>，
 * 不在这里半接一个数据源——半接会让 T-105 落地时更难对齐。
 */
@DisplayName("T-505 风险分级与审计汇总")
class AuditPipelineTest {

    /** 与 T-501 同一份样例：四类缺陷齐全，用来验证「真实产出能被直接汇总」。 */
    private static final String SAMPLE = """
            package demo.audit;

            public class VulnerableService {

                private static final String DB_PASSWORD = "password=SuperSecret123";

                public boolean check(String input) {
                    return input.equals("admin");
                }

                public void swallow() {
                    try {
                        risky();
                    } catch (Exception e) {
                    }
                }

                private void unusedHelper() {
                }

                private void risky() {
                }
            }
            """;

    private static final String SAMPLE_PATH = "src/main/java/demo/audit/VulnerableService.java";

    private final AuditPipeline pipeline = new AuditPipeline();

    private static AuditIssue issue(String ruleId, AuditIssue.RiskLevel level, String file, int line,
                                    String description) {
        return new AuditIssue(ruleId, AuditIssue.IssueCategory.DEPENDENCY, level, file, line,
                description, "触发片段", "风险说明");
    }

    // ---- 分级 ----

    @Nested
    @DisplayName("风险分级")
    class Grading {

        @Test
        @DisplayName("按等级计数，最高等级取报告里最高的一档")
        void countsByRiskLevel() {
            AuditReport report = pipeline.aggregate(List.of(
                    issue("CW-A", AuditIssue.RiskLevel.LOW, "a.java", 1, "低"),
                    issue("CW-B", AuditIssue.RiskLevel.HIGH, "b.java", 2, "高"),
                    issue("CW-C", AuditIssue.RiskLevel.MEDIUM, "c.java", 3, "中"),
                    issue("CW-D", AuditIssue.RiskLevel.HIGH, "d.java", 4, "高")));

            assertThat(report.total()).isEqualTo(4);
            assertThat(report.countByRiskLevel())
                    .containsEntry(AuditIssue.RiskLevel.HIGH, 2L)
                    .containsEntry(AuditIssue.RiskLevel.MEDIUM, 1L)
                    .containsEntry(AuditIssue.RiskLevel.LOW, 1L);
            assertThat(report.highestRiskLevel()).contains(AuditIssue.RiskLevel.HIGH);
            assertThat(report.hasHighRisk()).isTrue();
            assertThat(report.issuesOf(AuditIssue.RiskLevel.HIGH)).hasSize(2);
        }

        @Test
        @DisplayName("三档恒定出现：高危 0 与「压根没统计高危」必须能区分")
        void alwaysReportsAllThreeLevels() {
            AuditReport report = pipeline.aggregate(List.of(
                    issue("CW-A", AuditIssue.RiskLevel.LOW, "a.java", 1, "低")));

            assertThat(report.countByRiskLevel()).containsOnlyKeys(
                    AuditIssue.RiskLevel.HIGH, AuditIssue.RiskLevel.MEDIUM, AuditIssue.RiskLevel.LOW);
            assertThat(report.countByRiskLevel().get(AuditIssue.RiskLevel.HIGH)).isZero();
            assertThat(report.hasHighRisk()).isFalse();
            assertThat(report.highestRiskLevel()).contains(AuditIssue.RiskLevel.LOW);
        }

        @Test
        @DisplayName("空报告不给自己安等级")
        void emptyReportHasNoRiskLevel() {
            AuditReport report = pipeline.aggregate(List.of());

            assertThat(report.isEmpty()).isTrue();
            assertThat(report.total()).isZero();
            // 「没问题」与「问题都是低危」是两回事
            assertThat(report.highestRiskLevel()).isEmpty();
            assertThat(report.hasHighRisk()).isFalse();
            assertThat(report.describe()).isEqualTo("未发现审计问题");
        }

        @Test
        @DisplayName("同一条发现被两路产出且等级不一致时，取风险更高的那条")
        void keepsHighestRiskWhenDuplicatesDisagree() {
            AuditReport report = pipeline.aggregate(List.of(
                    issue("CW-A", AuditIssue.RiskLevel.LOW, "a.java", 1, "同一条发现"),
                    issue("CW-A", AuditIssue.RiskLevel.HIGH, "a.java", 1, "同一条发现")));

            // 宁可报重也不漏报：把高危降级显示比重复报一次危险得多
            assertThat(report.total()).isEqualTo(1);
            assertThat(report.issues().get(0).riskLevel()).isEqualTo(AuditIssue.RiskLevel.HIGH);
        }
    }

    // ---- 汇总 ----

    @Nested
    @DisplayName("多路汇总")
    class Aggregation {

        @Test
        @DisplayName("完全相同的条目被合并，不是两个问题")
        void deduplicatesIdenticalIssues() {
            AuditIssue first = issue("CW-A", AuditIssue.RiskLevel.MEDIUM, "a.java", 7, "同一条");
            AuditIssue second = issue("CW-A", AuditIssue.RiskLevel.MEDIUM, "a.java", 7, "同一条");

            AuditReport report = pipeline.aggregate(List.of(first), List.of(second));

            assertThat(report.total()).isEqualTo(1);
        }

        @Test
        @DisplayName("规则、文件、行号、描述任一不同都算两条")
        void keepsIssuesThatDifferInAnyKeyPart() {
            AuditReport report = pipeline.aggregate(List.of(
                    issue("CW-A", AuditIssue.RiskLevel.MEDIUM, "a.java", 7, "同一条"),
                    issue("CW-B", AuditIssue.RiskLevel.MEDIUM, "a.java", 7, "同一条"),
                    issue("CW-A", AuditIssue.RiskLevel.MEDIUM, "b.java", 7, "同一条"),
                    issue("CW-A", AuditIssue.RiskLevel.MEDIUM, "a.java", 8, "同一条"),
                    issue("CW-A", AuditIssue.RiskLevel.MEDIUM, "a.java", 7, "另一条")));

            assertThat(report.total()).isEqualTo(5);
        }

        @Test
        @DisplayName("按规则与文件计数")
        void countsByRuleAndFile() {
            AuditReport report = pipeline.aggregate(List.of(
                    issue("CW-A", AuditIssue.RiskLevel.LOW, "a.java", 1, "一"),
                    issue("CW-A", AuditIssue.RiskLevel.LOW, "a.java", 2, "二"),
                    issue("CW-B", AuditIssue.RiskLevel.LOW, "b.java", 3, "三")));

            assertThat(report.countByRule()).containsExactly(
                    Map.entry("CW-A", 2L), Map.entry("CW-B", 1L));
            assertThat(report.countByFile()).containsExactly(
                    Map.entry("a.java", 2L), Map.entry("b.java", 1L));
            assertThat(report.files()).containsExactly("a.java", "b.java");
            assertThat(report.issuesOf("CW-A")).hasSize(2);
        }

        @Test
        @DisplayName("null 与空输入不炸")
        void handlesNullAndEmptyGroups() {
            assertThat(pipeline.aggregate((List<AuditIssue>) null).isEmpty()).isTrue();
            assertThat(pipeline.aggregate(List.of(), null, List.of()).isEmpty()).isTrue();
            assertThat(pipeline.aggregate((List<AuditIssue>) null, List.of()).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("输出按 文件 → 行号 → 规则 ID 排序，与传入顺序无关")
        void outputIsOrderedAndStable() {
            List<AuditIssue> issues = List.of(
                    issue("CW-B", AuditIssue.RiskLevel.LOW, "z.java", 1, "一"),
                    issue("CW-A", AuditIssue.RiskLevel.LOW, "a.java", 9, "二"),
                    issue("CW-A", AuditIssue.RiskLevel.LOW, "a.java", 2, "三"));

            AuditReport forward = pipeline.aggregate(issues);
            List<AuditIssue> reversed = new ArrayList<>(issues);
            Collections.reverse(reversed);
            AuditReport backward = pipeline.aggregate(reversed);

            assertThat(forward.issues()).isEqualTo(backward.issues());
            assertThat(forward.issues()).extracting(AuditIssue::filePath)
                    .containsExactly("a.java", "a.java", "z.java");
            assertThat(forward.issues()).extracting(AuditIssue::line).containsExactly(2, 9, 1);
        }
    }

    // ---- 端到端：真实分析器产出直接可汇总 ----

    @Test
    @DisplayName("真实分析器的产出能被直接汇总成报告，不用二次加工")
    void aggregatesRealAnalyzerOutput() {
        AuditReport report = pipeline.aggregate(runRealAudit());

        // 样例埋了四类缺陷：硬编码凭据（高）、字面量在 equals 右侧、空 catch、死代码
        assertThat(report.total()).isEqualTo(4);
        assertThat(report.hasHighRisk()).isTrue();
        assertThat(report.highestRiskLevel()).contains(AuditIssue.RiskLevel.HIGH);
        assertThat(report.countByRiskLevel().get(AuditIssue.RiskLevel.HIGH)).isEqualTo(1L);

        // 风险分级之外，报告本身要能直接交付：每条都齐六个字段
        assertThat(report.issues()).allSatisfy(issue -> {
            assertThat(issue.filePath()).isEqualTo(SAMPLE_PATH);
            assertThat(issue.line()).isPositive();
            assertThat(issue.description()).isNotBlank();
            assertThat(issue.riskDescription()).isNotBlank();
            assertThat(issue.trigger()).isNotBlank();
            assertThat(issue.riskLevel()).isNotNull();
        });

        System.out.printf("审计报告摘要：%s%n", report.describe());
    }

    private List<AuditIssue> runRealAudit() {
        LanguageRegistry registry = new LanguageRegistry();
        SourceParser parser = new SourceParser(registry);
        JavaStructureExtractor structureExtractor = new JavaStructureExtractor();
        JavaDependencyExtractor dependencyExtractor = new JavaDependencyExtractor(structureExtractor);

        try (ParseHandle handle = parser.parse("java", SAMPLE)) {
            SourceStructure structure = structureExtractor.extract(handle);
            CallGraph callGraph = CallGraph.build(List.of(new CallGraph.SourceUnit(
                    SAMPLE_PATH, structure.packageName(), structure.types(), structure.methods(),
                    dependencyExtractor.extractImports(handle),
                    dependencyExtractor.extractCallSites(handle))));
            AuditEngine engine = new AuditEngine(List.of(new NullSafetyRule(),
                    new ExceptionHandlingRule(), new HardcodedValueRule(), new DeadCodeRule()));
            return engine.audit(SAMPLE_PATH, handle, structure, callGraph);
        }
    }
}
