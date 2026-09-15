package com.codewisdom.resource.domain.enums;

/**
 * 项目整体生命周期状态。
 *
 * <p>持久化时按枚举名（{@code name()}）写入 VARCHAR 列，不依赖序号，避免调整顺序导致数据错乱。
 */
public enum ProjectStatus {
    /** 已创建，尚未开始导入。 */
    CREATED,

    /** 正在拉取/解压/建树。 */
    IMPORTING,

    /** 导入完成，文件树可用。 */
    READY,

    /** 导入失败，详见最近一条导入任务的错误码。 */
    FAILED
}
