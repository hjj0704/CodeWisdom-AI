package com.codewisdom.agent.dto;

import java.time.LocalDateTime;

public record ChatSessionView(
        Long id,
        Long projectId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
