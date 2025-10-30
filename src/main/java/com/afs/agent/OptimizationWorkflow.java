package com.afs.agent;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class OptimizationWorkflow {
    static final String POLICY = "AFS_LANGGRAPH_POLICY_V3";
    static final String ENGINE = "LangGraph4j 1.5.14 + LangChain4j 1.0.1";

    private final LegacyContext legacy;
    private final ExperimentAdvisor advisor;
    private final DiagnosticAgents diagnostics;

    OptimizationWorkflow(LegacyContext legacy, ExperimentAdvisor advisor, DiagnosticAgents diagnostics) {
        this.legacy = legacy;
        this.advisor = advisor;
        this.diagnostics = diagnostics;
    }

    /** Rules only: never calls the model, so seeding and tests stay deterministic. */
    Plan rulesOnly(Input input, CmsReportSource.Campaign report) {
        return analyze(input, report, false, "RULES_ONLY");
    }

    /** Rules + placeholder explanation; the controller fills the LangGraph analysis in the background. */
    Plan draft(Input input, CmsReportSource.Campaign report) {
        return draft(input, report, "STANDARD");
    }

    Plan draft(Input input, CmsReportSource.Campaign report, String mode) {
        String normalizedMode = "DEEP".equalsIgnoreCase(mode) ? "DEEP" : "STANDARD";
        return analyze(input, report, advisor.configured(), normalizedMode);
    }

    /**
     * Runs the compiled LangGraph4j + LangChain4j optimization graph and applies deterministic
     * commercial safety guardrails on top of the model's recommendation.
     */
    Plan complete(UUID runId, String mode, Plan draft, CmsReportSource.Campaign report) {
        AdOptimizationGraph.GraphOutcome advice = advisor.advise(
                runId == null ? null : runId.toString(),
                mode,
                draft.input(), draft.metrics(), draft.proposal(),
                draft.findings(), report, draft.context());
        Guard guard = applyGuardrail(draft, advice);
        List<DiagnosticAgents.Finding> findings = new ArrayList<>(draft.findings());
        if (advice.stageFindings() != null && !advice.stageFindings().isEmpty()) {
            for (DiagnosticAgents.Finding aiFinding : advice.stageFindings()) {
                // Keep MONITOR's deterministic status intact for audit consistency, enrich evidence.
                if ("MONITOR".equals(aiFinding.stage())) {
                    int idx = indexOfStage(findings, "MONITOR");
                    if (idx >= 0) {
                        var existing = findings.get(idx);
                        findings.set(idx, new DiagnosticAgents.Finding("MONITOR", existing.status(),
                                aiFinding.evidence(), existing.value()));
                    }
                } else if ("AUDIENCE".equals(aiFinding.stage()) || "CREATIVE".equals(aiFinding.stage())) {
                    replaceFinding(findings, aiFinding.stage(), aiFinding);
                }
            }
        }
        replaceFinding(findings, "BIDDING", new DiagnosticAgents.Finding("BIDDING",
                guard.proposal().action().name(), guard.proposal().reason(),
                BigDecimal.valueOf(guard.proposal().changePercent())));
        if (guard.note() != null) {
            int supervisor = indexOfStage(findings, "SUPERVISOR");
            findings.add(supervisor < 0 ? findings.size() : supervisor,
                    new DiagnosticAgents.Finding("GUARDRAIL", "ADJUSTED", guard.note(), null));
        }
        String text = (advice.text() == null || advice.text().isBlank())
                ? ExperimentAdvisor.fallback(guard.proposal()) : advice.text();
        var explanation = new Explanation(
                advice.source(), text, advice.model(), advice.durationMs(),
                decisionJson(advice), guard.note(), advice.note(),
                List.copyOf(advice.evidenceRefs()), advice.toolCalls(), List.copyOf(advice.tools()),
                advice.confidence(), advice.riskLevel(),
                advice.inputTokens(), advice.outputTokens(), advice.revisions(),
                List.copyOf(advice.graphNodes()),
                List.copyOf(advice.actionItems()),
                List.copyOf(advice.experiments()),
                advice.mode(), ENGINE);
        var scorecard = SignalScorecard.evaluate(draft.input(), draft.metrics(), draft.context(), report, guard.proposal());
        return new Plan(draft.input(), draft.metrics(), guard.proposal(), draft.context(), explanation,
                List.copyOf(findings), draft.evidence(), POLICY, scorecard);
    }

    private Guard applyGuardrail(Plan draft, AdOptimizationGraph.GraphOutcome advice) {
        Proposal rules = draft.proposal();
        if (!advice.decided()) return new Guard(rules, null);
        Input input = draft.input();
        int limit = Math.max(0, Math.min(input.maxChangePercent(), draft.context().budgetChangeCap()));
        if (!draft.context().channelConfigured()) {
            return new Guard(rules, "报表渠道未配置，模型的动作建议已忽略");
        }
        if (limit <= 0) {
            return new Guard(rules, "活动未绑定可调整预算的规则，模型的动作建议已忽略");
        }
        if (input.spend().compareTo(input.minimumSpend()) < 0 || input.clicks() < input.minimumClicks()) {
            return new Guard(rules, "样本未达到最低花费/点击阈值，模型的动作建议已忽略");
        }
        Action wanted = Action.valueOf(advice.action());
        int requested = advice.changePercent() == null ? 0 : advice.changePercent();
        Action finalAction;
        int applied;
        switch (wanted) {
            case HOLD -> {
                finalAction = Action.HOLD;
                applied = 0;
            }
            case INCREASE -> {
                if (!"READY".equals(monitorStatus(draft)) || requested <= 0) {
                    finalAction = Action.HOLD;
                    applied = 0;
                } else {
                    finalAction = Action.INCREASE;
                    applied = Math.min(limit, requested);
                }
            }
            case REDUCE -> {
                if (requested == 0) {
                    finalAction = Action.HOLD;
                    applied = 0;
                } else {
                    finalAction = Action.REDUCE;
                    applied = -Math.min(limit, Math.abs(requested));
                }
            }
            default -> {
                return new Guard(rules, null);
            }
        }
        boolean changed = finalAction != wanted || applied != Math.abs(requested) * (wanted == Action.REDUCE ? -1 : 1)
                || (wanted == Action.REDUCE && requested > 0);
        Proposal guarded = new Proposal(finalAction, applied,
                advice.reason() == null || advice.reason().isBlank() ? rules.reason() : advice.reason());
        if (!changed) return new Guard(guarded, null);
        return new Guard(guarded, "模型建议 " + wanted + " " + requested + "% 已按规则收紧为 "
                + finalAction + " " + applied + "%（上限 ±" + limit + "%）");
    }

    private Plan analyze(Input input, CmsReportSource.Campaign report, boolean pending, String mode) {
        if (input.end().isBefore(input.start()) || input.clicks() > input.impressions()
                || input.conversions() > input.clicks()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range or funnel counts");
        }

        Metrics metrics = monitor(input);
        LegacyContext.Snapshot context = legacy.load(input.site(), input.channel(), input.campaignId());
        List<DiagnosticAgents.Finding> findings = new ArrayList<>(diagnostics.inspect(input, report));
        DiagnosticAgents.Finding monitor = findings.get(0);
        Proposal proposal = propose(input, monitor.status(), context);
        findings.add(new DiagnosticAgents.Finding("BIDDING", proposal.action().name(), proposal.reason(),
                BigDecimal.valueOf(proposal.changePercent())));
        findings.add(new DiagnosticAgents.Finding("SUPERVISOR", "REVIEW_REQUIRED",
                "等待审批，审批通过后可进行预算试算与执行", null));
        var evidence = report == null ? null
                : new Evidence(report.id(), report.currentBudget(), report.daily());
        var explanation = pending
                ? new Explanation(ExperimentAdvisor.PENDING, "", null, 0, null, null, null, List.of(), 0, List.of(),
                        null, null, 0, 0, 0, List.of(), List.of(), List.of(), mode, ENGINE)
                : new Explanation(ExperimentAdvisor.RULES, ExperimentAdvisor.fallback(proposal), null, 0, null,
                        null, null, List.of(), 0, List.of(),
                        null, null, 0, 0, 0, List.of(), List.of(), List.of(), "RULES_ONLY", ENGINE);
        var scorecard = SignalScorecard.evaluate(input, metrics, context, report, proposal);
        return new Plan(input, metrics, proposal, context, explanation, List.copyOf(findings), evidence, POLICY, scorecard);
    }

    private String monitorStatus(Plan plan) {
        return plan.findings().stream().filter(finding -> "MONITOR".equals(finding.stage()))
                .map(DiagnosticAgents.Finding::status).findFirst().orElse("INSUFFICIENT");
    }

    private void replaceFinding(List<DiagnosticAgents.Finding> findings, String stage,
                                DiagnosticAgents.Finding replacement) {
        int index = indexOfStage(findings, stage);
        if (index < 0) findings.add(replacement);
        else findings.set(index, replacement);
    }

    private int indexOfStage(List<DiagnosticAgents.Finding> findings, String stage) {
        for (int index = 0; index < findings.size(); index++) {
            if (stage.equals(findings.get(index).stage())) return index;
        }
        return -1;
    }

    private String decisionJson(AdOptimizationGraph.GraphOutcome advice) {
        if (!advice.decided()) return null;
        return "{\"action\":\"" + advice.action() + "\",\"changePercent\":" + advice.changePercent()
                + ",\"reason\":" + quote(advice.reason()) + "}";
    }

    private String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ") + "\"";
    }

    private Metrics monitor(Input input) {
        return new Metrics(
                divide(BigDecimal.valueOf(input.clicks()), BigDecimal.valueOf(input.impressions())),
                divide(BigDecimal.valueOf(input.conversions()), BigDecimal.valueOf(input.clicks())),
                divide(input.spend(), BigDecimal.valueOf(input.conversions())),
                divide(input.revenue(), input.spend()));
    }

    private Proposal propose(Input input, String evidenceStatus, LegacyContext.Snapshot context) {
        if (!context.channelConfigured()) {
            return new Proposal(Action.HOLD, 0, "当前站点和渠道没有对应的报表配置");
        }
        int changeLimit = Math.min(input.maxChangePercent(), context.budgetChangeCap());
        if (changeLimit <= 0) {
            return new Proposal(Action.HOLD, 0, "活动未绑定可调整预算的投放规则");
        }
        if (input.spend().compareTo(input.minimumSpend()) < 0 || input.clicks() < input.minimumClicks()) {
            return new Proposal(Action.HOLD, 0, "样本不足，继续观察");
        }
        BigDecimal targetRevenue = input.spend().multiply(input.targetRoas());
        if (input.revenue().compareTo(targetRevenue) < 0) {
            return new Proposal(Action.REDUCE, -changeLimit, "回收低于目标，检查归因和低效版位");
        }
        if (input.conversions() > 0 && input.revenue().compareTo(targetRevenue.multiply(new BigDecimal("1.20"))) >= 0) {
            if (!"READY".equals(evidenceStatus)) {
                return new Proposal(Action.HOLD, 0, "回收达标，日度和转化证据齐备后再考虑放量");
            }
            return new Proposal(Action.INCREASE, changeLimit, "回收高于目标至少 20%，可小幅放量");
        }
        return new Proposal(Action.HOLD, 0, "继续观察回收和转化稳定性");
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() == 0 ? null : numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private record Guard(Proposal proposal, String note) {}

    record Input(@NotBlank @Size(max = 80) String site,
                 @NotBlank @Size(max = 80) String channel,
                 @NotBlank @Size(max = 120) String campaignId,
                 @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
                 @NotNull LocalDate start, @NotNull LocalDate end,
                 @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 6) BigDecimal spend,
                 @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 6) BigDecimal revenue,
                 @NotNull @PositiveOrZero Long impressions, @NotNull @PositiveOrZero Long clicks,
                 @NotNull @PositiveOrZero Long conversions,
                 @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 6) BigDecimal targetRoas,
                 @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 6) BigDecimal minimumSpend,
                 @NotNull @Min(1) Integer minimumClicks, @NotNull @Min(1) @Max(20) Integer maxChangePercent) {}

    enum Action { HOLD, REDUCE, INCREASE }
    record Metrics(BigDecimal ctr, BigDecimal cvr, BigDecimal cpa, BigDecimal roas) {}
    record Proposal(Action action, int changePercent, String reason) {}

    /**
     * Rich commercial explanation payload generated by the LangGraph4j + LangChain4j agent graph.
     * Fully backwards-compatible with existing UI/API fields.
     */
    record Explanation(String source, String text, String model, long durationMs, String decision,
                       String guardrail, String note, List<String> evidenceRefs, int toolCalls,
                       List<String> tools, Double confidence, String riskLevel,
                       int inputTokens, int outputTokens, int revisions,
                       List<String> graphNodes, List<String> actionItems, List<String> experiments,
                       String mode, String engine) {}

    record Evidence(String scenario, BigDecimal observedBudget, List<CmsReportSource.Day> daily) {}
    record Plan(Input input, Metrics metrics, Proposal proposal, LegacyContext.Snapshot context, Explanation explanation,
                List<DiagnosticAgents.Finding> findings, Evidence evidence, String policyVersion,
                SignalScorecard.Scorecard scorecard) {}
}
