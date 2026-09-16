package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.domain.EvalErrorTrace;
import com.codewisdom.evaluation.domain.EvalErrorTrace.PipelineNode;
import com.codewisdom.evaluation.domain.EvalErrorTrace.TraceEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 错误溯源服务（T-903）：定位首个失败节点并序列化为 JSON。
 */
public class ErrorTraceService {

    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<TraceEntry> locateFirstFailure(List<TraceEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(0));
    }

    public PipelineNode locateFailedNode(List<TraceEntry> entries) {
        return locateFirstFailure(entries)
                .map(TraceEntry::node)
                .orElseThrow(() -> new IllegalArgumentException("无失败记录"));
    }

    public String toJson(List<TraceEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        try {
            return mapper.writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("溯源 JSON 序列化失败", e);
        }
    }
}
