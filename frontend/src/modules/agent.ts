import { accountName, request, requestList, requestNonce, streamRequest } from "./api.js";
import { $, escape as e, money, multiple, percent, numeric, dateTime, statusNames, badge, metric, pageHead,
  dialog, closeDialog, formValues, errorIn, toast, busy, renderMarkdown, table, defaultDateRange,
  exportCsv, profitDisplay, roasBadge } from "./ui.js";
import { COPILOT_PLAYBOOKS, extractMentionedCampaignIds, renderAgentToolbox, renderAttributionSection } from "./agent-extensions.js";

const runsBase = "/cms/agent/runs";
const agentBase = "/cms/agent";
const chatBase = "/cms/agent/chat";

const scenarioTitles = { healthy: "表现良好", underperforming: "回收偏低", "low-volume": "样本不足" };
const stageTitles = { MONITOR: "监控", AUDIENCE: "受众", CREATIVE: "创意", BIDDING: "预算", GUARDRAIL: "护栏", SUPERVISOR: "审批" };
const nodeLabels = {
  intake_context: "读取投放数据",
  rule_baseline: "规则基线评估",
  research_specialist: "多维归因分析",
  strategy_planner: "策略与实验规划",
  tool_executor: "查询业务数据",
  guardrail_critic: "合规与护栏校验",
  self_reflect: "方案自动复核修正",
  finalize_plan: "生成最终方案",
  copilot_agent: "理解意图与分析",
  copilot_tools: "检索实时数据"
};
const toolLabels = {
  dailyReport: "逐日报表",
  countryBreakdown: "国家/受众拆分",
  creativeAndLandingContext: "素材与落地页",
  ruleContext: "投放规则",
  peerBenchmarks: "同站点基准",
  historicalRuns: "历史审批记录",
  scenarioCatalog: "场景清单",
  simulateBudget: "预算试算",
  getPortfolioOverview: "全盘投放概览",
  scanAnomalies: "全盘异常巡检",
  listUnboundCampaigns: "未绑规则排查",
  searchCampaigns: "活动检索",
  getCampaignDeepDive: "活动深度档案",
  getRuleCatalog: "规则目录",
  getRecentOptimizationRuns: "近期分析记录",
  simulateBudgetProjection: "预算试算",
  launchOptimizationAnalysis: "发起分析工单"
};
const explanationLabels = {
  PENDING: "分析生成中",
  RULES: "规则建议",
  RULES_MODEL_UNAVAILABLE: "服务暂不可用，已降级为规则",
  LANGCHAIN4J_OPENAI_COMPATIBLE: "智能分析",
  LANGCHAIN4J_LOCAL: "智能分析"
};
const riskLabels = { LOW: ["低风险", "teal"], MEDIUM: ["中风险", "amber"], HIGH: ["高风险", "red"] };
const severityBadges = {
  HIGH: '<span class="badge red">严重亏损</span>',
  OPPORTUNITY: '<span class="badge teal">放量机会</span>',
  MEDIUM: '<span class="badge amber">需关注</span>'
};
const categoryLabels = {
  CRITICAL_BLEED: "急需止损",
  UNDERPERFORMING: "回收偏低",
  SCALE_OPPORTUNITY: "高潜放量",
  RULE_UNBOUND: "缺规则护栏"
};
const inputFields = [
  ["site", "站点", "required"], ["channel", "报表渠道", "required"], ["campaignId", "活动 ID", "required"],
  ["currency", "币种", 'required pattern="[A-Z]{3}" maxlength="3" placeholder="USD" value="USD"'],
  ["start", "开始日期", 'required type="date"'], ["end", "结束日期", 'required type="date"'],
  ["spend", "花费", 'required type="number" min="0" step="0.01"'],
  ["revenue", "收入", 'required type="number" min="0" step="0.01"'],
  ["impressions", "展示量", 'required type="number" min="0"'],
  ["clicks", "点击量", 'required type="number" min="0"'],
  ["conversions", "转化量", 'required type="number" min="0"'],
  ["targetRoas", "目标 ROAS", 'required type="number" min="0.01" step="0.01" value="1.25"'],
  ["minimumSpend", "最低花费样本", 'required type="number" min="0.01" step="0.01" value="20"'],
  ["minimumClicks", "最低点击样本", 'required type="number" min="1" value="30"'],
  ["maxChangePercent", "预算最大调整百分比", 'required type="number" min="1" max="20" value="10"']
];

const copilotQuickPrompts = [
  "总结全盘投放表现、各平台 ROAS 对比，以及盈利和亏损前 5 的活动。",
  "巡检全盘异常活动，列出最急需止损和最值得放量的项目并说明原因。",
  "找出花费较高但尚未绑定预算护栏规则的活动。",
  "查看近期分析记录，有哪些待审批项需要优先处理？"
];

let activeTab = "scan";
let workflowViewMode: "list" | "kanban" = "list";
let selectedId;
let filter = "";
let aiOnlyFilter = false;
let lastAttempt;
let runs = [];
let engineStatus = null;
let safetyPolicy = null;
let selectedSessionId;
let chatSessions = [];
let activeScanResult = null;
let activeReplayResult = null;
let activeFiveAgentResult = null;

const safetyModeLabels = {
  SHADOW: ["SHADOW · 影子观察（禁写）", "gray"],
  COPILOT: ["COPILOT · 人工协同审批", "teal"],
  AUTONOMOUS: ["AUTONOMOUS · 限额自动执行", "amber"]
};

const verdictLabels = {
  SCALE: ["建议放量 (SCALE)", "teal"],
  REDUCE: ["建议降预算 (REDUCE)", "amber"],
  PAUSE: ["建议止损暂停 (PAUSE)", "red"],
  REFRESH: ["建议换新素材 (REFRESH)", "amber"],
  HOLD: ["保守观察 (HOLD)", "gray"]
};

export function focusAgentRun(runId) {
  selectedId = runId;
  activeTab = "workflow";
  filter = "";
  aiOnlyFilter = false;
  location.hash = "#agent";
}

export function openAgentTab(tabName) {
  activeTab = tabName || "workflow";
  location.hash = "#agent";
}

export function resetAgent() {
  activeTab = "scan";
  selectedId = undefined;
  filter = "";
  aiOnlyFilter = false;
  lastAttempt = undefined;
  runs = [];
  engineStatus = null;
  selectedSessionId = undefined;
  chatSessions = [];
  activeScanResult = null;
}

export async function refreshRuns() {
  runs = await requestList(runsBase);
  return runs;
}

const runTitle = run => run.plan?.input?.campaignId || "未命名分析";
const isAiRun = run => String(run.plan?.explanation?.source || "").startsWith("LANGCHAIN4J_");
const formatActor = raw => {
  const v = String(raw || "").trim();
  if (!v || v === "local-operator" || v === "local-admin") return "admin";
  if (v === "langgraph-agent") return "智能引擎";
  return v;
};
const formatSite = raw => (!raw || raw === "local") ? "全站默认" : String(raw);

function formatToolName(raw) {
  const name = String(raw || "").split("(")[0].trim();
  return toolLabels[name] || name;
}

function engineNoticeMarkup(status, context) {
  // 配好 AI 后不展示底层框架或模型路径，保持工作台简洁；仅在未配置时给出配置入口。
  if (!status || status.configured) return "";
  return `<div class="agent-engine-bar offline">
    <div class="engine-info"><span class="engine-status-dot offline"></span>
      <strong>尚未配置 AI 服务</strong>
      <span class="muted">当前仅运行规则建议。在渠道密钥中完成配置后即可使用智能分析与副驾驶对话。</span>
    </div>
    ${context.localCms ? '<a class="button small" href="#vault">前往配置</a>' : ""}
  </div>`;
}

function runList(list, selected, status, onlyAi) {
  const visible = list.filter(run => (!status || run.state === status) && (!onlyAi || isAiRun(run)));
  if (!visible.length) return '<div class="placeholder">当前筛选条件下暂无分析记录。</div>';
  return `<ul class="history">${visible.map(run => {
    const src = run.plan?.explanation?.source || "";
    const tag = src === "PENDING" ? '<span class="mini-tag pending">分析中</span>'
      : src.startsWith("LANGCHAIN4J_") ? '<span class="mini-tag ai">智能</span>'
      : src === "RULES_MODEL_UNAVAILABLE" ? '<span class="mini-tag warn">降级</span>'
      : '<span class="mini-tag rules">规则</span>';
    return `<li><button type="button" data-run="${e(run.id)}" aria-pressed="${run.id === selected}">
      <span><strong>${e(runTitle(run))} ${tag}</strong><small>${e(dateTime(run.createdAt))}</small></span>${badge(run.state)}</button></li>`;
  }).join("")}</ul>`;
}

function metrics(plan) {
  const data = plan.metrics || {};
  const currency = plan.input?.currency;
  return `<div class="cards">${metric("CTR", percent(data.ctr))}${metric("CVR", percent(data.cvr))}${
    metric("CPA", money(data.cpa, currency))}${metric("ROAS", multiple(data.roas))}</div>`;
}

function explanationMeta(explanation) {
  if (!explanation) return "";
  const parts = [explanationLabels[explanation.source] || explanation.source];
  if (explanation.mode === "DEEP") parts.push("深度分析");
  if (explanation.durationMs) parts.push(`耗时 ${(explanation.durationMs / 1000).toFixed(1)} 秒`);
  if (explanation.toolCalls) parts.push(`核验 ${explanation.toolCalls} 项业务数据`);
  if (explanation.revisions > 0) parts.push(`合规复核修正 ${explanation.revisions} 次`);
  if (explanation.decision) {
    try {
      const decision = JSON.parse(explanation.decision);
      parts.push(`建议动作 ${decision.action} ${decision.changePercent}%`);
    } catch {
      // ignore unparseable decision
    }
  }
  const uniqueTools = Array.isArray(explanation.tools) && explanation.tools.length
    ? [...new Set(explanation.tools.map(formatToolName))] : [];
  const toolsLine = uniqueTools.length
    ? `<p class="analysis-meta">参考数据源：${uniqueTools.map(e).join(" · ")}</p>` : "";
  return `<p class="analysis-meta">${parts.map(e).join(" · ")}</p>${toolsLine}`;
}

function livePipelineMarkup(nodes, isPending) {
  const defaultOrder = ["intake_context", "research_specialist", "strategy_planner", "tool_executor", "guardrail_critic", "finalize_plan"];
  const seen = new Map();
  (nodes || []).forEach(item => {
    const key = item.node || item.nodeName;
    if (key) seen.set(key, item);
  });
  const list = seen.size > 0
    ? [...seen.values()]
    : defaultOrder.map(k => ({ node: k, status: isPending ? "WAITING" : "SKIPPED", summary: "" }));
  return `<div class="graph-stepper" id="live-graph-stepper">
    ${list.map(step => {
      const name = step.node || step.nodeName;
      const status = step.status || "OK";
      const cls = status === "OK" || status === "RECOVERED" ? "done"
        : status === "FAILED" || status === "DEGRADED" ? "warn"
        : status === "REFLECT" ? "reflect" : "waiting";
      const dur = step.durationMs != null ? ` · ${(step.durationMs / 1000).toFixed(1)}s` : "";
      return `<div class="graph-step ${cls}">
        <strong>${e(nodeLabels[name] || name)}</strong>
        <small>${e(cls === "waiting" ? "等待执行…" : (status === "OK" ? "已完成" : status === "REFLECT" ? "自动复核中" : "已处理"))}${e(dur)}</small>
      </div>`;
    }).join("")}
  </div>`;
}

function safetyBarMarkup(policy) {
  if (!policy) return "";
  const [modeText, modeTone] = safetyModeLabels[policy.safetyMode] || [policy.safetyMode || "COPILOT", "teal"];
  const killed = Boolean(policy.killSwitch);
  const used = Number(policy.usedBudgetDeltaToday || 0);
  const cap = Number(policy.dailyBudgetDeltaCap || 500);
  return `<div class="agent-safety-bar ${killed ? "killed" : ""}">
    <div class="safety-bar-left">
      <span class="badge ${killed ? "red" : modeTone}">${e(killed ? "KILL SWITCH · 全局熔断中" : modeText)}</span>
      <span class="safety-meta">今日已变动预算 <strong>$${used.toFixed(2)} / $${cap.toFixed(2)}</strong> · 单次调幅上限 <strong>±${e(policy.maxSingleChangePct ?? 20)}%</strong> · 今日模型调用 <strong>${e(policy.llmCallsToday ?? 0)} / ${e(policy.maxLlmCallsPerDay ?? 200)}</strong></span>
    </div>
    <div class="action-set">
      <button type="button" class="button secondary small" id="btn-safety-settings">安全策略</button>
      <button type="button" class="button ${killed ? "" : "negative"} small" id="btn-kill-switch">${killed ? "解除熔断" : "一键熔断 (Kill Switch)"}</button>
    </div>
  </div>`;
}

