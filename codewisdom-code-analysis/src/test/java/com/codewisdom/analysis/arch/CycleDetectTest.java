package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.CycleReport;
import com.codewisdom.analysis.domain.DependencyCycle;
import com.codewisdom.analysis.domain.LayerReport;
import com.codewisdom.analysis.domain.TypeDeclaration;
import com.codewisdom.analysis.parser.CallGraph;
import com.codewisdom.analysis.parser.JavaDependencyExtractor;
import com.codewisdom.analysis.parser.JavaStructureExtractor;
import com.codewisdom.analysis.parser.LanguageRegistry;
import com.codewisdom.analysis.parser.ParseHandle;
import com.codewisdom.analysis.parser.SourceParser;
import com.codewisdom.analysis.parser.SourceStructure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-404 验收：模块循环依赖检测。
 *
 * <p>三类样例，分别对应三种必须成立的判断：
 * <ol>
 *   <li><b>包环</b>——{@code demo.a → demo.b → demo.c → demo.a}，要检出并给出完整路径；</li>
 *   <li><b>分层环</b>——{@code controller ↔ service} 互相依赖，架构分层倒了，
 *       要能报出来；同一份依赖在包级与层级<b>同时成环</b>也要两处都报；</li>
 *   <li><b>无环</b>——链式依赖 {@code a → b → c} 不能报成环。
 *       这条是<b>负向断言</b>：只有「能检出环」的断言，一个永远返回「有环」的实现也能全绿。</li>
 * </ol>
 *
 * <p>口径：检出的是<b>静态调用关系</b>构成的环，不是运行时依赖环。反射、事件、配置注入
 * 产生的运行期环不在此列；只在死代码里存在的调用反而会被算进来。输出按
 * 「辅助分析结果，需人工确认」表述。
 */
@DisplayName("T-404 模块循环依赖检测")
class CycleDetectTest {

    private final LanguageRegistry registry = new LanguageRegistry();
    private final SourceParser parser = new SourceParser(registry);
    private final JavaStructureExtractor structureExtractor = new JavaStructureExtractor();
    private final JavaDependencyExtractor dependencyExtractor = new JavaDependencyExtractor(structureExtractor);
    private final LayerDetector layerDetector = new LayerDetector();
    private final CycleDetector cycleDetector = new CycleDetector();

    /** 一个样例工程的两份产物：分层报告与调用图。 */
    private record Project(LayerReport layers, CallGraph callGraph) {
    }

    private Project analyze(Map<String, String> files) {
        Map<String, List<TypeDeclaration>> typesByPath = new LinkedHashMap<>();
        List<CallGraph.SourceUnit> units = new ArrayList<>();
        files.forEach((path, source) -> {
            try (ParseHandle handle = parser.parse("java", source)) {
                SourceStructure structure = structureExtractor.extract(handle);
                typesByPath.put(path, structure.types());
                units.add(new CallGraph.SourceUnit(path, structure.packageName(),
                        structure.types(), structure.methods(),
                        dependencyExtractor.extractImports(handle),
                        dependencyExtractor.extractCallSites(handle)));
            }
        });
        return new Project(layerDetector.detect(typesByPath), CallGraph.build(units));
    }

    private static Map<String, String> files(String... pathAndSourcePairs) {
        Map<String, String> files = new LinkedHashMap<>();
        for (int i = 0; i < pathAndSourcePairs.length; i += 2) {
            files.put(pathAndSourcePairs[i], pathAndSourcePairs[i + 1]);
        }
        return files;
    }

    /** 生成一个「X 调 Y」的服务类，用来拼样例依赖链。 */
    private static String serviceSource(String packageName, String className,
                                        String fieldType, String fieldName, String callee) {
        return "package " + packageName + ";\n\n"
                + "import " + callee.substring(0, callee.lastIndexOf('.')) + ";\n\n"
                + "@Service\n"
                + "public class " + className + " {\n"
                + "    private " + fieldType + " " + fieldName + ";\n\n"
                + "    public String work() {\n"
                + "        return " + fieldName + "." + callee.substring(callee.lastIndexOf('.') + 1) + "();\n"
                + "    }\n"
                + "}\n";
    }

    // ---- 包级环 ----

    @Nested
    @DisplayName("包级循环依赖")
    class PackageCycles {

        /** demo.a → demo.b → demo.c → demo.a，另有一条不含环的 demo.healthy → demo.util。 */
        private Project threePackagesCycle() {
            return analyze(files(
                    "src/main/java/demo/a/AService.java",
                    serviceSource("demo.a", "AService", "BService", "bService", "demo.b.BService.work"),
                    "src/main/java/demo/b/BService.java",
                    serviceSource("demo.b", "BService", "CService", "cService", "demo.c.CService.work"),
                    "src/main/java/demo/c/CService.java",
                    serviceSource("demo.c", "CService", "AService", "aService", "demo.a.AService.work"),
                    "src/main/java/demo/healthy/HService.java",
                    serviceSource("demo.healthy", "HService", "Util", "util", "demo.util.Util.help"),
                    "src/main/java/demo/util/Util.java", """
                            package demo.util;

                            @Component
                            public class Util {
                                public String help() {
                                    return "";
                                }
                            }
                            """));
        }

