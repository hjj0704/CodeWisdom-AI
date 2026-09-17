package com.codewisdom.agent.dto;

import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
        @Size(max = 128)
        String title
) {
}
