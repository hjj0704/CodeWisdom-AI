package com.codewisdom.analysis.domain;

import java.util.List;
import java.util.Set;

/**
 * 类型声明（类 / 接口 / 枚举 / 注解 / 记录）。
 *
 * @param name          简单名，如 {@code OrderService}
 * @param qualifiedName 限定名，含包名与外层类型，如 {@code demo.orders.OrderService.Inner}
 * @param packageName   所在包；默认包为空串
 * @param kind          类型种类
 * @param modifiers     修饰符集合，如 {@code public} / {@code abstract}
 * @param annotations   标注在类型上的注解简单名，不含 {@code @}
 * @param superClass    父类简单名；无则为 null
 * @param interfaces    实现的接口 / 继承的父接口简单名列表
 * @param enclosingType 外层类型限定名；顶层类型为 null
 * @param startLine     起始行号（1-based，含）
 * @param endLine       结束行号（1-based，含）
 */
public record TypeDeclaration(
        String name,
        String qualifiedName,
        String packageName,
        TypeKind kind,
        Set<String> modifiers,
        List<String> annotations,
        String superClass,
        List<String> interfaces,
        String enclosingType,
        int startLine,
        int endLine
) {
    /** 是否为顶层类型（不属于任何其他类型）。 */
    public boolean isTopLevel() {
        return enclosingType == null;
    }

    /** 是否为嵌套类型。 */
    public boolean isNested() {
        return enclosingType != null;
    }
}
