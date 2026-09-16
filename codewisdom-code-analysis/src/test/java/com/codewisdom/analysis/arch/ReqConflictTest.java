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
 * T-503 验收：{@code requirements.txt} 依赖冲突检测。
 *
 * <h2>与 Maven 侧是两套语义</h2>
 * 这一卡的断言刻意<b>不是</b> T-502 的翻版，因为 pip 有三处不同的地方，每一处都有对应的用例：
 * <ol>
 *   <li><b>环境标记</b>：{@code pydantic==2.7.1 ; python_version &lt; "3.9"} 与
 *       {@code pydantic==2.9.0 ; python_version &gt;= "3.9"} 是 pip 的<b>标准写法</b>，
 *       不是冲突。有一条<b>负向断言</b>锁定「带标记的不参与判定」这个刻意选择。</li>
 *   <li><b>单等号 {@code =}</b>：{@code django=4.2} 从 setup.py 抄过来，pip 会直接拒绝安装。
 *       这不是「宽松但能用」，是装不上，必须报出来。</li>
 *   <li><b>PEP 503 归一化</b>：{@code SQLAlchemy} 与 {@code sqlalchemy} 是同一个包，
 *       不做归一化会把同一份依赖当成两条。</li>
 * </ol>
 *
 * <h2>范围与口径</h2>
 * <b>只比对 {@code ==} 钉死的精确版本</b>——区间约束之间能否同时满足是区间求解问题，本卡不做。
 * 取舍依据：requirements.txt 的主流形态就是全钉版本（pip-tools 编译产物），钉版本之间的冲突
 * 正是最常见也最该报的。不跟随 {@code -r}/{@code -c} 包含的文件，不解析 {@code constraints.txt}。
 * 结论一律表述为「<b>声明</b>层面存在冲突」。
 */
@DisplayName("T-503 requirements 依赖冲突检测")
class ReqConflictTest {

    /** 行号锚点：① fastapi ② fastapi（重复）③ uvicorn ④ SQLAlchemy ⑤ requests ⑥ requests ⑦⑧ pydantic ⑨ django */
    private static final String REQUIREMENTS = """
            # Web 框架
            fastapi==0.115.0
            fastapi==0.115.0
            uvicorn>=0.30.0
            SQLAlchemy==2.0.30
            requests==2.31.0
            requests==2.32.0
            pydantic==2.7.1 ; python_version < "3.9"
            pydantic==2.9.0 ; python_version >= "3.9"
            django=4.2
            -r base.txt
            -c constraints.txt
            --index-url https://private.example.com/simple
            """;

    /** 与 REQUIREMENTS 对 SQLAlchemy 的版本不一致；pytest 只在这里出现。 */
    private static final String REQUIREMENTS_DEV = """
            -r requirements.txt
            SQLAlchemy==2.0.20
            pytest==8.2.0
            """;

    private final RequirementsConflictAnalyzer analyzer = new RequirementsConflictAnalyzer();

