package com.codewisdom.agent.dto;

import com.codewisdom.agent.domain.fix.FixModels.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FixSuggestRequest(
        @NotEmpty(message = "issues 不能为空")
        List<IssueInput> issues
) {
    public record IssueInput(
            @NotBlank String ruleId,
            @NotBlank String filePath,
            int line,
            @NotBlank String description,
            String riskNote,
            @NotNull RiskLevel riskLevel
    ) {
    }
}
