package com.codewisdom.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 8000)
        String content,
        WorkbenchContextDto workbench
) {
}
