package com.codewisdom.resource.mq;

/**
 * {@code cw.parse} 队列消息体（T-206）。
 *
 * <ul>
 *   <li>{@code GIT_IMPORT}：异步 Git 导入，任务 PENDING → RUNNING → SUCCESS</li>
 *   <li>{@code PARSE_NOTIFY}：导入完成后的解析通知（供 code-analysis 消费）</li>
 * </ul>
 */
public record ParseTaskMessage(
        String action,
        Long projectId,
        Long taskId,
        String url,
        String branch,
        Boolean exportEnabled
) {

    public static final String ACTION_GIT_IMPORT = "GIT_IMPORT";
    public static final String ACTION_PARSE_NOTIFY = "PARSE_NOTIFY";

    public static ParseTaskMessage gitImport(Long projectId,
                                             Long taskId,
                                             String url,
                                             String branch,
                                             Boolean exportEnabled) {
        return new ParseTaskMessage(ACTION_GIT_IMPORT, projectId, taskId, url, branch, exportEnabled);
    }

    public static ParseTaskMessage parseNotify(Long projectId, Long taskId) {
        return new ParseTaskMessage(ACTION_PARSE_NOTIFY, projectId, taskId, null, null, null);
    }
}
