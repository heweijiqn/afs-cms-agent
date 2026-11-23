import { openCohortImport, openFxRates, openAutomationRule, openEvidenceImport } from "./overseas-forms.js";
import { request, requestList } from "./api.js";
import { $, escape as e, n, numeric, percent, money, toneBadge, roasBadge, profitDisplay, table, dialog, closeDialog, formValues, errorIn, toast, busy } from "./ui.js";

const SEVERITY_META: Record<string, [string, string]> = {
  CRITICAL: ["严重告警", "red"],
  WARN: ["风险预警", "amber"],
  INFO: ["机会提醒", "teal"]
};

const CHANNEL_LABELS: Record<string, string> = {
  FEISHU: "飞书机器人",
  DINGTALK: "钉钉机器人",
  WECOM: "企业微信",
  WEBHOOK: "自定义 Webhook",
  INBOX: "站内通知"
};

const METRIC_LABELS: Record<string, string> = {
  ROAS: "ROAS",
  LOSS: "累计亏损 ($)",
  PACING: "消耗速率 (Pacing)",
  SPEND: "今日消耗 ($)"
};

const OP_LABELS: Record<string, string> = {
  LT: "<",
  LTE: "≤",
  GT: ">",
  GTE: "≥"
};

const FIREWALL_TONE: Record<string, string> = {
  LOSS_BREAKER: "red",
  OVERSPEND_ALERT: "amber",
  UNDERSPEND: "gray",
  HEALTHY: "teal"
};

const DAY_LABELS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];

// ============================================================================
// 1. Overview Insights: Conversion Funnel + Budget Pacing Firewall
// ============================================================================

export function mountOverviewInsights(container: HTMLElement, onDiagnose: (campaignId: string, btn: HTMLElement) => void) {
  let selectedPlatform = "";
  let selectedCurrency = "USD";
  let selectedCohortDays = 14;

  container.innerHTML = `
    <div class="ops-insights-grid">
      <section class="panel" id="ops-funnel-panel">
        <div class="panel-head">
          <div>
            <h2>全链路转化漏斗与象限诊断</h2>
            <p class="sub">追踪曝光 → 点击 → 落地页互动 → 变现转化各环节流失，快速识别放量明星与止损活动。</p>
          </div>
          <div class="preset-group" id="ops-platform-tabs" role="tablist" aria-label="按平台筛选漏斗">
            <button type="button" class="button small" data-plat="">全部平台</button>
            <button type="button" class="button secondary small" data-plat="Google">Google</button>
            <button type="button" class="button secondary small" data-plat="Facebook">Facebook</button>
            <button type="button" class="button secondary small" data-plat="TikTok">TikTok</button>
          </div>
        </div>
        <div id="ops-funnel-body"><div class="placeholder">正在计算全链路转化漏斗…</div></div>
      </section>

      <section class="panel" id="ops-pacing-panel">
        <div class="panel-head">
          <div>
            <h2>预算平滑消耗与资金防火墙</h2>
            <p class="sub">实时监测日预算消耗速率（Pacing）与亏损熔断风险，防止预算过快跑空。</p>
          </div>
          <a href="#rules" class="table-link">配置预警与排期</a>
        </div>
        <div id="ops-pacing-body"><div class="placeholder">正在监测预算消耗速率…</div></div>
      </section>
    </div>

    <section class="panel"><div class="panel-head"><h2>账户余额与健康状态</h2><button type="button" id="account-import" class="button secondary small">导入平台账户快照</button></div><p class="sub">以最新导入的平台状态及余额判断；超过 1 天的快照标为过期，余额可支撑天数按该日消耗计算。</p><div id="ops-account-body"></div></section>
    <section class="panel"><div class="panel-head"><h2>落地页表现与 CVR</h2><button type="button" id="landing-import" class="button secondary small">导入落地页观测</button></div><div id="ops-landing-body"></div></section>
    <section class="panel" id="ops-cohort-panel" style="margin-top:20px">
      <div class="panel-head">
        <div>
          <h2>出海长周期 Cohort 队列回收与 LTV 预测</h2>
          <p class="sub">突破单日静态 ROAS 局限，追踪 D0 / D1 / D3 / D7 / D14 / D30 队列留存与长周期回本周期（Payback Period）。</p>
        </div>
        <div class="action-set">
          <button type="button" class="button secondary small" id="cohort-import">导入归因队列</button><button type="button" class="button secondary small" id="fx-manage">录入汇率</button>
          <div class="preset-group" id="ops-cohort-currency" role="tablist" aria-label="切换币种">
            <button type="button" class="button small" data-curr="USD">USD ($)</button>
            <button type="button" class="button secondary small" data-curr="CNY">CNY (¥)</button>
            <button type="button" class="button secondary small" data-curr="EUR">EUR (€)</button>
            <button type="button" class="button secondary small" data-curr="GBP">GBP (£)</button>
            <button type="button" class="button secondary small" data-curr="JPY">JPY (¥)</button>
          </div>
          <div class="preset-group" id="ops-cohort-days" role="tablist" aria-label="队列周期">
            <button type="button" class="button small" data-days="14">近 14 天</button>
            <button type="button" class="button secondary small" data-days="7">近 7 天</button>
            <button type="button" class="button secondary small" data-days="30">近 30 天</button><button type="button" class="button secondary small" data-days="90">近 90 天</button>
          </div>
        </div>
      </div>
      <div id="ops-cohort-body"><div class="placeholder">正在计算长周期队列留存与回收模型…</div></div>
    </section>
  `;

  const funnelBody = $("#ops-funnel-body", container) as HTMLElement;
  const pacingBody = $("#ops-pacing-body", container) as HTMLElement;
  const cohortBody = $("#ops-cohort-body", container) as HTMLElement;
  const platTabs = $("#ops-platform-tabs", container) as HTMLElement;
  const currTabs = $("#ops-cohort-currency", container) as HTMLElement;
  const daysTabs = $("#ops-cohort-days", container) as HTMLElement;

  async function loadEvidence() {
    try{
      const [accounts,landing]=await Promise.all([requestList("/cms/agent/overseas/accounts?currency="+encodeURIComponent(selectedCurrency)),request("/cms/agent/overseas/landing")]);
      if(!container.isConnected)return;
      $("#ops-account-body",container).innerHTML=table(["账号","平台","日期","余额","可支撑天数","状态"],accounts.map((r:any)=>[e(r.account_id),e(r.platform),e(r.report_date),r.displayBalance==null?"缺少汇率":money(r.displayBalance,r.displayCurrency),r.daysRemaining==null?"—":numeric(r.daysRemaining),e(r.health)]),"暂无平台账户快照，请导入余额和状态");
      $("#ops-landing-body",container).innerHTML='<p class="sub">'+e(landing.notice)+' 相关系数：'+(landing.ttfbCvrCorrelation==null?"观测不足":numeric(landing.ttfbCvrCorrelation))+'</p>'+table(["活动","日期","URL","TTFB ms","访问","跳出率","CVR"],(landing.observations||[]).map((r:any)=>[e(r.campaign_id),e(r.report_date),e(r.url),numeric(r.ttfb_ms),numeric(r.sessions),r.bounceRate==null?"—":percent(r.bounceRate),r.cvr==null?"—":percent(r.cvr)]),"暂无落地页性能与访问观测");
    }catch(err:any){if(container.isConnected)errorIn($("#ops-account-body",container),err.message);}
  }
  $("#account-import",container).onclick=()=>openEvidenceImport("accounts",loadEvidence);
  $("#landing-import",container).onclick=()=>openEvidenceImport("landing",loadEvidence);
  let loadRevision=0;
  async function loadData() {
    loadEvidence();
    const qs = `?platform=${encodeURIComponent(selectedPlatform)}&currency=${encodeURIComponent(selectedCurrency)}`;
    const rev=++loadRevision;
    const tasks:[string,HTMLElement,(data:any)=>void][]=[
      [`/cms/agent/ops/funnel${qs}`,funnelBody,data=>renderFunnel(funnelBody,data,onDiagnose)],
      [`/cms/agent/ops/pacing${qs}`,pacingBody,data=>renderPacing(pacingBody,data,onDiagnose)],
      [`/cms/agent/overseas/cohort?platform=${encodeURIComponent(selectedPlatform)}&days=${selectedCohortDays}&currency=${encodeURIComponent(selectedCurrency)}`,cohortBody,data=>renderCohort(cohortBody,data,onDiagnose)]
    ];
    await Promise.allSettled(tasks.map(async([url,body,render])=>{
      try{const data=await request(url);if(container.isConnected&&rev===loadRevision)render(data);}
      catch(err:any){if(container.isConnected&&rev===loadRevision)errorIn(body,err.message);}
    }));
  }

  platTabs.querySelectorAll<HTMLButtonElement>("[data-plat]").forEach(btn => {
    btn.onclick = () => {
      selectedPlatform = btn.dataset.plat || "";
      platTabs.querySelectorAll<HTMLButtonElement>("[data-plat]").forEach(b => {
        b.className = b === btn ? "button small" : "button secondary small";
      });
      loadData();
    };
  });

  currTabs.querySelectorAll<HTMLButtonElement>("[data-curr]").forEach(btn => {
    btn.onclick = () => {
      selectedCurrency = btn.dataset.curr || "USD";
      currTabs.querySelectorAll<HTMLButtonElement>("[data-curr]").forEach(b => {
        b.className = b === btn ? "button small" : "button secondary small";
      });
      loadData();
    };
  });

  daysTabs.querySelectorAll<HTMLButtonElement>("[data-days]").forEach(btn => {
    btn.onclick = () => {
      selectedCohortDays = Number(btn.dataset.days || 14);
      daysTabs.querySelectorAll<HTMLButtonElement>("[data-days]").forEach(b => {
        b.className = b === btn ? "button small" : "button secondary small";
      });
      loadData();
    };
  });

  $("#cohort-import", container).onclick = () => openCohortImport(loadData);
  $("#fx-manage", container).onclick = () => openFxRates(loadData);
  loadData();
}

