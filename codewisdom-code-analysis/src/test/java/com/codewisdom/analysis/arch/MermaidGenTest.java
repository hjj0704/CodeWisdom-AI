package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.LayerReport;
import com.codewisdom.analysis.domain.TypeDeclaration;
import com.codewisdom.analysis.domain.TypeKind;
import com.codewisdom.analysis.parser.CallGraph;
import com.codewisdom.analysis.parser.JavaDependencyExtractor;
import com.codewisdom.analysis.parser.JavaStructureExtractor;
import com.codewisdom.analysis.parser.LanguageRegistry;
import com.codewisdom.analysis.parser.ParseHandle;
import com.codewisdom.analysis.parser.SourceParser;
import com.codewisdom.analysis.parser.SourceStructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T-402 验收：Mermaid 架构图生成。
 *
 * <h2>关于「能被官方解析器渲染」这条验收</h2>
 * {@code docs/acceptance.md} 的要求是「能被 Mermaid 官方解析器<b>成功渲染</b>，
 * 不是只生成字符串」。所以本测试有两条独立的腿：
 * <ol>
 *   <li><b>快照断言</b>——逐字符锁定输出，保证内容与顺序稳定；</li>
 *   <li><b>官方解析器真校验</b>——把生成的图通过 stdin 喂给
 *       {@code tools/mermaid-verify/verify.mjs}，里面跑的是 mermaid 包自己的 jison 语法，
 *       与前端渲染同一份解析器。</li>
 * </ol>
 * 并且专门有一条<b>负向用例</b>：故意喂进去一个坏图，断言校验器判它失败。
 * 没有这条，「校验通过」完全可能只是校验器永远返回通过——T-401 就是这么栽的
 * （路径标记写错，所有正向断言全绿，负向断言一测就露）。
 *
 * <p>校验器依赖 Node 与 {@code tools/mermaid-verify} 下的 npm 依赖。
 * 环境不具备时用例会<b>显式跳过并打印启用办法</b>，不会静默通过。
 */
@DisplayName("T-402 Mermaid 架构图生成")
class MermaidGenTest {

    private static final Path HARNESS = locateHarness();
    private static final String SKIP_REASON = skipReason();

    private static final Map<String, String> FILES = new LinkedHashMap<>();

    private static final String EXPECTED_LAYER_DIAGRAM = """
            flowchart TD
            %% CodeWisdom 架构图：基于静态分析的辅助结果，需人工确认
              L_CONTROLLER["controller 入口层<br/>1 个类型"]
              L_SERVICE["service 业务层<br/>2 个类型"]
              L_MAPPER["mapper 持久层<br/>1 个类型"]
              L_CONTROLLER -->|1| L_SERVICE
              L_SERVICE -->|1| L_MAPPER
            """.stripTrailing();

    private static final String EXPECTED_PACKAGE_DIAGRAM = """
            flowchart TD
            %% CodeWisdom 架构图：基于静态分析的辅助结果，需人工确认
              P_demo_order_mapper["demo.order.mapper<br/>1 个类型"]
              P_demo_order_service["demo.order.service<br/>1 个类型"]
              P_demo_order_service_impl["demo.order.service.impl<br/>1 个类型"]
              P_demo_order_web["demo.order.web<br/>1 个类型"]
              P_demo_order_service -->|1| P_demo_order_mapper
              P_demo_order_service_impl -->|1| P_demo_order_service
              P_demo_order_web -->|1| P_demo_order_service
            """.stripTrailing();

    static {
        FILES.put("src/main/java/demo/order/web/OrderController.java", """
                package demo.order.web;

                import demo.order.service.OrderService;

                @RestController
                public class OrderController {
                    private OrderService orderService;

                    public String list() {
                        return orderService.load("1");
                    }
                }
                """);
        FILES.put("src/main/java/demo/order/service/OrderService.java", """
                package demo.order.service;

                import demo.order.mapper.OrderMapper;

                @Service
                public class OrderService {
                    private OrderMapper orderMapper;

                    public String load(String id) {
                        return orderMapper.selectById(id);
                    }
                }
                """);
        FILES.put("src/main/java/demo/order/mapper/OrderMapper.java", """
                package demo.order.mapper;

                @Mapper
                public interface OrderMapper {
                    String selectById(String id);
                }
                """);
        // 与 OrderService 同一个分层（SERVICE）但不同包——用来对照
        // 「同层边要被滤掉」与「跨包边不能被误滤」两条相反的要求
        FILES.put("src/main/java/demo/order/service/impl/OrderAuditService.java", """
                package demo.order.service.impl;

                import demo.order.service.OrderService;

                @Service
                public class OrderAuditService {
                    private OrderService orderService;

                    public String audit() {
                        return orderService.load("a");
                    }
                }
                """);
    }

