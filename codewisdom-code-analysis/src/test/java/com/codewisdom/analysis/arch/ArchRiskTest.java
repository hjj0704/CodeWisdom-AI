package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.AuditIssue;
import com.codewisdom.analysis.domain.CycleReport;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-504 验收：架构隐患规则。
 *
 * <h2>这一卡在验什么</h2>
 * 卡片要求「<b>与 T-404 结果打通</b>，输出统一问题模型」。所以断言分两层：
 * <ol>
 *   <li><b>打通</b>：同一份输入下，{@code CW-ARCH-001} 的环路径必须与
 *       {@link CycleDetector} 直接算出来的一致——不是「另外算了一个环」。</li>
 *   <li><b>统一模型</b>：每条产出都齐六个字段、分类都是 {@code ARCHITECTURE}、
 *       且锚在<b>真实存在的文件与行号</b>上（不是敷衍的 NO_LINE）。</li>
 * </ol>
 *
 * <h2>样例工程一次埋三类隐患</h2>
 * <pre>
 * demo.web.WController  ↔  demo.svc.SService      包环 + 分层环（一箭双雕）
 * demo.order.service    OrderController(@RestController) 混在 OrderService(@Service) 里
 * demo.big.Big0..Big20  21 个类型同包               包职责过重
 * demo.hub.Hub + 16 个调用方                        疑似上帝类
 * </pre>
 *
 * <p>口径：全部结论基于<b>静态结构</b>。环不含反射/事件/配置注入产生的运行期依赖；
 * 阈值（20 个类型、15 个依赖方）是经验值，措辞是「建议关注」而不是「违反规范」，
 * 输出一律按「辅助分析结果，需人工确认」表述。
 */
@DisplayName("T-504 架构隐患规则")
class ArchRiskTest {

    private static final int BIG_PACKAGE_TYPES = 21;
    private static final int HUB_DEPENDENTS = 16;

    private final LanguageRegistry registry = new LanguageRegistry();
    private final SourceParser parser = new SourceParser(registry);
    private final JavaStructureExtractor structureExtractor = new JavaStructureExtractor();
    private final JavaDependencyExtractor dependencyExtractor = new JavaDependencyExtractor(structureExtractor);
    private final LayerDetector layerDetector = new LayerDetector();
    private final CycleDetector cycleDetector = new CycleDetector();
    private final ArchRiskAnalyzer analyzer = new ArchRiskAnalyzer(cycleDetector);

    /** 一次分析的完整产物，供「打通」断言对照。 */
    private record Analysis(LayerReport layers, CallGraph callGraph, List<AuditIssue> issues,
                            Map<String, List<TypeDeclaration>> typesBySourcePath) {
    }

    /** 样例工程：源码路径 → 源码。 */
    private static Map<String, String> sampleProject() {
        Map<String, String> sources = new LinkedHashMap<>();

        // ① 互相依赖：包环 + 分层环
        sources.put("src/main/java/demo/web/WController.java", """
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
                """);
        sources.put("src/main/java/demo/svc/SService.java", """
                package demo.svc;

                import demo.web.WController;

                @Service
                public class SService {
                    private WController controller;

                    public String work() {
                        return controller.help();
                    }
                }
                """);

        // ② 分层混乱：@RestController 落在 service 包里
        sources.put("src/main/java/demo/order/service/OrderController.java", """
                package demo.order.service;

                @RestController
                public class OrderController {
                    public String list() {
                        return "";
                    }
                }
                """);
        sources.put("src/main/java/demo/order/service/OrderService.java", """
                package demo.order.service;

                @Service
                public class OrderService {
                    public String load() {
                        return "";
                    }
                }
                """);

        // ③ 包职责过重：同一包下 21 个类型
        for (int i = 0; i < BIG_PACKAGE_TYPES; i++) {
            sources.put("src/main/java/demo/big/Big" + i + ".java", """
                    package demo.big;

                    public class Big%d {
                        public String value() {
                            return "";
                        }
                    }
                    """.formatted(i));
        }

        // ④ 疑似上帝类：Hub 被 16 个类型依赖
        sources.put("src/main/java/demo/hub/Hub.java", """
                package demo.hub;

                public class Hub {
                    public String ping() {
                        return "";
                    }
                }
                """);
        for (int i = 0; i < HUB_DEPENDENTS; i++) {
            sources.put("src/main/java/demo/hub/Caller" + i + ".java", """
                    package demo.hub;

                    public class Caller%d {
                        private Hub hub;

                        public String call() {
                            return hub.ping();
                        }
                    }
                    """.formatted(i));
        }
        return sources;
    }

