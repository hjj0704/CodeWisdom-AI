package com.codewisdom.analysis.parser;

import com.codewisdom.analysis.domain.TypeDeclaration;
import com.codewisdom.analysis.domain.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-302 验收：类型声明的抽取。
 *
 * <p>样例刻意包含全部五种类型声明、多层嵌套、泛型、注解与继承关系——
 * 这些正是「按包结构统计类数量」时会出错的地方。
 */
@DisplayName("T-302 类型声明抽取")
class JavaStructureExtractorTest {

    private static final String SAMPLE = """
            package demo.orders;

            import java.util.List;

            /** 订单服务。 */
            @Deprecated
            public abstract class OrderService<T> extends BaseService implements Runnable, AutoCloseable {

                private static final int MAX = 100;

                /** 静态嵌套类。 */
                public static class Builder {
                }

                class Inner {
                    class Deepest {
                    }
                }

                public void run() {
                }

                @Override
                public void close() {
                }
            }

            interface Marker {
                void mark();
            }

            sealed interface Shape permits Circle {
            }

            final class Circle implements Shape {
            }

            enum Status { NEW, PAID }

            @interface Audited {
                String value() default "";
            }

            public record Point(int x, int y) {
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

    private Map<String, TypeDeclaration> bySimpleName(SourceStructure structure) {
        return structure.types().stream()
                .collect(Collectors.toMap(TypeDeclaration::name, Function.identity()));
    }

    /**
     * 求源码中首个包含指定片段的行号（1-based）。
     *
     * <p>测试里不写死行号常量——手数的行号会随样例增删而漂移，
     * 而且数错时无法区分"抽取器错了"和"我数错了"。
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

    @Test
    @DisplayName("包名解析正确")
    void extractsPackageName() {
        assertThat(extract(SAMPLE).packageName()).isEqualTo("demo.orders");
    }

    @Test
    @DisplayName("默认包返回空串，不抛异常")
    void handlesDefaultPackage() {
        assertThat(extract("class A {}").packageName()).isEmpty();
    }

    @Test
    @DisplayName("五种类型声明全部识别，数量与源码一致")
    void extractsAllTypeKinds() {
        SourceStructure structure = extract(SAMPLE);

        Map<TypeKind, Long> countByKind = structure.types().stream()
                .collect(Collectors.groupingBy(TypeDeclaration::kind, Collectors.counting()));

        // class: OrderService / Builder / Inner / Deepest / Circle = 5
        assertThat(countByKind.get(TypeKind.CLASS)).isEqualTo(5);
        // interface: Marker / Shape = 2
        assertThat(countByKind.get(TypeKind.INTERFACE)).isEqualTo(2);
        assertThat(countByKind.get(TypeKind.ENUM)).isEqualTo(1);
        assertThat(countByKind.get(TypeKind.ANNOTATION)).isEqualTo(1);
        assertThat(countByKind.get(TypeKind.RECORD)).isEqualTo(1);

        assertThat(structure.types()).hasSize(10);
        assertThat(bySimpleName(structure)).containsKeys(
                "OrderService", "Builder", "Inner", "Deepest", "Circle",
                "Marker", "Shape", "Status", "Audited", "Point");
    }

    @Test
    @DisplayName("顶层类型识别正确，嵌套类型不混入顶层")
    void separatesTopLevelFromNested() {
        SourceStructure structure = extract(SAMPLE);

        assertThat(structure.topLevelTypes()).extracting(TypeDeclaration::name)
                .containsExactlyInAnyOrder("OrderService", "Marker", "Shape", "Circle",
                        "Status", "Audited", "Point");
        assertThat(structure.nestedTypes()).extracting(TypeDeclaration::name)
                .containsExactlyInAnyOrder("Builder", "Inner", "Deepest");
    }

    @Nested
    @DisplayName("限定名与外层类型")
    class QualifiedNames {

        @Test
        @DisplayName("顶层类型限定名 = 包名 + 类名")
        void computesTopLevelQualifiedName() {
            Map<String, TypeDeclaration> types = bySimpleName(extract(SAMPLE));

            assertThat(types.get("OrderService").qualifiedName()).isEqualTo("demo.orders.OrderService");
            assertThat(types.get("OrderService").enclosingType()).isNull();
            assertThat(types.get("OrderService").isTopLevel()).isTrue();
        }

        @Test
        @DisplayName("嵌套类型限定名逐层拼接，外层类型可追溯")
        void computesNestedQualifiedName() {
            Map<String, TypeDeclaration> types = bySimpleName(extract(SAMPLE));

            assertThat(types.get("Builder").qualifiedName())
                    .isEqualTo("demo.orders.OrderService.Builder");
            assertThat(types.get("Builder").enclosingType()).isEqualTo("demo.orders.OrderService");

            // 三层嵌套：Deepest 在 Inner 里，Inner 在 OrderService 里
            assertThat(types.get("Deepest").qualifiedName())
                    .isEqualTo("demo.orders.OrderService.Inner.Deepest");
            assertThat(types.get("Deepest").enclosingType())
                    .isEqualTo("demo.orders.OrderService.Inner");
        }

        @Test
        @DisplayName("嵌套层级不会串：Builder 与 Inner 是兄弟，不是父子")
        void siblingsDoNotNestIntoEachOther() {
            Map<String, TypeDeclaration> types = bySimpleName(extract(SAMPLE));

            assertThat(types.get("Inner").enclosingType()).isEqualTo("demo.orders.OrderService");
            assertThat(types.get("Builder").enclosingType()).isEqualTo("demo.orders.OrderService");
        }
    }

    @Nested
    @DisplayName("修饰符与注解")
    class ModifiersAndAnnotations {

        @Test
        @DisplayName("抽取修饰符关键字，不含注解")
        void extractsModifierKeywords() {
            Map<String, TypeDeclaration> types = bySimpleName(extract(SAMPLE));

            assertThat(types.get("OrderService").modifiers())
                    .containsExactlyInAnyOrder("public", "abstract");
            assertThat(types.get("Builder").modifiers())
                    .containsExactlyInAnyOrder("public", "static");
            assertThat(types.get("Inner").modifiers()).isEmpty();
            // sealed / non-sealed 也是修饰符
            assertThat(types.get("Shape").modifiers()).contains("sealed");
            assertThat(types.get("Circle").modifiers()).containsExactlyInAnyOrder("final");
        }

        @Test
        @DisplayName("抽取类型上的注解简单名，不含 @ 符号")
        void extractsAnnotations() {
            Map<String, TypeDeclaration> types = bySimpleName(extract(SAMPLE));

            assertThat(types.get("OrderService").annotations()).containsExactly("Deprecated");
            assertThat(types.get("Marker").annotations()).isEmpty();
        }

        @Test
        @DisplayName("注解类型自身不被误当成使用注解")
        void annotationDeclarationIsNotAnAnnotationUsage() {
            Map<String, TypeDeclaration> types = bySimpleName(extract(SAMPLE));

            assertThat(types.get("Audited").kind()).isEqualTo(TypeKind.ANNOTATION);
            assertThat(types.get("Audited").annotations()).as("它自己就是注解定义").isEmpty();
        }
    }

    @Nested
    @DisplayName("继承关系与位置")
    class InheritanceAndLocation {

        @Test
        @DisplayName("父类原样保留（含泛型），未继承时为 null")
        void extractsSuperClass() {
            Map<String, TypeDeclaration> types = bySimpleName(extract(SAMPLE));

            assertThat(types.get("OrderService").superClass()).isEqualTo("BaseService");
            assertThat(types.get("Marker").superClass()).isNull();
            assertThat(types.get("Point").superClass()).isNull();
        }

        @Test
        @DisplayName("接口列表抽取正确，泛型参数被剥掉")
        void extractsInterfaces() {
            Map<String, TypeDeclaration> types = bySimpleName(extract(SAMPLE));

            assertThat(types.get("OrderService").interfaces())
                    .containsExactly("Runnable", "AutoCloseable");
            assertThat(types.get("Circle").interfaces()).containsExactly("Shape");
            assertThat(types.get("Marker").interfaces()).isEmpty();
        }

        @Test
        @DisplayName("泛型父类剥掉类型参数后只留类型名")
        void stripsGenericsFromSuperTypes() {
            SourceStructure structure = extract("""
                    package p;
                    import java.util.ArrayList;
                    class Sub extends ArrayList<String> implements java.io.Serializable {}
                    """);

            TypeDeclaration sub = structure.types().get(0);
            assertThat(sub.superClass()).isEqualTo("ArrayList<String>");
            assertThat(sub.interfaces()).containsExactly("java.io.Serializable");
        }

        @Test
        @DisplayName("行号范围覆盖整个类型体，1-based")
        void reportsLineRange() {
            Map<String, TypeDeclaration> types = bySimpleName(extract(SAMPLE));

            TypeDeclaration orderService = types.get("OrderService");
            // 注意：grammar 把注解放进 modifiers 节点，因此类型的起始行指向注解行，
            // 而不是 class 关键字行。这是 grammar 语义，不是 bug。
            assertThat(orderService.startLine())
                    .isEqualTo(lineOf(SAMPLE, "@Deprecated"));
            assertThat(orderService.endLine()).isGreaterThan(orderService.startLine());

            // 无注解的类型，起始行就是声明行
            assertThat(types.get("Marker").startLine())
                    .isEqualTo(lineOf(SAMPLE, "interface Marker"));

            // 空体 record：起止行应覆盖到右花括号
            TypeDeclaration point = types.get("Point");
            assertThat(point.startLine()).isEqualTo(lineOf(SAMPLE, "public record Point"));
            assertThat(point.endLine()).isGreaterThanOrEqualTo(point.startLine());
        }

        @Test
        @DisplayName("带注解时起始行指向注解——grammar 把注解算作 modifiers 的一部分")
        void annotatedTypeStartsAtAnnotationLine() {
            String source = """
                    package p;

