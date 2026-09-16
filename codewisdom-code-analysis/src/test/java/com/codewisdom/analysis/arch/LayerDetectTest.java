package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.LayerAssignment;
import com.codewisdom.analysis.domain.LayerKind;
import com.codewisdom.analysis.domain.LayerReport;
import com.codewisdom.analysis.domain.TypeDeclaration;
import com.codewisdom.analysis.domain.TypeKind;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * T-401 验收：包结构与分层识别。
 *
 * <h2>准确率的口径（重要，别把结论说大）</h2>
 * 断言给出的准确率是<b>在本测试自建的样例集上的</b>结果。样例集是按 RuoYi + Spring Boot 的
 * 真实包结构手工标注的，覆盖了各类分层与易错形态，但它仍然是我自己造的样本，
 * <b>只能说明规则内部自洽，不能外推成「真实工程识别准确」</b>。
 * 真实工程的准确率需要拿独立标注的工程另测。
 *
 * <p>样例里刻意放了两个<b>认不出来</b>的类（{@code model} 包下的 {@code LoginBody}、
 * {@code core.page} 包下的 {@code PageDomain}），并且断言它们就是 UNKNOWN——
 * 分层识别宁可认不出，也不能靠猜把架构图画歪。
 */
@DisplayName("T-401 包结构与分层识别")
class LayerDetectTest {

    private static final String MAIN = "src/main/java";
    private static final String TEST = "src/test/java";

    private final LayerDetector detector = new LayerDetector();
    private final LanguageRegistry registry = new LanguageRegistry();
    private final SourceParser parser = new SourceParser(registry);
    private final JavaStructureExtractor structureExtractor = new JavaStructureExtractor();

    private final Map<String, List<TypeDeclaration>> project = new LinkedHashMap<>();
    private final Map<String, LayerKind> expected = new LinkedHashMap<>();

    private static TypeDeclaration type(String packageName, String name, TypeKind kind, String... annotations) {
        return new TypeDeclaration(name, packageName + "." + name, packageName, kind,
                Set.of("public"), List.of(annotations), null, List.of(), null, 1, 1);
    }

    /** 登记一个样例：既进「待识别工程」，也进人工标注表。 */
    private void sample(String root, String packageName, String name, TypeKind kind,
                        LayerKind want, String... annotations) {
        String path = root + "/" + packageName.replace('.', '/') + "/" + name + ".java";
        TypeDeclaration declaration = type(packageName, name, kind, annotations);
        project.computeIfAbsent(path, key -> new ArrayList<>()).add(declaration);
        expected.put(declaration.qualifiedName(), want);
    }

    // ---- 样例集与准确率 ----

