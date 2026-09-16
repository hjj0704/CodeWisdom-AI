package com.codewisdom.agent.service.fix;

import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmModels.CallResult;
import com.codewisdom.agent.client.llm.LlmModels.Request;
import com.codewisdom.agent.domain.fix.FixModels.FixSuggestResult;
import com.codewisdom.agent.domain.fix.FixModels.FixSuggestion;
import com.codewisdom.agent.domain.fix.FixModels.FixTargetIssue;
import com.codewisdom.agent.domain.fix.FixModels.RiskLevel;
import com.codewisdom.agent.service.doc.DocCommentGenerator.DocGenProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 修复建议生成（T-701）：每条高危问题产出建议 + 修改理由。
 */
public class FixSuggestGenerator {

    private static final Logger log = LoggerFactory.getLogger(FixSuggestGenerator.class);

    private static final String SYSTEM_PROMPT = """
            你是 Java 代码修复助手。只输出 JSON 数组，不要 Markdown 围栏。
            每项格式：{"issueKey":"...","suggestion":"具体改法","rationale":"为什么这样改"}
            suggestion 与 rationale 均不能为空。
            """;

    private final LlmClient llmClient;
    private final DocGenProperties properties;
    private final ObjectMapper objectMapper;

    public FixSuggestGenerator(LlmClient llmClient, DocGenProperties properties) {
        this(llmClient, properties, new ObjectMapper());
    }

    FixSuggestGenerator(LlmClient llmClient, DocGenProperties properties, ObjectMapper objectMapper) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public FixSuggestResult suggest(List<FixTargetIssue> issues) {
        List<FixTargetIssue> safeIssues = issues == null ? List.of() : issues;
        if (!properties.enabled()) {
            return FixSuggestResult.notGenerated();
        }

        List<FixTargetIssue> highRisk = safeIssues.stream()
                .filter(i -> i.riskLevel() == RiskLevel.HIGH)
                .toList();
        if (highRisk.isEmpty()) {
            return new FixSuggestResult(false, List.of());
        }

        CallResult callResult = llmClient.call(new Request(buildPrompt(highRisk), SYSTEM_PROMPT));
        if (callResult.failed()) {
            log.warn("修复建议生成失败: {}", callResult.failureReason());
            return new FixSuggestResult(false, List.of());
        }

        try {
            LlmFixPayload[] payloads = objectMapper.readValue(callResult.response().content(), LlmFixPayload[].class);
            List<FixSuggestion> suggestions = new ArrayList<>();
            for (LlmFixPayload payload : payloads) {
                if (payload.suggestion() == null || payload.suggestion().isBlank()
                        || payload.rationale() == null || payload.rationale().isBlank()) {
                    throw new IllegalArgumentException("建议或理由为空");
                }
                FixTargetIssue matched = highRisk.stream()
                        .filter(i -> i.issueKey().equals(payload.issueKey()))
                        .findFirst()
                        .orElse(null);
                if (matched == null) {
                    continue;
                }
                suggestions.add(new FixSuggestion(
                        matched.issueKey(),
                        matched.ruleId(),
                        payload.suggestion(),
                        payload.rationale()));
            }
            return new FixSuggestResult(false, suggestions);
        } catch (Exception ex) {
            log.warn("无法解析修复建议 JSON: {}", ex.getMessage());
            return new FixSuggestResult(false, List.of());
        }
    }

    private static String buildPrompt(List<FixTargetIssue> issues) {
        StringBuilder sb = new StringBuilder("请为以下高危问题生成修复建议 JSON 数组。\n");
        for (FixTargetIssue issue : issues) {
            sb.append("- issueKey=").append(issue.issueKey())
                    .append(" rule=").append(issue.ruleId())
                    .append(" file=").append(issue.filePath()).append(':').append(issue.lineNo())
                    .append(" desc=").append(issue.description())
                    .append('\n');
        }
        return sb.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmFixPayload(String issueKey, String suggestion, String rationale) {
    }
}
