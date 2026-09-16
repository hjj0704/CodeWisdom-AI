package com.codewisdom.agent.domain.fix;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 修复建议与 Diff 相关模型（T-701/T-702）。 */
public final class FixModels {

    private FixModels() {
    }

    public enum RiskLevel {
        HIGH, MEDIUM, LOW
    }

    /** 待修复的审计问题（与 code-analysis 的 AuditIssue 字段对齐）。 */
    public record FixTargetIssue(
            String ruleId,
            String filePath,
            int lineNo,
            String description,
            String riskNote,
            RiskLevel riskLevel
    ) {
        public FixTargetIssue {
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(filePath, "filePath");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(riskLevel, "riskLevel");
        }

        public String issueKey() {
            return ruleId + "|" + filePath + "|" + lineNo;
        }
    }

    public record FixSuggestion(
            String issueKey,
            String ruleId,
            String suggestion,
            String rationale
    ) {
        public FixSuggestion {
            Objects.requireNonNull(issueKey, "issueKey");
            Objects.requireNonNull(suggestion, "suggestion");
            Objects.requireNonNull(rationale, "rationale");
        }
    }

    public record FixSuggestResult(
            boolean skipped,
            List<FixSuggestion> suggestions
    ) {
        public FixSuggestResult {
            suggestions = List.copyOf(suggestions == null ? List.of() : suggestions);
        }

        public static FixSuggestResult notGenerated() {
            return new FixSuggestResult(true, List.of());
        }
    }

    /** 单文件 Diff 请求。 */
    public record DiffRequest(String filePath, String beforeContent, String afterContent) {
        public DiffRequest {
            Objects.requireNonNull(filePath, "filePath");
            Objects.requireNonNull(beforeContent, "beforeContent");
            Objects.requireNonNull(afterContent, "afterContent");
        }
    }

    public record DiffHunk(int oldStart, int oldLines, int newStart, int newLines, List<String> lines) {
        public DiffHunk {
            lines = List.copyOf(lines == null ? List.of() : lines);
        }
    }

    public record StructuredDiff(
            String filePath,
            String beforeContent,
            String afterContent,
            List<DiffHunk> hunks,
            boolean changed
    ) {
        public StructuredDiff {
            hunks = List.copyOf(hunks == null ? List.of() : hunks);
        }

        public static StructuredDiff unchanged(String filePath, String content) {
            return new StructuredDiff(filePath, content, content, List.of(), false);
        }
    }

    public enum HitlReviewStatus {
        PENDING, APPROVED, MODIFIED, REJECTED
    }

    public record HitlDecision(HitlReviewStatus status) {
        public HitlDecision {
            Objects.requireNonNull(status, "status");
        }
    }

    public record HitlReviewState(
            int round,
            boolean approved,
            boolean terminated,
            HitlReviewStatus lastStatus
    ) {
        public HitlReviewState {
            if (round < 0) {
                throw new IllegalArgumentException("round 不能为负");
            }
            Objects.requireNonNull(lastStatus, "lastStatus");
        }

        public static HitlReviewState initial() {
            return new HitlReviewState(0, false, false, HitlReviewStatus.PENDING);
        }

        public HitlReviewState withRound(int newRound) {
            return new HitlReviewState(newRound, approved, terminated, lastStatus);
        }

        public HitlReviewState withApproved(boolean newApproved) {
            return new HitlReviewState(round, newApproved, terminated, lastStatus);
        }

        public HitlReviewState withTerminated(boolean newTerminated) {
            return new HitlReviewState(round, approved, newTerminated, lastStatus);
        }

        public HitlReviewState withLastStatus(HitlReviewStatus status) {
            return new HitlReviewState(round, approved, terminated, status);
        }
    }
}
