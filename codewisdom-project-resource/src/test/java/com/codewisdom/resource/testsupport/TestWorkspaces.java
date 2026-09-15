package com.codewisdom.resource.testsupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 测试工作区清理工具。
 *
 * <p>存在的唯一理由：Git 写出的 pack 文件在 Windows 上带<b>只读属性</b>，
 * 直接 {@link Files#deleteIfExists} 会抛 {@code AccessDeniedException}。
 * 不先清掉只读位，测试残留就删不掉，最终让 {@code mvn clean} 失败。
 */
public final class TestWorkspaces {

    private static final Logger log = LoggerFactory.getLogger(TestWorkspaces.class);

    private TestWorkspaces() {
    }

    /**
     * 强制递归删除，先清只读位再删。
     *
     * <p>清理失败只记日志不抛异常——测试收尾不应把构建染红。
     */
    public static void forceDelete(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (!Files.isSymbolicLink(path)) {
                    path.toFile().setWritable(true);
                }
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("清理测试工作区失败，可手动删除: {} ({})", root, e.getMessage());
        }
    }
}