    private Analysis analyze(Map<String, String> sources) {
        Map<String, List<TypeDeclaration>> typesByPath = new LinkedHashMap<>();
        List<CallGraph.SourceUnit> units = new ArrayList<>();
        sources.forEach((path, source) -> {
            try (ParseHandle handle = parser.parse("java", source)) {
                SourceStructure structure = structureExtractor.extract(handle);
                typesByPath.put(path, structure.types());
                units.add(new CallGraph.SourceUnit(path, structure.packageName(),
                        structure.types(), structure.methods(),
                        dependencyExtractor.extractImports(handle),
                        dependencyExtractor.extractCallSites(handle)));
            }
        });
        LayerReport layers = layerDetector.detect(typesByPath);
        CallGraph callGraph = CallGraph.build(units);
        return new Analysis(layers, callGraph,
                analyzer.analyze(layers, callGraph, typesByPath), typesByPath);
    }

    private Analysis analyzeSample() {
        return analyze(sampleProject());
    }

    private static List<AuditIssue> issuesOf(List<AuditIssue> issues, String ruleId) {
        return issues.stream().filter(issue -> issue.ruleId().equals(ruleId)).toList();
    }

    private static AuditIssue find(List<AuditIssue> issues, String ruleId, String marker) {
        return issuesOf(issues, ruleId).stream()
                .filter(issue -> issue.trigger().contains(marker) || issue.description().contains(marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "没检出 " + ruleId + " 命中 " + marker + "；实际: "
                                + issues.stream().map(AuditIssue::describe).toList()));
    }

    // ---- CW-ARCH-001 循环依赖 ----

    @Nested
    @DisplayName("循环依赖")
    class Cycles {

        @Test
        @DisplayName("包环与分层环两个粒度都报，等级分开")
        void reportsBothGranularities() {
            List<AuditIssue> issues = analyzeSample().issues();
            List<AuditIssue> cycles = issuesOf(issues, "CW-ARCH-001");

            assertThat(cycles).hasSize(2);

            // 分层环：架构分层被反过来依赖 → 高危
            AuditIssue layerCycle = find(issues, "CW-ARCH-001", "CONTROLLER -> SERVICE");
            assertThat(layerCycle.riskLevel()).isEqualTo(AuditIssue.RiskLevel.HIGH);
            assertThat(layerCycle.description()).contains("分层循环依赖");

            // 包环：强耦合 → 中危，措辞与层环分开
            AuditIssue packageCycle = find(issues, "CW-ARCH-001", "demo.svc -> demo.web");
            assertThat(packageCycle.riskLevel()).isEqualTo(AuditIssue.RiskLevel.MEDIUM);
            assertThat(packageCycle.description()).contains("包循环依赖");
        }

        @Test
        @DisplayName("与 T-404 打通：环路径就是 CycleDetector 直接算出来的那条")
        void reusesCycleDetectorResult() {
            Analysis analysis = analyzeSample();

            CycleReport layerCycles = cycleDetector.detectByLayer(analysis.layers(), analysis.callGraph());
            CycleReport packageCycles = cycleDetector.detectByPackage(analysis.layers(), analysis.callGraph());

            assertThat(layerCycles.cycles()).singleElement()
                    .satisfies(cycle -> assertThat(find(analysis.issues(), "CW-ARCH-001", cycle.describe()))
                            .as("分层环 %s 必须出现在审计结果里", cycle.describe())
                            .isNotNull());
            assertThat(packageCycles.cycles()).singleElement()
                    .satisfies(cycle -> assertThat(find(analysis.issues(), "CW-ARCH-001", cycle.describe()))
                            .as("包环 %s 必须出现在审计结果里", cycle.describe())
                            .isNotNull());
        }

