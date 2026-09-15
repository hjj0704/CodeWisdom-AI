package com.codewisdom.common.exception;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.api.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器。
 *
 * <p>约束：对外只暴露错误码与可读信息，<b>不返回堆栈</b>；堆栈仅进日志。
 * 每个响应都带 traceId，便于按链路定位。
 *
 * <p>注意：该处理器基于 Spring MVC。WebFlux 网关不扫描该包，需要自己的错误处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，按 warn 记录，不打堆栈。 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException ex) {
        log.warn("业务异常 code={} message={}", ex.getErrorCode().code(), ex.getMessage());
        return R.fail(ex.getErrorCode(), ex.getMessage());
    }

    /** 参数校验失败（@Valid 触发）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(ErrorCode.BAD_REQUEST.message());
        log.warn("参数校验失败: {}", detail);
        return R.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /** 缺少必填请求参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("缺少请求参数: {}", ex.getParameterName());
        return R.fail(ErrorCode.BAD_REQUEST, "缺少必填参数: " + ex.getParameterName());
    }

    /** 参数类型不匹配。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配: {}", ex.getName());
        return R.fail(ErrorCode.BAD_REQUEST, "参数类型不正确: " + ex.getName());
    }

    /** 请求体不可解析。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体不可解析: {}", ex.getMessage());
        return R.fail(ErrorCode.BAD_REQUEST, "请求体格式不正确");
    }

    /** 请求方法不支持。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("请求方法不支持: {}", ex.getMethod());
        return R.fail(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 路由不存在。 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNoHandler(NoHandlerFoundException ex) {
        log.warn("路由不存在: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return R.fail(ErrorCode.NOT_FOUND);
    }

    /** 兜底：不可预期错误。对外隐藏细节，对内保留完整堆栈。 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleUnexpected(Exception ex) {
        log.error("系统内部错误", ex);
        return R.fail(ErrorCode.SYSTEM_ERROR);
    }
}
