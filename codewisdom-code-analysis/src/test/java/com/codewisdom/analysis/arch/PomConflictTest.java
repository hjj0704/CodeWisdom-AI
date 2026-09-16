package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.AuditIssue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-502 验收：{@code pom.xml} 依赖冲突检测。
 *
 * <h2>口径（别把结论说大）</h2>
 * 检测的是「<b>声明级</b>」冲突：只看工程里明明写出来的 {@code <version>}。
 * <b>传递依赖冲突检不出来</b>——A 依赖 B、B 依赖 C-1.0 而工程直接声明 C-2.0，
 * 这种最常见的 Maven 冲突需要真正的依赖解析器。{@code <dependencyManagement>}、
 * {@code <exclusions>}、{@code <profiles>} 也都不在范围内。
 *
 * <p>因此断言的是「这几处<b>声明</b>被检出」，而不是「运行时一定会冲突」。
 * 跨模块版本不一致尤其要注意：两个独立模块各用各的版本<b>是合法的</b>，
 * 所以那一条报的是「声明不一致」而不是「冲突」，措辞与等级都分开。
 *
 * <h2>行号锚在哪</h2>
 * 锚在 {@code <dependency>} <b>块首行</b>，不是 {@code <version>} 行——用户拿着行号要跳到的是
 * 「这条声明」，而块首比块内某一行更适合作为跳转目标。所以断言用「第 N 个 dependency 块」
 * 定位，不用「哪一行写了版本号」。
 */
@DisplayName("T-502 pom 依赖冲突检测")
class PomConflictTest {

