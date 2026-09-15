package com.codewisdom.common.api;

import com.codewisdom.common.trace.TraceIdHolder;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体。所有 REST 接口一律返回该结构，禁止直接返回裸对象或裸 Map。
 *
 * @param <T> 业务数据类型
 */
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码，0 表示成功，见 {@link ErrorCode} */
    private int code;

    /** 提示信息，失败时为可读原因 */
    private String message;

    /** 业务数据，失败时为 null */
    private T data;

    /** 链路追踪 ID，便于前后端对齐排查 */
    private String traceId;

    /** 服务端时间戳（毫秒） */
    private long timestamp;

    public R() {
        this.timestamp = System.currentTimeMillis();
        this.traceId = TraceIdHolder.get();
    }

    public R(int code, String message, T data) {
        this();
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ---- 成功 ----

    public static <T> R<T> ok() {
        return new R<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), data);
    }

    // ---- 失败 ----

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.code(), errorCode.message(), null);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.code(), message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /** 是否为成功响应。 */
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.code();
    }

    // ---- getter / setter ----

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
