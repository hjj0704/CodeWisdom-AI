package com.codewisdom.resource.domain.enums;

/**
 * 单次导入任务的状态。
 *
 * <p>持久化时按枚举名（{@code name()}）写入 VARCHAR 列，不依赖序号，避免调整顺序导致数据错乱。
 */
public enum ImportTaskStatus {
    /** 已入队，等待消费。 */
    PENDING,

    /** 处理中。 */
    RUNNING,

    /** 成功。 */
    SUCCESS,

    /** 失败，{@code error_code} 说明原因。 */
    FAILED
}
