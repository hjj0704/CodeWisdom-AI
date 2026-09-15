package com.codewisdom.agent.spike;

import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S3 冒烟用的最小状态与图定义。
 *
 * <p>用「审计 → 修复 → 人工审核（循环边）」三节点图模拟真实流水线骨架，
 * 目的是在写业务代码之前证明：条件分支、循环边、状态累加、终止条件都能跑通。
 */
final class SpikeGraphFixture {

    /** 人工审核最多打回次数，防止无限循环。 */
    static final int MAX_ROUNDS = 2;

    private SpikeGraphFixture() {
    }

    /** 图状态：日志按追加合并，轮次与通过标记按覆盖合并。 */
    public static class SpikeState extends AgentState {

        public SpikeState(Map<String, Object> initData) {
            super(initData);
        }

        public List<String> logs() {
            return this.<List<String>>value("logs").orElseGet(List::of);
        }

        public int round() {
            return this.<Integer>value("round").orElse(0);
        }

        public boolean approved() {
            return this.<Boolean>value("approved").orElse(false);
        }
    }

    /** 定义 channel 合并语义：logs 累加，其余覆盖。 */
    static Map<String, Channel<?>> schema() {
        Map<String, Channel<?>> schema = new HashMap<>();
        schema.put("logs", Channels.appender(ArrayList::new));
        schema.put("round", Channels.base(() -> 0));
        schema.put("approved", Channels.base(() -> false));
        return schema;
    }

    static Map<String, Object> initialState() {
        Map<String, Object> init = new HashMap<>();
        init.put("logs", new ArrayList<String>());
        init.put("round", 0);
        init.put("approved", false);
        return init;
    }

    /**
     * 构建三节点图：
     *
     * <pre>
     * START → audit → fix → hitl ─┬─(rejected, round &lt; MAX)─→ fix
     *                             └─(approved)──────────────→ END
     * </pre>
     */
    static StateGraph<SpikeState> buildGraph() throws GraphStateException {
        StateGraph<SpikeState> graph = new StateGraph<>(schema(), SpikeState::new);

        // 节点 1：静态审计（模拟，不调用真实 LLM）
        graph.addNode("audit", AsyncNodeAction.node_async(state -> Map.of(
                "logs", List.of("audit: 发现 2 处高危问题"))));

        // 节点 2：生成修复建议（模拟 LLM 调用）
        graph.addNode("fix", AsyncNodeAction.node_async(state -> {
            int next = state.round() + 1;
            return Map.of(
                    "round", next,
                    "logs", List.of("fix: 第 " + next + " 轮修复建议已生成"));
        }));

        // 节点 3：HITL 人工审核，纯状态推进
        graph.addNode("hitl", AsyncNodeAction.node_async(state ->
                Map.of("logs", List.of("hitl: 收到第 " + state.round() + " 轮审核结果"))));

        graph.addEdge(GraphDefinition.START, "audit");
        graph.addEdge("audit", "fix");
        graph.addEdge("fix", "hitl");

        // 条件边：审核未通过且未超轮次则回到 fix，否则结束
        graph.addConditionalEdges("hitl",
                AsyncCommandAction.command_async((state, config) -> {
                    if (state.round() >= MAX_ROUNDS) {
                        return new Command(GraphDefinition.END);
                    }
                    return new Command("fix");
                }),
                Map.of("fix", "fix",
                        GraphDefinition.END, GraphDefinition.END));

        return graph;
    }
}
