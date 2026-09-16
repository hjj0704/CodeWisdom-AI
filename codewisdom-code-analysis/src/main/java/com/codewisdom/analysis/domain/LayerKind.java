package com.codewisdom.analysis.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 架构分层。
 *
 * <p>取值对齐 {@code docs/architecture.md} §3 约定的模块内部结构
 * （controller / service / domain / mapper / entity / dto / convert / config / client），
 * 再补上 Java 工程里同样常见的 util / exception / constant / aspect / repository，
 * 以及用于隔离测试代码的 {@link #TEST}。
 *
 * <p><b>三张映射表集中在这里</b>，不让「什么算 controller」这种判断散落到多处：
 * <pre>
 * PACKAGE_SEGMENTS   包名段   → 分层   （包名从右往左取第一个命中的段）
 * ANNOTATIONS        注解简单名 → 分层
 * NAME_SUFFIXES      类型名后缀 → 分层
 * </pre>
 *
 * <p>判定优先级不在本类，在 {@link LayerAssignment#layer()}。
 */
public enum LayerKind {

    /** 请求入口。{@code @RestController} / {@code @Controller} / {@code *Controller} */
    CONTROLLER,

    /** 业务编排。{@code @Service} / {@code *Service} / {@code *ServiceImpl} */
    SERVICE,

    /** 持久层接口。{@code @Mapper} / {@code *Mapper}（MyBatis） */
    MAPPER,

    /** 仓储。{@code dao} / {@code repository} 包，{@code *Dao} / {@code *Repository} */
    REPOSITORY,

    /** 数据实体。{@code entity} / {@code domain} / {@code po} 包，{@code *Entity} 等 */
    ENTITY,

    /** 数据传输对象。{@code dto} / {@code vo} / {@code request} 等包，{@code *DTO} 等 */
    DTO,

    /** 配置。{@code @Configuration} / {@code @ConfigurationProperties} / {@code *Config} */
    CONFIG,

    /** 对象转换。{@code convert} 包，{@code *Converter} / {@code *Assembler} */
    CONVERTER,

    /** 外部服务调用。{@code @FeignClient} / {@code *Client} */
    CLIENT,

    /** 工具。{@code util} 包，{@code *Utils} / {@code *Helper} */
    UTIL,

    /** 异常。{@code exception} 包，{@code *Exception} */
    EXCEPTION,

    /** 常量。{@code constant} 包，{@code *Constants} */
    CONSTANT,

    /** 切面。{@code @Aspect} / {@code *Aspect} */
    ASPECT,

    /**
     * 测试代码——由<b>文件路径</b>判定，不是由包名或名称判定。
     *
     * <p>单列一层是为了把它挡在架构图与分层统计之外：测试类通常与被测类同包同名加 {@code Test} 后缀，
     * 不隔离的话 {@code service} 层会被测试类灌满，分层统计与循环依赖检测都会被带偏。
     */
    TEST,

    /** 认不出来。宁可显式标注，也不硬塞进某个分层充数。 */
    UNKNOWN;

    /**
     * 包名段 → 分层。
     *
     * <p><b>刻意不收的段</b>：{@code model} / {@code pojo} / {@code bean} / {@code core} / {@code common}。
     * 这些包里实体、DTO、值对象混在一起是常态，凭段名判断等于猜——猜错会直接污染分层统计，
     * 不如留空让类型名后缀或注解去决定，都决定不了就是 {@link #UNKNOWN}。
     */
    private static final Map<String, LayerKind> PACKAGE_SEGMENTS = new LinkedHashMap<>();

    /** 注解简单名 → 分层。泛化的 {@code @Component} 不表态——它什么层都能挂。 */
    private static final Map<String, LayerKind> ANNOTATIONS = new LinkedHashMap<>();

    /** 类型名后缀 → 分层，按声明顺序匹配。同一分层内的长短后缀都写上，避免遗漏。 */
    private static final Map<String, LayerKind> NAME_SUFFIXES = new LinkedHashMap<>();

    static {
        PACKAGE_SEGMENTS.put("controller", CONTROLLER);
        PACKAGE_SEGMENTS.put("controllers", CONTROLLER);
        PACKAGE_SEGMENTS.put("web", CONTROLLER);
        PACKAGE_SEGMENTS.put("api", CONTROLLER);

        PACKAGE_SEGMENTS.put("service", SERVICE);
        PACKAGE_SEGMENTS.put("services", SERVICE);

        PACKAGE_SEGMENTS.put("mapper", MAPPER);
        PACKAGE_SEGMENTS.put("mappers", MAPPER);

        PACKAGE_SEGMENTS.put("dao", REPOSITORY);
        PACKAGE_SEGMENTS.put("repository", REPOSITORY);
        PACKAGE_SEGMENTS.put("repositories", REPOSITORY);

        PACKAGE_SEGMENTS.put("entity", ENTITY);
        PACKAGE_SEGMENTS.put("entities", ENTITY);
        PACKAGE_SEGMENTS.put("domain", ENTITY);
        PACKAGE_SEGMENTS.put("po", ENTITY);

        PACKAGE_SEGMENTS.put("dto", DTO);
        PACKAGE_SEGMENTS.put("dtos", DTO);
        PACKAGE_SEGMENTS.put("vo", DTO);
        PACKAGE_SEGMENTS.put("vos", DTO);
        PACKAGE_SEGMENTS.put("request", DTO);
        PACKAGE_SEGMENTS.put("response", DTO);
        PACKAGE_SEGMENTS.put("query", DTO);
        PACKAGE_SEGMENTS.put("param", DTO);
        PACKAGE_SEGMENTS.put("form", DTO);

        PACKAGE_SEGMENTS.put("config", CONFIG);
        PACKAGE_SEGMENTS.put("configuration", CONFIG);
        PACKAGE_SEGMENTS.put("properties", CONFIG);
        PACKAGE_SEGMENTS.put("setting", CONFIG);
        PACKAGE_SEGMENTS.put("settings", CONFIG);

        PACKAGE_SEGMENTS.put("convert", CONVERTER);
        PACKAGE_SEGMENTS.put("converter", CONVERTER);
        PACKAGE_SEGMENTS.put("converters", CONVERTER);
        PACKAGE_SEGMENTS.put("mapstruct", CONVERTER);

        PACKAGE_SEGMENTS.put("client", CLIENT);
        PACKAGE_SEGMENTS.put("clients", CLIENT);
        PACKAGE_SEGMENTS.put("feign", CLIENT);
        PACKAGE_SEGMENTS.put("remote", CLIENT);
        PACKAGE_SEGMENTS.put("rpc", CLIENT);

        PACKAGE_SEGMENTS.put("util", UTIL);
        PACKAGE_SEGMENTS.put("utils", UTIL);
        PACKAGE_SEGMENTS.put("utility", UTIL);
        PACKAGE_SEGMENTS.put("helper", UTIL);
        PACKAGE_SEGMENTS.put("helpers", UTIL);

        PACKAGE_SEGMENTS.put("exception", EXCEPTION);
        PACKAGE_SEGMENTS.put("exceptions", EXCEPTION);
        PACKAGE_SEGMENTS.put("error", EXCEPTION);
        PACKAGE_SEGMENTS.put("errors", EXCEPTION);

        PACKAGE_SEGMENTS.put("constant", CONSTANT);
        PACKAGE_SEGMENTS.put("constants", CONSTANT);

        PACKAGE_SEGMENTS.put("aspect", ASPECT);
        PACKAGE_SEGMENTS.put("aspects", ASPECT);
        PACKAGE_SEGMENTS.put("aop", ASPECT);

        ANNOTATIONS.put("RestController", CONTROLLER);
        ANNOTATIONS.put("Controller", CONTROLLER);
        ANNOTATIONS.put("RestControllerAdvice", CONTROLLER);
        ANNOTATIONS.put("ControllerAdvice", CONTROLLER);

        ANNOTATIONS.put("Service", SERVICE);

        ANNOTATIONS.put("Mapper", MAPPER);

        ANNOTATIONS.put("Repository", REPOSITORY);

        ANNOTATIONS.put("Entity", ENTITY);
        ANNOTATIONS.put("TableName", ENTITY);
        ANNOTATIONS.put("Document", ENTITY);

        // 启动类归到配置：它整份都是装配与配置，没有业务
        ANNOTATIONS.put("Configuration", CONFIG);
        ANNOTATIONS.put("ConfigurationProperties", CONFIG);
        ANNOTATIONS.put("SpringBootApplication", CONFIG);
        ANNOTATIONS.put("ComponentScan", CONFIG);
        ANNOTATIONS.put("AutoConfiguration", CONFIG);

        ANNOTATIONS.put("FeignClient", CLIENT);

        ANNOTATIONS.put("Aspect", ASPECT);

        // 后缀匹配按声明顺序，长后缀写在短后缀之前
        NAME_SUFFIXES.put("ServiceImpl", SERVICE);
        NAME_SUFFIXES.put("Service", SERVICE);

        NAME_SUFFIXES.put("Controller", CONTROLLER);
        NAME_SUFFIXES.put("Resource", CONTROLLER);
        NAME_SUFFIXES.put("Endpoint", CONTROLLER);

        NAME_SUFFIXES.put("Mapper", MAPPER);

        NAME_SUFFIXES.put("Repository", REPOSITORY);
        NAME_SUFFIXES.put("Dao", REPOSITORY);

        NAME_SUFFIXES.put("Entity", ENTITY);
        NAME_SUFFIXES.put("DO", ENTITY);
        NAME_SUFFIXES.put("PO", ENTITY);

        NAME_SUFFIXES.put("DTO", DTO);
        NAME_SUFFIXES.put("VO", DTO);
        NAME_SUFFIXES.put("BO", DTO);
        NAME_SUFFIXES.put("Request", DTO);
        NAME_SUFFIXES.put("Response", DTO);
        NAME_SUFFIXES.put("Query", DTO);
        NAME_SUFFIXES.put("Param", DTO);
        NAME_SUFFIXES.put("Params", DTO);
        NAME_SUFFIXES.put("Form", DTO);
        NAME_SUFFIXES.put("Command", DTO);
        NAME_SUFFIXES.put("Cmd", DTO);

        NAME_SUFFIXES.put("Configuration", CONFIG);
        NAME_SUFFIXES.put("Config", CONFIG);
        NAME_SUFFIXES.put("Properties", CONFIG);
        NAME_SUFFIXES.put("Settings", CONFIG);

        NAME_SUFFIXES.put("Converter", CONVERTER);
        NAME_SUFFIXES.put("Convertor", CONVERTER);
        NAME_SUFFIXES.put("Assembler", CONVERTER);

        NAME_SUFFIXES.put("FeignClient", CLIENT);
        NAME_SUFFIXES.put("Client", CLIENT);

        NAME_SUFFIXES.put("Utils", UTIL);
        NAME_SUFFIXES.put("Util", UTIL);
        NAME_SUFFIXES.put("Utility", UTIL);
        NAME_SUFFIXES.put("Helper", UTIL);
        NAME_SUFFIXES.put("Tools", UTIL);

        NAME_SUFFIXES.put("Exception", EXCEPTION);
        NAME_SUFFIXES.put("Error", EXCEPTION);

        NAME_SUFFIXES.put("Constants", CONSTANT);
        NAME_SUFFIXES.put("Constant", CONSTANT);

        NAME_SUFFIXES.put("Aspect", ASPECT);

        NAME_SUFFIXES.put("Tests", TEST);
        NAME_SUFFIXES.put("Test", TEST);
        NAME_SUFFIXES.put("ITCase", TEST);
    }

    /**
     * 按包名段判断。
     *
     * <p>从<b>最内层</b>往外找第一个能命中的段：{@code ..service.impl} 里 {@code impl} 不表态，
     * 继续往外命中 {@code service}；{@code ..domain.vo} 则直接命中 {@code vo}（DTO）而不是 {@code domain}（实体）。
     * 由内向外是刻意的——越靠内的包越具体，越能反映这个类实际放在哪。
     *
     * @param packageName 包名；空或 null 返回 {@code null}
     * @return 命中的分层；没命中返回 {@code null}（<b>不是</b> {@link #UNKNOWN}，UNKNOWN 由上层兜底）
     */
    public static LayerKind fromPackageName(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return null;
        }
        String[] segments = packageName.split("\\.");
        for (int i = segments.length - 1; i >= 0; i--) {
            LayerKind layer = PACKAGE_SEGMENTS.get(segments[i].trim());
            if (layer != null) {
                return layer;
            }
        }
        return null;
    }

    /**
     * 按注解简单名判断，取第一个表态的注解。
     *
     * @param annotations 注解简单名（不含 {@code @}），顺序即源码顺序
     * @return 命中的分层；没命中返回 {@code null}
     */
    public static LayerKind fromAnnotations(Iterable<String> annotations) {
        if (annotations == null) {
            return null;
        }
        for (String annotation : annotations) {
            LayerKind layer = annotation == null ? null : ANNOTATIONS.get(annotation.trim());
            if (layer != null) {
                return layer;
            }
        }
        return null;
    }

    /**
     * 按类型名后缀判断。
     *
     * <p>大小写敏感，不做「前一个字符必须大写」的额外约束——{@code Todo} 对后缀 {@code DO}
     * 本来就不匹配（{@code "Todo".endsWith("DO")} 为 false），加约束反而会把 {@code UserDO} 挡掉。
     *
     * @param typeName 类型简单名
     * @return 命中的分层；没命中返回 {@code null}
     */
    public static LayerKind fromTypeName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        String name = typeName.trim();
        for (Map.Entry<String, LayerKind> entry : NAME_SUFFIXES.entrySet()) {
            if (name.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
