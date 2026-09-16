package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.DependencyCoordinate;
import com.codewisdom.analysis.domain.ImportDeclaration;
import com.codewisdom.analysis.domain.TechStack;
import com.codewisdom.analysis.domain.TechStackItem;
import com.codewisdom.analysis.domain.TechStackReport;
import com.codewisdom.analysis.domain.TypeDeclaration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技术栈识别。
 *
 * <p>三路证据，互为补充——只靠任何一路都会漏：
 * <ol>
 *   <li><b>源码 import 前缀</b>：{@code org.mybatis} → MyBatis。没有 pom 也能认出框架。</li>
 *   <li><b>源码注解名</b>：{@code @RestController} → Spring MVC。
 *       这是 <b>Spring MVC 唯一可靠的证据</b>——{@code spring-boot-starter-web} 也会被
 *       非 MVC 项目当 HTTP 客户端依赖引进来。</li>
 *   <li><b>依赖声明文件坐标</b>：{@code pom.xml} / {@code package.json} / {@code requirements.txt}。
 *       这是<b>版本号的唯一来源</b>，也是 Python、前端技术的唯一证据。</li>
 * </ol>
 *
 * <h2>不猜的地方</h2>
 * <ul>
 *   <li><b>{@code @Mapper} 不进注解规则表</b>：MyBatis 与 MapStruct 都用这个名字，
 *       光看注解简单名分不出来。只认 import（{@code org.apache.ibatis} / {@code org.mapstruct}）
 *       与依赖坐标——分不出来就不认，不掷硬币。</li>
 *   <li><b>版本解析不出就是 {@code null}</b>：Maven 里版本常写成 {@code ${property}} 引用，
 *       会先回查 {@code <properties>}；回查不到就留空，<b>不把 {@code ${...}} 原文当版本号输出</b>。
 *       {@code package.json} 与 {@code requirements.txt} 的版本约束（{@code ^} / {@code >=}）
 *       只剥掉前缀字符，不做区间求解。</li>
 *   <li><b>匹配不上规则的依赖坐标全部进 {@link TechStackReport#unrecognized()}</b>，
 *       不静默丢弃——「没认出来」必须是可见的。</li>
 * </ul>
 *
 * <h2>口径</h2>
 * 识别的是「工程里<b>声明或引用</b>了这项技术」，<b>不是</b>「这项技术在运行」。
 * 依赖引了不用、代码里 import 了没跑，都会被识别出来。输出一律按
 * 「辅助分析结果，需人工确认」表述。
 */
@Component
public class TechStackDetector {

    // ---- 规则表 ----

    /** import 包名前缀 → 技术。匹配时按前缀长度从长到短，保证 {@code ..spring.boot} 不被 {@code ..spring} 抢走。 */
    private static final Map<String, TechStack> IMPORT_PREFIXES = new LinkedHashMap<>();

    /** 注解简单名 → 技术。只收不会与其它框架重名的。 */
    private static final Map<String, TechStack> ANNOTATION_RULES = new LinkedHashMap<>();

    /** Maven artifactId → 技术（一个坐标可能同时说明多项，如 starter-web = Spring Boot + Spring MVC）。 */
    private static final Map<String, List<TechStack>> POM_ARTIFACTS = new LinkedHashMap<>();

    /** artifactId 族前缀 → 技术，用于兜住表里没逐条列出同族坐标。 */
    private static final Map<String, List<TechStack>> POM_ARTIFACT_PREFIXES = new LinkedHashMap<>();

    /** package.json 依赖名 → 技术。 */
    private static final Map<String, TechStack> NPM_PACKAGES = new LinkedHashMap<>();

    /** requirements.txt 包名（小写、下划线归一为连字符）→ 技术。 */
    private static final Map<String, TechStack> PYTHON_PACKAGES = new LinkedHashMap<>();

    private static final List<Map.Entry<String, TechStack>> IMPORT_RULES;
    private static final List<Map.Entry<String, List<TechStack>>> POM_PREFIX_RULES;

    static {
        // ---- import 前缀 ----
        IMPORT_PREFIXES.put("org.springframework.boot", TechStack.SPRING_BOOT);
        IMPORT_PREFIXES.put("org.springframework.cloud.openfeign", TechStack.SPRING_CLOUD_OPENFEIGN);
        IMPORT_PREFIXES.put("org.springframework.cloud", TechStack.SPRING_CLOUD);
        IMPORT_PREFIXES.put("org.springframework.web", TechStack.SPRING_MVC);
        IMPORT_PREFIXES.put("org.springframework.security", TechStack.SPRING_SECURITY);
        IMPORT_PREFIXES.put("org.springframework.data.jpa", TechStack.SPRING_DATA_JPA);
        IMPORT_PREFIXES.put("org.springframework.data.redis", TechStack.REDIS);
        IMPORT_PREFIXES.put("org.springframework.amqp", TechStack.RABBITMQ);
        IMPORT_PREFIXES.put("org.springframework.kafka", TechStack.KAFKA);
        IMPORT_PREFIXES.put("org.springframework.aop", TechStack.SPRING_AOP);
        IMPORT_PREFIXES.put("org.springframework.stereotype", TechStack.SPRING_FRAMEWORK);
        IMPORT_PREFIXES.put("org.springframework.context", TechStack.SPRING_FRAMEWORK);
        IMPORT_PREFIXES.put("org.springframework.beans", TechStack.SPRING_FRAMEWORK);
        IMPORT_PREFIXES.put("org.springframework.core", TechStack.SPRING_FRAMEWORK);
        IMPORT_PREFIXES.put("org.springframework", TechStack.SPRING_FRAMEWORK);

        IMPORT_PREFIXES.put("com.baomidou.mybatisplus", TechStack.MYBATIS_PLUS);
        // MyBatis 的**核心**包是 org.apache.ibatis（@Mapper、SqlSession 都在这里），
        // org.mybatis 只是 mybatis-spring 等集成包——两个都要收，只写一个会漏掉大半个真实项目
        IMPORT_PREFIXES.put("org.apache.ibatis", TechStack.MYBATIS);
        IMPORT_PREFIXES.put("org.mybatis", TechStack.MYBATIS);
        IMPORT_PREFIXES.put("org.hibernate", TechStack.HIBERNATE);
        IMPORT_PREFIXES.put("org.mapstruct", TechStack.MAPSTRUCT);
        IMPORT_PREFIXES.put("org.apache.shiro", TechStack.SHIRO);
        IMPORT_PREFIXES.put("org.aspectj", TechStack.SPRING_AOP);
        IMPORT_PREFIXES.put("lombok", TechStack.LOMBOK);

        IMPORT_PREFIXES.put("org.junit.jupiter", TechStack.JUNIT5);
        IMPORT_PREFIXES.put("org.mockito", TechStack.MOCKITO);

        IMPORT_PREFIXES.put("com.alibaba.nacos", TechStack.NACOS);
        IMPORT_PREFIXES.put("com.alibaba.csp.sentinel", TechStack.SENTINEL);
        IMPORT_PREFIXES.put("com.rabbitmq", TechStack.RABBITMQ);
        IMPORT_PREFIXES.put("org.apache.kafka", TechStack.KAFKA);
        IMPORT_PREFIXES.put("redis.clients", TechStack.REDIS);
        IMPORT_PREFIXES.put("io.minio", TechStack.MINIO);
        IMPORT_PREFIXES.put("org.elasticsearch", TechStack.ELASTICSEARCH);
        IMPORT_PREFIXES.put("com.mongodb", TechStack.MONGODB);

        IMPORT_PREFIXES.put("org.flywaydb", TechStack.FLYWAY);
        IMPORT_PREFIXES.put("org.springdoc", TechStack.SPRINGDOC);
        IMPORT_PREFIXES.put("org.treesitter", TechStack.TREE_SITTER);
        IMPORT_PREFIXES.put("org.eclipse.jgit", TechStack.JGIT);
        IMPORT_PREFIXES.put("org.bsc.langgraph4j", TechStack.LANGGRAPH4J);

        // ---- 注解 ----
        ANNOTATION_RULES.put("SpringBootApplication", TechStack.SPRING_BOOT);
        ANNOTATION_RULES.put("ConfigurationProperties", TechStack.SPRING_BOOT);
        ANNOTATION_RULES.put("EnableConfigurationProperties", TechStack.SPRING_BOOT);

        ANNOTATION_RULES.put("RestController", TechStack.SPRING_MVC);
        ANNOTATION_RULES.put("Controller", TechStack.SPRING_MVC);
        ANNOTATION_RULES.put("RestControllerAdvice", TechStack.SPRING_MVC);
        ANNOTATION_RULES.put("ControllerAdvice", TechStack.SPRING_MVC);
        ANNOTATION_RULES.put("RequestMapping", TechStack.SPRING_MVC);
        ANNOTATION_RULES.put("GetMapping", TechStack.SPRING_MVC);
        ANNOTATION_RULES.put("PostMapping", TechStack.SPRING_MVC);
        ANNOTATION_RULES.put("PutMapping", TechStack.SPRING_MVC);
        ANNOTATION_RULES.put("DeleteMapping", TechStack.SPRING_MVC);

        ANNOTATION_RULES.put("FeignClient", TechStack.SPRING_CLOUD_OPENFEIGN);
        ANNOTATION_RULES.put("Aspect", TechStack.SPRING_AOP);

        ANNOTATION_RULES.put("Service", TechStack.SPRING_FRAMEWORK);
        ANNOTATION_RULES.put("Component", TechStack.SPRING_FRAMEWORK);
        ANNOTATION_RULES.put("Repository", TechStack.SPRING_FRAMEWORK);
        ANNOTATION_RULES.put("Configuration", TechStack.SPRING_FRAMEWORK);
        ANNOTATION_RULES.put("Bean", TechStack.SPRING_FRAMEWORK);
        ANNOTATION_RULES.put("Autowired", TechStack.SPRING_FRAMEWORK);
        ANNOTATION_RULES.put("Scheduled", TechStack.SPRING_FRAMEWORK);

        // MyBatis-Plus 独有，不会与别家重名
        ANNOTATION_RULES.put("TableName", TechStack.MYBATIS_PLUS);
        ANNOTATION_RULES.put("TableId", TechStack.MYBATIS_PLUS);
        ANNOTATION_RULES.put("TableField", TechStack.MYBATIS_PLUS);
        ANNOTATION_RULES.put("TableLogic", TechStack.MYBATIS_PLUS);

        // ---- Maven 坐标 ----
        POM_ARTIFACTS.put("spring-boot-starter-parent", List.of(TechStack.SPRING_BOOT));
        POM_ARTIFACTS.put("spring-boot-maven-plugin", List.of(TechStack.SPRING_BOOT));
        POM_ARTIFACTS.put("spring-boot-starter", List.of(TechStack.SPRING_BOOT));
        POM_ARTIFACTS.put("spring-boot-starter-web", List.of(TechStack.SPRING_BOOT, TechStack.SPRING_MVC));
        POM_ARTIFACTS.put("spring-boot-starter-validation", List.of(TechStack.SPRING_BOOT));
        POM_ARTIFACTS.put("spring-boot-starter-actuator", List.of(TechStack.SPRING_BOOT));
        POM_ARTIFACTS.put("spring-boot-starter-aop", List.of(TechStack.SPRING_BOOT, TechStack.SPRING_AOP));
        POM_ARTIFACTS.put("spring-boot-starter-test", List.of(TechStack.SPRING_BOOT, TechStack.JUNIT5));
        POM_ARTIFACTS.put("spring-boot-starter-data-jpa",
                List.of(TechStack.SPRING_BOOT, TechStack.SPRING_DATA_JPA));
        POM_ARTIFACTS.put("spring-boot-starter-data-redis", List.of(TechStack.SPRING_BOOT, TechStack.REDIS));
        POM_ARTIFACTS.put("spring-boot-starter-amqp", List.of(TechStack.SPRING_BOOT, TechStack.RABBITMQ));
        POM_ARTIFACTS.put("spring-boot-starter-security",
                List.of(TechStack.SPRING_BOOT, TechStack.SPRING_SECURITY));
        POM_ARTIFACTS.put("spring-boot-starter-data-mongodb",
                List.of(TechStack.SPRING_BOOT, TechStack.MONGODB));

        POM_ARTIFACTS.put("spring-cloud-dependencies", List.of(TechStack.SPRING_CLOUD));
        POM_ARTIFACTS.put("spring-cloud-alibaba-dependencies", List.of(TechStack.SPRING_CLOUD));
        POM_ARTIFACTS.put("spring-cloud-starter-gateway", List.of(TechStack.SPRING_CLOUD));
        POM_ARTIFACTS.put("spring-cloud-starter-openfeign",
                List.of(TechStack.SPRING_CLOUD, TechStack.SPRING_CLOUD_OPENFEIGN));
        POM_ARTIFACTS.put("spring-cloud-starter-alibaba-nacos-discovery",
                List.of(TechStack.SPRING_CLOUD, TechStack.NACOS));
        POM_ARTIFACTS.put("spring-cloud-starter-alibaba-nacos-config",
                List.of(TechStack.SPRING_CLOUD, TechStack.NACOS));
        POM_ARTIFACTS.put("spring-cloud-starter-alibaba-sentinel",
                List.of(TechStack.SPRING_CLOUD, TechStack.SENTINEL));

        POM_ARTIFACTS.put("mybatis-plus-boot-starter", List.of(TechStack.MYBATIS_PLUS));
        POM_ARTIFACTS.put("mybatis-spring-boot-starter", List.of(TechStack.MYBATIS));
        POM_ARTIFACTS.put("mybatis-plus-extension", List.of(TechStack.MYBATIS_PLUS));
        POM_ARTIFACTS.put("hibernate-core", List.of(TechStack.HIBERNATE));
        POM_ARTIFACTS.put("mapstruct", List.of(TechStack.MAPSTRUCT));
        POM_ARTIFACTS.put("shiro-spring-boot-web-starter", List.of(TechStack.SHIRO));
        POM_ARTIFACTS.put("lombok", List.of(TechStack.LOMBOK));

        POM_ARTIFACTS.put("mysql-connector-j", List.of(TechStack.MYSQL));
        POM_ARTIFACTS.put("mysql-connector-java", List.of(TechStack.MYSQL));
        POM_ARTIFACTS.put("postgresql", List.of(TechStack.POSTGRESQL));
        POM_ARTIFACTS.put("flyway-core", List.of(TechStack.FLYWAY));
        POM_ARTIFACTS.put("minio", List.of(TechStack.MINIO));
        POM_ARTIFACTS.put("springdoc-openapi-starter-webmvc-ui", List.of(TechStack.SPRINGDOC));

        POM_ARTIFACTS.put("langgraph4j-core", List.of(TechStack.LANGGRAPH4J));
        POM_ARTIFACTS.put("tree-sitter", List.of(TechStack.TREE_SITTER));
        POM_ARTIFACTS.put("tree-sitter-java", List.of(TechStack.TREE_SITTER));
        POM_ARTIFACTS.put("org.eclipse.jgit", List.of(TechStack.JGIT));

        POM_ARTIFACT_PREFIXES.put("spring-cloud-starter-alibaba-nacos",
                List.of(TechStack.SPRING_CLOUD, TechStack.NACOS));
        POM_ARTIFACT_PREFIXES.put("spring-cloud-starter-alibaba-sentinel",
                List.of(TechStack.SPRING_CLOUD, TechStack.SENTINEL));
        POM_ARTIFACT_PREFIXES.put("spring-cloud-", List.of(TechStack.SPRING_CLOUD));
        POM_ARTIFACT_PREFIXES.put("spring-boot-starter-data-redis",
                List.of(TechStack.SPRING_BOOT, TechStack.REDIS));
        POM_ARTIFACT_PREFIXES.put("spring-boot-", List.of(TechStack.SPRING_BOOT));
        POM_ARTIFACT_PREFIXES.put("spring-security-", List.of(TechStack.SPRING_SECURITY));
        POM_ARTIFACT_PREFIXES.put("spring-data-jpa", List.of(TechStack.SPRING_DATA_JPA));
        POM_ARTIFACT_PREFIXES.put("mybatis-plus-", List.of(TechStack.MYBATIS_PLUS));
        POM_ARTIFACT_PREFIXES.put("mybatis-", List.of(TechStack.MYBATIS));
        POM_ARTIFACT_PREFIXES.put("shiro-", List.of(TechStack.SHIRO));
        POM_ARTIFACT_PREFIXES.put("nacos-", List.of(TechStack.NACOS));
        POM_ARTIFACT_PREFIXES.put("sentinel-", List.of(TechStack.SENTINEL));
        POM_ARTIFACT_PREFIXES.put("junit-jupiter", List.of(TechStack.JUNIT5));
        POM_ARTIFACT_PREFIXES.put("mockito-", List.of(TechStack.MOCKITO));
        POM_ARTIFACT_PREFIXES.put("flyway-", List.of(TechStack.FLYWAY));
        POM_ARTIFACT_PREFIXES.put("springdoc-", List.of(TechStack.SPRINGDOC));
        POM_ARTIFACT_PREFIXES.put("tree-sitter", List.of(TechStack.TREE_SITTER));
        POM_ARTIFACT_PREFIXES.put("langgraph4j", List.of(TechStack.LANGGRAPH4J));
        POM_ARTIFACT_PREFIXES.put("jedis", List.of(TechStack.REDIS));
        POM_ARTIFACT_PREFIXES.put("lettuce-", List.of(TechStack.REDIS));
        POM_ARTIFACT_PREFIXES.put("redisson-", List.of(TechStack.REDIS));
        POM_ARTIFACT_PREFIXES.put("kafka-", List.of(TechStack.KAFKA));
        POM_ARTIFACT_PREFIXES.put("amqp-client", List.of(TechStack.RABBITMQ));
        POM_ARTIFACT_PREFIXES.put("mongodb-driver", List.of(TechStack.MONGODB));
        POM_ARTIFACT_PREFIXES.put("elasticsearch", List.of(TechStack.ELASTICSEARCH));

        // ---- npm ----
        NPM_PACKAGES.put("vue", TechStack.VUE);
        NPM_PACKAGES.put("vue-router", TechStack.VUE_ROUTER);
        NPM_PACKAGES.put("pinia", TechStack.PINIA);
        NPM_PACKAGES.put("vuex", TechStack.VUEX);
        NPM_PACKAGES.put("react", TechStack.REACT);
        NPM_PACKAGES.put("element-plus", TechStack.ELEMENT_PLUS);
        NPM_PACKAGES.put("ant-design-vue", TechStack.ANT_DESIGN_VUE);
        NPM_PACKAGES.put("axios", TechStack.AXIOS);
        NPM_PACKAGES.put("echarts", TechStack.ECHARTS);
        NPM_PACKAGES.put("vite", TechStack.VITE);
        NPM_PACKAGES.put("typescript", TechStack.TYPESCRIPT);

        // ---- Python ----
        PYTHON_PACKAGES.put("fastapi", TechStack.FASTAPI);
        PYTHON_PACKAGES.put("flask", TechStack.FLASK);
        PYTHON_PACKAGES.put("django", TechStack.DJANGO);
        PYTHON_PACKAGES.put("pydantic", TechStack.PYDANTIC);
        PYTHON_PACKAGES.put("sqlalchemy", TechStack.SQLALCHEMY);
        PYTHON_PACKAGES.put("pandas", TechStack.PANDAS);
        PYTHON_PACKAGES.put("numpy", TechStack.NUMPY);
        PYTHON_PACKAGES.put("pytest", TechStack.PYTEST);

        IMPORT_RULES = IMPORT_PREFIXES.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, TechStack> entry) -> entry.getKey().length())
                        .reversed())
                .toList();
        POM_PREFIX_RULES = POM_ARTIFACT_PREFIXES.entrySet().stream()
                .sorted(Comparator.comparingInt(
                        (Map.Entry<String, List<TechStack>> entry) -> entry.getKey().length()).reversed())
                .toList();
    }

    // ---- 依赖文件解析用的正则 ----

    private static final Pattern NPM_DEPENDENCY_BLOCK = Pattern.compile(
            "\"(?:dev|peer|optional)?[dD]ependencies\"\\s*:\\s*\\{([^}]*)}");
    private static final Pattern NPM_ENTRY = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NPM_VERSION_PREFIX = Pattern.compile("^[\\^~>=<v\\s]+");

    private static final Pattern REQUIREMENT_LINE = Pattern.compile(
            "^([A-Za-z0-9_.\\-]+)\\s*(==|>=|<=|~=|!=|>|<)?\\s*([^\\s;#]*)");

    /**
     * 识别整个工程。
     *
     * @param importsByPath         文件路径 → import 列表（{@code JavaDependencyExtractor#extractImports}）
     * @param typesByPath           文件路径 → 类型声明（{@code JavaStructureExtractor}），用于读注解
     * @param dependencyFilesByPath 依赖声明文件路径 → 文件原文（{@code pom.xml} / {@code package.json} /
     *                              {@code requirements.txt}）；只传这三种，其它文件传进来也不解析
     */
    public TechStackReport detect(Map<String, List<ImportDeclaration>> importsByPath,
                                  Map<String, List<TypeDeclaration>> typesByPath,
                                  Map<String, String> dependencyFilesByPath) {
        Map<TechStack, TechStackItem> found = new LinkedHashMap<>();
        Set<String> unrecognized = new LinkedHashSet<>();

        importsByPath.forEach((path, imports) -> detectFromImports(path, imports, found));
        typesByPath.forEach((path, types) -> detectFromAnnotations(path, types, found));
        dependencyFilesByPath.forEach((path, content) ->
                detectFromDependencyFile(path, content, found, unrecognized));

        return new TechStackReport(new ArrayList<>(found.values()), unrecognized);
    }

    // ---- 源码证据 ----

    private static void detectFromImports(String path,
                                          List<ImportDeclaration> imports,
                                          Map<TechStack, TechStackItem> found) {
        for (ImportDeclaration declaration : imports) {
            String name = declaration.importedQualifiedName();
            for (Map.Entry<String, TechStack> rule : IMPORT_RULES) {
                if (matchesPrefix(name, rule.getKey())) {
                    record(found, rule.getValue(), null, path + ": import " + name);
                    break;
                }
            }
        }
    }

    /** 前缀匹配必须整段对齐，避免 {@code org.springframeworkx} 被 {@code org.springframework} 命中。 */
    private static boolean matchesPrefix(String name, String prefix) {
        return name.equals(prefix) || name.startsWith(prefix + ".");
    }

    private static void detectFromAnnotations(String path,
                                              List<TypeDeclaration> types,
                                              Map<TechStack, TechStackItem> found) {
        for (TypeDeclaration type : types) {
            for (String annotation : type.annotations()) {
                TechStack stack = ANNOTATION_RULES.get(annotation);
                if (stack != null) {
                    record(found, stack, null, path + ": @" + annotation + " 于 " + type.name());
                }
            }
        }
    }

    // ---- 依赖文件证据 ----

    private static void detectFromDependencyFile(String path,
                                                 String content,
                                                 Map<TechStack, TechStackItem> found,
                                                 Set<String> unrecognized) {
        String fileName = fileNameOf(path).toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".xml")) {
            detectFromPom(path, content, found, unrecognized);
        } else if (fileName.equals("package.json")) {
            detectFromPackageJson(path, content, found, unrecognized);
        } else if (fileName.endsWith(".txt") && fileName.contains("requirement")) {
            detectFromRequirements(path, content, found, unrecognized);
        }
    }

    /**
     * Maven POM。
     *
     * <p>解析交给 {@link PomDependencyReader}，与依赖冲突检测共用同一份实现——
     * 「同一份 pom 解析有两份必须同步的代码」是隐患，两边一旦漂移，
     * 技术栈识别与冲突检测会给出互相矛盾的结论。
     */
    private static void detectFromPom(String path,
                                      String content,
                                      Map<TechStack, TechStackItem> found,
                                      Set<String> unrecognized) {
        for (DependencyCoordinate dependency : PomDependencyReader.readDependencies(path, content)) {
            // 未解析的 ${...} 不作为版本号：识别关心的是「版本是多少」，拿不到就是拿不到。
            // （依赖冲突检测的口径不同——那边要把「引用不存在的属性」报成缺陷，所以读取器保留原文。）
            String version = dependency.hasResolvedVersion() ? dependency.version() : null;
            String coordinate = (dependency.groupId() == null ? "" : dependency.groupId() + ":")
                    + dependency.artifactId()
                    + (version == null ? "" : ":" + version);
            String evidence = path + ": " + coordinate;

            List<TechStack> stacks = lookupPomArtifact(dependency.artifactId());
            if (stacks.isEmpty()) {
                unrecognized.add(coordinate);
                continue;
            }
            for (TechStack stack : stacks) {
                record(found, stack, version, evidence);
            }
        }
    }

    private static List<TechStack> lookupPomArtifact(String artifactId) {
        List<TechStack> exact = POM_ARTIFACTS.get(artifactId);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, List<TechStack>> rule : POM_PREFIX_RULES) {
            if (artifactId.startsWith(rule.getKey())) {
                return rule.getValue();
            }
        }
        return List.of();
    }

    /**
     * {@code package.json}。
     *
     * <p>只在 {@code dependencies} / {@code devDependencies} / {@code peerDependencies} /
     * {@code optionalDependencies} 四个块内取键值对——否则 {@code "name"}、{@code "version"}、
     * {@code "scripts"} 这些元数据键会灌进「未识别依赖」清单，把它变成噪音。
     */
    private static void detectFromPackageJson(String path,
                                              String content,
                                              Map<TechStack, TechStackItem> found,
                                              Set<String> unrecognized) {
        Matcher blockMatcher = NPM_DEPENDENCY_BLOCK.matcher(content);
        while (blockMatcher.find()) {
            Matcher entryMatcher = NPM_ENTRY.matcher(blockMatcher.group(1));
            while (entryMatcher.find()) {
                String name = entryMatcher.group(1);
                String version = normalizeNpmVersion(entryMatcher.group(2));
                TechStack stack = NPM_PACKAGES.get(name);
                if (stack == null) {
                    unrecognized.add(name + (version == null ? "" : "@" + version));
                    continue;
                }
                record(found, stack, version, path + ": " + name + (version == null ? "" : "@" + version));
            }
        }
    }

    /** 剥掉 {@code ^} / {@code ~} / {@code >=} 这类范围前缀。不做区间求解，只留字面版本。 */
    private static String normalizeNpmVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String stripped = NPM_VERSION_PREFIX.matcher(raw.trim()).replaceFirst("").trim();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * {@code requirements.txt}。
     *
     * <p>包名统一小写并把下划线归一成连字符（PEP 503 的规范化），否则
     * {@code SQLAlchemy} 与 {@code sqlalchemy} 会被当成两个包。
     * 只有 {@code ==} 才认为拿到了确切版本；{@code >=} 这类是区间，留空。
     */
    private static void detectFromRequirements(String path,
                                               String content,
                                               Map<TechStack, TechStackItem> found,
                                               Set<String> unrecognized) {
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("-")) {
                continue;
            }
            Matcher matcher = REQUIREMENT_LINE.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String name = normalizePythonName(matcher.group(1));
            String operator = matcher.group(2);
            String version = "==".equals(operator) && !matcher.group(3).isBlank()
                    ? matcher.group(3).trim()
                    : null;

            TechStack stack = PYTHON_PACKAGES.get(name);
            if (stack == null) {
                unrecognized.add(name + (version == null ? "" : "==" + version));
                continue;
            }
            record(found, stack, version, path + ": " + line);
        }
    }

    private static String normalizePythonName(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    // ---- 工具 ----

    private static void record(Map<TechStack, TechStackItem> found,
                               TechStack stack,
                               String version,
                               String evidence) {
        found.merge(stack, new TechStackItem(stack, version, List.of(evidence)), TechStackItem::merge);
    }

    private static String fileNameOf(String path) {
        if (path == null) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
