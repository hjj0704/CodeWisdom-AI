package com.codewisdom.agent.service.doc;

import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmClient.FallbackLlmProvider;
import com.codewisdom.agent.client.llm.LlmClient.LlmClientProperties;
import com.codewisdom.agent.client.llm.LlmClient.MockLlmProvider;
import com.codewisdom.agent.domain.doc.ReadmeModels.ReadmeRequest;
import com.codewisdom.agent.domain.doc.ReadmeModels.ReadmeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T-603 验收：README 五节结构完整。 */
@DisplayName("T-603 README 生成")
class ReadmeGenTest {

    private static final String MOCK_README = """
            # CodeWisdom Demo

            ## 项目介绍
            示例 Spring Boot 工程，用于演示导入与分析。

            ## 技术栈
            - Java 17
            - Spring Boot 3.5

            ## 目录结构
            - src/main/java — 业务代码
            - src/test/java — 测试

            ## 启动方式
            ```bash
            mvn spring-boot:run
            ```

            ## 注意事项
            辅助生成内容需人工确认。
            """;

    private ReadmeGenerator generator(MockLlmProvider mock, DocCommentGenerator.DocGenProperties props) {
        LlmClient client = new LlmClient(
                mock,
                new FallbackLlmProvider("fallback"),
                LlmClientProperties.defaults());
        return new ReadmeGenerator(client, props);
    }

    @Test
    @DisplayName("开关关闭时不调用 LLM")
    void toggleOff() {
        MockLlmProvider mock = new MockLlmProvider("mock", MOCK_README);
        ReadmeResult result = generator(mock, DocCommentGenerator.DocGenProperties.off())
                .generate(sampleRequest());

        assertThat(mock.callCount()).isZero();
        assertThat(result.skipped()).isTrue();
    }

    @Test
    @DisplayName("生成 README 含五节")
    void fiveSections() {
        MockLlmProvider mock = new MockLlmProvider("mock", MOCK_README);
        ReadmeResult result = generator(mock, DocCommentGenerator.DocGenProperties.on())
                .generate(sampleRequest());

        assertThat(mock.callCount()).isEqualTo(1);
        assertThat(result.skipped()).isFalse();
        for (String section : ReadmeGenerator.REQUIRED_SECTIONS) {
            assertThat(result.markdown()).contains(section);
        }
    }

    @Test
    @DisplayName("缺章节时校验失败")
    void missingSectionRejected() {
        assertThatThrownBy(() -> ReadmeGenerator.validateSections("## 项目介绍\n只有一节"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("技术栈");
    }

    private static ReadmeRequest sampleRequest() {
        return new ReadmeRequest(
                "codewisdom-demo",
                "代码智能治理样例",
                List.of("Java 17", "Spring Boot"),
                List.of("src", "pom.xml"),
                "mvn spring-boot:run");
    }
}
