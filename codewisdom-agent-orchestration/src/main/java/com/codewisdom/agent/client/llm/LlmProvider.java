package com.codewisdom.agent.client.llm;

import com.codewisdom.agent.client.llm.LlmModels.Request;
import com.codewisdom.agent.client.llm.LlmModels.Response;

/**
 * LLM 供应方抽象。
 *
 * <p>加新厂商只需实现本接口并注入 {@link LlmClient}，无需修改门面逻辑。
 * T-601 阶段仅提供 Mock / 降级实现；真实 SDK 接入留待 tech-spike 验证之后。
 */
public interface LlmProvider {

    /** 唯一标识，写入 {@link Response#providerId()} 便于溯源。 */
    String id();

    /**
     * 同步完成一次补全。
     *
     * @throws RuntimeException 调用失败时抛出，由 {@link LlmClient} 负责重试与降级
     */
    Response complete(Request request);
}
