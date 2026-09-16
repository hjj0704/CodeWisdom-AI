package com.codewisdom.agent.client.llm;

import com.codewisdom.agent.client.llm.LlmClient.FallbackLlmProvider;
import com.codewisdom.agent.client.llm.LlmClient.LlmClientProperties;
import com.codewisdom.agent.client.llm.LlmClient.MockLlmProvider;
import com.codewisdom.agent.client.llm.LlmModels.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-601 验收：LLM 客户端抽象层（可切换 Provider，含超时/重试/降级）。
 */
@DisplayName("T-601 LLM 客户端抽象层")
class LlmClientTest {

    private static final String FALLBACK_TEXT = "降级占位文本";

    private LlmClientProperties fastProperties() {
        return new LlmClientProperties(
                true,
                200L,
                1,
                10L,
                FALLBACK_TEXT,
                "LLM 已关闭");
    }

    @Nested
    @DisplayName("Mock Provider 正常路径")
    class MockSuccess {

        @Test
        @DisplayName("主 Provider 成功返回 SUCCESS")
        void primarySuccess() {
            MockLlmProvider mock = new MockLlmProvider("mock-primary", "hello");
            FallbackLlmProvider fallback = new FallbackLlmProvider(FALLBACK_TEXT);
            LlmClient client = new LlmClient(mock, fallback, fastProperties());

            var result = client.call(Request.of("explain void main"));

            assertThat(result.success()).isTrue();
            assertThat(result.response().content()).isEqualTo("hello");
            assertThat(result.response().providerId()).isEqualTo("mock-primary");
            assertThat(result.attempts()).isEqualTo(1);
            assertThat(mock.callCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("可切换不同 Provider 标识")
        void switchableProviderId() {
            MockLlmProvider alt = new MockLlmProvider("openai-compat", "from-alt");
            LlmClient client = new LlmClient(
                    alt,
                    new FallbackLlmProvider(FALLBACK_TEXT),
                    fastProperties());

            var result = client.call(Request.of("ping"));

            assertThat(result.response().providerId()).isEqualTo("openai-compat");
            assertThat(result.response().content()).isEqualTo("from-alt");
        }
    }

    @Nested
    @DisplayName("超时与降级")
    class TimeoutDegrade {

        @Test
        @DisplayName("主 Provider 超时触发降级，不抛异常")
        void timeoutTriggersFallback() {
            LlmProvider slow = new SlowLlmProvider(500L);
            FallbackLlmProvider fallback = new FallbackLlmProvider(FALLBACK_TEXT);
            LlmClient client = new LlmClient(slow, fallback, fastProperties());

            var result = client.call(Request.of("slow"));

            assertThat(result.degraded()).isTrue();
            assertThat(result.response().content()).isEqualTo(FALLBACK_TEXT);
            assertThat(result.response().providerId()).isEqualTo("fallback");
            assertThat(result.failureReason()).isNotBlank();
        }

        @Test
        @DisplayName("主 Provider 抛错且重试耗尽后降级")
        void failureAfterRetriesDegrades() {
            AtomicInteger attempts = new AtomicInteger();
            LlmProvider failing = new LlmProvider() {
                @Override
                public String id() {
                    return "failing";
                }

                @Override
                public LlmModels.Response complete(Request request) {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("upstream 503");
                }
            };
            LlmClient client = new LlmClient(
                    failing,
                    new FallbackLlmProvider(FALLBACK_TEXT),
                    fastProperties());

            var result = client.call(Request.of("fail"));

            assertThat(result.degraded()).isTrue();
            assertThat(result.attempts()).isEqualTo(2);
            assertThat(attempts.get()).isEqualTo(2);
            assertThat(result.failureReason()).contains("503");
        }

        @Test
        @DisplayName("重试后成功则不降级")
        void retryThenSuccess() {
            AtomicInteger attempts = new AtomicInteger();
            LlmProvider flaky = new LlmProvider() {
                @Override
                public String id() {
                    return "flaky";
                }

                @Override
                public LlmModels.Response complete(Request request) {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("transient");
                    }
                    return new LlmModels.Response("ok-after-retry", id());
                }
            };
            LlmClient client = new LlmClient(
                    flaky,
                    new FallbackLlmProvider(FALLBACK_TEXT),
                    fastProperties());

            var result = client.call(Request.of("retry-me"));

            assertThat(result.success()).isTrue();
            assertThat(result.response().content()).isEqualTo("ok-after-retry");
            assertThat(result.attempts()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("开关（T-602 前置）")
    class Toggle {

        @Test
        @DisplayName("关闭时不触达 Provider，调用次数为 0")
        void disabledSkipsProviders() {
            MockLlmProvider mock = new MockLlmProvider();
            LlmClientProperties disabled = new LlmClientProperties(
                    false,
                    200L,
                    0,
                    0L,
                    FALLBACK_TEXT,
                    "功能关闭占位");
            LlmClient client = new LlmClient(
                    mock,
                    new FallbackLlmProvider(FALLBACK_TEXT),
                    disabled);

            var result = client.call(Request.of("should-not-run"));

            assertThat(mock.callCount()).isZero();
            assertThat(result.degraded()).isTrue();
            assertThat(result.response().providerId()).isEqualTo("disabled");
            assertThat(result.response().content()).isEqualTo("功能关闭占位");
            assertThat(result.attempts()).isZero();
        }
    }

    @Nested
    @DisplayName("彻底失败")
    class HardFailure {

        @Test
        @DisplayName("主 Provider 与降级均失败时返回 FAILED 占位")
        void bothProvidersFail() {
            LlmProvider failing = new LlmProvider() {
                @Override
                public String id() {
                    return "primary-down";
                }

                @Override
                public LlmModels.Response complete(Request request) {
                    throw new IllegalStateException("primary down");
                }
            };
            LlmProvider brokenFallback = new LlmProvider() {
                @Override
                public String id() {
                    return "fallback-down";
                }

                @Override
                public LlmModels.Response complete(Request request) {
                    throw new IllegalStateException("fallback down");
                }
            };
            LlmClient client = new LlmClient(failing, brokenFallback, fastProperties());

            var result = client.call(Request.of("no-luck"));

            assertThat(result.failed()).isTrue();
            assertThat(result.response().content()).isEqualTo(FALLBACK_TEXT);
            assertThat(result.failureReason()).contains("primary down");
            assertThat(result.failureReason()).contains("fallback down");
        }
    }

    /** 故意阻塞超过客户端超时，用于触发 orTimeout。 */
    private static final class SlowLlmProvider implements LlmProvider {

        private final long delayMs;

        private SlowLlmProvider(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public String id() {
            return "slow";
        }

        @Override
        public LlmModels.Response complete(Request request) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            return new LlmModels.Response("too-late", id());
        }
    }
}
