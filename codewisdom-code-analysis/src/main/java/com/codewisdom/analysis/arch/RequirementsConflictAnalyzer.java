package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.domain.PythonRequirement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Python 依赖冲突检测（{@code requirements.txt} 侧）。
 *
 * <h2>与 Maven 侧是两套语义，不是同一套规则换个文件</h2>
 * 三个必须分开处理的差异：
 * <ol>
 *   <li><b>环境标记（{@code ; python_version &lt; "3.9"}）</b>——Maven 没有对应的东西
 *       （{@code profiles} 是按构建环境激活，语义不同）。同一条依赖在不同标记下写不同版本
 *       是 pip 的<b>标准写法</b>，不是冲突。因此：<b>带标记的声明一律不参与冲突判定</b>——
 *       两个标记是否互斥需要求解标记表达式，本工具不做，不判就不会误报。</li>
 *   <li><b>区间约束（{@code >=1.0,&lt;2.0}）</b>——两个区间能否同时满足是个区间求解问题。
 *       本卡<b>只比对 {@code ==} 钉死的精确版本</b>。这个取舍损失不大：
 *       requirements.txt 的主流形态就是全钉版本（pip-tools 编译产物、导出锁文件），
 *       <b>钉版本之间的冲突正是最常见也最该报的那种</b>。</li>
 *   <li><b>包名归一化（PEP 503）</b>——{@code zope.interface} 与 {@code zope-interface} 是同一个包。
 *       不做归一化会把同一份依赖当成两条。</li>
 * </ol>
 *
 * <h2>范围外事项（写清楚，免得报告被当成 pip 的依赖解析结果）</h2>
 * <ul>
 *   <li><b>不跟随 {@code -r} / {@code -c} 包含的文件</b>：每个文件独立分析，
 *       包含关系由调用方把文件给全。</li>
 *   <li><b>不解析 {@code constraints.txt} 对版本的约束</b>。</li>
 *   <li><b>不解析 extras 对传递依赖的影响</b>（{@code pkg[extra]} 会拉进额外的依赖）。</li>
 * </ul>
 * 结论一律表述为「<b>声明</b>层面存在冲突」。
 *
 * <h2>等级</h2>
 * <table>
 *   <tr><th>判定</th><th>等级</th><th>理由</th></tr>
 *   <tr><td>同一文件内同包钉了两个版本</td><td>高危</td>
 *       <td>pip 取后者，前一处<b>静默失效</b>——文件里写着 2.31，装出来的是 2.32</td></tr>
 *   <tr><td>跨文件同包版本不一致</td><td>中危</td>
 *       <td>两个文件各自装各自的<b>是合法的</b>（如运行时依赖与开发依赖分文件）；
 *           只有装进同一环境才会冲突，本工具判不出，所以报「声明不一致」</td></tr>
 *   <tr><td>同一文件内同包同约束重复</td><td>低危</td><td>纯冗余，删一行即可</td></tr>
 *   <tr><td>包名解析不出 / 操作符非法</td><td>中危</td>
 *       <td>pip 会拒绝安装，例如 {@code django=4.2} 这种从 setup.py 抄来的单等号写法</td></tr>
 * </table>
 */
@Component
public class RequirementsConflictAnalyzer {

    private static final String RULE_VERSION_CONFLICT = "CW-REQ-001";
    private static final String RULE_DUPLICATE = "CW-REQ-002";
    private static final String RULE_INVALID = "CW-REQ-003";

    /**
     * 检测一批 requirements 文件。
     *
     * @param requirementFilesByPath 文件路径 → 文件原文
     * @return 按「文件 → 行号 → 规则 ID」排序的问题列表
     */
    public List<AuditIssue> analyze(Map<String, String> requirementFilesByPath) {
        List<PythonRequirement> requirements = new ArrayList<>();
        // TreeMap 保证文件按字典序遍历，输出顺序与入参 Map 的实现无关
        new TreeMap<>(requirementFilesByPath)
                .forEach((path, content) -> requirements.addAll(
                        RequirementsReader.read(path, content)));

        List<AuditIssue> issues = new ArrayList<>();
        issues.addAll(detectInvalid(requirements));
        issues.addAll(detectDuplicates(requirements));
        issues.addAll(detectPinnedConflicts(requirements));

        issues.sort(Comparator.comparing(AuditIssue::filePath)
                .thenComparingInt(AuditIssue::line)
                .thenComparing(AuditIssue::ruleId));
        return List.copyOf(issues);
    }

    // ---- 无效声明 ----

    private static List<AuditIssue> detectInvalid(List<PythonRequirement> requirements) {
        List<AuditIssue> issues = new ArrayList<>();
        for (PythonRequirement requirement : requirements) {
            if (!requirement.isParsed()) {
                issues.add(invalid(requirement, "依赖声明解析不出包名",
                        "该行不是合法的 requirement，pip 会直接报 Invalid requirement 并拒绝安装"));
            } else if (!RequirementsReader.isValidConstraint(requirement.constraint())) {
                issues.add(invalid(requirement, "依赖声明的版本操作符非法",
                        "合法操作符只有 == != <= >= < > ~=；" + requirement.constraint() + " 不是其中任何一种。"
                                + "「单等号 =」这种写法多半是从 setup.py 抄来的，pip 会拒绝安装"));
            }
        }
        return issues;
    }

