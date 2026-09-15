package com.codewisdom.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * TraceId 上下文持有者，基于 SLF4J MDC 实现。
 *
 * <p>写入方：入口过滤器 / MQ 消费者 / 定时任务。
 * 读取方：{@code R.traceId}、日志 pattern、跨服务 Feign 拦截器。
 */
public final class TraceIdHolder {

    /** MDC key，同时用于日志 pattern 与跨服务请求头。 */
    public static final String TRACE_ID = "traceId";

    /** 跨服务透传的 HTTP 请求头名。 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    private TraceIdHolder() {
    }

    /**
     * 获取当前 TraceId；不存在时生成并写入 MDC。
     *
     * @return 非空的 TraceId
     */
    public static String getOrCreate() {
        String existing = MDC.get(TRACE_ID);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String generated = generate();
        MDC.put(TRACE_ID, generated);
        return generated;
    }

    /**
     * 获取当前 TraceId，不生成。
     *
     * @return 当前 TraceId，可能为 null
     */
    public static String get() {
        return MDC.get(TRACE_ID);
    }

    /**
     * 绑定指定 TraceId（用于上游透传场景）。
     *
     * @param traceId 上游传入的 TraceId；为空时自动生成
     */
    public static void set(String traceId) {
        MDC.put(TRACE_ID, (traceId == null || traceId.isBlank()) ? generate() : traceId);
    }

    /** 清理，必须在请求结束时调用，避免线程池复用导致的串号。 */
    public static void clear() {
        MDC.remove(TRACE_ID);
    }

    private static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