    private static Map<String, String> project() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("requirements.txt", REQUIREMENTS);
        files.put("requirements-dev.txt", REQUIREMENTS_DEV);
        return files;
    }

    private List<AuditIssue> analyze() {
        return analyzer.analyze(project());
    }

    /** 按文件原文里的标记反查行号，避免写死行号常量。 */
    private static int lineOf(String content, String marker) {
        List<String> lines = content.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(marker)) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("样例文件里找不到标记: " + marker);
    }

    /** 第 occurrence 次出现的行号，用于定位重复声明。 */
    private static int nthLineOf(String content, String marker, int occurrence) {
        List<String> lines = content.lines().toList();
        int seen = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(marker)) {
                seen++;
                if (seen == occurrence) {
                    return i + 1;
                }
            }
        }
        throw new IllegalArgumentException("样例文件里找不到第 " + occurrence + " 处: " + marker);
    }

    private static List<AuditIssue> issuesOf(List<AuditIssue> issues, String ruleId) {
        return issues.stream().filter(issue -> issue.ruleId().equals(ruleId)).toList();
    }

    private static AuditIssue find(List<AuditIssue> issues, String ruleId, String marker) {
        return issuesOf(issues, ruleId).stream()
                .filter(issue -> issue.trigger().contains(marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "没检出 " + ruleId + " 命中 " + marker + "；实际: "
                                + issues.stream().map(AuditIssue::describe).toList()));
    }

    // ---- 三类判定 ----

    @Test
    @DisplayName("三类问题各自检出，等级分明")
    void detectsAllThreeKinds() {
        List<AuditIssue> issues = analyze();

        // 同一文件内 requests 钉了 2.31.0 与 2.32.0：pip 取后者，前者静默失效 → 高危
        AuditIssue sameFile = find(issues, "CW-REQ-001", "requests");
        assertThat(sameFile.riskLevel()).isEqualTo(AuditIssue.RiskLevel.HIGH);
        assertThat(sameFile.filePath()).isEqualTo("requirements.txt");
        assertThat(sameFile.line()).isEqualTo(lineOf(REQUIREMENTS, "requests==2.32.0"));

        // 跨文件 SQLAlchemy 2.0.30 与 2.0.20：只是声明不一致 → 中危，措辞分开
        AuditIssue crossFile = find(issues, "CW-REQ-001", "sqlalchemy");
        assertThat(crossFile.riskLevel()).isEqualTo(AuditIssue.RiskLevel.MEDIUM);
        assertThat(crossFile.description()).contains("不一致").doesNotContain("冲突");
        assertThat(crossFile.riskDescription()).contains("是合法的").contains("本工具判不出");

        // 同一文件内 fastapi 同约束重复 → 低危，锚在冗余的那一处
        List<AuditIssue> duplicates = issuesOf(issues, "CW-REQ-002");
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.get(0).riskLevel()).isEqualTo(AuditIssue.RiskLevel.LOW);
        assertThat(duplicates.get(0).line()).isEqualTo(nthLineOf(REQUIREMENTS, "fastapi==", 2));
    }

    @Test
    @DisplayName("单等号 = 判为无效操作符：pip 会拒绝安装，不是「宽松但能用」")
    void detectsInvalidOperator() {
        List<AuditIssue> invalid = issuesOf(analyze(), "CW-REQ-003");

        assertThat(invalid).singleElement().satisfies(issue -> {
            assertThat(issue.description()).contains("操作符非法");
            assertThat(issue.riskLevel()).isEqualTo(AuditIssue.RiskLevel.MEDIUM);
            assertThat(issue.trigger()).contains("django=4.2");
            assertThat(issue.line()).isEqualTo(lineOf(REQUIREMENTS, "django=4.2"));
        });
    }

    @Test
    @DisplayName("PEP 503 归一化：SQLAlchemy 与 sqlalchemy 是同一个包")
    void normalizesPackageNamesPerPep503() {
        assertThat(com.codewisdom.analysis.domain.PythonRequirement.normalizeName("SQLAlchemy"))
                .isEqualTo("sqlalchemy");
        assertThat(com.codewisdom.analysis.domain.PythonRequirement.normalizeName("zope.interface"))
                .isEqualTo("zope-interface");
        assertThat(com.codewisdom.analysis.domain.PythonRequirement.normalizeName("ruamel_yaml"))
                .isEqualTo("ruamel-yaml");

        // 归一化之后才认得出跨文件不一致
        assertThat(issuesOf(analyze(), "CW-REQ-001")).extracting(AuditIssue::trigger)
                .anyMatch(trigger -> trigger.contains("sqlalchemy"));
    }

    // ---- 不误报 ----

    @Test
    @DisplayName("带环境标记的声明不参与冲突判定（负向断言）")
    void environmentMarkersAreNotConflicts() {
        List<AuditIssue> issues = analyze();

        // pydantic 在两个互斥标记下写了两个版本，这是 pip 的标准写法
        assertThat(issues).noneMatch(issue -> issue.trigger().contains("pydantic"));
        assertThat(issues).noneMatch(issue -> issue.description().contains("pydantic"));
    }

    @Test
    @DisplayName("区间约束不参与判定：>= 拿不到「是哪个版本」")
    void rangeConstraintsAreNotCompared() {
        List<AuditIssue> issues = analyze();

        // uvicorn>=0.30.0 既没参与冲突，也不该被报成无效
        assertThat(issues).noneMatch(issue -> issue.trigger().contains("uvicorn"));
    }

    @Test
    @DisplayName("选项行与注释行完全跳过")
    void skipsOptionAndCommentLines() {
        List<AuditIssue> issues = analyze();

        assertThat(issues).noneMatch(issue -> issue.trigger().contains("-r "))
                .noneMatch(issue -> issue.trigger().contains("-c "))
                .noneMatch(issue -> issue.trigger().contains("--index-url"))
                .noneMatch(issue -> issue.trigger().contains("Web 框架"));
    }

    @Test
    @DisplayName("跨文件同版本不算冲突")
    void sameVersionAcrossFilesIsFine() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("requirements.txt", "requests==2.31.0\n");
        files.put("requirements-dev.txt", "requests==2.31.0\n");

        assertThat(analyzer.analyze(files)).isEmpty();
    }

    @Test
    @DisplayName("extras 与行内注释不影响解析")
    void handlesExtrasAndInlineComments() {
        Map<String, String> files = Map.of("requirements.txt", """
                requests[security,socks]==2.31.0
                uvicorn[standard]>=0.30.0  # 需要标准版
                """);

        assertThat(analyzer.analyze(files)).isEmpty();
    }

    // ---- 冲突路径 ----

    @Test
    @DisplayName("冲突给出路径：触发场景里含每一处声明的 文件:行号")
    void conflictsCarryEveryLocation() {
        List<AuditIssue> issues = analyze();

        AuditIssue crossFile = find(issues, "CW-REQ-001", "sqlalchemy");
        assertThat(crossFile.trigger())
                .contains("requirements.txt:" + lineOf(REQUIREMENTS, "SQLAlchemy==2.0.30"))
                .contains("requirements-dev.txt:" + lineOf(REQUIREMENTS_DEV, "SQLAlchemy==2.0.20"))
                .contains("sqlalchemy==2.0.30")
                .contains("sqlalchemy==2.0.20");

        AuditIssue sameFile = find(issues, "CW-REQ-001", "requests");
        assertThat(sameFile.trigger())
                .contains("requirements.txt:" + lineOf(REQUIREMENTS, "requests==2.31.0"))
                .contains("requirements.txt:" + lineOf(REQUIREMENTS, "requests==2.32.0"))
                .contains("requests==2.31.0")
                .contains("requests==2.32.0");
    }

    // ---- 健壮性与输出 ----

    @Nested
    @DisplayName("健壮性与输出")
    class Robustness {

        @Test
        @DisplayName("空输入不炸")
        void handlesEmptyInput() {
            assertThat(analyzer.analyze(Map.of())).isEmpty();
            assertThat(analyzer.analyze(Map.of("requirements.txt", ""))).isEmpty();
            assertThat(analyzer.analyze(Map.of("requirements.txt", "\n\n# 只有注释\n"))).isEmpty();
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
                assertThat(issue.category()).isEqualTo(AuditIssue.IssueCategory.DEPENDENCY);
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

            Map<String, String> original = project();
            Map<String, String> reversed = new LinkedHashMap<>();
            List<String> keys = new ArrayList<>(original.keySet());
            Collections.reverse(keys);
            keys.forEach(key -> reversed.put(key, original.get(key)));

            assertThat(analyzer.analyze(reversed)).isEqualTo(issues);
        }
    }
}
