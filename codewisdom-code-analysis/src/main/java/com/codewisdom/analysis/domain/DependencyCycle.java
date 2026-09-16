package com.codewisdom.analysis.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一条循环依赖。
 *
 * <p>{@code path} <b>首尾是同一个模块</b>，形如 {@code [demo.a, demo.b, demo.c, demo.a]}。
 * 收尾重复一次是刻意的——不重复的话，「这是一条环」这个信息只能靠读者自己把首尾接起来，
 * 而架构文档的读者往往正是没意识到这是个环的人。
 *
 * @param path 环上的模块序列，首尾相同，长度 ≥ 2
 */
public record DependencyCycle(List<String> path) {

    public DependencyCycle {
        if (path == null || path.size() < 2 || !path.get(0).equals(path.get(path.size() - 1))) {
            throw new IllegalArgumentException("环路径必须首尾相同且至少含一条边: " + path);
        }
        path = List.copyOf(path);
    }

    /** 环上的<b>边数</b>（不含收尾重复的那个节点）。 */
    public int length() {
        return path.size() - 1;
    }

    /** 环上的模块（去重，首尾重复的只算一次）。 */
    public Set<String> members() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(path.subList(0, length())));
    }

    /** 是否包含某个模块。 */
    public boolean contains(String module) {
        return members().contains(module);
    }

    /** 可读形式，如 {@code demo.a -> demo.b -> demo.c -> demo.a}。 */
    public String describe() {
        return String.join(" -> ", path);
    }
}
