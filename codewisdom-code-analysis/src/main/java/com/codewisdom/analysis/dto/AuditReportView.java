package com.codewisdom.analysis.dto;

import com.codewisdom.analysis.domain.AuditIssue;

import java.util.List;

public record AuditReportView(
        int total,
        int highCount,
        int mediumCount,
        int lowCount,
        List<AuditIssueItem> issues,
        int javaFilesTotal,
        int javaFilesScanned,
        int parseSkippedCount,
        boolean scanTruncated,
        String scanNote
) {
    public record AuditIssueItem(
            String issueKey,
            String ruleId,
            String category,
            String riskLevel,
            String filePath,
            int line,
            String description,
            String riskNote,
            String triggerSnippet
    ) {
        public static AuditIssueItem from(AuditIssue issue) {
            return new AuditIssueItem(
                    issue.ruleId() + "|" + issue.filePath() + "|" + issue.line(),
                    issue.ruleId(),
                    issue.category().name(),
                    issue.riskLevel().name(),
                    issue.filePath(),
                    issue.line(),
                    issue.description(),
                    issue.riskDescription(),
                    issue.trigger());
        }
    }
}
