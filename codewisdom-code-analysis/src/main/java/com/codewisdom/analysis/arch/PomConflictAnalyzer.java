package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.domain.DependencyCoordinate;
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
 * POM 依赖冲突检测。
 *
 * <h2>为什么不是 {@code AuditRule}</h2>
 * {@link AuditRule} 的上下文绑定在「已解析的 Java 源文件句柄」上，而这里输入的是 pom 文本、
 * 还没有语法树。强行塞进去只能把上下文改成「什么文件都能塞」的模糊结构，
 * 两边都变差。因此本类是一个独立分析器，<b>但产出同一个 {@link AuditIssue}</b>——
 * {@code acceptance.md} 要求的是「统一问题模型」，不是「统一下所有分析器」。
 *
 * <h2>检测的是「声明级」冲突，不是运行时冲突</h2>
 * 这条例外必须说死，否则报告会被当成依赖仲裁结果：
 * <ul>
 *   <li><b>不解析传递依赖</b>：A 依赖 B、B 依赖 C-1.0，而工程里直接声明 C-2.0 ——
 *       这种最常见的 Maven 冲突<b>检不出来</b>，那需要真正的依赖解析器。</li>
 *   <li><b>不看 {@code <dependencyManagement>}</b>：被管理的版本决定实际生效版本，
 *       但这里只比对「明写出来的版本」。</li>
 *   <li><b>不看 {@code <exclusions>} 与 {@code <profiles>}</b>。</li>
 * </ul>
 * 所以结论一律表述为「<b>声明</b>层面存在冲突」，不是「运行时一定会冲突」。
 *
 * <h2>三类判定，等级不同</h2>
 * <table>
 *   <tr><th>判定</th><th>等级</th><th>为什么是这个等级</th></tr>
 *   <tr><td>同一 pom 内同坐标多个版本</td><td>高危</td>
 *       <td>Maven 按「最近优先」裁决后<b>另一个版本静默失效</b>，
 *           源码里写着 2.0、实际加载 1.0，排查时极具误导性</td></tr>
 *   <tr><td>跨模块同坐标版本不一致</td><td>中危</td>
 *       <td>两个独立模块各用各的版本<b>是合法的</b>。只有当它们共享 classpath 时才成问题，
 *           而本工具判不出是否共享——所以报为「声明不一致」，让人去确认，不冒充「冲突」</td></tr>
 *   <tr><td>同一 pom 内重复声明</td><td>低危</td>
 *       <td>冗余但通常无害（同版本时）；不同版本的重复会同时被版本冲突规则报出</td></tr>
 *   <tr><td>坐标无效</td><td>中危</td>
 *       <td>缺 groupId 或版本引用了不存在的属性，声明无法生效</td></tr>
 * </table>
 */
@Component
public class PomConflictAnalyzer {

    private static final String RULE_VERSION_CONFLICT = "CW-DEP-001";
    private static final String RULE_DUPLICATE = "CW-DEP-002";
    private static final String RULE_INVALID = "CW-DEP-003";

    /**
     * 检测一批 pom。
     *
     * @param pomFilesByPath pom 文件路径 → 文件原文
     * @return 按「文件 → 行号 → 规则 ID」排序的问题列表
     */
    public List<AuditIssue> analyze(Map<String, String> pomFilesByPath) {
        List<DependencyCoordinate> dependencies = new ArrayList<>();
        // TreeMap 保证文件按字典序遍历，输出顺序与入参 Map 的实现无关
        new TreeMap<>(pomFilesByPath)
                .forEach((path, content) -> dependencies.addAll(
                        PomDependencyReader.readDependencies(path, content)));

        List<AuditIssue> issues = new ArrayList<>();
        issues.addAll(detectInvalid(dependencies));
        issues.addAll(detectDuplicates(dependencies, samePomConflictKeys(dependencies)));
        issues.addAll(detectVersionConflicts(dependencies));

        issues.sort(Comparator.comparing(AuditIssue::filePath)
                .thenComparingInt(AuditIssue::line)
                .thenComparing(AuditIssue::ruleId));
        return List.copyOf(issues);
    }

