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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 修复建议生成（T-701）：每条高危/中危问题产出建议 + 修改理由。
 */
public class FixSuggestGenerator {

    private static final Logger log = LoggerFactory.getLogger(FixSuggestGenerator.class);

    private static final String SYSTEM_PROMPT = """
            你是 Java 代码修复助手。只输出 JSON 数组，不要 Markdown 代码块，不要其他文字。
            每项格式：{"issueKey":"规则ID|文件路径|行号","suggestion":"具体改法","rationale":"为什么这样改"}
            issueKey 必须与输入完全一致。suggestion 与 rationale 均不能为空，用简洁中文。
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
            log.info("LLM 已关闭，使用规则模板生成修复建议");
            List<FixTargetIssue> targets = selectTargets(safeIssues);
            if (targets.isEmpty()) {
                return new FixSuggestResult(false, List.of());
            }
            List<FixSuggestion> fallback = targets.stream().map(this::templateSuggestion).toList();
            return new FixSuggestResult(false, fallback);
        }
        if (safeIssues.isEmpty()) {
            return new FixSuggestResult(false, List.of());
        }

        List<FixTargetIssue> targets = selectTargets(safeIssues);
        List<FixSuggestion> fromLlm = tryLlmSuggestions(targets);
        if (!fromLlm.isEmpty()) {
            return new FixSuggestResult(false, fromLlm);
        }

        log.info("LLM 未产出有效修复建议，使用规则模板兜底 {} 条", targets.size());
        List<FixSuggestion> fallback = targets.stream().map(this::templateSuggestion).toList();
        return new FixSuggestResult(false, fallback);
    }

    private static List<FixTargetIssue> selectTargets(List<FixTargetIssue> issues) {
        List<FixTargetIssue> highAndMedium = issues.stream()
                .filter(i -> i.riskLevel() == RiskLevel.HIGH || i.riskLevel() == RiskLevel.MEDIUM)
                .toList();
        if (!highAndMedium.isEmpty()) {
            return highAndMedium;
        }
        return issues;
    }

    private List<FixSuggestion> tryLlmSuggestions(List<FixTargetIssue> targets) {
        CallResult callResult = llmClient.call(new Request(buildPrompt(targets), SYSTEM_PROMPT));
        if (callResult.failed() || callResult.degraded()) {
            log.warn("修复建议 LLM 不可用: {}", callResult.failureReason());
            return List.of();
        }

        try {
            String json = extractJsonArray(callResult.response().content());
            LlmFixPayload[] payloads = objectMapper.readValue(json, LlmFixPayload[].class);
            Map<String, FixSuggestion> byKey = new LinkedHashMap<>();
            for (LlmFixPayload payload : payloads) {
                if (payload.issueKey() == null || payload.issueKey().isBlank()
                        || payload.suggestion() == null || payload.suggestion().isBlank()
                        || payload.rationale() == null || payload.rationale().isBlank()) {
                    continue;
                }
                FixTargetIssue matched = targets.stream()
                        .filter(i -> i.issueKey().equals(payload.issueKey()))
                        .findFirst()
                        .orElse(null);
                if (matched == null) {
                    continue;
                }
                byKey.put(matched.issueKey(), new FixSuggestion(
                        matched.issueKey(),
                        matched.ruleId(),
                        payload.suggestion().trim(),
                        payload.rationale().trim()));
            }
            return new ArrayList<>(byKey.values());
        } catch (Exception ex) {
            log.warn("无法解析修复建议 JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    private FixSuggestion templateSuggestion(FixTargetIssue issue) {
        String suggestion = buildTemplateSuggestion(issue);
        String rationale = issue.riskNote() != null && !issue.riskNote().isBlank()
                ? issue.riskNote()
                : "基于审计规则 " + issue.ruleId() + " 的辅助修复方向，请人工确认后再改代码";
        return new FixSuggestion(issue.issueKey(), issue.ruleId(), suggestion, rationale);
    }

    private static String buildTemplateSuggestion(FixTargetIssue issue) {
        String rule = issue.ruleId().toUpperCase(Locale.ROOT);
        if (rule.contains("NULL") || issue.description().contains("NPE") || issue.description().contains("空指针")) {
            return "在 " + issue.filePath() + ":" + issue.lineNo()
                    + " 将 equals 改为常量在左，例如 \"常量\".equals(变量)，或增加 null 判断";
        }
        if (rule.contains("DEAD") || issue.description().contains("死代码")) {
            return "检查 " + issue.filePath() + ":" + issue.lineNo()
                    + " 是否确实无调用；确认后删除或补充调用入口";
        }
        if (rule.contains("EXCEPTION") || issue.description().contains("catch")) {
            return "在 " + issue.filePath() + ":" + issue.lineNo()
                    + " 的 catch 块中至少记录日志或重新抛出，避免空 catch 吞掉异常";
        }
        if (rule.contains("CONFLICT") || issue.description().contains("冲突")) {
            return "统一 " + issue.filePath() + " 中的依赖/版本声明，删除重复或矛盾的条目";
        }
        return "打开 " + issue.filePath() + ":" + issue.lineNo()
                + "，根据问题「" + issue.description() + "」修改对应代码";
    }

    static String extractJsonArray(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content 为空");
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int fenceEnd = trimmed.lastIndexOf("```");
            if (firstLine >= 0 && fenceEnd > firstLine) {
                trimmed = trimmed.substring(firstLine + 1, fenceEnd).trim();
            }
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static String buildPrompt(List<FixTargetIssue> issues) {
        StringBuilder sb = new StringBuilder("请为以下问题生成修复建议 JSON 数组。\n");
        for (FixTargetIssue issue : issues) {
            sb.append("- issueKey=").append(issue.issueKey())
                    .append(" rule=").append(issue.ruleId())
                    .append(" risk=").append(issue.riskLevel())
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
