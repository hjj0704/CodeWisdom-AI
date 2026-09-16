package com.codewisdom.agent.client.llm;

import com.codewisdom.agent.client.llm.LlmModels.CallResult;
import com.codewisdom.agent.client.llm.LlmModels.CallStatus;
import com.codewisdom.agent.client.llm.LlmModels.Request;
import com.codewisdom.agent.client.llm.LlmModels.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LLM 调用门面：超时、重试、降级。
 *
 * <p>IO 等待型调用与其他服务策略不同，因此独立封装。主 Provider 失败时不抛异常阻塞上游，
 * 而是返回 {@link CallStatus#DEGRADED} 的占位文本，保证 LangGraph 主流程可继续。
 */
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final LlmProvider primary;
    private final LlmProvider fallback;
    private final LlmClientProperties properties;

    public LlmClient(LlmProvider primary, LlmProvider fallback, LlmClientProperties properties) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 发起一次 LLM 调用。
     *
     * <p>{@code enabled=false} 时不触达任何 Provider（供 T-602 开关断言复用）。
     */
    public CallResult call(Request request) {
        Objects.requireNonNull(request, "request");

        if (!properties.enabled()) {
            return new CallResult(
                    CallStatus.DEGRADED,
                    new Response(properties.disabledMessage(), "disabled"),
                    "LLM 功能已关闭",
                    0);
        }

        int maxAttempts = properties.maxRetries() + 1;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Response response = invokeWithTimeout(primary, request);
                return new CallResult(CallStatus.SUCCESS, response, null, attempt);
            } catch (Exception ex) {
                lastError = ex;
                log.warn("LLM 主 Provider [{}] 第 {}/{} 次调用失败: {}",
                        primary.id(), attempt, maxAttempts, rootMessage(ex));
                if (attempt < maxAttempts) {
                    sleepQuietly(properties.retryBackoffMs());
                }
            }
        }

        return degrade(request, lastError, maxAttempts);
    }

    private CallResult degrade(Request request, Exception lastError, int attempts) {
        String reason = lastError == null ? "unknown" : rootMessage(lastError);
        try {
            Response fallbackResponse = fallback.complete(request);
            log.info("LLM 已降级至 Provider [{}]，原因: {}", fallback.id(), reason);
            return new CallResult(CallStatus.DEGRADED, fallbackResponse, reason, attempts);
        } catch (Exception fallbackError) {
            log.error("LLM 降级 Provider [{}] 也失败: {}", fallback.id(), rootMessage(fallbackError));
            Response stub = new Response(properties.fallbackMessage(), fallback.id());
            return new CallResult(
                    CallStatus.FAILED,
                    stub,
                    reason + "; fallback: " + rootMessage(fallbackError),
                    attempts);
        }
    }

    private Response invokeWithTimeout(LlmProvider provider, Request request)
            throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Response> future = CompletableFuture.supplyAsync(
                () -> provider.complete(request));
        try {
            return future.orTimeout(properties.timeoutMs(), TimeUnit.MILLISECONDS).get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message;
    }

    /** 运行时配置，与 {@code codewisdom.llm.*} 对齐。 */
    public record LlmClientProperties(
            boolean enabled,
            long timeoutMs,
            int maxRetries,
            long retryBackoffMs,
            String fallbackMessage,
            String disabledMessage
    ) {

        public LlmClientProperties {
            if (timeoutMs < 1) {
                throw new IllegalArgumentException("timeoutMs 至少为 1");
            }
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries 不能为负");
            }
            if (retryBackoffMs < 0) {
                throw new IllegalArgumentException("retryBackoffMs 不能为负");
            }
            Objects.requireNonNull(fallbackMessage, "fallbackMessage");
            Objects.requireNonNull(disabledMessage, "disabledMessage");
        }

        public static LlmClientProperties defaults() {
            return new LlmClientProperties(
                    true,
                    30_000L,
                    2,
                    50L,
                    "【辅助分析不可用】模型调用失败，请稍后重试或人工补充。",
                    "【LLM 已关闭】未生成 AI 内容，请人工补充。");
        }
    }

    /** 测试与本地开发用的 Mock Provider，可统计调用次数。 */
    public static final class MockLlmProvider implements LlmProvider {

        private final String id;
        private final String fixedContent;
        private int callCount;

        public MockLlmProvider() {
            this("mock", null);
        }

        public MockLlmProvider(String id, String fixedContent) {
            this.id = Objects.requireNonNull(id, "id");
            this.fixedContent = fixedContent;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public synchronized Response complete(Request request) {
            callCount++;
            String content = fixedContent != null ? fixedContent : "mock:" + request.prompt();
            return new Response(content, id);
        }

        public synchronized int callCount() {
            return callCount;
        }
    }

    /** 降级 Provider：返回固定占位文本，不访问外部网络。 */
    public static final class FallbackLlmProvider implements LlmProvider {

        private final String message;

        public FallbackLlmProvider(String message) {
            this.message = Objects.requireNonNull(message, "message");
        }

        @Override
        public String id() {
            return "fallback";
        }

        @Override
        public Response complete(Request request) {
            return new Response(message, id());
        }
    }
}
