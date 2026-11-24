package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Commercial-grade Ad Optimization Multi-Agent Workflow compiled on {@code LangGraph4j} + {@code LangChain4j}.
 *
 * <p>Graph Topology:
 * <pre>
 * START
 *   -> intake_context        (Real CMS data grounding + quantitative trend & anomaly baseline)
 *      -[unconfigured/rules]-> rule_baseline -> guardrail_critic -> finalize_plan -> END
 *      -[DEEP mode]----------> research_specialist <-> tool_executor -> strategy_planner
 *      -[STANDARD mode]------> strategy_planner <-> tool_executor
 *   -> guardrail_critic      (Schema, numeric bound, & commercial safety critic)
 *      -[needs_reflection]---> self_reflect -> strategy_planner   (LangGraph self-correction cycle)
 *      -[approved]-----------> finalize_plan -> END
 * </pre>
 */
@Component
class AdOptimizationGraph {
    static final String GRAPH_NAME = "afs_ad_optimization_graph";
    static final String NODE_INTAKE = "intake_context";
    static final String NODE_RULES = "rule_baseline";
    static final String NODE_RESEARCH = "research_specialist";
    static final String NODE_PLANNER = "strategy_planner";
    static final String NODE_TOOLS = "tool_executor";
    static final String NODE_CRITIC = "guardrail_critic";
    static final String NODE_REFLECT = "self_reflect";
    static final String NODE_FINALIZE = "finalize_plan";

    private static final Logger log = LoggerFactory.getLogger(AdOptimizationGraph.class);
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final int MAX_REVISIONS = 1;
    private static final int MAX_TOOL_OUTPUT = 6500;
    private static final Set<String> VALID_ACTIONS = Set.of("HOLD", "REDUCE", "INCREASE");

    private final AiModelGateway gateway;
    private final ObjectMapper json;
    private final CmsReportSource reports;
    private final CmsCampaignDataService cmsData;
    private final JdbcCheckpointSaver checkpointSaver;
    private final AgentTelemetryService telemetry;
    private final CompiledGraph<OptimizationGraphState> compiledGraph;
    private final String mermaidTopology;
    private final Map<String, RunSession> activeSessions = new ConcurrentHashMap<>();

    AdOptimizationGraph(AiModelGateway gateway, ObjectMapper json, CmsReportSource reports,
                        CmsCampaignDataService cmsData, JdbcCheckpointSaver checkpointSaver,
                        AgentTelemetryService telemetry) throws Exception {
        this.gateway = gateway;
        this.json = json;
        this.reports = reports;
        this.cmsData = cmsData;
        this.checkpointSaver = checkpointSaver;
        this.telemetry = telemetry;
        this.compiledGraph = buildGraph();
        this.mermaidTopology = compiledGraph
                .getGraph(GraphRepresentation.Type.MERMAID, "AFS AdOps LangGraph4j Workflow", false)
                .content();
    }

    String mermaidTopology() {
        return mermaidTopology;
    }

    /**
     * Executes the compiled LangGraph4j graph with JDBC checkpointing and real-time node streaming.
     */
    GraphOutcome execute(String runId, String mode, OptimizationWorkflow.Input input,
                         OptimizationWorkflow.Metrics metrics, OptimizationWorkflow.Proposal ruleProposal,
                         List<DiagnosticAgents.Finding> baselineFindings,
                         CmsReportSource.Campaign report, LegacyContext.Snapshot context) {
        long started = System.nanoTime();
        String executionKey = (runId == null || runId.isBlank() ? UUID.randomUUID().toString() : runId);
        String resolvedMode = normalizeMode(mode);
        AdvisoryTools advisoryTools = new AdvisoryTools(json, input, report, context, reports, cmsData);
        LC4jToolService toolService = LC4jToolService.builder().toolsFromObject(advisoryTools).build();
        RunSession session = new RunSession(executionKey, resolvedMode, input, metrics, ruleProposal,
                baselineFindings, report, context, advisoryTools, toolService);
        activeSessions.put(executionKey, session);

        try {
            Map<String, Object> initial = new LinkedHashMap<>();
            initial.put(OptimizationGraphState.RUN_ID, executionKey);
            initial.put(OptimizationGraphState.MODE, resolvedMode);
            initial.put(OptimizationGraphState.TOOL_ROUND, 0);
            initial.put(OptimizationGraphState.REVISION_COUNT, 0);
            initial.put(OptimizationGraphState.PENDING_TOOLS, false);
            initial.put(OptimizationGraphState.NEEDS_REFLECTION, false);

            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId("run-" + executionKey)
                    .build();

            OptimizationGraphState finalState = null;
            for (NodeOutput<OptimizationGraphState> output : compiledGraph.stream(initial, runnableConfig)) {
                finalState = output.state();
            }
            if (finalState == null) {
                throw new IllegalStateException("LangGraph4j workflow produced no terminal state");
            }
            return buildOutcome(finalState, session, ruleProposal, millis(started));
        } catch (Exception failure) {
            String reason = AiModelGateway.redact(session.resolvedModel, AiModelGateway.describe(failure));
            log.warn("LangGraph4j 优化图执行异常 run={}：{}", executionKey, reason, failure);
            telemetry.recordNode(executionKey, null, GRAPH_NAME, "graph_error", "FAILED",
                    session.resolvedModel == null ? "" : session.resolvedModel.chatModel(),
                    0, 0, 0, millis(started), reason);
            return GraphOutcome.failed(ExperimentAdvisor.UNAVAILABLE,
                    session.resolvedModel == null ? null : session.resolvedModel.chatModel(),
                    reason, millis(started));
        } finally {
            activeSessions.remove(executionKey);
        }
    }

