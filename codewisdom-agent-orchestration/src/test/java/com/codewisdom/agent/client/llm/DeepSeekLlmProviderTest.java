package com.codewisdom.agent.client.llm;

import com.codewisdom.agent.client.llm.LlmModels.Request;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepSeek 真实连通冒烟（需外网 + {@code CW_DEEPSEEK_API_KEY}）。
 */
@Tag("network")
@DisplayName("DeepSeek 真实调用冒烟")
class DeepSeekLlmProviderTest {

    @Test
    @DisplayName("API Key 配置时可拿到非空回复")
    void liveCall() {
        String apiKey = System.getenv("CW_DEEPSEEK_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "未配置 CW_DEEPSEEK_API_KEY，跳过");

        DeepSeekLlmProvider provider = new DeepSeekLlmProvider(
                apiKey, "https://api.deepseek.com", "deepseek-chat");
        LlmModels.Response response = provider.complete(Request.of("用一句话介绍你自己"));

        assertThat(response.content()).isNotBlank();
        assertThat(response.providerId()).isEqualTo("deepseek");
    }
}
