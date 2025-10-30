package com.afs.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.PreDestroy;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/cms/agent")
class OptimizationController {
    private static final Logger log = LoggerFactory.getLogger(OptimizationController.class);
    private static final String SELECT = """
            SELECT id, created_at, plan, state, reviewer, reviewed_at, review_note, simulation,
                   proposal_revision, proposal_hash, created_by, execution
            FROM optimization_run
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OptimizationWorkflow workflow;
    private final CmsReportSource reports;
    private final CmsCampaignDataService cmsData;
    private final AiModelGateway gateway;
    private final AdOptimizationGraph optimizationGraph;
    private final AdOpsCopilotGraph copilotGraph;
    private final JdbcCheckpointSaver checkpointSaver;
    private final AgentTelemetryService telemetry;
    private final AgentSafetyPolicyService safetyPolicy;
    private final OfflineReplayEvaluator replayEvaluator;
    private final FiveAgentOrchestrator fiveAgentOrchestrator;

    /** Dedicated pool keeps LLM graph executions from blocking console HTTP threads. */
    private final ExecutorService advisoryPool = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "langgraph-worker");
        thread.setDaemon(true);
        return thread;
    });

    OptimizationController(JdbcTemplate jdbc, ObjectMapper json, OptimizationWorkflow workflow,
                           CmsReportSource reports, CmsCampaignDataService cmsData,
                           AiModelGateway gateway, AdOptimizationGraph optimizationGraph,
                           AdOpsCopilotGraph copilotGraph, JdbcCheckpointSaver checkpointSaver,
                           AgentTelemetryService telemetry, AgentSafetyPolicyService safetyPolicy,
                           OfflineReplayEvaluator replayEvaluator, FiveAgentOrchestrator fiveAgentOrchestrator) {
        this.jdbc = jdbc;
        this.json = json;
        this.workflow = workflow;
        this.reports = reports;
        this.cmsData = cmsData;
        this.gateway = gateway;
        this.optimizationGraph = optimizationGraph;
        this.copilotGraph = copilotGraph;
        this.checkpointSaver = checkpointSaver;
        this.telemetry = telemetry;
        this.safetyPolicy = safetyPolicy;
        this.replayEvaluator = replayEvaluator;
        this.fiveAgentOrchestrator = fiveAgentOrchestrator;
    }

    @PreDestroy
    void shutdown() {
        advisoryPool.shutdownNow();
    }

    /** AI engine health, LangGraph4j/LangChain4j metadata, and live Mermaid graph topologies. */
    @GetMapping("/status")
    Map<String, Object> status() {
        var result = new LinkedHashMap<String, Object>(gateway.status());
        result.put("localWorkspace", cmsData.isLocalWorkspace());
        result.put("tools", AdvisoryTools.toolNames());
        result.put("graphs", Map.of(
                "optimization", optimizationGraph.mermaidTopology(),
                "copilot", copilotGraph.mermaidTopology()));
        return result;
    }

    /** Lists real CMS campaigns (or simulation scenarios) for 1-click analysis. */
    @GetMapping("/campaigns")
    List<CmsCampaignDataService.CampaignSummary> campaigns(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "40") int limit) {
        return cmsData.listCampaigns(query, platform, limit);
    }

    /** Autonomous portfolio anomaly scan across all active campaigns. */
    @PostMapping(path = "/scan", consumes = "application/json")
    Map<String, Object> scan(@RequestBody(required = false) ScanRequest request) {
        BigDecimal targetRoas = request != null && request.targetRoas() != null
                ? request.targetRoas() : new BigDecimal("1.25");
        BigDecimal minSpend = request != null && request.minimumSpend() != null
                ? request.minimumSpend() : new BigDecimal("30.00");
        return cmsData.scanAnomalies(targetRoas, minSpend);
    }

    /** Commercial observability & token usage metrics. */
    @GetMapping("/telemetry")
    Map<String, Object> telemetrySummary() {
        var data = new LinkedHashMap<String, Object>(telemetry.summary());
        data.put("status", gateway.status());
        data.put("safety", safetyPolicy.summary());
        data.put("graphs", Map.of(
                "optimization", optimizationGraph.mermaidTopology(),
                "copilot", copilotGraph.mermaidTopology()));
        return data;
    }

    /** Three-tier safety policy (SHADOW / COPILOT / AUTONOMOUS), Kill Switch, and daily caps. */
    @GetMapping("/safety")
    Map<String, Object> getSafetyPolicy() {
        return safetyPolicy.summary();
    }

    @PostMapping(path = "/safety", consumes = "application/json")
    Map<String, Object> updateSafetyPolicy(@RequestBody SafetyUpdateRequest request, Principal principal) {
        safetyPolicy.update(
                request.safetyMode(),
                request.dailyBudgetDeltaCap(),
                request.maxSingleChangePct(),
                request.maxLlmCallsPerDay(),
                actor(principal));
        return safetyPolicy.summary();
    }

    @PostMapping(path = "/safety/kill-switch", consumes = "application/json")
    Map<String, Object> toggleKillSwitch(@RequestBody KillSwitchRequest request, Principal principal) {
        safetyPolicy.setKillSwitch(Boolean.TRUE.equals(request.active()), request.reason(), actor(principal));
        return safetyPolicy.summary();
    }

    /** Offline counterfactual replay & paired uplift evaluation over real historical campaign data. */
    @PostMapping(path = "/replay", consumes = "application/json")
    Map<String, Object> runOfflineReplay(@RequestBody(required = false) ReplayRequest request) {
        BigDecimal targetRoas = request != null && request.targetRoas() != null
                ? request.targetRoas() : new BigDecimal("1.25");
        BigDecimal minSpend = request != null && request.minimumSpend() != null
                ? request.minimumSpend() : new BigDecimal("30.00");
        int minClicks = request != null && request.minimumClicks() != null ? request.minimumClicks() : 30;
        int maxChange = request != null && request.maxChangePercent() != null ? request.maxChangePercent() : 10;
        int limit = request != null && request.limit() != null ? request.limit() : 40;
        return replayEvaluator.evaluate(targetRoas, minSpend, minClicks, maxChange, limit);
    }

    /** 5-Agent closed-loop orchestration: Monitor -> Audience -> Creative -> Bidding -> Optimize. */
    @PostMapping(path = "/five-agent/run", consumes = "application/json")
    Map<String, Object> runFiveAgentLoop(@RequestBody(required = false) FiveAgentRequest request) {
        String cid = request == null ? null : request.campaignId();
        BigDecimal targetRoas = request != null && request.targetRoas() != null
                ? request.targetRoas() : new BigDecimal("1.25");
        BigDecimal targetCpa = request == null ? null : request.targetCpa();
        boolean useLlm = request == null || !Boolean.FALSE.equals(request.useLlmForCreative());
        int maxChange = request != null && request.maxChangePercent() != null ? request.maxChangePercent() : 15;
        int iteration = request != null && request.iteration() != null ? request.iteration() : 1;
        return fiveAgentOrchestrator.runClosedLoop(cid, targetRoas, targetCpa, useLlm, maxChange, iteration);
    }

    /** Standalone convex budget reallocation solver across active campaigns. */
    @PostMapping(path = "/five-agent/allocate", consumes = "application/json")
    Map<String, Object> solveConvexAllocation(@RequestBody(required = false) AllocationRequest request) {
        BigDecimal total = request == null ? null : request.totalBudget();
        int maxChange = request != null && request.maxChangePercent() != null ? request.maxChangePercent() : 15;
        int limit = request != null && request.limit() != null ? request.limit() : 10;
        return fiveAgentOrchestrator.solvePortfolioAllocation(total, maxChange, limit);
    }

    @PostMapping(path = "/runs", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Run create(@Valid @RequestBody OptimizationWorkflow.Input input, Principal principal)
            throws JsonProcessingException {
        return create(input, principal, true);
    }

    Run create(OptimizationWorkflow.Input input, Principal principal, boolean advisory) throws JsonProcessingException {
        // Enrich with real local CMS daily/country/creative evidence when available instead of passing null!
        CmsReportSource.Campaign evidence = advisory
                ? cmsData.loadCampaignEvidence(input.campaignId(), input.site(), input.channel(),
                        input.start(), input.end())
                : null;
        OptimizationWorkflow.Plan draft = advisory
                ? workflow.draft(input, evidence, "STANDARD")
                : workflow.rulesOnly(input, null);
        Run run = save(draft, null, principal);
        scheduleAfterCommit(draft, run.id(), evidence, "STANDARD");
        return run;
    }

    /** One-click LangGraph4j optimization analysis directly from a real CMS campaign ID. */
    @PostMapping(path = "/runs/campaigns/{campaignId}", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Run createForCampaign(@PathVariable String campaignId,
                                 @RequestBody(required = false) CampaignAnalysisRequest request,
                                 Principal principal) throws JsonProcessingException {
        String mode = request != null && "DEEP".equalsIgnoreCase(request.mode()) ? "DEEP" : "STANDARD";
        CmsCampaignDataService.CampaignPackage pkg = cmsData.buildPackageForCampaign(
                campaignId,
                request == null ? null : request.targetRoas(),
                request == null ? null : request.minimumSpend(),
                request == null ? null : request.minimumClicks(),
                request == null ? null : request.maxChangePercent());
        OptimizationWorkflow.Plan draft = workflow.draft(pkg.input(), pkg.evidence(), mode);
        Run run = save(draft, null, principal);
        scheduleAfterCommit(draft, run.id(), pkg.evidence(), mode);
        return run;
    }

    /** Programmatic helper used by CopilotTools to launch an optimization run safely from chat. */
    @Transactional
    Map<String, Object> launchFromCopilot(String campaignId, String mode, Principal actor) {
        try {
            Run run = createForCampaign(campaignId, new CampaignAnalysisRequest(mode, null, null, null, null), actor);
            var res = new LinkedHashMap<String, Object>();
            res.put("created", true);
            res.put("runId", run.id().toString());
            res.put("campaignId", campaignId);
            res.put("mode", mode);
            res.put("state", run.state());
            res.put("analysisStatus", run.plan().at("/explanation/source").asText(""));
            res.put("note", "已创建正式的 LangGraph 优化分析与审批工单，请前往智能体工作台查看并审批");
            return res;
        } catch (Exception ex) {
            return Map.of("created", false, "reason", ex.getMessage() == null ? "创建分析失败" : ex.getMessage());
        }
    }

    /** Upgrades any existing RULES-only / failed / pending run with a fresh LangGraph4j AI analysis. */
    @PostMapping(path = "/runs/{id}/reanalyze", consumes = "application/json")
    @Transactional
    public Run reanalyze(@PathVariable UUID id,
                         @RequestBody(required = false) ReanalyzeRequest request,
                         Principal principal) throws JsonProcessingException {
        Run existing = get(id);
        if (!"PENDING".equals(existing.state())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅「待审批」状态的记录支持重新运行 AI 分析");
        }
        if (!gateway.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "尚未配置 AI 聊天模型，请先到「渠道密钥 → AI 平台」配置 Base URL、API Key 与聊天模型");
        }
        if (safetyPolicy.isLlmBudgetExhausted()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "今日 AI 模型调用次数已达安全上限，请前往「安全策略」调高每日限额或明日再试");
        }
        String mode = request != null && "DEEP".equalsIgnoreCase(request.mode()) ? "DEEP" : "STANDARD";
        OptimizationWorkflow.Input input = json.treeToValue(existing.plan().path("input"), OptimizationWorkflow.Input.class);
        CmsReportSource.Campaign evidence = cmsData.loadCampaignEvidence(
                input.campaignId(), input.site(), input.channel(), input.start(), input.end());
        if (input.impressions() == 0L && input.spend() != null && input.spend().signum() > 0 && cmsData.isLocalWorkspace()) {
            try {
                var refreshed = cmsData.buildPackageForCampaign(input.campaignId(), input.targetRoas(),
                        input.minimumSpend(), input.minimumClicks(), input.maxChangePercent());
                input = refreshed.input();
                evidence = refreshed.evidence();
            } catch (Exception ignored) {
                // Keep original input if campaign is no longer in ad_resource.
            }
        }
        OptimizationWorkflow.Plan draft = workflow.draft(input, evidence, mode);
        String nextHash = SignalScorecard.proposalFingerprint(draft.input(), draft.proposal());
        jdbc.update("""
                UPDATE optimization_run
                SET plan = ?, proposal_revision = proposal_revision + 1, proposal_hash = ?
                WHERE id = ?
                """, json.writeValueAsString(draft), nextHash, id.toString());
        recordEvent(id, "REANALYZE_" + mode, principal);
        scheduleAfterCommit(draft, id, evidence, mode);
        return get(id);
    }

    @GetMapping("/runs/scenarios")
    List<CmsReportSource.Scenario> scenarios() {
        return reports.scenarios();
    }

    @PostMapping(path = "/runs/scenarios/{scenario}", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Run createScenario(@PathVariable String scenario, @RequestHeader("Idempotency-Key") UUID key,
                              Principal principal) throws JsonProcessingException {
        Run existing = findByKey(key);
        if (existing != null) {
            return verifyScenario(existing, scenario);
        }
        CmsReportSource.Campaign report = reports.get(scenario);
        try {
            OptimizationWorkflow.Plan draft = workflow.draft(report.toInput(), report, "STANDARD");
            Run run = save(draft, key, principal);
            scheduleAfterCommit(draft, run.id(), report, "STANDARD");
            return run;
        } catch (DuplicateKeyException ex) {
            Run concurrent = findByKey(key);
            if (concurrent == null) {
                throw ex;
            }
            return verifyScenario(concurrent, scenario);
        }
    }

    /** Returns node-by-node LangGraph execution trace and persisted JDBC checkpoints for a run. */
    @GetMapping("/runs/{id}/trace")
    Map<String, Object> trace(@PathVariable UUID id) {
        get(id);
        var result = new LinkedHashMap<String, Object>();
        result.put("runId", id.toString());
        result.put("nodes", telemetry.tracesForRun(id.toString()));
        result.put("checkpoints", checkpointSaver.inspectThread("run-" + id));
        result.put("mermaid", optimizationGraph.mermaidTopology());
        return result;
    }

    /** Live Server-Sent Events stream of LangGraph4j node transitions for an active run. */
    @GetMapping(path = "/runs/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable UUID id) {
        Run run = get(id);
        SseEmitter emitter = telemetry.subscribeRun(id);
        String source = run.plan().at("/explanation/source").asText("");
        if (!ExperimentAdvisor.PENDING.equals(source)) {
            telemetry.completeRunStream(id, source, "COMPLETED");
        }
        return emitter;
    }

    private void scheduleAfterCommit(OptimizationWorkflow.Plan draft, UUID id,
                                     CmsReportSource.Campaign report, String mode) {
        if (!ExperimentAdvisor.PENDING.equals(draft.explanation().source())) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue(draft, id, report, mode);
                }
            });
        } else {
            enqueue(draft, id, report, mode);
        }
    }

    private void enqueue(OptimizationWorkflow.Plan draft, UUID id, CmsReportSource.Campaign report, String mode) {
        advisoryPool.execute(() -> complete(id, draft, report, mode));
    }

    private void complete(UUID id, OptimizationWorkflow.Plan draft, CmsReportSource.Campaign report, String mode) {
        OptimizationWorkflow.Plan completed;
        try {
            completed = workflow.complete(id, mode, draft, report);
        } catch (Exception failure) {
            log.error("LangGraph AI 分析执行失败 run={}", id, failure);
            telemetry.completeRunStream(id, ExperimentAdvisor.UNAVAILABLE, "FAILED");
            return;
        }
        try {
            String nextHash = SignalScorecard.proposalFingerprint(completed.input(), completed.proposal());
            jdbc.update("""
                    UPDATE optimization_run
                    SET plan = ?,
                        proposal_revision = CASE WHEN COALESCE(proposal_hash, '') <> ? THEN proposal_revision + 1 ELSE proposal_revision END,
                        proposal_hash = ?
                    WHERE id = ?
                    """, json.writeValueAsString(completed), nextHash, nextHash, id.toString());
            String source = completed.explanation().source();
            boolean failed = ExperimentAdvisor.UNAVAILABLE.equals(source) || ExperimentAdvisor.RULES.equals(source);
            recordEvent(id, failed ? "ANALYSIS_FAILED" : "ANALYSIS_READY", "langgraph-agent");
            telemetry.completeRunStream(id, source, failed ? "DEGRADED" : "READY");
        } catch (Exception failure) {
            log.error("LangGraph AI 分析结果写入失败 run={}", id, failure);
            telemetry.completeRunStream(id, ExperimentAdvisor.UNAVAILABLE, "ERROR");
        }
    }

    private Run save(OptimizationWorkflow.Plan result, UUID key, Principal principal) throws JsonProcessingException {
        UUID id = UUID.randomUUID();
        String plan = json.writeValueAsString(result);
        String hash = SignalScorecard.proposalFingerprint(result.input(), result.proposal());
        jdbc.update("""
                INSERT INTO optimization_run (id, plan, request_key, proposal_revision, proposal_hash, created_by)
                VALUES (?, ?, ?, 1, ?, ?)
                """, id.toString(), plan, key == null ? null : key.toString(), hash, actor(principal));
        recordEvent(id, "PENDING", principal);
        return get(id);
    }

    private Run findByKey(UUID key) {
        return jdbc.query(SELECT + " WHERE request_key = ?", this::map, key.toString())
                .stream().findFirst().orElse(null);
    }

    private Run verifyScenario(Run run, String scenario) {
        if (!run.plan().path("evidence").path("scenario").asText().equals(scenario)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to a different scenario");
        }
        return run;
    }

    @GetMapping("/runs")
    List<Run> recent() {
        return jdbc.query(SELECT + " ORDER BY created_at DESC, id DESC LIMIT 30", this::map);
    }

    @GetMapping("/runs/{id}")
    Run get(@PathVariable UUID id) {
        return jdbc.query(SELECT + " WHERE id = ?", this::map, id.toString()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping(path = "/runs/{id}/review", consumes = "application/json")
    @Transactional
    public Run review(@PathVariable UUID id, @Valid @RequestBody Review review, Principal principal) {
        Run existing = get(id);
        if (!"PENDING".equals(existing.state())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run has already been reviewed");
        }
        if (review.expectedRevision() != null && review.expectedRevision() != existing.proposalRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "提案版本已更新（当前版本 v" + existing.proposalRevision() + "），请刷新核对最新方案后再审批");
        }
        if (review.expectedHash() != null && !review.expectedHash().isBlank()
                && existing.proposalHash() != null && !existing.proposalHash().isBlank()
                && !review.expectedHash().equals(existing.proposalHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "提案指纹不匹配（方案内容已变更），审批已拒绝以防止盲批");
        }
        int updated = jdbc.update("""
                UPDATE optimization_run SET state = ?, reviewer = ?, reviewed_at = CURRENT_TIMESTAMP, review_note = ?
                WHERE id = ? AND state = 'PENDING' AND proposal_revision = ?
                """, review.decision().name(), actor(principal), review.note(), id.toString(), existing.proposalRevision());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run has already been reviewed or updated concurrently");
        }
        recordEvent(id, review.decision().name(), principal);
        return get(id);
    }

    /** Batch approve or reject multiple PENDING optimization runs atomically with full append-only audit trail. */
    @PostMapping(path = "/runs/batch-review", consumes = "application/json")
    @Transactional
    public Map<String, Object> batchReview(@Valid @RequestBody BatchReviewRequest request, Principal principal) {
        int succeeded = 0;
        int skipped = 0;
        List<String> processedIds = new java.util.ArrayList<>();
        String reviewerName = actor(principal);
        for (UUID runId : request.runIds()) {
            int updated = jdbc.update("""
                    UPDATE optimization_run
                    SET state = ?, reviewer = ?, reviewed_at = CURRENT_TIMESTAMP, review_note = ?
                    WHERE id = ? AND state = 'PENDING'
                    """, request.decision().name(), reviewerName, request.note(), runId.toString());
            if (updated == 1) {
                recordEvent(runId, request.decision().name(), principal);
                processedIds.add(runId.toString());
                succeeded++;
            } else {
                skipped++;
            }
        }
        return Map.of(
                "decision", request.decision().name(),
                "succeeded", succeeded,
                "skipped", skipped,
                "processedIds", processedIds);
    }

    @PostMapping(path = "/runs/{id}/simulate", consumes = "application/json")
    @Transactional
    public Run simulate(@PathVariable UUID id, @Valid @RequestBody SimulationRequest request, Principal principal)
            throws JsonProcessingException {
        Run run = get(id);
        int percent = run.plan().path("proposal").path("changePercent").intValue();
        BigDecimal budget = request.currentBudget();
        JsonNode observed = run.plan().path("evidence").path("observedBudget");
        if (observed.isNumber() && observed.decimalValue().compareTo(budget) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Budget differs from the reviewed report");
        }
        BigDecimal projected = budget.multiply(BigDecimal.valueOf(100L + percent))
                .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        var simulation = new Simulation(budget, projected, run.plan().path("input").path("currency").asText(),
                actor(principal), OffsetDateTime.now());
        int updated = jdbc.update("""
                UPDATE optimization_run SET state = 'SIMULATED', simulation = ?
                WHERE id = ? AND state = 'APPROVED'
                """, json.writeValueAsString(simulation), id.toString());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Simulation requires an approved run");
        }
        recordEvent(id, "SIMULATED", principal);
        return get(id);
    }

    /**
     * Transactional, idempotent budget execution gated by Approval State, Proposal Fingerprint,
     * Kill Switch, 3-Tier Safety Mode, Learning-Phase Cap, and Daily Budget Delta Cap.
     */
    @PostMapping(path = "/runs/{id}/execute", consumes = "application/json")
    @Transactional
    public Run execute(@PathVariable UUID id,
                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                       @Valid @RequestBody ExecutionRequest request,
                       Principal principal) throws JsonProcessingException {
        Run run = get(id);
        String cleanKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? null : idempotencyKey.trim();
        String requestHash = SignalScorecard.sha256Hex(id + "|" + request.currentBudget().setScale(2, RoundingMode.HALF_UP)
                + "|" + (request.expectedHash() == null ? "" : request.expectedHash())
                + "|" + (request.expectedRevision() == null ? "" : request.expectedRevision()));

        // 1. Check idempotency ledger first (handles double-clicks & network retries safely)
        if (cleanKey != null) {
            var existingLedger = jdbc.query("""
                    SELECT run_id, request_hash, status FROM optimization_ledger WHERE idempotency_key = ?
                    """, (rs, i) -> Map.of(
                    "runId", rs.getString("run_id"),
                    "requestHash", rs.getString("request_hash"),
                    "status", rs.getString("status")
            ), cleanKey).stream().findFirst().orElse(null);
            if (existingLedger != null) {
                if (!id.toString().equals(existingLedger.get("runId"))
                        || !requestHash.equals(existingLedger.get("requestHash"))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "幂等键冲突：相同 Idempotency-Key 已被用于其他请求参数");
                }
                return get(id);
            }
        }

        // 2. Verify approval state & proposal fingerprint
        if (!"APPROVED".equals(run.state()) && !"SIMULATED".equals(run.state())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "仅「已通过」或「已试算」状态的提案允许执行预算变更（当前状态：" + run.state() + "）");
        }
        if (request.expectedRevision() != null && request.expectedRevision() != run.proposalRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "提案版本不匹配，已拦截执行");
        }
        if (request.expectedHash() != null && !request.expectedHash().isBlank()
                && run.proposalHash() != null && !run.proposalHash().isBlank()
                && !request.expectedHash().equals(run.proposalHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "提案指纹不匹配，已拦截执行");
        }

        BigDecimal beforeBudget = request.currentBudget().setScale(2, RoundingMode.HALF_UP);
        JsonNode observed = run.plan().path("evidence").path("observedBudget");
        if (observed.isNumber() && observed.decimalValue().setScale(2, RoundingMode.HALF_UP).compareTo(beforeBudget) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前预算与审批报表中的预算不一致，请核对后重试");
        }

        int percent = run.plan().path("proposal").path("changePercent").intValue();
        BigDecimal afterBudget = beforeBudget.multiply(BigDecimal.valueOf(100L + percent))
                .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal absDelta = afterBudget.subtract(beforeBudget).abs();

        // 3. Enforce Kill Switch, Safety Mode (SHADOW/COPILOT/AUTONOMOUS), single-change cap, daily delta cap
        safetyPolicy.assertExecutionAllowed(absDelta, Math.abs(percent));

        String currency = run.plan().path("input").path("currency").asText("USD");
        String campaignId = run.plan().path("input").path("campaignId").asText("");
        String ledgerKey = id + ":" + run.proposalRevision() + ":EXECUTE";
        String execActor = actor(principal);
        String activeMode = safetyPolicy.current().safetyMode();

        // 4. Write atomic ledger row (primary key run:revision:EXECUTE prevents crash/concurrency double-exec)
        try {
            jdbc.update("""
                    INSERT INTO optimization_ledger
                    (ledger_key, run_id, proposal_revision, proposal_hash, idempotency_key, request_hash,
                     action_type, before_budget, after_budget, currency, actor, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'EXECUTE', ?, ?, ?, ?, 'COMMITTED')
                    """, ledgerKey, id.toString(), run.proposalRevision(), run.proposalHash(),
                    cleanKey, requestHash, beforeBudget, afterBudget, currency, execActor);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该版本提案已存在执行账本记录，已阻止重复执行");
        }

        // 5. Update local workspace ad-group budget when available (best-effort within local H2)
        boolean localBudgetSynced = applyLocalCampaignBudget(campaignId, afterBudget);

        var execution = new Execution(ledgerKey, beforeBudget, afterBudget, currency, activeMode,
                run.proposalRevision(), run.proposalHash(), localBudgetSynced, execActor, OffsetDateTime.now(), null, null);
        int updated = jdbc.update("""
                UPDATE optimization_run SET state = 'EXECUTED', execution = ?
                WHERE id = ? AND state IN ('APPROVED', 'SIMULATED')
                """, json.writeValueAsString(execution), id.toString());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "并发执行冲突，提案状态已变更");
        }
        recordEvent(id, "EXECUTED", principal);
        return get(id);
    }

    /** One-click Saga rollback: restores beforeBudget and writes a compensating ROLLBACK ledger entry. */
    @PostMapping(path = "/runs/{id}/rollback", consumes = "application/json")
    @Transactional
    public Run rollback(@PathVariable UUID id,
                        @RequestBody(required = false) RollbackRequest request,
                        Principal principal) throws JsonProcessingException {
        Run run = get(id);
        if (!"EXECUTED".equals(run.state()) || run.execution() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅「已执行」状态的提案支持一键回滚");
        }
        BigDecimal beforeBudget = run.execution().path("beforeBudget").decimalValue();
        BigDecimal afterBudget = run.execution().path("afterBudget").decimalValue();
        String currency = run.execution().path("currency").asText("USD");
        String campaignId = run.plan().path("input").path("campaignId").asText("");
        String rollbackActor = actor(principal);
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "管理员手动一键回滚预算" : request.reason().trim();

        String rollbackKey = id + ":" + run.proposalRevision() + ":ROLLBACK";
        String requestHash = SignalScorecard.sha256Hex(rollbackKey + "|" + reason);
        try {
            jdbc.update("""
                    INSERT INTO optimization_ledger
                    (ledger_key, run_id, proposal_revision, proposal_hash, idempotency_key, request_hash,
                     action_type, before_budget, after_budget, currency, actor, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'ROLLBACK', ?, ?, ?, ?, 'COMMITTED')
                    """, rollbackKey, id.toString(), run.proposalRevision(), run.proposalHash(),
                    rollbackKey, requestHash, afterBudget, beforeBudget, currency, rollbackActor);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该提案已回滚，无需重复操作");
        }

        jdbc.update("""
                UPDATE optimization_ledger SET status = 'ROLLED_BACK'
                WHERE run_id = ? AND action_type = 'EXECUTE'
                """, id.toString());

        boolean localRestored = applyLocalCampaignBudget(campaignId, beforeBudget);
        var updatedExec = new Execution(
                run.execution().path("ledgerKey").asText(),
                beforeBudget, afterBudget, currency,
                run.execution().path("safetyMode").asText("COPILOT"),
                run.proposalRevision(), run.proposalHash(), localRestored,
                run.execution().path("executedBy").asText(),
                OffsetDateTime.parse(run.execution().path("executedAt").asText()),
                rollbackActor, OffsetDateTime.now());

        jdbc.update("""
                UPDATE optimization_run SET state = 'ROLLED_BACK', execution = ?
                WHERE id = ? AND state = 'EXECUTED'
                """, json.writeValueAsString(updatedExec), id.toString());
        recordEvent(id, "ROLLED_BACK", principal);
        return get(id);
    }

    @GetMapping("/runs/{id}/ledger")
    List<LedgerEntry> ledger(@PathVariable UUID id) {
        get(id);
        return jdbc.query("""
                SELECT ledger_key, proposal_revision, proposal_hash, idempotency_key, action_type,
                       before_budget, after_budget, currency, actor, status, created_at
                FROM optimization_ledger
                WHERE run_id = ?
                ORDER BY created_at ASC
                """, (rs, idx) -> new LedgerEntry(
                rs.getString("ledger_key"),
                rs.getInt("proposal_revision"),
                rs.getString("proposal_hash"),
                rs.getString("idempotency_key"),
                rs.getString("action_type"),
                rs.getBigDecimal("before_budget"),
                rs.getBigDecimal("after_budget"),
                rs.getString("currency"),
                rs.getString("actor"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)
        ), id.toString());
    }

    private boolean applyLocalCampaignBudget(String campaignId, BigDecimal budget) {
        if (!cmsData.isLocalWorkspace() || campaignId == null || campaignId.isBlank() || budget == null) {
            return false;
        }
        try {
            int updated = jdbc.update(
                    "UPDATE tiktok_ad_group_info SET budget = ? WHERE ad_resource_id = ?",
                    budget.doubleValue(), campaignId);
            return updated > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    @GetMapping("/runs/{id}/audit")
    List<AuditEvent> audit(@PathVariable UUID id) {
        get(id);
        return jdbc.query("""
                SELECT stage, actor, occurred_at FROM optimization_audit WHERE run_id = ? ORDER BY seq
                """, (row, index) -> new AuditEvent(row.getString("stage"), row.getString("actor"),
                row.getObject("occurred_at", OffsetDateTime.class)), id.toString());
    }

    private void recordEvent(UUID id, String stage, Principal principal) {
        recordEvent(id, stage, actor(principal));
    }

    private void recordEvent(UUID id, String stage, String actor) {
        jdbc.update("INSERT INTO optimization_audit (run_id, stage, actor) VALUES (?, ?, ?)",
                id.toString(), stage, actor);
    }

    private String actor(Principal principal) {
        return principal == null ? "admin" : principal.getName();
    }

    private Run map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        try {
            return new Run(UUID.fromString(row.getString("id")),
                    row.getObject("created_at", OffsetDateTime.class), json.readTree(row.getString("plan")),
                    row.getString("state"), row.getString("reviewer"),
                    row.getObject("reviewed_at", OffsetDateTime.class), row.getString("review_note"),
                    readJson(row.getString("simulation")),
                    row.getInt("proposal_revision"),
                    row.getString("proposal_hash"),
                    row.getString("created_by"),
                    readJson(row.getString("execution")));
        } catch (JsonProcessingException ex) {
            throw new java.sql.SQLException("Invalid stored optimization plan", ex);
        }
    }

    private JsonNode readJson(String value) throws JsonProcessingException {
        return value == null ? null : json.readTree(value);
    }

    enum Decision { APPROVED, REJECTED }
    record AuditEvent(String stage, String actor, OffsetDateTime occurredAt) {}
    record Review(@NotNull Decision decision, @NotBlank @Size(max = 500) String note,
                  String expectedHash, Integer expectedRevision) {
        Review(Decision decision, String note) {
            this(decision, note, null, null);
        }
    }
    record SimulationRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal currentBudget) {}
    record Simulation(BigDecimal currentBudget, BigDecimal projectedBudget, String currency,
                      String simulatedBy, OffsetDateTime simulatedAt) {}
    record ExecutionRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal currentBudget,
                            String expectedHash, Integer expectedRevision) {}
    record Execution(String ledgerKey, BigDecimal beforeBudget, BigDecimal afterBudget, String currency,
                     String safetyMode, int proposalRevision, String proposalHash, boolean localBudgetSynced,
                     String executedBy, OffsetDateTime executedAt, String rolledBackBy, OffsetDateTime rolledBackAt) {}
    record RollbackRequest(@Size(max = 240) String reason) {}
    record LedgerEntry(String ledgerKey, int proposalRevision, String proposalHash, String idempotencyKey,
                       String actionType, BigDecimal beforeBudget, BigDecimal afterBudget, String currency,
                       String actor, String status, OffsetDateTime createdAt) {}
    record Run(UUID id, OffsetDateTime createdAt, JsonNode plan, String state,
               String reviewer, OffsetDateTime reviewedAt, String reviewNote, JsonNode simulation,
               int proposalRevision, String proposalHash, String createdBy, JsonNode execution) {}
    record CampaignAnalysisRequest(String mode,
                                   @DecimalMin("0.01") BigDecimal targetRoas,
                                   @DecimalMin("0.01") BigDecimal minimumSpend,
                                   @Min(1) Integer minimumClicks,
                                   @Min(1) @Max(20) Integer maxChangePercent) {}
    record ReanalyzeRequest(String mode) {}
    record ScanRequest(@DecimalMin("0.01") BigDecimal targetRoas,
                       @DecimalMin("0.00") BigDecimal minimumSpend) {}
    record SafetyUpdateRequest(String safetyMode, BigDecimal dailyBudgetDeltaCap,
                               Integer maxSingleChangePct, Integer maxLlmCallsPerDay) {}
    record KillSwitchRequest(Boolean active, @Size(max = 240) String reason) {}
    record ReplayRequest(@DecimalMin("0.01") BigDecimal targetRoas,
                         @DecimalMin("0.00") BigDecimal minimumSpend,
                         @Min(1) Integer minimumClicks,
                         @Min(1) @Max(20) Integer maxChangePercent,
                         @Min(5) @Max(80) Integer limit) {}
    record FiveAgentRequest(String campaignId,
                            @DecimalMin("0.01") BigDecimal targetRoas,
                            @DecimalMin("0.01") BigDecimal targetCpa,
                            Boolean useLlmForCreative,
                            @Min(5) @Max(20) Integer maxChangePercent,
                            @Min(1) @Max(3) Integer iteration) {}
    record AllocationRequest(@DecimalMin("1.00") BigDecimal totalBudget,
                             @Min(5) @Max(25) Integer maxChangePercent,
                             @Min(3) @Max(20) Integer limit) {}
    record BatchReviewRequest(@NotNull @Size(min = 1, max = 30) List<UUID> runIds,
                              @NotNull Decision decision,
                              @NotBlank @Size(max = 500) String note) {}
}
