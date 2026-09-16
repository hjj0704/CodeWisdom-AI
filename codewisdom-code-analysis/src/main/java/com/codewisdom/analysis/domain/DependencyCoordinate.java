package com.codewisdom.analysis.domain;

/**
 * 一条依赖声明。
 *
 * <p>带上 {@code filePath} 与 {@code line} 是刻意的：依赖冲突报告的价值大半在于
 * 「<b>去哪一行改</b>」。只说「shared-lib 有多个版本」而不给位置，等于把活儿又推回给人。
 *
 * @param groupId   groupId；缺失时为 {@code null}（声明无效，由检测器报出来）
 * @param artifactId artifactId；缺失时为 {@code null}
 * @param version   版本。<b>解析不出 {@code ${property}} 时保留原文</b>而不是置空——
 *                  「版本写成了引用但属性不存在」本身就是要报的缺陷，置空就看不见了。
 *                  不写版本（由 dependencyManagement 管理）时才是 {@code null}
 * @param scope     作用域，如 {@code test} / {@code provided}；未声明时 {@code null}
 * @param filePath  pom 文件路径
 * @param line      该 {@code <dependency>} / {@code <parent>} 块的起始行号（1-based）
 */
public record DependencyCoordinate(String groupId,
                                   String artifactId,
                                   String version,
                                   String scope,
                                   String filePath,
                                   int line) {

    /** 坐标标识 {@code groupId:artifactId}，用于分组比对。groupId 缺失时用 {@code ?} 占位。 */
    public String id() {
        return (groupId == null ? "?" : groupId) + ":" + (artifactId == null ? "?" : artifactId);
    }

    /** 是否有版本。 */
    public boolean hasVersion() {
        return version != null && !version.isBlank();
    }

    /**
     * 版本解析到了实际值没有。
     *
     * <p>{@code false} 有两种情况：压根没写版本（合法，由 dependencyManagement 管理），
     * 或写成了 {@code ${property}} 但回查不到（缺陷）。用 {@link #hasUnresolvedVersion()} 区分。
     */
    public boolean hasResolvedVersion() {
        return hasVersion() && !hasUnresolvedVersion();
    }

    /** 版本是回查不到的属性引用。 */
    public boolean hasUnresolvedVersion() {
        return hasVersion() && version.trim().startsWith("${");
    }

    /** 位置，形如 {@code module-a/pom.xml:42}。 */
    public String location() {
        return filePath + ":" + line;
    }

    /** 带版本的可读坐标，形如 {@code com.example:shared-lib:1.0}。 */
    public String describe() {
        return id() + (hasVersion() ? ":" + version : "");
    }

    /** 完整描述，形如 {@code com.example:shared-lib:1.0 (module-a/pom.xml:42)}。 */
    public String describeWithLocation() {
        return describe() + " (" + location() + ")";
    }
}