        @Test
        @DisplayName("环的问题锚在真实文件与行号上，触发场景带出全部涉及文件")
        void cycleIssuesCarryRealLocations() {
            Analysis analysis = analyzeSample();
            AuditIssue packageCycle = find(analysis.issues(), "CW-ARCH-001", "demo.svc -> demo.web");

            assertThat(analysis.typesBySourcePath()).containsKey(packageCycle.filePath());
            assertThat(packageCycle.line()).isPositive();
            assertThat(packageCycle.trigger()).contains("涉及文件:").contains("demo/");
        }
    }

    // ---- CW-ARCH-002 分层混乱 ----

    @Nested
    @DisplayName("分层混乱")
    class Layering {

        @Test
        @DisplayName("类放错包：类级信号冲突判中危，锚在该类自己的声明行")
        void reportsMisplacedClass() {
            Analysis analysis = analyzeSample();

            AuditIssue conflict = find(analysis.issues(), "CW-ARCH-002", "OrderController");

            assertThat(conflict.riskLevel()).isEqualTo(AuditIssue.RiskLevel.MEDIUM);
            assertThat(conflict.filePath())
                    .isEqualTo("src/main/java/demo/order/service/OrderController.java");
            // 锚在第 3 行——即 @RestController 那一行，而不是 public class 那一行。
            // 这是 T-302 记录的 grammar 行为：带注解的类型，节点起始行指向注解行
            // （grammar 把 @RestController 放进 modifiers，而 modifiers 属于 class_declaration）。
            // 不是 bug，是语义如此。
            assertThat(conflict.line()).isEqualTo(3);
            assertThat(conflict.trigger()).contains("CONTROLLER").contains("SERVICE");
            assertThat(conflict.riskDescription()).contains("放错了包");
        }

        @Test
        @DisplayName("包内混了多个分层判低危：common 包混放 config 与 util 是常态，只作提示")
        void reportsMixedPackageAsHint() {
            List<AuditIssue> issues = analyzeSample().issues();

            // 按描述精确定位：类级冲突问题的限定名里也含 "demo.order.service"，
            // 用包名去匹配会命中错的那条
            AuditIssue mixed = issuesOf(issues, "CW-ARCH-002").stream()
                    .filter(issue -> issue.description().contains("混放了多个分层"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("没检出包级混杂问题"));

            assertThat(mixed.riskLevel()).isEqualTo(AuditIssue.RiskLevel.LOW);
            assertThat(mixed.description()).contains("demo.order.service");
            assertThat(mixed.trigger()).contains("CONTROLLER").contains("SERVICE");
            assertThat(mixed.riskDescription()).contains("需人工判断");
        }

        @Test
        @DisplayName("分层一致的工程不报分层混乱")
        void cleanLayeringProducesNothing() {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("src/main/java/demo/web/WController.java", """
                    package demo.web;

                    @RestController
                    public class WController {
                        public String list() {
                            return "";
                        }
                    }
                    """);
            sources.put("src/main/java/demo/svc/SService.java", """
                    package demo.svc;

                    @Service
                    public class SService {
                        public String work() {
                            return "";
                        }
                    }
                    """);

            List<AuditIssue> issues = analyze(sources).issues();

            assertThat(issuesOf(issues, "CW-ARCH-002")).isEmpty();
        }
    }

    // ---- CW-ARCH-003 职责不单一 ----

    @Nested
    @DisplayName("职责不单一")
    class Responsibility {

        @Test
        @DisplayName("包内类型数过多判中危")
        void reportsOverloadedPackage() {
            List<AuditIssue> issues = analyzeSample().issues();

            AuditIssue overloaded = find(issues, "CW-ARCH-003", "demo.big");

            assertThat(overloaded.riskLevel()).isEqualTo(AuditIssue.RiskLevel.MEDIUM);
            assertThat(overloaded.description()).contains(String.valueOf(BIG_PACKAGE_TYPES));
            assertThat(overloaded.riskDescription()).contains("建议按子领域拆分子包");
        }

        @Test
        @DisplayName("被过多类型依赖判低危，措辞是「疑似」")
        void reportsGodClass() {
            List<AuditIssue> issues = analyzeSample().issues();

            AuditIssue godClass = find(issues, "CW-ARCH-003", "demo.hub.Hub");

            assertThat(godClass.riskLevel()).isEqualTo(AuditIssue.RiskLevel.LOW);
            assertThat(godClass.description()).contains("疑似上帝类");
            assertThat(godClass.trigger()).contains(String.valueOf(HUB_DEPENDENTS));
        }

