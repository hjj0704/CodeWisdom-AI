package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.domain.CycleReport;
import com.codewisdom.analysis.domain.DependencyCycle;
import com.codewisdom.analysis.domain.LayerAssignment;
import com.codewisdom.analysis.domain.LayerKind;
import com.codewisdom.analysis.domain.LayerReport;
import com.codewisdom.analysis.domain.TypeDeclaration;
import com.codewisdom.analysis.parser.CallGraph;
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
 * 架构隐患规则。
 *
 * <h2>这一卡在做什么：<b>翻译</b>，不是重新分析</h2>
 * 阶段 4 已经把架构逆向做完了，产物都是结构化数据：
 * <pre>
 * T-401 LayerReport   分层、包结构、信号冲突（conflicts）、包内混杂（mixedPackages）
 * T-404 CycleReport   循环依赖（包级 / 分层级，已给出最短环路径）
 * T-403 TechStackReport 技术栈
 * </pre>
 * 本类<b>不重新解析源码、不重新跑图算法</b>，只是把这些结论翻译成统一的
 * {@link AuditIssue}（{@code acceptance.md} 要求的「统一问题模型」）。
 * 环检测仍然调用 T-404 的 {@link CycleDetector}——那是复用，不是重写。
 *
 * <h2>为什么需要 {@code typesBySourcePath}</h2>
 * {@link AuditIssue} 要求「文件 + 行号」，而 {@link DependencyCycle} 里只有模块名
 * （{@code demo.a} 或 {@code CONTROLLER}），没有位置。调用方本来就为 {@code LayerDetector}
 * 准备了「文件 → 类型声明」这份数据，本类据此建索引，把问题锚到<b>真正参与的那个类</b>上。
 * 不这么做就只能填 {@link AuditIssue#NO_LINE}——架构隐患虽然横跨多个文件，
 * 但「哪个类的声明行」仍然比「没有行号」有用得多。
 *
 * <h2>三类隐患与它们的等级</h2>
 * <table>
 *   <tr><th>规则</th><th>判定</th><th>等级</th><th>理由</th></tr>
 *   <tr><td rowspan="2">{@code CW-ARCH-001} 循环依赖</td>
 *       <td>分层之间的环（{@code controller ↔ service}）</td><td>高危</td>
 *       <td>分层被反过来依赖，架构约束失效——这比「两个包互相用」严重一档</td></tr>
 *   <tr><td>包之间的环</td><td>中危</td><td>强耦合，改动会互相牵连；但不一定违反分层约定</td></tr>
 *   <tr><td rowspan="2">{@code CW-ARCH-002} 分层混乱</td>
 *       <td>单个类的信号自相矛盾</td><td>中危</td><td>{@code @RestController} 落在 service 包里，能找到具体是哪个类</td></tr>
 *   <tr><td>一个包里混了多个角色分层</td><td>低危</td>
 *       <td>真实工程里 {@code common} 包混放 config 与 util <b>是常态</b>，所以只作提示</td></tr>
 *   <tr><td rowspan="2">{@code CW-ARCH-003} 职责不单一</td>
 *       <td>单个包下类型数过多</td><td>中危</td><td>上帝包，定位与维护成本高</td></tr>
 *   <tr><td>单个类被过多类型依赖</td><td>低危</td>
 *       <td>可能是上帝类，也可能本来就是个共享实体——<b>本应被广泛引用的分层已被排除</b>，
 *           剩下的仍需人工确认</td></tr>
 * </table>
 *
 * <h2>口径</h2>
 * 全部结论基于<b>静态结构</b>：调用图来自源码里的调用关系，环不含反射/事件/配置注入产生的
 * 运行期依赖；分层与包结构来自目录约定。输出一律按「辅助分析结果，需人工确认」表述。
 */
@Component
public class ArchRiskAnalyzer {

    /** 循环依赖（包级 / 分层级）。 */
    private static final String RULE_CYCLE = "CW-ARCH-001";

    /** 分层混乱（类级冲突 / 包级混杂）。 */
    private static final String RULE_LAYERING = "CW-ARCH-002";

    /** 职责不单一（包过重 / 疑似上帝类）。 */
    private static final String RULE_RESPONSIBILITY = "CW-ARCH-003";

    /**
     * 单包类型数上限。超过就提示「职责过重」。
     *
     * <p>20 这个数没有理论依据，是经验值：正常分层下一个包放 5~15 个类，
     * 超过 20 基本意味着两层职责被塞进了一个包，或者该拆子包了。
     * <b>它是提示阈值，不是缺陷判定线</b>，所以措辞是「建议拆分」而不是「违反规范」。
     */
    private static final int MAX_TYPES_PER_PACKAGE = 20;

    /** 单个类被多少个「不同」类型依赖就算疑似上帝类。 */
    private static final int MAX_DEPENDENTS = 15;

    /**
     * 上帝类检查排除的分层：这些类型<b>本来就应该被广泛引用</b>，
     * 把它们报成上帝类全是误报。
     */
    private static final Set<LayerKind> WIDELY_USED_BY_DESIGN = Set.of(
            LayerKind.DTO, LayerKind.ENTITY, LayerKind.CONSTANT, LayerKind.UTIL, LayerKind.EXCEPTION);

    private final CycleDetector cycleDetector;

    public ArchRiskAnalyzer(CycleDetector cycleDetector) {
        this.cycleDetector = cycleDetector;
    }

    /**
     * 分析架构隐患。
     *
     * @param layers              分层与包结构报告（T-401）
     * @param callGraph           跨文件调用图（T-304）
     * @param typesBySourcePath   文件路径 → 该文件的类型声明；<b>用于把问题锚到具体行</b>，
     *                            与传给 {@code LayerDetector} 的是同一份数据
     * @return 按「文件 → 行号 → 规则 ID」排序的问题列表
     */
    public List<AuditIssue> analyze(LayerReport layers,
                                    CallGraph callGraph,
                                    Map<String, List<TypeDeclaration>> typesBySourcePath) {
        TypeIndex index = TypeIndex.of(layers, typesBySourcePath);

        List<AuditIssue> issues = new ArrayList<>();
        issues.addAll(detectCycles(layers, callGraph, index));
        issues.addAll(detectLayeringSmells(layers, index));
        issues.addAll(detectResponsibilitySmells(layers, callGraph, index));

        issues.sort(Comparator.comparing(AuditIssue::filePath)
                .thenComparingInt(AuditIssue::line)
                .thenComparing(AuditIssue::ruleId));
        return List.copyOf(issues);
    }

    // ---- CW-ARCH-001 循环依赖 ----

    private List<AuditIssue> detectCycles(LayerReport layers, CallGraph callGraph, TypeIndex index) {
        List<AuditIssue> issues = new ArrayList<>();
        // 两个粒度都报：包环回答「哪两个包要拆」，层环回答「架构分层倒了」
        issues.addAll(toIssues(cycleDetector.detectByLayer(layers, callGraph), callGraph, index,
                AuditIssue.RiskLevel.HIGH, "分层",
                "分层之间形成了环，架构约定被反过来依赖：下层调用上层，分层约束不再成立",
                "应引入中间层或事件解耦，把反向调用消掉；不要用「反正能跑」把它留着"));
        issues.addAll(toIssues(cycleDetector.detectByPackage(layers, callGraph), callGraph, index,
                AuditIssue.RiskLevel.MEDIUM, "包",
                "两个包互相调用形成强耦合：改一个包会牵动另一个，也难以单独复用或测试",
                "应先确认环上哪些调用是真实的业务依赖，再考虑下沉公共部分或引入接口反转"));
        return issues;
    }

    private static List<AuditIssue> toIssues(CycleReport report,
                                             CallGraph callGraph,
                                             TypeIndex index,
                                             AuditIssue.RiskLevel level,
                                             String granularity,
                                             String riskDescription,
                                             String suggestion) {
        List<AuditIssue> issues = new ArrayList<>();
        for (DependencyCycle cycle : report.cycles()) {
            Anchor anchor = cycleAnchor(cycle, callGraph, index);
            issues.add(new AuditIssue(RULE_CYCLE, AuditIssue.IssueCategory.ARCHITECTURE,
                    level, anchor.filePath(), anchor.line(),
                    granularity + "循环依赖：" + cycle.describe(),
                    cycle.describe() + "；涉及文件: " + String.join(", ", cycleFiles(cycle, callGraph, index)),
                    riskDescription + "。" + suggestion));
        }
        return issues;
    }

    /**
     * 把环锚到「环上第一条边所在文件里、属于环首模块的那个类」。
     *
     * <p>优先用调用边自己的 {@code sourcePaths}（那是调用真正发生的文件），
     * 拿不到再退回环首模块的任意一个类。
     */
    private static Anchor cycleAnchor(DependencyCycle cycle, CallGraph callGraph, TypeIndex index) {
        String first = cycle.path().get(0);
        String second = cycle.path().get(1);

        for (CallGraph.TypeEdge edge : callGraph.typeEdges()) {
            if (!edge.fromType().equals(first) || !edge.toType().equals(second)) {
                continue;
            }
            for (String filePath : edge.sourcePaths()) {
                Anchor anchor = index.firstInFileWithModule(filePath, first);
                if (anchor != null) {
                    return anchor;
                }
            }
            Anchor byType = index.ofType(edge.fromType());
            if (byType != null) {
                return byType;
            }
        }
        return index.firstOfModule(first);
    }

    private static List<String> cycleFiles(DependencyCycle cycle, CallGraph callGraph, TypeIndex index) {
        Set<String> files = new LinkedHashSet<>();
        for (int i = 0; i < cycle.length(); i++) {
            String from = cycle.path().get(i);
            String to = cycle.path().get(i + 1);
            for (CallGraph.TypeEdge edge : callGraph.typeEdges()) {
                if (edge.fromType().equals(from) && edge.toType().equals(to)) {
                    files.addAll(edge.sourcePaths());
                }
            }
        }
        if (files.isEmpty()) {
            cycle.members().forEach(module -> {
                Anchor anchor = index.firstOfModule(module);
                if (anchor != null) {
                    files.add(anchor.filePath());
                }
            });
        }
        return List.copyOf(files);
    }

    // ---- CW-ARCH-002 分层混乱 ----

    private static List<AuditIssue> detectLayeringSmells(LayerReport layers, TypeIndex index) {
        List<AuditIssue> issues = new ArrayList<>();

        // 类级：信号自相矛盾，能定位到具体是哪个类放错了
        for (LayerAssignment conflict : layers.conflicts()) {
            Anchor anchor = index.ofType(conflict.typeQualifiedName());
            if (anchor == null) {
                continue;
            }
            issues.add(new AuditIssue(RULE_LAYERING, AuditIssue.IssueCategory.ARCHITECTURE,
                    AuditIssue.RiskLevel.MEDIUM, anchor.filePath(), anchor.line(),
                    conflict.typeQualifiedName() + " 的分层归属自相矛盾",
                    conflict.typeQualifiedName() + "：注解/命名指向 " + signal(conflict.annotationSignal())
                            + "，所在包指向 " + signal(conflict.packageSignal())
                            + "，类名指向 " + signal(conflict.namingSignal())
                            + "（最终判定按 " + conflict.rule() + "）",
                    "类放错了包：调用方按包名找依赖会找错，分层约束也形同虚设。"
                            + "应把类移到与它角色一致的包，或确认包名才是本意后改注解"));
        }

        // 包级：一个包里混了多个角色分层
        layers.mixedPackages().forEach((packageName, kinds) -> {
            Anchor anchor = index.firstOfPackage(packageName);
            if (anchor == null) {
                return;
            }
            issues.add(new AuditIssue(RULE_LAYERING, AuditIssue.IssueCategory.ARCHITECTURE,
                    AuditIssue.RiskLevel.LOW, anchor.filePath(), anchor.line(),
                    "包 " + packageName + " 内混放了多个分层",
                    packageName + " 同时包含: " + kinds.stream().map(Enum::name).collect(Collectors.joining(" / ")),
                    "一个包承担多个分层职责时，「按包找代码」不再成立。"
                            + "注意：工具类与配置类同放 common 包是常见做法，本条只作提示，需人工判断"));
        });
        return issues;
    }

    // ---- CW-ARCH-003 职责不单一 ----

    private static List<AuditIssue> detectResponsibilitySmells(LayerReport layers,
                                                              CallGraph callGraph,
                                                              TypeIndex index) {
        List<AuditIssue> issues = new ArrayList<>();
        issues.addAll(detectOverloadedPackages(layers, index));
        issues.addAll(detectGodClasses(layers, callGraph, index));
        return issues;
    }

    /** 单个包下类型数过多。 */
    private static List<AuditIssue> detectOverloadedPackages(LayerReport layers, TypeIndex index) {
        Map<String, Long> countByPackage = new TreeMap<>();
        layers.assignments().forEach(assignment ->
                countByPackage.merge(assignment.packageName(), 1L, Long::sum));

        List<AuditIssue> issues = new ArrayList<>();
        countByPackage.forEach((packageName, count) -> {
            if (count <= MAX_TYPES_PER_PACKAGE) {
                return;
            }
            Anchor anchor = index.firstOfPackage(packageName);
            if (anchor == null) {
                return;
            }
            issues.add(new AuditIssue(RULE_RESPONSIBILITY, AuditIssue.IssueCategory.ARCHITECTURE,
                    AuditIssue.RiskLevel.MEDIUM, anchor.filePath(), anchor.line(),
                    "包 " + packageName + " 下有 " + count + " 个类型，职责过重",
                    packageName + " 类型数 = " + count + "（提示阈值 " + MAX_TYPES_PER_PACKAGE + "）",
                    "上帝包让定位与维护成本陡增，也说明这一层里可能塞了不止一种职责。"
                            + "建议按子领域拆分子包。阈值是经验值，需人工判断是否真的过重"));
        });
        return issues;
    }

    /** 单个类被过多「不同」类型依赖。 */
    private static List<AuditIssue> detectGodClasses(LayerReport layers,
                                                     CallGraph callGraph,
                                                     TypeIndex index) {
        Map<String, LayerKind> layerByType = new LinkedHashMap<>();
        layers.assignments().forEach(assignment ->
                layerByType.put(assignment.typeQualifiedName(), assignment.layer()));

        Map<String, Set<String>> dependentsByType = new TreeMap<>();
        for (CallGraph.TypeEdge edge : callGraph.typeEdges()) {
            dependentsByType.computeIfAbsent(edge.toType(), key -> new LinkedHashSet<>())
                    .add(edge.fromType());
        }

        List<AuditIssue> issues = new ArrayList<>();
        dependentsByType.forEach((type, dependents) -> {
            if (dependents.size() <= MAX_DEPENDENTS) {
                return;
            }
            // 本应被广泛引用的分层排除：把它们报成上帝类全是误报
            if (WIDELY_USED_BY_DESIGN.contains(layerByType.get(type))) {
                return;
            }
            Anchor anchor = index.ofType(type);
            if (anchor == null) {
                return;
            }
            issues.add(new AuditIssue(RULE_RESPONSIBILITY, AuditIssue.IssueCategory.ARCHITECTURE,
                    AuditIssue.RiskLevel.LOW, anchor.filePath(), anchor.line(),
                    type + " 被 " + dependents.size() + " 个类型依赖，疑似上帝类",
                    type + " 的依赖方 = " + dependents.size() + " 个（提示阈值 " + MAX_DEPENDENTS + "）",
                    "被大量类型依赖意味着它的改动会波及整个工程，也常常意味着它承担了不止一种职责。"
                            + "应确认是否可以按职责拆分；DTO/实体/常量/工具层已排除在检查之外"));
        });
        return issues;
    }

    // ---- 位置索引 ----

    /** 把可能为 null 的分层信号渲染成可读文本，避免报告里出现字面的 "null" 让人以为出了 bug。 */
    private static String signal(LayerKind kind) {
        return kind == null ? "(无)" : kind.name();
    }

    /** 问题锚点：文件 + 行号。 */
    private record Anchor(String filePath, int line) {
    }

    /** 「类型 → 位置」与「模块 → 代表类型」的索引，用来把模块级结论落回具体行。 */
    private static final class TypeIndex {

        private final Map<String, Anchor> byType = new LinkedHashMap<>();
        private final Map<String, String> firstTypeOfModule = new LinkedHashMap<>();
        private final Map<String, String> firstTypeOfPackage = new LinkedHashMap<>();
        /** 文件 → 该文件里的类型（按声明顺序），用于在指定文件里找属于某模块的类。 */
        private final Map<String, List<String>> typesByFile = new LinkedHashMap<>();
        private final Map<String, String> packageByType = new LinkedHashMap<>();

        private static TypeIndex of(LayerReport layers,
                                    Map<String, List<TypeDeclaration>> typesBySourcePath) {
            TypeIndex index = new TypeIndex();

            new TreeMap<>(typesBySourcePath).forEach((filePath, types) -> {
                for (TypeDeclaration type : types) {
                    index.byType.put(type.qualifiedName(), new Anchor(filePath, type.startLine()));
                    index.typesByFile.computeIfAbsent(filePath, key -> new ArrayList<>())
                            .add(type.qualifiedName());
                }
            });

            for (LayerAssignment assignment : layers.assignments()) {
                if (!index.byType.containsKey(assignment.typeQualifiedName())) {
                    continue;
                }
                index.packageByType.put(assignment.typeQualifiedName(), assignment.packageName());
                index.firstTypeOfPackage.putIfAbsent(assignment.packageName(),
                        assignment.typeQualifiedName());
                index.firstTypeOfModule.putIfAbsent(assignment.packageName(),
                        assignment.typeQualifiedName());
                index.firstTypeOfModule.putIfAbsent(assignment.layer().name(),
                        assignment.typeQualifiedName());
            }
            return index;
        }

        private Anchor ofType(String typeQualifiedName) {
            return byType.get(typeQualifiedName);
        }

        /** 模块名（包名或分层枚举名）的代表类型位置。 */
        private Anchor firstOfModule(String module) {
            String type = firstTypeOfModule.get(module);
            return type == null ? null : byType.get(type);
        }

        private Anchor firstOfPackage(String packageName) {
            String type = firstTypeOfPackage.get(packageName);
            return type == null ? null : byType.get(type);
        }

        /**
         * 指定文件里、属于指定模块的第一个类的位置。
         *
         * <p>要遍历该文件<b>全部</b>类型而不是只看第一个：一个文件里可以声明多个类
         * （嵌套类、同文件的包级私有类），只认第一个会在那种文件里锚错位置。
         */
        private Anchor firstInFileWithModule(String filePath, String module) {
            for (String type : typesByFile.getOrDefault(filePath, List.of())) {
                if (module.equals(packageByType.get(type))) {
                    return byType.get(type);
                }
            }
            return null;
        }
    }
}
