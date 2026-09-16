package com.codewisdom.evaluation.eval;

import com.codewisdom.evaluation.domain.EvalErrorTrace;
import com.codewisdom.evaluation.domain.EvalErrorTrace.PipelineNode;
import com.codewisdom.evaluation.domain.EvalErrorTrace.TraceEntry;
import com.codewisdom.evaluation.service.ErrorTraceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("T-903 错误溯源")
class ErrorTraceTest {

    private final ErrorTraceService service = new ErrorTraceService();

    @Test
    @DisplayName("能定位到首个失败节点与原因")
    void locatesFirstFailure() {
        List<TraceEntry> trace = new EvalErrorTrace.Builder()
                .fail(PipelineNode.IMPORT, "Git 克隆超时", "url=https://example.com/a.git")
                .fail(PipelineNode.AUDIT, "不应出现的后续错误")
                .build();

        TraceEntry first = service.locateFirstFailure(trace).orElseThrow();
        assertThat(first.node()).isEqualTo(PipelineNode.IMPORT);
        assertThat(first.message()).contains("克隆超时");
        assertThat(service.locateFailedNode(trace)).isEqualTo(PipelineNode.IMPORT);
    }

    @Test
    @DisplayName("溯源明细可序列化为 JSON 供报告入库")
    void serializesToJson() {
        List<TraceEntry> trace = List.of(
                new TraceEntry(PipelineNode.FIX_SUGGEST, "LLM 调用失败", "provider=deepseek"));

        String json = service.toJson(trace);
        assertThat(json).contains("FIX_SUGGEST").contains("LLM");
    }

    @Test
    @DisplayName("无失败记录时定位抛错")
    void emptyTraceRejected() {
        assertThatThrownBy(() -> service.locateFailedNode(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