function renderCohort(target: HTMLElement, data: any, onDiagnose: (cid: string, btn: HTMLElement) => void) {
  const summary = data?.summary || {};
  const cohorts = data?.cohorts || [];
  const sym = summary.currencySymbol || "$";

  const metric = (value: any) => value == null ? "—" : numeric(value);
  const kpisHtml = `
    <div class="cohort-summary-grid">
      <div class="cohort-summary-card">
        <div class="label">累计队列花费</div>
        <div class="val">${e(sym)}${metric(summary.totalSpend)}</div>
      </div>
      <div class="cohort-summary-card">
        <div class="label">获客规模 (Acquisitions)</div>
        <div class="val">${metric(summary.totalAcquisitions)}</div>
      </div>
      <div class="cohort-summary-card">
        <div class="label">平均获客成本 (CAC)</div>
        <div class="val">${e(sym)}${metric(summary.avgCac)}</div>
      </div>
      <div class="cohort-summary-card">
        <div class="label">平均回本周期 (Payback)</div>
        <div class="val">${metric(summary.avgPaybackDays)} <span style="font-size:13px;font-weight:600">天</span></div>
      </div>
      <div class="cohort-summary-card">
        <div class="label">已观测 D30 LTV / CAC</div>
        <div class="val" style="color:#087c69">${metric(summary.ltvCacRatio)}x</div>
      </div>
      <div class="cohort-summary-card">
        <div class="label">D0 → D7 → D30 ROAS</div>
        <div class="val" style="font-size:15px">${metric(summary.avgD0Roas)}x → ${metric(summary.avgD7Roas)}x → ${metric(summary.avgD30Roas)}x</div>
      </div>
    </div>
    <div class="cohort-narrative">
      <strong>归因观测说明：</strong>
      <span>${e(summary.narrative || "")}</span>
    </div>
  `;

  const rows = cohorts.map((c: any) => {
    const d0Class = c.d0Roas >= 1.0 ? "cohort-cell-win" : c.d0Roas >= 0.8 ? "cohort-cell-mid" : "cohort-cell-low";
    const d1Class = c.d1Roas >= 1.0 ? "cohort-cell-win" : c.d1Roas >= 0.8 ? "cohort-cell-mid" : "cohort-cell-low";
    const d3Class = c.d3Roas >= 1.0 ? "cohort-cell-win" : c.d3Roas >= 0.8 ? "cohort-cell-mid" : "cohort-cell-low";
    const d7Class = c.d7Roas >= 1.0 ? "cohort-cell-win" : c.d7Roas >= 0.8 ? "cohort-cell-mid" : "cohort-cell-low";
    const d14Class = c.d14Roas >= 1.0 ? "cohort-cell-win" : c.d14Roas >= 0.8 ? "cohort-cell-mid" : "cohort-cell-low";
    const d30Class = c.d30Roas >= 1.0 ? "cohort-cell-win" : c.d30Roas >= 0.8 ? "cohort-cell-mid" : "cohort-cell-low";

    const statusBadge = c.breakEvenStatus === "RECOVERED"
      ? toneBadge("已回本", "teal")
      : c.breakEvenStatus === "PROJECTED_RECOVERED"
      ? toneBadge("预计回本", "amber")
      : toneBadge("尚未观测到回本", "gray");

    return [
      `<strong>${e(c.cohortDate)}</strong>`,
      `<span class="badge gray">${e(c.platform)}</span>`,
      `<span class="mono">${e(c.currencySymbol || "$")}${numeric(c.spend)}</span>`,
      `<span class="mono">${numeric(c.acquisitions)}</span>`,
      `<span class="${d0Class}">${metric(c.d0Roas)}x <small>(${c.d0Retention == null ? "—" : metric(c.d0Retention) + "%"})</small></span>`,
      `<span class="${d1Class}">${metric(c.d1Roas)}x <small>(${c.d1Retention == null ? "—" : metric(c.d1Retention) + "%"})</small></span>`,
      `<span class="${d3Class}">${metric(c.d3Roas)}x <small>(${c.d3Retention == null ? "—" : metric(c.d3Retention) + "%"})</small></span>`,
      `<span class="${d7Class}">${metric(c.d7Roas)}x <small>(${c.d7Retention == null ? "—" : metric(c.d7Retention) + "%"})</small></span>`,
      `<span class="${d14Class}">${metric(c.d14Roas)}x <small>(${c.d14Retention == null ? "—" : metric(c.d14Retention) + "%"})</small></span>`,
      `<span class="${d30Class}">${metric(c.d30Roas)}x <small>(${c.d30Retention == null ? "—" : metric(c.d30Retention) + "%"})</small></span>`,
      `<span class="mono">${c.paybackDays != null ? c.paybackDays + " 天" : "未达标"}</span>`,
      c.forecast?.available ? `<span class="mono">预测 D30 ${numeric(c.forecast.projectedD30Roas)}×<br>R² ${numeric(c.forecast.rSquared)}</span>` : `<small>${e(c.forecast?.reason || "缺少拟合观测")}</small>`,
      statusBadge
    ];
  });

  const tableHtml = table(
    ["队列批次", "渠道", "首日花费", "获客数", "D0 ROAS", "D1 ROAS", "D3 ROAS", "D7 ROAS", "D14 ROAS", "D30 ROAS", "首次观测回本天数", "趋势外推（非实际收入）", "回本状态"],
    rows, "暂无归因队列数据，请导入累计归因观测"
  );

  target.innerHTML = kpisHtml + tableHtml;
}