    @Test
    @DisplayName("自建样例集上分层归类全部正确，并打印实测准确率")
    void classifiesSampleProject() {
        // 请求入口
        sample(MAIN, "com.ruoyi.web.controller.system", "SysUserController", TypeKind.CLASS,
                LayerKind.CONTROLLER, "RestController");
        sample(MAIN, "com.ruoyi.web.controller.system", "SysRoleController", TypeKind.CLASS,
                LayerKind.CONTROLLER);
        sample(MAIN, "com.ruoyi.framework.web.controller", "BaseController", TypeKind.CLASS,
                LayerKind.CONTROLLER);

        // 业务层
        sample(MAIN, "com.ruoyi.system.service", "ISysUserService", TypeKind.INTERFACE,
                LayerKind.SERVICE);
        sample(MAIN, "com.ruoyi.system.service.impl", "SysUserServiceImpl", TypeKind.CLASS,
                LayerKind.SERVICE, "Service");
        sample(MAIN, "com.demo.order", "OrderService", TypeKind.CLASS, LayerKind.SERVICE);

        // 持久层
        sample(MAIN, "com.ruoyi.system.mapper", "SysUserMapper", TypeKind.INTERFACE,
                LayerKind.MAPPER, "Mapper");
        sample(MAIN, "com.ruoyi.system.mapper", "SysDeptMapper", TypeKind.INTERFACE,
                LayerKind.MAPPER);
        sample(MAIN, "com.demo.infra.dao", "OrderDao", TypeKind.CLASS, LayerKind.REPOSITORY);
        sample(MAIN, "com.demo.infra.repository", "OrderRepository", TypeKind.INTERFACE,
                LayerKind.REPOSITORY);

        // 实体与 DTO
        sample(MAIN, "com.ruoyi.system.domain", "SysUser", TypeKind.CLASS, LayerKind.ENTITY);
        sample(MAIN, "com.ruoyi.common.core.domain.entity", "SysDept", TypeKind.CLASS,
                LayerKind.ENTITY);
        sample(MAIN, "com.demo.order.entity", "OrderDO", TypeKind.CLASS, LayerKind.ENTITY);
        sample(MAIN, "com.ruoyi.system.domain.vo", "SysUserVo", TypeKind.CLASS, LayerKind.DTO);
        sample(MAIN, "com.demo.order.dto", "CreateOrderRequest", TypeKind.CLASS, LayerKind.DTO);
        sample(MAIN, "com.demo.order.param", "OrderQueryParam", TypeKind.CLASS, LayerKind.DTO);
        sample(MAIN, "com.demo.order.contract", "CreateOrderCommand", TypeKind.CLASS, LayerKind.DTO);

        // 配置与装配
        sample(MAIN, "com.ruoyi.framework.config", "ShiroConfig", TypeKind.CLASS,
                LayerKind.CONFIG, "Configuration");
        sample(MAIN, "com.ruoyi.common.config", "RuoYiConfig", TypeKind.CLASS,
                LayerKind.CONFIG, "ConfigurationProperties");
        sample(MAIN, "com.demo", "MainApplication", TypeKind.CLASS,
                LayerKind.CONFIG, "SpringBootApplication");

        // 横切与基础设施
        sample(MAIN, "com.demo.order.convert", "OrderConverter", TypeKind.CLASS, LayerKind.CONVERTER);
        sample(MAIN, "com.demo.order.client", "RemoteUserClient", TypeKind.CLASS,
                LayerKind.CLIENT, "FeignClient");
        sample(MAIN, "com.demo.order.aspect", "LogAspect", TypeKind.CLASS,
                LayerKind.ASPECT, "Aspect");
        sample(MAIN, "com.ruoyi.common.utils", "StringUtils", TypeKind.CLASS, LayerKind.UTIL);
        sample(MAIN, "com.demo.shared", "DateHelper", TypeKind.CLASS, LayerKind.UTIL);
        sample(MAIN, "com.ruoyi.common.exception", "ServiceException", TypeKind.CLASS,
                LayerKind.EXCEPTION);
        sample(MAIN, "com.demo.core", "BizException", TypeKind.CLASS, LayerKind.EXCEPTION);
        sample(MAIN, "com.ruoyi.common.constant", "Constants", TypeKind.CLASS, LayerKind.CONSTANT);

        // 测试代码：路径信号优先于包名信号
        sample(TEST, "com.ruoyi.system.service", "SysUserServiceTest", TypeKind.CLASS, LayerKind.TEST);

        // 刻意认不出来的两类：包名段与类型名都不表态
        sample(MAIN, "com.ruoyi.common.core.page", "PageDomain", TypeKind.CLASS, LayerKind.UNKNOWN);
        sample(MAIN, "com.ruoyi.common.core.model", "LoginBody", TypeKind.CLASS, LayerKind.UNKNOWN);
        // 对照：model 段不表态，但外层的 domain 段表态，于是判成实体
        // —— 这正是「从最内层往外找」的体现，不是把 model 当成实体包
        sample(MAIN, "com.ruoyi.common.core.domain.model", "UserProfile", TypeKind.CLASS,
                LayerKind.ENTITY);

        LayerReport report = detector.detect(project);

        List<String> mismatches = new ArrayList<>();
        report.assignments().forEach(assignment -> {
            LayerKind want = expected.get(assignment.typeQualifiedName());
            if (want != assignment.layer()) {
                mismatches.add(assignment.typeQualifiedName() + " 期望 " + want
                        + " 实际 " + assignment.layer() + "（依据 " + assignment.rule() + "）");
            }
        });

        int correct = expected.size() - mismatches.size();
        double accuracy = (double) correct / expected.size();
        System.out.printf("分层识别准确率（自建样例集）= %.1f%% (%d/%d)%n",
                accuracy * 100, correct, expected.size());

        assertThat(mismatches).as("归类错误的类型").isEmpty();
        assertThat(accuracy).isEqualTo(1.0);
    }

