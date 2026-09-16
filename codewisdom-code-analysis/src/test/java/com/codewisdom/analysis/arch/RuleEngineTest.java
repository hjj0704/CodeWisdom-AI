package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.arch.rules.DeadCodeRule;
import com.codewisdom.analysis.arch.rules.ExceptionHandlingRule;
import com.codewisdom.analysis.arch.rules.HardcodedValueRule;
import com.codewisdom.analysis.arch.rules.NullSafetyRule;
import com.codewisdom.analysis.domain.AuditIssue;
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
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-501 验收：审计规则引擎与规则注册机制。
 *
 * <h2>验收怎么算</h2>
 * {@code docs/acceptance.md} 阶段 5 的三条硬要求，本测试逐条对应：
 * <ol>
 *   <li>「每条问题必须包含：文件路径、行号、问题描述、风险说明、触发场景、风险等级」
 *       —— 断言<b>每一条</b>产出都齐六个字段，且行号能在源码里定位回去；</li>
 *   <li>「风险等级仅允许 高危/中危/低危 三值」—— 由枚举保证，断言取值合法性；</li>
 *   <li>「构造的缺陷样例集：<b>高危漏报率为 0</b>」—— 样例里每个高危缺陷都标注了位置，
 *       逐个断言被检出，并计算漏报率打印出来。</li>
 * </ol>
 *
 * <p>「漏报率」这个指标只有在<b>标注过</b>的样例集上才有意义——所以断言不是
 * 「检出了问题」，而是「<b>这几处</b>问题都被检出了」。前者一个永远返回空列表的实现也能通过。
 */
@DisplayName("T-501 审计规则引擎")
class RuleEngineTest {

    /** 同时埋了四类缺陷的样例；注释里的标记用于断言「这一处必须被检出」。 */
    private static final String SAMPLE = """
            package demo.audit;

            public class VulnerableService {

                private static final String DB_PASSWORD = "password=SuperSecret123";
                private static final String API_TOKEN = "token=abcdef123456";
                private static final String JDBC_URL = "jdbc:mysql://10.0.0.1:3306/demo";

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

                private void usedHelper() {
                }

                public void useHelper() {
                    usedHelper();
                }

                private void risky() {
                }

                @PostConstruct
                private void initOnStartup() {
                }
            }
            """;

    private final LanguageRegistry registry = new LanguageRegistry();
    private final SourceParser parser = new SourceParser(registry);
    private final JavaStructureExtractor structureExtractor = new JavaStructureExtractor();
    private final JavaDependencyExtractor dependencyExtractor = new JavaDependencyExtractor(structureExtractor);

    /** 四个内置规则，按 Spring 注入的形状交给引擎。 */
    private static List<AuditRule> builtInRules() {
        return List.of(new NullSafetyRule(), new ExceptionHandlingRule(),
                new HardcodedValueRule(), new DeadCodeRule());
    }

    private static final String SAMPLE_PATH = "src/main/java/demo/audit/VulnerableService.java";

    private List<AuditIssue> auditSample(List<AuditRule> rules) {
        SourceStructure structure;
        CallGraph callGraph;
        try (ParseHandle handle = parser.parse("java", SAMPLE)) {
            structure = structureExtractor.extract(handle);
            callGraph = CallGraph.build(List.of(new CallGraph.SourceUnit(
                    SAMPLE_PATH, structure.packageName(), structure.types(), structure.methods(),
                    dependencyExtractor.extractImports(handle),
                    dependencyExtractor.extractCallSites(handle))));
            return new AuditEngine(rules).audit(SAMPLE_PATH, handle, structure, callGraph);
        }
    }

    private List<AuditIssue> auditSample() {
        return auditSample(builtInRules());
    }

