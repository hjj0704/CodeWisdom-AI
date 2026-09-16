package com.codewisdom.agent.service.doc;

import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmClient.FallbackLlmProvider;
import com.codewisdom.agent.client.llm.LlmClient.LlmClientProperties;
import com.codewisdom.agent.client.llm.LlmClient.MockLlmProvider;
import com.codewisdom.agent.domain.doc.DocGenModels.DocGenResult;
import com.codewisdom.agent.domain.doc.DocGenModels.MethodDocRequest;
import com.codewisdom.agent.domain.doc.DocGenModels.ParameterDocRequest;
import com.codewisdom.agent.domain.doc.DocGenModels.TypeDocRequest;
import com.codewisdom.agent.service.doc.DocCommentGenerator.DocGenProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-602 验收：类/方法/参数/返回值注释生成，开关可控。
 */
@DisplayName("T-602 文档注释生成")
class DocGenToggleTest {

    private static final String MOCK_JSON = """
            {
              "typeComment": "/** 订单领域服务。 */",
              "methods": [
                {
                  "name": "findById",
                  "comment": "/**\\n * 按主键查询订单。\\n * @param id 订单主键\\n * @return 订单实体，不存在时为 null\\n */"
                },
                {
                  "name": "OrderService",
                  "comment": "/**\\n * 构造订单服务。\\n * @param repo 仓储\\n */"
                },
                {
                  "name": "close",
                  "comment": "/**\\n * 关闭资源。\\n */"
                }
              ]
            }
            """;

    private static TypeDocRequest sampleRequest() {
        return new TypeDocRequest(
                "demo.orders.OrderService",
                "class",
                "处理订单查询",
                List.of(
                        new MethodDocRequest(
                                "findById",
                                "Order",
                                false,
                                List.of(new ParameterDocRequest("id", "Long"))),
                        new MethodDocRequest(
                                "OrderService",
                                null,
                                true,
                                List.of(new ParameterDocRequest("repo", "OrderRepository"))),
                        new MethodDocRequest(
                                "close",
                                "void",
                                false,
                                List.of())));
    }

    private DocCommentGenerator generator(MockLlmProvider mock, DocGenProperties docProps) {
        LlmClientProperties llmProps = LlmClientProperties.defaults();
        LlmClient client = new LlmClient(mock, new FallbackLlmProvider("fallback"), llmProps);
        return new DocCommentGenerator(client, docProps);
    }

    @Nested
    @DisplayName("开关控制")
    class Toggle {

        @Test
        @DisplayName("关闭开关时不产生任何 LLM 调用")
        void disabledProducesNoLlmCalls() {
            MockLlmProvider mock = new MockLlmProvider("mock", MOCK_JSON);
            DocCommentGenerator generator = generator(mock, DocGenProperties.off());

            DocGenResult result = generator.generate(sampleRequest());

            assertThat(mock.callCount()).isZero();
            assertThat(result.skipped()).isTrue();
            assertThat(result.typeComment()).isEmpty();
            assertThat(result.methodComments()).isEmpty();
        }

        @Test
        @DisplayName("开启开关时调用 LLM 并产出注释")
        void enabledInvokesLlm() {
            MockLlmProvider mock = new MockLlmProvider("mock", MOCK_JSON);
            DocCommentGenerator generator = generator(mock, DocGenProperties.on());

            DocGenResult result = generator.generate(sampleRequest());

            assertThat(mock.callCount()).isEqualTo(1);
            assertThat(result.skipped()).isFalse();
            assertThat(result.typeComment()).contains("订单领域服务");
            assertThat(result.methodComments()).containsKey("findById");
        }
    }

    @Nested
    @DisplayName("Javadoc 合法性")
    class JavadocValidation {

        @Test
        @DisplayName("类/方法/参数/返回值注释齐全")
        void fullJavadocCoverage() {
            MockLlmProvider mock = new MockLlmProvider("mock", MOCK_JSON);
            DocGenResult result = generator(mock, DocGenProperties.on()).generate(sampleRequest());

            assertThat(result.typeComment()).startsWith("/**").endsWith("*/");

            String findById = result.methodComments().get("findById");
            assertThat(findById).contains("@param id");
            assertThat(findById).contains("@return");

            String ctor = result.methodComments().get("OrderService");
            assertThat(ctor).contains("@param repo");
            assertThat(ctor).doesNotContain("@return");

            String close = result.methodComments().get("close");
            assertThat(close).doesNotContain("@return");
            assertThat(close).doesNotContain("@param");
        }

        @Test
        @DisplayName("缺少 @param 时校验失败")
        void missingParamFailsValidation() {
            MethodDocRequest spec = new MethodDocRequest(
                    "save",
                    "void",
                    false,
                    List.of(new ParameterDocRequest("entity", "Order")));

            assertThatThrownBy(() -> DocCommentGenerator.validateMethodComment(
                    "/** 保存。 */", spec))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("@param entity");
        }

        @Test
        @DisplayName("非 void 方法缺少 @return 时校验失败")
        void missingReturnFailsValidation() {
            MethodDocRequest spec = new MethodDocRequest(
                    "count",
                    "long",
                    false,
                    List.of());

            assertThatThrownBy(() -> DocCommentGenerator.validateMethodComment(
                    "/** 计数。 */", spec))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("@return");
        }
    }
}
