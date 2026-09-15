package com.codewisdom.resource.domain.enums;

/**
 * 项目导入来源。
 *
 * <p>持久化时按枚举名（{@code name()}）写入 VARCHAR 列，不依赖序号，避免调整顺序导致数据错乱。
 */
public enum SourceType {
    /** Git 仓库导入（GitHub / Gitee 等公开仓库）。 */
    GIT,

    /** ZIP 压缩包上传导入。 */
    ZIP,

    /** 批量源码文件上传导入。 */
    FILES
}
