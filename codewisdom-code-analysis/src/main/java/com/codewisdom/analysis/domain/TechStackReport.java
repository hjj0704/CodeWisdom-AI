package com.codewisdom.analysis.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 技术栈识别报告。
 *
 * <p>构造时按技术分类 + 展示名排序并冻结，同一份输入永远得到同一份输出。
 *
 * @param items        识别到的技术，每个技术一条（证据已合并）
 * @param unrecognized 依赖坐标里<b>匹配不上任何规则</b>的那些，原样保留
 */
public record TechStackReport(List<TechStackItem> items, Set<String> unrecognized) {

    public TechStackReport {
        items = items.stream()
                .sorted(Comparator.comparing((TechStackItem item) -> item.category().ordinal())
                        .thenComparing(TechStackItem::name))
                .toList();
        unrecognized = Collections.unmodifiableSet(new LinkedHashSet<>(unrecognized));
    }

    /** 识别到的全部技术。 */
    public Set<TechStack> stacks() {
        Set<TechStack> stacks = new LinkedHashSet<>();
        for (TechStackItem item : items) {
            stacks.add(item.stack());
        }
        return Collections.unmodifiableSet(stacks);
    }

    /** 是否识别到某项技术。 */
    public boolean has(TechStack stack) {
        return stacks().contains(stack);
    }

    /** 取某项技术的结果。 */
    public Optional<TechStackItem> item(TechStack stack) {
        return items.stream().filter(item -> item.stack() == stack).findFirst();
    }

    /** 取某项技术的版本号，没识别到或没拿到版本都返回 {@code null}。 */
    public String versionOf(TechStack stack) {
        return item(stack).map(TechStackItem::version).orElse(null);
    }

    /** 按分类分组，只列出出现过的分类，按分类声明顺序排列。 */
    public Map<TechStack.Category, List<TechStackItem>> byCategory() {
        Map<TechStack.Category, List<TechStackItem>> grouped = new EnumMap<>(TechStack.Category.class);
        for (TechStackItem item : items) {
            grouped.computeIfAbsent(item.category(), key -> new ArrayList<>()).add(item);
        }
        Map<TechStack.Category, List<TechStackItem>> ordered = new LinkedHashMap<>();
        for (TechStack.Category category : TechStack.Category.values()) {
            List<TechStackItem> group = grouped.get(category);
            if (group != null) {
                ordered.put(category, List.copyOf(group));
            }
        }
        return Collections.unmodifiableMap(ordered);
    }

    /** 某项技术的全部证据。 */
    public List<String> evidenceOf(TechStack stack) {
        return item(stack).map(TechStackItem::evidence).orElse(List.of());
    }
}
