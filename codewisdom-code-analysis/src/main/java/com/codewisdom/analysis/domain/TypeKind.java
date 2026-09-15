package com.codewisdom.analysis.domain;

/**
 * 类型种类。
 *
 * <p>取值为 Tree-Sitter Java grammar 中五种类型声明节点的一一映射，
 * 不做合并——枚举和注解在审计阶段有各自的规则，混为一谈会丢信息。
 */
public enum TypeKind {

    /** {@code class_declaration} */
    CLASS,

    /** {@code interface_declaration} */
    INTERFACE,

    /** {@code enum_declaration} */
    ENUM,

    /** {@code annotation_type_declaration}，即 {@code @interface} */
    ANNOTATION,

    /** {@code record_declaration} */
    RECORD;

    /**
     * 由 Tree-Sitter 节点类型映射。
     *
     * @return 对应种类；非类型声明节点返回 {@code null}
     */
    public static TypeKind fromNodeType(String nodeType) {
        return switch (nodeType) {
            case "class_declaration" -> CLASS;
            case "interface_declaration" -> INTERFACE;
            case "enum_declaration" -> ENUM;
            case "annotation_type_declaration" -> ANNOTATION;
            case "record_declaration" -> RECORD;
            default -> null;
        };
    }

    /** 是否为「可被实例化」的具体类型，审计阶段用于判断是否有构造器要求。 */
    public boolean isInstantiable() {
        return this == CLASS || this == ENUM || this == RECORD;
    }
}
