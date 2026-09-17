package com.codewisdom.agent.dto;

import java.time.LocalDateTime;

public record ChatMessageView(
        Long id,
        Long sessionId,
        String role,
        String content,
        LocalDateTime createdAt
) {
}