    private static AuditIssue invalid(PythonRequirement requirement, String description, String risk) {
        return new AuditIssue(RULE_INVALID, AuditIssue.IssueCategory.DEPENDENCY,
                AuditIssue.RiskLevel.MEDIUM, requirement.filePath(), requirement.line(),
                description, requirement.raw(), risk);
    }

    // ---- 重复声明（同一文件内、同一约束） ----

    private static List<AuditIssue> detectDuplicates(List<PythonRequirement> requirements) {
        Map<String, List<PythonRequirement>> grouped = new LinkedHashMap<>();
        for (PythonRequirement requirement : requirements) {
            if (!requirement.isParsed()) {
                continue;
            }
            grouped.computeIfAbsent(requirement.filePath() + "|" + requirement.name()
                            + "|" + requirement.constraint(),
                    key -> new ArrayList<>()).add(requirement);
        }

        List<AuditIssue> issues = new ArrayList<>();
        for (List<PythonRequirement> group : grouped.values()) {
            if (group.size() < 2) {
                continue;
            }
            // 锚定在第二处：第一处是原始声明，冗余的是它
            PythonRequirement redundant = group.get(1);
            issues.add(new AuditIssue(RULE_DUPLICATE, AuditIssue.IssueCategory.DEPENDENCY,
                    AuditIssue.RiskLevel.LOW, redundant.filePath(), redundant.line(),
                    "同一文件内重复声明依赖 " + redundant.name() + redundant.constraint(),
                    locations(group),
                    "重复声明是纯冗余，删掉多余的即可；留着会让后来者分不清哪一处才是「正版」"));
        }
        return issues;
    }

    // ---- 钉版本冲突 ----

    /**
     * 比对 {@code ==} 钉死的精确版本。
     *
     * <p><b>带环境标记的声明在这里被整体排除</b>，理由见类注释：不同标记下写不同版本是 pip 的
     * 标准写法，判它冲突全是误报。
     */
    private static List<AuditIssue> detectPinnedConflicts(List<PythonRequirement> requirements) {
        Map<String, List<PythonRequirement>> pinnedByName = new LinkedHashMap<>();
        for (PythonRequirement requirement : requirements) {
            if (requirement.isPinned() && !requirement.hasMarker()) {
                pinnedByName.computeIfAbsent(requirement.name(), key -> new ArrayList<>()).add(requirement);
            }
        }

        List<AuditIssue> issues = new ArrayList<>();
        pinnedByName.forEach((name, group) -> {
            if (distinctVersions(group).size() < 2) {
                return;
            }
            List<AuditIssue> sameFile = sameFileConflicts(name, group);
            if (!sameFile.isEmpty()) {
                issues.addAll(sameFile);
                return;
            }
            issues.add(crossFileDivergence(name, group));
        });
        return issues;
    }

    /** 同一文件内同包钉了两个版本：pip 取后者，前一处静默失效。 */
    private static List<AuditIssue> sameFileConflicts(String name, List<PythonRequirement> group) {
        Map<String, List<PythonRequirement>> byFile = group.stream()
                .collect(Collectors.groupingBy(PythonRequirement::filePath, LinkedHashMap::new,
                        Collectors.toList()));

        List<AuditIssue> issues = new ArrayList<>();
        byFile.forEach((filePath, declarations) -> {
            if (distinctVersions(declarations).size() < 2) {
                return;
            }
            PythonRequirement anchor = declarations.get(1);
            issues.add(new AuditIssue(RULE_VERSION_CONFLICT, AuditIssue.IssueCategory.DEPENDENCY,
                    AuditIssue.RiskLevel.HIGH, filePath, anchor.line(),
                    "同一文件内 " + name + " 钉了两个版本",
                    locations(declarations),
                    "pip 只会装其中一个，另一处<b>静默失效</b>——文件里写着 2.31、装出来的是 2.32，"
                            + "排查时极具误导性。应统一为一个版本"));
        });
        return issues;
    }

    /** 跨文件版本不一致：合法但通常是隐患，措辞必须与真冲突区分开。 */
    private static AuditIssue crossFileDivergence(String name, List<PythonRequirement> group) {
        PythonRequirement anchor = group.get(0);
        return new AuditIssue(RULE_VERSION_CONFLICT, AuditIssue.IssueCategory.DEPENDENCY,
                AuditIssue.RiskLevel.MEDIUM, anchor.filePath(), anchor.line(),
                "跨文件 " + name + " 的版本声明不一致",
                locations(group),
                "不同文件各自钉了不同版本。运行时依赖与开发依赖分文件时这<b>是合法的</b>，"
                        + "只有装进同一环境才会冲突——本工具判不出，请先确认，再统一版本");
    }

    // ---- 工具 ----

    private static Set<String> distinctVersions(List<PythonRequirement> group) {
        Set<String> versions = new LinkedHashSet<>();
        for (PythonRequirement requirement : group) {
            versions.add(requirement.pinnedVersion());
        }
        return versions;
    }

    /** 把同一包的所有位置拼成一行，这就是「冲突路径」。 */
    private static String locations(List<PythonRequirement> group) {
        return group.stream()
                .map(PythonRequirement::describeWithLocation)
                .distinct()
                .collect(Collectors.joining(" / "));
    }
}