    /** 同一坐标在同一文件内的分组键：{@code 文件路径|groupId:artifactId}。 */
    private static String scopeKey(DependencyCoordinate dependency) {
        return dependency.filePath() + "|" + dependency.id();
    }

    private static Map<String, List<DependencyCoordinate>> groupByScope(
            List<DependencyCoordinate> dependencies) {
        Map<String, List<DependencyCoordinate>> grouped = new LinkedHashMap<>();
        for (DependencyCoordinate dependency : dependencies) {
            grouped.computeIfAbsent(scopeKey(dependency), key -> new ArrayList<>()).add(dependency);
        }
        return grouped;
    }

    /** 同一文件内出现多个版本的坐标。「重复声明」要避开它们，理由见 {@link #detectDuplicates}。 */
    private static Set<String> samePomConflictKeys(List<DependencyCoordinate> dependencies) {
        Set<String> keys = new LinkedHashSet<>();
        groupByScope(dependencies).forEach((key, group) -> {
            if (distinctResolvedVersions(group).size() >= 2) {
                keys.add(key);
            }
        });
        return keys;
    }

    // ---- 坐标无效 ----

    private static List<AuditIssue> detectInvalid(List<DependencyCoordinate> dependencies) {
        List<AuditIssue> issues = new ArrayList<>();
        for (DependencyCoordinate dependency : dependencies) {
            if (dependency.groupId() == null) {
                issues.add(issue(RULE_INVALID, AuditIssue.RiskLevel.MEDIUM, dependency,
                        "依赖声明缺少 groupId",
                        dependency.describe(),
                        "缺少 groupId 的依赖无法被 Maven 解析，构建会直接失败或该依赖被静默忽略"));
            } else if (dependency.hasUnresolvedVersion()) {
                issues.add(issue(RULE_INVALID, AuditIssue.RiskLevel.MEDIUM, dependency,
                        "依赖版本引用的属性不存在",
                        dependency.describe() + " @ " + dependency.location(),
                        "版本写成了 ${...} 引用但对应的 <properties> 项不存在，"
                                + "实际生效版本取决于父 pom 或干脆无法解析，等于版本不可控"));
            }
        }
        return issues;
    }

    // ---- 重复声明（同一 pom 内） ----

    /**
     * 同一 pom 内重复声明。
     *
     * <p><b>同坐标已有多版本冲突的，这里不再单独报</b>：那种情况下高危冲突条目已经说了
     * 「声明了多个版本」，再补一条低危的「重复声明」只是同一处代码刷两条，稀释重点。
     * 只有<b>同版本</b>的重复（纯冗余、删一行即可）才落到这条规则上。
     *
     * @param conflictingScopes 已有多版本冲突的 {@code 文件|坐标} 键，跳过
     */
    private static List<AuditIssue> detectDuplicates(List<DependencyCoordinate> dependencies,
                                                     Set<String> conflictingScopes) {
        List<AuditIssue> issues = new ArrayList<>();
        for (Map.Entry<String, List<DependencyCoordinate>> entry
                : groupByScope(dependencies).entrySet()) {
            List<DependencyCoordinate> group = entry.getValue();
            if (group.size() < 2 || conflictingScopes.contains(entry.getKey())) {
                continue;
            }
            // 锚定在第二处声明上：第一处是原始声明，冗余的是它
            DependencyCoordinate redundant = group.get(1);
            issues.add(new AuditIssue(RULE_DUPLICATE, AuditIssue.IssueCategory.DEPENDENCY,
                    AuditIssue.RiskLevel.LOW, redundant.filePath(), redundant.line(),
                    "同一 pom 内重复声明依赖 " + redundant.id(),
                    locations(group),
                    "重复声明是纯冗余，删掉多余的即可；Maven 会取其中一处，"
                            + "留着会让后来者分不清哪一处才是「正版」"));
        }
        return issues;
    }