function renderFunnel(target: HTMLElement, data: any, onDiagnose: (cid: string, btn: HTMLElement) => void) {
  const displayMoney=(v:any)=>money(v,data.currency||"USD");
  const stages = data?.stages || [];
  const maxVal = Math.max(1, n(stages[0]?.value));
  const winners = data?.winners || [];
  const bleeders = data?.bleeders || [];

  const stageHtml = stages.map((st: any, idx: number) => {
    const unavailable = idx === 2 && data.engagementAvailable === false;
    const val = n(st.value);
    // Log-scaled visual width so impressions vs conversions both remain legible
    const pct = idx === 0 ? 100 : Math.max(12, Math.min(96, Math.round((Math.log10(val + 10) / Math.log10(maxVal + 10)) * 100)));
    const rateLabel = idx === 0 ? "基准 100%" : `环节转化 ${percent(st.rate)}`;
    return `<div class="funnel-step">
      <div class="funnel-step-head">
        <div><strong>${e(st.name)}</strong> <span class="muted">${e(st.enName)}</span></div>
        <div class="funnel-step-meta">
          <strong class="mono">${unavailable ? "暂无观测" : e(numeric(val))}</strong>
          ${unavailable ? "" : toneBadge(rateLabel, st.tone === "warn" ? "amber" : "teal")}
        </div>
      </div>
      <progress class="funnel-meter tone-${e(st.tone || "teal")}" max="100" value="${pct}"></progress>
      <div class="funnel-step-hint">${e(st.insight || "")}</div>
    </div>`;
  }).join("");

  const renderMiniList = (rows: any[], emptyText: string, mode: "win" | "bleed") => {
    if (!rows.length) return `<p class="muted">${e(emptyText)}</p>`;
    return `<ul class="quadrant-list">${rows.slice(0, 4).map(r => `
      <li>
        <div class="quadrant-item-main">
          <strong>${e(r.name || r.campaignId)}</strong>
          <span class="muted">${e(r.platform)} · 花费 ${e(displayMoney(r.spend))}</span>
        </div>
        <div class="quadrant-item-right">
          ${roasBadge(r.roas)}
          <span class="mono ${mode === "win" ? "profit-pos" : "profit-neg"}">${n(r.profit) >= 0 ? "+" : ""}${e(displayMoney(r.profit))}</span>
          <button type="button" class="table-link" data-ops-diag="${e(r.campaignId)}">诊断</button>
        </div>
      </li>`).join("")}</ul>`;
  };

  target.innerHTML = `
    <div class="funnel-kpi-strip">
      <div><span class="muted">活动数</span><strong class="mono">${numeric(data?.campaignCount)}</strong></div>
      <div><span class="muted">综合 CTR</span><strong class="mono">${percent(data?.ctr)}</strong></div>
      <div><span class="muted">综合 CVR</span><strong class="mono">${percent(data?.cvr)}</strong></div>
      <div><span class="muted">平均 CPA</span><strong class="mono">${displayMoney(data?.cpa)}</strong></div>
      <div><span class="muted">整体 ROAS</span>${roasBadge(data?.roas)}</div>
    </div>
    <p class="sub">展示币种：${e(data.currency||"USD")}；非 USD 使用录入的有日期汇率。</p>
    <div class="funnel-stages">${stageHtml}</div>
    ${table(["国家/地区","市场分组","花费","CPC","CPM"],(data.countryBreakdown||[]).map((r:any)=>[e(r.country),e(r.regionGroup),displayMoney(r.spend),r.cpc==null?"—":displayMoney(r.cpc),r.cpm==null?"—":displayMoney(r.cpm)]),"暂无国家维度原始观测")}
    <div class="quadrant-grid">
      <div class="quadrant-box quadrant-win">
        <div class="quadrant-title"><span>明星放量活动（高 ROAS 盈利）</span>${toneBadge(`${winners.length} 个`, "teal")}</div>
        ${renderMiniList(winners, "当前筛选下暂无达标放量活动", "win")}
      </div>
      <div class="quadrant-box quadrant-bleed">
        <div class="quadrant-title"><span>急需止损活动（低 ROAS 出血）</span>${toneBadge(`${bleeders.length} 个`, "red")}</div>
        ${renderMiniList(bleeders, "当前筛选下无明显亏损出血活动", "bleed")}
      </div>
    </div>
  `;

  target.querySelectorAll<HTMLButtonElement>("[data-ops-diag]").forEach(btn => {
    btn.onclick = () => onDiagnose(btn.dataset.opsDiag || "", btn);
  });
}

