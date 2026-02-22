package com.afs.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

/**
 * Strongly-typed LangGraph4j {@link AgentState} for the AFS Ad Optimization Multi-Agent Graph.
 * Every stored field uses standard Serializable primitives/strings/lists so LangGraph4j cloning
 * and {@link JdbcCheckpointSaver} persistence remain deterministic and restart-safe.
 */
final class OptimizationGraphState extends AgentState {
    static final String RUN_ID = "runId";
    static final String MODE = "mode"; // STANDARD | DEEP | RULES_ONLY
    static final String CONFIGURED = "configured";
    static final String INTAKE_SUMMARY = "intakeSummary";
    static final String RESEARCH_DIGEST = "researchDigest";
    static final String RAW_RESPONSE = "rawResponse";
    static final String PENDING_TOOLS = "pendingTools";
    static final String TOOL_ROUND = "toolRound";
    static final String REVISION_COUNT = "revisionCount";
    static final String CRITIC_FEEDBACK = "criticFeedback";
    static final String NEEDS_REFLECTION = "needsReflection";
    static final String DECISION_ACTION = "decisionAction";
    static final String DECISION_PERCENT = "decisionPercent";
    static final String DECISION_REASON = "decisionReason";
    static final String EXPLANATION_TEXT = "explanationText";
    static final String CONFIDENCE = "confidence";
    static final String RISK_LEVEL = "riskLevel";
    static final String GUARDRAIL_NOTE = "guardrailNote";
    static final String FAILURE_NOTE = "failureNote";
    static final String SOURCE = "source";
    static final String MODEL_NAME = "modelName";
    static final String STRUCTURED_JSON = "structuredJson";
    static final String EVIDENCE_REFS = "evidenceRefs";
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

    OptimizationGraphState(Map<String, Object> initData) {
        super(initData);
    }

    String runId() { return this.<String>value(RUN_ID).orElse(""); }
    String mode() { return this.<String>value(MODE).orElse("STANDARD"); }
    boolean configured() { return this.<Boolean>value(CONFIGURED).orElse(false); }
    String intakeSummary() { return this.<String>value(INTAKE_SUMMARY).orElse(""); }
    String researchDigest() { return this.<String>value(RESEARCH_DIGEST).orElse(""); }
    String rawResponse() { return this.<String>value(RAW_RESPONSE).orElse(""); }
    boolean hasPendingTools() { return this.<Boolean>value(PENDING_TOOLS).orElse(false); }
    int toolRound() { return this.<Integer>value(TOOL_ROUND).orElse(0); }
    int revisionCount() { return this.<Integer>value(REVISION_COUNT).orElse(0); }
    String criticFeedback() { return this.<String>value(CRITIC_FEEDBACK).orElse(""); }
    boolean needsReflection() { return this.<Boolean>value(NEEDS_REFLECTION).orElse(false); }
    String decisionAction() { return this.<String>value(DECISION_ACTION).orElse(null); }
    Integer decisionPercent() { return this.<Integer>value(DECISION_PERCENT).orElse(null); }
    String decisionReason() { return this.<String>value(DECISION_REASON).orElse(null); }
    String explanationText() { return this.<String>value(EXPLANATION_TEXT).orElse(null); }
    Double confidence() { return this.<Double>value(CONFIDENCE).orElse(null); }
    String riskLevel() { return this.<String>value(RISK_LEVEL).orElse(null); }
    String guardrailNote() { return this.<String>value(GUARDRAIL_NOTE).orElse(null); }
    String failureNote() { return this.<String>value(FAILURE_NOTE).orElse(null); }
    String source() { return this.<String>value(SOURCE).orElse(ExperimentAdvisor.RULES); }
    String modelName() { return this.<String>value(MODEL_NAME).orElse(null); }
    String structuredJson() { return this.<String>value(STRUCTURED_JSON).orElse(""); }
    List<String> evidenceRefs() { return this.<List<String>>value(EVIDENCE_REFS).orElse(List.of()); }
    List<String> toolTrace() { return this.<List<String>>value(TOOL_TRACE).orElse(List.of()); }
    List<String> nodeTrace() { return this.<List<String>>value(NODE_TRACE).orElse(List.of()); }
    int totalToolCalls() { return this.<Integer>value(TOTAL_TOOL_CALLS).orElse(0); }
    int inputTokens() { return this.<Integer>value(INPUT_TOKENS).orElse(0); }
    int outputTokens() { return this.<Integer>value(OUTPUT_TOKENS).orElse(0); }
}