    // ---- 优先级与信号 ----

    @Nested
    @DisplayName("信号与优先级")
    class Signals {

        @Test
        @DisplayName("注解优先于包名：@RestController 落在 service 包里仍判 controller，且标出冲突")
        void annotationWinsOverPackage() {
            LayerAssignment assignment = detector.detect(
                    MAIN + "/com/demo/order/service/UserController.java",
                    type("com.demo.order.service", "UserController", TypeKind.CLASS, "RestController"));

            assertThat(assignment.layer()).isEqualTo(LayerKind.CONTROLLER);
            assertThat(assignment.rule()).isEqualTo("annotation");
            assertThat(assignment.hasConflict()).isTrue();
            assertThat(assignment.signals()).containsExactly(LayerKind.CONTROLLER, LayerKind.SERVICE);
        }

        @Test
        @DisplayName("包名优先于类型名")
        void packageWinsOverNaming() {
            LayerAssignment assignment = detector.detect(
                    MAIN + "/com/demo/order/controller/UserService.java",
                    type("com.demo.order.controller", "UserService", TypeKind.CLASS));

            assertThat(assignment.layer()).isEqualTo(LayerKind.CONTROLLER);
            assertThat(assignment.rule()).isEqualTo("package");
            assertThat(assignment.hasConflict()).isTrue();
        }

        @Test
        @DisplayName("三个角色信号一致时不算冲突")
        void agreeingSignalsAreNotAConflict() {
            LayerAssignment assignment = detector.detect(
                    MAIN + "/com/demo/order/service/impl/OrderServiceImpl.java",
                    type("com.demo.order.service.impl", "OrderServiceImpl", TypeKind.CLASS, "Service"));

            assertThat(assignment.layer()).isEqualTo(LayerKind.SERVICE);
            assertThat(assignment.hasConflict()).isFalse();
        }

        @Test
        @DisplayName("路径信号是作用域标记，不参与冲突判定")
        void pathSignalDoesNotCreateFalseConflicts() {
            LayerAssignment assignment = detector.detect(
                    TEST + "/com/demo/order/service/OrderServiceTest.java",
                    type("com.demo.order.service", "OrderServiceTest", TypeKind.CLASS));

            assertThat(assignment.layer()).isEqualTo(LayerKind.TEST);
            assertThat(assignment.rule()).isEqualTo("path");
            // 包名与类型名都指向 service，路径指向 test——这是规范做法，不是分层混乱
            assertThat(assignment.hasConflict()).isFalse();
        }

        @Test
        @DisplayName("包名从最内层往外找：..service.impl 命中 service，..domain.vo 命中 vo")
        void packageLookupGoesInnermostOut() {
            assertThat(LayerKind.fromPackageName("com.ruoyi.system.service.impl"))
                    .isEqualTo(LayerKind.SERVICE);
            assertThat(LayerKind.fromPackageName("com.ruoyi.system.domain.vo"))
                    .isEqualTo(LayerKind.DTO);
            assertThat(LayerKind.fromPackageName("com.ruoyi.common.core.page")).isNull();
            assertThat(LayerKind.fromPackageName("")).isNull();
            assertThat(LayerKind.fromPackageName(null)).isNull();
        }

        @Test
        @DisplayName("类型名后缀大小写敏感，Todo 不会被当成 DO 实体")
        void namingSuffixIsCaseSensitive() {
            assertThat(LayerKind.fromTypeName("UserDO")).isEqualTo(LayerKind.ENTITY);
            assertThat(LayerKind.fromTypeName("Todo")).isNull();
            assertThat(LayerKind.fromTypeName("Request")).isEqualTo(LayerKind.DTO);
            assertThat(LayerKind.fromTypeName("Latest")).isNull();
            assertThat(LayerKind.fromTypeName("OrderServiceImpl")).isEqualTo(LayerKind.SERVICE);
        }

