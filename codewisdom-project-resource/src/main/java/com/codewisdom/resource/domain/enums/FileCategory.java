package com.codewisdom.resource.domain.enums;

/**
 * 文件分类，供解析与审计阶段筛选使用。
 *
 * <p>持久化时按枚举名（{@code name()}）写入 VARCHAR 列，不依赖序号，避免调整顺序导致数据错乱。
 */
public enum FileCategory {
    /** 源码文件。 */
    SOURCE,

    /** 配置文件（pom.xml / application.yml / requirements.txt 等）。 */
    CONFIG,

    /** 文档（README / *.md 等）。 */
    DOC,

    /** 其他（资源、二进制、未知类型）。 */
    OTHER
}
