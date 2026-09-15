package com.codewisdom.agent.spike;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.NodeOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 冒烟：验证 LangGraph4j 在本项目技术栈下真实可用。
 *
 * <p>本测试证明四件事，缺一不可：
 * <ol>
 *   <li>状态图能编译（schema / channel / 节点 / 边装配正确）；</li>
 *   <li>图能执行完并拿到最终 state；</li>
 *   <li><b>循环边</b>按预期回退——这是 HITL 驳回重生成的基础；</li>
 *   <li>能产出 <b>Mermaid</b> 图 —— 阶段 4 的架构图可直接复用该能力。</li>
 * </ol>
 *
 * <p>纯 JUnit，不启动 Spring 上下文，不调用真实 LLM。
 */
@DisplayName("S3 冒烟：LangGraph4j 编排")
class LangGraphSmokeTest {

    @Test
    @DisplayName("三节点图含条件分支可编译并执行到终止")
    void compilesAndRunsToCompletion() throws Exception {
        CompiledGraph<SpikeGraphFixture.SpikeState> graph =
                SpikeGraphFixture.buildGraph().compile();

        Optional<SpikeGraphFixture.SpikeState> result =
                graph.invoke(SpikeGraphFixture.initialState());

        assertThat(result).as("图必须执行到终止并返回最终 state").isPresent();
        SpikeGraphFixture.SpikeState state = result.get();

        assertThat(state.logs()).hasSize(5);
        assertThat(state.logs().get(0)).startsWith("audit:");
        assertThat(state.round()).isEqualTo(SpikeGraphFixture.MAX_ROUNDS);
    }

    @Test
    @DisplayName("循环边生效：审核未通过时回到 fix 重跑，直到达到轮次上限")
    void loopEdgeRepeatsUntilCap() throws Exception {
        CompiledGraph<SpikeGraphFixture.SpikeState> graph =
                SpikeGraphFixture.buildGraph().compile();

        List<String> visited = graph.stream(SpikeGraphFixture.initialState())
                .map(NodeOutput::node)
                .stream()
                .toList();

        // 注意：流式执行会带上 __START__ / __END__ 两个合成节点
        assertThat(visited).containsExactly(
                "__START__",
                "audit",
                "fix", "hitl",
                "fix", "hitl",
                "__END__");
        assertThat(visited.stream().filter("fix"::equals).count())
                .as("fix 节点应因循环边被重复执行")
                .isEqualTo(SpikeGraphFixture.MAX_ROUNDS);
    }

    @Test
    @DisplayName("可生成 Mermaid 图，阶段 4 架构图可直接复用")
    void producesMermaidRepresentation() throws Exception {
        CompiledGraph<SpikeGraphFixture.SpikeState> graph =
                SpikeGraphFixture.buildGraph().compile();

        GraphRepresentation mermaid =
                graph.getGraph(GraphRepresentation.Type.MERMAID, "HITL 修复循环", false);

        String content = mermaid.content();
        assertThat(content).contains("flowchart TD");
        assertThat(content).contains("audit").contains("fix").contains("hitl");
        // 条件边渲染为虚线，且两条分支（回退 fix / 终止）都在
        assertThat(content).contains("hitl:::hitl -.-> fix:::fix");
        assertThat(content).contains("hitl:::hitl -.-> __END__:::__END__");
        System.out.println("[S3] Mermaid 输出：\n" + content);
    }
}
