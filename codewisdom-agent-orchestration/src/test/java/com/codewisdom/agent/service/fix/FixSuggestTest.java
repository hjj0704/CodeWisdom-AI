package com.codewisdom.agent.service.fix;

import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmClient.FallbackLlmProvider;
import com.codewisdom.agent.client.llm.LlmClient.LlmClientProperties;
import com.codewisdom.agent.client.llm.LlmClient.MockLlmProvider;
import com.codewisdom.agent.domain.fix.FixModels.FixSuggestResult;
import com.codewisdom.agent.domain.fix.FixModels.FixTargetIssue;
import com.codewisdom.agent.domain.fix.FixModels.RiskLevel;
import com.codewisdom.agent.service.doc.DocCommentGenerator.DocGenProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T-701 修复建议生成")
class FixSuggestTest {

    private static final String MOCK_JSON = """
            [
              {
                "issueKey": "CW-NULL-001|Demo.java|10",
                "suggestion": "改为 \\"常量\\".equals(input)",
                "rationale": "常量在左侧可避免 input 为 null 时的 NPE"
              }
            ]
            """;

    @Test
    @DisplayName("每条高危问题均有建议与修改理由")
    void highRiskGetsSuggestionWithRationale() {
        MockLlmProvider mock = new MockLlmProvider("mock", MOCK_JSON);
        FixSuggestGenerator generator = new FixSuggestGenerator(
                new LlmClient(mock, new FallbackLlmProvider("fb"), LlmClientProperties.defaults()),
                DocGenProperties.on());

        FixTargetIssue high = new FixTargetIssue(
                "CW-NULL-001", "Demo.java", 10, "可能 NPE", "equals 左侧应为字面量", RiskLevel.HIGH);
        FixTargetIssue low = new FixTargetIssue(
                "CW-DEAD-001", "Demo.java", 20, "死代码", "无调用", RiskLevel.LOW);

        FixSuggestResult result = generator.suggest(List.of(high, low));

        assertThat(mock.callCount()).isEqualTo(1);
        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).rationale()).contains("NPE");
        assertThat(result.suggestions().get(0).suggestion()).contains("equals");
    }
}