    /** 按源码里的文本标记反查行号，避免写死行号常量随样例增删漂移。 */
    private static int lineOf(String marker) {
        List<String> lines = SAMPLE.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(marker)) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("样例源码里找不到标记: " + marker);
    }

    private static List<AuditIssue> issuesAt(List<AuditIssue> issues, String marker) {
        int line = lineOf(marker);
        return issues.stream().filter(issue -> issue.line() == line).toList();
    }

    // ---- 统一问题模型 ----

    @Nested
    @DisplayName("统一问题模型")
    class UnifiedModel {

        @Test
        @DisplayName("每条问题都齐六个字段，行号能定位回源码")
        void everyIssueCarriesRequiredFields() {
            List<AuditIssue> issues = auditSample();

            assertThat(issues).isNotEmpty();
            assertThat(issues).allSatisfy(issue -> {
                assertThat(issue.ruleId()).isNotBlank();
                assertThat(issue.filePath()).isEqualTo(SAMPLE_PATH);
                assertThat(issue.hasLine()).isTrue();
                assertThat(issue.description()).isNotBlank();
                assertThat(issue.riskDescription()).isNotBlank();
                assertThat(issue.trigger()).isNotBlank();
                assertThat(issue.riskLevel()).isNotNull();
            });
        }

        @Test
        @DisplayName("风险等级只在三个取值里，构造缺字段直接失败")
        void modelEnforcesItsContract() {
            assertThat(AuditIssue.RiskLevel.values()).extracting(Enum::name)
                    .containsExactly("HIGH", "MEDIUM", "LOW");

            // 缺字段造不出对象：六个必填项逐个试一遍，少一个都要抛
            assertRejected(() -> issue("CW-X", null, "a.java", 1, "d", "t", "r"));
            assertRejected(() -> issue("CW-X", AuditIssue.RiskLevel.HIGH, " ", 1, "d", "t", "r"));
            assertRejected(() -> issue("CW-X", AuditIssue.RiskLevel.HIGH, "a.java", 1, " ", "t", "r"));
            assertRejected(() -> issue("CW-X", AuditIssue.RiskLevel.HIGH, "a.java", 1, "d", "", "r"));
            assertRejected(() -> issue("CW-X", AuditIssue.RiskLevel.HIGH, "a.java", 1, "d", "t", null));
            assertRejected(() -> issue(" ", AuditIssue.RiskLevel.HIGH, "a.java", 1, "d", "t", "r"));
            assertRejected(() -> issue("CW-X", AuditIssue.RiskLevel.HIGH, "a.java", -1, "d", "t", "r"));
        }

        private void assertRejected(Runnable construction) {
            assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class, construction::run)).isNotNull();
        }

        private static AuditIssue issue(String ruleId, AuditIssue.RiskLevel level, String filePath,
                                        int line, String description, String trigger, String risk) {
            return new AuditIssue(ruleId, AuditIssue.IssueCategory.HARDCODED, level,
                    filePath, line, description, trigger, risk);
        }
    }

    // ---- 四条内置规则 ----

    @Nested
    @DisplayName("内置规则")
    class BuiltInRules {

        @Test
        @DisplayName("四类缺陷都能检出，各自落在正确的行上")
        void detectsAllFourCategories() {
            List<AuditIssue> issues = auditSample();

            assertThat(issuesAt(issues, "input.equals(\"admin\")"))
                    .singleElement()
                    .satisfies(issue -> {
                        assertThat(issue.ruleId()).isEqualTo("CW-NULL-001");
                        assertThat(issue.category()).isEqualTo(AuditIssue.IssueCategory.NULL_SAFETY);
                        assertThat(issue.riskLevel()).isEqualTo(AuditIssue.RiskLevel.MEDIUM);
                    });

            assertThat(issuesAt(issues, "catch (Exception e)"))
                    .singleElement()
                    .satisfies(issue -> {
                        assertThat(issue.ruleId()).isEqualTo("CW-EXC-001");
                        assertThat(issue.category()).isEqualTo(AuditIssue.IssueCategory.UNCAUGHT_EXCEPTION);
                    });

            assertThat(issuesAt(issues, "DB_PASSWORD = "))
                    .singleElement()
                    .satisfies(issue -> {
                        assertThat(issue.ruleId()).isEqualTo("CW-HARDCODE-001");
                        assertThat(issue.riskLevel()).isEqualTo(AuditIssue.RiskLevel.HIGH);
                    });

            assertThat(issuesAt(issues, "JDBC_URL = "))
                    .singleElement()
                    .satisfies(issue -> assertThat(issue.riskLevel())
                            .isEqualTo(AuditIssue.RiskLevel.MEDIUM));

            assertThat(issuesAt(issues, "private void unusedHelper"))
                    .singleElement()
                    .satisfies(issue -> {
                        assertThat(issue.ruleId()).isEqualTo("CW-DEAD-001");
                        assertThat(issue.riskLevel()).isEqualTo(AuditIssue.RiskLevel.LOW);
                    });
        }

        @Test
        @DisplayName("不误报：被调用的私有方法、框架回调、字段都不算死代码")
        void doesNotReportFalsePositives() {
            List<AuditIssue> issues = auditSample();

            assertThat(issuesAt(issues, "private void usedHelper")).isEmpty();
            assertThat(issuesAt(issues, "@PostConstruct")).isEmpty();
            assertThat(issues).noneMatch(issue -> issue.trigger().contains("useHelper"));
            // 常量字段不是方法，死代码规则不该碰
            assertThat(issues).noneMatch(issue -> issue.ruleId().equals("CW-DEAD-001")
                    && issue.trigger().contains("DB_PASSWORD"));
        }

        @Test
        @DisplayName("触发场景里带的是真实代码片段，不是 RuleId 复读")
        void triggerContainsRealCode() {
            List<AuditIssue> issues = auditSample();

            assertThat(issuesAt(issues, "input.equals(\"admin\")"))
                    .singleElement()
                    .satisfies(issue -> assertThat(issue.trigger()).contains("input.equals(\"admin\")"));
            assertThat(issuesAt(issues, "private void unusedHelper"))
                    .singleElement()
                    .satisfies(issue -> assertThat(issue.trigger()).contains("unusedHelper"));
        }

        @Test
        @DisplayName("没有调用图时死代码规则安静退出，不抛异常也不瞎报")
        void deadCodeRuleDegradesWithoutCallGraph() {
            try (ParseHandle handle = parser.parse("java", SAMPLE)) {
                SourceStructure structure = structureExtractor.extract(handle);
                List<AuditIssue> issues = new AuditEngine(List.of(new DeadCodeRule()))
                        .audit(SAMPLE_PATH, handle, structure, null);

                assertThat(issues).isEmpty();
            }
        }
    }

    // ---- 高危漏报率（acceptance.md 的硬指标） ----

    @Test
    @DisplayName("标注样例集上高危漏报率为 0")
    void noHighRiskMissesOnLabeledSample() {
        List<AuditIssue> issues = auditSample();

        // 人工标注：样例里每一处高危缺陷的位置
        List<String> labeledHighRisk = List.of("DB_PASSWORD = ", "API_TOKEN = ");

        int missed = 0;
        for (String marker : labeledHighRisk) {
            boolean detected = issuesAt(issues, marker).stream().anyMatch(AuditIssue::isHighRisk);
            if (!detected) {
                missed++;
            }
        }
        double missRate = (double) missed / labeledHighRisk.size();
        System.out.printf("标注样例集：高危缺陷 %d 处，漏报 %d 处，高危漏报率 = %.1f%%；总计检出 %d 条问题%n",
                labeledHighRisk.size(), missed, missRate * 100, issues.size());

        assertThat(missRate).as("高危漏报率").isEqualTo(0.0);
        assertThat(issues).filteredOn(AuditIssue::isHighRisk).hasSize(labeledHighRisk.size());
    }

    // ---- 可插拔 ----

    @Nested
    @DisplayName("规则可插拔")
    class Pluggable {

        /** 一个只存在于测试里的规则：引擎不该认识它，但必须能跑它。 */
        private static final class AlwaysFindingRule implements AuditRule {

            @Override
            public String id() {
                return "TEST-999";
            }

            @Override
            public AuditIssue.IssueCategory category() {
                return AuditIssue.IssueCategory.ARCHITECTURE;
            }

            @Override
            public AuditIssue.RiskLevel riskLevel() {
                return AuditIssue.RiskLevel.LOW;
            }

            @Override
            public List<AuditIssue> inspect(Context context) {
                return List.of(new AuditIssue(id(), category(), riskLevel(), context.sourcePath(),
                        AuditIssue.NO_LINE, "测试规则命中", "always", "仅用于验证引擎不认识具体规则"));
            }
        }

        @Test
        @DisplayName("自定义规则无需改引擎即可生效")
        void customRuleIsPickedUp() {
            List<AuditRule> rules = new ArrayList<>(builtInRules());
            rules.add(new AlwaysFindingRule());

            List<AuditIssue> issues = auditSample(rules);

            assertThat(issues).anyMatch(issue -> issue.ruleId().equals("TEST-999"));
            // 内置规则照常工作，加入新规则不影响它们
            assertThat(issues).anyMatch(issue -> issue.ruleId().equals("CW-NULL-001"));
        }

        @Test
        @DisplayName("引擎不认识任何具体规则：只注册自定义规则时也只跑它")
        void engineHasNoHardcodedRules() {
            List<AuditIssue> issues = auditSample(List.of(new AlwaysFindingRule()));

            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).ruleId()).isEqualTo("TEST-999");
        }

        @Test
        @DisplayName("一条规则都没有时引擎照样能构造与运行")
        void worksWithNoRules() {
            AuditEngine engine = new AuditEngine(List.of());

            assertThat(engine.rules()).isEmpty();
            assertThat(auditSample(List.of())).isEmpty();
        }

        @Test
        @DisplayName("规则清单可按 ID 查询，元信息完整")
        void exposesRuleMetadata() {
            AuditEngine engine = new AuditEngine(builtInRules());

            assertThat(engine.rules()).extracting(AuditRule::id)
                    .containsExactly("CW-DEAD-001", "CW-EXC-001", "CW-HARDCODE-001", "CW-NULL-001");
            assertThat(engine.rule("CW-HARDCODE-001").category())
                    .isEqualTo(AuditIssue.IssueCategory.HARDCODED);
            // 不执行规则也能知道它会不会产出高危问题
            assertThat(engine.rules()).extracting(AuditRule::riskLevel)
                    .contains(AuditIssue.RiskLevel.HIGH);
            assertThat(engine.rule("NOT-EXIST")).isNull();
        }
    }

    // ---- 输出顺序 ----

    @Test
    @DisplayName("输出按 文件 → 行号 → 规则 ID 稳定排序")
    void outputOrderIsStable() {
        List<AuditIssue> issues = auditSample();

        List<AuditIssue> sorted = issues.stream()
                .sorted(Comparator.comparing(AuditIssue::filePath)
                        .thenComparingInt(AuditIssue::line)
                        .thenComparing(AuditIssue::ruleId))
                .toList();
        assertThat(issues).isEqualTo(sorted);
        assertThat(auditSample()).isEqualTo(issues);
    }
}