function renderPacing(target: HTMLElement, data: any, onDiagnose: (cid: string, btn: HTMLElement) => void) {
  if(!data?.items?.length){target.innerHTML='<div class="placeholder">缺少当日报告或真实日预算，暂不计算消耗速率。</div>';return;}
  const displayMoney=(v:any)=>money(v,data.currency||"USD");
  const s = data?.summary || {};
  const items = data?.items || [];
  const healthTone = s.firewallHealth === "ALERT" ? "red" : s.firewallHealth === "WATCH" ? "amber" : "teal";
  const healthText = s.firewallHealth === "ALERT" ? "已触发亏损熔断预警" : s.firewallHealth === "WATCH" ? "存在超速消耗活动" : "资金消耗平稳受控";

  target.innerHTML = `
    <div class="firewall-banner firewall-${healthTone}">
      <div>
        <strong>资金防火墙状态：${e(healthText)}</strong>
        <p class="sub">当前时段日进度 ${e(s.dayProgressPct ?? 0)}% · 全盘今日已消耗 ${e(displayMoney(s.totalTodaySpend))} / 日预算 ${e(displayMoney(s.totalDailyBudget))}（Pacing ${e(s.overallPacingRatio ?? 1)}×）</p>
      </div>
      <div class="firewall-badges">
        ${toneBadge(`熔断预警 ${numeric(s.lossBreakerCount)}`, s.lossBreakerCount > 0 ? "red" : "gray")}
        ${toneBadge(`消耗过快 ${numeric(s.overspendCount)}`, s.overspendCount > 0 ? "amber" : "gray")}
        ${toneBadge(`平稳 ${numeric(s.healthyCount)}`, "teal")}
      </div>
    </div>
    ${table(
      ["广告活动", "今日消耗 / 日预算", "消耗进度", "速率", "防火墙状态", "操作"],
      items.slice(0, 8).map((it: any) => {
        const tone = FIREWALL_TONE[it.firewallStatus] || "gray";
        return [
          `<strong>${e(it.name || it.campaignId)}</strong><br><small class="muted">${e(it.platform)} · ROAS ${e(it.roas)}×</small>`,
          `<span class="mono">${e(displayMoney(it.todaySpend))} / ${e(displayMoney(it.dailyBudget))}</span><br><small class="muted">余 ${e(displayMoney(it.remainingBudget))}</small>`,
          `<div class="pacing-cell"><progress class="pacing-meter tone-${tone}" max="100" value="${Math.min(100, Math.round(n(it.spendPct)))}"></progress><span class="mono">${e(it.spendPct)}%</span></div>`,
          `<span class="mono">${e(it.pacingRatio)}×</span>`,
          toneBadge(it.firewallLabel || it.firewallStatus, tone),
          `<button type="button" class="table-link" data-pacing-diag="${e(it.campaignId)}">诊断</button>`
        ];
      }),
      "暂无在投预算消耗数据"
    )}
  `;

  target.querySelectorAll<HTMLButtonElement>("[data-pacing-diag]").forEach(btn => {
    btn.onclick = () => onDiagnose(btn.dataset.pacingDiag || "", btn);
  });
}

// ============================================================================
// 2. Real-time Alert Rules & Multi-Channel Notification Center
// ============================================================================