    private CompiledGraph<OptimizationGraphState> buildGraph() throws Exception {
        StateGraph<OptimizationGraphState> graph = new StateGraph<>(
                OptimizationGraphState.SCHEMA, OptimizationGraphState::new)
                .addNode(NODE_INTAKE, node_async(this::intakeContextNode))
                .addNode(NODE_RULES, node_async(this::ruleBaselineNode))
                .addNode(NODE_RESEARCH, node_async(this::researchSpecialistNode))
                .addNode(NODE_PLANNER, node_async(this::strategyPlannerNode))
                .addNode(NODE_TOOLS, node_async(this::toolExecutorNode))
                .addNode(NODE_CRITIC, node_async(this::guardrailCriticNode))
                .addNode(NODE_REFLECT, node_async(this::selfReflectNode))
                .addNode(NODE_FINALIZE, node_async(this::finalizePlanNode))
                .addEdge(START, NODE_INTAKE)
                .addConditionalEdges(NODE_INTAKE, edge_async(state -> {
                    if (!state.configured() || "RULES_ONLY".equals(state.mode())) {
                        return NODE_RULES;
                    }
                    return "DEEP".equals(state.mode()) ? NODE_RESEARCH : NODE_PLANNER;
                }), Map.of(
                        NODE_RULES, NODE_RULES,
                        NODE_RESEARCH, NODE_RESEARCH,
                        NODE_PLANNER, NODE_PLANNER))
                .addEdge(NODE_RULES, NODE_CRITIC)
                .addConditionalEdges(NODE_RESEARCH, edge_async(state -> {
                    if (state.hasPendingTools() && state.toolRound() < MAX_TOOL_ROUNDS) {
                        return NODE_TOOLS;
                    }
                    return NODE_PLANNER;
                }), Map.of(NODE_TOOLS, NODE_TOOLS, NODE_PLANNER, NODE_PLANNER))
                .addConditionalEdges(NODE_PLANNER, edge_async(state -> {
                    if (state.hasPendingTools() && state.toolRound() < MAX_TOOL_ROUNDS) {
                        return NODE_TOOLS;
                    }
                    return NODE_CRITIC;
                }), Map.of(NODE_TOOLS, NODE_TOOLS, NODE_CRITIC, NODE_CRITIC))
                .addConditionalEdges(NODE_TOOLS, edge_async(state -> {
                    RunSession session = activeSessions.get(state.runId());
                    if (session != null && session.inResearchPhase) {
                        return NODE_RESEARCH;
                    }
                    return NODE_PLANNER;
                }), Map.of(NODE_RESEARCH, NODE_RESEARCH, NODE_PLANNER, NODE_PLANNER))
                .addConditionalEdges(NODE_CRITIC, edge_async(state -> {
                    if (state.needsReflection() && state.revisionCount() < MAX_REVISIONS) {
                        return NODE_REFLECT;
                    }
                    return NODE_FINALIZE;
                }), Map.of(NODE_REFLECT, NODE_REFLECT, NODE_FINALIZE, NODE_FINALIZE))
                .addEdge(NODE_REFLECT, NODE_PLANNER)
                .addEdge(NODE_FINALIZE, END);

        CompileConfig config = CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .build();
        CompiledGraph<OptimizationGraphState> compiled = graph.compile(config);
        compiled.setMaxIterations(24);
        return compiled;
    }

    /** Node 1: Data ingestion, real CMS quantitative trend computation, and model availability check. */
    private Map<String, Object> intakeContextNode(OptimizationGraphState state) {
        long t0 = System.nanoTime();
        RunSession session = requireSession(state);
        String trend = summarizeDailyTrend(session.report);
        boolean configured = false;
        String note = null;
        if (!"RULES_ONLY".equals(session.mode)) {
            try {
                session.resolvedModel = gateway.resolve();
                configured = session.resolvedModel != null;
            } catch (Exception ex) {
                note = "AI 平台配置读取失败：" + AiModelGateway.describe(ex);
                log.warn(note);
            }
        }
        String summary = "活动 " + session.input.campaignId()
                + " | ROAS=" + fmt(session.metrics.roas())
                + " (目标 " + session.input.targetRoas() + ")"
                + " | 上限 ±" + session.tools.effectiveLimit() + "%"
                + " | " + trend;
        telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_INTAKE, "OK",
                configured ? session.resolvedModel.chatModel() : "rules",
                0, 0, 0, millis(t0), summary);

