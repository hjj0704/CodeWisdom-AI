package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.DependencyCoordinate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maven POM 依赖声明读取器。
 *
 * <p><b>无状态纯函数，刻意不做成 Spring Bean</b>：输入文本、输出坐标列表，没有依赖也没有生命周期。
 * 用静态方法是为了不给调用方增加构造签名——T-403 的技术栈识别已经用上它，
 * 改成实例方法就要连带改那边的构造与测试，收益为零。
 *
 * <h2>解析到什么程度</h2>
 * 只看 {@code <parent>} 与 {@code <dependency>} 两类块，<b>不做完整 XML 解析</b>：
 * 目的是「找出声明与位置」，不是做依赖仲裁。因此以下都<b>不</b>在范围内：
 * <ul>
 *   <li>{@code <dependencyManagement>} 管理的版本（它决定实际生效版本，但不在这里判定）</li>
 *   <li>{@code <exclusions>} 排除的传递依赖</li>
 *   <li>{@code <profiles>} 里按环境激活的声明</li>
 *   <li>传递依赖与版本仲裁——那需要真正的解析器，不是文本扫描能做的</li>
 * </ul>
 * 这些边界写在调用方的报告口径里，不能让人以为「没报冲突就是真的没冲突」。
 *
 * <h2>两处容易静默出错的地方</h2>
 * <ol>
 *   <li><b>版本常写成 {@code ${property}} 引用</b>：必须回查 {@code <properties>}。
 *       回查不到时<b>保留原文</b>而不是置空——「引用了一个不存在的属性」本身就是要报的缺陷。</li>
 *   <li><b>行号靠块的起始位置算</b>：正则只能给字符下标，得自己数换行符。
 *       用 {@code <artifactId>} 的下标当行号是错的——多行写法下它与块首不在同一行。</li>
 * </ol>
 */
public final class PomDependencyReader {

    private static final Pattern PARENT_BLOCK = Pattern.compile("<parent>(.*?)</parent>", Pattern.DOTALL);
    private static final Pattern DEPENDENCY_BLOCK =
            Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL);
    private static final Pattern PROPERTIES_BLOCK =
            Pattern.compile("<properties>(.*?)</properties>", Pattern.DOTALL);
    private static final Pattern PROPERTY_ENTRY = Pattern.compile("<([\\w.-]+)>([^<]*)</([\\w.-]+)>");
    private static final Pattern GROUP_ID = Pattern.compile("<groupId>([^<]+)</groupId>");
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern VERSION = Pattern.compile("<version>([^<]+)</version>");
    private static final Pattern SCOPE = Pattern.compile("<scope>([^<]+)</scope>");
    private static final Pattern MAVEN_PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    private PomDependencyReader() {
    }

    /**
     * 读取一个 pom 里的依赖声明。
     *
     * <p>顺序固定为「{@code <parent>} 块在前，{@code <dependency>} 块按文档顺序在后」。
     * 顺序有意义：同一个坐标若在 parent 与 dependency 里都出现，先读到的会先被记录下来。
     *
     * @param path    pom 文件路径，写进结果的 {@code filePath}
     * @param content pom 原文
     * @return 依赖声明列表，按上述顺序
     */
    public static List<DependencyCoordinate> readDependencies(String path, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Map<String, String> properties = readProperties(content);

        List<Block> blocks = new ArrayList<>();
        collectBlocks(PARENT_BLOCK, content, blocks);
        collectBlocks(DEPENDENCY_BLOCK, content, blocks);

        List<DependencyCoordinate> dependencies = new ArrayList<>();
        for (Block block : blocks) {
            String artifactId = firstGroup(ARTIFACT_ID, block.text());
            if (artifactId == null) {
                // 连 artifactId 都没有的块没法定位，跳过；「缺 groupId」的情况仍然保留
                continue;
            }
            dependencies.add(new DependencyCoordinate(
                    trimmed(firstGroup(GROUP_ID, block.text())),
                    artifactId.trim(),
                    resolveVersion(firstGroup(VERSION, block.text()), properties),
                    trimmed(firstGroup(SCOPE, block.text())),
                    path,
                    lineAt(content, block.startIndex())));
        }
        return dependencies;
    }

    // ---- 块与属性 ----

    /** 一个块：内容 + 在原文中的起始下标（用于算行号）。 */
    private record Block(String text, int startIndex) {
    }

    private static void collectBlocks(Pattern pattern, String content, List<Block> out) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            out.add(new Block(matcher.group(1), matcher.start()));
        }
    }

    private static Map<String, String> readProperties(String content) {
        Map<String, String> properties = new LinkedHashMap<>();
        Matcher blockMatcher = PROPERTIES_BLOCK.matcher(content);
        while (blockMatcher.find()) {
            Matcher entryMatcher = PROPERTY_ENTRY.matcher(blockMatcher.group(1));
            while (entryMatcher.find()) {
                // 开闭标签名必须一致，否则匹配到的其实是 <a><b/></a> 这类嵌套
                if (entryMatcher.group(1).equals(entryMatcher.group(3))) {
                    properties.put(entryMatcher.group(1), entryMatcher.group(2).trim());
                }
            }
        }
        return properties;
    }

    /**
     * 解析版本：{@code ${property}} 回查 {@code <properties>}。
     *
     * <p>回查不到时<b>返回原文</b>（形如 {@code ${flyway.version}}），不返回 null——
     * 调用方需要能区分「没写版本」与「版本引用解析不了」，后者是要报的缺陷。
     */
    private static String resolveVersion(String raw, Map<String, String> properties) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        Matcher matcher = MAVEN_PROPERTY.matcher(value);
        if (matcher.matches()) {
            String resolved = properties.get(matcher.group(1));
            return resolved == null || resolved.isBlank() ? value : resolved;
        }
        return value;
    }

    // ---- 文本工具 ----

    /** 字符下标 → 行号（1-based）。 */
    private static int lineAt(String content, int index) {
        int line = 1;
        int limit = Math.min(index, content.length());
        for (int i = 0; i < limit; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