function scorecardMarkup(plan) {
  const card = plan?.scorecard;
  if (!card || !Array.isArray(card.signals) || !card.signals.length) return "";
  const [vLabel, vTone] = verdictLabels[card.verdict] || [card.verdict || "HOLD", "gray"];
  const confPct = Math.round(Number(card.confidence || 0) * 100);
  const sigTone = status => status === "GREEN" ? "teal" : status === "YELLOW" ? "amber" : "red";
  const sigText = status => status === "GREEN" ? "健康" : status === "YELLOW" ? "观察" : "预警";
  return `<section class="scorecard-box">
    <div class="scorecard-head">
      <div>
        <h3>8 信号确定性诊断决策卡 <span class="muted">（规则引擎核算 · 零模型幻觉）</span></h3>
        <p class="sub">绿灯 ${e(card.greenCount)} · 黄灯 ${e(card.yellowCount)} · 红灯 ${e(card.redCount)} · 规则置信度 ${confPct}%</p>
      </div>
      <span class="badge ${vTone}">${e(vLabel)}</span>
    </div>
    <div class="signal-grid">
      ${card.signals.map(s => `<article class="signal-card sig-${e(String(s.status || "YELLOW").toLowerCase())}">
        <div class="signal-top">
          <strong>${e(s.label)}</strong>
          <span class="badge ${sigTone(s.status)}">${sigText(s.status)}</span>
        </div>
        <div class="signal-val mono">${e(s.value)}</div>
        <small class="signal-bench">${e(s.benchmark)}</small>
        <p class="signal-detail">${e(s.detail)}</p>
      </article>`).join("")}
    </div>
    ${Array.isArray(card.ruleChain) && card.ruleChain.length
      ? `<details class="rule-chain-details"><summary>查看规则决策链 (${card.ruleChain.length} 条)</summary><ul>${card.ruleChain.map(r => `<li><code>${e(r)}</code></li>`).join("")}</ul></details>`
      : ""}
  </section>`;
}

function detailMarkup(run, context) {
  const { plan, simulation, execution } = run;
  const decision = plan.proposal;
  const explanation = plan.explanation || {};
  const name = accountName();
  const canReview = !context.requireStaffLogin || name === "reviewer";
  const canSimulate = !context.requireStaffLogin || name === "operator";
  const canOperate = !context.requireStaffLogin || name === "operator";
  const budget = plan.evidence?.observedBudget;
  const actions = [];
  if (run.state === "PENDING") {
    actions.push(canReview ? `<form id="review-form"><label>审批说明（绑定当前版本 v${e(run.proposalRevision || 1)} 指纹防篡改）<textarea rows="2" name="note" required maxlength="500" placeholder="说明通过或驳回的依据"></textarea></label>
      <div class="form-actions"><button class="button" type="submit" value="APPROVED">通过审批</button><button class="button negative" type="submit" value="REJECTED">驳回</button></div></form>`
      : '<p class="sub">请切换至审批员账号处理这条建议。</p>');
  }
  if (run.state === "APPROVED") {
    actions.push(canSimulate ? `<form id="simulation-form" class="form-grid"><label>当前预算（${e(plan.input.currency)}）<input type="number" name="budget" required min="0.01" max="999999999999.99" step="0.01" value="${e(budget ?? "")}" ${budget != null ? "readonly" : ""}></label>
      <div class="form-actions"><button class="button secondary" type="submit" data-action="simulate">仅预算试算 (Dry-Run)</button><button class="button" type="button" id="btn-execute-budget">执行预算变更（幂等账本）</button></div></form>`
      : '<p class="sub">请切换至操作员账号试算或执行预算变更。</p>');
  }
  if (run.state === "SIMULATED") {
    const simBudget = simulation?.currentBudget ?? budget ?? "";
    actions.push(canSimulate ? `<form id="execute-only-form" class="form-grid"><label>确认执行前预算（${e(plan.input.currency)}）<input type="number" name="budget" required min="0.01" max="999999999999.99" step="0.01" value="${e(simBudget)}" ${simBudget !== "" ? "readonly" : ""}></label>
      <div class="form-actions"><button class="button" type="submit">执行预算变更（幂等账本）</button></div></form>`
      : "");
  }
  if (run.state === "EXECUTED" && canOperate) {
    actions.push(`<div class="form-actions"><button type="button" class="button negative" id="btn-rollback-budget">一键回滚至执行前预算 (${e(money(execution?.beforeBudget, execution?.currency || plan.input?.currency))})</button></div>`);
  }

  const riskBadge = explanation.riskLevel && riskLabels[explanation.riskLevel]
    ? `<span class="badge ${riskLabels[explanation.riskLevel][1]}">${riskLabels[explanation.riskLevel][0]}</span>` : "";
  const confBadge = typeof explanation.confidence === "number"
    ? `<span class="badge teal">置信度 ${Math.round(explanation.confidence * 100)}%</span>` : "";
  const modeBadge = explanation.mode === "DEEP" ? '<span class="badge">深度分析</span>' : "";
  const shortHash = run.proposalHash ? String(run.proposalHash).slice(0, 8) : "";
  const fingerprintBadge = shortHash
    ? `<span class="badge gray mono" title="提案 SHA-256 指纹：${e(run.proposalHash)}">v${e(run.proposalRevision || 1)} · #${e(shortHash)}</span>` : "";

  const evidenceChips = Array.isArray(explanation.evidenceRefs) && explanation.evidenceRefs.length
    ? `<div class="chip-section"><h4>关键依据</h4><div class="chip-list">${explanation.evidenceRefs.map(ref => `<span class="chip">${e(ref)}</span>`).join("")}</div></div>` : "";
  const actionChecklist = Array.isArray(explanation.actionItems) && explanation.actionItems.length
    ? `<div class="chip-section"><h4>优化动作清单</h4><ol class="action-checklist">${explanation.actionItems.map(item => `<li>${e(item)}</li>`).join("")}</ol></div>` : "";
  const experimentsBlock = Array.isArray(explanation.experiments) && explanation.experiments.length
    ? `<div class="chip-section"><h4>推荐 A/B 实验</h4><ul class="action-checklist">${explanation.experiments.map(item => `<li>${e(item)}</li>`).join("")}</ul></div>` : "";

  const reanalyzeButtons = run.state === "PENDING" && canOperate && explanation.source !== "PENDING"
    ? `<button type="button" class="button secondary small" id="reanalyze-standard">重新分析</button>
       <button type="button" class="button secondary small" id="reanalyze-deep">深度分析</button>` : "";

  const executionCard = execution ? `<div class="recommendation ${run.state === "ROLLED_BACK" ? "rolled-back-card" : ""}">
      <h3>${run.state === "ROLLED_BACK" ? "已执行 Saga 回滚" : "幂等账本执行回执"}</h3>
      <p>账本主键 <code>${e(execution.ledgerKey)}</code> · 安全模式 <strong>${e(execution.safetyMode)}</strong> · 预算从 ${e(money(execution.beforeBudget, execution.currency))} 调整为 <strong>${e(money(execution.afterBudget, execution.currency))}</strong>（执行人：${e(execution.executedBy)}）${execution.rolledBackBy ? ` · 已由 ${e(execution.rolledBackBy)} 回滚恢复原预算` : ""}</p>
    </div>` : "";

  return `<div class="panel-head">
      <div>
        <h2>${e(runTitle(run))}</h2>
        <p class="sub">${e(plan.input?.start)} 至 ${e(plan.input?.end)} · 渠道 ${e(plan.input?.channel)} · 站点 ${e(formatSite(plan.input?.site))}${run.createdBy ? ` · 发起人 ${e(formatActor(run.createdBy))}` : ""}</p>
      </div>
      <div class="action-set">${fingerprintBadge}${modeBadge}${riskBadge}${confBadge}${badge(run.state)}</div>
    </div>
    <div class="detail-quick-actions">
      ${reanalyzeButtons}
      ${canOperate ? '<button type="button" class="button secondary small" id="ask-copilot-run">继续追问</button>' : ""}
    </div>
    ${metrics(plan)}
    ${scorecardMarkup(plan)}
    <div class="recommendation">
      <h3>预算建议：${e(decision.action)} ${decision.changePercent > 0 ? "+" : ""}${e(decision.changePercent)}%</h3>
      <p>${e(decision.reason)}</p>
      ${explanation.source === "PENDING"
        ? `<div class="analysis-pending">正在读取报表、国家拆分、创意素材与规则并生成分析，完成后自动刷新…</div>${livePipelineMarkup([], true)}`
        : `<div class="analysis-text">${renderMarkdown(explanation.text || "")}</div>`}
      ${evidenceChips}${actionChecklist}${experimentsBlock}
      ${explanation.guardrail ? `<p class="guardrail">规则护栏：${e(explanation.guardrail)}</p>` : ""}
      ${explanation.note ? `<p class="guardrail">提示：${e(explanation.note)}</p>` : ""}
      ${explanationMeta(explanation)}
    </div>
    ${plan.findings?.length ? `<h3>阶段诊断</h3><ul class="findings">${plan.findings.map(item =>
      `<li><strong>${e(stageTitles[item.stage] || item.stage)}</strong><span>${e(item.evidence)}</span></li>`).join("")}</ul>` : ""}
    ${run.reviewNote ? `<p class="sub">审批说明 · ${e(run.reviewNote)}</p>` : ""}
    ${simulation ? `<div class="recommendation"><h3>预算试算结果 (Dry-Run)</h3><p>${e(money(simulation.currentBudget, simulation.currency))} 预计调整为 <strong>${e(money(simulation.projectedBudget, simulation.currency))}</strong>（未修改真实预算）</p></div>` : ""}
    ${executionCard}
    ${actions.length ? `<div class="agent-tools">${actions.join("")}<div id="agent-action-error"></div></div>` : ""}
    <details class="trace-details" id="trace-details">
      <summary>分析执行明细</summary>
      <div id="run-trace-body"><p class="sub">展开查看各分析步骤耗时与执行状态…</p></div>
    </details>
    <section class="audit-section"><h3>执行账本与审计记录</h3><div id="ledger-box"></div><div id="audit"><p class="sub">正在读取…</p></div></section>`;
}

