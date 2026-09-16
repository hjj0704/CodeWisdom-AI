package com.codewisdom.agent.service.graph;

import org.bsc.langgraph4j.CompiledGraph;
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
import java.util.Optional;

/**
 * T-704：audit → fix → hitl 循环边全图（模拟节点，不调用真实 LLM）。
 */
public final class AgentPipelineGraph {

    public static final int MAX_HITL_ROUNDS = 5;

    private AgentPipelineGraph() {
    }

    public static class PipelineState extends AgentState {

        public PipelineState(Map<String, Object> initData) {
            super(initData);
        }

        public List<String> logs() {
            return this.<List<String>>value("logs").orElseGet(List::of);
        }

        public int fixRound() {
            return this.<Integer>value("fixRound").orElse(0);
        }

        public int hitlRound() {
            return this.<Integer>value("hitlRound").orElse(0);
        }

        public boolean approved() {
            return this.<Boolean>value("approved").orElse(false);
        }

        public boolean terminated() {
            return this.<Boolean>value("terminated").orElse(false);
        }
    }

    public record RunResult(PipelineState finalState, List<String> executionLog) {
    }

    static Map<String, Channel<?>> schema() {
        Map<String, Channel<?>> schema = new HashMap<>();
        schema.put("logs", Channels.appender(ArrayList::new));
        schema.put("fixRound", Channels.base(() -> 0));
        schema.put("hitlRound", Channels.base(() -> 0));
        schema.put("approved", Channels.base(() -> false));
        schema.put("terminated", Channels.base(() -> false));
        schema.put("rejectRemaining", Channels.base(() -> 0));
        return schema;
    }

    public static StateGraph<PipelineState> buildGraph() throws GraphStateException {
        StateGraph<PipelineState> graph = new StateGraph<>(schema(), PipelineState::new);

        graph.addNode("audit", AsyncNodeAction.node_async(state -> Map.of(
                "logs", List.of("audit:done"))));

        graph.addNode("fix", AsyncNodeAction.node_async(state -> {
            int next = state.fixRound() + 1;
            return Map.of(
                    "fixRound", next,
                    "logs", List.of("fix:round-" + next));
        }));

        graph.addNode("hitl", AsyncNodeAction.node_async(state -> {
            int rejectRemaining = state.<Integer>value("rejectRemaining").orElse(0);
            boolean rejectNow = rejectRemaining > 0;
            if (rejectNow) {
                return Map.of(
                        "rejectRemaining", rejectRemaining - 1,
                        "hitlRound", state.hitlRound() + 1,
                        "logs", List.of("hitl:rejected"));
            }
            return Map.of(
                    "approved", true,
                    "terminated", true,
                    "logs", List.of("hitl:approved"));
        }));

        graph.addEdge(GraphDefinition.START, "audit");
        graph.addEdge("audit", "fix");
        graph.addEdge("fix", "hitl");

        graph.addConditionalEdges("hitl",
                AsyncCommandAction.command_async((state, config) -> {
                    if (state.approved() || state.terminated()) {
                        return new Command(GraphDefinition.END);
                    }
                    if (state.hitlRound() >= MAX_HITL_ROUNDS) {
                        return new Command(GraphDefinition.END);
                    }
                    return new Command("fix");
                }),
                Map.of("fix", "fix", GraphDefinition.END, GraphDefinition.END));

        return graph;
    }

    public static RunResult run(int plannedRejects) throws GraphStateException {
        Map<String, Object> init = new HashMap<>();
        init.put("logs", new ArrayList<String>());
        init.put("fixRound", 0);
        init.put("hitlRound", 0);
        init.put("approved", false);
        init.put("terminated", false);
        init.put("rejectRemaining", plannedRejects);

        CompiledGraph<PipelineState> compiled = buildGraph().compile();
        Optional<PipelineState> finalState = compiled.invoke(init).stream().reduce((a, b) -> b);
        PipelineState state = finalState.orElseThrow();
        return new RunResult(state, state.logs());
    }
}
