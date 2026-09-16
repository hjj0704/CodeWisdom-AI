package com.codewisdom.analysis.parser;

import com.codewisdom.analysis.domain.CallSite;
import com.codewisdom.analysis.domain.ImportDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-304 验收：提取 import / 跨文件调用关系。
 *
 * <p>样例刻意做成多文件工程，覆盖三件最容易做错的事：
 * <ol>
 *   <li><b>跨文件解析</b>——简单名要经 import 表落到全限定名，同包、单类型导入、
 *       按需导入三条路径的结果必须一致。</li>
 *   <li><b>自环噪音</b>——递归自调用、{@code this(...)} 委派、同类内部互调
 *       三种噪音必须在<b>对应层级</b>被滤掉，且不能误伤方法级的真实同类型调用。</li>
 *   <li><b>歧义不猜</b>——两个包下有同名类且都被按需导入时，必须判为未解析，
 *       绝不能挑一个产出假边。</li>
 * </ol>
 */
@DisplayName("T-304 import 与跨文件调用关系")
class CallGraphTest {

    private static final String ORDER_MAPPER = """
            package demo.mapper;

            public interface OrderMapper {
                String selectById(String id);
            }
            """;

    private static final String HELPER = """
            package demo.util;

            public class Helper {
                public static Helper create() {
                    return new Helper();
                }

                public static String staticHelper() {
                    return "";
                }

                public String decorate(String value) {
                    return value;
                }
            }
            """;

    private static final String ORDER_SERVICE = """
            package demo.order;

            import demo.mapper.OrderMapper;
            import demo.util.Helper;
            import java.util.List;

            public class OrderService {

                private final OrderMapper orderMapper;
                private Helper helper = Helper.create();

                public OrderService(OrderMapper orderMapper) {
                    this(1);
                    this.orderMapper = orderMapper;
                }

                public OrderService(int retry) {
                }

                public String run(String userId) {
                    String decorated = this.helper.decorate(userId);
                    decorated = helper.decorate(decorated);
                    decorated = run(userId);
                    orderMapper.selectById(decorated);
                    Helper.create();
                    new Helper().decorate(decorated);
                    java.util.function.Supplier<String> ref = Helper::staticHelper;
                    List<String> ignored = null;
                    ignored.isEmpty();
                    return decorated;
                }

                private void selfOnly() {
                    selfOnly();
                }
            }
            """;

    private static final String BOOT = """
            package demo.boot;

            import demo.order.OrderService;
            import java.util.Objects;

            public class Boot {
                public static void main(String[] args) {
                    OrderService service = new OrderService(0);
                    service.run("u");
                    Objects.requireNonNull(service);
                    StringBuilder builder = new StringBuilder();
                    builder.append("x");
                }

                private void a() {
                    b();
                }

                private void b() {
                }
            }
            """;

    private static final String DUP_A = """
            package demo.a;

            public class Dup {
                public static Dup create() {
                    return new Dup();
                }
            }
            """;

    private static final String DUP_B = """
            package demo.b;

            public class Dup {
                public static Dup create() {
                    return null;
                }
            }
            """;

    /** 同时按需导入 demo.a 与 demo.b，两边都有 Dup——必须判歧义。 */
    private static final String WILD = """
            package demo.c;

            import demo.a.*;
            import demo.b.*;
            import demo.util.*;

            public class Wild {
                public void run() {
                    Dup.create();
                    Helper.create();
                }
            }
            """;

    private final LanguageRegistry registry = new LanguageRegistry();
    private final SourceParser parser = new SourceParser(registry);
    private final JavaStructureExtractor structureExtractor = new JavaStructureExtractor();
    private final JavaDependencyExtractor dependencyExtractor = new JavaDependencyExtractor(structureExtractor);