    // ---- 版本冲突 ----

    private static List<AuditIssue> detectVersionConflicts(List<DependencyCoordinate> dependencies) {
        Map<String, List<DependencyCoordinate>> byId = new LinkedHashMap<>();
        for (DependencyCoordinate dependency : dependencies) {
            byId.computeIfAbsent(dependency.id(), key -> new ArrayList<>()).add(dependency);
        }

        List<AuditIssue> issues = new ArrayList<>();
        for (List<DependencyCoordinate> group : byId.values()) {
            if (distinctResolvedVersions(group).size() < 2) {
                continue;
            }
            List<AuditIssue> conflicts = samePomConflicts(group);
            if (!conflicts.isEmpty()) {
                issues.addAll(conflicts);
                continue;
            }
            issues.add(crossModuleDivergence(group));
        }
        return issues;
    }

    /** 同一个 pom 内出现多个版本：这是真冲突，Maven 会裁决掉其中一个。 */
    private static List<AuditIssue> samePomConflicts(List<DependencyCoordinate> group) {
        Map<String, List<DependencyCoordinate>> byFile = group.stream()
                .collect(Collectors.groupingBy(DependencyCoordinate::filePath, LinkedHashMap::new,
                        Collectors.toList()));

        List<AuditIssue> issues = new ArrayList<>();
        byFile.forEach((filePath, declarations) -> {
            if (distinctResolvedVersions(declarations).size() < 2) {
                return;
            }
            DependencyCoordinate anchor = declarations.get(1);
            issues.add(new AuditIssue(RULE_VERSION_CONFLICT, AuditIssue.IssueCategory.DEPENDENCY,
                    AuditIssue.RiskLevel.HIGH, filePath, anchor.line(),
                    "同一 pom 内 " + anchor.id() + " 声明了多个版本",
                    locations(declarations),
                    "Maven 按「最近优先」裁决后只有一个版本生效，其余<b>静默失效</b>——"
                            + "源码里写着 2.0、实际加载 1.0，排查时极具误导性。"
                            + "应统一为一个版本，或用 <dependencyManagement> 集中管理"));
        });
        return issues;
    }

    /** 跨模块版本不一致：合法但通常是隐患，措辞必须与真冲突区分开。 */
    private static AuditIssue crossModuleDivergence(List<DependencyCoordinate> group) {
        DependencyCoordinate anchor = group.get(0);
        return new AuditIssue(RULE_VERSION_CONFLICT, AuditIssue.IssueCategory.DEPENDENCY,
                AuditIssue.RiskLevel.MEDIUM, anchor.filePath(), anchor.line(),
                "跨模块 " + anchor.id() + " 的版本声明不一致",
                locations(group),
                "各模块独立声明不同版本。仅当这些模块共享同一个运行时 classpath 时才会真正冲突，"
                        + "本工具判不出是否共享——请先确认，再由父 pom 的 <dependencyManagement> 统一版本");
    }

    // ---- 工具 ----

    private static Set<String> distinctResolvedVersions(List<DependencyCoordinate> group) {
        Set<String> versions = new LinkedHashSet<>();
        for (DependencyCoordinate dependency : group) {
            if (dependency.hasResolvedVersion()) {
                versions.add(dependency.version());
            }
        }
        return versions;
    }

    /** 把同一坐标的所有位置拼成一行，这就是「冲突路径」。 */
    private static String locations(List<DependencyCoordinate> group) {
        return group.stream()
                .map(DependencyCoordinate::describeWithLocation)
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    private static AuditIssue issue(String ruleId, AuditIssue.RiskLevel level,
                                    DependencyCoordinate dependency,
                                    String description, String trigger, String risk) {
        return new AuditIssue(ruleId, AuditIssue.IssueCategory.DEPENDENCY, level,
                dependency.filePath(), dependency.line(), description, trigger, risk);
    }
}
