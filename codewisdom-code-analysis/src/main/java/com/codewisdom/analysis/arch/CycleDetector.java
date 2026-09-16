package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.CycleReport;
import com.codewisdom.analysis.domain.DependencyCycle;
import com.codewisdom.analysis.domain.LayerAssignment;
import com.codewisdom.analysis.domain.LayerReport;
import com.codewisdom.analysis.parser.CallGraph;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * 循环依赖检测。
 *
 * <p>输入是 T-304 的 {@link CallGraph#typeEdges()}（类型级调用边，已去自环）与 T-401 的
 * {@link LayerReport}。调用图是<b>类型到类型</b>的，而循环依赖的「模块」通常指包或分层，
 * 所以先把类型聚合成模块，再在模块图上找环。
 *
 * <h2>两个粒度，都要报</h2>
 * 同一份依赖在两个粒度上可能<b>同时成环</b>：{@code demo.web ↔ demo.svc} 既是包环，
 * 也是「controller ↔ service」的分层环。两个粒度各自独立检测、各自出报告——
 * 包环告诉你「哪两个包要拆」，层环告诉你「架构分层倒了」。只报一个会漏掉另一半信息。
 *
 * <h2>为什么用强连通分量而不是逐个节点找环</h2>
 * 「从每个节点出发 DFS 看能不能回到自己」这种写法在稠密图上会退化成指数级，
 * 而且会把同一个环按不同起点重复报好几遍。Tarjan 一次遍历求出全部强连通分量：
 * <ul>
 *   <li>分量大小为 1 → 不在任何环上，直接跳过；</li>
 *   <li>分量大小 ≥ 2 → 该分量内任意两点互相可达，也就是整块缠在一起。</li>
 * </ul>
 * 但只有「块」还不够——验收要的是<b>完整环路径</b>，所以每个分量内再从字典序最小的节点
 * 做一次 BFS，取出回到起点的<b>最短</b>环作为代表路径。短环优先是刻意的：
 * 拆一条边就能断的环，比需要拆五条的好修得多。
 *
 * <h2>模块内调用不是环</h2>
 * 同一个包内 A 调 B、B 调 A，在包级图上就是一条自环，毫无信息量，构建邻接表时直接丢掉。
 * （类型级的自环在 T-304 就已经滤掉了。）
 *
 * <h2>口径</h2>
 * 检出的是<b>静态调用关系</b>构成的环，不是运行时依赖环。反射、事件、配置注入产生的
 * 运行时环不在此列；反过来，只在死代码里存在的调用也会被算进来。输出一律按
 * 「辅助分析结果，需人工确认」表述。
 */
@Component
public class CycleDetector {

    /** 按<b>包</b>检测循环依赖。 */
    public CycleReport detectByPackage(LayerReport report, CallGraph callGraph) {
        return detect(callGraph, moduleByType(report, LayerAssignment::packageName));
    }

    /**
     * 按<b>分层</b>检测循环依赖。
     *
     * <p>模块名用 {@link com.codewisdom.analysis.domain.LayerKind} 的枚举名（如 {@code CONTROLLER}），
     * 而不是中文展示名——报告是给程序与前端消费的，展示层再去映射文案。
     */
    public CycleReport detectByLayer(LayerReport report, CallGraph callGraph) {
        return detect(callGraph, moduleByType(report, assignment -> assignment.layer().name()));
    }

    /** 按调用方给定的映射检测：类型限定名 → 模块名。 */
    public CycleReport detect(CallGraph callGraph, Map<String, String> moduleByType) {
        Map<String, Set<String>> adjacency = adjacency(callGraph, moduleByType);

        List<DependencyCycle> cycles = new ArrayList<>();
        for (List<String> component : stronglyConnectedComponents(adjacency)) {
            if (component.size() < 2) {
                continue;
            }
            Set<String> members = new LinkedHashSet<>(component);
            // 固定从字典序最小的节点出发，保证同一份输入永远得到同一条代表路径
            String start = members.stream().min(Comparator.naturalOrder()).orElseThrow();
            List<String> path = shortestCycleThrough(start, members, adjacency);
            if (!path.isEmpty()) {
                cycles.add(new DependencyCycle(path));
            }
        }
        return new CycleReport(cycles);
    }

    // ---- 建图 ----

    private static Map<String, String> moduleByType(LayerReport report,
                                                    Function<LayerAssignment, String> resolver) {
        Map<String, String> moduleByType = new LinkedHashMap<>();
        for (LayerAssignment assignment : report.assignments()) {
            String module = resolver.apply(assignment);
            if (module != null && !module.isBlank()) {
                moduleByType.put(assignment.typeQualifiedName(), module);
            }
        }
        return moduleByType;
    }

    private static Map<String, Set<String>> adjacency(CallGraph callGraph, Map<String, String> moduleByType) {
        Map<String, Set<String>> adjacency = new TreeMap<>();
        for (String module : moduleByType.values()) {
            adjacency.computeIfAbsent(module, key -> new TreeSet<>());
        }
        for (CallGraph.TypeEdge edge : callGraph.typeEdges()) {
            String from = moduleByType.get(edge.fromType());
            String to = moduleByType.get(edge.toType());
            // 模块内调用不是环；类型有任一没归到模块就跳过，不猜
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            adjacency.get(from).add(to);
        }
        return adjacency;
    }

    // ---- 强连通分量（Tarjan） ----

    /**
     * 求全部强连通分量。
     *
     * <p>递归深度等于图里最长链的长度。包级图的链长在真实工程里通常只有几十，
     * 离栈溢出很远；真遇到病态输入再改迭代版。
     */
    private static List<List<String>> stronglyConnectedComponents(Map<String, Set<String>> adjacency) {
        Tarjan tarjan = new Tarjan(adjacency);
        for (String node : adjacency.keySet()) {
            if (!tarjan.index.containsKey(node)) {
                tarjan.visit(node);
            }
        }
        return tarjan.components;
    }

    private static final class Tarjan {

        private final Map<String, Set<String>> adjacency;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<String>> components = new ArrayList<>();
        private int nextIndex;

        private Tarjan(Map<String, Set<String>> adjacency) {
            this.adjacency = adjacency;
        }

        private void visit(String node) {
            index.put(node, nextIndex);
            lowLink.put(node, nextIndex);
            nextIndex++;
            stack.push(node);
            onStack.add(node);

            for (String next : adjacency.getOrDefault(node, Set.of())) {
                if (!index.containsKey(next)) {
                    visit(next);
                    lowLink.put(node, Math.min(lowLink.get(node), lowLink.get(next)));
                } else if (onStack.contains(next)) {
                    lowLink.put(node, Math.min(lowLink.get(node), index.get(next)));
                }
            }

            if (lowLink.get(node).equals(index.get(node))) {
                List<String> component = new ArrayList<>();
                String member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(node));
                components.add(component);
            }
        }
    }

    // ---- 分量内取最短环 ----

    /**
     * 在分量内从 {@code start} 出发，找回到 {@code start} 的最短路径。
     *
     * <p>BFS 而不是 DFS：BFS 第一次回到起点时走的一定是最短环，
     * 而 DFS 找到的那条可能绕一大圈，把「拆一条边就能断」的环描述成「要拆五条」。
     *
     * @return 首尾都是 {@code start} 的路径；分量内没有回到起点的通路时返回空列表
     */
    private static List<String> shortestCycleThrough(String start,
                                                     Set<String> members,
                                                     Map<String, Set<String>> adjacency) {
        Deque<String> queue = new ArrayDeque<>();
        Map<String, String> parent = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : adjacency.getOrDefault(current, Set.of())) {
                if (!members.contains(next)) {
                    continue;
                }
                if (next.equals(start)) {
                    List<String> middle = new ArrayList<>();
                    for (String node = current; node != null && !node.equals(start); node = parent.get(node)) {
                        middle.add(node);
                    }
                    Collections.reverse(middle);

                    List<String> path = new ArrayList<>();
                    path.add(start);
                    path.addAll(middle);
                    path.add(start);
                    return path;
                }
                if (visited.add(next)) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }
        return List.of();
    }
}
