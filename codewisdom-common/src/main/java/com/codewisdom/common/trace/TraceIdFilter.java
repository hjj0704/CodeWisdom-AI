package com.codewisdom.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TraceId 注入与回写过滤器。
 *
 * <p>行为：
 * <ol>
 *   <li>优先复用上游传入的 {@code X-Trace-Id} 请求头，保证跨服务链路一致；</li>
 *   <li>缺失时生成新的 TraceId；</li>
 *   <li>写入 MDC（供日志 pattern 使用）并回写到响应头（供前端排查）；</li>
 *   <li>请求结束后 <b>必须清理 MDC</b>，否则线程池复用会串号。</li>
 * </ol>
 *
 * <p>仅被扫描 {@code com.codewisdom.common} 的 WebMVC 服务装配；WebFlux 网关走自己的
 * {@code GlobalFilter}，不使用本类。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TraceIdHolder.TRACE_HEADER);
        TraceIdHolder.set(incoming);
        response.setHeader(TraceIdHolder.TRACE_HEADER, TraceIdHolder.get());
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIdHolder.clear();
        }
    }
}
