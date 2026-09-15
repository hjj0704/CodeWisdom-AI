package com.codewisdom.resource.importer;

import com.codewisdom.resource.config.ImportProperties;
import com.codewisdom.resource.domain.enums.FileCategory;
import com.codewisdom.resource.domain.enums.FileNodeType;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件树扫描测试。
 *
 * <p>刻意造出一棵「真实项目会有、但必须被剪掉」的目录树，验证剪枝发生在遍历阶段，
 * 而不是入库之后再过滤——后者会把无用节点写进数据库，白白放大几倍数据量。
 */
@DisplayName("文件树扫描 FileTreeScanner")
class FileTreeScannerTest {

    @TempDir
    Path workspace;

    private ImportProperties properties;
    private FileTreeScanner scanner;

    @BeforeEach
    void setUp() {
        properties = new ImportProperties();
        properties.setMaxFileSize(1024 * 1024);
        properties.setMaxFiles(1000);

        scanner = new FileTreeScanner(
                new PathFilter(properties),
                new LanguageDetector(),
                new FileClassifier(),
                properties);
    }

    @Test
    @DisplayName("剪掉 .git / target / node_modules，只保留源码与配置")
    void prunesBuildAndVcsArtifacts() throws IOException {
        write("pom.xml", "<project/>");
        write("README.md", "# demo");
        write("src/main/java/demo/App.java", "package demo; class App {}");
        write("src/main/resources/application.yml", "server:\n  port: 8080\n");
        // 以下都应被剪掉
        write(".git/config", "[core]");
        write(".git/objects/ab/cdef", "binary");
        write("target/classes/demo/App.class", "bytecode");
        write("target/demo-1.0.jar", "jar");
        write("node_modules/lodash/index.js", "module.exports={}");
        write(".idea/workspace.xml", "<xml/>");
        write("app.log", "log line");

        FileTreeScanner.ScanResult result = scanner.scan(workspace);
        List<String> paths = flatten(result.roots());

        assertThat(paths).contains("pom.xml", "README.md",
                "src", "src/main", "src/main/java", "src/main/java/demo",
                "src/main/java/demo/App.java",
                "src/main/resources", "src/main/resources/application.yml");

        assertThat(paths).as("不应包含版本控制目录").noneMatch(p -> p.startsWith(".git"));
        assertThat(paths).as("不应包含构建产物").noneMatch(p -> p.startsWith("target"));
        assertThat(paths).as("不应包含依赖目录").noneMatch(p -> p.startsWith("node_modules"));
        assertThat(paths).as("不应包含 IDE 元数据").noneMatch(p -> p.startsWith(".idea"));
        assertThat(paths).as("不应包含编译产物文件").noneMatch(p -> p.endsWith(".class"));
    }

    @Test
    @DisplayName("统计值与实际纳入的文件一致，目录不计入 fileCount")
    void countsOnlyFiles() throws IOException {
        write("a.java", "class A {}");
        write("b.java", "class B {}");
        write("dir/c.java", "class C {}");

        FileTreeScanner.ScanResult result = scanner.scan(workspace);

        assertThat(result.fileCount()).isEqualTo(3);
        assertThat(result.totalSize()).isEqualTo("class A {}".length()
                + "class B {}".length() + "class C {}".length());
    }

    @Test
    @DisplayName("语言与分类被正确标注，目录不自称语言")
    void annotatesLanguageAndCategory() throws IOException {
        write("src/App.java", "class App {}");
        write("pom.xml", "<project/>");
        write("README.md", "# hi");
        write("data.unknownext", "???");

        FileTreeScanner.ScanResult result = scanner.scan(workspace);

        assertThat(find(result.roots(), "src/App.java"))
                .satisfies(n -> {
                    assertThat(n.language()).isEqualTo("java");
                    assertThat(n.category()).isEqualTo(FileCategory.SOURCE);
                    assertThat(n.type()).isEqualTo(FileNodeType.FILE);
                    assertThat(n.checksum()).hasSize(64);
                });
        assertThat(find(result.roots(), "pom.xml").category()).isEqualTo(FileCategory.CONFIG);
        assertThat(find(result.roots(), "README.md").category()).isEqualTo(FileCategory.DOC);
        assertThat(find(result.roots(), "data.unknownext").category()).isEqualTo(FileCategory.OTHER);

        assertThat(find(result.roots(), "src").type()).isEqualTo(FileNodeType.DIR);
        assertThat(find(result.roots(), "src").language()).isNull();
        assertThat(find(result.roots(), "src").checksum()).isNull();
    }

    @Test
    @DisplayName("内容相同的文件 checksum 相同，内容不同则不同")
    void computesStableChecksum() throws IOException {
        write("a.java", "class Same {}");
        write("b.java", "class Same {}");
        write("c.java", "class Different {}");

        FileTreeScanner.ScanResult result = scanner.scan(workspace);

        String a = find(result.roots(), "a.java").checksum();
        String b = find(result.roots(), "b.java").checksum();
        String c = find(result.roots(), "c.java").checksum();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("节点数超过上限时中止导入，而不是撑爆内存")
    void abortsWhenExceedingMaxFiles() throws IOException {
        properties.setMaxFiles(3);
        for (int i = 0; i < 10; i++) {
            write("File" + i + ".java", "class F" + i + " {}");
        }

        assertThatThrownBy(() -> scanner.scan(workspace))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMPORT_ERROR))
                .hasMessageContaining("超过上限");
    }

    @Test
    @DisplayName("空目录扫描出空树，不抛异常")
    void handlesEmptyWorkspace() throws IOException {
        FileTreeScanner.ScanResult result = scanner.scan(workspace);

        assertThat(result.roots()).isEmpty();
        assertThat(result.fileCount()).isZero();
        assertThat(result.totalSize()).isZero();
    }

    @Test
    @DisplayName("工作区不存在时给出明确错误")
    void rejectsMissingWorkspace() {
        Path missing = workspace.resolve("not-there");

        assertThatThrownBy(() -> scanner.scan(missing))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
    }

    // ---- helpers ----

    private void write(String relativePath, String content) throws IOException {
        Path target = workspace.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private static List<String> flatten(List<FileTreeScanner.ScannedNode> nodes) {
        return nodes.stream()
                .flatMap(n -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(n.path()),
                        flatten(n.children()).stream()))
                .toList();
    }

    private static FileTreeScanner.ScannedNode find(List<FileTreeScanner.ScannedNode> nodes, String path) {
        for (FileTreeScanner.ScannedNode node : nodes) {
            if (node.path().equals(path)) {
                return node;
            }
            FileTreeScanner.ScannedNode inChild = findOrNull(node.children(), path);
            if (inChild != null) {
                return inChild;
            }
        }
        throw new AssertionError("未找到节点: " + path);
    }

    private static FileTreeScanner.ScannedNode findOrNull(List<FileTreeScanner.ScannedNode> nodes, String path) {
        for (FileTreeScanner.ScannedNode node : nodes) {
            if (node.path().equals(path)) {
                return node;
            }
            FileTreeScanner.ScannedNode inChild = findOrNull(node.children(), path);
            if (inChild != null) {
                return inChild;
            }
        }
        return null;
    }
}
