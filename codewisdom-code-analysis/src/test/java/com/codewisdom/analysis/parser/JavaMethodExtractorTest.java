package com.codewisdom.analysis.parser;

import com.codewisdom.analysis.domain.MethodDeclaration;
import com.codewisdom.analysis.domain.MethodKind;
import com.codewisdom.analysis.domain.MethodParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-303 验收：方法签名抽取。
 *
 * <p>样例覆盖构造器、泛型方法、可变参数、抽象方法、接口默认方法、参数注解——
 * 这些正是「按方法签名建跨文件调用图」时最容易出错的形态。
 */
@DisplayName("T-303 方法签名抽取")
class JavaMethodExtractorTest {

    private static final String SAMPLE = """
            package demo.svc;

            import java.io.IOException;
            import java.util.List;

            public abstract class OrderService {

                private final int retry;

                public OrderService(int retry) {
                    this.retry = retry;
                }

                protected OrderService() {
                    this(0);
                }

                @Deprecated
                public <R> List<R> query(String userId, int limit, String... tags)
                        throws IllegalStateException, java.io.IOException {
                    return null;
                }

                abstract int retryCount();

                static void reset() {
                }

                private void helper(@NotNull String name, final boolean flag) {
                }

                class Local {
                    void inner() {
                    }
                }
            }

            interface Handler {
                void handle(String event);

                default int priority() {
                    return 0;
                }
            }
            """;

    private final LanguageRegistry registry = new LanguageRegistry();
    private final SourceParser parser = new SourceParser(registry);
    private final JavaStructureExtractor extractor = new JavaStructureExtractor();

    private SourceStructure extract(String source) {
        try (ParseHandle handle = parser.parse("java", source)) {
            return extractor.extract(handle);
        }
    }

    private Map<String, MethodDeclaration> bySignature(SourceStructure structure) {
        return structure.methods().stream()
                .collect(Collectors.toMap(MethodDeclaration::signature, Function.identity()));
    }

    private MethodDeclaration method(SourceStructure structure, String signature) {
        return bySignature(structure).get(signature);
    }

