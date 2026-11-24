package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
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
 * Commercial Multi-Turn Interactive AdOps Copilot compiled on {@code LangGraph4j} + {@code LangChain4j}.
 *
 * <p>Graph Topology:
 * <pre>
 * START -> copilot_agent --(hasToolCalls && round < 6)--> copilot_tools (ToolNode) -> copilot_agent
 *                        --(done)-----------------------> END
 * </pre>
 */
@Component
class AdOpsCopilotGraph {
    static final String GRAPH_NAME = "afs_adops_copilot_graph";
    static final String NODE_AGENT = "copilot_agent";
    static final String NODE_TOOLS = "copilot_tools";

    private static final Logger log = LoggerFactory.getLogger(AdOpsCopilotGraph.class);
    private static final int MAX_TOOL_ROUNDS = 6;
    private static final int MAX_TOOL_OUTPUT = 12000;

    private final AiModelGateway gateway;
    private final ObjectMapper json;
    private final CmsCampaignDataService cmsData;
    private final JdbcCheckpointSaver checkpointSaver;
    private final AgentTelemetryService telemetry;
    private final CompiledGraph<CopilotGraphState> compiledGraph;
    private final String mermaidTopology;
    private final Map<String, CopilotTurnContext> activeTurns = new ConcurrentHashMap<>();

    AdOpsCopilotGraph(AiModelGateway gateway, ObjectMapper json, CmsCampaignDataService cmsData,
                      JdbcCheckpointSaver checkpointSaver, AgentTelemetryService telemetry) throws Exception {
        this.gateway = gateway;
        this.json = json;
        this.cmsData = cmsData;
        this.checkpointSaver = checkpointSaver;
        this.telemetry = telemetry;
        this.compiledGraph = buildGraph();
        this.mermaidTopology = compiledGraph
                .getGraph(GraphRepresentation.Type.MERMAID, "AFS AdOps Copilot LangGraph4j ReAct", false)
                .content();
    }

    String mermaidTopology() {
        return mermaidTopology;
    }

    CopilotReply chatTurn(String sessionId, String boundCampaignId, String boundRunContext,
                          List<HistoryEntry> history, String userPrompt, Principal actor,
                          BiFunction<String, String, Map<String, Object>> optimizationLauncher,
                          BiConsumer<String, Map<String, Object>> eventSink) {
        long started = System.nanoTime();
        BiConsumer<String, Map<String, Object>> emit = eventSink == null ? (e, d) -> {} : eventSink;

        AiModelGateway.ResolvedModel resolved;
        try {
            resolved = gateway.resolve();
        } catch (Exception ex) {
            String msg = "AI 平台配置读取失败：" + AiModelGateway.describe(ex)
                    + "。请前往「渠道密钥 → AI 平台」核对 Base URL、API Key 与聊天模型。";
            emit.accept("answer", Map.of("content", msg));
            return new CopilotReply(msg, "", "CONFIG_ERROR", 0, 0, 0, List.of(), List.of(), millis(started));
        }
        if (resolved == null) {
            String offline = offlineFallbackReply(userPrompt);
            emit.accept("answer", Map.of("content", offline));
            return new CopilotReply(offline, "", "RULES_OFFLINE", 0, 0, 0, List.of(), List.of(), millis(started));
        }

        String turnId = UUID.randomUUID().toString();
        CopilotTools tools = new CopilotTools(json, cmsData, optimizationLauncher, actor);
        LC4jToolService toolNode = LC4jToolService.builder().toolsFromObject(tools).build();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(copilotSystemPrompt(boundCampaignId, boundRunContext)));
        for (HistoryEntry entry : history) {
            if ("user".equalsIgnoreCase(entry.role())) {
                messages.add(UserMessage.from(entry.content()));
            } else if ("assistant".equalsIgnoreCase(entry.role())) {
                messages.add(AiMessage.from(entry.content()));
            }
        }
        messages.add(UserMessage.from(userPrompt));

        CopilotTurnContext turn = new CopilotTurnContext(turnId, sessionId, resolved, toolNode, messages, emit);
        activeTurns.put(turnId, turn);