    private static final String PARENT_POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0.0</version>
                <packaging>pom</packaging>
                <modules>
                    <module>module-a</module>
                    <module>module-b</module>
                    <module>module-c</module>
                    <module>module-clean</module>
                </modules>
            </project>
            """;

    /** dependency 块顺序：① shared-lib ② slf4j-api ③ slf4j-api（重复） */
    private static final String MODULE_A_POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                </parent>
                <artifactId>module-a</artifactId>

                <dependencies>
                    <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>shared-lib</artifactId>
                        <version>1.0</version>
                    </dependency>
                    <dependency>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-api</artifactId>
                        <version>2.0.9</version>
                    </dependency>
                    <dependency>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-api</artifactId>
                        <version>2.0.9</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    private static final String MODULE_B_POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>module-b</artifactId>

                <dependencies>
                    <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>shared-lib</artifactId>
                        <version>2.0</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    /** dependency 块顺序：① dup-lib 1.0 ② dup-lib 2.0 ③ 缺 groupId ④ 版本属性不存在 */
    private static final String MODULE_C_POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>module-c</artifactId>

                <dependencies>
                    <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>dup-lib</artifactId>
                        <version>1.0</version>
                    </dependency>
                    <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>dup-lib</artifactId>
                        <version>2.0</version>
                    </dependency>
                    <dependency>
                        <artifactId>no-group-id</artifactId>
                        <version>1.0</version>
                    </dependency>
                    <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>bad-version</artifactId>
                        <version>${undefined.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    private static final String MODULE_CLEAN_POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>module-clean</artifactId>

                <dependencies>
                    <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>clean-lib</artifactId>
                        <version>1.0</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    private final PomConflictAnalyzer analyzer = new PomConflictAnalyzer();

    private static Map<String, String> multiModuleProject() {
        Map<String, String> poms = new LinkedHashMap<>();
        poms.put("pom.xml", PARENT_POM);
        poms.put("module-a/pom.xml", MODULE_A_POM);
        poms.put("module-b/pom.xml", MODULE_B_POM);
        poms.put("module-c/pom.xml", MODULE_C_POM);
        poms.put("module-clean/pom.xml", MODULE_CLEAN_POM);
        return poms;
    }

    private List<AuditIssue> analyze() {
        return analyzer.analyze(multiModuleProject());
    }

    /**
     * 第 {@code occurrence} 个「{@code <dependency>} 块」所在的行号。
     *
     * <p>依赖检测把行号锚在块首，所以断言必须按块定位；用 {@code <version>} 行的位置去比会差三行。
     */
    private static int dependencyBlockLine(String pom, int occurrence) {
        List<String> lines = pom.lines().toList();
        int seen = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("<dependency>")) {
                seen++;
                if (seen == occurrence) {
                    return i + 1;
                }
            }
        }
        throw new IllegalArgumentException("样例 pom 里没有第 " + occurrence + " 个 dependency 块");
    }

    private static List<AuditIssue> issuesOf(List<AuditIssue> issues, String ruleId) {
        return issues.stream().filter(issue -> issue.ruleId().equals(ruleId)).toList();
    }

    private static AuditIssue find(List<AuditIssue> issues, String ruleId, String coordinate) {
        return issuesOf(issues, ruleId).stream()
                .filter(issue -> issue.trigger().contains(coordinate))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "没检出 " + ruleId + " 命中 " + coordinate + " 的问题；实际: "
                                + issues.stream().map(AuditIssue::describe).toList()));
    }

    // ---- 三类判定 ----

    @Test
    @DisplayName("三类冲突各自检出，等级分明，行号落在块首")
    void detectsAllThreeKinds() {
        List<AuditIssue> issues = analyze();

        // ① 同一 pom 内 dup-lib 1.0 与 2.0：真冲突 → 高危，锚在第二处声明
        AuditIssue samePom = find(issues, "CW-DEP-001", "dup-lib");
        assertThat(samePom.riskLevel()).isEqualTo(AuditIssue.RiskLevel.HIGH);
        assertThat(samePom.filePath()).isEqualTo("module-c/pom.xml");
        assertThat(samePom.line()).isEqualTo(dependencyBlockLine(MODULE_C_POM, 2));
        assertThat(samePom.description()).contains("同一 pom");

        // ② 跨模块 shared-lib 1.0 与 2.0：只是声明不一致 → 中危，措辞不许说成冲突
        AuditIssue crossModule = find(issues, "CW-DEP-001", "shared-lib");
        assertThat(crossModule.riskLevel()).isEqualTo(AuditIssue.RiskLevel.MEDIUM);
        assertThat(crossModule.description()).contains("不一致").doesNotContain("冲突");
        assertThat(crossModule.riskDescription()).contains("本工具判不出");

        // ③ 同一 pom 内重复声明 slf4j-api → 低危，锚在冗余的那一处
        List<AuditIssue> duplicates = issuesOf(issues, "CW-DEP-002");
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.get(0).riskLevel()).isEqualTo(AuditIssue.RiskLevel.LOW);
        assertThat(duplicates.get(0).line()).isEqualTo(dependencyBlockLine(MODULE_A_POM, 3));
    }

    @Test
    @DisplayName("坐标无效：缺 groupId、版本引用不存在的属性")
    void detectsInvalidDeclarations() {
        List<AuditIssue> invalid = issuesOf(analyze(), "CW-DEP-003");

        assertThat(invalid).hasSize(2);
        assertThat(invalid).extracting(AuditIssue::description)
                .contains("依赖声明缺少 groupId", "依赖版本引用的属性不存在");
        assertThat(invalid).allSatisfy(issue -> {
            assertThat(issue.riskLevel()).isEqualTo(AuditIssue.RiskLevel.MEDIUM);
            assertThat(issue.category()).isEqualTo(AuditIssue.IssueCategory.DEPENDENCY);
            assertThat(issue.filePath()).isEqualTo("module-c/pom.xml");
        });

        AuditIssue missingGroupId = invalid.stream()
                .filter(issue -> issue.description().contains("groupId")).findFirst().orElseThrow();
        assertThat(missingGroupId.line()).isEqualTo(dependencyBlockLine(MODULE_C_POM, 3));

        AuditIssue unresolvedVersion = invalid.stream()
                .filter(issue -> issue.description().contains("属性")).findFirst().orElseThrow();
        assertThat(unresolvedVersion.line()).isEqualTo(dependencyBlockLine(MODULE_C_POM, 4));
        assertThat(unresolvedVersion.trigger()).contains("${undefined.version}");
    }

    // ---- 冲突路径 ----

    @Test
    @DisplayName("冲突给出路径：触发场景里含每一处声明的 文件:行号")
    void conflictsCarryEveryLocation() {
        List<AuditIssue> issues = analyze();

        AuditIssue crossModule = find(issues, "CW-DEP-001", "shared-lib");
        assertThat(crossModule.trigger())
                .contains("module-a/pom.xml:" + dependencyBlockLine(MODULE_A_POM, 1))
                .contains("module-b/pom.xml:" + dependencyBlockLine(MODULE_B_POM, 1))
                .contains("shared-lib:1.0")
                .contains("shared-lib:2.0");

        AuditIssue samePom = find(issues, "CW-DEP-001", "dup-lib");
        assertThat(samePom.trigger())
                .contains("module-c/pom.xml:" + dependencyBlockLine(MODULE_C_POM, 1))
                .contains("module-c/pom.xml:" + dependencyBlockLine(MODULE_C_POM, 2))
                .contains("dup-lib:1.0")
                .contains("dup-lib:2.0");
    }

    // ---- 不误报 ----

    @Test
    @DisplayName("干净模块、parent pom 都不产出问题")
    void cleanModuleProducesNothing() {
        List<AuditIssue> issues = analyze();

        assertThat(issues).noneMatch(issue -> issue.filePath().equals("module-clean/pom.xml"));
        assertThat(issues).noneMatch(issue -> issue.trigger().contains("clean-lib"));
        // parent pom 只有 modules 声明，没有 dependency，不该被当成依赖
        assertThat(issues).noneMatch(issue -> issue.filePath().equals("pom.xml"));
    }

    @Test
    @DisplayName("跨模块同版本不算冲突：多模块工程每个模块都声明同样的依赖是常态")
    void sameVersionAcrossModulesIsFine() {
        Map<String, String> poms = new LinkedHashMap<>();
        poms.put("a/pom.xml", moduleWith("a", "common-lib", "1.0"));
        poms.put("b/pom.xml", moduleWith("b", "common-lib", "1.0"));
        poms.put("c/pom.xml", moduleWith("c", "common-lib", "1.0"));

        assertThat(analyzer.analyze(poms)).isEmpty();
    }

    @Test
    @DisplayName("属性引用能解析时不报无效：只报回查不到的")
    void resolvedPropertyIsNotInvalid() {
        Map<String, String> poms = Map.of("pom.xml", """
                <project>
                    <artifactId>demo</artifactId>
                    <properties>
                        <slf4j.version>2.0.9</slf4j.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>${slf4j.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        assertThat(analyzer.analyze(poms)).isEmpty();
    }

