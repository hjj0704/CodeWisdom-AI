package com.codewisdom.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewisdom.agent.client.ProjectResourceClient;
import com.codewisdom.agent.client.llm.LlmClient;
import com.codewisdom.agent.client.llm.LlmModels.CallResult;
import com.codewisdom.agent.client.llm.LlmModels.Request;
import com.codewisdom.agent.dto.AgentActionView;
import com.codewisdom.agent.dto.ChatMessageView;
import com.codewisdom.agent.dto.ChatReplyView;
import com.codewisdom.agent.dto.ChatSessionView;
import com.codewisdom.agent.dto.WorkbenchContextDto;
import com.codewisdom.agent.entity.ChatMessageEntity;
import com.codewisdom.agent.entity.ChatSessionEntity;
import com.codewisdom.agent.mapper.ChatMessageMapper;
import com.codewisdom.agent.mapper.ChatSessionMapper;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ChatService {

    private static final String ONBOARD_PROMPT = """
            你是资深代码分析师。用户已在工作台看到项目名称、文件数、体积等，不要复述这些基础信息。
            请阅读文档与源码抽样，用口语化中文给出「页面上看不出的」判断：
            项目在做什么、技术栈要点、最值得优先关注的 1~2 个风险或改进点。
            总共 3~5 句、不超过 180 字；不要编号分段、不要 Markdown 标题、不要套话开场。
            不确定用「可能」并说明依据；不要编造仓库里不存在的文件或功能。
            """;

    private static final String CHAT_PROMPT = """
            你是企业级代码工作台 Agent：理解意图 → 分析 → 提议可执行操作（由用户确认后才生效，不自动改仓库/不自动跳转）。
            - 结合文档、统计、源码、工作台上下文（含 Java 文件列表节选）与对话历史作答。
            - 默认 2~6 句说明；修代码用 ```java``` 给出片段。
            - 需求不明确（找不到符号、多个同名、不知范围）时：先简短说明，并在末尾输出 CLARIFY，给出 2~4 个可点选选项。
            - 用户要找方法/类/文件位置时：根据上下文判断 filePath 与 symbol，输出 NAVIGATE（用户确认后工作台跳转）。
            - 用户要加 Javadoc 且当前为 Java 文件时：输出 JAVADOC。
            - 在回复正文后单独一行输出（正文勿提及该行），最多 2 条 action：
            AGENT_ACTIONS_JSON:[...]
            类型与字段：
            1) {"type":"NAVIGATE","symbol":"方法或类名","filePath":"可选，来自文件列表","line":可选行号,"summary":"不超过40字"}
            2) {"type":"JAVADOC","scope":"file|viewport|selection","summary":"..."}
            3) {"type":"CLARIFY","clarifyQuestion":"反问句","options":[{"id":"a","label":"选项文案"},...]}
            白名单仅以上三类；禁止删除文件、执行命令、改依赖。不编造路径或符号；不确定用 CLARIFY 或说明依据。
            """;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final LlmClient llmClient;
    private final ProjectResourceClient projectResourceClient;
    private final AgentActionParser agentActionParser;

    public ChatService(ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper,
                       LlmClient llmClient,
                       ProjectResourceClient projectResourceClient,
                       AgentActionParser agentActionParser) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.llmClient = llmClient;
        this.projectResourceClient = projectResourceClient;
        this.agentActionParser = agentActionParser;
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
    public ChatReplyView chat(long sessionId, String content, WorkbenchContextDto workbench,
                              String authorizationHeader) {
        ChatSessionEntity session = requireSession(sessionId);
        String context = buildContext(session.getProjectId(), sessionId, authorizationHeader, 8);
        String workbenchBlock = formatWorkbenchContext(workbench);
        String prompt = context + workbenchBlock + "\n\n用户问题：\n" + content;
        String rawAnswer = callLlm(prompt, CHAT_PROMPT);
        AgentActionParser.ParseResult parsed = agentActionParser.parse(rawAnswer);
        List<AgentActionView> actions = sanitizeActions(parsed.actions(), workbench);

        ChatMessageEntity userMsg = insertMessage(sessionId, "user", content);
        updateSessionTitle(session, content);
        ChatMessageEntity assistant = insertMessage(sessionId, "assistant", parsed.visibleAnswer());
        sessionMapper.updateById(session);
        return new ChatReplyView(toMessageView(userMsg), toMessageView(assistant), actions);
    }

    @Transactional
    public ChatReplyView onboard(long projectId, String authorizationHeader) {
        String userText = "请判断：这个项目主要在做什么？有哪些值得优先关注的问题？";
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
        return new ChatReplyView(toMessageView(userMsg), toMessageView(assistant), List.of());
    }

    private static String formatWorkbenchContext(WorkbenchContextDto workbench) {
        if (workbench == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n=== 工作台上下文（用户当前编辑态，勿外传） ===\n");
        if (workbench.filePath() != null && !workbench.filePath().isBlank()) {
            sb.append("当前文件：").append(workbench.filePath()).append('\n');
        }
        if (workbench.selectionStartLine() != null && workbench.selectionEndLine() != null) {
            sb.append("选中行：")
                    .append(workbench.selectionStartLine())
                    .append('-')
                    .append(workbench.selectionEndLine())
                    .append('\n');
        }
        if (workbench.viewportStartLine() != null && workbench.viewportEndLine() != null) {
            sb.append("可见行：")
                    .append(workbench.viewportStartLine())
                    .append('-')
                    .append(workbench.viewportEndLine())
                    .append('\n');
        }
        if (workbench.selectionSnippet() != null && !workbench.selectionSnippet().isBlank()) {
            sb.append("选中片段：\n").append(truncate(workbench.selectionSnippet(), 1500)).append('\n');
        }
        if (workbench.fileContent() != null && !workbench.fileContent().isBlank()) {
            sb.append("当前文件内容（节选）：\n").append(truncate(workbench.fileContent(), 8000)).append('\n');
        }
        if (workbench.javaFilePaths() != null && !workbench.javaFilePaths().isEmpty()) {
            sb.append("Java 源文件列表（节选，共 ").append(workbench.javaFilePaths().size()).append(" 条）：\n");
            int limit = Math.min(workbench.javaFilePaths().size(), 80);
            for (int i = 0; i < limit; i++) {
                sb.append("- ").append(workbench.javaFilePaths().get(i)).append('\n');
            }
        }
        return sb.toString();
    }

    private static List<AgentActionView> sanitizeActions(List<AgentActionView> actions, WorkbenchContextDto workbench) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        boolean javaOpen = workbench != null
                && workbench.filePath() != null
                && workbench.filePath().toLowerCase(Locale.ROOT).endsWith(".java");
        List<AgentActionView> out = new ArrayList<>();
        for (AgentActionView action : actions) {
            AgentActionView safe = sanitizeOne(action, javaOpen);
            if (safe != null) {
                out.add(safe);
            }
        }
        return out;
    }

    private static AgentActionView sanitizeOne(AgentActionView action, boolean javaFileOpen) {
        if (action == null || action.type() == null) {
            return null;
        }
        return switch (action.type()) {
            case "JAVADOC" -> javaFileOpen ? action : null;
            case "NAVIGATE" -> hasNavigateTarget(action) ? action : null;
            case "CLARIFY" -> hasClarifyOptions(action) ? action : null;
            default -> null;
        };
    }

    private static boolean hasNavigateTarget(AgentActionView action) {
        boolean hasSymbol = action.symbol() != null && !action.symbol().isBlank();
        boolean hasFile = action.filePath() != null && !action.filePath().isBlank();
        return hasSymbol || hasFile;
    }

    private static boolean hasClarifyOptions(AgentActionView action) {
        return action.options() != null && action.options().size() >= 2;
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
