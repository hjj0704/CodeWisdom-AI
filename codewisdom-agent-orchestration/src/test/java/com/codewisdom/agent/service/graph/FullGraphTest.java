package com.codewisdom.agent.service.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T-704 LangGraph 全图串联")
class FullGraphTest {

    @Test
    @DisplayName("含 1 次驳回重生成后通过")
    void oneRejectThenApprove() throws Exception {
        AgentPipelineGraph.RunResult result = AgentPipelineGraph.run(1);

        assertThat(result.executionLog()).containsExactly(
                "audit:done",
                "fix:round-1",
                "hitl:rejected",
                "fix:round-2",
                "hitl:approved");
        assertThat(result.finalState().fixRound()).isEqualTo(2);
        assertThat(result.finalState().approved()).isTrue();
    }
}