    private static Map<String, String> project() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/main/java/demo/mapper/OrderMapper.java", ORDER_MAPPER);
        files.put("src/main/java/demo/util/Helper.java", HELPER);
        files.put("src/main/java/demo/order/OrderService.java", ORDER_SERVICE);
        files.put("src/main/java/demo/boot/Boot.java", BOOT);
        files.put("src/main/java/demo/a/Dup.java", DUP_A);
        files.put("src/main/java/demo/b/Dup.java", DUP_B);
        files.put("src/main/java/demo/c/Wild.java", WILD);
        return files;
    }

    private CallGraph buildGraph(Map<String, String> files) {
        List<CallGraph.SourceUnit> units = new ArrayList<>();
        files.forEach((path, source) -> {
            try (ParseHandle handle = parser.parse("java", source)) {
                SourceStructure structure = structureExtractor.extract(handle);
                units.add(new CallGraph.SourceUnit(
                        path,
                        structure.packageName(),
                        structure.types(),
                        structure.methods(),
                        dependencyExtractor.extractImports(handle),
                        dependencyExtractor.extractCallSites(handle)));
            }
        });
        return CallGraph.build(units);
    }

    private CallGraph graph() {
        return buildGraph(project());
    }

    private static Set<String> edgeIds(CallGraph graph) {
        return graph.methodEdges().stream()
                .map(edge -> edge.fromId() + " -> " + edge.toId())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private static int weightOf(CallGraph graph, String fromId, String toId) {
        return graph.methodEdges().stream()
                .filter(edge -> edge.fromId().equals(fromId) && edge.toId().equals(toId))
                .mapToInt(CallGraph.CallEdge::weight)
                .findFirst()
                .orElse(0);
    }

    private List<ImportDeclaration> importsOf(String source) {
        try (ParseHandle handle = parser.parse("java", source)) {
            return dependencyExtractor.extractImports(handle);
        }
    }

    // ---- import 抽取 ----

    @Nested
    @DisplayName("import 抽取")
    class ImportExtraction {

        @Test
        @DisplayName("普通 / 静态 / 通配符三种 import 的旗标与名称都对")
        void extractsAllImportFlavours() {
            List<ImportDeclaration> imports = importsOf("""
                    package demo;

                    import java.util.List;
                    import java.util.*;
                    import static java.util.Objects.requireNonNull;
                    import demo.util.Helper;

                    public class Sample {
                    }
                    """);

            assertThat(imports).extracting(ImportDeclaration::importedQualifiedName)
                    .containsExactly("java.util.List", "java.util",
                            "java.util.Objects.requireNonNull", "demo.util.Helper");
            assertThat(imports).extracting(ImportDeclaration::line).containsExactly(3, 4, 5, 6);

            ImportDeclaration wildcard = imports.get(1);
            assertThat(wildcard.isWildcard()).isTrue();
            assertThat(wildcard.isStatic()).isFalse();
            // 通配符 import 的 scoped_identifier 文本不含 .*，asterisk 是独立兄弟节点
            assertThat(wildcard.simpleName()).isEqualTo("*");
            assertThat(wildcard.wildcardPackage()).isEqualTo("java.util");
            assertThat(wildcard.isDirectTypeImport()).isFalse();

            ImportDeclaration staticImport = imports.get(2);
            assertThat(staticImport.isStatic()).isTrue();
            // 静态导入如实记录整条成员路径，不截断成类型名
            assertThat(staticImport.simpleName()).isEqualTo("requireNonNull");
            assertThat(staticImport.staticOwnerType()).isEqualTo("java.util.Objects");
            // 静态导入映射的是成员不是类型，不能参与「简单名 → 类型」映射
            assertThat(staticImport.isDirectTypeImport()).isFalse();

            ImportDeclaration plain = imports.get(0);
            assertThat(plain.simpleName()).isEqualTo("List");
            assertThat(plain.packageName()).isEqualTo("java.util");
            assertThat(plain.staticOwnerType()).isNull();
            assertThat(plain.isDirectTypeImport()).isTrue();
        }
    }

    // ---- 跨文件解析 ----

    @Nested
    @DisplayName("跨文件解释")
    class CrossFileResolution {

        @Test
        @DisplayName("简单名经 import 表落到全限定名，形成跨文件调用边")
        void resolvesSimpleNamesThroughImports() {
            CallGraph graph = graph();

            assertThat(edgeIds(graph)).containsExactly(
                    "demo.a.Dup#create -> demo.a.Dup#<init>",
                    "demo.boot.Boot#a -> demo.boot.Boot#b",
                    "demo.boot.Boot#main -> demo.order.OrderService#<init>",
                    "demo.boot.Boot#main -> demo.order.OrderService#run",
                    "demo.c.Wild#run -> demo.util.Helper#create",
                    "demo.order.OrderService#<initializer> -> demo.util.Helper#create",
                    "demo.order.OrderService#run -> demo.mapper.OrderMapper#selectById",
                    "demo.order.OrderService#run -> demo.util.Helper#<init>",
                    "demo.order.OrderService#run -> demo.util.Helper#create",
                    "demo.order.OrderService#run -> demo.util.Helper#decorate",
                    "demo.order.OrderService#run -> demo.util.Helper#staticHelper",
                    "demo.util.Helper#create -> demo.util.Helper#<init>");
        }

        @Test
        @DisplayName("同一对节点重复调用累加次数，不会重复产边")
        void aggregatesRepeatedCalls() {
            CallGraph graph = graph();

            // this.helper.decorate(...) / helper.decorate(...) / new Helper().decorate(...) 共三次
            assertThat(weightOf(graph, "demo.order.OrderService#run", "demo.util.Helper#decorate"))
                    .isEqualTo(3);
            // 静态方法引用 Helper::staticHelper
            assertThat(weightOf(graph, "demo.order.OrderService#run", "demo.util.Helper#staticHelper"))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("字段初始化器里的调用归属 <initializer>，不丢也不并进构造器")
        void attributesFieldInitializerCalls() {
            CallGraph graph = graph();

            assertThat(edgeIds(graph))
                    .contains("demo.order.OrderService#<initializer> -> demo.util.Helper#create");
            assertThat(edgeIds(graph))
                    .doesNotContain("demo.order.OrderService#<init> -> demo.util.Helper#create");
        }

        @Test
        @DisplayName("工程外引用（显式 import 的 JDK 类）进入外部边，不进内部边")
        void separatesExternalEdges() {
            CallGraph graph = graph();

            assertThat(graph.externalEdges()).extracting(edge -> edge.fromId() + " -> " + edge.toId())
                    .containsExactly(
                            "demo.boot.Boot#main -> java.util.Objects#requireNonNull",
                            "demo.order.OrderService#run -> java.util.List#isEmpty");

            assertThat(edgeIds(graph)).doesNotContain(
                    "demo.boot.Boot#main -> java.util.Objects#requireNonNull",
                    "demo.order.OrderService#run -> java.util.List#isEmpty");
        }

        @Test
        @DisplayName("无 import 的 JDK 类不产边——解析不到就不猜")
        void doesNotEmitEdgesForUnresolvableJdkTypes() {
            CallGraph graph = graph();

            assertThat(edgeIds(graph)).noneMatch(id -> id.contains("StringBuilder"));

            assertThat(graph.unresolvedCallSites())
                    .extracting(CallSite::calleeName)
                    .contains("StringBuilder", "append");
        }

        @Test
        @DisplayName("按需导入命中唯一包时正常解析")
        void resolvesThroughWildcardImportWhenUnambiguous() {
            CallGraph graph = graph();

            // demo.c.Wild 用 import demo.util.* 导入了 Helper
            assertThat(edgeIds(graph)).contains("demo.c.Wild#run -> demo.util.Helper#create");
        }

        @Test
        @DisplayName("按需导入命中多个同名类时判为歧义，不产边")
        void refusesAmbiguousWildcardResolution() {
            CallGraph graph = graph();

            assertThat(edgeIds(graph)).noneMatch(id -> id.contains("Dup#create") && id.startsWith("demo.c.Wild"));

            assertThat(graph.unresolvedCallSites())
                    .anyMatch(site -> "create".equals(site.calleeName())
                            && "Dup".equals(site.receiverText())
                            && "demo.c.Wild".equals(site.callerOwnerQualifiedName()));
        }

        @Test
        @DisplayName("工程内全部类型都在 projectTypes 里")
        void listsProjectTypes() {
            assertThat(graph().projectTypes()).containsExactly(
                    "demo.a.Dup", "demo.b.Dup", "demo.boot.Boot",
                    "demo.c.Wild", "demo.mapper.OrderMapper",
                    "demo.order.OrderService", "demo.util.Helper");
        }
    }

    // ---- 自环过滤（T-304 验收核心） ----

    @Nested
    @DisplayName("自环过滤")
    class SelfLoopFiltering {

        @Test
        @DisplayName("方法级：递归自调用与 this(...) 委派都不产边")
        void dropsMethodLevelSelfLoops() {
            CallGraph graph = graph();

            // run() 里调 run()、selfOnly() 里调 selfOnly()、OrderService(...) 里 this(1)
            assertThat(edgeIds(graph)).doesNotContain(
                    "demo.order.OrderService#run -> demo.order.OrderService#run",
                    "demo.order.OrderService#selfOnly -> demo.order.OrderService#selfOnly",
                    "demo.order.OrderService#<init> -> demo.order.OrderService#<init>");

            assertThat(graph.methodEdges()).noneMatch(CallGraph.CallEdge::isSelfLoop);
        }

        @Test
        @DisplayName("方法级保留同类内部互调——它是真实调用，只在类型级才是自环")
        void keepsSameOwnerEdgesAtMethodLevel() {
            CallGraph graph = graph();

            assertThat(edgeIds(graph)).contains(
                    "demo.boot.Boot#a -> demo.boot.Boot#b",
                    "demo.util.Helper#create -> demo.util.Helper#<init>",
                    "demo.a.Dup#create -> demo.a.Dup#<init>");
        }

        @Test
        @DisplayName("类型级：同类内部互调被投影掉，不产生类型自环")
        void dropsTypeLevelSelfLoops() {
            CallGraph graph = graph();

            assertThat(graph.typeEdges()).noneMatch(edge -> edge.fromType().equals(edge.toType()));
            assertThat(graph.typeEdges()).extracting(edge -> edge.fromType() + " -> " + edge.toType())
                    .containsExactly(
                            "demo.boot.Boot -> demo.order.OrderService",
                            "demo.c.Wild -> demo.util.Helper",
                            "demo.order.OrderService -> demo.mapper.OrderMapper",
                            "demo.order.OrderService -> demo.util.Helper");
        }

        @Test
        @DisplayName("类型级边权是底层方法级边权之和")
        void aggregatesTypeEdgeWeight() {
            CallGraph graph = graph();

            CallGraph.TypeEdge orderToHelper = graph.typeEdges().stream()
                    .filter(edge -> edge.fromType().equals("demo.order.OrderService")
                            && edge.toType().equals("demo.util.Helper"))
                    .findFirst()
                    .orElseThrow();

            // create(字段初始化器 1) + decorate(3) + create(1) + <init>(1) + staticHelper(1)
            assertThat(orderToHelper.weight()).isEqualTo(7);
            assertThat(orderToHelper.sourcePaths())
                    .containsExactly("src/main/java/demo/order/OrderService.java");
        }
    }

    // ---- 继承链与静态导入 ----

    @Nested
    @DisplayName("继承链与静态导入")
    class InheritanceAndStaticImport {

        private static final String BASE_SERVICE = """
                package demo.base;

                public abstract class BaseService {
                    protected String currentUser() {
                        return "u";
                    }
                }
                """;

        private static final String CHILD_SERVICE = """
                package demo.order;

                import demo.base.BaseService;

                public class OrderService extends BaseService {

                    public String handle() {
                        return currentUser();
                    }

                    public void callsMissing() {
                        inheritedFromOutside();
                    }
                }
                """;

        private static final String STATIC_CALLER = """
                package demo.order;

                import static demo.util.Helper.staticHelper;

                public class OrderService {

                    public String handle() {
                        return staticHelper();
                    }
                }
                """;

        private Map<String, String> files(String path, String source) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put(path, source);
            return map;
        }

        @Test
        @DisplayName("继承来的方法挂到真正声明的父类型上，不挂到子类")
        void resolvesInheritedMethodToDeclaringParent() {
            Map<String, String> files = files("src/main/java/demo/base/BaseService.java", BASE_SERVICE);
            files.put("src/main/java/demo/order/OrderService.java", CHILD_SERVICE);

            CallGraph graph = buildGraph(files);

            assertThat(edgeIds(graph)).contains("demo.order.OrderService#handle -> demo.base.BaseService#currentUser");
            // 假边：把父类方法算到子类头上
            assertThat(edgeIds(graph)).doesNotContain(
                    "demo.order.OrderService#handle -> demo.order.OrderService#currentUser");
            assertThat(graph.typeEdges()).extracting(edge -> edge.fromType() + " -> " + edge.toType())
                    .containsExactly("demo.order.OrderService -> demo.base.BaseService");
        }

        @Test
        @DisplayName("整条继承链上都没有的方法不产边——宁可缺边也不编边")
        void dropsCallsToUndeclaredMethods() {
            Map<String, String> files = files("src/main/java/demo/base/BaseService.java", BASE_SERVICE);
            files.put("src/main/java/demo/order/OrderService.java", CHILD_SERVICE);

            CallGraph graph = buildGraph(files);

            assertThat(edgeIds(graph)).noneMatch(id -> id.contains("inheritedFromOutside"));
            assertThat(graph.unresolvedCallSites())
                    .anyMatch(site -> "inheritedFromOutside".equals(site.calleeName()));
        }

        @Test
        @DisplayName("无接收者的静态导入调用指向宿主类型，不指向调用方自己")
        void resolvesStaticImportToOwnerType() {
            Map<String, String> files = files("src/main/java/demo/order/OrderService.java", STATIC_CALLER);
            files.put("src/main/java/demo/util/Helper.java", HELPER);

            CallGraph graph = buildGraph(files);

            assertThat(edgeIds(graph)).contains("demo.order.OrderService#handle -> demo.util.Helper#staticHelper");
            assertThat(edgeIds(graph)).doesNotContain(
                    "demo.order.OrderService#handle -> demo.order.OrderService#staticHelper");
        }

        @Test
        @DisplayName("本类同名方法优先于静态导入（JLS §6.5.6.1）")
        void ownMethodWinsOverStaticImport() {
            Map<String, String> files = files("src/main/java/demo/order/OrderService.java", """
                    package demo.order;

                    import static demo.util.Helper.staticHelper;

                    public class OrderService {

                        private String staticHelper() {
                            return "";
                        }

                        public String handle() {
                            return staticHelper();
                        }
                    }
                    """);
            files.put("src/main/java/demo/util/Helper.java", HELPER);

            CallGraph graph = buildGraph(files);

            assertThat(edgeIds(graph)).contains("demo.order.OrderService#handle -> demo.order.OrderService#staticHelper");
            assertThat(edgeIds(graph)).doesNotContain("demo.order.OrderService#handle -> demo.util.Helper#staticHelper");
        }

        @Test
        @DisplayName("隐式构造器：没写构造器的类也能 new，接口不能")
        void treatsImplicitConstructorAsDeclared() {
            Map<String, String> files = files("src/main/java/demo/util/Helper.java", HELPER);
            files.put("src/main/java/demo/order/OrderService.java", """
                    package demo.order;

                    import demo.util.Helper;

                    public class OrderService {
                        public void handle() {
                            new Helper();
                        }
                    }
                    """);

            CallGraph graph = buildGraph(files);

            assertThat(edgeIds(graph)).contains("demo.order.OrderService#handle -> demo.util.Helper#<init>");
        }
    }

    // ---- 调用点抽取细节 ----

    @Nested
    @DisplayName("调用点抽取")
    class CallSiteExtraction {

        private List<CallSite> callSitesOf(String source) {
            try (ParseHandle handle = parser.parse("java", source)) {
                return dependencyExtractor.extractCallSites(handle);
            }
        }

        @Test
        @DisplayName("无接收者的调用记 NONE，不会被误当成 this.foo()")
        void distinguishesImplicitReceiver() {
            List<CallSite> sites = callSitesOf("""
                    package demo;

                    public class Sample {
                        void bare() {
                        }

                        void run() {
                            bare();
                            this.bare();
                        }
                    }
                    """);

            List<CallSite> bareCalls = sites.stream()
                    .filter(site -> "bare".equals(site.calleeName()))
                    .toList();

            assertThat(bareCalls).hasSize(2);
            assertThat(bareCalls).extracting(CallSite::receiverKind)
                    .containsExactlyInAnyOrder(CallSite.ReceiverKind.NONE, CallSite.ReceiverKind.THIS);
            assertThat(bareCalls).allMatch(CallSite::isImplicitSelfReceiver);
        }

        @Test
        @DisplayName("泛型方法调用带 type_arguments 时仍能取到方法名")
        void handlesExplicitTypeArguments() {
            List<CallSite> sites = callSitesOf("""
                    package demo;

                    public class Sample {
                        <R> R identity(R value) {
                            return value;
                        }

                        void run() {
                            this.<String>identity("x");
                        }
                    }
                    """);

            assertThat(sites).extracting(CallSite::calleeName).contains("identity");
        }

        @Test
        @DisplayName("构造器委派被如实抽出：this 指向自身、super 指向工程外")
        void extractsConstructorDelegation() {
            List<CallSite> sites = callSitesOf("""
                    package demo;

                    public class Sample {
                        public Sample() {
                            this(1);
                        }

                        public Sample(int n) {
                            super();
                        }
                    }
                    """);

            assertThat(sites).filteredOn(site -> site.kind() == CallSite.CallKind.CONSTRUCTOR_DELEGATION)
                    .extracting(CallSite::receiverKind)
                    .containsExactlyInAnyOrder(CallSite.ReceiverKind.THIS, CallSite.ReceiverKind.SUPER);
            assertThat(sites).filteredOn(site -> site.kind() == CallSite.CallKind.CONSTRUCTOR_DELEGATION)
                    .allMatch(site -> CallSite.CONSTRUCTOR_METHOD.equals(site.calleeName()));
        }

        @Test
        @DisplayName("方法引用 X::foo 与 X::new 分别记为方法和构造")
        void extractsMethodReferences() {
            List<CallSite> sites = callSitesOf("""
                    package demo;

                    import java.util.function.Supplier;

                    public class Sample {
                        static String value() {
                            return "";
                        }

                        void run() {
                            Supplier<String> a = Sample::value;
                            Supplier<Sample> b = Sample::new;
                        }
                    }
                    """);

            assertThat(sites).filteredOn(site -> site.kind() == CallSite.CallKind.METHOD_REFERENCE)
                    .extracting(CallSite::calleeName)
                    .containsExactly("value");
            assertThat(sites).filteredOn(site -> site.kind() == CallSite.CallKind.CONSTRUCTOR)
                    .extracting(CallSite::calleeName)
                    .containsExactly("Sample");
        }

        @Test
        @DisplayName("变量接收者按声明的类型推断，参数与局部变量都算")
        void infersVariableReceiverTypes() {
            List<CallSite> sites = callSitesOf("""
                    package demo;

                    import java.util.List;

                    public class Sample {
                        void run(List<String> items, Holder holder) {
                            items.size();
                            holder.ping();
                        }
                    }
                    """);

            assertThat(sites).filteredOn(site -> "size".equals(site.calleeName()))
                    .singleElement()
                    .satisfies(site -> {
                        assertThat(site.receiverKind()).isEqualTo(CallSite.ReceiverKind.VARIABLE);
                        assertThat(site.receiverTypeRef()).isEqualTo("List");
                    });
            assertThat(sites).filteredOn(site -> "ping".equals(site.calleeName()))
                    .singleElement()
                    .satisfies(site -> {
                        assertThat(site.receiverKind()).isEqualTo(CallSite.ReceiverKind.VARIABLE);
                        assertThat(site.receiverTypeRef()).isEqualTo("Holder");
                    });
        }

        @Test
        @DisplayName("链式调用结果作为接收者时不推断")
        void refusesToGuessChainedReceivers() {
            List<CallSite> sites = callSitesOf("""
                    package demo;

                    public class Sample {
                        Sample next() {
                            return this;
                        }

                        void run() {
                            next().next();
                        }
                    }
                    """);

            assertThat(sites).filteredOn(site -> "next".equals(site.calleeName()))
                    .extracting(CallSite::receiverKind)
                    .contains(CallSite.ReceiverKind.UNRESOLVED);
        }
    }
}
