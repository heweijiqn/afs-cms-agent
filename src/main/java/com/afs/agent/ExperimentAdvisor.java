package com.afs.agent;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Facade bridging {@link OptimizationWorkflow} to the compiled {@link AdOptimizationGraph}
 * (LangGraph4j + LangChain4j) and {@link AiModelGateway}.
 */
@Component
class ExperimentAdvisor {
    static final String RULES = "RULES";
    static final String PENDING = "PENDING";
    static final String MODEL = AiModelGateway.MODEL_SOURCE;
    static final String LOCAL_MODEL = AiModelGateway.LOCAL_SOURCE;
    static final String UNAVAILABLE = "RULES_MODEL_UNAVAILABLE";

    private final AiModelGateway gateway;
    private final AdOptimizationGraph graph;

    ExperimentAdvisor(AiModelGateway gateway, AdOptimizationGraph graph) {
        this.gateway = gateway;
        this.graph = graph;
    }

    /** True when a chat model is reachable from process configuration or the local vault. */
    boolean configured() {
        return gateway.isConfigured();
    }

    /** Deterministic template used when no model is configured or the model call fails. */
    static String fallback(OptimizationWorkflow.Proposal proposal) {
        return proposal.reason() + "。创意实验仅改变开头素材，比较 CTR 和 CPA。"
                + "受众实验保留同一创意，比较不同受众的 CPA 和 ROAS，等待归因窗口成熟后复盘。";
    }

    AdOptimizationGraph.GraphOutcome advise(String runId, String mode,
                                            OptimizationWorkflow.Input input,
                                            OptimizationWorkflow.Metrics metrics,
                                            OptimizationWorkflow.Proposal proposal,
                                            List<DiagnosticAgents.Finding> findings,
                                            CmsReportSource.Campaign report,
                                            LegacyContext.Snapshot context) {
        return graph.execute(runId, mode, input, metrics, proposal, findings, report, context);
    }
}
