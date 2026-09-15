package com.codewisdom.common.exception;

import com.codewisdom.common.api.ErrorCode;

/**
 * 业务异常。可预期的、需要向前端返回明确错误码的失败一律抛该异常。
 *
 * <p>不可预期错误请直接抛出原始异常，由全局异常处理器兜底为 {@link ErrorCode#SYSTEM_ERROR}。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    // ---- 便捷工厂 ----

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    public static BizException of(ErrorCode errorCode, String message, Throwable cause) {
        return new BizException(errorCode, message, cause);
    }
}
