package com.codewisdom.agent.dto;

import java.util.List;

public record AgentActionView(
        String type,
        String scope,
        String summary,
        boolean requiresConfirmation,
        String filePath,
        String symbol,
        Integer line,
        String clarifyQuestion,
        List<ClarifyOptionView> options
) {
}