        @Test
        @DisplayName("歧义包名段不作为信号，宁可认不出来")
        void ambiguousPackageSegmentsAreNotSignals() {
            LayerAssignment assignment = detector.detect(
                    MAIN + "/com/ruoyi/common/core/model/LoginBody.java",
                    type("com.ruoyi.common.core.model", "LoginBody", TypeKind.CLASS));

            assertThat(assignment.layer()).isEqualTo(LayerKind.UNKNOWN);
            assertThat(assignment.rule()).isEqualTo("none");
            assertThat(assignment.packageSignal()).isNull();
            assertThat(assignment.namingSignal()).isNull();
        }

        @Test
        @DisplayName("model 段不表态但不挡住外层：..domain.model 由 domain 段判定")
        void unopinionatedSegmentFallsThroughToOuterSegment() {
            LayerAssignment assignment = detector.detect(
                    MAIN + "/com/ruoyi/common/core/domain/model/UserProfile.java",
                    type("com.ruoyi.common.core.domain.model", "UserProfile", TypeKind.CLASS));

            assertThat(assignment.layer()).isEqualTo(LayerKind.ENTITY);
            assertThat(assignment.packageSignal()).isEqualTo(LayerKind.ENTITY);
        }
    }

    // ---- 报告视图 ----

    @Nested
    @DisplayName("报告视图")
    class Report {

        private Map<String, List<TypeDeclaration>> mixedProject() {
            Map<String, List<TypeDeclaration>> project = new LinkedHashMap<>();
            project.put(MAIN + "/com/demo/mixed/UserController.java", List.of(
                    type("com.demo.mixed", "UserController", TypeKind.CLASS, "RestController")));
            project.put(MAIN + "/com/demo/mixed/UserService.java", List.of(
                    type("com.demo.mixed", "UserService", TypeKind.CLASS, "Service")));
            project.put(MAIN + "/com/demo/mixed/UserMapper.java", List.of(
                    type("com.demo.mixed", "UserMapper", TypeKind.INTERFACE, "Mapper")));
            // 同包里再放一个测试类，用来验证 TEST 不算混乱
            project.put(TEST + "/com/demo/mixed/UserServiceTest.java", List.of(
                    type("com.demo.mixed", "UserServiceTest", TypeKind.CLASS)));
            return project;
        }

        @Test
        @DisplayName("包结构 → 分层：一个包混了三个角色分层要被标出来")
        void reportsMixedPackages() {
            LayerReport report = detector.detect(mixedProject());

            assertThat(report.mixedPackages()).containsOnlyKeys("com.demo.mixed");
            assertThat(report.mixedPackages().get("com.demo.mixed"))
                    .containsExactlyInAnyOrder(LayerKind.CONTROLLER, LayerKind.SERVICE, LayerKind.MAPPER);
        }

        @Test
        @DisplayName("只剩一个角色分层（哪怕混了测试类）就不算混乱")
        void singleRolePackageIsNotMixed() {
            Map<String, List<TypeDeclaration>> project = new LinkedHashMap<>();
            project.put(MAIN + "/com/demo/order/service/OrderService.java", List.of(
                    type("com.demo.order.service", "OrderService", TypeKind.CLASS, "Service")));
            project.put(TEST + "/com/demo/order/service/OrderServiceTest.java", List.of(
                    type("com.demo.order.service", "OrderServiceTest", TypeKind.CLASS)));

            LayerReport report = detector.detect(project);

            assertThat(report.layersByPackage().get("com.demo.order.service"))
                    .containsExactlyInAnyOrder(LayerKind.SERVICE, LayerKind.TEST);
            assertThat(report.mixedPackages()).isEmpty();
        }

        @Test
        @DisplayName("按分层计数与包索引")
        void aggregatesByLayerAndPackage() {
            LayerReport report = detector.detect(mixedProject());

            assertThat(report.countByLayer())
                    .containsEntry(LayerKind.CONTROLLER, 1L)
                    .containsEntry(LayerKind.SERVICE, 1L)
                    .containsEntry(LayerKind.MAPPER, 1L)
                    .containsEntry(LayerKind.TEST, 1L);
            assertThat(report.packagesOf(LayerKind.MAPPER)).containsExactly("com.demo.mixed");
            assertThat(report.typesOf(LayerKind.TEST)).hasSize(1);
            assertThat(report.unclassified()).isEmpty();
        }