    private static String moduleWith(String artifactId, String dependency, String version) {
        return """
                <project>
                    <artifactId>%s</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>%s</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(artifactId, dependency, version);
    }

    // ---- 健壮性与输出 ----

    @Nested
    @DisplayName("健壮性与输出")
    class Robustness {

        @Test
        @DisplayName("空输入不炸")
        void handlesEmptyInput() {
            assertThat(analyzer.analyze(Map.of())).isEmpty();
            assertThat(analyzer.analyze(Map.of("pom.xml", ""))).isEmpty();
            assertThat(analyzer.analyze(Map.of("pom.xml", "<project/>"))).isEmpty();
        }

        @Test
        @DisplayName("每条问题都齐六个字段（沿用统一问题模型的约束）")
        void issuesSatisfyUnifiedModel() {
            List<AuditIssue> issues = analyze();

            assertThat(issues).isNotEmpty();
            assertThat(issues).allSatisfy(issue -> {
                assertThat(issue.ruleId()).isNotEmpty();
                assertThat(issue.description()).isNotEmpty();
                assertThat(issue.trigger()).isNotEmpty();
                assertThat(issue.riskDescription()).isNotEmpty();
                assertThat(issue.riskLevel()).isNotNull();
                assertThat(issue.filePath()).isNotEmpty();
                assertThat(issue.line()).isPositive();
            });
        }

        @Test
        @DisplayName("输出按 文件 → 行号 → 规则 ID 稳定排序，与入参顺序无关")
        void outputIsOrderedAndStable() {
            List<AuditIssue> issues = analyze();
            assertThat(issues).isEqualTo(analyze());

            List<AuditIssue> sorted = issues.stream()
                    .sorted(Comparator.comparing(AuditIssue::filePath)
                            .thenComparingInt(AuditIssue::line)
                            .thenComparing(AuditIssue::ruleId))
                    .toList();
            assertThat(issues).isEqualTo(sorted);

            // 倒着传进去，结果必须一样
            Map<String, String> original = multiModuleProject();
            Map<String, String> reversed = new LinkedHashMap<>();
            List<String> keys = new ArrayList<>(original.keySet());
            Collections.reverse(keys);
            keys.forEach(key -> reversed.put(key, original.get(key)));

            assertThat(analyzer.analyze(reversed)).isEqualTo(issues);
        }
    }
}