export async function renderAlertCenter(container: HTMLElement) {
  container.innerHTML = `<div class="placeholder">正在加载实时预警规则与告警记录…</div>`;
  try {
    const [rules, events] = await Promise.all([
      requestList("/cms/agent/ops/alerts/rules"),
      requestList("/cms/agent/ops/alerts/events?status=ALL")
    ]);
    if (!container.isConnected) return;

    const openEvents = events.filter((ev: any) => ev.status === "OPEN").length;
    container.innerHTML = `
      <div class="alert-center-head">
        <div class="alert-summary-chips">
          ${toneBadge(`已启用规则 ${rules.filter((r: any) => r.enabled).length} / ${rules.length}`, "teal")}
          ${toneBadge(`待处理告警 ${openEvents}`, openEvents > 0 ? "red" : "gray")}
          ${toneBadge("支持飞书 / 钉钉 / 企业微信 / Webhook", "gray")}
        </div>
        <div class="action-set">
          <button type="button" class="button secondary" id="btn-add-alert-rule">新增预警规则</button>
          <button type="button" class="button" id="btn-scan-alerts">立即巡检并推送告警</button>
        </div>
      </div>

      <section class="sub-section">
        <h3>预警规则配置</h3>
        ${table(
          ["规则名称", "触发条件", "告警级别", "通知通道", "状态", "操作"],
          rules.map((r: any) => {
            const sev = SEVERITY_META[r.severity] || [r.severity, "gray"];
            const cond = `${METRIC_LABELS[r.metric] || r.metric} ${OP_LABELS[r.operator] || r.operator} ${n(r.threshold).toFixed(2)}`;
            return [
              `<strong>${e(r.name)}</strong>${r.webhookUrl ? `<br><small class="muted mono">${e(String(r.webhookUrl).slice(0, 48))}${String(r.webhookUrl).length > 48 ? "…" : ""}</small>` : ""}`,
              `<code class="mono">${e(cond)}</code>`,
              toneBadge(sev[0], sev[1]),
              toneBadge(CHANNEL_LABELS[r.channel] || r.channel, "gray"),
              r.enabled ? toneBadge("已启用", "teal") : toneBadge("已停用", "gray"),
              `<div class="record-actions">
                <button type="button" class="table-link" data-test-alert="${r.id}">测试推送</button>
                <button type="button" class="table-link" data-edit-alert="${r.id}">编辑</button>
                <button type="button" class="table-link" data-del-alert="${r.id}">删除</button>
              </div>`
            ];
          }),
          "暂无预警规则"
        )}
      </section>

      <section class="sub-section">
        <h3>实时告警触发记录</h3>
        ${table(
          ["时间", "告警内容", "级别", "通知通道", "状态", "操作"],
          events.map((ev: any) => {
            const sev = SEVERITY_META[ev.severity] || [ev.severity, "gray"];
            const stTone = ev.status === "OPEN" ? "red" : ev.status === "RESOLVED" ? "teal" : "gray";
            const stLabel = ev.status === "OPEN" ? "待处理" : ev.status === "RESOLVED" ? "已处理" : "已忽略";
            return [
              `<span class="mono muted">${e(String(ev.createdAt || "").slice(0, 19).replace("T", " "))}</span>`,
              `<div><strong>${e(ev.ruleName)}</strong> · ${e(ev.campaignName || ev.campaignId)}<br><small class="muted">${e(ev.message)}</small></div>`,
              toneBadge(sev[0], sev[1]),
              toneBadge(CHANNEL_LABELS[ev.channel] || ev.channel, "gray"),
              toneBadge(stLabel, stTone),
              ev.status === "OPEN"
                ? `<div class="record-actions">
                    <button type="button" class="table-link" data-ack-alert="${ev.id}" data-ack-status="RESOLVED">标记已处理</button>
                    <button type="button" class="table-link" data-ack-alert="${ev.id}" data-ack-status="IGNORED">忽略</button>
                  </div>`
                : `<span class="muted">${e(ev.handledBy || "已归档")}</span>`
            ];
          }),
          "暂无告警记录，点击「立即巡检并推送告警」执行实时检测。"
        )}
      </section>
    `;

    $("#btn-scan-alerts", container).onclick = async (ev) => {
      await busy(ev.currentTarget as HTMLButtonElement, async () => {
        const res = await request("/cms/agent/ops/alerts/scan", { body: {} });
        toast(`巡检完成：扫描 ${res.scannedRules} 条规则，新触发 ${res.newlyTriggered} 条告警`);
        await renderAlertCenter(container);
      });
    };

    $("#btn-add-alert-rule", container).onclick = () => openAlertRuleDialog(null, () => renderAlertCenter(container));

    container.querySelectorAll("[data-test-alert]").forEach((btn: HTMLButtonElement) => {
      btn.onclick = () => busy(btn, async () => {
        const res = await request(`/cms/agent/ext/alerts/rules/${btn.dataset.testAlert}/test`, { body: {} });
        toast(res.detail || (res.sent ? "测试消息已成功推送" : "测试推送未发送"));
      });
    });

    container.querySelectorAll<HTMLButtonElement>("[data-edit-alert]").forEach(btn => {
      btn.onclick = () => {
        const rule = rules.find((r: any) => String(r.id) === btn.dataset.editAlert);
        if (rule) openAlertRuleDialog(rule, () => renderAlertCenter(container));
      };
    });

    container.querySelectorAll<HTMLButtonElement>("[data-del-alert]").forEach(btn => {
      btn.onclick = () => busy(btn, async () => {
        await request(`/cms/agent/ops/alerts/rules/${btn.dataset.delAlert}/delete`, { body: {} });
        toast("预警规则已删除");
        await renderAlertCenter(container);
      });
    });

    container.querySelectorAll<HTMLButtonElement>("[data-ack-alert]").forEach(btn => {
      btn.onclick = () => busy(btn, async () => {
        await request(`/cms/agent/ops/alerts/events/${btn.dataset.ackAlert}/ack`, {
          body: { status: btn.dataset.ackStatus }
        });
        toast("告警状态已更新");
        await renderAlertCenter(container);
      });
    });
  } catch (err: any) {
    if (container.isConnected) errorIn(container, err?.message || "加载预警中心失败");
  }
}