    /**
     * 求源码中首个包含指定片段的行号（1-based）。
     *
     * <p>不写死行号常量：手数的行号会随样例增删漂移，且数错时无法区分
     * "抽取器错了"和"我数错了"。
     */
    private static int lineOf(String source, String fragment) {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(fragment)) {
                return i + 1;
            }
        }
        throw new AssertionError("样例源码中未找到片段: " + fragment);
    }

    @Nested
    @DisplayName("基本签名")
    class BasicSignature {

        @Test
        @DisplayName("方法名、返回类型、参数类型与参数名全部正确")
        void extractsSignatureParts() {
            MethodDeclaration query = method(extract(SAMPLE),
                    "query(String userId, int limit, String... tags)");

            assertThat(query.name()).isEqualTo("query");
            assertThat(query.kind()).isEqualTo(MethodKind.METHOD);
            assertThat(query.returnType()).isEqualTo("List<R>");
            assertThat(query.ownerQualifiedName()).isEqualTo("demo.svc.OrderService");
            assertThat(query.parameters()).hasSize(3);
            assertThat(query.parameters().get(0).name()).isEqualTo("userId");
            assertThat(query.parameters().get(0).type()).isEqualTo("String");
            assertThat(query.parameterCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("无参方法参数列表为空，不把括号当成参数")
        void handlesNoArgMethods() {
            MethodDeclaration reset = method(extract(SAMPLE), "reset()");

            assertThat(reset.parameters()).isEmpty();
            assertThat(reset.returnType()).isEqualTo("void");
        }

        @Test
        @DisplayName("参数上的 final 与注解不影响类型名")
        void stripsParameterNoise() {
            MethodDeclaration helper = method(extract(SAMPLE), "helper(String name, boolean flag)");

            assertThat(helper.parameters()).extracting(MethodParameter::name)
                    .containsExactly("name", "flag");
            assertThat(helper.parameters()).extracting(MethodParameter::type)
                    .containsExactly("String", "boolean");

            MethodParameter annotated = helper.parameters().get(0);
            assertThat(annotated.annotations()).containsExactly("NotNull");
        }
    }

    @Nested
    @DisplayName("构造器")
    class Constructors {

        @Test
        @DisplayName("构造器 kind 正确，返回类型为 null，名字是类名")
        void extractsConstructor() {
            SourceStructure structure = extract(SAMPLE);

            List<MethodDeclaration> constructors = structure.constructors();
            assertThat(constructors).hasSize(2);
            assertThat(constructors).allSatisfy(c -> {
                assertThat(c.kind()).isEqualTo(MethodKind.CONSTRUCTOR);
                assertThat(c.name()).isEqualTo("OrderService");
                assertThat(c.returnType()).as("构造器没有返回类型").isNull();
                assertThat(c.isConstructor()).isTrue();
            });
            assertThat(constructors).extracting(MethodDeclaration::signature)
                    .containsExactlyInAnyOrder("OrderService(int retry)", "OrderService()");
        }

        @Test
        @DisplayName("构造器与同名方法不会混淆——Java 里构造器不写返回类型")
        void constructorIsDistinguishedFromMethod() {
            SourceStructure structure = extract("""
                    package p;
                    class A {
                        A() {}
                        void A() {}
                        int A(int x) { return x; }
                    }
                    """);

            List<MethodDeclaration> all = structure.methods();
            assertThat(all).hasSize(3);
            assertThat(all.stream().filter(MethodDeclaration::isConstructor).toList())
                    .singleElement()
                    .satisfies(c -> assertThat(c.parameters()).isEmpty());
            assertThat(all.stream().filter(m -> !m.isConstructor()).toList())
                    .extracting(MethodDeclaration::returnType)
                    .containsExactlyInAnyOrder("void", "int");
        }
    }

    @Nested
    @DisplayName("方法体与抽象方法")
    class Bodies {

        @Test
        @DisplayName("抽象方法与接口方法 hasBody=false——靠空节点判断，不能只看 Java null")
        void detectsAbstractMethods() {
            SourceStructure structure = extract(SAMPLE);

            assertThat(method(structure, "retryCount()").hasBody())
                    .as("抽象类里的抽象方法").isFalse();
            assertThat(method(structure, "retryCount()").isAbstract()).isTrue();

            assertThat(method(structure, "handle(String event)").hasBody())
                    .as("接口方法无方法体").isFalse();

            assertThat(method(structure, "reset()").hasBody()).isTrue();
            assertThat(method(structure, "priority()").hasBody())
                    .as("接口默认方法有方法体").isTrue();
        }
    }

    @Nested
    @DisplayName("泛型、可变参数与异常")
    class Advanced {

        @Test
        @DisplayName("泛型方法的类型参数被抽取，返回类型保留泛型")
        void extractsTypeParameters() {
            MethodDeclaration query = method(extract(SAMPLE),
                    "query(String userId, int limit, String... tags)");

            assertThat(query.typeParameters()).containsExactly("R");
            assertThat(query.returnType()).isEqualTo("List<R>");
        }

        @Test
        @DisplayName("可变参数被标记，类型不含 ... —— spread_parameter 的字段是空的，只能解析文本")
        void extractsVarArgs() {
            MethodParameter tags = method(extract(SAMPLE),
                    "query(String userId, int limit, String... tags)")
                    .parameters().get(2);

            assertThat(tags.varArgs()).isTrue();
            assertThat(tags.type()).isEqualTo("String");
            assertThat(tags.name()).isEqualTo("tags");
            assertThat(tags.signature()).isEqualTo("String... tags");
        }

        @Test
        @DisplayName("声明的受检异常被抽取——throws 子句没有字段名，需按节点类型定位")
        void extractsThrownTypes() {
            MethodDeclaration query = method(extract(SAMPLE),
                    "query(String userId, int limit, String... tags)");

            assertThat(query.thrownTypes())
                    .containsExactly("IllegalStateException", "java.io.IOException");
            assertThat(method(extract(SAMPLE), "reset()").thrownTypes()).isEmpty();
        }

        @Test
        @DisplayName("修饰符与注解分别抽取")
        void extractsModifiersAndAnnotations() {
            SourceStructure structure = extract(SAMPLE);

            MethodDeclaration query = method(structure, "query(String userId, int limit, String... tags)");
            assertThat(query.modifiers()).containsExactlyInAnyOrder("public");
            assertThat(query.annotations()).containsExactly("Deprecated");

            assertThat(method(structure, "reset()").modifiers()).contains("static");
            assertThat(method(structure, "helper(String name, boolean flag)").modifiers())
                    .containsExactlyInAnyOrder("private");
        }
    }

    @Nested
    @DisplayName("归属与位置")
    class OwnershipAndLocation {

        @Test
        @DisplayName("方法归属到正确的类型：嵌套类的方法不挂到外层类上")
        void assignsOwnerType() {
            SourceStructure structure = extract(SAMPLE);

            assertThat(method(structure, "inner()").ownerQualifiedName())
                    .isEqualTo("demo.svc.OrderService.Local");
            assertThat(method(structure, "reset()").ownerQualifiedName())
                    .isEqualTo("demo.svc.OrderService");
            assertThat(method(structure, "handle(String event)").ownerQualifiedName())
                    .isEqualTo("demo.svc.Handler");

            assertThat(structure.methodsOf("demo.svc.Handler"))
                    .extracting(MethodDeclaration::name)
                    .containsExactly("handle", "priority");
        }

        @Test
        @DisplayName("行号范围覆盖方法体，1-based")
        void reportsLineRange() {
            MethodDeclaration query = method(extract(SAMPLE),
                    "query(String userId, int limit, String... tags)");

            // 带注解的方法，起始行指向注解行（grammar 把注解放进 modifiers）
            assertThat(query.startLine()).isEqualTo(lineOf(SAMPLE, "@Deprecated"));
            assertThat(query.endLine()).isGreaterThan(query.startLine());
        }

        @Test
        @DisplayName("方法体内的局部类仍能被识别，且归属正确")
        void findsLocalClassesInsideMethodBodies() {
            SourceStructure structure = extract("""
                    package p;
                    class Outer {
                        void run() {
                            class Local {
                                void inside() {}
                            }
                            Runnable r = new Runnable() {
                                public void run() {}
                            };
                        }
                    }
                    """);

            assertThat(structure.types()).extracting(t -> t.name())
                    .contains("Outer", "Local");
            assertThat(structure.methodsOf("p.Outer.Local"))
                    .extracting(MethodDeclaration::name)
                    .containsExactly("inside");
        }
    }

    @Test
    @DisplayName("空文件与无方法文件不产生方法，也不报错")
    void handlesFilesWithoutMethods() {
        assertThat(extract("").methods()).isEmpty();
        assertThat(extract("package a; class A {}").methods()).isEmpty();
    }

    @Test
    @DisplayName("语法错误的文件仍能抽出可识别的方法")
    void extractsFromBrokenSource() {
        SourceStructure structure = extract("""
                package p;
                class A { void good(int x) {} void broken( { }
                """);

        assertThat(structure.methods()).extracting(MethodDeclaration::name).contains("good");
    }

    @Test
    @DisplayName("方法按源码顺序返回")
    void preservesSourceOrder() {
        List<String> names = extract(SAMPLE).methods().stream()
                .map(MethodDeclaration::name)
                .toList();

        assertThat(names.indexOf("query")).isLessThan(names.indexOf("retryCount"));
        assertThat(names.indexOf("retryCount")).isLessThan(names.indexOf("reset"));
    }
}