        @Test
        @DisplayName("三包首尾相接的环被检出，并给出完整环路径")
        void detectsThreePackageCycle() {
            Project project = threePackagesCycle();

            CycleReport report = cycleDetector.detectByPackage(project.layers(), project.callGraph());

            assertThat(report.hasCycles()).isTrue();
            assertThat(report.size()).isEqualTo(1);
            assertThat(report.cycles()).singleElement()
                    .satisfies(cycle -> {
                        assertThat(cycle.describe()).isEqualTo("demo.a -> demo.b -> demo.c -> demo.a");
                        assertThat(cycle.length()).isEqualTo(3);
                        assertThat(cycle.members())
                                .containsExactlyInAnyOrder("demo.a", "demo.b", "demo.c");
                    });
        }

        @Test
        @DisplayName("不含环的链式依赖不被误报")
        void doesNotReportAcyclicChain() {
            // a → b → c 单向，没有任何回路
            Project project = analyze(files(
                    "src/main/java/demo/a/AService.java",
                    serviceSource("demo.a", "AService", "BService", "bService", "demo.b.BService.work"),
                    "src/main/java/demo/b/BService.java",
                    serviceSource("demo.b", "BService", "CService", "cService", "demo.c.CService.work"),
                    "src/main/java/demo/c/CService.java", """
                            package demo.c;

                            @Service
                            public class CService {
                                public String work() {
                                    return "";
                                }
                            }
                            """));

            CycleReport report = cycleDetector.detectByPackage(project.layers(), project.callGraph());

            assertThat(report.hasCycles()).isFalse();
            assertThat(report.size()).isZero();
            assertThat(report.involvedModules()).isEmpty();
            assertThat(report.describe()).isEqualTo("未检出循环依赖");
        }

        @Test
        @DisplayName("两包互调也被检出，长度为 2")
        void detectsTwoPackageCycle() {
            Project project = analyze(files(
                    "src/main/java/demo/x/XService.java",
                    serviceSource("demo.x", "XService", "YService", "yService", "demo.y.YService.work"),
                    "src/main/java/demo/y/YService.java",
                    serviceSource("demo.y", "YService", "XService", "xService", "demo.x.XService.work")));

            CycleReport report = cycleDetector.detectByPackage(project.layers(), project.callGraph());

            assertThat(report.cycles()).singleElement()
                    .satisfies(cycle -> {
                        assertThat(cycle.describe()).isEqualTo("demo.x -> demo.y -> demo.x");
                        assertThat(cycle.length()).isEqualTo(2);
                    });
        }

        @Test
        @DisplayName("同包内互调不算环：模块级自环没有信息量")
        void intraPackageCallsAreNotCycles() {
            // 两个类同属 demo.same 包，互相调用
            Project project = analyze(files(
                    "src/main/java/demo/same/One.java",
                    serviceSource("demo.same", "One", "Two", "two", "demo.same.Two.work"),
                    "src/main/java/demo/same/Two.java",
                    serviceSource("demo.same", "Two", "One", "one", "demo.same.One.work")));

            CycleReport packageReport = cycleDetector.detectByPackage(project.layers(), project.callGraph());

            assertThat(packageReport.hasCycles()).isFalse();
            // 但调用关系本身是存在的，只是不成环——确认样例确实构造出了环，
            // 否则「没检出」可能只是因为压根没有依赖
            assertThat(project.callGraph().typeEdges()).hasSize(2);
        }
    }

    // ---- 分层环 ----

    @Nested
    @DisplayName("分层循环依赖")
    class LayerCycles {

        /** demo.web（controller）与 demo.svc（service）互相依赖：包级与层级<b>同时成环</b>。 */
        private Project controllerServiceCycle() {
            return analyze(files(
                    "src/main/java/demo/web/WController.java", """
                            package demo.web;

                            import demo.svc.SService;

                            @RestController
                            public class WController {
                                private SService sService;

                                public String list() {
                                    return sService.work();
                                }

                                public String help() {
                                    return "";
                                }
                            }
                            """,
                    "src/main/java/demo/svc/SService.java", """
                            package demo.svc;

                            import demo.web.WController;

                            @Service
                            public class SService {
                                private WController controller;

                                public String work() {
                                    return controller.help();
                                }
                            }
                            """));
        }

        @Test
        @DisplayName("controller ↔ service 互调被报为分层环")
        void detectsLayerCycle() {
            Project project = controllerServiceCycle();

            CycleReport report = cycleDetector.detectByLayer(project.layers(), project.callGraph());

            assertThat(report.cycles()).singleElement()
                    .satisfies(cycle -> {
                        assertThat(cycle.describe()).isEqualTo("CONTROLLER -> SERVICE -> CONTROLLER");
                        assertThat(cycle.members()).containsExactlyInAnyOrder("CONTROLLER", "SERVICE");
                    });
        }

