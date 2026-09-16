package com.codewisdom.analysis.domain;

import java.util.Locale;

/**
 * 一条 Python 依赖声明（{@code requirements.txt} 里的一行）。
 *
 * <p><b>与 Maven 的依赖声明不是一回事</b>，所以没有复用 {@link DependencyCoordinate}：
 * pip 没有 {@code dependencyManagement}、没有作用域，但多了一个 Maven 完全没有的东西——
 * <b>环境标记</b>（{@code ; python_version < "3.9"}），它会让同一条依赖按运行环境取不同版本。
 *
 * @param name       归一化后的包名（PEP 503：小写 + 连续的 {@code -_.} 归成一个 {@code -}）；
 *                   解析不出包名时为 {@code null}
 * @param constraint 版本约束原文，如 {@code ==1.2.3} / {@code >=1.0,<2.0}；没写约束为 {@code null}
 * @param marker     环境标记原文，如 {@code python_version < "3.9"}；没写为 {@code null}
 * @param filePath   requirements 文件路径
 * @param line       行号（1-based）
 * @param raw        该行原文（去注释后），用于展示与排错
 */
public record PythonRequirement(String name,
                                String constraint,
                                String marker,
                                String filePath,
                                int line,
                                String raw) {

    /** 解析出了包名。 */
    public boolean isParsed() {
        return name != null && !name.isBlank();
    }

    /** 有环境标记。 */
    public boolean hasMarker() {
        return marker != null && !marker.isBlank();
    }

    /**
     * 是否是「钉死的精确版本」：约束形如 {@code ==1.2.3} 且版本号是干净的纯数字点分形式。
     *
     * <p>{@code ==1.0rc1}、{@code ==1.0.post1} 这类<b>不算</b>——预发布号与后缀的比较规则
     * 需要完整的版本语义实现，本模块不做，宁可判「不是精确版本」而跳过比对。
     */
    public boolean isPinned() {
        return pinnedVersion() != null;
    }

    /** 钉死的版本号；不是精确版本返回 {@code null}。 */
    public String pinnedVersion() {
        if (constraint == null) {
            return null;
        }
        String trimmed = constraint.trim();
        if (!trimmed.startsWith("==")) {
            return null;
        }
        String version = trimmed.substring(2).trim();
        return version.matches("[0-9][0-9.]*") ? version : null;
    }

    /** 位置，形如 {@code requirements.txt:7}。 */
    public String location() {
        return filePath + ":" + line;
    }

    /** 带位置的可读描述，形如 {@code requests==2.31.0 (requirements.txt:7)}。 */
    public String describeWithLocation() {
        String text = (name == null ? raw : name) + (constraint == null ? "" : constraint);
        return text + " (" + location() + ")";
    }

    /**
     * PEP 503 包名归一化：小写，并把连续的 {@code -} {@code _} {@code .} 统一成一个 {@code -}。
     *
     * <p>只把下划线换成连字符是不够的：{@code zope.interface} 与 {@code zope-interface}
     * 指的是同一个包，只换下划线会漏掉点号那种写法。
     */
    public static String normalizeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String normalized = rawName.trim().toLowerCase(Locale.ROOT).replaceAll("[-_.]+", "-");
        return normalized.isEmpty() ? null : normalized;
    }
}