        try {
            Map<String, Object> initial = new LinkedHashMap<>();
            initial.put(CopilotGraphState.TURN_ID, turnId);
            initial.put(CopilotGraphState.SESSION_ID, sessionId);
            initial.put(CopilotGraphState.PENDING_TOOLS, false);
            initial.put(CopilotGraphState.TOOL_ROUND, 0);

            RunnableConfig config = RunnableConfig.builder()
                    .threadId("chat-" + sessionId + "-" + turnId)
                    .build();

            CopilotGraphState finalState = null;
            for (NodeOutput<CopilotGraphState> output : compiledGraph.stream(initial, config)) {
                finalState = output.state();
            }
            if (finalState == null) {
                throw new IllegalStateException("Copilot LangGraph 未产生最终状态");
            }
            String answer = AiModelGateway.redact(resolved, finalState.finalAnswer());
            emit.accept("answer", Map.of("content", answer, "model", finalState.modelName(),
                    "toolCalls", finalState.totalToolCalls(),
                    "inputTokens", finalState.inputTokens(),
                    "outputTokens", finalState.outputTokens()));
            return new CopilotReply(
                    answer,
                    finalState.modelName(),
                    finalState.source(),
                    finalState.inputTokens(),
                    finalState.outputTokens(),
                    finalState.totalToolCalls(),
                    List.copyOf(finalState.toolTrace()),
                    List.copyOf(finalState.nodeTrace()),
                    millis(started));
        } catch (Exception failure) {
            String reason = AiModelGateway.redact(resolved, AiModelGateway.describe(failure));
            log.warn("Copilot LangGraph 对话失败 session={}：{}", sessionId, reason, failure);
            String msg = "调用 AI 模型失败（" + reason + "）。请检查「渠道密钥 → AI 平台」的连接状态或稍后重试。";
            emit.accept("answer", Map.of("content", msg, "error", reason));
            return new CopilotReply(msg, resolved.chatModel(), ExperimentAdvisor.UNAVAILABLE,
                    0, 0, 0, List.of(), List.of("error"), millis(started));
        } finally {
            activeTurns.remove(turnId);
        }
    }

    private CompiledGraph<CopilotGraphState> buildGraph() throws Exception {
        StateGraph<CopilotGraphState> graph = new StateGraph<>(CopilotGraphState.SCHEMA, CopilotGraphState::new)
                .addNode(NODE_AGENT, node_async(this::agentNode))
                .addNode(NODE_TOOLS, node_async(this::toolsNode))
                .addEdge(START, NODE_AGENT)
                .addConditionalEdges(NODE_AGENT, edge_async(state -> {
                    if (state.hasPendingTools() && state.toolRound() < MAX_TOOL_ROUNDS) {
                        return NODE_TOOLS;
                    }
                    return END;
                }), Map.of(NODE_TOOLS, NODE_TOOLS, END, END))
                .addEdge(NODE_TOOLS, NODE_AGENT);

        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .build();
        CompiledGraph<CopilotGraphState> compiled = graph.compile(compileConfig);
        compiled.setMaxIterations(18);
        return compiled;
    }

    private Map<String, Object> agentNode(CopilotGraphState state) {
        long t0 = System.nanoTime();
        CopilotTurnContext turn = requireTurn(state);
        boolean allowTools = !turn.toolsDisabled && state.toolRound() < MAX_TOOL_ROUNDS;
        if (!allowTools && state.toolRound() >= MAX_TOOL_ROUNDS) {
            turn.messages.add(UserMessage.from(
                    "【系统提示】数据检索已达到本轮上限，现在不再提供额外工具调用。请立即基于前面已经查询到的全部真实数据，"
                            + "直接给出清晰完整的最终结论、数据表格与可执行建议；不要再回复“我将继续查询/拉取”等过渡语。"));
        }
        turn.emit.accept("node", Map.of("node", NODE_AGENT, "status", "RUNNING",
                "detail", allowTools ? "正在分析数据（第 " + (state.toolRound() + 1) + " 轮）..." : "正在汇总最终结论..."));
        try {
            var call = gateway.chat(turn.resolved, turn.messages,
                    allowTools ? turn.toolNode.toolSpecifications() : List.of());
            turn.lastAiMessage = call.message();
            turn.messages.add(call.message());
            boolean hasTools = allowTools && call.message().hasToolExecutionRequests();
            int inTokens = call.inputTokens();
            int outTokens = call.outputTokens();
            String finalText = call.message().text() == null ? "" : call.message().text();

            // Guardrail: if the model emitted only a short transitional "I will fetch more..." stub
            // without actually issuing tool calls, force one immediate final synthesis pass so the user
            // always gets a complete answer on the first turn.
            if (!hasTools && state.toolRound() > 0 && looksLikeUnfinishedTransition(finalText)) {
                log.warn("Copilot 输出疑似未完成过渡句（长度 {}），自动触发一次最终汇总", finalText.length());
                turn.messages.add(UserMessage.from(
                        "请不要只说明查询计划或过渡步骤；请直接基于目前已获取的数据输出完整的最终结论、明细表格和具体建议。"));
                var synth = gateway.chat(turn.resolved, turn.messages, List.of());
                turn.lastAiMessage = synth.message();
                turn.messages.add(synth.message());
                inTokens += synth.inputTokens();
                outTokens += synth.outputTokens();
                if (synth.message().text() != null && !synth.message().text().isBlank()) {
                    finalText = synth.message().text();
                }
            }

            telemetry.recordNode(null, turn.sessionId, GRAPH_NAME, NODE_AGENT, "OK",
                    call.modelName(), inTokens, outTokens, 0, millis(t0),
                    hasTools ? "请求工具 " + call.message().toolExecutionRequests().stream()
                            .map(ToolExecutionRequest::name).toList() : "生成最终回复");
            var out = new LinkedHashMap<String, Object>();
            out.put(CopilotGraphState.PENDING_TOOLS, hasTools);
            out.put(CopilotGraphState.MODEL_NAME, call.modelName());
            out.put(CopilotGraphState.SOURCE, call.source());
            out.put(CopilotGraphState.INPUT_TOKENS, inTokens);
            out.put(CopilotGraphState.OUTPUT_TOKENS, outTokens);
            out.put(CopilotGraphState.NODE_TRACE, NODE_AGENT + ":" + millis(t0) + "ms");
            if (!hasTools) {
                out.put(CopilotGraphState.FINAL_ANSWER, finalText);
            }
            return out;
        } catch (Exception failure) {
            if (allowTools) {
                // Retry once without tool specifications if the upstream OpenAI-compatible endpoint rejects tools.
                turn.toolsDisabled = true;
                String reason = AiModelGateway.redact(turn.resolved, AiModelGateway.describe(failure));
                log.warn("Copilot 工具模式调用失败，降级无工具对话：{}", reason);
                var retry = gateway.chat(turn.resolved, turn.messages, List.of());
                turn.messages.add(retry.message());
                telemetry.recordNode(null, turn.sessionId, GRAPH_NAME, NODE_AGENT, "RECOVERED",
                        retry.modelName(), retry.inputTokens(), retry.outputTokens(), 0, millis(t0),
                        "无工具降级回复");
                var out = new LinkedHashMap<String, Object>();
                out.put(CopilotGraphState.PENDING_TOOLS, false);
                out.put(CopilotGraphState.MODEL_NAME, retry.modelName());
                out.put(CopilotGraphState.SOURCE, retry.source());
                out.put(CopilotGraphState.INPUT_TOKENS, retry.inputTokens());
                out.put(CopilotGraphState.OUTPUT_TOKENS, retry.outputTokens());
                out.put(CopilotGraphState.FINAL_ANSWER, retry.message().text() == null ? "" : retry.message().text());
                out.put(CopilotGraphState.NODE_TRACE, NODE_AGENT + ":recovered:" + millis(t0) + "ms");
                return out;
            }
            throw failure;
        }
    }

    private Map<String, Object> toolsNode(CopilotGraphState state) {
        long t0 = System.nanoTime();
        CopilotTurnContext turn = requireTurn(state);
        AiMessage last = turn.lastAiMessage;
        List<String> executed = new ArrayList<>();
        int count = 0;

        if (last != null && last.hasToolExecutionRequests()) {
            for (ToolExecutionRequest req : last.toolExecutionRequests()) {
                turn.emit.accept("tool_call", Map.of("name", req.name(),
                        "arguments", abbreviate(req.arguments(), 160)));
                ToolExecutionResultMessage resultMsg = executeTool(turn.toolNode, req);
                turn.messages.add(resultMsg);
                count++;
                String traceEntry = req.name() + "(" + abbreviate(req.arguments(), 100) + ")";
                executed.add(traceEntry);
                turn.emit.accept("tool_result", Map.of("name", req.name(),
                        "preview", abbreviate(resultMsg.text(), 220)));
            }
        }
        telemetry.recordNode(null, turn.sessionId, GRAPH_NAME, NODE_TOOLS, "OK",
                turn.resolved.chatModel(), 0, 0, count, millis(t0),
                "执行 " + count + " 次 Copilot 工具：" + String.join(", ", executed));

        var out = new LinkedHashMap<String, Object>();
        out.put(CopilotGraphState.PENDING_TOOLS, false);
        out.put(CopilotGraphState.TOOL_ROUND, state.toolRound() + 1);
        out.put(CopilotGraphState.TOTAL_TOOL_CALLS, count);
        out.put(CopilotGraphState.TOOL_TRACE, executed);
        out.put(CopilotGraphState.NODE_TRACE, NODE_TOOLS + "(" + count + "):" + millis(t0) + "ms");
        return out;
    }

    private ToolExecutionResultMessage executeTool(LC4jToolService toolNode, ToolExecutionRequest req) {
        try {
            var res = toolNode.execute(req);
            if (res.isPresent()) {
                String text = res.get().text();
                if (text != null && text.length() > MAX_TOOL_OUTPUT) {
                    return ToolExecutionResultMessage.from(req, text.substring(0, MAX_TOOL_OUTPUT) + "...(已截断)");
                }
                return res.get();
            }
            return ToolExecutionResultMessage.from(req,
                    "{\"available\":false,\"reason\":\"未知工具：" + req.name() + "\"}");
        } catch (Exception ex) {
            return ToolExecutionResultMessage.from(req,
                    "{\"available\":false,\"reason\":\"" + AiModelGateway.describe(ex).replace('"', '\'') + "\"}");
        }
    }

    private String offlineFallbackReply(String userPrompt) {
        var overview = cmsData.getPortfolioOverview();
        return """
                > **当前尚未配置 AI 聊天模型**，无法进行大模型深度推理与多轮工具编排。
                > 请点击左侧导航 **「渠道密钥 → AI 平台」** 填写 Base URL、API Key 和聊天模型后立即生效（无需重启）。

                以下是从当前工作区实时读取的速览：
                - 纳管活动数：**%s**（已绑定预算护栏规则：**%s**）
                - 累计花费：**$%s** · 累计收入：**$%s** · 累计净利：**$%s** · 综合 ROAS：**%s×**

                配置好 AI 平台后，您可以直接对我提问：「%s」，我将自动查询逐日报表、国家盈亏、创意素材并为您创建审批分析单。
                """.formatted(
                overview.get("campaignCount"),
                overview.get("ruleBoundCount"),
                overview.get("totalSpend"),
                overview.get("totalRevenue"),
                overview.get("totalProfit"),
                overview.get("blendedRoas") == null ? "—" : overview.get("blendedRoas"),
                abbreviate(userPrompt, 80));
    }

    private String copilotSystemPrompt(String boundCampaignId, String boundRunContext) {
        StringBuilder sb = new StringBuilder("""
                你是 AFS 商业广告投放智能副驾驶，实时连接 CMS 广告投放数据。

                你可以调用的实时数据工具：
                - getPortfolioOverview：查看全盘花费、收入、净利、ROAS 与各平台对比
                - scanAnomalies：全盘巡检异常活动（严重亏损、回收偏低、放量机会、未绑规则）
                - listUnboundCampaigns：专门一次性查出全部在投且未绑定任何规则的高消耗活动（排查未绑规则时优先直接调用此工具）
                - searchCampaigns：按关键词、活动 ID 或广告平台检索活动
                - getCampaignDeepDive：深入查看单个活动的逐日趋势、国家维度盈亏、创意素材、文案、规则与站点反馈
                - getRuleCatalog：查看当前已启用的投放规则；getRecentOptimizationRuns：查看近期分析与审批记录
                - simulateBudgetProjection：试算预算调整后的新日预算及日变动额
                - launchOptimizationAnalysis：为指定活动创建正式的投放优化审批单

                商业合规与回复规范：
                1. 所有指标、活动 ID、花费/收入/ROAS 必须来自工具返回的真实数据，严禁杜撰。
                2. 你不会直接修改线上广告平台预算；需要落地调整时，引导用户通过发起分析审批单完成人工审批。
                3. 用清晰专业的中文 Markdown（含结论速览、数据表格与可执行建议）一次给出完整答复。
                4. 严禁只输出“我将继续拉取/分别查询/马上核实”等过程性过渡语就结束回复；查到关键数据后立即汇总最终结论。
                5. 对用户输出时使用自然业务语言，不要暴露内部工具函数名或英文字段名（如不要写 searchCampaigns、ruleBound=false、ruleName 等）。
                """);
        if (boundCampaignId != null && !boundCampaignId.isBlank()) {
            sb.append("\n当前会话已绑定焦点活动 ID：`").append(boundCampaignId).append("`。若用户未指定其他活动，优先围绕该活动分析。");
        }
        if (boundRunContext != null && !boundRunContext.isBlank()) {
            sb.append("\n当前会话关联的优化分析上下文摘要：\n").append(abbreviate(boundRunContext, 2000));
        }
        return sb.toString();
    }

    private static boolean looksLikeUnfinishedTransition(String text) {
        if (text == null || text.isBlank()) return true;
        String trimmed = text.strip();
        // If it already contains a markdown table or rich structured analysis, it's a real answer.
        if (trimmed.length() >= 220 || trimmed.contains("|---") || trimmed.contains("\n- ") || trimmed.contains("\n1.")) {
            return false;
        }
        return trimmed.contains("拉取") || trimmed.contains("分别查") || trimmed.contains("继续查")
                || trimmed.contains("核实") || trimmed.contains("截断") || trimmed.contains("确保不遗漏")
                || trimmed.contains("接下来") || trimmed.contains("稍等") || trimmed.contains("我先");
    }

    private CopilotTurnContext requireTurn(CopilotGraphState state) {
        CopilotTurnContext turn = activeTurns.get(state.turnId());
        if (turn == null) {
            throw new IllegalStateException("Missing active Copilot LangGraph turn " + state.turnId());
        }
        return turn;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ");
        return clean.length() <= max ? clean : clean.substring(0, max - 3) + "...";
    }

    private static long millis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static final class CopilotTurnContext {
        final String turnId;
        final String sessionId;
        final AiModelGateway.ResolvedModel resolved;
        final LC4jToolService toolNode;
        final List<ChatMessage> messages;
        final BiConsumer<String, Map<String, Object>> emit;
        AiMessage lastAiMessage;
        boolean toolsDisabled;

        CopilotTurnContext(String turnId, String sessionId, AiModelGateway.ResolvedModel resolved,
                           LC4jToolService toolNode, List<ChatMessage> messages,
                           BiConsumer<String, Map<String, Object>> emit) {
            this.turnId = turnId;
            this.sessionId = sessionId;
            this.resolved = resolved;
            this.toolNode = toolNode;
            this.messages = messages;
            this.emit = emit;
        }
    }

    record HistoryEntry(String role, String content) {}

    record CopilotReply(String content, String model, String source, int inputTokens, int outputTokens,
                        int toolCalls, List<String> tools, List<String> graphNodes, long durationMs) {}
}
