package com.codewisdom.analysis.domain;

import java.util.List;

/**
 * 方法参数。
 *
 * @param name        参数名
 * @param type        参数类型文本（可能含泛型）
 * @param varArgs     是否为可变参数（{@code int...}）
 * @param annotations 参数上的注解简单名
 */
public record MethodParameter(String name, String type, boolean varArgs, List<String> annotations) {

    /** 可读签名片段，如 {@code int... nums}。 */
    public String signature() {
        return type + (varArgs ? "..." : "") + " " + name;
    }
}