    private final LanguageRegistry registry = new LanguageRegistry();
    private final SourceParser parser = new SourceParser(registry);
    private final JavaStructureExtractor structureExtractor = new JavaStructureExtractor();
    private final JavaDependencyExtractor dependencyExtractor = new JavaDependencyExtractor(structureExtractor);
    private final LayerDetector layerDetector = new LayerDetector();
    private final MermaidGenerator generator = new MermaidGenerator();

    private LayerReport report;
    private CallGraph callGraph;

    /** 走完整链路建图：Tree-Sitter 解析 → 结构抽取 → 调用图 → 分层 → 出图。 */
    @BeforeEach
    void buildProject() {
        Map<String, List<TypeDeclaration>> typesByPath = new LinkedHashMap<>();
        List<CallGraph.SourceUnit> units = new ArrayList<>();
        FILES.forEach((path, source) -> {
            try (ParseHandle handle = parser.parse("java", source)) {
                SourceStructure structure = structureExtractor.extract(handle);
                typesByPath.put(path, structure.types());
                units.add(new CallGraph.SourceUnit(path, structure.packageName(),
                        structure.types(), structure.methods(),
                        dependencyExtractor.extractImports(handle),
                        dependencyExtractor.extractCallSites(handle)));
            }
        });
        report = layerDetector.detect(typesByPath);
        callGraph = CallGraph.build(units);
    }

    // ---- 内容与结构 ----

    @Nested
    @DisplayName("图内容")
    class Content {

        @Test
        @DisplayName("分层图：节点是分层，边是分层之间的调用")
        void rendersLayerDiagram() {
            assertThat(generator.layerDiagram(report, callGraph)).isEqualTo(EXPECTED_LAYER_DIAGRAM);
        }

        @Test
        @DisplayName("包拓扑图：节点是包，边是包之间的调用")
        void rendersPackageDiagram() {
            assertThat(generator.packageDiagram(report, callGraph)).isEqualTo(EXPECTED_PACKAGE_DIAGRAM);
        }

        @Test
        @DisplayName("同层调用不画：OrderAuditService → OrderService 都在 service 层，分层图上没有它")
        void omitsIntraLayerEdges() {
            String diagram = generator.layerDiagram(report, callGraph);

            // OrderAuditService 在 demo.order.service.impl，与 OrderService 同属 SERVICE 层
            assertThat(diagram).doesNotContain("L_SERVICE -->|1| L_SERVICE");
            // 但同一条调用在包拓扑图上是跨包的，必须保留
            assertThat(generator.packageDiagram(report, callGraph))
                    .contains("P_demo_order_service_impl -->|1| P_demo_order_service");
        }

        @Test
        @DisplayName("同包调用不画：包拓扑图里没有指向自身包的边")
        void omitsIntraPackageEdges() {
            assertThat(generator.packageDiagram(report, callGraph))
                    .doesNotContain("P_demo_order_service -->|1| P_demo_order_service");
        }

        @Test
        @DisplayName("图里带免责声明：架构图脱离上下文后仍能看出它是辅助结果")
        void carriesDisclaimer() {
            assertThat(generator.layerDiagram(report, callGraph))
                    .contains("%% CodeWisdom 架构图：基于静态分析的辅助结果，需人工确认");
        }

        @Test
        @DisplayName("空工程不炸：只有头部的图仍然合法")
        void handlesEmptyProject() {
            String diagram = generator.layerDiagram(new LayerReport(List.of()), CallGraph.build(List.of()));

            assertThat(diagram).isEqualTo("flowchart TD\n%% CodeWisdom 架构图：基于静态分析的辅助结果，需人工确认");
        }
    }

    // ---- 节点 id 与转义 ----

    @Nested
    @DisplayName("节点 id 与标签转义")
    class Identifiers {

        @Test
        @DisplayName("包名归一化撞车时加后缀去重，不把两个包画成一个节点")
        void givesDistinctNodeIdsWhenNormalizationCollides() {
            Map<String, List<TypeDeclaration>> project = new LinkedHashMap<>();
            project.put("src/main/java/a.java", List.of(type("com.a.b", "A")));
            project.put("src/main/java/b.java", List.of(type("com_a_b", "B")));

            String diagram = generator.packageDiagram(layerDetector.detect(project), CallGraph.build(List.of()));

            // com.a.b 与 com_a_b 归一化后都是 P_com_a_b，必须区分开
            assertThat(diagram).contains("P_com_a_b[").contains("P_com_a_b_2[");
        }

