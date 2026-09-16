package com.codewisdom.common.api;

/**
 * 全局错误码。
 *
 * <p>分段约定：0 成功；4xxxx 客户端错误；5xxxx 服务端错误。
 * 领域错误码按服务段划分，新增时在对应段内递增，禁止复用已废弃编码。
 */
public enum ErrorCode {

    SUCCESS(0, "成功"),

    // ---- 通用客户端错误 4xxxx ----
    BAD_REQUEST(40000, "请求参数不合法"),
    UNAUTHORIZED(40100, "未认证"),
    FORBIDDEN(40300, "无访问权限"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
    PAYLOAD_TOO_LARGE(41300, "上传内容超出限制"),
    UNSUPPORTED_MEDIA_TYPE(41500, "不支持的内容类型"),

    // ---- 通用服务端错误 5xxxx ----
    BIZ_ERROR(50000, "业务处理失败"),
    SYSTEM_ERROR(50001, "系统内部错误"),
    REMOTE_CALL_ERROR(50002, "下游服务调用失败"),

    // ---- project-resource 51000 ----
    IMPORT_ERROR(51000, "项目导入失败"),
    IMPORT_URL_REJECTED(51001, "仓库地址不被允许"),
    ARCHIVE_INVALID(51002, "压缩包非法或已损坏"),
    AUTH_USERNAME_EXISTS(51010, "用户名已存在"),
    AUTH_BAD_CREDENTIALS(51011, "用户名或密码错误"),
    ARCHIVE_PATH_TRAVERSAL(51003, "压缩包包含非法路径"),

    // ---- code-analysis 52000 ----
    PARSE_ERROR(52000, "代码解析失败"),
    UNSUPPORTED_LANGUAGE(52001, "暂不支持的语言"),

    // ---- agent-orchestration 54000 ----
    AGENT_ERROR(54000, "智能体执行失败"),
    LLM_CALL_ERROR(54001, "模型调用失败"),
    HITL_REJECTED(54002, "人工审核驳回"),

    // ---- evaluation-export 55000 ----
    EXPORT_ERROR(55000, "项目导出失败"),
    RUN_JUDGE_ERROR(55001, "运行能力判定失败"),
    SANDBOX_REJECTED(55002, "沙箱拒绝执行"),

    // ---- 评测 56000 ----
    EVAL_ERROR(56000, "评测执行失败"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