        @Test
        @DisplayName("同一份依赖在两个粒度上同时成环：包级与层级都要报")
        void reportsBothGranularities() {
            Project project = controllerServiceCycle();

            CycleReport byPackage = cycleDetector.detectByPackage(project.layers(), project.callGraph());
            CycleReport byLayer = cycleDetector.detectByLayer(project.layers(), project.callGraph());

            assertThat(byPackage.cycles()).singleElement()
                    .satisfies(cycle -> assertThat(cycle.describe()).isEqualTo("demo.svc -> demo.web -> demo.svc"));
            assertThat(byLayer.cycles()).singleElement()
                    .satisfies(cycle -> assertThat(cycle.describe())
                            .isEqualTo("CONTROLLER -> SERVICE -> CONTROLLER"));
        }

        @Test
        @DisplayName("同层内跨包互调不是分层环，但仍是包环")
        void sameLayerDifferentPackagesIsNotALayerCycle() {
            // demo.a 与 demo.b 都在 api 包路径下、都判为 CONTROLLER 层？——
            // 这里用 service 层：..service 与 ..service.impl 同层不同包
            Project project = analyze(files(
                    "src/main/java/demo/order/service/OrderService.java",
                    serviceSource("demo.order.service", "OrderService",
                            "OrderAuditService", "auditService", "demo.order.service.impl.OrderAuditService.work"),
                    "src/main/java/demo/order/service/impl/OrderAuditService.java",
                    serviceSource("demo.order.service.impl", "OrderAuditService",
                            "OrderService", "orderService", "demo.order.service.OrderService.work")));

            CycleReport byLayer = cycleDetector.detectByLayer(project.layers(), project.callGraph());
            CycleReport byPackage = cycleDetector.detectByPackage(project.layers(), project.callGraph());

            // 同层互调在层图上是一条自环 → 不是分层环
            assertThat(byLayer.hasCycles()).isFalse();
            // 但两个包不同，包级上是真环
            assertThat(byPackage.cycles()).singleElement()
                    .satisfies(cycle -> assertThat(cycle.describe())
                            .isEqualTo("demo.order.service -> demo.order.service.impl -> demo.order.service"));
        }
    }

    // ---- 报告与健壮性 ----

    @Nested
    @DisplayName("报告与健壮性")
    class Report {

        @Test
        @DisplayName("参与环的模块可查，未参与的模块查不到")
        void queriesByModule() {
            Project project = analyze(files(
                    "src/main/java/demo/a/AService.java",
                    serviceSource("demo.a", "AService", "BService", "bService", "demo.b.BService.work"),
                    "src/main/java/demo/b/BService.java",
                    serviceSource("demo.b", "BService", "AService", "aService", "demo.a.AService.work"),
                    "src/main/java/demo/healthy/HService.java",
                    serviceSource("demo.healthy", "HService", "Util", "util", "demo.util.Util.help"),
                    "src/main/java/demo/util/Util.java", """
                            package demo.util;

                            @Component
                            public class Util {
                                public String help() {
                                    return "";
                                }
                            }
                            """));

            CycleReport report = cycleDetector.detectByPackage(project.layers(), project.callGraph());

            assertThat(report.involvedModules()).containsExactlyInAnyOrder("demo.a", "demo.b");
            assertThat(report.cyclesContaining("demo.a")).hasSize(1);
            assertThat(report.cyclesContaining("demo.healthy")).isEmpty();
        }

        @Test
        @DisplayName("多个环按长度排序，短的在前")
        void sortsCyclesByLength() {
            // 一个 2 环（x ↔ y）加一个 3 环（p → q → r → p）
            Project project = analyze(files(
                    "src/main/java/demo/x/XService.java",
                    serviceSource("demo.x", "XService", "YService", "yService", "demo.y.YService.work"),
                    "src/main/java/demo/y/YService.java",
                    serviceSource("demo.y", "YService", "XService", "xService", "demo.x.XService.work"),
                    "src/main/java/demo/p/PService.java",
                    serviceSource("demo.p", "PService", "QService", "qService", "demo.q.QService.work"),
                    "src/main/java/demo/q/QService.java",
                    serviceSource("demo.q", "QService", "RService", "rService", "demo.r.RService.work"),
                    "src/main/java/demo/r/RService.java",
                    serviceSource("demo.r", "RService", "PService", "pService", "demo.p.PService.work")));

            CycleReport report = cycleDetector.detectByPackage(project.layers(), project.callGraph());

            assertThat(report.size()).isEqualTo(2);
            assertThat(report.cycles()).extracting(DependencyCycle::length).containsExactly(2, 3);
            assertThat(report.describe()).contains("检出 2 处循环依赖：");
        }

        @Test
        @DisplayName("空工程不炸")
        void handlesEmptyProject() {
            CycleReport report = cycleDetector.detectByPackage(
                    new LayerReport(List.of()), CallGraph.build(List.of()));

            assertThat(report.hasCycles()).isFalse();
            assertThat(report.describe()).isEqualTo("未检出循环依赖");
        }

        @Test
        @DisplayName("环路径首尾必须相同，构造非法路径直接报错")
        void rejectsMalformedCycle() {
            assertThatThrownBy(() -> new DependencyCycle(List.of("a", "b", "c")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("首尾相同");
            assertThatThrownBy(() -> new DependencyCycle(List.of("a")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