        @Test
        @DisplayName("标签里的双引号换成 #quot;，反斜杠换成 /：都是实测会静默出错的字符")
        void escapesQuoteAndBackslash() {
            Map<String, List<TypeDeclaration>> project = new LinkedHashMap<>();
            project.put("src/main/java/x.java", List.of(type("com.a\"b\\c", "Odd")));

            String diagram = generator.packageDiagram(layerDetector.detect(project), CallGraph.build(List.of()));

            assertThat(diagram).contains("#quot;");
            // 反斜杠在标签里是转义符，会吞掉后一个字符（实测 a/b\c 渲染成 a/bc）
            assertThat(diagram).doesNotContain("\\");
            assertThat(diagram).contains("com.a#quot;b/c");
        }

        private static TypeDeclaration type(String packageName, String name) {
            return new TypeDeclaration(name, packageName + "." + name, packageName, TypeKind.CLASS,
                    Set.of("public"), List.of(), null, List.of(), null, 1, 1);
        }
    }

    // ---- 官方解析器真校验 ----

    @Nested
    @DisplayName("Mermaid 官方解析器校验")
    class OfficialParser {

        @Test
        @DisplayName("生成的分层图与包拓扑图都能被官方解析器解析")
        void acceptsGeneratedDiagrams() throws Exception {
            assumeTrue(SKIP_REASON == null, SKIP_REASON);

            assertThat(parseWithMermaid(generator.layerDiagram(report, callGraph)))
                    .as("分层架构图").isTrue();
            assertThat(parseWithMermaid(generator.packageDiagram(report, callGraph)))
                    .as("包依赖拓扑图").isTrue();
        }

        @Test
        @DisplayName("转义后的怪标签仍能被官方解析器接受")
        void acceptsEscapedLabels() throws Exception {
            assumeTrue(SKIP_REASON == null, SKIP_REASON);

            Map<String, List<TypeDeclaration>> project = new LinkedHashMap<>();
            project.put("src/main/java/x.java", List.of(new TypeDeclaration(
                    "Odd", "com.a\"b\\c.Odd", "com.a\"b\\c", TypeKind.CLASS,
                    Set.of("public"), List.of(), null, List.of(), null, 1, 1)));

            String diagram = generator.packageDiagram(layerDetector.detect(project), CallGraph.build(List.of()));

            assertThat(parseWithMermaid(diagram)).isTrue();
        }

        @Test
        @DisplayName("校验器本身有效：坏语法必须被判失败")
        void rejectsBrokenDiagram() throws Exception {
            assumeTrue(SKIP_REASON == null, SKIP_REASON);

            // 少了右括号。如果校验器永远返回通过，这条会红——它是上面几条「通过」的前提
            assertThat(parseWithMermaid("flowchart TD\n  A[Controller --> B[Service]")).isFalse();
            assertThat(parseWithMermaid("flowchart TD\n  A[\"a\"b\"] --> B[x]")).isFalse();
        }
    }

    // ---- 校验器接线 ----

    /** 从工作目录向上找仓库根下的校验器，Maven 与 IDE 下都能定位到。 */
    private static Path locateHarness() {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("tools").resolve("mermaid-verify").resolve("verify.mjs");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        return null;
    }

    /** 校验器不可用时的跳过原因；可用返回 null。 */
    private static String skipReason() {
        if (HARNESS == null) {
            return "未找到 tools/mermaid-verify/verify.mjs，跳过 Mermaid 官方解析器校验";
        }
        if (!Files.isDirectory(HARNESS.getParent().resolve("node_modules").resolve("mermaid"))) {
            return "未安装 Mermaid，跳过官方解析器校验。启用方式：cd tools/mermaid-verify && npm install";
        }
        return null;
    }

    /**
     * 把图通过 stdin 喂给官方解析器，返回是否解析通过。
     *
     * <p>输出很短（{@code PARSE_OK} 一行），先写完 stdin 再读 stdout 不会撑满管道缓冲区，
     * 因此不需要额外的读线程。
     */
    private static boolean parseWithMermaid(String diagram) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("node", HARNESS.toString());
        builder.directory(HARNESS.getParent().toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(diagram.getBytes(StandardCharsets.UTF_8));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return exitCode == 0 && output.contains("PARSE_OK");
    }
}
