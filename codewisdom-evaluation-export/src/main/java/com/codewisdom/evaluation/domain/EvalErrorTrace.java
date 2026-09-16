package com.codewisdom.evaluation.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 流水线错误溯源（T-903）：按节点记录失败原因，供评测报告使用。
 */
public final class EvalErrorTrace {

    private EvalErrorTrace() {
    }

    public enum PipelineNode {
        IMPORT,
        PARSE,
        ARCH_REVERSE,
        AUDIT,
        DOC_GEN,
        FIX_SUGGEST,
        HITL,
        EXPORT,
        EVAL
    }

    public record TraceEntry(
            PipelineNode node,
            String message,
            String detail
    ) {
        public TraceEntry {
            Objects.requireNonNull(node, "node");
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message 不能为空");
            }
            detail = detail == null ? "" : detail;
        }
    }

    public static final class Builder {
        private final List<TraceEntry> entries = new ArrayList<>();

        public Builder fail(PipelineNode node, String message) {
            return fail(node, message, "");
        }

        public Builder fail(PipelineNode node, String message, String detail) {
            entries.add(new TraceEntry(node, message, detail));
            return this;
        }

        public List<TraceEntry> build() {
            return List.copyOf(entries);
        }

        public TraceEntry firstFailure() {
            return entries.isEmpty() ? null : entries.get(0);
        }
    }
}