export async function agent(target, context, isCurrent, openLogin) {
  const simulated = context.mode === "simulation" || context.localCms;
  const canCreate = !context.requireStaffLogin || accountName() === "operator";
  const headActions = simulated && canCreate
    ? (context.localCms ? "" : '<button class="button secondary" id="sample-analysis" type="button">选择示例</button>')
      + '<button class="button" id="new-analysis" type="button">+ 新建分析</button>' : "";

  target.innerHTML = pageHead("智能体工作台", "投放诊断、异常巡检、智能副驾驶与预算审批。", headActions);

  if (!simulated) {
    target.insertAdjacentHTML("beforeend", '<div class="placeholder">当前环境暂未启用智能体服务。</div>');
    return;
  }
  if (context.requireStaffLogin && !accountName()) {
    target.insertAdjacentHTML("beforeend", '<div class="placeholder">使用操作员或审批员账号登录后继续。 <button class="button small" id="agent-login" type="button">登录</button></div>');
    $("#agent-login", target).onclick = openLogin;
    return;
  }

  target.insertAdjacentHTML("beforeend", `
    <div id="agent-engine-slot">${engineNoticeMarkup(engineStatus, context)}</div>
    <div id="agent-safety-slot">${safetyBarMarkup(safetyPolicy)}</div>
    <div class="agent-tabs" role="tablist" aria-label="智能体工作台功能区">
      <button type="button" class="tab-btn" data-agent-tab="scan" aria-pressed="${activeTab === "scan"}">异常巡检</button>
      <button type="button" class="tab-btn" data-agent-tab="five-agent" aria-pressed="${activeTab === "five-agent"}">五智能体闭环</button>
      <button type="button" class="tab-btn" data-agent-tab="workflow" aria-pressed="${activeTab === "workflow"}">分析与审批</button>
      <button type="button" class="tab-btn" data-agent-tab="copilot" aria-pressed="${activeTab === "copilot"}">智能副驾驶</button>
      <button type="button" class="tab-btn" data-agent-tab="toolbox" aria-pressed="${activeTab === "toolbox"}">智能工具箱</button>
      <button type="button" class="tab-btn" data-agent-tab="replay" aria-pressed="${activeTab === "replay"}">离线回放评估</button>
      <button type="button" class="tab-btn" data-agent-tab="observability" aria-pressed="${activeTab === "observability"}">运行统计</button>
    </div>
    <div id="agent-tab-body"></div>
  `);

  const engineSlot = $("#agent-engine-slot", target);
  const safetySlot = $("#agent-safety-slot", target);
  const tabBody = $("#agent-tab-body", target);
  const current = () => isCurrent() && tabBody.isConnected;
  let activeStreamAbort = null;
  let pendingTimer;

  function renderSafetySlot() {
    if (!current()) return;
    safetySlot.innerHTML = safetyBarMarkup(safetyPolicy);
    const killBtn = $("#btn-kill-switch", safetySlot);
    const settingsBtn = $("#btn-safety-settings", safetySlot);
    if (killBtn) {
      killBtn.onclick = () => busy(killBtn, async () => {
        const nextActive = !safetyPolicy?.killSwitch;
        safetyPolicy = await request(`${agentBase}/safety/kill-switch`, {
          body: { active: nextActive, reason: nextActive ? "管理员一键开启全局熔断" : "" }
        });
        toast(nextActive ? "已开启全局熔断 (Kill Switch)，所有写操作已拦截" : "已解除全局熔断");
        renderSafetySlot();
      });
    }
    if (settingsBtn) {
      settingsBtn.onclick = () => {
        const p = safetyPolicy || { safetyMode: "COPILOT", dailyBudgetDeltaCap: 500, maxSingleChangePct: 20, maxLlmCallsPerDay: 200 };
        const content = dialog("资金安全策略与护栏设置", `<p class="sub">控制智能体的资金写操作权限梯度、学习期单次调幅上限与每日成本预算。</p>
          <form id="safety-policy-form" class="form-grid">
            <label class="all">安全模式梯度
              <select name="safetyMode">
                <option value="SHADOW" ${p.safetyMode === "SHADOW" ? "selected" : ""}>SHADOW · 影子观察（只出建议与试算，禁止写入）</option>
                <option value="COPILOT" ${p.safetyMode === "COPILOT" ? "selected" : ""}>COPILOT · 人工协同（审批通过后由人工确认执行）</option>
                <option value="AUTONOMOUS" ${p.safetyMode === "AUTONOMOUS" ? "selected" : ""}>AUTONOMOUS · 限额自动（在单日与单次上限内执行）</option>
              </select>
            </label>
            <label>单日累计预算变动上限 (USD)<input type="number" name="dailyBudgetDeltaCap" min="0" step="10" value="${e(p.dailyBudgetDeltaCap ?? 500)}" required></label>
            <label>单次最大调幅上限 (%)<input type="number" name="maxSingleChangePct" min="1" max="30" value="${e(p.maxSingleChangePct ?? 20)}" required></label>
            <label>每日 AI 调用上限 (次)<input type="number" name="maxLlmCallsPerDay" min="10" max="10000" value="${e(p.maxLlmCallsPerDay ?? 200)}" required></label>
            <div class="all" id="safety-form-error"></div>
            <div class="all form-actions"><button type="submit" class="button">保存安全策略</button></div>
          </form>`);
        const form = $("#safety-policy-form", content);
        form.onsubmit = event => {
          event.preventDefault();
          const vals = formValues(form);
          busy($("button", form), async () => {
            safetyPolicy = await request(`${agentBase}/safety`, {
              body: {
                safetyMode: vals.safetyMode,
                dailyBudgetDeltaCap: Number(vals.dailyBudgetDeltaCap),
                maxSingleChangePct: Number(vals.maxSingleChangePct),
                maxLlmCallsPerDay: Number(vals.maxLlmCallsPerDay)
              }
            });
            closeDialog();
            toast("资金安全策略已保存并生效");
            renderSafetySlot();
          }, msg => errorIn($("#safety-form-error", content), msg));
        };
      };
    }
  }

  function refreshSafety() {
    return request(`${agentBase}/safety`).then(policy => {
      safetyPolicy = policy;
      renderSafetySlot();
    }).catch(() => {});
  }
  refreshSafety();

  function stopLiveStream() {
    clearTimeout(pendingTimer);
    if (activeStreamAbort) {
      activeStreamAbort.abort();
      activeStreamAbort = null;
    }
  }

  request(`${agentBase}/status`).then(status => {
    engineStatus = status;
    if (current()) engineSlot.innerHTML = engineNoticeMarkup(status, context);
  }).catch(() => {});

  function setTab(next) {
    stopLiveStream();
    activeTab = next;
    target.querySelectorAll("[data-agent-tab]").forEach(btn => {
      btn.setAttribute("aria-pressed", String(btn.dataset.agentTab === activeTab));
    });
    if (activeTab === "workflow") renderWorkflowTab();
    else if (activeTab === "five-agent") renderFiveAgentTab();
    else if (activeTab === "copilot") renderCopilotTab();
    else if (activeTab === "toolbox") renderAgentToolbox(tabBody, quickDiagnoseFromAgent);
    else if (activeTab === "scan") renderScanTab();
    else if (activeTab === "replay") renderReplayTab();
    else renderObservabilityTab();
  }

  async function quickDiagnoseFromAgent(campaignId, button) {
    await busy(button, async () => {
      const run = await request(`${runsBase}/campaigns/${encodeURIComponent(campaignId)}`, {
        body: { mode: "STANDARD", targetRoas: 1.25 }
      });
      toast(`已为 ${campaignId} 创建智能诊断提案`);
      selectedId = run.id;
      workflowViewMode = "list";
      await refreshRuns();
      setTab("workflow");
    });
  }

  target.querySelectorAll("[data-agent-tab]").forEach(btn => {
    btn.onclick = () => setTab(btn.dataset.agentTab);
  });

  // =========================================================================
  // Tab 1: 分析与审批（支持列表详情 + 看板视图 + 批量审批）
  // =========================================================================
  async function renderWorkflowTab() {
    const canReview = !context.requireStaffLogin || accountName() === "reviewer";
    tabBody.innerHTML = `<div class="workflow-bar">
      <div class="action-set">
        <button type="button" class="button ${workflowViewMode === "list" ? "" : "secondary"} small" id="wf-mode-list">列表详情视图</button>
        <button type="button" class="button ${workflowViewMode === "kanban" ? "" : "secondary"} small" id="wf-mode-kanban">审批看板 (Kanban)</button>
      </div>
      ${canReview ? '<button type="button" class="button secondary small" id="wf-batch-approve">一键批量通过待审批</button>' : ""}
    </div>
    <div id="wf-content"></div>`;

    const wfContent = $("#wf-content", tabBody);

    $("#wf-mode-list", tabBody).onclick = () => {
      workflowViewMode = "list";
      renderWorkflowTab();
    };
    $("#wf-mode-kanban", tabBody).onclick = () => {
      workflowViewMode = "kanban";
      renderWorkflowTab();
    };

    const batchBtn = $("#wf-batch-approve", tabBody);
    if (batchBtn) {
      batchBtn.onclick = () => {
        const pendingIds = runs.filter(r => r.state === "PENDING").map(r => r.id);
        if (!pendingIds.length) {
          toast("当前没有待审批的分析记录");
          return;
        }
        const content = dialog("批量审批待处理提案", `<p class="sub">当前共有 <strong>${pendingIds.length}</strong> 条「待审批」提案，将统一记录不可变审计日志。</p>
          <form id="batch-review-form" class="form-grid">
            <label class="all">审批动作
              <select name="decision">
                <option value="APPROVED">全部通过 (APPROVED)</option>
                <option value="REJECTED">全部驳回 (REJECTED)</option>
              </select>
            </label>
            <label class="all">统一审批说明<textarea name="note" rows="3" required maxlength="500" placeholder="例如：已核对 8 信号决策卡与预算护栏上限，同意进入执行阶段"></textarea></label>
            <div class="all" id="batch-review-err"></div>
            <div class="all form-actions"><button type="submit" class="button">确认批量提交 (${pendingIds.length} 条)</button></div>
          </form>`);
        const bf = $("#batch-review-form", content);
        bf.onsubmit = event => {
          event.preventDefault();
          const vals = formValues(bf);
          busy($("button", bf), async () => {
            const res = await request(`${runsBase}/batch-review`, {
              body: { runIds: pendingIds.slice(0, 30), decision: vals.decision, note: vals.note }
            });
            closeDialog();
            toast(`批量审批完成：成功 ${res.succeeded} 条${res.skipped ? `，跳过 ${res.skipped} 条` : ""}`);
            await refreshRuns();
            if (current() && activeTab === "workflow") renderWorkflowTab();
          }, msg => errorIn($("#batch-review-err", content), msg));
        };
      };
    }

    if (workflowViewMode === "kanban") {
      wfContent.innerHTML = '<div class="placeholder">正在加载审批看板…</div>';
      try {
        await refreshRuns();
        if (!current() || activeTab !== "workflow" || workflowViewMode !== "kanban") return;
        const columns = [
          { title: "待审批 (PENDING)", states: ["PENDING"], tone: "amber" },
          { title: "已通过 / 已试算", states: ["APPROVED", "SIMULATED"], tone: "teal" },
          { title: "已执行 (EXECUTED)", states: ["EXECUTED"], tone: "teal" },
          { title: "已驳回 / 已回滚", states: ["REJECTED", "ROLLED_BACK"], tone: "red" }
        ];
        wfContent.innerHTML = `<div class="kanban-board">${columns.map(col => {
          const colRuns = runs.filter(r => col.states.includes(r.state));
          return `<section class="kanban-col">
            <header class="kanban-col-head">
              <strong>${e(col.title)}</strong>
              <span class="badge ${col.tone}">${colRuns.length}</span>
            </header>
            <div class="kanban-cards">
              ${colRuns.length ? colRuns.map(r => {
                const prop = r.plan?.proposal || {};
                const roas = r.plan?.metrics?.roas;
                const shortHash = r.proposalHash ? `#${String(r.proposalHash).slice(0, 6)}` : "";
                return `<article class="kanban-card" data-kanban-run="${e(r.id)}">
                  <div class="signal-top">
                    <strong class="mono">${e(runTitle(r))}</strong>
                    ${badge(r.state)}
                  </div>
                  <p class="sub">${e(prop.action || "HOLD")} ${prop.changePercent > 0 ? "+" : ""}${e(prop.changePercent ?? 0)}% · ROAS ${e(multiple(roas))}</p>
                  <div class="kanban-card-foot">
                    <small class="muted">v${e(r.proposalRevision || 1)} ${e(shortHash)}</small>
                    <small class="muted">${e(dateTime(r.createdAt))}</small>
                  </div>
                </article>`;
              }).join("") : '<p class="sub kanban-empty">暂无记录</p>'}
            </div>
          </section>`;
        }).join("")}</div>`;
        wfContent.querySelectorAll("[data-kanban-run]").forEach(card => {
          card.onclick = () => {
            selectedId = card.dataset.kanbanRun;
            workflowViewMode = "list";
            renderWorkflowTab();
          };
        });
      } catch (err) {
        if (current() && activeTab === "workflow") errorIn(wfContent, err.message);
      }
      return;
    }

    wfContent.innerHTML = `<div class="agent-grid">
      <section class="panel">
        <div class="panel-head">
          <h2>分析记录</h2>
          <select id="run-filter" aria-label="筛选记录状态">
            <option value="">全部状态</option>${Object.entries(statusNames)
              .filter(([k]) => ["PENDING", "APPROVED", "REJECTED", "SIMULATED", "EXECUTED", "ROLLED_BACK"].includes(k))
              .map(([key, value]) => `<option value="${key}" ${key === filter ? "selected" : ""}>${value}</option>`).join("")}
          </select>
        </div>
        <label class="inline-check"><input type="checkbox" id="ai-only-toggle" ${aiOnlyFilter ? "checked" : ""}> 仅看智能分析</label>
        <div id="run-history"><div class="placeholder">正在读取…</div></div>
      </section>
      <section class="panel agent-detail" id="run-detail"><div class="placeholder">选择一条分析记录，或点击右上角「+ 新建分析」。</div></section>
    </div>`;

    const history = $("#run-history", tabBody);
    const detail = $("#run-detail", tabBody);
    let focused;

    function showHistory() {
      if (!current() || activeTab !== "workflow") return;
      history.innerHTML = runList(runs, selectedId, filter, aiOnlyFilter);
      history.querySelectorAll("[data-run]").forEach(button => {
        button.onclick = () => busy(button, () => select(button.dataset.run), message => errorIn(detail, message));
      });
    }

    async function paint(run) {
      focused = run;
      detail.innerHTML = detailMarkup(run, context);
      wireDetailActions(run);

      const ledgerBox = $("#ledger-box", detail);
      if (ledgerBox) {
        requestList(`${runsBase}/${run.id}/ledger`).then(entries => {
          if (!ledgerBox.isConnected || !entries.length) return;
          ledgerBox.innerHTML = table(
            ["账本主键", "动作", "版本", "变更前预算", "变更后预算", "状态", "操作人", "时间"],
            entries.map(item => [
              `<code class="mono">${e(item.ledgerKey)}</code>`,
              e(item.actionType === "ROLLBACK" ? "回滚 (ROLLBACK)" : "执行 (EXECUTE)"),
              e(`v${item.proposalRevision}`),
              e(money(item.beforeBudget, item.currency)),
              e(money(item.afterBudget, item.currency)),
              e(item.status === "ROLLED_BACK" ? "已回滚" : "已落账"),
              e(item.actor),
              e(dateTime(item.createdAt))
            ]), "");
        }).catch(() => {});
      }

      const audit = $("#audit", detail);
      requestList(`${runsBase}/${run.id}/audit`).then(events => {
        if (!audit.isConnected) return;
        audit.innerHTML = events.length ? `<ol class="audit">${events.map(event =>
          `<li>${e(statusNames[event.stage] || event.stage)} · ${e(formatActor(event.actor))} · ${e(dateTime(event.occurredAt))}</li>`).join("")}</ol>`
          : '<p class="sub">暂无审计操作。</p>';
      }).catch(error => {
        if (audit.isConnected) errorIn(audit, error.message);
      });

      const traceDetails = $("#trace-details", detail);
      if (traceDetails) {
        traceDetails.ontoggle = async () => {
          if (!traceDetails.open) return;
          const body = $("#run-trace-body", detail);
          try {
            const trace = await request(`${runsBase}/${run.id}/trace`);
            if (!body.isConnected) return;
            const nodeTable = table(["分析步骤", "状态", "调用工具", "耗时", "说明"],
              (trace.nodes || []).map(n => [
                e(nodeLabels[n.nodeName] || n.nodeName),
                e(n.status === "OK" ? "完成" : n.status === "REFLECT" ? "复核修正" : n.status === "RECOVERED" ? "已恢复" : n.status),
                e(String(n.toolCalls || 0)),
                e(`${n.durationMs || 0} ms`),
                e(n.summary || "")
              ]), "该记录暂无执行明细，待审批记录可点击上方「重新分析」生成。");
            body.innerHTML = nodeTable;
          } catch (error) {
            if (body.isConnected) errorIn(body, error.message);
          }
        };
      }

      if (run.plan?.explanation?.source === "PENDING") {
        watchPendingRun(run.id);
      }
    }

    function watchPendingRun(id) {
      stopLiveStream();
      const liveNodes = [];
      const controller = new AbortController();
      activeStreamAbort = controller;

      streamRequest(`${runsBase}/${id}/stream`, {
        method: "GET",
        signal: controller.signal,
        onEvent: (eventName, payload) => {
          if (!current() || activeTab !== "workflow" || selectedId !== id) return;
          if (eventName === "node" && payload) {
            liveNodes.push(payload);
            const stepper = $("#live-graph-stepper", detail);
            if (stepper) stepper.outerHTML = livePipelineMarkup(liveNodes, true);
          } else if (eventName === "done") {
            refreshSelected(id);
          }
        }
      }).catch(() => {
        if (current() && activeTab === "workflow" && selectedId === id) {
          pollFallback(id);
        }
      });

      pendingTimer = setTimeout(() => {
        if (current() && activeTab === "workflow" && selectedId === id) {
          pollFallback(id);
        }
      }, 3500);
    }

    async function pollFallback(id) {
      clearTimeout(pendingTimer);
      if (!current() || activeTab !== "workflow" || selectedId !== id) return;
      try {
        const run = await request(`${runsBase}/${id}`);
        if (!current() || activeTab !== "workflow" || selectedId !== id) return;
        if (run.plan?.explanation?.source === "PENDING") {
          pendingTimer = setTimeout(() => pollFallback(id), 2000);
          return;
        }
        await refreshSelected(id, run);
      } catch {
        // Ignore transient poll errors while streaming.
      }
    }

    async function refreshSelected(id: string, preloaded?: any) {
      stopLiveStream();
      const run = preloaded || await request(`${runsBase}/${id}`);
      if (!current() || activeTab !== "workflow" || selectedId !== id) return;
      await refreshRuns();
      if (!current() || activeTab !== "workflow" || selectedId !== id) return;
      showHistory();
      await paint(run);
    }

    async function select(id) {
      selectedId = id;
      stopLiveStream();
      showHistory();
      detail.innerHTML = '<div class="placeholder">正在读取分析详情…</div>';
      const run = await request(`${runsBase}/${id}`);
      if (!current() || activeTab !== "workflow" || selectedId !== id) return;
      await paint(run);
    }

    function wireDetailActions(run) {
      const review = $("#review-form", detail);
      const simulation = $("#simulation-form", detail);
      function submit(event, endpoint, payload, success) {
        event.preventDefault();
        const button = event.submitter || $("button", event.currentTarget);
        const siblings = [...event.currentTarget.querySelectorAll("button")].filter(other => other !== button);
        siblings.forEach(other => { other.disabled = true; });
        const error = $("#agent-action-error", detail);
        busy(button, async () => {
          await request(`${runsBase}/${run.id}/${endpoint}`, { body: payload });
          toast(success);
          if (!current() || selectedId !== run.id) return;
          await refreshRuns();
          await select(run.id);
        }, message => error?.isConnected ? errorIn(error, message) : toast(message))
          .finally(() => siblings.forEach(other => { other.disabled = false; }));
      }
      if (review) review.onsubmit = event => submit(event, "review", {
        decision: event.submitter?.value || "APPROVED",
        note: formValues(review).note,
        expectedHash: run.proposalHash || undefined,
        expectedRevision: run.proposalRevision || undefined
      }, "审批已记录（已绑定提案指纹）");
      if (simulation) simulation.onsubmit = event => submit(event, "simulate", {
        currentBudget: Number(formValues(simulation).budget)
      }, "预算试算已完成");

      const runExecute = (button, budgetVal) => {
        const error = $("#agent-action-error", detail);
        const idemKey = requestNonce();
        busy(button, async () => {
          await request(`${runsBase}/${run.id}/execute`, {
            key: idemKey,
            body: {
              currentBudget: Number(budgetVal),
              expectedHash: run.proposalHash || undefined,
              expectedRevision: run.proposalRevision || undefined
            }
          });
          toast("预算变更已写入幂等账本并执行成功");
          await refreshSafety();
          if (!current() || selectedId !== run.id) return;
          await refreshRuns();
          await select(run.id);
        }, message => error?.isConnected ? errorIn(error, message) : toast(message));
      };

      const execBtn = $("#btn-execute-budget", detail);
      if (execBtn && simulation) {
        execBtn.onclick = () => runExecute(execBtn, formValues(simulation).budget);
      }
      const execOnlyForm = $("#execute-only-form", detail);
      if (execOnlyForm) {
        execOnlyForm.onsubmit = event => {
          event.preventDefault();
          runExecute($("button", execOnlyForm), formValues(execOnlyForm).budget);
        };
      }
      const rollbackBtn = $("#btn-rollback-budget", detail);
      if (rollbackBtn) {
        rollbackBtn.onclick = () => {
          const error = $("#agent-action-error", detail);
          busy(rollbackBtn, async () => {
            await request(`${runsBase}/${run.id}/rollback`, { body: { reason: "控制台一键回滚预算" } });
            toast("已执行 Saga 回滚并恢复执行前预算");
            await refreshSafety();
            if (!current() || selectedId !== run.id) return;
            await refreshRuns();
            await select(run.id);
          }, message => error?.isConnected ? errorIn(error, message) : toast(message));
        };
      }

      const reStd = $("#reanalyze-standard", detail);
      const reDeep = $("#reanalyze-deep", detail);
      const runReanalyze = (button, mode) => busy(button, async () => {
        const updated = await request(`${runsBase}/${run.id}/reanalyze`, { body: { mode } });
        toast(mode === "DEEP" ? "已启动深度分析" : "已启动重新分析");
        await refreshRuns();
        await paint(updated);
      });
      if (reStd) reStd.onclick = () => runReanalyze(reStd, "STANDARD");
      if (reDeep) reDeep.onclick = () => runReanalyze(reDeep, "DEEP");

      const askBtn = $("#ask-copilot-run", detail);
      if (askBtn) askBtn.onclick = () => busy(askBtn, async () => {
        const detailSession = await request(`${chatBase}/sessions`, {
          body: { title: `追问 · ${runTitle(run)}`, campaignId: run.plan?.input?.campaignId || "", runId: run.id }
        });
        selectedSessionId = detailSession.session.id;
        setTab("copilot");
      });
    }

    $("#run-filter", tabBody).onchange = event => { filter = event.currentTarget.value; showHistory(); };
    $("#ai-only-toggle", tabBody).onchange = event => { aiOnlyFilter = event.currentTarget.checked; showHistory(); };

    try {
      await refreshRuns();
      if (!current() || activeTab !== "workflow") return;
      showHistory();
      if (runs.length && (!focused || focused.id !== selectedId)) {
        await select(runs.find(run => run.id === selectedId)?.id || runs[0].id);
      }
    } catch (error) {
      if (current() && activeTab === "workflow") errorIn(history, error.message);
    }
  }

  // =========================================================================
  // Tab 1.5: 五智能体闭环（Monitor / Audience / Creative / Bidding / Optimize）
  // =========================================================================
  async function renderFiveAgentTab() {
    tabBody.innerHTML = `<section class="panel">
      <div class="panel-head">
        <div>
          <h2>五智能体闭环优化引擎</h2>
          <p class="sub">监控评分 (0-100) → 受众分层与 Lookalike → 创意变体生成 → eCPM 动态出价 (≤80% TargetCPA) → 凸优化预算再分配与 A/B 闭环控制。</p>
        </div>
      </div>
      <form id="five-agent-form" class="toolbar catalog-search">
        <label class="publisher-field">目标广告活动<select name="campaignId"><option value="">正在加载活动…</option></select></label>
        <label>目标 ROAS<input type="number" name="targetRoas" step="0.05" min="0.1" value="1.25" required></label>
        <label>预算调幅上限 (%)<input type="number" name="maxChangePercent" step="1" min="5" max="20" value="15" required></label>
        <button type="submit" class="button">运行五智能体闭环</button>
      </form>
      <div id="five-agent-output"><div class="placeholder">正在初始化五智能体闭环分析…</div></div>
    </section>`;

    const form = $("#five-agent-form", tabBody);
    const output = $("#five-agent-output", tabBody);
    const select = form.elements.campaignId;

    try {
      const list = await requestList(`${agentBase}/campaigns?limit=40`);
      if (!current() || activeTab !== "five-agent") return;
      select.innerHTML = list.length
        ? list.map(c => `<option value="${e(c.campaignId)}">${e(c.campaignId)} · ${e(c.platform || "")} · ROAS ${e(multiple(c.roas))} · ${e(c.name)}</option>`).join("")
        : '<option value="">默认活动</option>';
    } catch {
      select.innerHTML = '<option value="">默认活动</option>';
    }

    function paintFiveAgent(data) {
      const m = data.monitor || {};
      const a = data.audience || {};
      const cr = data.creative || {};
      const b = data.bidding || {};
      const o = data.optimize || {};
      const camp = data.campaign || {};

      const scoreTone = m.healthScore >= 75 ? "teal" : m.healthScore >= 50 ? "amber" : "red";
      const pmTone = o.processMonitorStatus === "HEALTHY" ? "teal" : o.processMonitorStatus === "WATCH" ? "amber" : "red";

      const pipelineStrip = `<div class="five-agent-strip">
        <div class="agent-node done"><strong>1. Monitor 监控</strong><small>健康度 ${e(m.healthScore)}/100 (${e(m.grade)}级)</small></div>
        <div class="agent-node done"><strong>2. Audience 受众</strong><small>${(a.topSegments || []).length} 分层 · 3 档 Lookalike</small></div>
        <div class="agent-node done"><strong>3. Creative 创意</strong><small>${(cr.variants || []).length} 条文案变体</small></div>
        <div class="agent-node done"><strong>4. Bidding 出价</strong><small>倍率 ×${e(b.dynamicMultiplier)} · $${e(b.recommendedBid)}</small></div>
        <div class="agent-node ${o.shouldContinue ? "reflect" : "done"}"><strong>5. Optimize 闭环</strong><small>迭代 ${e(o.currentIteration)}/${e(o.maxIterations)} · 凸优化</small></div>
      </div>`;

      const monitorSection = `<section class="panel">
        <div class="panel-head">
          <div><h3>1. MonitorAgent · 实时监控与健康度评分</h3><p class="sub">${e(m.summary || "")}</p></div>
          <span class="badge ${scoreTone}">健康分 ${e(m.healthScore)} / 100 (${e(m.grade)} 级)</span>
        </div>
        <div class="cards compact-cards">
          ${metric("CTR", `${m.ctrPct ?? 0}%`, "警戒线 < 0.5%")}
          ${metric("CVR", `${m.cvrPct ?? 0}%`, "转化承接")}
          ${metric("CPA", `$${m.cpa ?? 0}`, `目标 ≤ $${m.targetCpa ?? 0}`)}
          ${metric("ROAS", `${m.roas ?? 0}×`, "警戒线 < 1.0×")}
        </div>
        ${(m.alerts || []).length
          ? `<ul class="findings">${m.alerts.map(al => `<li><span class="badge ${al.severity === "HIGH" ? "red" : "amber"}">${e(al.code)}</span><span>${e(al.message)}</span></li>`).join("")}</ul>`
          : '<p class="sub">当前所有监控指标均处于健康区间，无异常告警。</p>'}
      </section>`;

      const biddingSection = `<section class="panel">
        <div class="panel-head">
          <div><h3>4. BiddingAgent · eCPM 实时出价策略</h3><p class="sub">${e(b.multiplierReason || "")}</p></div>
          <span class="badge ${b.capApplied ? "amber" : "teal"}">${b.capApplied ? "已触发 80% TargetCPA 硬上限" : "在 80% TargetCPA 安全内"}</span>
        </div>
        <div class="cards compact-cards">
          ${metric("基准 eCPM", `$${b.baseEcpm ?? 0}`, `pCTR ${((b.pCtr || 0) * 100).toFixed(2)}% × pCVR ${((b.pCvr || 0) * 100).toFixed(2)}%`)}
          ${metric("动态倍率", `×${b.dynamicMultiplier ?? 1}`, "好×1.3 / 差×0.6")}
          ${metric("建议出价", `$${b.recommendedBid ?? 0}`, `上限 $${b.maxAllowedBid ?? 0} (80%)`)}
          ${metric("调后 eCPM", `$${b.adjustedEcpm ?? 0}`, `TargetCPA $${b.targetCpa ?? 0}`)}
        </div>
        <div class="login-hint-box"><strong>核心算法校验</strong><p><code>${e(b.formulaText || "")}</code></p></div>
      </section>`;

      const creativeSection = `<section class="panel">
        <div class="panel-head">
          <div>
            <h3>3. CreativeAgent · 广告文案变体自动生成 (${(cr.variants || []).length} 条)</h3>
            <p class="sub">策略：<strong>${e(cr.strategy || "")}</strong> · ${e(cr.triggerReason || "")}</p>
          </div>
          <span class="badge ${cr.source === "LLM_GENERATED" ? "teal" : "gray"}">${cr.source === "LLM_GENERATED" ? "AI 大模型实时生成" : "确定性策略模板"}</span>
        </div>
        <div class="creative-variant-grid">
          ${(cr.variants || []).map(v => `<article class="creative-card">
            <div class="signal-top">
              <span class="badge teal">${e(v.emotion)}</span>
              <span class="badge gray mono">${e(v.id)} · 预估 ${e(v.predictedScore)}分</span>
            </div>
            <h4>${e(v.headline)}</h4>
            <p class="sub">${e(v.description)}</p>
            <div class="creative-card-foot">
              <span class="badge">${e(v.cta)}</span>
              <small class="muted">${e(v.angle)}</small>
            </div>
          </article>`).join("")}
        </div>
      </section>`;

      const audienceSection = `<section class="panel">
        <div class="panel-head">
          <div><h3>2. AudienceAgent · 高价值人群分段排名 &amp; Lookalike 扩量</h3><p class="sub">${e(a.summary || "")}</p></div>
        </div>
        ${table(
          ["排名", "人群分段", "设备偏好", "花费", "转化", "CPA", "ROAS", "价值分", "定向建议"],
          (a.topSegments || []).map(s => [
            `<strong class="mono">#${e(s.rank)}</strong>`,
            `<strong>${e(s.segmentName)}</strong>`,
            e(s.deviceHint),
            `$${Number(s.spend || 0).toFixed(2)}`,
            numeric(s.conversions || 0),
            `$${Number(s.cpa || 0).toFixed(2)}`,
            roasBadge(s.roas),
            `<span class="badge ${s.valueScore >= 70 ? "teal" : s.valueScore >= 50 ? "amber" : "red"}">${e(s.valueScore)}</span>`,
            e(s.recommendation)
          ]), "暂无受众分段数据"
        )}
        <h4 class="sub-heading">Lookalike 相似受众扩展计划</h4>
        ${table(
          ["扩量层级", "种子人群来源", "预估覆盖规模", "出价与预算策略"],
          (a.lookalikePlans || []).map(p => [
            `<strong>${e(p.tier)}</strong>`, e(p.seedAudience), e(p.estimatedReach), e(p.bidStrategy)
          ]), ""
        )}
      </section>`;

      const optimizeSection = `<section class="panel">
        <div class="panel-head">
          <div>
            <h3>5. OptimizeAgent · 闭环优化执行（低效素材暂停 + 凸优化预算再分配 + ProcessMonitor + A/B 启停）</h3>
            <p class="sub">闭环状态：<strong>${e(o.loopReason || "")}</strong></p>
          </div>
          <div class="action-set">
            <span class="badge ${pmTone}">ProcessMonitor: ${e(o.processMonitorStatus)}</span>
            ${canCreate && camp.campaignId ? `<button type="button" class="button small" id="btn-five-to-approval">一键生成正式审批工单</button>` : ""}
            <button type="button" class="button secondary small" id="btn-export-convex">导出凸优化分配方案 CSV</button>
          </div>
        </div>
        <div class="result-toolbar">
          <p class="sub">${e(o.processMonitorNote || "")}</p>
          <span class="badge teal">总日预算守恒：$${Number(o.totalCurrentBudget || 0).toFixed(2)} → $${Number(o.totalOptimalBudget || 0).toFixed(2)} · 预计收入提升 +${Number(o.expectedRevenueUpliftPct || 0).toFixed(2)}%</span>
        </div>

        <h4 class="sub-heading">凸优化多活动预算再分配（Projected KKT Water-Filling · 严格遵守学习期上下界）</h4>
        ${table(
          ["活动 ID", "活动名称", "平台", "历史 ROAS", "当前日预算", "凸优化建议预算", "预算变动", "变动幅度", "动作"],
          (o.budgetAllocations || []).map(al => [
            `<code class="mono">${e(al.campaignId)}</code>`,
            `<span class="clamp">${e(al.name)}</span>`,
            e(al.platform),
            roasBadge(al.historicalRoas),
            `$${Number(al.currentBudget || 0).toFixed(2)}`,
            `<strong>$${Number(al.optimalBudget || 0).toFixed(2)}</strong>`,
            profitDisplay(al.deltaBudget, "$"),
            `${al.deltaPct > 0 ? "+" : ""}${Number(al.deltaPct || 0).toFixed(1)}%`,
            `<span class="badge ${al.action === "SCALE" ? "teal" : al.action === "REDUCE" ? "amber" : "gray"}">${e(al.action)}</span>`
          ]), "暂无可分配活动"
        )}

        <div class="five-agent-two-col">
          <div>
            <h4 class="sub-heading">低效素材评分审核（综合评分 &lt; 40 分自动建议暂停）</h4>
            ${table(
              ["素材名称", "展示", "点击", "CTR", "综合分", "动作"],
              (o.pausedCreatives || []).map(c => [
                e(c.creativeName), numeric(c.impressions), numeric(c.clicks), `${c.ctrPct}%`,
                `<span class="badge ${c.score < 40 ? "red" : "teal"}">${e(c.score)}</span>`,
                `<span class="badge ${c.action === "PAUSE" ? "red" : "teal"}">${c.action === "PAUSE" ? "暂停 (PAUSE)" : "保留 (KEEP)"}</span>`
              ]), "暂无素材明细"
            )}
          </div>
          <div>
            <h4 class="sub-heading">A/B 测试启停管理</h4>
            ${table(
              ["实验编号", "建议动作", "决策依据"],
              (o.abTestActions || []).map(t => [
                `<strong>${e(t.testName)}</strong>`,
                `<span class="badge ${t.action === "START" ? "teal" : t.action === "STOP" ? "red" : "gray"}">${e(t.action)}</span>`,
                `<span class="clamp">${e(t.rationale)}</span>`
              ]), "暂无实验变更"
            )}
          </div>
        </div>
      </section>`;

      output.innerHTML = pipelineStrip + monitorSection + audienceSection + creativeSection + biddingSection + optimizeSection;

      const exportBtn = $("#btn-export-convex", output);
      if (exportBtn) {
        exportBtn.onclick = () => {
          const headers = ["活动ID", "活动名称", "平台", "历史ROAS", "当前日预算", "凸优化预算", "预算变动", "变动幅度(%)", "建议动作"];
          const rows = (o.budgetAllocations || []).map(al => [
            al.campaignId, al.name, al.platform, al.historicalRoas,
            al.currentBudget, al.optimalBudget, al.deltaBudget, al.deltaPct, al.action
          ]);
          exportCsv(`五智能体凸优化预算方案-${camp.campaignId || "portfolio"}.csv`, headers, rows);
          toast("已导出凸优化预算再分配方案 CSV");
        };
      }
      const handoffBtn = $("#btn-five-to-approval", output);
      if (handoffBtn && camp.campaignId) {
        handoffBtn.onclick = () => busy(handoffBtn, async () => {
          const run = await request(`${runsBase}/campaigns/${encodeURIComponent(camp.campaignId)}`, {
            body: { mode: "DEEP", targetRoas: Number(formValues(form).targetRoas) || 1.25 }
          });
          toast(`已为 ${camp.campaignId} 启动深度智能分析并转入审批队列`);
          selectedId = run.id;
          workflowViewMode = "list";
          await refreshRuns();
          setTab("workflow");
        });
      }
    }

    async function runFiveAgent(notify = false) {
      const vals = formValues(form);
      const data = await request(`${agentBase}/five-agent/run`, {
        body: {
          campaignId: vals.campaignId || undefined,
          targetRoas: Number(vals.targetRoas) || 1.25,
          maxChangePercent: Number(vals.maxChangePercent) || 15,
          useLlmForCreative: true,
          iteration: 1
        }
      });
      activeFiveAgentResult = data;
      if (current() && activeTab === "five-agent") {
        paintFiveAgent(data);
        if (notify) {
          toast(`五智能体闭环完成：健康分 ${data.monitor?.healthScore}/100 · 生成 ${(data.creative?.variants || []).length} 条文案 · 建议出价 $${data.bidding?.recommendedBid}`);
        }
      }
    }

    form.onsubmit = event => {
      event.preventDefault();
      busy($('button[type="submit"]', form), () => runFiveAgent(true), msg => errorIn(output, msg));
    };

    if (activeFiveAgentResult) paintFiveAgent(activeFiveAgentResult);
    else runFiveAgent(false).catch(err => { if (current() && activeTab === "five-agent") errorIn(output, err.message); });
  }

  // =========================================================================
  // Tab 2: 智能副驾驶（含专家技能包 Playbooks + 行动化诊断卡片）
  // =========================================================================
  async function renderCopilotTab() {
    let activePlaybookId = "default";
    tabBody.innerHTML = `<div class="agent-grid copilot-layout">
      <section class="panel">
        <div class="panel-head">
          <h2>对话记录</h2>
          <button type="button" class="button small" id="new-chat-session">+ 新对话</button>
        </div>
        <div id="chat-session-list"><div class="placeholder">正在加载对话…</div></div>
      </section>
      <section class="panel copilot-panel">
        <div class="panel-head">
          <div><h2 id="chat-session-title">智能副驾驶</h2>
            <p class="sub" id="chat-session-sub">结合实时投放数据，支持专家技能包切换、全盘复盘、活动诊断与一键发起审批。</p></div>
        </div>
        <div class="playbook-bar" id="copilot-playbooks" aria-label="专家技能包">
          <span class="playbook-label">专家模式：</span>
          ${COPILOT_PLAYBOOKS.map(pb => `<button type="button" class="playbook-chip" data-playbook="${e(pb.id)}" aria-pressed="${pb.id === activePlaybookId}"><span>${e(pb.name)}</span><small>${e(pb.badge)}</small></button>`).join("")}
        </div>
        <div class="quick-prompts">${copilotQuickPrompts.map(p =>
          `<button type="button" class="chip-btn" data-quick-prompt="${e(p)}">${e(p)}</button>`).join("")}</div>
        <div class="chat-messages" id="chat-messages"><div class="placeholder">选择左侧对话，或直接输入问题开始。</div></div>
        <form class="chat-composer" id="chat-form">
          <textarea name="content" rows="2" required maxlength="4000" placeholder="例如：分析近 14 天 ROAS 最低的 3 个活动并给出止损建议…"></textarea>
          <button type="submit" class="button" id="chat-send">发送</button>
        </form>
        <div id="chat-error"></div>
      </section>
    </div>`;

    const sessionListEl = $("#chat-session-list", tabBody);
    const messagesEl = $("#chat-messages", tabBody);
    const titleEl = $("#chat-session-title", tabBody);
    const subEl = $("#chat-session-sub", tabBody);
    const form = $("#chat-form", tabBody);
    const errorEl = $("#chat-error", tabBody);

    function paintSessionList() {
      if (!chatSessions.length) {
        sessionListEl.innerHTML = '<div class="placeholder">暂无对话，点击「+ 新对话」或直接提问。</div>';
        return;
      }
      sessionListEl.innerHTML = `<ul class="history">${chatSessions.map(s => `
        <li class="chat-session-row">
          <button type="button" data-chat-session="${e(s.id)}" aria-pressed="${s.id === selectedSessionId}">
            <span><strong>${e(s.title)}</strong><small>${e(s.campaignId ? `活动 ${s.campaignId} · ` : "")}${e(dateTime(s.updatedAt))}</small></span>
          </button>
          <button type="button" class="icon-mini" title="删除对话" data-delete-session="${e(s.id)}">×</button>
        </li>`).join("")}</ul>`;
      sessionListEl.querySelectorAll("[data-chat-session]").forEach(btn => {
        btn.onclick = () => selectSession(btn.dataset.chatSession);
      });
      sessionListEl.querySelectorAll("[data-delete-session]").forEach(btn => {
        btn.onclick = () => busy(btn, async () => {
          const id = btn.dataset.deleteSession;
          await request(`${chatBase}/sessions/${encodeURIComponent(id)}`, { method: "DELETE" });
          if (selectedSessionId === id) selectedSessionId = undefined;
          await loadSessions();
        });
      });
    }

    function messageMarkup(msg) {
      const isUser = msg.role === "user";
      const meta = msg.meta || {};
      const metaParts = [];
      if (meta.toolCalls) metaParts.push(`已查询 ${meta.toolCalls} 项数据`);
      if (meta.durationMs) metaParts.push(`${(meta.durationMs / 1000).toFixed(1)} 秒`);
      const uniqueTools = Array.isArray(meta.tools) && meta.tools.length
        ? [...new Set(meta.tools.map(formatToolName))] : [];
      const toolsLine = uniqueTools.length
        ? `<div class="chat-tools-used">数据来源：${uniqueTools.map(t => `<code>${e(t)}</code>`).join(" ")}</div>` : "";
      const mentioned = !isUser ? extractMentionedCampaignIds(msg.content || "") : [];
      const actionLine = mentioned.length
        ? `<div class="chat-action-chips">${mentioned.map(cid => `<button type="button" class="chip-btn action-chip" data-copilot-diag="${e(cid)}">✦ 发起诊断 ${e(cid)}</button>`).join("")}</div>` : "";
      return `<article class="chat-bubble ${isUser ? "user" : "assistant"}">
        <div class="chat-role">${isUser ? "我" : "智能副驾驶"}</div>
        <div class="analysis-text">${isUser ? `<p>${e(msg.content)}</p>` : renderMarkdown(msg.content || "")}</div>
        ${actionLine}
        ${toolsLine}
        ${metaParts.length ? `<div class="chat-meta">${e(metaParts.join(" · "))}</div>` : ""}
      </article>`;
    }

    async function selectSession(id) {
      selectedSessionId = id;
      paintSessionList();
      messagesEl.innerHTML = '<div class="placeholder">正在加载对话记录…</div>';
      try {
        const detail = await request(`${chatBase}/sessions/${encodeURIComponent(id)}`);
        if (!current() || activeTab !== "copilot") return;
        titleEl.textContent = detail.session.title;
        subEl.textContent = detail.session.campaignId
          ? `当前关联活动：${detail.session.campaignId}`
          : "结合实时投放数据，支持全盘复盘、活动诊断与发起分析工单。";
        messagesEl.innerHTML = detail.messages.length
          ? detail.messages.map(messageMarkup).join("")
          : '<div class="placeholder">点击上方快捷问题或直接输入您的问题。</div>';
        messagesEl.scrollTop = messagesEl.scrollHeight;
      } catch (error) {
        errorIn(messagesEl, error.message);
      }
    }

    async function loadSessions() {
      chatSessions = await requestList(`${chatBase}/sessions`);
      if (!current() || activeTab !== "copilot") return;
      paintSessionList();
      if (chatSessions.length) {
        const targetId = chatSessions.find(s => s.id === selectedSessionId)?.id || chatSessions[0].id;
        await selectSession(targetId);
      } else {
        messagesEl.innerHTML = '<div class="placeholder">点击上方快捷问题或直接发送消息开始对话。</div>';
      }
    }

    $("#new-chat-session", tabBody).onclick = event => busy(event.currentTarget, async () => {
      const created = await request(`${chatBase}/sessions`, { body: {} });
      selectedSessionId = created.session.id;
      await loadSessions();
    });

    tabBody.querySelectorAll("[data-playbook]").forEach(btn => {
      btn.onclick = () => {
        activePlaybookId = btn.dataset.playbook || "default";
        tabBody.querySelectorAll("[data-playbook]").forEach(b => {
          b.setAttribute("aria-pressed", String(b.dataset.playbook === activePlaybookId));
        });
        const pb = COPILOT_PLAYBOOKS.find(p => p.id === activePlaybookId);
        if (pb) {
          form.elements.content.placeholder = pb.samplePrompt;
          if (!form.elements.content.value.trim()) form.elements.content.value = pb.samplePrompt;
          toast(`已切换专家技能包：${pb.name}`);
        }
      };
    });

    messagesEl.onclick = (ev) => {
      const btn = (ev.target as HTMLElement)?.closest("[data-copilot-diag]") as HTMLButtonElement | null;
      if (btn && btn.dataset.copilotDiag) {
        quickDiagnoseFromAgent(btn.dataset.copilotDiag, btn);
      }
    };

    tabBody.querySelectorAll("[data-quick-prompt]").forEach(btn => {
      btn.onclick = () => {
        form.elements.content.value = btn.dataset.quickPrompt;
        form.requestSubmit();
      };
    });

    form.elements.content.addEventListener("keydown", event => {
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        form.requestSubmit();
      }
    });

    form.onsubmit = event => {
      event.preventDefault();
      const text = form.elements.content.value.trim();
      if (!text) return;
      errorEl.replaceChildren();
      busy($("#chat-send", form), async () => {
        if (!selectedSessionId) {
          const created = await request(`${chatBase}/sessions`, { body: {} });
          selectedSessionId = created.session.id;
        }
        form.elements.content.value = "";
        if ($(".placeholder", messagesEl)) messagesEl.innerHTML = "";
        messagesEl.insertAdjacentHTML("beforeend", messageMarkup({ role: "user", content: text }));
        const liveId = "live-reply-" + Date.now();
        messagesEl.insertAdjacentHTML("beforeend", `<article class="chat-bubble assistant" id="${liveId}">
          <div class="chat-role">智能副驾驶 · 正在分析…</div>
          <div class="live-tool-feed"></div>
          <div class="analysis-text"><p class="muted">正在检索实时投放数据…</p></div>
        </article>`);
        messagesEl.scrollTop = messagesEl.scrollHeight;
        const liveNode = document.getElementById(liveId);
        const toolFeed = $(".live-tool-feed", liveNode);
        const bodySlot = $(".analysis-text", liveNode);
        let receivedAnswer = false;

        try {
          const activePb = COPILOT_PLAYBOOKS.find(p => p.id === activePlaybookId);
          const payloadText = activePb && activePb.instruction ? `${activePb.instruction}\n\n用户问题：${text}` : text;
          await streamRequest(`${chatBase}/sessions/${encodeURIComponent(selectedSessionId)}/stream`, {
            body: { content: payloadText },
            onEvent: (eventName, payload) => {
              if (!liveNode.isConnected) return;
              if (eventName === "tool_call" && payload?.name) {
                toolFeed.insertAdjacentHTML("beforeend", `<span class="tool-pill active">正在查询：${e(formatToolName(payload.name))}</span>`);
              } else if (eventName === "tool_result" && payload?.name) {
                toolFeed.insertAdjacentHTML("beforeend", `<span class="tool-pill done">✓ ${e(formatToolName(payload.name))}</span>`);
              } else if (eventName === "answer" && payload?.content != null) {
                receivedAnswer = true;
                bodySlot.innerHTML = renderMarkdown(payload.content);
              } else if (eventName === "error" && payload?.message) {
                errorIn(errorEl, payload.message);
              }
              messagesEl.scrollTop = messagesEl.scrollHeight;
            }
          });
        } catch {
          if (!receivedAnswer) {
            const fallback = await request(`${chatBase}/sessions/${encodeURIComponent(selectedSessionId)}/messages`, {
              body: { content: text }
            });
            bodySlot.innerHTML = renderMarkdown(fallback.content || "");
          }
        }
        await loadSessions();
      }, message => errorIn(errorEl, message));
    };

    try {
      await loadSessions();
    } catch (error) {
      if (current() && activeTab === "copilot") errorIn(sessionListEl, error.message);
    }
  }

  // =========================================================================
  // Tab 3: 异常巡检
  // =========================================================================
  async function renderScanTab() {
    tabBody.innerHTML = `<section class="panel">
      <div class="panel-head">
        <div><h2>全盘投放异常巡检</h2><p class="sub">扫描所有在投活动的 ROAS、亏损幅度、放量机会与规则护栏覆盖情况，支持一键发起深度分析。</p></div>
      </div>
      <form id="scan-form" class="toolbar catalog-search">
        <label>目标 ROAS<input type="number" name="targetRoas" step="0.05" min="0.1" value="1.25" required></label>
        <label>最低花费过滤 (USD)<input type="number" name="minimumSpend" step="1" min="0" value="30" required></label>
        <button type="submit" class="button">立即巡检</button>
      </form>
      <div id="scan-output"><div class="placeholder">正在执行全盘巡检…</div></div>
    </section>`;

    const form = $("#scan-form", tabBody);
    const output = $("#scan-output", tabBody);

    function paintScan(data) {
      const counts = data.counts || {};
      const cards = `<div class="cards">
        ${metric("急需止损", numeric(counts.criticalBleed || 0), `ROAS < ${(data.targetRoas * 0.7).toFixed(2)}×`)}
        ${metric("回收偏低", numeric(counts.underperforming || 0), `ROAS < ${data.targetRoas}×`)}
        ${metric("放量机会", numeric(counts.scaleOpportunity || 0), `ROAS ≥ ${(data.targetRoas * 1.25).toFixed(2)}×`)}
        ${metric("缺规则护栏", numeric(counts.ruleUnbound || 0), `已扫描 ${data.scannedCampaigns || 0} 个活动`)}
      </div>`;
      const items = data.items || [];
      const rows = items.map(item => [
        severityBadges[item.severity] || e(item.severity),
        e(categoryLabels[item.category] || item.category),
        `<strong>${e(item.campaignId)}</strong><br><small class="muted">${e(item.name || "")}</small>`,
        e(item.platform || "—"),
        e(money(item.spend, "USD")),
        e(money(item.revenue, "USD")),
        e(multiple(item.roas)),
        `<span class="clamp">${e(item.diagnosis)}</span>`,
        canCreate
          ? `<button type="button" class="button small" data-scan-analyze="${e(item.campaignId)}">发起深度分析</button>`
          : "—"
      ]);
      const scanTime = new Date().toLocaleTimeString("zh-CN", { hour12: false });
      const toolbar = `<div class="result-toolbar">
        <p class="sub">巡检完成于 <strong>${e(scanTime)}</strong> · 目标 ROAS ${e(data.targetRoas)}× · 最低花费 $${e(data.minimumSpend)} · 共扫描 ${numeric(data.scannedCampaigns || 0)} 个活动，检出 ${items.length} 条重点项</p>
        <div class="action-set">
          ${items.length ? '<button type="button" class="button small" id="btn-ai-scan-interpret">✦ AI 深度解读巡检结果</button>' : ""}
          ${items.length ? '<button type="button" class="button secondary small" id="btn-export-scan">导出巡检结果 CSV</button>' : ""}
        </div>
      </div>
      <div id="scan-ai-box"></div>`;

      output.innerHTML = toolbar + cards + table(["等级", "分类", "活动", "平台", "花费", "收入", "ROAS", "诊断摘要", "操作"],
        rows, "当前阈值下未发现异常或放量项，可调整目标 ROAS 或最低花费重试。");

      const aiScanBtn = $("#btn-ai-scan-interpret", output);
      if (aiScanBtn) {
        aiScanBtn.onclick = () => busy(aiScanBtn, async () => {
          const box = $("#scan-ai-box", output);
          box.innerHTML = '<div class="placeholder">正在调用大模型深度解读全盘巡检数据…</div>';
          const digest = `扫描活动数=${data.scannedCampaigns}, 急需止损=${counts.criticalBleed || 0}, 回收偏低=${counts.underperforming || 0}, 放量机会=${counts.scaleOpportunity || 0}, 缺规则护栏=${counts.ruleUnbound || 0}\n重点活动前8条：\n`
            + items.slice(0, 8).map(it => `- [${it.category}] ${it.name || it.campaignId} (${it.platform}): 花费=$${it.spend}, 收入=$${it.revenue}, ROAS=${it.roas}x`).join("\n");
          const res = await request(`${agentBase}/ext/scan-interpret`, { body: { contextText: digest } });
          box.innerHTML = `<section class="memory-lessons">
            <h3>✦ AI 巡检深度诊断报告 ${res.aiPowered ? '<span class="badge teal">AI 实时生成</span>' : ""}</h3>
            <div class="analysis-text">${renderMarkdown(res.analysis || "")}</div>
          </section>`;
        });
      }

      const exportBtn = $("#btn-export-scan", output);
      if (exportBtn) {
        exportBtn.onclick = () => {
          const headers = ["等级", "分类", "活动ID", "活动名称", "平台", "花费(USD)", "收入(USD)", "ROAS", "诊断摘要"];
          const csvRows = items.map(item => [
            item.severity, categoryLabels[item.category] || item.category,
            item.campaignId, item.name || "", item.platform || "",
            Number(item.spend || 0).toFixed(2), Number(item.revenue || 0).toFixed(2),
            `${Number(item.roas || 0).toFixed(2)}x`, item.diagnosis || ""
          ]);
          exportCsv(`全盘异常巡检-${new Date().toISOString().slice(0, 10)}.csv`, headers, csvRows);
          toast(`已导出 ${items.length} 条巡检记录（CSV）`);
        };
      }

      output.querySelectorAll("[data-scan-analyze]").forEach(btn => {
        btn.onclick = () => busy(btn, async () => {
          const cid = btn.dataset.scanAnalyze;
          const run = await request(`${runsBase}/campaigns/${encodeURIComponent(cid)}`, {
            body: { mode: "DEEP", targetRoas: Number(formValues(form).targetRoas) || 1.25 }
          });
          toast(`已为 ${cid} 启动深度分析`);
          selectedId = run.id;
          await refreshRuns();
          setTab("workflow");
        });
      });
    }

    async function runScan(notify = false) {
      const vals = formValues(form);
      const data = await request(`${agentBase}/scan`, {
        body: { targetRoas: Number(vals.targetRoas) || 1.25, minimumSpend: Number(vals.minimumSpend) || 30 }
      });
      activeScanResult = data;
      if (current() && activeTab === "scan") {
        paintScan(data);
        if (notify) {
          const c = data.counts || {};
          toast(`巡检完成：扫描 ${data.scannedCampaigns || 0} 个活动（急需止损 ${c.criticalBleed || 0} · 放量机会 ${c.scaleOpportunity || 0} · 缺规则 ${c.ruleUnbound || 0}）`);
        }
      }
    }

    form.onsubmit = event => {
      event.preventDefault();
      busy($("button", form), () => runScan(true), msg => errorIn(output, msg));
    };

    if (activeScanResult) paintScan(activeScanResult);
    else runScan(false).catch(err => { if (current() && activeTab === "scan") errorIn(output, err.message); });
  }

  // =========================================================================
  // Tab 4: 离线回放评估（真实历史数据反事实回放 + 配对 t 检验 + 4-Gate 上线门禁）
  // =========================================================================
  async function renderReplayTab() {
    tabBody.innerHTML = `<section class="panel">
      <div class="panel-head">
        <div>
          <h2>历史数据离线回放与 4-Gate 上线门禁</h2>
          <p class="sub">使用真实历史日度投放数据，对比「确定性 8 信号规则引擎决策 vs 历史人工基线」的净利润、ROAS 与统计显著性（配对 t 检验）。</p>
        </div>
      </div>
      <form id="replay-form" class="toolbar catalog-search">
        <label>目标 ROAS<input type="number" name="targetRoas" step="0.05" min="0.1" value="1.25" required></label>
        <label>最低花费样本 (USD)<input type="number" name="minimumSpend" step="5" min="0" value="30" required></label>
        <label>单次调幅上限 (%)<input type="number" name="maxChangePercent" step="1" min="1" max="20" value="10" required></label>
        <label>评估活动上限<input type="number" name="limit" step="5" min="5" max="80" value="40" required></label>
        <button type="submit" class="button">运行离线回放</button>
      </form>
      <div id="replay-output"><div class="placeholder">正在加载真实历史日度数据并执行配对回放评估…</div></div>
    </section>`;

    const form = $("#replay-form", tabBody);
    const output = $("#replay-output", tabBody);

    function paintReplay(data) {
      const s = data.summary || {};
      const gates = data.gates || [];
      const items = data.items || [];
      const gateTone = status => status === "PASS" ? "teal" : status === "WARN" ? "amber" : "red";

      const gatesHtml = `<div class="gate-grid">${gates.map(g => `<article class="gate-card gate-${e(String(g.status || "PASS").toLowerCase())}">
        <div class="signal-top"><strong>${e(g.name)}</strong><span class="badge ${gateTone(g.status)}">${e(g.status)}</span></div>
        <p class="sub">${e(g.detail)}</p>
      </article>`).join("")}</div>`;

      const upliftSign = Number(s.profitUplift || 0) >= 0 ? "+" : "";
      const roasSign = Number(s.roasUplift || 0) >= 0 ? "+" : "";
      const sigLabel = s.statisticallySignificant
        ? `统计显著 (t=${s.pairedTStat}, p=${s.pValue} < 0.05)`
        : `t=${s.pairedTStat ?? 0}, p=${s.pValue ?? 1}`;

      const kpis = `<div class="cards">
        ${metric("评估活动总数", numeric(s.evaluatedCampaigns || 0), `放量 ${s.actionBreakdown?.INCREASE || 0} · 降预算 ${s.actionBreakdown?.REDUCE || 0} · 维持 ${s.actionBreakdown?.HOLD || 0}`)}
        ${metric("净利润回放提升", `${upliftSign}$${Number(s.profitUplift || 0).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, `基线 $${Number(s.baselineProfit || 0).toFixed(2)} → 智能体 $${Number(s.agentProfit || 0).toFixed(2)} (${upliftSign}${s.profitUpliftPct || 0}%)`)}
        ${metric("综合 ROAS 对比", `${Number(s.agentRoas || 0).toFixed(3)}×`, `人工基线 ${Number(s.baselineRoas || 0).toFixed(3)}× (${roasSign}${Number(s.roasUplift || 0).toFixed(3)}×)`)}
        ${metric("调优胜率 & 显著性", `${s.winRatePct || 0}%`, sigLabel)}
      </div>`;

      const toolbar = `<div class="result-toolbar">
        <p class="sub">${e(s.methodology || "")}</p>
        ${items.length ? '<button type="button" class="button secondary small" id="btn-export-replay">导出回放评估报告 CSV</button>' : ""}
      </div>`;

      const rows = items.map(item => [
        `<strong>${e(item.campaignId)}</strong><br><small class="muted">${e(item.name || "")}</small>`,
        e(item.platform || "—"),
        e(item.site || "—"),
        `<span class="badge ${item.agentAction === "INCREASE" ? "teal" : item.agentAction === "REDUCE" ? "amber" : "gray"}">${e(item.agentAction)} ${item.changePercent > 0 ? "+" : ""}${e(item.changePercent)}%</span>`,
        `<span class="mono">$${Number(item.baselineProfit || 0).toFixed(2)}</span><br><small class="muted">ROAS ${Number(item.baselineRoas || 0).toFixed(2)}×</small>`,
        `<span class="mono">$${Number(item.agentProfit || 0).toFixed(2)}</span><br><small class="muted">ROAS ${Number(item.agentRoas || 0).toFixed(2)}×</small>`,
        profitDisplay(item.profitDelta, "$"),
        item.changePercent === 0 ? '<span class="badge gray">维持一致</span>' : (item.agentImproved ? '<span class="badge teal">优于人工</span>' : '<span class="badge red">低于人工</span>')
      ]);

      output.innerHTML = gatesHtml + kpis + toolbar + table(
        ["活动", "平台", "站点", "智能体决策", "人工基线利润/ROAS", "智能体回放利润/ROAS", "净利润差值 (Uplift)", "配对结果"],
        rows, "暂无满足回放窗口的历史日度数据。");

      const exportBtn = $("#btn-export-replay", output);
      if (exportBtn) {
        exportBtn.onclick = () => {
          const headers = ["活动ID", "活动名称", "平台", "站点", "观察期", "验证期", "智能体决策", "调幅(%)",
            "人工花费", "人工收入", "人工利润", "人工ROAS", "回放花费", "回放收入", "回放利润", "回放ROAS", "利润差值(Uplift)"];
          const csvRows = items.map(i => [
            i.campaignId, i.name, i.platform, i.site, i.decisionWindow, i.outcomeWindow,
            i.agentAction, i.changePercent, i.baselineSpend, i.baselineRevenue, i.baselineProfit, i.baselineRoas,
            i.agentSpend, i.agentRevenue, i.agentProfit, i.agentRoas, i.profitDelta
          ]);
          csvRows.push(["SUMMARY", `汇总(${s.evaluatedCampaigns}个活动)`, "", "", "", "", `胜率${s.winRatePct}%`, `t=${s.pairedTStat},p=${s.pValue}`,
            s.baselineSpend, s.baselineRevenue, s.baselineProfit, s.baselineRoas,
            s.agentSpend, s.agentRevenue, s.agentProfit, s.agentRoas, s.profitUplift]);
          exportCsv(`离线回放评估报告-${new Date().toISOString().slice(0, 10)}.csv`, headers, csvRows);
          toast("已导出离线回放评估报告 CSV");
        };
      }
    }

    async function runReplay(notify = false) {
      const vals = formValues(form);
      const data = await request(`${agentBase}/replay`, {
        body: {
          targetRoas: Number(vals.targetRoas) || 1.25,
          minimumSpend: Number(vals.minimumSpend) || 30,
          maxChangePercent: Number(vals.maxChangePercent) || 10,
          limit: Number(vals.limit) || 40
        }
      });
      activeReplayResult = data;
      if (current() && activeTab === "replay") {
        paintReplay(data);
        if (notify) {
          const s = data.summary || {};
          toast(`离线回放完成：已评估 ${s.evaluatedCampaigns || 0} 个活动，净利润提升 ${Number(s.profitUplift || 0) >= 0 ? "+" : ""}$${Number(s.profitUplift || 0).toFixed(2)}`);
        }
      }
    }

    form.onsubmit = event => {
      event.preventDefault();
      busy($("button", form), () => runReplay(true), msg => errorIn(output, msg));
    };

    if (activeReplayResult) paintReplay(activeReplayResult);
    else runReplay(false).catch(err => { if (current() && activeTab === "replay") errorIn(output, err.message); });
  }

  // =========================================================================
  // Tab 5: 运行统计（面向运营的简洁业务统计，不暴露底层框架与模型路径）
  // =========================================================================
  async function renderObservabilityTab() {
    tabBody.innerHTML = '<div class="placeholder">正在加载运行统计…</div>';
    try {
      const data = await request(`${agentBase}/telemetry`);
      if (!current() || activeTab !== "observability") return;
      const totals = data.totals || {};
      const runStats = data.runs || {};
      const totalRuns = Number(runStats.totalRuns || 0);
      const aiRuns = Number(runStats.aiPoweredRuns || 0);
      const pendingRuns = Number(runStats.pendingRuns || 0);
      const approvedRuns = Number(runStats.approvedRuns || 0) + Number(runStats.simulatedRuns || 0);
      const totalToolCalls = Number(totals.totalToolCalls || 0);
      const avgDur = Math.round(Number(totals.avgNodeDurationMs || 0));

      const kpis = `<div class="cards">
        ${metric("智能分析单", `${numeric(aiRuns)} / ${numeric(totalRuns)}`, "已生成智能诊断占比")}
        ${metric("待审批建议", numeric(pendingRuns), `已通过/已试算 ${numeric(approvedRuns)} 条`)}
        ${metric("实时数据核验", numeric(totalToolCalls), "累计调用 CMS 业务数据次数")}
        ${metric("平均处理耗时", avgDur ? `${(avgDur / 1000).toFixed(2)} 秒` : "—", "单步骤平均响应")}
      </div>`;

      const byNodeTable = `<section class="panel"><h2>各分析环节效率</h2>${table(
        ["分析环节", "执行次数", "平均耗时", "调用数据次数"],
        (data.byNode || []).map(row => [
          e(nodeLabels[row.nodeName] || row.nodeName),
          e(numeric(row.executions)),
          e(`${Math.round(Number(row.avgDurationMs || 0))} ms`),
          e(numeric(row.toolCalls || 0))
        ]), "暂无运行统计，发起一次分析或副驾驶对话后即自动记录。"
      )}</section>`;

      const recentTable = `<section class="panel"><h2>最近运行记录</h2>${table(
        ["时间", "分析环节", "状态", "耗时", "说明"],
        (data.recentTraces || []).map(row => [
          e(dateTime(row.createdAt)),
          e(nodeLabels[row.nodeName] || row.nodeName),
          e(row.status === "OK" ? "完成" : row.status === "REFLECT" ? "复核修正" : row.status === "RECOVERED" ? "已恢复" : row.status),
          e(`${row.durationMs || 0} ms`),
          `<span class="clamp">${e(row.summary || "")}</span>`
        ]), "暂无运行记录。"
      )}</section>`;

      tabBody.innerHTML = kpis + byNodeTable + recentTable + '<div id="agent-attribution-mount"></div>';
      renderAttributionSection($("#agent-attribution-mount", tabBody));
    } catch (error) {
      if (current() && activeTab === "observability") errorIn(tabBody, error.message);
    }
  }

  // =========================================================================
  // 弹窗：新建分析（真实活动一键分析 + 手动汇总表单）
  // =========================================================================
  async function showCreated(run) {
    closeDialog();
    toast(run.plan?.explanation?.source === "PENDING"
      ? "分析已启动，完成后自动刷新"
      : "分析已生成（规则建议）");
    if (!current()) return;
    selectedId = run.id;
    filter = "";
    aiOnlyFilter = false;
    await refreshRuns();
    setTab("workflow");
  }

  if (canCreate) $("#new-analysis", target).onclick = async () => {
    let campaigns = [];
    try {
      campaigns = await requestList(`${agentBase}/campaigns?limit=50`);
    } catch { campaigns = []; }

    const hasCampaigns = campaigns.length > 0;
    const campaignForm = `<form id="campaign-analysis-form" class="form-grid operation-form">
      <label class="all">搜索活动<input type="search" id="campaign-picker-search" placeholder="输入活动 ID、名称、平台或站点过滤…"></label>
      <label class="all">选择在投活动（自动拉取逐日报表、国家拆分、创意与规则）
        <select name="campaignId" id="campaign-picker-select" required size="6">
          ${campaigns.map(c => `<option value="${e(c.campaignId)}">${e(c.campaignId)} · ${e(c.platform || "")} · 花费 ${e(money(c.spend, c.currency))} · ROAS ${e(multiple(c.roas))} · ${c.ruleBound ? "已绑规则" : "未绑规则"} · ${e(c.name)}</option>`).join("")}
        </select>
      </label>
      <label>分析深度
        <select name="mode">
          <option value="STANDARD">标准分析（快速）</option>
          <option value="DEEP">深度分析（多维归因与复核）</option>
        </select>
      </label>
      <label>目标 ROAS<input name="targetRoas" type="number" min="0.01" step="0.05" value="1.25" required></label>
      <label>最低花费样本<input name="minimumSpend" type="number" min="0.01" step="1" value="20" required></label>
      <label>最低点击样本<input name="minimumClicks" type="number" min="1" value="30" required></label>
      <label>最大预算调整 %<input name="maxChangePercent" type="number" min="1" max="20" value="10" required></label>
      <div class="all" id="create-campaign-error"></div>
      <div class="form-actions all"><button class="button" type="submit">开始分析</button></div>
    </form>`;

    const defaultRange = defaultDateRange(7);
    const customForm = `<form id="custom-analysis-form" class="form-grid operation-form" ${hasCampaigns ? "hidden" : ""}>
      ${inputFields.map(([name, label, attributes]) => {
        const extra = name === "start" ? ` value="${e(defaultRange.start)}"`
          : name === "end" ? ` value="${e(defaultRange.end)}"` : "";
        return `<label>${label}<input name="${name}" ${attributes}${extra}></label>`;
      }).join("")}
      <div class="all" id="create-custom-error"></div>
      <div class="form-actions all"><button class="button" type="submit">开始分析</button></div>
    </form>`;

    const modeSwitcher = hasCampaigns
      ? `<div class="catalog-tabs mode-switcher">
           <button type="button" class="button secondary small" id="mode-tab-campaign" aria-pressed="true">从在投活动一键分析（推荐）</button>
           <button type="button" class="button secondary small" id="mode-tab-custom" aria-pressed="false">手动输入汇总指标</button>
         </div>` : "";

    const content = dialog("新建分析", `${modeSwitcher}${hasCampaigns ? campaignForm : ""}${customForm}`);

    if (hasCampaigns) {
      const cForm = $("#campaign-analysis-form", content);
      const mForm = $("#custom-analysis-form", content);
      const tabC = $("#mode-tab-campaign", content);
      const tabM = $("#mode-tab-custom", content);
      const searchInput = $("#campaign-picker-search", content);
      const selectEl = $("#campaign-picker-select", content);

      if (selectEl.options.length) selectEl.selectedIndex = 0;

      searchInput.oninput = () => {
        const q = searchInput.value.trim().toLowerCase();
        const filtered = campaigns.filter(c =>
          !q || `${c.campaignId} ${c.name} ${c.platform} ${c.site} ${c.channel}`.toLowerCase().includes(q));
        selectEl.innerHTML = filtered.map(c =>
          `<option value="${e(c.campaignId)}">${e(c.campaignId)} · ${e(c.platform || "")} · 花费 ${e(money(c.spend, c.currency))} · ROAS ${e(multiple(c.roas))} · ${c.ruleBound ? "已绑规则" : "未绑规则"} · ${e(c.name)}</option>`
        ).join("");
        if (selectEl.options.length) selectEl.selectedIndex = 0;
      };

      tabC.onclick = () => {
        tabC.setAttribute("aria-pressed", "true"); tabM.setAttribute("aria-pressed", "false");
        cForm.hidden = false; mForm.hidden = true;
      };
      tabM.onclick = () => {
        tabM.setAttribute("aria-pressed", "true"); tabC.setAttribute("aria-pressed", "false");
        mForm.hidden = false; cForm.hidden = true;
      };

      cForm.onsubmit = event => {
        event.preventDefault();
        const vals = formValues(cForm);
        if (!vals.campaignId) { errorIn($("#create-campaign-error", content), "请先选择一个活动"); return; }
        busy($("button", cForm), async () => {
          const run = await request(`${runsBase}/campaigns/${encodeURIComponent(vals.campaignId)}`, {
            body: {
              mode: vals.mode || "STANDARD",
              targetRoas: Number(vals.targetRoas),
              minimumSpend: Number(vals.minimumSpend),
              minimumClicks: Number(vals.minimumClicks),
              maxChangePercent: Number(vals.maxChangePercent)
            }
          });
          await showCreated(run);
        }, message => errorIn($("#create-campaign-error", content), message));
      };
    }

    const custom = $("#custom-analysis-form", content);
    custom.onsubmit = event => {
      event.preventDefault();
      const vals = formValues(custom);
      if (vals.start && vals.end && vals.start > vals.end) {
        errorIn($("#create-custom-error", content), "结束日期不能早于开始日期");
        return;
      }
      busy($("button", custom), async () => {
        const run = await request(runsBase, { body: vals });
        await showCreated(run);
      }, message => errorIn($("#create-custom-error", content), message));
    };
  };

  if (canCreate && !context.localCms) $("#sample-analysis", target).onclick = event => busy(event.currentTarget, async () => {
    const scenarios = await requestList(`${runsBase}/scenarios`);
    if (!current()) return;
    const content = dialog("新建场景分析", `<p class="sub">选择合成报表场景，然后运行分析。</p>
      <form><label>投放场景<select name="scenario" required>${scenarios.map(scenario => `<option value="${e(scenario.id)}">${e(scenarioTitles[scenario.id] || scenario.id)} · ${e(scenario.campaignId)}</option>`).join("")}</select></label>
      <div id="create-error"></div><div class="form-actions"><button class="button" type="submit">开始分析</button></div></form>`);
    $("form", content).onsubmit = formEvent => {
      formEvent.preventDefault();
      const form = formEvent.currentTarget;
      const scenario = formValues(form).scenario;
      if (lastAttempt?.scenario !== scenario) lastAttempt = { scenario, key: requestNonce() };
      busy($("button", form), async () => {
        const run = await request(`${runsBase}/scenarios/${encodeURIComponent(scenario)}`, { body: {}, key: lastAttempt.key });
        lastAttempt = undefined;
        await showCreated(run);
      }, message => errorIn($("#create-error", content), message));
    };
  });

  setTab(activeTab);
}
