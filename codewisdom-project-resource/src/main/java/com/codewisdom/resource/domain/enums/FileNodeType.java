package com.codewisdom.resource.domain.enums;

/**
 * 文件树节点类型。
 *
 * <p>持久化时按枚举名（{@code name()}）写入 VARCHAR 列，不依赖序号，避免调整顺序导致数据错乱。
 */
public enum FileNodeType {
    /** 目录。 */
    DIR,

    /** 普通文件。 */
    FILE
}
