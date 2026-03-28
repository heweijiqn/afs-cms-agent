package com.afs.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

/**
 * LangGraph4j {@link AgentState} for the Interactive Multi-Turn AdOps Copilot ReAct Graph.
 */
final class CopilotGraphState extends AgentState {
    static final String TURN_ID = "turnId";
    static final String SESSION_ID = "sessionId";
    static final String PENDING_TOOLS = "pendingTools";
    static final String TOOL_ROUND = "toolRound";
    static final String FINAL_ANSWER = "finalAnswer";
    static final String MODEL_NAME = "modelName";
    static final String SOURCE = "source";
    static final String TOOL_TRACE = "toolTrace";
    static final String NODE_TRACE = "nodeTrace";
    static final String TOTAL_TOOL_CALLS = "totalToolCalls";
    static final String INPUT_TOKENS = "inputTokens";
    static final String OUTPUT_TOKENS = "outputTokens";

    static final Map<String, Channel<?>> SCHEMA = buildSchema();

    private static Map<String, Channel<?>> buildSchema() {
        var schema = new LinkedHashMap<String, Channel<?>>();
        schema.put(TOOL_TRACE, Channels.appender(ArrayList::new));
        schema.put(NODE_TRACE, Channels.appender(ArrayList::new));
        schema.put(TOTAL_TOOL_CALLS, Channels.base(Integer::sum, () -> 0));
        schema.put(INPUT_TOKENS, Channels.base(Integer::sum, () -> 0));
        schema.put(OUTPUT_TOKENS, Channels.base(Integer::sum, () -> 0));
        return Map.copyOf(schema);
    }

    CopilotGraphState(Map<String, Object> initData) {
        super(initData);
    }

    String turnId() { return this.<String>value(TURN_ID).orElse(""); }
    String sessionId() { return this.<String>value(SESSION_ID).orElse(""); }
    boolean hasPendingTools() { return this.<Boolean>value(PENDING_TOOLS).orElse(false); }
    int toolRound() { return this.<Integer>value(TOOL_ROUND).orElse(0); }
    String finalAnswer() { return this.<String>value(FINAL_ANSWER).orElse(""); }
    String modelName() { return this.<String>value(MODEL_NAME).orElse(""); }
    String source() { return this.<String>value(SOURCE).orElse("RULES"); }
    List<String> toolTrace() { return this.<List<String>>value(TOOL_TRACE).orElse(List.of()); }
    List<String> nodeTrace() { return this.<List<String>>value(NODE_TRACE).orElse(List.of()); }
    int totalToolCalls() { return this.<Integer>value(TOTAL_TOOL_CALLS).orElse(0); }
    int inputTokens() { return this.<Integer>value(INPUT_TOKENS).orElse(0); }
    int outputTokens() { return this.<Integer>value(OUTPUT_TOKENS).orElse(0); }
}
