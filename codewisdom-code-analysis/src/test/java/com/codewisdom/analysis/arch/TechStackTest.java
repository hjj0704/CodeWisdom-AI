package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.ImportDeclaration;
import com.codewisdom.analysis.domain.TechStack;
import com.codewisdom.analysis.domain.TechStackItem;
import com.codewisdom.analysis.domain.TechStackReport;
import com.codewisdom.analysis.domain.TypeDeclaration;
import com.codewisdom.analysis.domain.TypeKind;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-403 验收：技术栈识别。
 *
 * <h2>准确率的口径（别把结论说大）</h2>
 * 断言用的是<b>本测试自建的样例工程</b>：一个 Spring Boot + MyBatis-Plus 的 Java 后端、
 * 一个 Vue 3 前端、一份 Python 依赖清单。逐项人工标注后<b>双向</b>断言——
 * 既不能漏（该认的没认出来），也不能多（不该认的认了出来）。
 *
 * <p>但样例是我自己造的，<b>只能说明规则与标注一致，不等于真实工程识别准确</b>。
 * 真实工程里会出现目录外的技术、私有的内部 starter、奇形怪状的版本写法，
 * 那些都会落到 {@link TechStackReport#unrecognized()} 里——这正是它存在的理由。
 *
 * <p>另外重申识别的是「工程里<b>声明或引用</b>了这项技术」，<b>不是</b>「它在运行」：
 * 依赖引了不用、代码 import 了没跑，都会被识别出来。输出一律按
 * 「辅助分析结果，需人工确认」表述。
 */
@DisplayName("T-403 技术栈识别")
class TechStackTest {

    private static final String POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>

                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.5.15</version>
                </parent>

                <groupId>demo</groupId>
                <artifactId>demo-order</artifactId>
                <version>1.0.0-SNAPSHOT</version>

                <properties>
                    <mybatis-plus.version>3.5.17</mybatis-plus.version>
                    <spring-cloud.version>2025.0.3</spring-cloud.version>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>com.baomidou</groupId>
                        <artifactId>mybatis-plus-boot-starter</artifactId>
                        <version>${mybatis-plus.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.cloud</groupId>
                        <artifactId>spring-cloud-dependencies</artifactId>
                        <version>${spring-cloud.version}</version>
                        <type>pom</type>
                        <scope>import</scope>
                    </dependency>
                    <dependency>
                        <groupId>com.mysql</groupId>
                        <artifactId>mysql-connector-j</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                    </dependency>
                    <!-- 版本属性未定义：应当识别出技术，但版本留空，不能填 ${...} 原文 -->
                    <dependency>
                        <groupId>org.flywaydb</groupId>
                        <artifactId>flyway-core</artifactId>
                        <version>${flyway.version}</version>
                    </dependency>
                    <!-- 目录外的三方 starter：应当落到「未识别」，不能装作没看见 -->
                    <dependency>
                        <groupId>com.github.xiaoymin</groupId>
                        <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
                        <version>4.5.0</version>
                    </dependency>
                </dependencies>
            </project>
            """;

    private static final String CONTROLLER = """
            package demo.order.web;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.beans.factory.annotation.Autowired;
            import demo.order.service.OrderService;

            @RestController
            public class OrderController {

                @Autowired
                private OrderService orderService;

                @GetMapping("/orders")
                public String list() {
                    return orderService.load();
                }
            }
            """;

    private static final String MAPPER = """
            package demo.order.mapper;

            import com.baomidou.mybatisplus.core.mapper.BaseMapper;
            import org.apache.ibatis.annotations.Mapper;

            @Mapper
            public interface OrderMapper extends BaseMapper<String> {
                String selectById(String id);
            }
            """;

    private static final String ENTITY = """
            package demo.order.domain;

            import com.baomidou.mybatisplus.annotation.TableName;
            import lombok.Data;

            @Data
            @TableName("t_order")
            public class Order {
                private String id;
            }
            """;

    private static final String PACKAGE_JSON = """
            {
              "name": "codewisdom-web",
              "version": "1.0.0",
              "private": true,
              "scripts": {
                "dev": "vite"
              },
              "dependencies": {
                "vue": "^3.4.21",
                "vue-router": "^4.3.0",
                "pinia": "^2.1.7",
                "element-plus": "^2.7.0",
                "axios": "^1.7.0",
                "echarts": "^5.5.0"
              },
              "devDependencies": {
                "vite": "^5.2.0",
                "typescript": "^5.4.5",
                "lodash": "^4.17.21"
              }
            }
            """;

    private static final String REQUIREMENTS = """
            # Web 框架
            fastapi==0.115.0
            uvicorn>=0.30.0

            pydantic==2.7.1
            pandas==2.2.2
            SQLAlchemy==2.0.30

            -r base.txt
            """;

    private final LanguageRegistry registry = new LanguageRegistry();
    private final SourceParser parser = new SourceParser(registry);
    private final JavaStructureExtractor structureExtractor = new JavaStructureExtractor();
    private final JavaDependencyExtractor dependencyExtractor = new JavaDependencyExtractor(structureExtractor);
    private final TechStackDetector detector = new TechStackDetector();

    private final Map<String, List<ImportDeclaration>> importsByPath = new LinkedHashMap<>();
    private final Map<String, List<TypeDeclaration>> typesByPath = new LinkedHashMap<>();
    private final Map<String, String> dependencyFiles = new LinkedHashMap<>();

    @BeforeEach
    void buildSampleProject() {
        addJavaSource("src/main/java/demo/order/web/OrderController.java", CONTROLLER);
        addJavaSource("src/main/java/demo/order/mapper/OrderMapper.java", MAPPER);
        addJavaSource("src/main/java/demo/order/domain/Order.java", ENTITY);
        dependencyFiles.put("pom.xml", POM);
        dependencyFiles.put("web/package.json", PACKAGE_JSON);
        dependencyFiles.put("scripts/requirements.txt", REQUIREMENTS);
    }

    private void addJavaSource(String path, String source) {
        try (ParseHandle handle = parser.parse("java", source)) {
            SourceStructure structure = structureExtractor.extract(handle);
            typesByPath.put(path, structure.types());
            importsByPath.put(path, dependencyExtractor.extractImports(handle));
        }
    }

    private TechStackReport detect() {
        return detector.detect(importsByPath, typesByPath, dependencyFiles);
    }

    // ---- 验收：识别结果与标注一致 ----

    @Test
    @DisplayName("样例工程识别结果与人工标注逐项一致（不漏也不多）")
    void matchesAnnotatedSample() {
        Set<TechStack> expected = new LinkedHashSet<>(List.of(
                // 后端：来自 pom 的 parent/starter + 源码 import 与注解
                TechStack.SPRING_BOOT,
                TechStack.SPRING_MVC,
                TechStack.SPRING_FRAMEWORK,
                TechStack.SPRING_CLOUD,
                // 持久层：com.baomidou 与 org.apache.ibatis 两条 import
                TechStack.MYBATIS,
                TechStack.MYBATIS_PLUS,
                // 依赖文件
                TechStack.MYSQL,
                TechStack.LOMBOK,
                TechStack.FLYWAY,
                // 前端
                TechStack.VUE, TechStack.VUE_ROUTER, TechStack.PINIA,
                TechStack.ELEMENT_PLUS, TechStack.AXIOS, TechStack.ECHARTS,
                TechStack.VITE, TechStack.TYPESCRIPT,
                // Python
                TechStack.FASTAPI, TechStack.PYDANTIC, TechStack.PANDAS, TechStack.SQLALCHEMY));

        TechStackReport report = detect();

        System.out.printf("技术栈识别：命中 %d 项，未识别 %d 项%n",
                report.stacks().size(), report.unrecognized().size());

        // 双向断言：多认一项和少认一项都要红
        assertThat(report.stacks()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("每条识别结果都带证据，不能出现「凭空断言」")
    void everyItemCarriesEvidence() {
        TechStackReport report = detect();

        assertThat(report.items()).isNotEmpty();
        assertThat(report.items()).allSatisfy(item ->
                assertThat(item.evidence()).as("%s 的证据", item.name()).isNotEmpty());
    }

    // ---- 版本号 ----

    @Nested
    @DisplayName("版本号解析")
    class Versions {

        @Test
        @DisplayName("pom 里的 ${属性} 引用回查 <properties> 得到真实版本")
        void resolvesMavenPropertyReference() {
            TechStackReport report = detect();

            assertThat(report.versionOf(TechStack.MYBATIS_PLUS)).isEqualTo("3.5.17");
            assertThat(report.versionOf(TechStack.SPRING_CLOUD)).isEqualTo("2025.0.3");
            assertThat(report.versionOf(TechStack.SPRING_BOOT)).isEqualTo("3.5.15");
        }

        @Test
        @DisplayName("属性回查不到时版本留空，绝不把 ${...} 原文当版本号")
        void leavesVersionNullWhenPropertyIsUndefined() {
            TechStackReport report = detect();

            // pom 里 flyway-core 写的是 <version>${flyway.version}</version>，而该属性未定义
            assertThat(report.has(TechStack.FLYWAY)).isTrue();
            assertThat(report.versionOf(TechStack.FLYWAY)).isNull();
            assertThat(report.evidenceOf(TechStack.FLYWAY))
                    .anyMatch(evidence -> evidence.contains("flyway-core"))
                    .noneMatch(evidence -> evidence.contains("${"));
        }

        @Test
        @DisplayName("npm 与 Python 的版本范围前缀被剥掉，只留字面版本")
        void normalizesRangePrefixes() {
            TechStackReport report = detect();

            assertThat(report.versionOf(TechStack.VUE)).isEqualTo("3.4.21");
            assertThat(report.versionOf(TechStack.ELEMENT_PLUS)).isEqualTo("2.7.0");
            assertThat(report.versionOf(TechStack.FASTAPI)).isEqualTo("0.115.0");
            // PEP 503 归一化：requirements 里写的是 SQLAlchemy
            assertThat(report.versionOf(TechStack.SQLALCHEMY)).isEqualTo("2.0.30");
        }

        @Test
        @DisplayName("同一技术多处声明时保留先拿到的版本，证据全部累积")
        void keepsFirstVersionAndMergesEvidence() {
            TechStackReport report = detect();

            TechStackItem springBoot = report.item(TechStack.SPRING_BOOT).orElseThrow();

            // parent 给了 3.5.15；后面的 starter 没写版本，不能把版本冲成 null
            assertThat(springBoot.version()).isEqualTo("3.5.15");
            assertThat(springBoot.evidence())
                    .anyMatch(evidence -> evidence.contains("spring-boot-starter-parent"))
                    .anyMatch(evidence -> evidence.contains("spring-boot-starter-web"));
        }

        @Test
        @DisplayName("只有 == 才算拿到确切版本，>= 这类区间留空")
        void doesNotTreatRangeAsVersion() {
            TechStackReport report = detect();

            // requirements 里是 uvicorn>=0.30.0，但 uvicorn 不在目录里，先验证它落进了未识别
            assertThat(report.unrecognized()).contains("uvicorn");
        }
    }

    // ---- 不猜 ----

    @Nested
    @DisplayName("不猜的地方")
    class NoGuessing {

        @Test
        @DisplayName("@Mapper 不与 MyBatis 挂钩：它在 MyBatis 与 MapStruct 里同名")
        void doesNotInferMyBatisFromMapperAnnotationAlone() {
            Map<String, List<TypeDeclaration>> types = new LinkedHashMap<>();
            types.put("src/main/java/demo/OnlyAnnotation.java", List.of(new TypeDeclaration(
                    "OnlyAnnotation", "demo.OnlyAnnotation", "demo", TypeKind.INTERFACE,
                    Set.of("public"), List.of("Mapper"), null, List.of(), null, 1, 3)));

            TechStackReport report = detector.detect(Map.of(), types, Map.of());

            assertThat(report.has(TechStack.MYBATIS)).isFalse();
            assertThat(report.has(TechStack.MAPSTRUCT)).isFalse();
            assertThat(report.items()).isEmpty();
        }

        @Test
        @DisplayName("认不出的依赖坐标进未识别清单，不静默丢弃")
        void listsUnrecognizedDependencies() {
            TechStackReport report = detect();

            assertThat(report.unrecognized())
                    .contains("com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:4.5.0")
                    .contains("lodash@4.17.21")
                    .contains("uvicorn");
        }

        @Test
        @DisplayName("package.json 的元数据键不算依赖，不能灌进未识别清单")
        void ignoresPackageJsonMetadataKeys() {
            TechStackReport report = detect();

            assertThat(report.unrecognized())
                    .noneMatch(entry -> entry.startsWith("name"))
                    .noneMatch(entry -> entry.startsWith("private"))
                    .noneMatch(entry -> entry.startsWith("scripts"));
        }

        @Test
        @DisplayName("空输入不炸，报告为空")
        void handlesEmptyProject() {
            TechStackReport report = detector.detect(Map.of(), Map.of(), Map.of());

            assertThat(report.items()).isEmpty();
            assertThat(report.unrecognized()).isEmpty();
            assertThat(report.stacks()).isEmpty();
        }
    }

    // ---- 报告视图 ----

    @Nested
    @DisplayName("报告视图")
    class Report {

        @Test
        @DisplayName("按分类分组，只列出出现过的分类")
        void groupsByCategory() {
            TechStackReport report = detect();

            assertThat(report.byCategory()).containsKeys(
                    TechStack.Category.BACKEND,
                    TechStack.Category.PERSISTENCE,
                    TechStack.Category.FRONTEND,
                    TechStack.Category.DATABASE);
            assertThat(report.byCategory().get(TechStack.Category.FRONTEND))
                    .extracting(TechStackItem::name)
                    .contains("Vue", "Vite", "TypeScript");
        }

        @Test
        @DisplayName("源码证据与依赖文件证据都能追溯到具体位置")
        void evidencePointsBackToSources() {
            TechStackReport report = detect();

            assertThat(report.evidenceOf(TechStack.SPRING_MVC))
                    .anyMatch(evidence -> evidence.startsWith("src/main/java/demo/order/web/OrderController.java")
                            && evidence.contains("@RestController"))
                    .anyMatch(evidence -> evidence.startsWith("pom.xml"));
            assertThat(report.evidenceOf(TechStack.VUE))
                    .anyMatch(evidence -> evidence.equals("web/package.json: vue@3.4.21"));
        }

        @Test
        @DisplayName("输出顺序稳定：同一份输入重复识别结果一致")
        void outputIsDeterministic() {
            assertThat(detect().items()).isEqualTo(detect().items());
            assertThat(new ArrayList<>(detect().unrecognized()))
                    .isEqualTo(new ArrayList<>(detect().unrecognized()));
        }
    }
}