function openAlertRuleDialog(rule: any, onSaved: () => void) {
  const body = dialog(rule ? "编辑预警规则" : "新增预警规则", `<form class="form-grid">
    <label class="all">规则名称<input name="name" required value="${e(rule?.name || "")}" placeholder="例如：ROAS 跌破止损线"></label>
    <label>监控指标<select name="metric">
      ${Object.entries(METRIC_LABELS).map(([k, v]) => `<option value="${k}" ${rule?.metric === k ? "selected" : ""}>${e(v)}</option>`).join("")}
    </select></label>
    <label>判断条件<select name="operator">
      ${Object.entries(OP_LABELS).map(([k, v]) => `<option value="${k}" ${rule?.operator === k ? "selected" : ""}>${e(v)}</option>`).join("")}
    </select></label>
    <label>阈值<input name="threshold" type="number" step="0.01" required value="${e(rule?.threshold ?? 1.0)}"></label>
    <label>告警级别<select name="severity">
      <option value="CRITICAL" ${rule?.severity === "CRITICAL" ? "selected" : ""}>严重告警</option>
      <option value="WARN" ${!rule || rule?.severity === "WARN" ? "selected" : ""}>风险预警</option>
      <option value="INFO" ${rule?.severity === "INFO" ? "selected" : ""}>机会提醒</option>
    </select></label>
    <label>通知通道<select name="channel">
      ${Object.entries(CHANNEL_LABELS).map(([k, v]) => `<option value="${k}" ${rule?.channel === k ? "selected" : ""}>${e(v)}</option>`).join("")}
    </select></label>
    <label>启用状态<select name="enabled">
      <option value="true" ${rule?.enabled !== false ? "selected" : ""}>启用</option>
      <option value="false" ${rule?.enabled === false ? "selected" : ""}>停用</option>
    </select></label>
    <label class="all">Webhook 推送地址（飞书 / 钉钉 / 企业微信）<input name="webhookUrl" value="${e(rule?.webhookUrl || "")}" placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/..."></label>
    <div id="alert-rule-err" class="all"></div>
    <div class="form-actions all"><button type="submit" class="button">保存规则</button></div>
  </form>`);

  const form = $("form", body) as HTMLFormElement;
  form.onsubmit = (ev) => {
    ev.preventDefault();
    const v = formValues(form);
    busy($('button[type="submit"]', form), async () => {
      await request("/cms/agent/ops/alerts/rules", {
        body: {
          id: rule?.id || null,
          name: v.name,
          metric: v.metric,
          operator: v.operator,
          threshold: Number(v.threshold),
          severity: v.severity,
          channel: v.channel,
          webhookUrl: v.webhookUrl,
          enabled: v.enabled !== "false"
        }
      });
      closeDialog();
      toast("预警规则已保存");
      onSaved();
    }, msg => errorIn($("#alert-rule-err", body), msg));
  };
}

// ============================================================================
// 3. 7x24 Interactive Drag-to-Select Dayparting Schedule Matrix
// ============================================================================

export async function renderDaypartingMatrix(container: HTMLElement) {
  container.innerHTML = `<div class="placeholder">正在加载 7×24 分时投放排期…</div>`;
  try {
    const schedules = await requestList("/cms/agent/ops/dayparting");
    if (!container.isConnected) return;

    let current = schedules[0] || {
      scopeKey: "global:default",
      label: "全局默认投放排期",
      platform: "ALL",
      timezone: "UTC+8",
      matrixBits: "1".repeat(168),
      presetName: "全天候"
    };
    let bits = String(current.matrixBits || "1".repeat(168)).split("");
    let presetName = current.presetName || "自定义排期";
    let dragging = false;
    let paintValue = "1";

    function buildPreset(type: string) {
      const arr: string[] = [];
      for (let d = 0; d < 7; d++) {
        for (let h = 0; h < 24; h++) {
          let on = false;
          if (type === "ALL_DAY") on = true;
          else if (type === "WORKDAY_PRIME") on = d < 5 && h >= 9 && h <= 21;
          else if (type === "EVENING_PEAK") on = (h >= 18 && h <= 23) || (d >= 5 && h >= 10 && h <= 23);
          else if (type === "AI_OPTIMAL") on = (h >= 8 && h <= 23) && !(d === 0 && h < 10);
          arr.push(on ? "1" : "0");
        }
      }
      return arr;
    }

    function updateSummary() {
      const active = bits.filter(b => b === "1").length;
      const counter = $("#dp-active-count", container);
      if (counter) counter.textContent = `${active} / 168 小时 (${Math.round(active / 168 * 100)}%)`;
      const cells = container.querySelectorAll<HTMLElement>(".dp-cell");
      cells.forEach(cell => {
        const idx = Number(cell.dataset.idx);
        cell.classList.toggle("active", bits[idx] === "1");
      });
    }

    function renderGrid() {
      const hourHeaders = Array.from({ length: 24 }, (_, h) =>
        `<button type="button" class="dp-hour-hdr" data-col="${h}" title="点击切换所有天的 ${h}:00 时段">${String(h).padStart(2, "0")}</button>`
      ).join("");

      const rowsHtml = DAY_LABELS.map((dayName, d) => {
        const cells = Array.from({ length: 24 }, (_, h) => {
          const idx = d * 24 + h;
          const active = bits[idx] === "1" ? " active" : "";
          return `<div class="dp-cell${active}" data-idx="${idx}" title="${dayName} ${String(h).padStart(2, "0")}:00 - ${String(h).padStart(2, "0")}:59"></div>`;
        }).join("");
        return `<div class="dp-row">
          <button type="button" class="dp-day-hdr" data-row="${d}" title="点击切换${dayName}全天">${dayName}</button>
          <div class="dp-cells">${cells}</div>
        </div>`;
      }).join("");

      container.innerHTML = `
        <div class="dp-toolbar">
          <div class="dp-scope-picker">
            <label>排期策略范围
              <select id="dp-scope-select">
                ${schedules.map((sc: any) => `<option value="${e(sc.scopeKey)}" ${sc.scopeKey === current.scopeKey ? "selected" : ""}>${e(sc.label)} (${e(sc.timezone)})</option>`).join("")}
              </select>
            </label>
            <div class="dp-stat">已选投放时段：<strong id="dp-active-count" class="mono"></strong></div>
          </div>
          <div class="preset-group">
            <button type="button" class="button secondary small" data-preset="ALL_DAY">全天候 168h</button>
            <button type="button" class="button secondary small" data-preset="WORKDAY_PRIME">工作日黄金</button>
            <button type="button" class="button secondary small" data-preset="EVENING_PEAK">欧美晚高峰</button>
            <button type="button" class="button secondary small" data-preset="AI_OPTIMAL">AI 推荐时段</button>
            <button type="button" class="button secondary small" data-preset="CLEAR">清空</button>
            <button type="button" class="button small" id="dp-save-btn">保存排期</button>
          </div>
        </div>
        <p class="sub">按住鼠标左键在网格上拖动可连续框选或取消投放时段；点击左侧「星期」或顶部「小时」可一键切换整行/整列。</p>
        <div class="dp-matrix" id="dp-matrix">
          <div class="dp-header-row">
            <span class="dp-corner">星期 \\ 时段</span>
            <div class="dp-hour-headers">${hourHeaders}</div>
          </div>
          ${rowsHtml}
        </div>
        <div class="dp-legend">
          <span><i class="dp-swatch active"></i> 投放开启</span>
          <span><i class="dp-swatch"></i> 暂停投放</span>
          <span class="muted">当前模板：${e(presetName)} · 时区：${e(current.timezone || "UTC+8")}</span>
        </div>
      `;

      updateSummary();

      const matrix = $("#dp-matrix", container) as HTMLElement;
      matrix.onpointerdown = (ev: PointerEvent) => {
        const cell = (ev.target as HTMLElement)?.closest(".dp-cell") as HTMLElement | null;
        if (!cell) return;
        ev.preventDefault();
        const idx = Number(cell.dataset.idx);
        dragging = true;
        paintValue = bits[idx] === "1" ? "0" : "1";
        bits[idx] = paintValue;
        presetName = "自定义排期";
        updateSummary();
      };
      matrix.onpointerover = (ev: PointerEvent) => {
        if (!dragging) return;
        const cell = (ev.target as HTMLElement)?.closest(".dp-cell") as HTMLElement | null;
        if (!cell) return;
        const idx = Number(cell.dataset.idx);
        if (bits[idx] !== paintValue) {
          bits[idx] = paintValue;
          updateSummary();
        }
      };
      window.addEventListener("pointerup", () => { dragging = false; }, { once: false });

      matrix.querySelectorAll<HTMLButtonElement>("[data-row]").forEach(btn => {
        btn.onclick = () => {
          const d = Number(btn.dataset.row);
          const rowSlice = bits.slice(d * 24, d * 24 + 24);
          const next = rowSlice.every(v => v === "1") ? "0" : "1";
          for (let h = 0; h < 24; h++) bits[d * 24 + h] = next;
          presetName = "自定义排期";
          updateSummary();
        };
      });

      matrix.querySelectorAll<HTMLButtonElement>("[data-col]").forEach(btn => {
        btn.onclick = () => {
          const h = Number(btn.dataset.col);
          let allOn = true;
          for (let d = 0; d < 7; d++) if (bits[d * 24 + h] !== "1") allOn = false;
          const next = allOn ? "0" : "1";
          for (let d = 0; d < 7; d++) bits[d * 24 + h] = next;
          presetName = "自定义排期";
          updateSummary();
        };
      });

      container.querySelectorAll<HTMLButtonElement>("[data-preset]").forEach(btn => {
        btn.onclick = () => {
          const p = btn.dataset.preset || "CLEAR";
          bits = buildPreset(p);
          presetName = btn.textContent || p;
          updateSummary();
        };
      });

      const scopeSel = $("#dp-scope-select", container) as HTMLSelectElement;
      if (scopeSel) {
        scopeSel.onchange = () => {
          const found = schedules.find((s: any) => s.scopeKey === scopeSel.value);
          if (found) {
            current = found;
            bits = String(found.matrixBits || "1".repeat(168)).split("");
            presetName = found.presetName || "自定义排期";
            renderGrid();
          }
        };
      }

      $("#dp-save-btn", container).onclick = (ev) => {
        busy(ev.currentTarget as HTMLButtonElement, async () => {
          const res = await request("/cms/agent/ops/dayparting", {
            body: {
              scopeKey: current.scopeKey,
              label: current.label,
              platform: current.platform,
              timezone: current.timezone,
              matrixBits: bits.join(""),
              presetName
            }
          });
          current.matrixBits = bits.join("");
          current.presetName = presetName;
          toast(`排期已保存：${current.label}（${res.activeHours} / 168 小时生效）`);
        });
      };
    }

    renderGrid();
  } catch (err: any) {
    if (container.isConnected) errorIn(container, err?.message || "加载分时排期失败");
  }
}

