package com.codewisdom.analysis.domain;

/**
 * 技术栈目录。
 *
 * <p><b>这是一份有边界的目录，不是「什么都能识别」。</b>把「认哪些技术」写死成枚举，
 * 是为了让识别结果可枚举、可比对、可测试——代价是目录外的技术认不出来。
 * 因此 {@link TechStackReport#unrecognized()} 会把<b>匹配不上任何规则的依赖坐标原样列出</b>，
 * 「没认出来」是可见的，不是悄悄消失的。
 *
 * <p>要加技术就往这里加一条 + 在 {@code TechStackDetector} 的规则表里加一行，是纯数据改动。
 *
 * @param displayName 展示名，如 {@code Spring Boot}
 * @param category    分类，用于报告分组
 */
public enum TechStack {

    // ---- 后端框架 ----
    SPRING_FRAMEWORK("Spring Framework", Category.BACKEND),
    SPRING_BOOT("Spring Boot", Category.BACKEND),
    SPRING_MVC("Spring MVC", Category.BACKEND),
    SPRING_CLOUD("Spring Cloud", Category.BACKEND),
    SPRING_CLOUD_OPENFEIGN("Spring Cloud OpenFeign", Category.BACKEND),
    SPRING_AOP("Spring AOP", Category.BACKEND),
    LANGGRAPH4J("LangGraph4j", Category.BACKEND),
    FASTAPI("FastAPI", Category.BACKEND),
    FLASK("Flask", Category.BACKEND),
    DJANGO("Django", Category.BACKEND),

    // ---- 持久层 ----
    MYBATIS("MyBatis", Category.PERSISTENCE),
    MYBATIS_PLUS("MyBatis-Plus", Category.PERSISTENCE),
    HIBERNATE("Hibernate", Category.PERSISTENCE),
    SPRING_DATA_JPA("Spring Data JPA", Category.PERSISTENCE),
    MAPSTRUCT("MapStruct", Category.PERSISTENCE),
    SQLALCHEMY("SQLAlchemy", Category.PERSISTENCE),

    // ---- 数据库 ----
    MYSQL("MySQL", Category.DATABASE),
    POSTGRESQL("PostgreSQL", Category.DATABASE),
    MONGODB("MongoDB", Category.DATABASE),

    // ---- 中间件 ----
    REDIS("Redis", Category.MIDDLEWARE),
    RABBITMQ("RabbitMQ", Category.MIDDLEWARE),
    KAFKA("Kafka", Category.MIDDLEWARE),
    NACOS("Nacos", Category.MIDDLEWARE),
    SENTINEL("Sentinel", Category.MIDDLEWARE),
    MINIO("MinIO", Category.MIDDLEWARE),
    ELASTICSEARCH("Elasticsearch", Category.MIDDLEWARE),

    // ---- 安全 ----
    SPRING_SECURITY("Spring Security", Category.SECURITY),
    SHIRO("Apache Shiro", Category.SECURITY),

    // ---- 工程与工具 ----
    LOMBOK("Lombok", Category.TOOL),
    FLYWAY("Flyway", Category.TOOL),
    SPRINGDOC("SpringDoc OpenAPI", Category.TOOL),
    TREE_SITTER("Tree-Sitter", Category.TOOL),
    JGIT("JGit", Category.TOOL),
    PANDAS("pandas", Category.TOOL),
    NUMPY("numpy", Category.TOOL),
    PYDANTIC("pydantic", Category.TOOL),

    // ---- 测试 ----
    JUNIT5("JUnit 5", Category.TEST),
    MOCKITO("Mockito", Category.TEST),
    PYTEST("pytest", Category.TEST),

    // ---- 前端 ----
    VUE("Vue", Category.FRONTEND),
    VUE_ROUTER("Vue Router", Category.FRONTEND),
    PINIA("Pinia", Category.FRONTEND),
    VUEX("Vuex", Category.FRONTEND),
    REACT("React", Category.FRONTEND),
    ELEMENT_PLUS("Element Plus", Category.FRONTEND),
    ANT_DESIGN_VUE("Ant Design Vue", Category.FRONTEND),
    AXIOS("Axios", Category.FRONTEND),
    ECHARTS("ECharts", Category.FRONTEND),
    VITE("Vite", Category.FRONTEND),
    TYPESCRIPT("TypeScript", Category.FRONTEND);

    /** 技术栈分类，用于报告分组展示。 */
    public enum Category {
        /** 后端框架 */
        BACKEND,
        /** 持久层 / ORM */
        PERSISTENCE,
        /** 数据库 */
        DATABASE,
        /** 中间件 */
        MIDDLEWARE,
        /** 安全框架 */
        SECURITY,
        /** 前端框架与库 */
        FRONTEND,
        /** 工程与工具 */
        TOOL,
        /** 测试框架 */
        TEST
    }

    private final String displayName;
    private final Category category;

    TechStack(String displayName, Category category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String displayName() {
        return displayName;
    }

    public Category category() {
        return category;
    }
}
