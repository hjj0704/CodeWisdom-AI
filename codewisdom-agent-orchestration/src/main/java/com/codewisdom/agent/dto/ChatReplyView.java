package com.codewisdom.agent.dto;

public record ChatReplyView(
        ChatMessageView userMessage,
        ChatMessageView assistantMessage
) {
}
