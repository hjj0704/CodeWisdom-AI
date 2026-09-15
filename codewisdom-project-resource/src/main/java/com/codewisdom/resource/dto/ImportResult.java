package com.codewisdom.resource.dto;

import com.codewisdom.resource.domain.enums.ImportTaskStatus;

/**
 * 导入结果。
 *
 * @param projectId  项目 id
 * @param taskId     本次导入任务 id
 * @param status     任务终态
 * @param branch     实际检出的分支
 * @param headCommit HEAD 提交 id
 * @param fileCount  纳入文件树的文件数
 * @param totalSize  纳入文件树的总字节数
 * @param message    可读说明；失败时为失败原因
 */
public record ImportResult(
        Long projectId,
        Long taskId,
        ImportTaskStatus status,
        String branch,
        String headCommit,
        Integer fileCount,
        Long totalSize,
        String message
) {
}
