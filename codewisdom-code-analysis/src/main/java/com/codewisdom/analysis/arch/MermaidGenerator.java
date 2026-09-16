package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.LayerAssignment;
import com.codewisdom.analysis.domain.LayerKind;
import com.codewisdom.analysis.domain.LayerReport;
import com.codewisdom.analysis.parser.CallGraph;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 生成 Mermaid 架构图。
 *
 * <p>两张图，都从 T-401 的 {@link LayerReport} 与 T-304 的 {@link CallGraph} 现成产物推导，
 * 不重新解析源码：分层图节点是分层，包拓扑图节点是包，边都来自类型级调用边
 * {@link CallGraph#typeEdges()}（已去自环）。
 *
 * <h2>转义规则只有两条，但都是实测出来的</h2>
 * 把图喂给 Mermaid 官方解析器逐字符试过，引号标签 {@code ["..."]} 里
 * <b>只有双引号会真的解析失败</b>，其余字符（{@code & # < > | ; , $ % @ ! * + = ? ~ ' `}
 * 以及中文）原样保留即可：
 * <table>
 *   <tr><td>{@code "}</td><td>破坏引号标签的边界</td><td>换成 Mermaid 的实体 {@code #quot;}</td></tr>
 *   <tr><td>{@code \}</td><td>在标签里是<b>转义符</b>，会吞掉后一个字符
 *       （实测 {@code a/b\c} 渲染成 {@code a/bc}）</td><td>统一换成 {@code /}</td></tr>
 * </table>
 * 第二条例外容易被忽略：包名或类名里出现反斜杠本身就少见，
 * 但一旦出现就是**静默内容丢失**——图照样能解析，只是少了一个字符。
 *
 * <h2>节点 id 与显示名分开</h2>
 * id 只用 {@code [A-Za-z0-9_]}，显示名才放中文与包路径。Mermaid 的 id 出现在语法位置，
 * 一旦掺进点号、斜杠或中文，出错信息会指向难以理解的列位置；
 * 而标签在引号里，几乎什么都能放。分开之后，{@code com.a.b} 与 {@code com_a_b}
 * 这两种包名归一化后会撞 id，因此按出现顺序加数字后缀去重。
 *
 * <h2>同层调用不是模块间依赖</h2>
 * 分层图上 {@code CONTROLLER → CONTROLLER} 这种边一律丢掉——两个 controller 互相调用
 * 在架构图上画成自环毫无信息量，只会把图糊掉。包拓扑图同理，同包内的调用也不画。
 */
@Component
public class MermaidGenerator {

    private static final String HEADER = "flowchart TD";

    /**
     * 图里带上免责声明。
     *
     * <p>架构图会被直接贴进文档或前端，脱离上下文之后没人记得它是怎么来的。
     * 把「辅助分析、需人工确认」写进图本身，比只写在接口文档里更可靠。
     */
    private static final String COMMENT = "%% CodeWisdom 架构图：基于静态分析的辅助结果，需人工确认";

    private static final String INDENT = "  ";

    /** 分层架构图：节点是分层，边是分层之间的调用（同层调用不画）。 */
    public String layerDiagram(LayerReport report, CallGraph callGraph) {
        Map<String, LayerKind> layerByType = layerByType(report);

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add(COMMENT);

        Map<LayerKind, Long> counts = report.countByLayer();
        for (LayerKind layer : LayerKind.values()) {
            Long count = counts.get(layer);
            if (count != null) {
                lines.add(INDENT + layerNodeId(layer) + "["
                        + label(layerLabel(layer) + "<br/>" + count + " 个类型") + "]");
            }
        }

        Map<LayerKind, Map<LayerKind, Long>> dependencies = new TreeMap<>();
        for (CallGraph.TypeEdge edge : callGraph.typeEdges()) {
            LayerKind from = layerByType.get(edge.fromType());
            LayerKind to = layerByType.get(edge.toType());
            if (from == null || to == null || from == to) {
                continue;
            }
            dependencies.computeIfAbsent(from, key -> new TreeMap<>())
                    .merge(to, (long) edge.weight(), Long::sum);
        }
        dependencies.forEach((from, targets) -> targets.forEach((to, weight) ->
                lines.add(INDENT + layerNodeId(from) + " -->|" + weight + "| " + layerNodeId(to))));

        return String.join("\n", lines);
    }

    /** 包依赖拓扑图：节点是包，边是包之间的调用依赖（同包调用不画）。 */
    public String packageDiagram(LayerReport report, CallGraph callGraph) {
        Map<String, String> packageByType = new LinkedHashMap<>();
        Map<String, Long> typeCountByPackage = new TreeMap<>();
        for (LayerAssignment assignment : report.assignments()) {
            packageByType.put(assignment.typeQualifiedName(), assignment.packageName());
            typeCountByPackage.merge(assignment.packageName(), 1L, Long::sum);
        }

        Map<String, String> nodeIds = packageNodeIds(typeCountByPackage.keySet());

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add(COMMENT);
        typeCountByPackage.forEach((packageName, count) -> lines.add(INDENT
                + nodeIds.get(packageName) + "["
                + label(packageLabel(packageName) + "<br/>" + count + " 个类型") + "]"));

        Map<String, Map<String, Long>> dependencies = new TreeMap<>();
        for (CallGraph.TypeEdge edge : callGraph.typeEdges()) {
            String from = packageByType.get(edge.fromType());
            String to = packageByType.get(edge.toType());
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            dependencies.computeIfAbsent(from, key -> new TreeMap<>())
                    .merge(to, (long) edge.weight(), Long::sum);
        }
        dependencies.forEach((from, targets) -> targets.forEach((to, weight) ->
                lines.add(INDENT + nodeIds.get(from) + " -->|" + weight + "| " + nodeIds.get(to))));

        return String.join("\n", lines);
    }

    // ---- 节点与标签 ----

    private static String layerNodeId(LayerKind layer) {
        return "L_" + layer.name();
    }

    /**
     * 包名 → 节点 id。
     *
     * <p>归一化会把 {@code com.a.b} 与 {@code com_a_b} 变成同一个 id，
     * 那样 Mermaid 会把两个包画成一个节点、边还会互相覆盖——属于**静默画错**，
     * 因此按出现顺序加数字后缀去重。包的顺序由调用方保证（TreeMap，字典序）。
     */
    private static Map<String, String> packageNodeIds(Collection<String> packages) {
        Map<String, String> nodeIds = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String packageName : packages) {
            String base = "P_" + (packageName.isEmpty() ? "default" : packageName.replaceAll("[^A-Za-z0-9]", "_"));
            String candidate = base;
            int suffix = 2;
            while (!used.add(candidate)) {
                candidate = base + "_" + suffix++;
            }
            nodeIds.put(packageName, candidate);
        }
        return nodeIds;
    }

    private static String packageLabel(String packageName) {
        return packageName.isEmpty() ? "(默认包)" : packageName;
    }

    /**
     * 包进引号标签。
     *
     * <p>两条转义规则见类注释，都是把图喂给官方解析器实测出来的，不是照搬文档。
     */
    private static String label(String text) {
        return "\"" + text
                .replace("\\", "/")
                .replace("\"", "#quot;")
                .replaceAll("[\\r\\n]+", " ")
                + "\"";
    }

    private static String layerLabel(LayerKind layer) {
        return switch (layer) {
            case CONTROLLER -> "controller 入口层";
            case SERVICE -> "service 业务层";
            case MAPPER -> "mapper 持久层";
            case REPOSITORY -> "repository 仓储层";
            case ENTITY -> "entity 实体";
            case DTO -> "dto 传输对象";
            case CONFIG -> "config 配置";
            case CONVERTER -> "converter 对象转换";
            case CLIENT -> "client 外部调用";
            case UTIL -> "util 工具";
            case EXCEPTION -> "exception 异常";
            case CONSTANT -> "constant 常量";
            case ASPECT -> "aspect 切面";
            case TEST -> "test 测试代码";
            case UNKNOWN -> "未归类";
        };
    }

    private static Map<String, LayerKind> layerByType(LayerReport report) {
        Map<String, LayerKind> layers = new LinkedHashMap<>();
        for (LayerAssignment assignment : report.assignments()) {
            layers.put(assignment.typeQualifiedName(), assignment.layer());
        }
        return layers;
    }
}