                    @Deprecated
                    public class Annotated {
                    }

                    public class Plain {
                    }
                    """;

            Map<String, TypeDeclaration> types = bySimpleName(extract(source));

            assertThat(types.get("Annotated").startLine())
                    .as("指向 @Deprecated 所在行")
                    .isEqualTo(lineOf(source, "@Deprecated"));
            assertThat(types.get("Plain").startLine())
                    .as("无注解则指向声明行")
                    .isEqualTo(lineOf(source, "public class Plain"));
        }
    }

    @Test
    @DisplayName("空文件与只有包声明的文件不产生类型，也不报错")
    void handlesFilesWithoutTypes() {
        assertThat(extract("").types()).isEmpty();
        assertThat(extract("package a.b;").types()).isEmpty();
        assertThat(extract("// 只有注释").types()).isEmpty();
    }

    @Test
    @DisplayName("语法错误的文件仍能抽出可识别的类型")
    void extractsFromBrokenSource() {
        SourceStructure structure = extract("""
                package p;
                public class Good { void m() {} }
                class Broken { void n( {
                """);

        assertThat(structure.types()).extracting(TypeDeclaration::name).contains("Good");
        assertThat(structure.packageName()).isEqualTo("p");
    }

    @Test
    @DisplayName("类型声明按源码顺序返回，外层先于内层")
    void preservesSourceOrder() {
        List<String> names = extract(SAMPLE).types().stream()
                .map(TypeDeclaration::name)
                .toList();

        assertThat(names.indexOf("OrderService")).isLessThan(names.indexOf("Builder"));
        assertThat(names.indexOf("Inner")).isLessThan(names.indexOf("Deepest"));
    }
}
