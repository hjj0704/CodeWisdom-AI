package com.codewisdom.analysis.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条技术栈识别结果。
 *
 * <p><b>证据是一等公民，不是附属信息。</b>技术栈识别是启发式的，输出会直接进架构说明书，
 * 脱离上下文之后没人能判断「这个结论是怎么来的」。每条结果都必须能回答
 * 「凭什么说是它」——没有证据的识别结果不允许存在（{@link #evidence()} 为空即视为缺陷）。
 *
 * @param stack    识别到的技术
 * @param version  版本号；<b>拿不到就是 {@code null}</b>，不填占位符、不填 Maven 属性引用原文
 * @param evidence 证据，形如 {@code src/main/java/.../OrderController.java: @RestController}
 *                 或 {@code pom.xml: org.springframework.boot:spring-boot-starter-web:3.5.15}；
 *                 同一技术可能有多条，按发现顺序排列
 */
public record TechStackItem(TechStack stack, String version, List<String> evidence) {

    public TechStackItem {
        evidence = List.copyOf(evidence);
    }

    /** 展示名，如 {@code Spring Boot}。 */
    public String name() {
        return stack.displayName();
    }

    public TechStack.Category category() {
        return stack.category();
    }

    /** 是否拿到了版本号。 */
    public boolean hasVersion() {
        return version != null && !version.isBlank();
    }

    /** 合并同类技术的两条结果，证据取并集，版本优先保留已有的。 */
    public TechStackItem merge(TechStackItem other) {
        List<String> merged = new ArrayList<>(evidence);
        for (String item : other.evidence) {
            if (!merged.contains(item)) {
                merged.add(item);
            }
        }
        return new TechStackItem(stack, hasVersion() ? version : other.version, merged);
    }
}
