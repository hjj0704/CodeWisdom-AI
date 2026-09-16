package com.codewisdom.analysis.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 循环依赖检测报告。
 *
 * <p>构造时按「环长度 → 路径文本」排序并冻结：环长度短的先列，同一长度按路径字典序。
 * 短的环通常更好修（拆一条边就断），排前面是有意的。
 *
 * @param cycles 检出的环，已排序
 */
public record CycleReport(List<DependencyCycle> cycles) {

    public CycleReport {
        List<DependencyCycle> sorted = new ArrayList<>(cycles);
        sorted.sort(Comparator.comparingInt(DependencyCycle::length)
                .thenComparing(DependencyCycle::describe));
        cycles = List.copyOf(sorted);
    }

    /** 是否有环。 */
    public boolean hasCycles() {
        return !cycles.isEmpty();
    }

    /** 环的数量。 */
    public int size() {
        return cycles.size();
    }

    /** 涉及循环依赖的全部模块。 */
    public Set<String> involvedModules() {
        Set<String> modules = new LinkedHashSet<>();
        for (DependencyCycle cycle : cycles) {
            modules.addAll(cycle.members());
        }
        return Collections.unmodifiableSet(modules);
    }

    /** 某个模块参与了哪些环。 */
    public List<DependencyCycle> cyclesContaining(String module) {
        return cycles.stream().filter(cycle -> cycle.contains(module)).toList();
    }

    /** 多行可读描述，便于直接写进日志或架构说明。 */
    public String describe() {
        if (!hasCycles()) {
            return "未检出循环依赖";
        }
        StringBuilder text = new StringBuilder("检出 " + cycles.size() + " 处循环依赖：");
        for (DependencyCycle cycle : cycles) {
            text.append("\n  - ").append(cycle.describe());
        }
        return text.toString();
    }
}
