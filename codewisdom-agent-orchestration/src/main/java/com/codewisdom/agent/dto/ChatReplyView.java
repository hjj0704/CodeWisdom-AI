package com.codewisdom.agent.dto;

import java.util.List;

public record ChatReplyView(
        ChatMessageView userMessage,
        ChatMessageView assistantMessage,
        List<AgentActionView> actions
) {
}
