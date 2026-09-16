package com.codewisdom.analysis.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 整个工程的分层识别报告。
 *
 * <p>同时给两个视角，缺一个都不够用：
 * <ul>
 *   <li><b>按分层看</b>——{@link #typesOf} / {@link #countByLayer}：各层有多少类，
 *       是架构图与「分层是否失衡」判断的输入。</li>
 *   <li><b>按包看</b>——{@link #packagesOf} / {@link #layersByPackage}：包结构到分层的映射。
 *       一个包里混了多个分层就是分层混乱的现场证据（如 {@code service} 包下出现 controller）。</li>
 * </ul>
 *
 * <p>构造时按类型限定名排序并冻结，保证同一份输入永远得到同一份输出——
 * 否则报告会随文件遍历顺序漂移，测试与前端 diff 都没法比对。
 */
public record LayerReport(List<LayerAssignment> assignments) {

    public LayerReport {
        List<LayerAssignment> sorted = new ArrayList<>(assignments);
        sorted.sort(Comparator.comparing(LayerAssignment::typeQualifiedName));
        assignments = List.copyOf(sorted);
    }

    /** 出现过类型的分层。 */
    public Set<LayerKind> layers() {
        Set<LayerKind> layers = new LinkedHashSet<>();
        for (LayerAssignment assignment : assignments) {
            layers.add(assignment.layer());
        }
        return Collections.unmodifiableSet(layers);
    }

    /** 某个分层下的全部类型。 */
    public List<LayerAssignment> typesOf(LayerKind layer) {
        return assignments.stream().filter(assignment -> assignment.layer() == layer).toList();
    }

    /** 各分层的类型数量。只列出真实出现过的层，按枚举声明顺序排列。 */
    public Map<LayerKind, Long> countByLayer() {
        Map<LayerKind, Long> counts = new EnumMap<>(LayerKind.class);
        for (LayerAssignment assignment : assignments) {
            counts.merge(assignment.layer(), 1L, Long::sum);
        }
        // 用 LinkedHashMap 而不是 Map.copyOf：后者不保证迭代顺序，输出的层顺序会随机漂移
        Map<LayerKind, Long> ordered = new LinkedHashMap<>();
        for (LayerKind layer : LayerKind.values()) {
            Long count = counts.get(layer);
            if (count != null) {
                ordered.put(layer, count);
            }
        }
        return Collections.unmodifiableMap(ordered);
    }

    /** 包名 → 该包下出现过的分层集合，按包名字典序。 */
    public Map<String, Set<LayerKind>> layersByPackage() {
        Map<String, Set<LayerKind>> byPackage = new TreeMap<>();
        for (LayerAssignment assignment : assignments) {
            byPackage.computeIfAbsent(assignment.packageName(), key -> new LinkedHashSet<>())
                    .add(assignment.layer());
        }
        return Collections.unmodifiableMap(byPackage);
    }

    /** 某个分层占据了哪些包。 */
    public Set<String> packagesOf(LayerKind layer) {
        Set<String> packages = new LinkedHashSet<>();
        for (LayerAssignment assignment : assignments) {
            if (assignment.layer() == layer) {
                packages.add(assignment.packageName());
            }
        }
        return Collections.unmodifiableSet(packages);
    }

    /**
     * 包内分层混乱的包：同一个包里出现了<b>两个以上</b>角色分层。
     *
     * <p>{@link LayerKind#TEST} 与 {@link LayerKind#UNKNOWN} 不算——测试代码与被测类同包是规范做法，
     * 认不出来的类型更不该被当成混乱的证据。
     */
    public Map<String, Set<LayerKind>> mixedPackages() {
        Map<String, Set<LayerKind>> mixed = new TreeMap<>();
        layersByPackage().forEach((packageName, layers) -> {
            Set<LayerKind> roleLayers = new LinkedHashSet<>(layers);
            roleLayers.remove(LayerKind.TEST);
            roleLayers.remove(LayerKind.UNKNOWN);
            if (roleLayers.size() > 1) {
                mixed.put(packageName, roleLayers);
            }
        });
        return Collections.unmodifiableMap(mixed);
    }

    /** 信号自相矛盾的类型，供 T-504「分层混乱」规则直接消费。 */
    public List<LayerAssignment> conflicts() {
        return assignments.stream().filter(LayerAssignment::hasConflict).toList();
    }

    /** 认不出分层的类型。显式列出来，不装作已经全部分类完成。 */
    public List<LayerAssignment> unclassified() {
        return assignments.stream().filter(LayerAssignment::isUnclassified).toList();
    }
}
