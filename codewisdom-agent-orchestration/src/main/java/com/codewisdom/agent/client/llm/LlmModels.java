package com.codewisdom.agent.client.llm;

import java.util.Objects;

/**
 * LLM 调用相关的值对象。
 *
 * <p>T-601 只定义抽象层数据形状，不绑定任何厂商 SDK。
 */
public final class LlmModels {

    private LlmModels() {
    }

    /** 调用结果状态：成功 / 降级 / 彻底失败。 */
    public enum CallStatus {
        /** 主 Provider 在超时与重试窗口内正常返回。 */
        SUCCESS,
        /** 主 Provider 失败或超时，已走降级 Provider。 */
        DEGRADED,
        /** 主 Provider 与降级 Provider 均不可用。 */
        FAILED
    }

    /** 发往模型的最小请求体。 */
    public record Request(String prompt, String systemPrompt) {

        public Request {
            Objects.requireNonNull(prompt, "prompt");
            if (prompt.isBlank()) {
                throw new IllegalArgumentException("prompt 不能为空");
            }
            systemPrompt = systemPrompt == null ? "" : systemPrompt;
        }

        public static Request of(String prompt) {
            return new Request(prompt, "");
        }
    }

    /** 模型返回的文本与来源 Provider。 */
    public record Response(String content, String providerId) {

        public Response {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(providerId, "providerId");
        }
    }

    /**
     * 一次 {@link LlmClient#call} 的完整结果。
     *
     * @param failureReason 主 Provider 最后一次失败原因；成功时为 {@code null}
     * @param attempts      对主 Provider 的实际尝试次数（含首次）
     */
    public record CallResult(
            CallStatus status,
            Response response,
            String failureReason,
            int attempts
    ) {

        public CallResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(response, "response");
            if (attempts < 0) {
                throw new IllegalArgumentException("attempts 不能为负");
            }
        }

        public boolean success() {
            return status == CallStatus.SUCCESS;
        }

        public boolean degraded() {
            return status == CallStatus.DEGRADED;
        }

        public boolean failed() {
            return status == CallStatus.FAILED;
        }
    }
}
