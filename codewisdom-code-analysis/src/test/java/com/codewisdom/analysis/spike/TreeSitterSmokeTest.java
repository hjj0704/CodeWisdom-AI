package com.codewisdom.analysis.spike;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterPython;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S2 冒烟：验证 Tree-Sitter Java 绑定在本项目技术栈下真实可用。
 *
 * <p>本测试回答 tech-spike 中标记为「需验证」的两件事：
 * <ol>
 *   <li>core 0.26.6 与 grammar 0.23.5 的 <b>ABI 兼容性</b>（风险 R-01）；</li>
 *   <li>能否从源码中稳定取到 {@code class_declaration} / {@code method_declaration}
 *       的<b>节点类型与行号</b>——这是阶段 3 的验收前提。</li>
 * </ol>
 *
 * <p>纯 JUnit，不启动 Spring 上下文，不依赖任何中间件。
 */
@DisplayName("S2 冒烟：Tree-Sitter Java 绑定")
class TreeSitterSmokeTest {

    private static final String JAVA_SOURCE = """
            package demo.orders;

            import java.util.List;

            /** 订单服务。 */
            public class OrderService implements Runnable {

                private static final int MAX = 100;

                public List<String> findOrders(String userId, int limit) {
                    return List.of(userId, String.valueOf(limit));
                }

                @Override
                public void run() {
                }

                static class Inner {
                    int compute(int a, int b) {
                        return a + b;
                    }
                }
            }

            interface Marker {
                void mark();
            }

            enum Status { NEW, PAID }
            """;

    @Test
    @DisplayName("ABI 兼容：core 可加载 grammar，且 grammar ABI 落在受支持区间内")
    void abiIsCompatible() {
        try (TSParser parser = new TSParser(); TSLanguage java = new TreeSitterJava()) {
            assertThat(parser.setLanguage(java))
                    .as("setLanguage 必须成功，否则说明原生库或 ABI 不匹配")
                    .isTrue();

            assertThat(java.abiVersion())
                    .as("grammar ABI 必须落在 [MIN_COMPATIBLE, LANGUAGE_VERSION] 区间内")
                    .isBetween(TSParser.TREE_SITTER_MIN_COMPATIBLE_LANGUAGE_VERSION,
                            TSParser.TREE_SITTER_LANGUAGE_VERSION);

            System.out.printf("[S2] core ABI=%d, minCompatible=%d, javaGrammarABI=%d -> 兼容%n",
                    TSParser.TREE_SITTER_LANGUAGE_VERSION,
                    TSParser.TREE_SITTER_MIN_COMPATIBLE_LANGUAGE_VERSION,
                    java.abiVersion());
        }
    }

    @Test
    @DisplayName("解析 Java：能取到类/接口/枚举/方法声明及其行号")
    void extractsDeclarationsWithLineNumbers() {
        try (TSParser parser = new TSParser();
             TSLanguage java = new TreeSitterJava();
             TSTree tree = parse(parser, java, JAVA_SOURCE)) {

            TSNode root = tree.getRootNode();

            assertThat(root.getType()).isEqualTo("program");
            assertThat(root.hasError()).as("源码应能被无错解析").isFalse();

            byte[] src = JAVA_SOURCE.getBytes(StandardCharsets.UTF_8);

            assertThat(namesOf(root, "class_declaration", src))
                    .containsExactlyInAnyOrder("OrderService", "Inner");
            assertThat(namesOf(root, "interface_declaration", src))
                    .containsExactly("Marker");
            assertThat(namesOf(root, "enum_declaration", src))
                    .containsExactly("Status");

            List<TSNode> methods = collect(root, "method_declaration");
            assertThat(methods).hasSize(4);
            assertThat(methods.stream().map(m -> nameOf(m, src)).toList())
                    .containsExactlyInAnyOrder("findOrders", "run", "compute", "mark");

            // 行号（0-based）：findOrders 声明在第 10 行 → row = 9
            TSNode findOrders = methods.stream()
                    .filter(m -> "findOrders".equals(nameOf(m, src)))
                    .findFirst()
                    .orElseThrow();
            assertThat(findOrders.getStartPoint().getRow()).isEqualTo(9);
            assertThat(findOrders.getStartPoint().getColumn()).isEqualTo(4);
            assertThat(findOrders.getEndPoint().getRow()).isEqualTo(11);
        }
    }

    @Test
    @DisplayName("多语言可用：Python grammar 同样可加载解析")
    void supportsMultipleLanguages() {
        String python = """
                class Greeter:
                    def hello(self, name):
                        return f"hi {name}"
                """;
        try (TSParser parser = new TSParser();
             TSLanguage grammar = new TreeSitterPython();
             TSTree tree = parse(parser, grammar, python)) {

            TSNode root = tree.getRootNode();
            byte[] src = python.getBytes(StandardCharsets.UTF_8);
            assertThat(namesOf(root, "class_definition", src)).containsExactly("Greeter");
            assertThat(namesOf(root, "function_definition", src)).containsExactly("hello");
        }
    }

    // ---- helpers ----

    private static TSTree parse(TSParser parser, TSLanguage language, String source) {
        assertThat(parser.setLanguage(language)).isTrue();
        TSTree tree = parser.parseString(null, source);
        assertThat(tree).as("解析结果不应为 null").isNotNull();
        return tree;
    }

    private static List<TSNode> collect(TSNode node, String type) {
        List<TSNode> acc = new ArrayList<>();
        walk(node, type, acc);
        return acc;
    }

    private static void walk(TSNode node, String type, List<TSNode> acc) {
        if (type.equals(node.getType())) {
            acc.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), type, acc);
        }
    }

    private static List<String> namesOf(TSNode root, String type, byte[] sourceBytes) {
        return collect(root, type).stream().map(n -> nameOf(n, sourceBytes)).toList();
    }

    /**
     * 取声明节点的 {@code name} 字段文本。
     *
     * <p><b>关键陷阱</b>：该绑定只暴露字节偏移（{@link TSNode#getStartByte()} /
     * {@link TSNode#getEndByte()}），且是 <b>UTF-8 字节</b>偏移，而
     * {@link String#substring(int, int)} 用的是 UTF-16 字符索引。源码一旦包含中文等多字节字符，
     * 用 substring 会切出乱码。必须按 UTF-8 字节切片再解码。
     */
    private static String nameOf(TSNode declaration, byte[] sourceBytes) {
        TSNode name = declaration.getChildByFieldName("name");
        if (name == null || name.isNull()) {
            return "<anonymous>";
        }
        int start = name.getStartByte();
        int end = name.getEndByte();
        return new String(sourceBytes, start, end - start, StandardCharsets.UTF_8);
    }
}