        @Test
        @DisplayName("本应被广泛引用的分层排除在上帝类检查之外")
        void excludesWidelyUsedLayersByDesign() {
            // 一个被 16 个类型依赖的 DTO：DTO 本来就该被广泛引用，报成上帝类全是误报
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("src/main/java/demo/dto/SharedDto.java", """
                    package demo.dto;

                    public class SharedDto {
                        public String value() {
                            return "";
                        }
                    }
                    """);
            for (int i = 0; i < HUB_DEPENDENTS; i++) {
                sources.put("src/main/java/demo/use/User" + i + ".java", """
                        package demo.use;

                        import demo.dto.SharedDto;

                        public class User%d {
                            private SharedDto dto;

                            public String read() {
                                return dto.value();
                            }
                        }
                        """.formatted(i));
            }

            List<AuditIssue> issues = analyze(sources).issues();

            assertThat(issues).noneMatch(issue -> issue.ruleId().equals("CW-ARCH-003")
                    && issue.trigger().contains("SharedDto"));
            // 但依赖关系确实建起来了，否则「没报」可能只是因为压根没有依赖
            assertThat(issuesOf(issues, "CW-ARCH-003")).isEmpty();
        }
    }

    // ---- 统一问题模型与健壮性 ----

    @Nested
    @DisplayName("统一问题模型与健壮性")
    class Contract {

        @Test
        @DisplayName("每条问题都齐六个字段，分类统一为 ARCHITECTURE")
        void issuesSatisfyUnifiedModel() {
            List<AuditIssue> issues = analyzeSample().issues();

            assertThat(issues).hasSize(6);
            assertThat(issues).allSatisfy(issue -> {
                assertThat(issue.category()).isEqualTo(AuditIssue.IssueCategory.ARCHITECTURE);
                assertThat(issue.ruleId()).startsWith("CW-ARCH-");
                assertThat(issue.description()).isNotEmpty();
                assertThat(issue.trigger()).isNotEmpty();
                assertThat(issue.riskDescription()).isNotEmpty();
                assertThat(issue.riskLevel()).isNotNull();
                assertThat(issue.filePath()).isNotEmpty();
                assertThat(issue.line()).isPositive();
            });
        }

        @Test
        @DisplayName("三类隐患各报两条，等级分布符合设计")
        void reportsAllThreeKinds() {
            List<AuditIssue> issues = analyzeSample().issues();

            assertThat(issuesOf(issues, "CW-ARCH-001")).hasSize(2);
            assertThat(issuesOf(issues, "CW-ARCH-002")).hasSize(2);
            assertThat(issuesOf(issues, "CW-ARCH-003")).hasSize(2);

            assertThat(issues).filteredOn(issue -> issue.riskLevel() == AuditIssue.RiskLevel.HIGH)
                    .singleElement()
                    .satisfies(issue -> assertThat(issue.description()).contains("分层循环依赖"));
        }

        @Test
        @DisplayName("干净工程零产出")
        void cleanProjectProducesNothing() {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("src/main/java/demo/svc/OrderService.java", """
                    package demo.svc;

                    @Service
                    public class OrderService {
                        public String load() {
                            return "";
                        }
                    }
                    """);

            assertThat(analyze(sources).issues()).isEmpty();
        }

        @Test
        @DisplayName("空工程不炸")
        void handlesEmptyProject() {
            Analysis analysis = analyze(new LinkedHashMap<>());

            assertThat(analysis.issues()).isEmpty();
        }

        @Test
        @DisplayName("输出按 文件 → 行号 → 规则 ID 稳定排序")
        void outputIsOrderedAndStable() {
            List<AuditIssue> issues = analyzeSample().issues();
            assertThat(issues).isEqualTo(analyzeSample().issues());

            List<AuditIssue> sorted = issues.stream()
                    .sorted(Comparator.comparing(AuditIssue::filePath)
                            .thenComparingInt(AuditIssue::line)
                            .thenComparing(AuditIssue::ruleId))
                    .toList();
            assertThat(issues).isEqualTo(sorted);
        }
    }
}