        var out = new LinkedHashMap<String, Object>();
        out.put(OptimizationGraphState.CONFIGURED, configured);
        out.put(OptimizationGraphState.INTAKE_SUMMARY, summary);
        out.put(OptimizationGraphState.NODE_TRACE, NODE_INTAKE + ":" + millis(t0) + "ms");
        if (note != null) {
            out.put(OptimizationGraphState.FAILURE_NOTE, note);
            out.put(OptimizationGraphState.SOURCE, ExperimentAdvisor.UNAVAILABLE);
        }
        return out;
    }

    /** Node 2 (Fallback): Deterministic rule baseline when AI model is not configured. */
    private Map<String, Object> ruleBaselineNode(OptimizationGraphState state) {
        long t0 = System.nanoTime();
        RunSession session = requireSession(state);
        String source = state.failureNote() != null && !state.failureNote().isBlank()
                ? ExperimentAdvisor.UNAVAILABLE : ExperimentAdvisor.RULES;
        String text = ExperimentAdvisor.fallback(session.ruleProposal);
        telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_RULES, "OK",
                "rules", 0, 0, 0, millis(t0),
                "规则基线：" + session.ruleProposal.action() + " " + session.ruleProposal.changePercent() + "%");

        var out = new LinkedHashMap<String, Object>();
        out.put(OptimizationGraphState.SOURCE, source);
        out.put(OptimizationGraphState.EXPLANATION_TEXT, text);
        out.put(OptimizationGraphState.NODE_TRACE, NODE_RULES + ":" + millis(t0) + "ms");
        return out;
    }

    /**
     * Node 3 (DEEP mode): Dedicated Research & Attribution Specialist LLM agent.
     * Gathers multi-dimensional evidence via LangGraph4j ToolNode before handing off to Strategy Planner.
     */
    private Map<String, Object> researchSpecialistNode(OptimizationGraphState state) {
        long t0 = System.nanoTime();
        RunSession session = requireSession(state);
        session.inResearchPhase = true;
        if (session.researchMessages.isEmpty()) {
            session.researchMessages.add(SystemMessage.from("""
                    你是 AFS 商业广告投放「数据归因与异常排查专家」(Research Specialist)。
                    你的职责是通过只读工具采集当前活动的真实多维证据：逐日趋势、国家/受众盈亏、落地页与创意素材、同站点基准和历史审批记忆。
                    规则：
                    1. 优先并行或按需调用 dailyReport、countryBreakdown、creativeAndLandingContext、peerBenchmarks、historicalRuns。
                    2. 绝不编造工具未返回的数据。
                    3. 收集完关键证据后，用简洁的中文要点输出《多维证据摘要》，指出核心盈亏归因、异常点和可复用历史教训，不要输出预算执行声明。
                    """));
            session.researchMessages.add(UserMessage.from(buildUserPayload(session, state.intakeSummary(), null, null)));
        }
        try {
            boolean allowTools = state.toolRound() < Math.min(3, MAX_TOOL_ROUNDS);
            var call = gateway.chat(session.resolvedModel, session.researchMessages,
                    allowTools ? session.toolNode.toolSpecifications() : List.of());
            session.lastAiMessage = call.message();
            session.researchMessages.add(call.message());
            boolean hasTools = allowTools && call.message().hasToolExecutionRequests();
            String digest = hasTools ? "" : safeText(call.message().text());
            if (!hasTools) {
                session.inResearchPhase = false;
            }
            telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_RESEARCH, "OK",
                    call.modelName(), call.inputTokens(), call.outputTokens(), 0, millis(t0),
                    hasTools ? "请求 " + call.message().toolExecutionRequests().size() + " 个取数工具"
                            : "完成多维归因证据采集（" + digest.length() + " 字）");

            var out = new LinkedHashMap<String, Object>();
            out.put(OptimizationGraphState.PENDING_TOOLS, hasTools);
            out.put(OptimizationGraphState.MODEL_NAME, call.modelName());
            out.put(OptimizationGraphState.SOURCE, call.source());
            out.put(OptimizationGraphState.INPUT_TOKENS, call.inputTokens());
            out.put(OptimizationGraphState.OUTPUT_TOKENS, call.outputTokens());
            out.put(OptimizationGraphState.NODE_TRACE, NODE_RESEARCH + ":" + millis(t0) + "ms");
            if (!hasTools) {
                out.put(OptimizationGraphState.RESEARCH_DIGEST, digest);
            }
            return out;
        } catch (Exception failure) {
            session.inResearchPhase = false;
            String reason = AiModelGateway.redact(session.resolvedModel, AiModelGateway.describe(failure));
            log.warn("Research Specialist 失败，降级进入 Strategy Planner：{}", reason);
            telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_RESEARCH, "DEGRADED",
                    session.resolvedModel.chatModel(), 0, 0, 0, millis(t0), reason);
            var out = new LinkedHashMap<String, Object>();
            out.put(OptimizationGraphState.PENDING_TOOLS, false);
            out.put(OptimizationGraphState.RESEARCH_DIGEST, "（研究节点降级：" + reason + "）");
            out.put(OptimizationGraphState.NODE_TRACE, NODE_RESEARCH + ":degraded");
            return out;
        }
    }

    /**
     * Node 4: Strategy & Bidding Planner LLM agent (ReAct tool-calling + structured commercial JSON output).
     */
    private Map<String, Object> strategyPlannerNode(OptimizationGraphState state) {
        long t0 = System.nanoTime();
        RunSession session = requireSession(state);
        session.inResearchPhase = false;
        int limit = session.tools.effectiveLimit();
        if (session.plannerMessages.isEmpty()) {
            session.plannerMessages.add(SystemMessage.from(strategySystemPrompt(limit)));
            session.plannerMessages.add(UserMessage.from(buildUserPayload(
                    session, state.intakeSummary(), state.researchDigest(), null)));
        }
        boolean allowTools = !session.disableToolsForFallback && state.toolRound() < MAX_TOOL_ROUNDS;
        try {
            var call = gateway.chat(session.resolvedModel, session.plannerMessages,
                    allowTools ? session.toolNode.toolSpecifications() : List.of());
            session.lastAiMessage = call.message();
            session.plannerMessages.add(call.message());
            boolean hasTools = allowTools && call.message().hasToolExecutionRequests();
            String summary = hasTools
                    ? "调用工具：" + call.message().toolExecutionRequests().stream()
                            .map(ToolExecutionRequest::name).toList()
                    : "生成结构化投放优化策略" + (call.fallbackUsed() ? "（备选模型）" : "");
            telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_PLANNER, "OK",
                    call.modelName(), call.inputTokens(), call.outputTokens(), 0, millis(t0), summary);

            var out = new LinkedHashMap<String, Object>();
            out.put(OptimizationGraphState.PENDING_TOOLS, hasTools);
            out.put(OptimizationGraphState.MODEL_NAME, call.modelName());
            out.put(OptimizationGraphState.SOURCE, call.source());
            out.put(OptimizationGraphState.INPUT_TOKENS, call.inputTokens());
            out.put(OptimizationGraphState.OUTPUT_TOKENS, call.outputTokens());
            out.put(OptimizationGraphState.NODE_TRACE, NODE_PLANNER + ":" + millis(t0) + "ms");
            if (!hasTools) {
                out.put(OptimizationGraphState.RAW_RESPONSE, safeText(call.message().text()));
            }
            if (call.fallbackUsed()) {
                out.put(OptimizationGraphState.FAILURE_NOTE, "主模型不可用，已自动切换至备选模型 " + call.modelName());
            }
            return out;
        } catch (Exception failure) {
            String reason = AiModelGateway.redact(session.resolvedModel, AiModelGateway.describe(failure));
            // Graceful degradation: if tool-calling endpoint rejected tools, retry once cleanly without tools.
            if (allowTools) {
                log.warn("带工具的策略调用失败，切换无工具重试：{}", reason);
                session.disableToolsForFallback = true;
                try {
                    List<ChatMessage> plainMessages = List.of(
                            SystemMessage.from(strategySystemPrompt(limit) + "\n本次不提供工具，请直接依据已有数据作答。"),
                            UserMessage.from(buildUserPayload(session, state.intakeSummary(), state.researchDigest(), null)));
                    var retry = gateway.chat(session.resolvedModel, plainMessages, List.of());
                    telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_PLANNER, "RECOVERED",
                            retry.modelName(), retry.inputTokens(), retry.outputTokens(), 0, millis(t0),
                            "工具模式失败，无工具重试成功");
                    var out = new LinkedHashMap<String, Object>();
                    out.put(OptimizationGraphState.PENDING_TOOLS, false);
                    out.put(OptimizationGraphState.MODEL_NAME, retry.modelName());
                    out.put(OptimizationGraphState.SOURCE, retry.source());
                    out.put(OptimizationGraphState.RAW_RESPONSE, safeText(retry.message().text()));
                    out.put(OptimizationGraphState.INPUT_TOKENS, retry.inputTokens());
                    out.put(OptimizationGraphState.OUTPUT_TOKENS, retry.outputTokens());
                    out.put(OptimizationGraphState.FAILURE_NOTE, "工具调用失败（" + reason + "），已自动改用无工具模式完成分析");
                    out.put(OptimizationGraphState.NODE_TRACE, NODE_PLANNER + ":recovered:" + millis(t0) + "ms");
                    return out;
                } catch (Exception retryFailure) {
                    reason = reason + " / 无工具重试同样失败："
                            + AiModelGateway.redact(session.resolvedModel, AiModelGateway.describe(retryFailure));
                }
            }
            log.warn("AI 策略节点最终失败，降级为规则模板：{}", reason);
            telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_PLANNER, "FAILED",
                    session.resolvedModel.chatModel(), 0, 0, 0, millis(t0), reason);
            var out = new LinkedHashMap<String, Object>();
            out.put(OptimizationGraphState.PENDING_TOOLS, false);
            out.put(OptimizationGraphState.SOURCE, ExperimentAdvisor.UNAVAILABLE);
            out.put(OptimizationGraphState.MODEL_NAME, session.resolvedModel.chatModel());
            out.put(OptimizationGraphState.FAILURE_NOTE, reason);
            out.put(OptimizationGraphState.NODE_TRACE, NODE_PLANNER + ":failed:" + millis(t0) + "ms");
            return out;
        }
    }

    /**
     * Node 5: Native LangGraph4j {@link ToolNode} executor running LangChain4j {@code @Tool} methods.
     */
    private Map<String, Object> toolExecutorNode(OptimizationGraphState state) {
        long t0 = System.nanoTime();
        RunSession session = requireSession(state);
        AiMessage last = session.lastAiMessage;
        List<String> executed = new ArrayList<>();
        int count = 0;
        List<ChatMessage> targetHistory = session.inResearchPhase
                ? session.researchMessages : session.plannerMessages;

        if (last != null && last.hasToolExecutionRequests()) {
            for (ToolExecutionRequest request : last.toolExecutionRequests()) {
                ToolExecutionResultMessage resultMsg = executeSingleTool(session.toolNode, request);
                targetHistory.add(resultMsg);
                count++;
                executed.add(request.name() + "(" + abbreviate(request.arguments(), 100) + ")");
            }
        }
        telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_TOOLS, "OK",
                session.resolvedModel == null ? "" : session.resolvedModel.chatModel(),
                0, 0, count, millis(t0), "执行 " + count + " 次工具：" + String.join(", ", executed));

        var out = new LinkedHashMap<String, Object>();
        out.put(OptimizationGraphState.PENDING_TOOLS, false);
        out.put(OptimizationGraphState.TOOL_ROUND, state.toolRound() + 1);
        out.put(OptimizationGraphState.TOTAL_TOOL_CALLS, count);
        out.put(OptimizationGraphState.TOOL_TRACE, executed);
        out.put(OptimizationGraphState.NODE_TRACE, NODE_TOOLS + "(" + count + "):" + millis(t0) + "ms");
        return out;
    }

    /**
     * Node 6: Commercial Safety Critic & Guardrail Verifier.
     * Parses structured JSON, verifies numeric/policy invariants, and triggers LangGraph
     * self-reflection if a structured JSON response violated hard safety rules.
     */
    private Map<String, Object> guardrailCriticNode(OptimizationGraphState state) {
        long t0 = System.nanoTime();
        RunSession session = requireSession(state);
        var out = new LinkedHashMap<String, Object>();
        String source = state.source();

        if (ExperimentAdvisor.RULES.equals(source) || ExperimentAdvisor.UNAVAILABLE.equals(source)) {
            telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_CRITIC, "OK",
                    "guardrail", 0, 0, 0, millis(t0), "规则/降级路径通过静态安全校验");
            out.put(OptimizationGraphState.NEEDS_REFLECTION, false);
            out.put(OptimizationGraphState.NODE_TRACE, NODE_CRITIC + ":" + millis(t0) + "ms");
            return out;
        }

        String raw = state.rawResponse();
        if (raw == null || raw.isBlank()) {
            telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_CRITIC, "FAILED",
                    state.modelName(), 0, 0, 0, millis(t0), "模型返回空内容");
            out.put(OptimizationGraphState.SOURCE, ExperimentAdvisor.UNAVAILABLE);
            out.put(OptimizationGraphState.FAILURE_NOTE, "模型返回空内容");
            out.put(OptimizationGraphState.NEEDS_REFLECTION, false);
            out.put(OptimizationGraphState.NODE_TRACE, NODE_CRITIC + ":empty");
            return out;
        }

        String clean = stripFences(raw);
        ParsedStrategy parsed = parseStrategy(clean, session.ruleProposal);
        session.parsedStrategy = parsed;

        // Check if structured JSON violated hard bounds and deserves a LangGraph reflection cycle.
        int limit = session.tools.effectiveLimit();
        List<String> violations = new ArrayList<>();
        if (parsed.structuredJsonFound()) {
            if (parsed.action() == null || !VALID_ACTIONS.contains(parsed.action())) {
                violations.add("action 必须为 HOLD / REDUCE / INCREASE");
            } else {
                int pct = parsed.changePercent() == null ? 0 : parsed.changePercent();
                if (Math.abs(pct) > limit && limit > 0) {
                    violations.add("changePercent=" + pct + " 超出当前允许上限 ±" + limit + "%");
                }
                if ("INCREASE".equals(parsed.action()) && pct <= 0) {
                    violations.add("INCREASE 动作必须使用正数 changePercent");
                }
                if ("REDUCE".equals(parsed.action()) && pct >= 0) {
                    violations.add("REDUCE 动作必须使用负数 changePercent");
                }
                if ("HOLD".equals(parsed.action()) && pct != 0) {
                    violations.add("HOLD 动作的 changePercent 必须为 0");
                }
            }
        }

        if (!violations.isEmpty() && state.revisionCount() < MAX_REVISIONS) {
            String feedback = String.join("；", violations);
            telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_CRITIC, "REFLECT",
                    state.modelName(), 0, 0, 0, millis(t0), "触发自检反思：" + feedback);
            out.put(OptimizationGraphState.NEEDS_REFLECTION, true);
            out.put(OptimizationGraphState.CRITIC_FEEDBACK, feedback);
            out.put(OptimizationGraphState.NODE_TRACE, NODE_CRITIC + ":reflect:" + millis(t0) + "ms");
            return out;
        }

        String explanation = AiModelGateway.redact(session.resolvedModel, parsed.explanation());
        telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_CRITIC, "OK",
                state.modelName(), 0, 0, 0, millis(t0),
                parsed.action() == null
                        ? "文本分析通过红线校验（决策交由规则护栏）"
                        : "策略通过校验：" + parsed.action() + " " + parsed.changePercent() + "%（置信度 "
                                + (parsed.confidence() == null ? "—" : Math.round(parsed.confidence() * 100) + "%") + "）");

        out.put(OptimizationGraphState.NEEDS_REFLECTION, false);
        if (parsed.action() != null) out.put(OptimizationGraphState.DECISION_ACTION, parsed.action());
        if (parsed.changePercent() != null) out.put(OptimizationGraphState.DECISION_PERCENT, parsed.changePercent());
        if (parsed.reason() != null) out.put(OptimizationGraphState.DECISION_REASON, parsed.reason());
        out.put(OptimizationGraphState.EXPLANATION_TEXT, explanation);
        if (parsed.confidence() != null) out.put(OptimizationGraphState.CONFIDENCE, parsed.confidence());
        if (parsed.riskLevel() != null) out.put(OptimizationGraphState.RISK_LEVEL, parsed.riskLevel());
        out.put(OptimizationGraphState.EVIDENCE_REFS, new ArrayList<>(parsed.evidenceRefs()));
        out.put(OptimizationGraphState.STRUCTURED_JSON, parsed.rawJson() == null ? "" : parsed.rawJson());
        out.put(OptimizationGraphState.NODE_TRACE, NODE_CRITIC + ":" + millis(t0) + "ms");
        return out;
    }

    /** Node 7: LangGraph Reflection node injecting Critic feedback back into Strategy Planner. */
    private Map<String, Object> selfReflectNode(OptimizationGraphState state) {
        long t0 = System.nanoTime();
        RunSession session = requireSession(state);
        String feedback = state.criticFeedback();
        session.plannerMessages.add(UserMessage.from(
                "护栏质检节点发现你的上一版 JSON 方案存在违规项：" + feedback
                        + "。\n请立即修正并只输出符合约定的单个 JSON 对象，不要调用工具。"));
        session.disableToolsForFallback = true;
        telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_REFLECT, "OK",
                state.modelName(), 0, 0, 0, millis(t0), "回写质检反馈并重新规划：" + feedback);

        var out = new LinkedHashMap<String, Object>();
        out.put(OptimizationGraphState.REVISION_COUNT, state.revisionCount() + 1);
        out.put(OptimizationGraphState.NEEDS_REFLECTION, false);
        out.put(OptimizationGraphState.NODE_TRACE, NODE_REFLECT + ":" + millis(t0) + "ms");
        return out;
    }

    /** Node 8: Final state packaging & telemetry completion checkpoint. */
    private Map<String, Object> finalizePlanNode(OptimizationGraphState state) {
        long t0 = System.nanoTime();
        RunSession session = requireSession(state);
        telemetry.recordNode(session.runId, null, GRAPH_NAME, NODE_FINALIZE, "OK",
                state.modelName() == null ? "rules" : state.modelName(),
                0, 0, 0, millis(t0),
                "图执行完成 · 节点 " + state.nodeTrace().size() + " · 工具 " + state.totalToolCalls()
                        + " · Tokens " + (state.inputTokens() + state.outputTokens()));
        var out = new LinkedHashMap<String, Object>();
        out.put(OptimizationGraphState.NODE_TRACE, NODE_FINALIZE + ":" + millis(t0) + "ms");
        return out;
    }

    private ToolExecutionResultMessage executeSingleTool(LC4jToolService toolNode, ToolExecutionRequest request) {
        try {
            var result = toolNode.execute(request);
            if (result.isPresent()) {
                String text = result.get().text();
                if (text != null && text.length() > MAX_TOOL_OUTPUT) {
                    return ToolExecutionResultMessage.from(request,
                            text.substring(0, MAX_TOOL_OUTPUT) + "\n…工具输出已截断");
                }
                return result.get();
            }
            return ToolExecutionResultMessage.from(request,
                    "{\"available\":false,\"reason\":\"未知工具：" + request.name() + "\"}");
        } catch (Exception failure) {
            return ToolExecutionResultMessage.from(request,
                    "{\"available\":false,\"reason\":\""
                            + AiModelGateway.describe(failure).replace('"', '\'') + "\"}");
        }
    }

    private GraphOutcome buildOutcome(OptimizationGraphState state, RunSession session,
                                      OptimizationWorkflow.Proposal ruleProposal, long totalMs) {
        ParsedStrategy parsed = session.parsedStrategy;
        List<DiagnosticAgents.Finding> aiFindings = parsed == null ? List.of() : parsed.stageFindings();
        List<String> actionItems = parsed == null ? List.of() : parsed.actionItems();
        List<String> experiments = parsed == null ? List.of() : parsed.experiments();
        List<String> tools = state.toolTrace();
        if (tools.size() > 16) tools = tools.subList(0, 16);
        String text = state.explanationText();
        if ((text == null || text.isBlank())
                && (ExperimentAdvisor.RULES.equals(state.source()) || ExperimentAdvisor.UNAVAILABLE.equals(state.source()))) {
            text = ExperimentAdvisor.fallback(ruleProposal);
        }
        return new GraphOutcome(
                state.source(),
                state.modelName(),
                text,
                state.decisionAction(),
                state.decisionPercent(),
                state.decisionReason(),
                state.evidenceRefs(),
                totalMs,
                state.totalToolCalls(),
                List.copyOf(tools),
                state.failureNote(),
                state.confidence(),
                state.riskLevel(),
                state.inputTokens(),
                state.outputTokens(),
                state.revisionCount(),
                List.copyOf(state.nodeTrace()),
                aiFindings,
                actionItems,
                experiments,
                state.mode());
    }

    private ParsedStrategy parseStrategy(String text, OptimizationWorkflow.Proposal proposal) {
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        if (open >= 0 && close > open) {
            String candidate = text.substring(open, close + 1);
            try {
                JsonNode node = json.readTree(candidate);
                if (node.isObject() && node.has("action")) {
                    String action = node.path("action").asText("").trim().toUpperCase(Locale.ROOT);
                    Integer pct = node.path("changePercent").isNumber() ? node.path("changePercent").asInt() : 0;
                    String reason = node.path("reason").asText("").strip();
                    String explanation = node.path("explanation").asText("").strip();
                    Double confidence = node.path("confidence").isNumber()
                            ? Math.max(0.0, Math.min(1.0, node.path("confidence").asDouble())) : null;
                    String riskLevel = node.path("riskLevel").asText("").trim().toUpperCase(Locale.ROOT);
                    if (!Set.of("LOW", "MEDIUM", "HIGH").contains(riskLevel)) riskLevel = null;

                    List<String> refs = stringList(node.path("evidenceRefs"), 10);
                    List<String> actionItems = stringList(node.path("actionItems"), 8);
                    List<String> experiments = stringList(node.path("experiments"), 6);

                    List<DiagnosticAgents.Finding> stageFindings = new ArrayList<>();
                    JsonNode stages = node.path("stageDiagnoses");
                    if (stages.isArray()) {
                        for (JsonNode s : stages) {
                            String stage = s.path("stage").asText("").trim().toUpperCase(Locale.ROOT);
                            String status = s.path("status").asText("AI_DIAGNOSED").trim().toUpperCase(Locale.ROOT);
                            String evidence = s.path("evidence").asText("").strip();
                            if (!stage.isEmpty() && !evidence.isEmpty() && stageFindings.size() < 6) {
                                stageFindings.add(new DiagnosticAgents.Finding(stage, status, evidence, null));
                            }
                        }
                    }

                    return new ParsedStrategy(true,
                            VALID_ACTIONS.contains(action) ? action : null,
                            pct,
                            reason.isEmpty() ? proposal.reason() : reason,
                            explanation.isEmpty() ? text : explanation,
                            confidence, riskLevel, refs, stageFindings, actionItems, experiments, candidate);
                }
            } catch (Exception ignored) {
                // Model answered in prose (e.g. simple models or test mocks): keep prose cleanly.
            }
        }
        return new ParsedStrategy(false, null, null, proposal.reason(), text,
                null, null, List.of(), List.of(), List.of(), List.of(), null);
    }

    private static List<String> stringList(JsonNode array, int max) {
        List<String> result = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                String val = item.isTextual() ? item.asText("").strip() : item.toString();
                if (!val.isEmpty() && result.size() < max) result.add(val);
            }
        }
        return List.copyOf(result);
    }

    private String strategySystemPrompt(int limit) {
        return """
                你是 AFS 商业级广告投放优化智能体（基于 LangGraph4j + LangChain4j），运行在 AFS CMS 投放管理系统中。
                你拥有只读业务工具：dailyReport（逐日趋势）、countryBreakdown（国家/受众盈亏）、creativeAndLandingContext（文章/素材/文案/反馈）、ruleContext（规则上限）、peerBenchmarks（同站点基准）、historicalRuns（历史审批记忆）、simulateBudget（预算试算）、scenarioCatalog（场景目录）。

                工作准则：
                1. 只能使用工具返回的真实数据和输入指标，严禁编造数字、国家或投放结果。
                2. 预算建议必须经人工审批，不得宣称已直接修改线上预算。
                3. 在信息不足时先调用必要工具收集证据（例如逐日趋势、国家拆分、素材/规则/历史审批），再给出最终策略。
                4. 最终回复必须是**单个合法 JSON 对象**（不要输出 JSON 以外的多余内容），字段如下：
                {
                  "action": "HOLD|REDUCE|INCREASE",
                  "changePercent": 0,
                  "confidence": 0.85,
                  "riskLevel": "LOW|MEDIUM|HIGH",
                  "reason": "一句话核心决策依据",
                  "evidenceRefs": ["引用的关键真实数据/日期/国家/规则要点"],
                  "stageDiagnoses": [
                    {"stage": "MONITOR", "status": "READY|WARNING|INSUFFICIENT", "evidence": "基于逐日趋势的监控诊断"},
                    {"stage": "AUDIENCE", "status": "OPPORTUNITY|BLEEDING|INSUFFICIENT", "evidence": "基于国家/受众维度的诊断"},
                    {"stage": "CREATIVE", "status": "OPPORTUNITY|FATIGUE|INSUFFICIENT", "evidence": "基于素材与落地页的创意诊断"}
                  ],
                  "actionItems": ["按优先级排序的可执行动作清单（3-5条）"],
                  "experiments": ["创意 A/B 实验方案（含变量、对照与验收指标）", "受众/国家分层实验方案"],
                  "explanation": "给投放总监和运营人员看的专业中文 Markdown 分析报告（含：核心指标与趋势诊断、预算调整理由与预期影响、国家/受众与创意优化抓手、风险与回滚止损条件）"
                }
                约束：changePercent 为整数；INCREASE 用正数，REDUCE 用负数，HOLD 为 0；绝对值不得超过 %d。
                """.formatted(limit);
    }

    private String buildUserPayload(RunSession session, String intakeSummary, String researchDigest, String extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("site", session.input.site());
        payload.put("channel", session.input.channel());
        payload.put("campaignId", session.input.campaignId());
        payload.put("currency", session.input.currency());
        payload.put("dateRange", session.input.start() + " 至 " + session.input.end());
        payload.put("spend", session.input.spend());
        payload.put("revenue", session.input.revenue());
        payload.put("impressions", session.input.impressions());
        payload.put("clicks", session.input.clicks());
        payload.put("conversions", session.input.conversions());
        payload.put("targetRoas", session.input.targetRoas());
        payload.put("metrics", Map.of(
                "ctr", String.valueOf(session.metrics.ctr()),
                "cvr", String.valueOf(session.metrics.cvr()),
                "cpa", String.valueOf(session.metrics.cpa()),
                "roas", String.valueOf(session.metrics.roas())));
        payload.put("ruleProposal", Map.of(
                "action", session.ruleProposal.action().name(),
                "changePercent", session.ruleProposal.changePercent(),
                "reason", session.ruleProposal.reason()));
        payload.put("budgetChangeLimitPercent", session.tools.effectiveLimit());
        payload.put("baselineFindings", session.baselineFindings);
        payload.put("intakeSummary", intakeSummary);
        if (researchDigest != null && !researchDigest.isBlank()) {
            payload.put("researchSpecialistDigest", researchDigest);
        }
        String serialized;
        try {
            serialized = json.writeValueAsString(payload);
        } catch (Exception ex) {
            serialized = payload.toString();
        }
        StringBuilder sb = new StringBuilder("以下是一次投放分析的输入、定量基线与规则约束，请按约定完成工具核验并输出最终 JSON：\n");
        sb.append(serialized);
        if (extra != null && !extra.isBlank()) {
            sb.append("\n\n补充要求：").append(extra);
        }
        return sb.toString();
    }

    private static String summarizeDailyTrend(CmsReportSource.Campaign report) {
        if (report == null || report.daily() == null || report.daily().isEmpty()) {
            return "仅汇总样本（无逐日明细）";
        }
        int n = report.daily().size();
        long active = report.daily().stream().filter(d -> d.spend().signum() > 0).count();
        if (n < 2) {
            return "日度样本 " + active + " 天";
        }
        int half = n / 2;
        BigDecimal earlySpend = BigDecimal.ZERO, earlyRev = BigDecimal.ZERO;
        BigDecimal lateSpend = BigDecimal.ZERO, lateRev = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            var d = report.daily().get(i);
            if (i < half) {
                earlySpend = earlySpend.add(d.spend());
                earlyRev = earlyRev.add(d.revenue());
            } else {
                lateSpend = lateSpend.add(d.spend());
                lateRev = lateRev.add(d.revenue());
            }
        }
        BigDecimal earlyRoas = earlySpend.signum() > 0 ? earlyRev.divide(earlySpend, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal lateRoas = lateSpend.signum() > 0 ? lateRev.divide(lateSpend, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        String trend = lateRoas.compareTo(earlyRoas) > 0 ? "ROAS上升" : (lateRoas.compareTo(earlyRoas) < 0 ? "ROAS下滑" : "ROAS持平");
        return "日度样本 " + active + " 天（前半段 " + earlyRoas + "× → 后半段 " + lateRoas + "×，" + trend + "）";
    }

    private RunSession requireSession(OptimizationGraphState state) {
        RunSession session = activeSessions.get(state.runId());
        if (session == null) {
            throw new IllegalStateException("Missing active LangGraph execution session for run " + state.runId());
        }
        return session;
    }

    private static String normalizeMode(String mode) {
        if (mode == null) return "STANDARD";
        return switch (mode.trim().toUpperCase(Locale.ROOT)) {
            case "DEEP" -> "DEEP";
            case "RULES_ONLY" -> "RULES_ONLY";
            default -> "STANDARD";
        };
    }

    private static String stripFences(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstBreak = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstBreak < 0 || lastFence <= firstBreak) return trimmed;
        return trimmed.substring(firstBreak + 1, lastFence).strip();
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static String fmt(BigDecimal value) {
        return value == null ? "—" : value.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private static String abbreviate(String text, int limit) {
        if (text == null) return "";
        String compact = text.replaceAll("\\s+", " ");
        return compact.length() <= limit ? compact : compact.substring(0, limit - 3) + "...";
    }

    private static long millis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static final class RunSession {
        final String runId;
        final String mode;
        final OptimizationWorkflow.Input input;
        final OptimizationWorkflow.Metrics metrics;
        final OptimizationWorkflow.Proposal ruleProposal;
        final List<DiagnosticAgents.Finding> baselineFindings;
        final CmsReportSource.Campaign report;
        final LegacyContext.Snapshot context;
        final AdvisoryTools tools;
        final LC4jToolService toolNode;
        final List<ChatMessage> researchMessages = new ArrayList<>();
        final List<ChatMessage> plannerMessages = new ArrayList<>();
        AiModelGateway.ResolvedModel resolvedModel;
        AiMessage lastAiMessage;
        boolean inResearchPhase;
        boolean disableToolsForFallback;
        ParsedStrategy parsedStrategy;

        RunSession(String runId, String mode, OptimizationWorkflow.Input input,
                   OptimizationWorkflow.Metrics metrics, OptimizationWorkflow.Proposal ruleProposal,
                   List<DiagnosticAgents.Finding> baselineFindings, CmsReportSource.Campaign report,
                   LegacyContext.Snapshot context, AdvisoryTools tools, LC4jToolService toolNode) {
            this.runId = runId;
            this.mode = mode;
            this.input = input;
            this.metrics = metrics;
            this.ruleProposal = ruleProposal;
            this.baselineFindings = baselineFindings;
            this.report = report;
            this.context = context;
            this.tools = tools;
            this.toolNode = toolNode;
        }
    }

    private record ParsedStrategy(boolean structuredJsonFound, String action, Integer changePercent,
                                  String reason, String explanation, Double confidence, String riskLevel,
                                  List<String> evidenceRefs, List<DiagnosticAgents.Finding> stageFindings,
                                  List<String> actionItems, List<String> experiments, String rawJson) {}

    record GraphOutcome(String source, String model, String text, String action, Integer changePercent,
                        String reason, List<String> evidenceRefs, long durationMs, int toolCalls,
                        List<String> tools, String note, Double confidence, String riskLevel,
                        int inputTokens, int outputTokens, int revisions, List<String> graphNodes,
                        List<DiagnosticAgents.Finding> stageFindings, List<String> actionItems,
                        List<String> experiments, String mode) {
        static GraphOutcome failed(String source, String model, String reason, long durationMs) {
            return new GraphOutcome(source, model, null, null, null, null, List.of(), durationMs,
                    0, List.of(), reason, null, null, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), "STANDARD");
        }

        boolean decided() {
            return action != null;
        }
    }
}
