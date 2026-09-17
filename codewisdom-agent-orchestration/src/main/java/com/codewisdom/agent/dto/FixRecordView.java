package com.codewisdom.agent.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FixRecordView(
        Long id,
        String issueKey,
        String ruleId,
        String filePath,
        int lineNo,
        String riskLevel,
        String suggestion,
        String rationale,
        boolean diffChanged,
        LocalDateTime createdAt
) {
}