        @Test
        @DisplayName("认不出分层的类型被显式列出，不混进某个分层充数")
        void exposesUnclassifiedTypes() {
            Map<String, List<TypeDeclaration>> project = new LinkedHashMap<>();
            project.put(MAIN + "/com/ruoyi/common/core/page/PageDomain.java", List.of(
                    type("com.ruoyi.common.core.page", "PageDomain", TypeKind.CLASS)));

            LayerReport report = detector.detect(project);

            assertThat(report.unclassified()).extracting(LayerAssignment::typeQualifiedName)
                    .containsExactly("com.ruoyi.common.core.page.PageDomain");
            assertThat(report.layers()).containsExactly(LayerKind.UNKNOWN);
        }

        @Test
        @DisplayName("嵌套类型用它<em>所在文件</em>的包判定，不从限定名反推包名")
        void classifiesNestedTypesByTheirFilePackage() {
            String packageName = "com.ruoyi.system.domain";
            String path = MAIN + "/" + packageName.replace('.', '/') + "/SysUser.java";
            TypeDeclaration outer = new TypeDeclaration("SysUser", packageName + ".SysUser", packageName,
                    TypeKind.CLASS, Set.of("public"), List.of(), null, List.of(), null, 1, 20);
            TypeDeclaration inner = new TypeDeclaration("Status", packageName + ".SysUser.Status",
                    packageName, TypeKind.ENUM, Set.of("public"), List.of(), null, List.of(),
                    packageName + ".SysUser", 3, 6);

            LayerReport report = detector.detect(Map.of(path, List.of(outer, inner)));

            assertThat(report.assignments()).extracting(LayerAssignment::layer)
                    .containsOnly(LayerKind.ENTITY);
            // 若从限定名反推包名，嵌套类型会得到 "com.ruoyi.system.domain.SysUser"
            assertThat(report.assignments()).extracting(LayerAssignment::packageName)
                    .containsOnly(packageName);
            assertThat(report.packagesOf(LayerKind.ENTITY)).containsExactly(packageName);
        }

        @Test
        @DisplayName("输出顺序稳定：同一份输入重复识别结果一致")
        void outputIsOrderIndependent() {
            Map<String, List<TypeDeclaration>> project = mixedProject();
            Map<String, List<TypeDeclaration>> reversed = new LinkedHashMap<>();
            List<String> keys = new ArrayList<>(project.keySet());
            for (int i = keys.size() - 1; i >= 0; i--) {
                reversed.put(keys.get(i), project.get(keys.get(i)));
            }

            assertThat(detector.detect(reversed).assignments())
                    .isEqualTo(detector.detect(project).assignments());
        }
    }

    // ---- 与真实解析链路打通 ----

    @Nested
    @DisplayName("与解析链路打通")
    class Pipeline {

        private static final String CONTROLLER_SOURCE = """
                package demo.order.web;

                @RestController
                public class OrderController {
                    public String list() {
                        return "";
                    }
                }
                """;

        private static final String SERVICE_SOURCE = """
                package demo.order.service;

                @Service
                public class OrderService {
                    public String load() {
                        return "";
                    }
                }
                """;

        @Test
        @DisplayName("Tree-Sitter 抽出的类型声明能直接喂给分层识别")
        void detectsLayersFromParsedSources() {
            Map<String, List<TypeDeclaration>> project = new LinkedHashMap<>();
            project.put(MAIN + "/demo/order/web/OrderController.java",
                    extractTypes(CONTROLLER_SOURCE));
            project.put(MAIN + "/demo/order/service/OrderService.java",
                    extractTypes(SERVICE_SOURCE));
            project.put(TEST + "/demo/order/service/OrderServiceTest.java",
                    extractTypes(SERVICE_SOURCE));

            LayerReport report = detector.detect(project);

            assertThat(report.assignments()).extracting(
                            LayerAssignment::typeQualifiedName, LayerAssignment::layer)
                    .containsExactlyInAnyOrder(
                            tuple("demo.order.web.OrderController", LayerKind.CONTROLLER),
                            tuple("demo.order.service.OrderService", LayerKind.SERVICE),
                            tuple("demo.order.service.OrderService", LayerKind.TEST));
        }

        private List<TypeDeclaration> extractTypes(String source) {
            try (ParseHandle handle = parser.parse("java", source)) {
                SourceStructure structure = structureExtractor.extract(handle);
                return structure.types();
            }
        }
    }
}
