package com.codewisdom.analysis.domain;

/**
 * 一条 import 声明。
 *
 * <p><b>节点结构来自对 tree-sitter-java 的实测</b>：{@code import_declaration}
 * <b>没有任何字段名</b>（{@code getFieldNameForChild} 全部返回 null），
 * 子节点顺次为 {@code import} 关键字 → {@code static}（可选）→
 * {@code scoped_identifier} 或 {@code identifier} → {@code asterisk}（通配符时）→ {@code ;}。
 *
 * <p>最容易踩的一条：<b>通配符 import 的 {@code scoped_identifier} 文本不含 {@code .*}</b>。
 * {@code import java.util.*;} 里 {@code scoped_identifier} 的文本就是 {@code java.util}，
 * {@code asterisk} 是它的<b>兄弟节点</b>。因此判断通配符只能看有没有 {@code asterisk} 节点，
 * 在文本里找 {@code *} 会永远找不到。
 *
 * @param importedQualifiedName 去掉 {@code static} 与 {@code .*} 之后的导入名：
 *                              普通导入是类型全限定名（{@code java.util.List}），
 *                              通配符导入是<b>包名</b>（{@code java.util}），
 *                              静态导入是<b>成员路径</b>（{@code java.util.Objects.requireNonNull}），
 *                              即如实记录「导入了什么」，不作截断——需要宿主类型时用
 *                              {@link #staticOwnerType()}
 * @param isStatic              是否静态导入（{@code import static a.b.C.member;}）
 * @param isWildcard            是否通配符导入（{@code import a.b.*;}）
 * @param line                  行号（1-based）
 */
public record ImportDeclaration(String importedQualifiedName,
                                boolean isStatic,
                                boolean isWildcard,
                                int line) {

    /**
     * 简单名：最后一段标识符。
     *
     * <p>通配符导入没有简单名，返回通配符 {@code *}——便于调用方一眼看出
     * 「这条 import 不能用来直接映射简单名」。
     *
     * <p><b>静态导入返回的是成员名不是类型名</b>（{@code import static a.b.C.member;}
     * 返回 {@code member}）。因此这张映射表绝不能直接用于「简单名 → 类型」，
     * 必须先用 {@link #isDirectTypeImport()} 过滤。
     */
    public String simpleName() {
        if (isWildcard) {
            return "*";
        }
        int dot = importedQualifiedName.lastIndexOf('.');
        return dot < 0 ? importedQualifiedName : importedQualifiedName.substring(dot + 1);
    }

    /**
     * 静态导入的宿主类型全限定名（{@code java.util.Objects}）；非静态导入返回 {@code null}。
     *
     * <p>用来把无接收者的调用 {@code requireNonNull(x)} 解析成
     * {@code java.util.Objects#requireNonNull}，而不是误当成调用方自己的方法——
     * 后者会产出一条指向自身类型的假边。
     */
    public String staticOwnerType() {
        return isStatic ? packageName() : null;
    }

    /**
     * 通配符导入的包名；非通配符返回 {@code null}。
     *
     * <p>刻意与 {@link #simpleName()} 分开：把「包名」当成「简单名」混用是
     * 通配符解析最容易出的错。
     */
    public String wildcardPackage() {
        return isWildcard ? importedQualifiedName : null;
    }

    /** 所属包名（去掉最后一段）。默认包导入不存在，因此至少有一段。 */
    public String packageName() {
        int dot = importedQualifiedName.lastIndexOf('.');
        return dot < 0 ? "" : importedQualifiedName.substring(0, dot);
    }

    /**
     * 是否可用于「简单名 → 全限定名」的直接映射。
     *
     * <p>静态导入映射的是成员不是类型，通配符导入映射的是一整个包——
     * 两者都不能按普通 import 处理，否则会把 {@code requireNonNull} 当成类名。
     */
    public boolean isDirectTypeImport() {
        return !isStatic && !isWildcard;
    }
}
