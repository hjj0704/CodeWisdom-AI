package com.codewisdom.analysis.parser;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.treesitter.TSNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-301 验收：解析器封装可用。
 *
 * <p>重点锁两件事：<b>语法缓存与 parser 生命周期互不干扰</b>，
 * 以及<b>文本提取在含中文时不乱码</b>（Tree-Sitter 给的是 UTF-8 字节偏移）。
 */
@DisplayName("T-301 源码解析器")
class SourceParserTest {

    private static final String JAVA_SOURCE = """
            package demo.orders;

            import java.util.List;

            /** 订单服务：负责查询与结算。 */
            public class OrderService implements Runnable {

                private static final int MAX_LIMIT = 100;

                public List<String> findOrders(String userId, int limit) {
                    return List.of(userId, String.valueOf(limit));
                }

                @Override
                public void run() {
                }
            }

            interface Marker {
                void mark();
            }

            enum Status { NEW, PAID }
            """;

    private final LanguageRegistry registry = new LanguageRegistry();
    private final SourceParser parser = new SourceParser(registry);

    @Test
    @DisplayName("解析 Java：根节点为 program 且无语法错误")
    void parsesJavaSource() {
        try (ParseHandle handle = parser.parse("java", JAVA_SOURCE)) {
            assertThat(handle.languageId()).isEqualTo("java");
            assertThat(handle.root().getType()).isEqualTo("program");
            assertThat(handle.hasError()).isFalse();
        }
    }

    @Test
    @DisplayName("语法缓存与 parser 生命周期互不干扰——连续解析两次都成功")
    void cachedGrammarSurvivesParserClose() {
        // 这是缓存设计的关键回归点：SourceParser 每次用完都 close 掉 TSParser，
        // 若该操作会连带释放共享的 TSLanguage，第二次解析就会崩或返回空树
        for (int i = 0; i < 3; i++) {
            try (ParseHandle handle = parser.parse("java", JAVA_SOURCE)) {
                assertThat(handle.findAll("class_declaration"))
                        .as("第 %d 次解析应仍能取到类声明", i + 1)
                        .hasSize(1);
            }
        }
        assertThat(parser.supports("java")).isTrue();
    }

    @Test
    @DisplayName("文本提取按 UTF-8 字节切片，含中文注释不乱码")
    void extractsTextWithMultibyteCharacters() {
        try (ParseHandle handle = parser.parse("java", JAVA_SOURCE)) {
            TSNode comment = handle.findAll("block_comment").get(0);

            assertThat(handle.text(comment))
                    .as("中文注释必须原样取回")
                    .isEqualTo("/** 订单服务：负责查询与结算。 */");
        }
    }

    @Test
    @DisplayName("取节点名称：类名、方法名均正确，含中文场景下也不串位")
    void extractsNodeNames() {
        try (ParseHandle handle = parser.parse("java", JAVA_SOURCE)) {
            List<TSNode> classes = handle.findAll("class_declaration");
            assertThat(handle.nameOf(classes.get(0))).isEqualTo("OrderService");

            // 注意 mark 也在内：tree-sitter-java 里接口方法同样是 method_declaration
            List<String> methodNames = handle.findAll("method_declaration").stream()
                    .map(handle::nameOf)
                    .toList();
            assertThat(methodNames).containsExactly("findOrders", "run", "mark");
        }
    }

    @Test
    @DisplayName("接口方法与类方法节点类型相同，要靠父节点类型与 body 字段区分")
    void distinguishesInterfaceMethodsFromClassMethods() {
        try (ParseHandle handle = parser.parse("java", JAVA_SOURCE)) {
            Map<String, String> ownerByMethod = new LinkedHashMap<>();
            Map<String, Boolean> hasBodyByMethod = new LinkedHashMap<>();

            for (TSNode method : handle.findAll("method_declaration")) {
                String name = handle.nameOf(method);
                ownerByMethod.put(name, method.getParent().getType());
                TSNode body = method.getChildByFieldName("body");
                hasBodyByMethod.put(name, body != null && !body.isNull());
            }

            assertThat(ownerByMethod)
                    .containsEntry("findOrders", "class_body")
                    .containsEntry("run", "class_body")
                    .containsEntry("mark", "interface_body");

            assertThat(hasBodyByMethod)
                    .as("接口方法是抽象方法，没有 body")
                    .containsEntry("mark", false)
                    .containsEntry("findOrders", true);
        }
    }

    @Test
    @DisplayName("行号对外统一为 1-based")
    void reportsOneBasedLineNumbers() {
        try (ParseHandle handle = parser.parse("java", JAVA_SOURCE)) {
            TSNode findOrders = handle.findAll("method_declaration").get(0);

            // 源码第 10 行是 "    public List<String> findOrders(...)"
            assertThat(handle.startLine(findOrders)).isEqualTo(10);
            assertThat(handle.endLine(findOrders)).isEqualTo(12);
            assertThat(handle.startLine(handle.root())).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("findAll 保持源码出现顺序")
    void findAllPreservesSourceOrder() {
        try (ParseHandle handle = parser.parse("java", JAVA_SOURCE)) {
            List<String> types = handle.findAll(List.of(
                            "class_declaration", "interface_declaration", "enum_declaration"))
                    .stream()
                    .map(TSNode::getType)
                    .toList();

            assertThat(types).containsExactly(
                    "class_declaration", "interface_declaration", "enum_declaration");
        }
    }

    @Test
    @DisplayName("容错解析：语法错误的源码仍返回句柄，由 hasError 标识")
    void toleratesSyntaxErrors() {
        String broken = "public class Broken { void m( { } ";

        try (ParseHandle handle = parser.parse("java", broken)) {
            assertThat(handle.hasError())
                    .as("真实项目常混有无法编译的文件，不能直接放弃解析")
                    .isTrue();
            assertThat(handle.findAll("class_declaration"))
                    .as("部分结构仍应被解析出来")
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("解析 Python")
    void parsesPythonSource() {
        String python = """
                class Greeter:
                    def hello(self, name):
                        return f"hi {name}"
                """;

        try (ParseHandle handle = parser.parse("python", python)) {
            assertThat(handle.root().getType()).isEqualTo("module");
            assertThat(handle.findAll("class_definition")).hasSize(1);
            assertThat(handle.nameOf(handle.findAll("function_definition").get(0))).isEqualTo("hello");
            assertThat(handle.startLine(handle.findAll("function_definition").get(0))).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("不支持的语言抛出 UNSUPPORTED_LANGUAGE")
    void rejectsUnsupportedLanguage() {
        assertThatThrownBy(() -> parser.parse("go", "package main"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_LANGUAGE));
    }

    @Test
    @DisplayName("空源码可解析，null 源码被拒绝")
    void handlesEmptySource() {
        try (ParseHandle handle = parser.parse("java", "")) {
            assertThat(handle.root().getType()).isEqualTo("program");
            assertThat(handle.findAll("class_declaration")).isEmpty();
        }
        assertThatThrownBy(() -> parser.parse("java", null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("关闭句柄后释放语法树，不影响后续解析")
    void closeReleasesTree() {
        for (int i = 0; i < 50; i++) {
            try (ParseHandle handle = parser.parse("java", JAVA_SOURCE)) {
                assertThat(handle.root().getType()).isEqualTo("program");
            }
        }
    }
}
