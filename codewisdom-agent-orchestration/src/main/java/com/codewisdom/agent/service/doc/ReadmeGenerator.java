package com.codewisdom.agent.service.doc;

import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmModels.CallResult;
import com.codewisdom.agent.client.llm.LlmModels.Request;
import com.codewisdom.agent.domain.doc.ReadmeModels.ReadmeRequest;
import com.codewisdom.agent.domain.doc.ReadmeModels.ReadmeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * 标准化 README 生成（T-603）。
 *
 * <p>验收要求五节齐全：项目介绍、技术栈、目录结构、启动方式、注意事项。
 */
public class ReadmeGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReadmeGenerator.class);

    static final List<String> REQUIRED_SECTIONS = List.of(
            "## 项目介绍",
            "## 技术栈",
            "## 目录结构",
            "## 启动方式",
            "## 注意事项");

    private static final String SYSTEM_PROMPT = """
            你是技术文档助手。根据输入生成 Markdown README。
            必须包含且仅使用以下五个二级标题（顺序一致）：
            ## 项目介绍
            ## 技术栈
            ## 目录结构
            ## 启动方式
            ## 注意事项
            只输出 Markdown 正文，不要代码围栏。
            """;

    private final LlmClient llmClient;
    private final DocCommentGenerator.DocGenProperties properties;

    public ReadmeGenerator(LlmClient llmClient, DocCommentGenerator.DocGenProperties properties) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public ReadmeResult generate(ReadmeRequest request) {
        Objects.requireNonNull(request, "request");

        if (!properties.enabled()) {
            return ReadmeResult.notGenerated();
        }

        CallResult callResult = llmClient.call(new Request(buildUserPrompt(request), SYSTEM_PROMPT));
        if (callResult.failed()) {
            log.warn("README 生成失败: {}", callResult.failureReason());
            return new ReadmeResult(false, "");
        }

        String markdown = callResult.response().content().trim();
        validateSections(markdown);
        return new ReadmeResult(false, markdown);
    }

    static void validateSections(String markdown) {
        Objects.requireNonNull(markdown, "markdown");
        for (String section : REQUIRED_SECTIONS) {
            if (!markdown.contains(section)) {
                throw new IllegalArgumentException("README 缺少章节: " + section);
            }
        }
    }

    private static String buildUserPrompt(ReadmeRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("项目名: ").append(request.projectName()).append('\n');
        if (!request.descriptionHint().isBlank()) {
            sb.append("简介线索: ").append(request.descriptionHint()).append('\n');
        }
        if (!request.techStack().isEmpty()) {
            sb.append("技术栈: ").append(String.join(", ", request.techStack())).append('\n');
        }
        if (!request.topLevelDirs().isEmpty()) {
            sb.append("顶层目录: ").append(String.join(", ", request.topLevelDirs())).append('\n');
        }
        if (!request.startCommandHint().isBlank()) {
            sb.append("启动线索: ").append(request.startCommandHint()).append('\n');
        }
        return sb.toString();
    }
}
