package com.codewisdom.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.agent.client.ProjectResourceClient;
import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmModels.CallResult;
import com.codewisdom.agent.client.llm.LlmModels.Request;
import com.codewisdom.agent.dto.ChatMessageView;
import com.codewisdom.agent.dto.ChatReplyView;
import com.codewisdom.agent.dto.ChatSessionView;
import com.codewisdom.agent.entity.ChatMessageEntity;
import com.codewisdom.agent.entity.ChatSessionEntity;
import com.codewisdom.agent.mapper.ChatMessageMapper;
import com.codewisdom.agent.mapper.ChatSessionMapper;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatService {

    private static final String ONBOARD_PROMPT = """
            你是 CodeWisdom AI 的项目分析助手。必须先阅读提供的文档摘录，再结合统计与源码判断项目定位。
            用通俗中文回答，总字数不超过 320 字，分 5 小段，每段 1~2 行：
            ① 定位：这是什么（游戏类型/行业工具/开源库/后台系统等，尽量具体）
            ② 用途：解决什么问题、给谁用
            ③ 技术：主要语言与框架
            ④ 结构：目录/模块怎么组织
            ⑤ 建议：优先改进的 2 点
            不要编造文档里没有的功能；不确定时用「可能」「推测」并说明依据。
            """;

    private static final String CHAT_PROMPT = """
            你是 CodeWisdom 工作台里的项目助手，可结合上下文中的文档、统计、源码与对话历史回答问题。
            规则：
            1. 用户问项目定位/技术/结构/风险：结合上下文简要回答，中文，条理清晰。
            2. 用户问如何修复、改哪行代码：必须给出可执行的改法；若提供替换代码，用 ```java 代码块``` 包裹修改后的完整行或片段，并 1~2 句说明。
            3. 用户问与项目无关的通用问题：先简要回答；若无法回答，说明你是项目助手并建议查看帮助文档。
            4. 不要编造仓库里不存在的文件或功能；不确定时明确说明。
            5. 不要每次都强行输出五段式项目介绍；仅在一键分析或用户明确要「介绍项目」时用五段式。
            """;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final LlmClient llmClient;
    private final ProjectResourceClient projectResourceClient;

    public ChatService(ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper,
                       LlmClient llmClient,
                       ProjectResourceClient projectResourceClient) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.llmClient = llmClient;
        this.projectResourceClient = projectResourceClient;
    }

    public List<ChatSessionView> listSessions(long projectId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getProjectId, projectId)
                        .orderByDesc(ChatSessionEntity::getUpdatedAt))
                .stream()
                .map(this::toSessionView)
                .toList();
    }

    @Transactional
    public ChatSessionView createSession(long projectId, String title) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setProjectId(projectId);
        session.setUserId(0L);
        session.setTitle(title == null || title.isBlank() ? "新会话" : title.trim());
        sessionMapper.insert(session);
        return toSessionView(session);
    }

    @Transactional
    public void deleteSession(long sessionId) {
        requireSession(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    public List<ChatMessageView> listMessages(long sessionId) {
        requireSession(sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByAsc(ChatMessageEntity::getId))
                .stream()
                .map(this::toMessageView)
                .toList();
    }

    @Transactional
    public ChatReplyView chat(long sessionId, String content, String authorizationHeader) {
        ChatSessionEntity session = requireSession(sessionId);
        String context = buildContext(session.getProjectId(), sessionId, authorizationHeader, 8);
        String prompt = context + "\n\n用户问题：\n" + content;
        String answer = callLlm(prompt, CHAT_PROMPT);

        ChatMessageEntity userMsg = insertMessage(sessionId, "user", content);
        updateSessionTitle(session, content);
        ChatMessageEntity assistant = insertMessage(sessionId, "assistant", answer);
        sessionMapper.updateById(session);
        return new ChatReplyView(toMessageView(userMsg), toMessageView(assistant));
    }

    @Transactional
    public ChatReplyView onboard(long projectId, String authorizationHeader) {
        String userText = "请先阅读项目文档和源码，用简短通俗的话介绍：这是什么项目/工具/游戏？给谁用？用什么技术？";
        ChatSessionEntity session = new ChatSessionEntity();
        session.setProjectId(projectId);
        session.setUserId(0L);
        session.setTitle(truncateTitle(userText));
        sessionMapper.insert(session);

        ChatMessageEntity userMsg = insertMessage(session.getId(), "user", userText);

        String context = buildContext(projectId, session.getId(), authorizationHeader, 0);
        String prompt = context + "\n\n" + userText;
        String answer = callLlm(prompt, ONBOARD_PROMPT);

        ChatMessageEntity assistant = insertMessage(session.getId(), "assistant", answer);
        sessionMapper.updateById(session);
        return new ChatReplyView(toMessageView(userMsg), toMessageView(assistant));
    }

    private String buildContext(long projectId, long sessionId, String authorizationHeader, int maxHistory) {
        StringBuilder sb = new StringBuilder();
        projectResourceClient.fetchProjectName(projectId, authorizationHeader)
                .ifPresent(name -> sb.append("项目名称：").append(name).append('\n'));
        sb.append("项目 ID=").append(projectId).append('\n');

        try {
            ProjectResourceClient.ProjectBrief brief =
                    projectResourceClient.fetchBrief(projectId, authorizationHeader);
            sb.append("项目统计：\n").append(brief.toPromptBlock());
        } catch (Exception ex) {
            sb.append("（未能拉取项目统计）\n");
        }

        String docs = projectResourceClient.fetchDocumentSnippets(projectId, authorizationHeader);
        if (!docs.isBlank()) {
            sb.append("\n=== 文档摘录（优先阅读） ===\n").append(docs).append('\n');
        } else {
            sb.append("\n（未找到 README/pom 等文档，请主要依据统计与源码抽样推断）\n");
        }

        String sources = projectResourceClient.fetchSourceSnippets(projectId, authorizationHeader);
        if (!sources.isBlank()) {
            sb.append("\n=== 源码抽样 ===\n").append(sources).append('\n');
        }

        if (maxHistory > 0) {
            List<ChatMessageEntity> history = messageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                    .eq(ChatMessageEntity::getSessionId, sessionId)
                    .orderByDesc(ChatMessageEntity::getId)
                    .last("LIMIT " + maxHistory));
            if (!history.isEmpty()) {
                sb.append("\n近期对话：\n");
                for (int i = history.size() - 1; i >= 0; i--) {
                    ChatMessageEntity msg = history.get(i);
                    sb.append(msg.getRole()).append(": ")
                            .append(truncate(msg.getContent(), 300)).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private void updateSessionTitle(ChatSessionEntity session, String userContent) {
        if (shouldAutoTitle(session.getTitle())) {
            session.setTitle(truncateTitle(userContent));
        }
    }

    private static boolean shouldAutoTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        return "新会话".equals(title) || "项目初诊".equals(title) || title.startsWith("会话");
    }

    private static String truncateTitle(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.isEmpty()) {
            return "新会话";
        }
        int max = 18;
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    private String callLlm(String prompt, String systemPrompt) {
        CallResult result = llmClient.call(new Request(prompt, systemPrompt));
        if (result.failed()) {
            throw BizException.of(ErrorCode.LLM_CALL_ERROR,
                    result.failureReason() == null ? "模型不可用" : result.failureReason());
        }
        return result.response().content();
    }

    private ChatMessageEntity insertMessage(long sessionId, String role, String content) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        messageMapper.insert(msg);
        return msg;
    }

    private ChatSessionEntity requireSession(long sessionId) {
        ChatSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    private ChatSessionView toSessionView(ChatSessionEntity entity) {
        return new ChatSessionView(
                entity.getId(), entity.getProjectId(), entity.getTitle(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ChatMessageView toMessageView(ChatMessageEntity entity) {
        return new ChatMessageView(
                entity.getId(), entity.getSessionId(), entity.getRole(),
                entity.getContent(), entity.getCreatedAt());
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