// ============================================================================
// 4. Autonomous Unattended Smart Rules Engine (Stop-loss, Scale-up, Fatigue, Off-peak)
// ============================================================================

const RULE_TYPE_META: Record<string, [string, string]> = {
  STOP_LOSS: ["空耗止损熔断", "red"],
  SCALE_UP: ["优质放量追投", "teal"],
  FATIGUE_ROTATE: ["素材疲劳轮换", "amber"],
  OFF_PEAK_SAVER: ["非高峰节能巡航", "gray"]
};

const ACTION_TYPE_LABELS: Record<string, string> = {
  PAUSE_CAMPAIGN: "自动暂停活动 (PAUSE)",
  ADJUST_BUDGET: "阶梯递增/降低日预算",
  SWAP_CREATIVE: "下线疲劳素材并切换备用",
  NOTIFY_ONLY: "站内提醒"
};

export async function renderSmartAutomationRules(container: HTMLElement) {
  async function reload() {
    container.innerHTML = `<div class="placeholder">正在加载无人值守自动化规则中心与执行日志…</div>`;
    try {
      const [rules, logs, schedule] = await Promise.all([
        requestList("/cms/agent/overseas/automation/rules"),
        requestList("/cms/agent/overseas/automation/logs?limit=30"),
        request("/cms/agent/overseas/automation/schedule")
      ]);
      if (!container.isConnected) return;
      renderContent(rules, logs, schedule);
    } catch (err: any) {
      if (container.isConnected) errorIn(container, err?.message || "加载自动化规则失败");
    }
  }

  function renderContent(rules: any[], logs: any[], schedule: any) {
    container.innerHTML = `
      <div class="alert-center-head">
        <div>
          <h2>无人值守自动化智能规则引擎</h2>
          <p class="sub">针对出海多时区痛点：空耗自动止损熔断、优质活动阶梯提预算、素材疲劳轮换与海外非高峰出价护栏。</p>
        </div>
        <div class="action-set">
          <button type="button" class="button secondary" id="auto-rule-new">新建规则</button><button type="button" class="button secondary" id="auto-schedule">${schedule.enabled ? "关闭定时评估" : "开启定时评估（每分钟）"}</button>
          <button type="button" class="button" id="auto-eval-btn">立即执行规则扫描与评估</button>
          <button type="button" class="button secondary" id="auto-clear-logs-btn">清空执行日志</button>
        </div>
      </div>

      <div class="sub-section">
        <h3>当前生效中的自动化托管策略（共 ${rules.length} 条）</h3>
        <div class="auto-rules-grid">
          ${rules.map(r => {
            const [typeLabel, typeColor] = RULE_TYPE_META[r.ruleType] || [r.ruleType, "gray"];
            const modeBadge = r.dryRun
              ? toneBadge("仿真试运行 (Dry Run)", "amber")
              : toneBadge("本地执行", "teal");
            const actionLabel = ACTION_TYPE_LABELS[r.actionType] || r.actionType;

            return `
              <div class="auto-rule-card">
                <div class="auto-rule-head">
                  <div>
                    <strong>${e(r.name)}</strong>
                    <div style="margin-top:4px;display:flex;gap:6px;align-items:center;flex-wrap:wrap">
                      ${toneBadge(typeLabel, typeColor as any)}
                      <span class="badge gray">${e(r.scopePlatform || "ALL")}</span>
                      ${modeBadge}
                    </div>
                  </div>
                </div>
                <div class="auto-rule-desc">
                  <div><strong>动作：</strong>${e(actionLabel)}</div><button type="button" class="table-link" data-edit-rule="${r.id}">编辑</button> <button type="button" class="table-link" data-delete-rule="${r.id}">删除</button>
                  <div style="margin-top:3px"><strong>触发条件：</strong><code class="mono">${e(r.conditionJson)}</code></div>
                </div>
                <div class="auto-rule-actions">
                  <span class="secondary-text">已触发 <strong>${r.triggerCount || 0}</strong> 次 · ${r.lastTriggeredAt ? e(r.lastTriggeredAt.substring(5, 16)) : "尚未触发"}</span>
                  <div class="action-set">
                    <button type="button" class="button secondary small" data-toggle-dryrun="${r.id}" data-current="${r.dryRun}">
                      ${r.dryRun ? "切为本地执行" : "切为仿真试运行"}
                    </button>
                  </div>
                </div>
              </div>
            `;
          }).join("")}
        </div>
      </div>

      <div class="sub-section" style="margin-top:24px">
        <div class="panel-head">
          <div>
            <h3>自动化规则执行与仿真日志流水</h3>
            <p class="sub">按时间倒序追踪系统自动发现的触发现象与采取的动作明细。</p>
          </div>
        </div>
        <div id="auto-logs-wrap">
          ${logs.length === 0
            ? '<div class="placeholder">暂无规则触发记录，点击上方「立即执行规则扫描与评估」进行即时扫描。</div>'
            : table(
                ["时间", "触发规则", "目标活动", "渠道", "触发原因", "执行动作", "运行模式", "执行状态"],
                logs.map(l => [
                  `<span class="mono">${e(String(l.createdAt || "").substring(5, 19))}</span>`,
                  `<strong>${e(l.ruleName)}</strong>`,
                  `<strong>${e(l.campaignName || l.campaignId)}</strong><br><small class="mono muted">${e(l.campaignId)}</small>`,
                  `<span class="badge gray">${e(l.platform || "ALL")}</span>`,
                  `<span>${e(l.triggerReason)}</span>`,
                  `<strong>${e(l.actionTaken)}</strong>`,
                  l.isSimulated ? toneBadge("仿真试运行", "amber") : toneBadge("自动执行", "teal"),
                  toneBadge(l.executionStatus === "SUCCESS" ? "成功" : l.executionStatus, "teal")
                ])
              )}
        </div>
      </div>
    `;

    $("#auto-rule-new", container).onclick = () => openAutomationRule(null,reload);
    $("#auto-schedule", container).onclick = ev => busy(ev.currentTarget as HTMLButtonElement,async()=>{await request("/cms/agent/overseas/automation/schedule",{body:{enabled:!schedule.enabled}});reload();});
    container.querySelectorAll<HTMLButtonElement>("[data-edit-rule]").forEach(btn=>btn.onclick=()=>openAutomationRule(rules.find(r=>r.id===Number(btn.dataset.editRule)),reload));
    container.querySelectorAll<HTMLButtonElement>("[data-delete-rule]").forEach(btn=>btn.onclick=()=>busy(btn,async()=>{await request(`/cms/agent/overseas/automation/rules/${btn.dataset.deleteRule}/delete`,{body:{}});reload();}));
    $("#auto-eval-btn", container).onclick = (ev) => {
      busy(ev.currentTarget as HTMLButtonElement, async () => {
        const res = await request("/cms/agent/overseas/automation/evaluate", { body: {} });
        toast(`自动化规则评估完成：已扫描 ${res.evaluatedRules} 条规则，发现 ${res.triggeredCount} 个匹配活动`);
        reload();
      });
    };

    $("#auto-clear-logs-btn", container).onclick = (ev) => {
      busy(ev.currentTarget as HTMLButtonElement, async () => {
        await request("/cms/agent/overseas/automation/logs/clear", { body: {} });
        toast("已清空执行日志");
        reload();
      });
    };

    container.querySelectorAll<HTMLButtonElement>("[data-toggle-dryrun]").forEach(btn => {
      btn.onclick = () => {
        busy(btn, async () => {
          const id = Number(btn.dataset.toggleDryrun);
          const currentDryRun = btn.dataset.current === "true";
          const rule = rules.find(r => r.id === id);
          if (rule) {
            await request("/cms/agent/overseas/automation/rules", {
              body: {
                id: rule.id,
                name: rule.name,
                ruleType: rule.ruleType,
                scopePlatform: rule.scopePlatform,
                conditionJson: rule.conditionJson,
                actionType: rule.actionType,
                actionParamsJson: rule.actionParamsJson,
                dryRun: !currentDryRun,
                enabled: rule.enabled
              }
            });
            toast(`已更新规则运行模式为：${!currentDryRun ? "仿真试运行" : "本地执行"}`);
            reload();
          }
        });
      };
    });
  }

  reload();
}

