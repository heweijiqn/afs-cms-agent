import { request, requestList } from "./api.js";
import { $, escape as e, n, numeric, ratio, multiple, percent, money, badge, toneBadge, roasBadge, profitDisplay,
  pageHead, metric, sparklineSvg, table, dialog, closeDialog, formValues, errorIn, toast, busy, listSearch, wireListSearch, exportCsv, defaultDateRange } from "./ui.js";
import { mountChart } from "./chart.js";
import { businessActions, wireBusinessActions } from "./business.js";
import { setReportChoices, wireReportChoices } from "./report-options.js";
import { ruleSummary } from "./rule-editor.js";
import { loadSiteFilter, loadSiteSelect, adPlatforms } from "./form-choices.js";
import { focusAgentRun, openAgentTab } from "./agent.js";
import { mountOverviewInsights, renderAlertCenter, renderDaypartingMatrix } from "./ops-insights.js";

const size = 10;
const state = {
  reports: { start: "", end: "", pubId: "", campaign: "", points: null },
  campaigns: { page: 1, filter: "", adPlatform: "", state: "", orderParam: "", data: null },
  rules: { page: 1, filter: "", tab: "auto", data: null },
  settings: { page: 1, filter: "", data: null }
};
const simulatedTitles = { healthy: "表现良好", underperforming: "回收偏低", "low-volume": "样本不足" };
const campaignStateMeta = {
  AUDITING: ["待审核", "amber"], PASS: ["已通过", "teal"], REJECT: ["已驳回", "red"], ARCHIVE: ["已归档", "gray"]
};
const platformStateMeta = {
  LOCAL_ONLY: ["待同步平台", "gray"], PLATFORM_CREATING: ["创建中", "amber"],
  PLATFORM_ACTIVE: ["投放中", "teal"], PLATFORM_PAUSED: ["已暂停", "gray"],
  PLATFORM_REJECTED: ["平台拒绝", "red"], PLATFORM_UNKNOWN: ["待核对", "amber"]
};
const ruleTypeLabels = { BUDGET: "预算规则", REPEAT: "重复规则", CONT: "连续规则" };
const ruleCycleLabels = { REPEAT: "重复执行", CONT: "连续执行" };

let chartCleanup = () => {};

const dateKey = date => String(date ?? "").slice(0, 10);
const blankDay = date => ({ date, spend: 0, revenue: 0, impressions: 0, clicks: 0, conversions: 0 });
const fields = { spend: "ad_spend", revenue: "gross_revenue", impressions: "ad_impressions",
  clicks: "ad_clicks", conversions: "ad_conversion" };

function isoShift(baseIso, deltaDays) {
  const base = baseIso ? new Date(`${baseIso}T00:00:00`) : new Date();
  if (Number.isNaN(base.getTime())) return "";
  base.setDate(base.getDate() + deltaDays);
  const y = base.getFullYear();
  const m = String(base.getMonth() + 1).padStart(2, "0");
  const d = String(base.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function pointsFrom(rows) {
  const byDay = new Map();
  rows.forEach(row => {
    const date = dateKey(row.date);
    if (!date) return;
    const point = byDay.get(date) || blankDay(date);
    Object.entries(fields).forEach(([key, column]) => { point[key] += n(row[column]); });
    byDay.set(date, point);
  });
  return [...byDay.values()].sort((left, right) => left.date.localeCompare(right.date));
}

function totals(points) {
  return points.reduce((sum, point) => {
    Object.keys(fields).forEach(key => { sum[key] += point[key]; });
    return sum;
  }, blankDay(""));
}

function simulationCampaigns(context) {
  return (context.campaigns || []).map(campaign => ({
    ...campaign, ...totals(pointsFrom(campaign.daily)), label: simulatedTitles[campaign.id],
    adResourceId: "demo-resource-" + campaign.id, campaign: campaign.campaignId
  }));
}

const amount = (value, simulated) => simulated ? money(value) : value == null ? "—" : n(value).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const rangeText = points => points.length ? `${points[0].date} 至 ${points.at(-1).date}` : "暂无数据";

function reportCards(points, simulated) {
  const summary = totals(points);
  const profit = summary.revenue - summary.spend;
  const margin = summary.revenue > 0 ? `${(profit / summary.revenue * 100).toFixed(1)}% 利润率` : "收入 / 花费差额";
  const ctr = ratio(summary.clicks, summary.impressions);
  const cvr = ratio(summary.conversions, summary.clicks);
  const spendSeries = points.map(p => p.spend);
  const revSeries = points.map(p => p.revenue);
  const profitSeries = points.map(p => p.revenue - p.spend);
  const roasSeries = points.map(p => (p.spend > 0 ? p.revenue / p.spend : 0));
  return `<div class="cards">
    ${metric("广告花费", amount(summary.spend, simulated), `${numeric(summary.impressions)} 展示 · ${numeric(summary.clicks)} 点击`, sparklineSvg(spendSeries, "indigo"))}
    ${metric("总收入", amount(summary.revenue, simulated), `CTR ${percent(ctr)} · CVR ${percent(cvr)}`, sparklineSvg(revSeries, "teal"))}
    ${metric("净利润", `${profit >= 0 ? "+" : ""}${amount(profit, simulated)}`, margin, sparklineSvg(profitSeries, profit >= 0 ? "teal" : "red"))}
    ${metric("综合 ROAS", multiple(ratio(summary.revenue, summary.spend)), `${numeric(summary.conversions)} 次转化`, sparklineSvg(roasSeries, "teal"))}
  </div>`;
}

function chartPanel(points) {
  return `<section class="panel"><div class="panel-head"><div><h2>花费与收入趋势</h2><p class="sub">${e(rangeText(points))}</p></div>
    <div class="trend-legend"><span>花费</span><span>收入</span></div></div>${
    points.length ? '<canvas class="chart" tabindex="0" aria-label="按日期显示广告花费和收入"></canvas><div class="chart-caption" aria-live="polite">悬停或按方向键查看每日数据</div>' : '<div class="placeholder">选择报表条件查询投放数据。</div>'
  }</section>`;
}

function showChart(target, points, simulated) {
  chartCleanup();
  const canvas = $(".chart", target);
  chartCleanup = points.length && canvas ? mountChart(canvas, $(".chart-caption", target), points, value => amount(value, simulated)) : () => {};
}

export function releaseChart() { chartCleanup(); }

export function clearCmsData() {
  state.reports.points = null;
  for (const key of ["campaigns", "rules", "settings"]) {
    state[key].data = null;
    state[key].page = 1;
  }
}

async function launchDiagnosis(campaignId, button) {
  await busy(button, async () => {
    const run = await request(`/cms/agent/runs/campaigns/${encodeURIComponent(campaignId)}`, {
      body: { mode: "STANDARD", targetRoas: 1.25 }
    });
    toast(`已为 ${campaignId} 发起智能诊断`);
    focusAgentRun(run.id);
  });
}

function datePresetsMarkup() {
  return `<div class="preset-group" aria-label="快捷日期">
    <button type="button" class="button secondary small" data-days="7">近 7 天</button>
    <button type="button" class="button secondary small" data-days="14">近 14 天</button>
    <button type="button" class="button secondary small" data-days="30">近 30 天</button>
  </div>`;
}

function wireDatePresets(form, onPick) {
  form.querySelectorAll("[data-days]").forEach(btn => {
    btn.onclick = () => {
      const days = Number(btn.dataset.days);
      const end = form.elements.end.value || isoShift("", -1);
      form.elements.end.value = end;
      form.elements.start.value = isoShift(end, -(days - 1));
      onPick();
    };
  });
}

export function overview(target, context, runs, isCurrent = () => true) {
  const simulated = context.mode === "simulation";
  if (context.localCms && !simulated) {
    const initialRange = defaultDateRange(7);
    target.innerHTML = pageHead("运营总览", "实时掌握投放花费、收入、利润趋势与智能预警待办。",
      '<button type="button" class="button secondary" id="ov-open-scan">异常巡检</button><a class="button" href="#reports">投放报表</a>')
      + `<section class="panel"><form class="toolbar catalog-search">
          <label class="publisher-field">AdSense 发布商<select name="publisher"></select></label>
          <label>开始日期<input type="date" name="start" value="${e(initialRange.start)}" required></label>
          <label>结束日期<input type="date" name="end" value="${e(initialRange.end)}" required></label>
          <button type="submit" class="button">查询</button>
          ${datePresetsMarkup()}
        </form></section>
        <div class="overview-results"><div class="placeholder">正在读取投放数据…</div></div>
        <div id="ov-ops-insights"></div>
        <section class="panel" id="ov-top-campaigns"><div class="panel-head"><div><h2>高消耗活动表现</h2><p class="sub">按花费排序的在投活动，支持一键发起智能诊断。</p></div><a href="#campaigns" class="table-link">管理全部活动</a></div><div id="ov-campaigns-body"><div class="placeholder">正在加载活动数据…</div></div></section>`;

    $("#ov-open-scan", target).onclick = () => openAgentTab("scan");
    mountOverviewInsights($("#ov-ops-insights", target), launchDiagnosis);
    const form = $("form", target);
    const output = $(".overview-results", target);
    const campBody = $("#ov-campaigns-body", target);
    let revision = 0;
    let timer;
    let opsSnapshot = null;

    function opsPanelMarkup() {
      const counts = opsSnapshot?.scan?.counts || {};
      const pending = Number(opsSnapshot?.telemetry?.runs?.pendingRuns || 0);
      return `<section class="panel agent-preview">
        <div class="feature-icon" aria-hidden="true">✦</div>
        <h2>智能运营待办</h2>
        <p class="sub">自动巡检投放异常、放量机会与待审批优化建议。</p>
        <ul class="queue">
          <li><span>待审批建议</span><strong>${badge("PENDING")} ${numeric(pending)}</strong></li>
          <li><span>急需止损活动</span><strong>${toneBadge("高优先级", "red")} ${numeric(counts.criticalBleed || 0)}</strong></li>
          <li><span>高潜放量机会</span><strong>${toneBadge("可放量", "teal")} ${numeric(counts.scaleOpportunity || 0)}</strong></li>
          <li><span>缺规则护栏</span><strong>${toneBadge("需补齐", "amber")} ${numeric(counts.ruleUnbound || 0)}</strong></li>
        </ul>
        <div class="action-set">
          <button type="button" class="button small" id="ov-go-agent">处理待审批</button>
          <button type="button" class="button secondary small" id="ov-go-copilot">智能副驾驶</button>
        </div>
      </section>`;
    }

    function wireOpsButtons() {
      const goAgent = $("#ov-go-agent", output);
      const goCopilot = $("#ov-go-copilot", output);
      if (goAgent) goAgent.onclick = () => openAgentTab("workflow");
      if (goCopilot) goCopilot.onclick = () => openAgentTab("copilot");
    }

    async function loadOpsWidgets() {
      try {
        const [scan, telemetry, topCamps] = await Promise.all([
          request("/cms/agent/scan", { body: { targetRoas: 1.25, minimumSpend: 30 } }).catch(() => null),
          request("/cms/agent/telemetry").catch(() => null),
          requestList("/cms/agent/campaigns?limit=8").catch(() => [])
        ]);
        if (!isCurrent() || !campBody.isConnected) return;
        opsSnapshot = { scan, telemetry };
        const existingSide = $(".agent-preview", output);
        if (existingSide) {
          existingSide.outerHTML = opsPanelMarkup();
          wireOpsButtons();
        }
        campBody.innerHTML = table(
          ["活动名称", "平台", "花费", "收入", "利润", "ROAS", "规则状态", "操作"],
          topCamps.map(c => [
            `<strong>${e(c.name || c.campaignId)}</strong><br><small class="muted mono">${e(c.campaignId)}</small>`,
            e(c.platform || "—"),
            `<span class="mono">${e(money(c.spend, c.currency))}</span>`,
            `<span class="mono">${e(money(c.revenue, c.currency))}</span>`,
            profitDisplay(c.profit, c.currency),
            roasBadge(c.roas),
            c.ruleBound ? toneBadge(c.ruleName || `±${c.ruleBudgetCap}%`, "teal") : toneBadge("未绑定", "amber"),
            `<button type="button" class="table-link" data-quick-diagnose="${e(c.campaignId)}">智能诊断</button>`
          ]),
          "暂无在投活动数据"
        );
        campBody.querySelectorAll("[data-quick-diagnose]").forEach(btn => {
          btn.onclick = () => launchDiagnosis(btn.dataset.quickDiagnose, btn);
        });
      } catch { /* non-blocking widget */ }
    }

    async function refresh(resetDates = false) {
      if (!isCurrent() || !output.isConnected) return;
      clearTimeout(timer);
      const current = ++revision;
      try {
        const pubId = form.elements.publisher.value;
        if (resetDates) {
          const dates = pubId ? await request("/cms/console/report-defaults?" + new URLSearchParams({ pubId })) : {};
          if (!isCurrent() || revision !== current) return;
          const fallback = defaultDateRange(7);
          form.elements.start.value = dates.start || fallback.start;
          form.elements.end.value = dates.end || fallback.end;
        }
        const { start, end } = formValues(form);
        if (start && end && start > end) throw new Error("结束日期不能早于开始日期");
        const data = pubId && start && end ? await request("/cms/ad/data/filter/list", {
          body: { pubId, dateStart: start, dateEnd: end }
        }) : null;
        if (!isCurrent() || revision !== current) return;
        const points = pointsFrom(data?.list || []);
        output.innerHTML = (points.length ? reportCards(points, false) : "")
          + `<div class="dashboard-grid">${points.length ? chartPanel(points) : '<section class="panel"><div class="placeholder">所选发布商和日期范围内暂无投放数据。</div></section>'}${opsPanelMarkup()}</div>`;
        showChart(output, points, false);
        wireOpsButtons();
      } catch (error) {
        if (isCurrent() && revision === current) errorIn(output, error.message);
      } finally {
        if (isCurrent() && revision === current) timer = setTimeout(refresh, 60_000);
      }
    }
    form.onsubmit = event => { event.preventDefault(); refresh(); };
    form.elements.publisher.onchange = () => refresh(true);
    wireDatePresets(form, () => refresh());
    loadOpsWidgets();
    Promise.all([request("/cms/console/report-options"), request("/cms/console/report-defaults")]).then(([options, defaults]) => {
      if (!isCurrent()) return;
      const select = form.elements.publisher;
      setReportChoices(select, options.publishers.map(id => [id, id]), "请选择发布商");
      select.value = options.publishers.includes(defaults.pubId) ? defaults.pubId : options.publishers[0] || "";
      refresh(true);
    }).catch(error => { if (isCurrent()) errorIn(output, error.message); });
    return;
  }
  if (simulated) {
    const data = context.archiveData || {};
    const records = data.metrics || [];
    const pvByDay = new Map();
    records.forEach(row => {
      const date = dateKey(row.date);
      if (date) pvByDay.set(date, (pvByDay.get(date) || 0) + n(row.PV));
    });
    const points = [...pvByDay.keys()].sort().map(date => ({ date, pv: pvByDay.get(date) }));
    target.innerHTML = pageHead("运营总览", "实时掌握第三方广告平台消耗、收入、利润与 ROAS 表现。",
      '<a class="button" href="#reports">查看投放报表</a>')
      + `<div class="cards">${metric("广告统计记录", numeric(records.length))}${metric("PV 合计", numeric(records.reduce((sum, row) => sum + n(row.PV), 0)))}
        ${metric("应用数", numeric(data.applications?.length))}${metric("文章数", numeric(data.articles?.length))}</div>
      <section class="panel"><div class="panel-head"><div><h2>每日 PV 趋势</h2><p class="sub">${e(rangeText(points))}</p></div></div>${
        points.length ? '<canvas class="chart" tabindex="0" aria-label="按日期显示 PV"></canvas><div class="chart-caption" aria-live="polite">悬停或按方向键查看每日数据</div>' : '<div class="placeholder">暂无按日期统计的 PV 数据。</div>'
      }</section>`;
    releaseChart();
    chartCleanup = points.length ? mountChart($(".chart", target), $(".chart-caption", target), points, numeric,
      { fields: ["pv"], labels: ["PV"], colors: ["#2aa98c"] }) : () => {};
    return;
  }
  const campaigns = simulationCampaigns(context);
  const points = simulated ? pointsFrom(campaigns.flatMap(campaign => campaign.daily)) : state.reports.points || [];
  target.innerHTML = pageHead("运营总览", "查看投放数据、广告活动和优化进度。",
    '<a class="button secondary" href="#reports">投放报表</a><a class="button" href="#agent">智能体工作台</a>')
    + (points.length ? reportCards(points, simulated) : "")
    + `<div class="dashboard-grid">${chartPanel(points)}
      <section class="panel agent-preview"><div class="feature-icon" aria-hidden="true">✦</div><h2>智能体工作台</h2>
      <p class="sub">投放诊断、异常巡检、智能副驾驶与预算审批。</p>
      <ul class="queue">${["PENDING", "APPROVED", "SIMULATED"].map(status =>
        `<li>${badge(status)}<strong>${runs.filter(run => run.state === status).length}</strong></li>`).join("")}</ul>
      <a class="button" href="#agent">进入工作台</a></section></div>`;
  showChart(target, points, simulated);
}

function campaignTable(rows, simulated, context) {
  const selectable = !simulated && context?.localCms;
  return table(
    [
      ...(selectable ? [{ html: '<input type="checkbox" id="camp-select-all" aria-label="全选当前页活动">', className: "col-check" }] : []),
      "广告活动", "平台", "花费", "收入", "利润", "ROAS", simulated ? "场景" : "投放规则",
      ...(context ? ["审核状态", "同步状态", "操作"] : [])
    ],
    rows.map((row, index) => {
      const spend = simulated ? row.spend : n(row.adSpend);
      const revenue = simulated ? row.revenue : n(row.grossRevenue);
      const profit = simulated ? (revenue - spend) : (row.profit != null ? n(row.profit) : revenue - spend);
      const roas = ratio(revenue, spend);
      const stateInfo = campaignStateMeta[row.state] || [row.state || "—", "gray"];
      const platMeta = row.platformCampaign
        ? ["已同步", "teal"]
        : (platformStateMeta[row.adPlatformState] || [row.adPlatformState || "—", "gray"]);
      const diagBtn = context?.localCms && row.adResourceId
        ? `<button type="button" class="table-link" data-diagnose-campaign="${e(row.adResourceId)}">诊断</button>` : "";
      const cloneBtn = context?.localCms && row.adResourceId
        ? `<button type="button" class="table-link" data-clone-campaign="${e(row.adResourceId)}">复制</button>` : "";
      return [
        ...(selectable ? [`<input type="checkbox" class="camp-select-row" value="${e(row.adResourceId)}" aria-label="选择活动 ${e(row.campaign || row.adResourceId)}">`] : []),
        `<strong>${e(simulated ? row.campaignId : row.campaign || row.title || row.adResourceId)}</strong>${!simulated && row.adResourceId ? `<br><small class="muted mono">${e(row.adResourceId)}</small>` : ""}`,
        e(simulated ? row.channel : (Array.isArray(row.adPlatform) ? row.adPlatform.join(" / ") : row.adPlatform) || row.channelId || "—"),
        `<span class="mono">${e(amount(spend, simulated))}</span>`,
        `<span class="mono">${e(amount(revenue, simulated))}</span>`,
        profitDisplay(profit),
        roasBadge(roas),
        simulated ? e(row.label) : (row.ruleName ? toneBadge(row.ruleName, "teal") : '<span class="muted">未绑定</span>'),
        ...(context ? [
          toneBadge(stateInfo[0], stateInfo[1]),
          toneBadge(platMeta[0], platMeta[1]),
          `<div class="record-actions">${diagBtn}${cloneBtn}${businessActions("campaigns", context, row, index)}</div>`
        ] : [])
      ];
    }),
    "没有符合条件的广告活动"
  );
}

export function reports(target, context) {
  const simulated = context.mode === "simulation";
  const campaigns = simulationCampaigns(context);
  const model = state.reports;
  const currentRows = campaigns.filter(campaign => !model.campaign || campaign.campaignId === model.campaign).flatMap(campaign => campaign.daily);
  const sample = pointsFrom(currentRows);
  const points = simulated ? sample.filter(point => (!model.start || point.date >= model.start) && (!model.end || point.date <= model.end)) : model.points || [];
  const options = campaigns.map(campaign => `<option value="${e(campaign.campaignId)}" ${model.campaign === campaign.campaignId ? "selected" : ""}>${e(campaign.campaignId)}</option>`).join("");
  const summary = totals(points);
  const dailyRows = points.map(point => {
    const profit = point.revenue - point.spend;
    const ctr = ratio(point.clicks, point.impressions);
    const cvr = ratio(point.conversions, point.clicks);
    const cpa = ratio(point.spend, point.conversions);
    const roas = ratio(point.revenue, point.spend);
    return [
      `<span class="mono">${e(point.date)}</span>`,
      `<span class="mono">${e(amount(point.spend, simulated))}</span>`,
      `<span class="mono">${e(amount(point.revenue, simulated))}</span>`,
      profitDisplay(profit),
      `<span class="mono">${e(numeric(point.impressions))}</span>`,
      `<span class="mono">${e(numeric(point.clicks))}</span>`,
      `<span class="mono">${e(percent(ctr))}</span>`,
      `<span class="mono">${e(numeric(point.conversions))}</span>`,
      `<span class="mono">${e(percent(cvr))}</span>`,
      `<span class="mono">${cpa == null ? "—" : e(amount(cpa, simulated))}</span>`,
      roasBadge(roas)
    ];
  });
  if (points.length) {
    const totalProfit = summary.revenue - summary.spend;
    dailyRows.push([
      "<strong>合计</strong>",
      `<strong class="mono">${e(amount(summary.spend, simulated))}</strong>`,
      `<strong class="mono">${e(amount(summary.revenue, simulated))}</strong>`,
      `<strong>${profitDisplay(totalProfit)}</strong>`,
      `<strong class="mono">${e(numeric(summary.impressions))}</strong>`,
      `<strong class="mono">${e(numeric(summary.clicks))}</strong>`,
      `<strong class="mono">${e(percent(ratio(summary.clicks, summary.impressions)))}</strong>`,
      `<strong class="mono">${e(numeric(summary.conversions))}</strong>`,
      `<strong class="mono">${e(percent(ratio(summary.conversions, summary.clicks)))}</strong>`,
      `<strong class="mono">${ratio(summary.spend, summary.conversions) == null ? "—" : e(amount(ratio(summary.spend, summary.conversions), simulated))}</strong>`,
      roasBadge(ratio(summary.revenue, summary.spend))
    ]);
  }

  const initialRange = defaultDateRange(7);
  const startVal = model.start || initialRange.start;
  const endVal = model.end || initialRange.end;
  target.innerHTML = pageHead("投放报表", "按发布商、活动和日期分析花费、收入、利润、转化与 ROAS 趋势。",
    '<a class="button secondary" href="#platform-reference">海外平台参考</a>'
    + (points.length ? '<button type="button" class="button secondary" id="export-report-csv">导出 CSV</button>' : "")
    + businessActions("reports", context)) + `<section class="panel filters">
    <form id="report-filters" class="form-grid">
      ${simulated ? `<label>广告活动<select name="campaign"><option value="">全部活动</option>${options}</select></label>` :
    `<label class="publisher-field">AdSense 发布商<select name="pubId" required><option value="">正在读取…</option></select></label>
      <label>广告活动<select name="campaign"><option value="">全部活动</option></select></label>`}
      <label>开始日期<input type="date" name="start" value="${e(startVal)}" ${simulated ? "" : "required"}></label>
      <label>结束日期<input type="date" name="end" value="${e(endVal)}" ${simulated ? "" : "required"}></label>
      <button class="button" type="submit" ${simulated ? "" : "disabled"}>查询</button>
      <div class="all">${datePresetsMarkup()}</div>
    </form><div id="report-error"></div></section>`
    + (points.length ? reportCards(points, simulated) : "")
    + chartPanel(points) + `<section class="panel"><div class="panel-head"><h2>逐日报表</h2><span class="sub">${points.length ? `共 ${points.length} 天` : ""}</span></div>${
      table(["日期", "花费", "收入", "利润", "展示", "点击", "CTR", "转化", "CVR", "CPA", "ROAS"], dailyRows,
      "没有符合条件的逐日数据")}</section>`;

  showChart(target, points, simulated);
  wireBusinessActions(target, context, "reports");

  const exportBtn = $("#export-report-csv", target);
  if (exportBtn) {
    exportBtn.onclick = () => {
      exportCsv(`afs-daily-report-${model.start || "all"}-${model.end || "all"}.csv`,
        ["日期", "花费", "收入", "利润", "展示", "点击", "CTR", "转化", "CVR", "CPA", "ROAS"],
        points.map(pt => [
          pt.date, pt.spend.toFixed(2), pt.revenue.toFixed(2), (pt.revenue - pt.spend).toFixed(2),
          pt.impressions, pt.clicks, percent(ratio(pt.clicks, pt.impressions)),
          pt.conversions, percent(ratio(pt.conversions, pt.clicks)),
          ratio(pt.spend, pt.conversions)?.toFixed(2) ?? "",
          ratio(pt.revenue, pt.spend)?.toFixed(4) ?? ""
        ]));
      toast("报表 CSV 已导出");
    };
  }

  const form = $("#report-filters", target);
  wireDatePresets(form, () => form.requestSubmit());
  form.onsubmit = event => {
    event.preventDefault();
    const values = formValues(form);
    if (values.start && values.end && values.start > values.end) {
      errorIn($("#report-error", target), "结束日期必须晚于或等于开始日期");
      return;
    }
    busy($('button[type="submit"]', form), async () => {
      if (!simulated) {
        const data = await request("/cms/ad/data/filter/list", { body: { pubId: values.pubId, dateStart: values.start,
          dateEnd: values.end, campaignId: values.campaign ? [values.campaign] : [] } });
        model.points = pointsFrom(data?.list || []);
      }
      Object.assign(model, values);
      if (form.isConnected) reports(target, context);
    }, message => errorIn($("#report-error", target), message));
  };
  if (!simulated) {
    const defaultsRequest = model.points ? Promise.resolve({}) : request("/cms/console/report-defaults");
    Promise.all([request("/cms/console/report-options"), defaultsRequest]).then(([options, defaults]) => {
      if (!form.isConnected) return;
      wireReportChoices(form, options, model.pubId || defaults.pubId, form.elements.campaign);
      form.elements.campaign.value = model.campaign || "";
      if (form.elements.campaign.selectedIndex < 0) form.elements.campaign.value = "";
      $('button[type="submit"]', form).disabled = !options.publishers.length;
      if (!options.publishers.length) {
        errorIn($("#report-error", target), "当前账号没有可查询的发布商和活动");
        return;
      }
      if (model.points || !defaults.start || !defaults.end) return;
      form.elements.start.value = model.start || defaults.start;
      form.elements.end.value = model.end || defaults.end;
      form.requestSubmit();
    }).catch(error => {
      if (form.isConnected) errorIn($("#report-error", target), error.message);
    });
  }
}

const endpoints = {
  campaigns: ["/cms/ad/resource/list", "campaign"],
  rules: ["/cms/ad/resource/rule/list", "name"],
  settings: ["/cms/config/list", "site"]
};

async function loadPage(view: string, page: number, filter: string, extras: Record<string, any> = {}) {
  const [endpoint, field] = endpoints[view];
  const pagination = { pageIndex: page, pageSize: size };
  const paginationQuery = { pageIndex: String(page), pageSize: String(size) };
  const body: Record<string, any> = { [field]: filter || undefined, ...(view === "settings" ? pagination : {}) };
  if (view === "campaigns") {
    if (extras.adPlatform) body.adPlatform = extras.adPlatform;
    if (extras.state) body.state = extras.state;
    if (extras.orderParam) {
      body.orderParam = extras.orderParam;
      body.orderDirection = "desc";
    }
  }
  const response = await request(view === "settings" ? endpoint : `${endpoint}?${new URLSearchParams(paginationQuery)}`, { body });
  Object.assign(state[view], { page, filter, ...extras, data: response });
}

function pager(page, total) {
  const last = Math.max(1, Math.ceil(total / size));
  return `<div class="pagination"><button class="button secondary small" type="button" data-page="${page - 1}" ${page <= 1 ? "disabled" : ""}>上一页</button>
    <span>第 ${page} / ${last} 页 · 共 ${numeric(total)} 条</span><button class="button secondary small" type="button" data-page="${page + 1}" ${page >= last ? "disabled" : ""}>下一页</button></div>`;
}

const viewMeta = {
  campaigns: ["广告活动", "管理全平台投放活动、盈亏表现、批量操作、规则绑定与智能诊断。", "活动名称"],
  rules: ["投放规则", "统一管理自动化调价规则、实时预警推送（飞书/钉钉/企微）与 7×24 分时排期。", "规则名称"],
  settings: ["站点配置", "按站点查看和维护业务配置项。", "站点标识"]
};

export async function collection(target, context, view, isCurrent) {
  const simulated = context.mode === "simulation";
  const model = state[view];
  const [title, subtitle, filterTitle] = viewMeta[view];

  // Rules view supports 3 integrated strategy tabs: auto rules / alert rules / 7x24 dayparting
  if (view === "rules" && !simulated) {
    const activeTab = model.tab || "auto";
    const tabBar = `<div class="agent-tabs" id="rules-subtabs" role="tablist">
      <button type="button" class="tab-btn" aria-pressed="${activeTab === "auto"}" data-rules-tab="auto">自动化调价规则</button>
      <button type="button" class="tab-btn" aria-pressed="${activeTab === "alerts"}" data-rules-tab="alerts">实时预警与通知</button>
      <button type="button" class="tab-btn" aria-pressed="${activeTab === "dayparting"}" data-rules-tab="dayparting">7×24 分时排期</button>
    </div>`;
    if (activeTab !== "auto") {
      target.innerHTML = pageHead(title, subtitle, "") + tabBar + `<section class="panel" id="rules-ext-panel"></section>`;
      target.querySelectorAll("[data-rules-tab]").forEach((btn: HTMLButtonElement) => {
        btn.onclick = () => {
          model.tab = btn.dataset.rulesTab || "auto";
          collection(target, context, view, isCurrent);
        };
      });
      const panel = $("#rules-ext-panel", target);
      if (activeTab === "alerts") await renderAlertCenter(panel);
      else await renderDaypartingMatrix(panel);
      return;
    }
  }

  const exportBtn = view === "campaigns" && !simulated
    ? '<button type="button" class="button secondary" id="export-campaigns-csv">导出当前页 CSV</button>' : "";
  const actions = view === "settings" ? '<a class="button secondary" href="#reportconfig">报表配置</a>'
    + (!simulated ? '<button type="button" class="button" id="add-config">新增配置</button>' : "")
    : exportBtn + businessActions(view, context);

  const campaignExtras = view === "campaigns" && !simulated ? `
    <label>平台<select name="adPlatform">
      <option value="">全部平台</option>
      ${adPlatforms.map(p => `<option value="${e(p)}" ${model.adPlatform === p ? "selected" : ""}>${e(p)}</option>`).join("")}
    </select></label>
    <label>审核状态<select name="state">
      <option value="">全部状态</option>
      ${Object.entries(campaignStateMeta).map(([k, [lbl]]) => `<option value="${e(k)}" ${model.state === k ? "selected" : ""}>${e(lbl)}</option>`).join("")}
    </select></label>
    <label>排序<select name="orderParam">
      <option value="">默认排序</option>
      <option value="adSpend" ${model.orderParam === "adSpend" ? "selected" : ""}>花费从高到低</option>
      <option value="grossRevenue" ${model.orderParam === "grossRevenue" ? "selected" : ""}>收入从高到低</option>
      <option value="profit" ${model.orderParam === "profit" ? "selected" : ""}>利润从高到低</option>
      <option value="roi" ${model.orderParam === "roi" ? "selected" : ""}>ROAS 从高到低</option>
    </select></label>` : "";

  const rulesTabBar = view === "rules" && !simulated ? `<div class="agent-tabs" id="rules-subtabs" role="tablist">
    <button type="button" class="tab-btn" aria-pressed="true" data-rules-tab="auto">自动化调价规则</button>
    <button type="button" class="tab-btn" aria-pressed="false" data-rules-tab="alerts">实时预警与通知</button>
    <button type="button" class="tab-btn" aria-pressed="false" data-rules-tab="dayparting">7×24 分时排期</button>
  </div>` : "";

  const batchBar = view === "campaigns" && !simulated && context.localCms ? `
    <div class="batch-toolbar hidden" id="camp-batch-bar">
      <span>已选 <strong id="camp-batch-count" class="mono">0</strong> 个活动</span>
      <div class="action-set">
        <button type="button" class="button small" id="batch-bind-rule">批量绑定规则</button>
        <button type="button" class="button secondary small" id="batch-pass">批量审核通过</button>
        <button type="button" class="button secondary small" id="batch-archive">批量归档</button>
        <button type="button" class="button secondary small" id="batch-export-csv">导出选中 CSV</button>
        <button type="button" class="button secondary small" id="batch-clear">取消选择</button>
      </div>
    </div>` : "";

  target.innerHTML = pageHead(title, subtitle, actions) + rulesTabBar + `<section class="panel">
    <form class="toolbar catalog-search" id="collection-search">${listSearch(filterTitle, model.filter, view === "settings" && !simulated, campaignExtras)}</form>
    ${batchBar}
    <div id="collection-error" aria-live="polite"></div>
    <div id="collection-results"><div class="placeholder">正在读取…</div></div></section>`;

  if (view === "rules" && !simulated) {
    target.querySelectorAll("[data-rules-tab]").forEach((btn: HTMLButtonElement) => {
      btn.onclick = () => {
        model.tab = btn.dataset.rulesTab || "auto";
        collection(target, context, view, isCurrent);
      };
    });
  }
  const form = $("#collection-search", target);
  const results = $("#collection-results", target);
  let currentFilter = model.filter;
  let currentExtras = view === "campaigns"
    ? { adPlatform: model.adPlatform || "", state: model.state || "", orderParam: model.orderParam || "" }
    : {};

  function render() {
    if (!isCurrent() || !results.isConnected) return;
    const simulatedRows = { campaigns: [], rules: [], settings: [] };
    const rows = simulated ? simulatedRows[view].filter(row =>
      String(row.campaignId || (view === "settings" ? row.site : row.name)).toLowerCase().includes(currentFilter.toLowerCase())) : model.data?.list || [];
    let markup;
    if (view === "campaigns") markup = campaignTable(rows, simulated, context);
    if (view === "rules") markup = table(["规则名称", "类型", "执行周期", "预算调整上限", "创建人", "操作"], rows.map((row, index) => [
      `<button type="button" class="table-link" data-rule="${index}">${e(row.name)}</button>`,
      toneBadge(ruleTypeLabels[row.type] || row.type || "—", "gray"),
      e(ruleCycleLabels[row.actionCycle] || row.actionCycle || "—"),
      row.budgetChange == null ? "—" : toneBadge(`±${row.budgetChange}%`, "teal"),
      e(row.creator || "—"),
      businessActions(view, context, row, index)
    ]), "没有符合条件的投放规则");
    if (view === "settings") markup = table(["站点", "配置键", "说明", "操作"],
      rows.map((row, index) => [e(row.site), `<code>${e(row.name)}</code>`, e(row.desc), `<button type="button" class="table-link" data-config="${index}">${simulated ? "查看" : "编辑"}</button>`]),
      "所选站点暂无配置");
    results.innerHTML = markup + (!simulated && model.data ? pager(model.page, n(model.data.totalSize)) : "");
    results.querySelectorAll("[data-rule]").forEach(button => button.onclick = () => {
      const row = rows[Number(button.dataset.rule)];
      dialog("规则明细", `<p class="sub">${e(row.name)} · ${e(ruleTypeLabels[row.type] || row.type)} · 预算变动上限 ±${e(row.budgetChange ?? "—")}%</p>
        ${ruleSummary(row.rule)}`);
    });
    results.querySelectorAll("[data-config]").forEach(button =>
      button.onclick = () => editConfig(rows[Number(button.dataset.config)], simulated, refresh));
    results.querySelectorAll("[data-page]").forEach(button =>
      button.onclick = () => busy(button, () => refresh(Number(button.dataset.page)), message => errorIn(results, message)));
    results.querySelectorAll("[data-diagnose-campaign]").forEach(button => {
      button.onclick = () => launchDiagnosis(button.dataset.diagnoseCampaign, button);
    });
    results.querySelectorAll("[data-clone-campaign]").forEach(button => {
      button.onclick = () => busy(button, async () => {
        const res = await request(`/cms/agent/ops/campaigns/${encodeURIComponent(button.dataset.cloneCampaign)}/clone`, { body: {} });
        toast(`已复制活动：${res.campaignName || res.newAdResourceId}`);
        await refresh(1);
      });
    });

    if (view === "campaigns" && !simulated && context.localCms) {
      const batchBarEl = $("#camp-batch-bar", target);
      const countEl = $("#camp-batch-count", target);
      const selectAll = $("#camp-select-all", results) as HTMLInputElement | null;
      const rowChecks = Array.from(results.querySelectorAll(".camp-select-row")) as HTMLInputElement[];

      const selectedIds = () => rowChecks.filter(c => c.checked).map(c => c.value);
      const syncBatchBar = () => {
        const ids = selectedIds();
        if (countEl) countEl.textContent = String(ids.length);
        if (batchBarEl) batchBarEl.classList.toggle("hidden", ids.length === 0);
        if (selectAll) selectAll.checked = rowChecks.length > 0 && ids.length === rowChecks.length;
      };
      if (selectAll) {
        selectAll.onchange = () => {
          rowChecks.forEach(c => { c.checked = selectAll.checked; });
          syncBatchBar();
        };
      }
      rowChecks.forEach(c => { c.onchange = syncBatchBar; });
      syncBatchBar();

      const clearBtn = $("#batch-clear", target);
      if (clearBtn) clearBtn.onclick = () => { rowChecks.forEach(c => { c.checked = false; }); syncBatchBar(); };

      const passBtn = $("#batch-pass", target);
      if (passBtn) passBtn.onclick = () => busy(passBtn, async () => {
        const ids = selectedIds();
        if (!ids.length) return;
        const res = await request("/cms/agent/ops/campaigns/batch", { body: { adResourceIds: ids, action: "SET_STATE", state: "PASS" } });
        toast(`已批量通过 ${res.affected} 个活动`);
        await refresh();
      });

      const archBtn = $("#batch-archive", target);
      if (archBtn) archBtn.onclick = () => busy(archBtn, async () => {
        const ids = selectedIds();
        if (!ids.length) return;
        const res = await request("/cms/agent/ops/campaigns/batch", { body: { adResourceIds: ids, action: "SET_STATE", state: "ARCHIVE" } });
        toast(`已批量归档 ${res.affected} 个活动`);
        await refresh();
      });

      const bindBtn = $("#batch-bind-rule", target);
      if (bindBtn) bindBtn.onclick = () => busy(bindBtn, async () => {
        const ids = selectedIds();
        if (!ids.length) return;
        const rulePage = await request("/cms/ad/resource/rule/list?pageIndex=1&pageSize=100", { body: {} });
        const ruleList = rulePage?.list || [];
        const dlg = dialog("批量绑定投放规则", `<form class="form-grid">
          <p class="sub all">已选择 <strong>${ids.length}</strong> 个广告活动，选择要统一绑定的自动化规则：</p>
          <label class="all">目标投放规则<select name="ruleId" required>
            <option value="0">解除规则绑定</option>
            ${ruleList.map((r: any) => `<option value="${r.id}">${e(r.name)}（±${e(r.budgetChange ?? 0)}%）</option>`).join("")}
          </select></label>
          <div class="form-actions all"><button type="submit" class="button">确认批量应用</button></div>
        </form>`);
        $("form", dlg).onsubmit = (ev) => {
          ev.preventDefault();
          const ruleId = Number((ev.currentTarget as HTMLFormElement).elements.namedItem("ruleId") ? ((ev.currentTarget as HTMLFormElement).elements.namedItem("ruleId") as HTMLSelectElement).value : 0);
          busy($('button[type="submit"]', dlg), async () => {
            const action = ruleId > 0 ? "BIND_RULE" : "UNBIND_RULE";
            const res = await request("/cms/agent/ops/campaigns/batch", { body: { adResourceIds: ids, action, ruleId } });
            closeDialog();
            toast(`已批量更新 ${res.affected} 个活动的规则绑定`);
            await refresh();
          });
        };
      });

      const batchCsv = $("#batch-export-csv", target);
      if (batchCsv) batchCsv.onclick = () => {
        const idSet = new Set(selectedIds());
        const picked = rows.filter(r => idSet.has(r.adResourceId));
        if (!picked.length) return;
        exportCsv(`afs-campaigns-selected-${picked.length}.csv`,
          ["活动 ID", "活动名称", "平台", "花费", "收入", "利润", "ROAS", "规则", "审核状态"],
          picked.map(r => [
            r.adResourceId, r.campaign || r.title, Array.isArray(r.adPlatform) ? r.adPlatform.join("/") : r.adPlatform,
            n(r.adSpend).toFixed(2), n(r.grossRevenue).toFixed(2),
            (r.profit != null ? n(r.profit) : n(r.grossRevenue) - n(r.adSpend)).toFixed(2),
            ratio(n(r.grossRevenue), n(r.adSpend))?.toFixed(4) ?? "",
            r.ruleName || "", (campaignStateMeta[r.state] || [r.state])[0]
          ]));
        toast(`已导出选中的 ${picked.length} 个活动 CSV`);
      };
    }
    wireBusinessActions(results, context, view, rows, () => refresh());
  }

  async function refresh(page = model.page) {
    if (!simulated) await loadPage(view, page, currentFilter, currentExtras);
    render();
  }

  const searchError = $("#collection-error", target);
  if (view === "settings" && !simulated) loadSiteFilter(form, model.filter, message => errorIn(searchError, message));
  wireListSearch(form, async value => {
    searchError.replaceChildren();
    currentFilter = value;
    if (view === "campaigns" && !simulated) {
      currentExtras = {
        adPlatform: form.elements.adPlatform?.value || "",
        state: form.elements.state?.value || "",
        orderParam: form.elements.orderParam?.value || ""
      };
    }
    if (simulated) model.filter = currentFilter;
    await refresh(1);
  }, message => errorIn(searchError, message));

  if (view === "campaigns" && !simulated) {
    for (const selName of ["adPlatform", "state", "orderParam"]) {
      const sel = form.elements[selName];
      if (sel) sel.onchange = () => form.requestSubmit();
    }
    const clearBtn = $("[data-clear-search]", form);
    if (clearBtn) {
      clearBtn.onclick = () => {
        form.elements.filter.value = "";
        if (form.elements.adPlatform) form.elements.adPlatform.value = "";
        if (form.elements.state) form.elements.state.value = "";
        if (form.elements.orderParam) form.elements.orderParam.value = "";
        form.requestSubmit();
      };
    }
    const csvBtn = $("#export-campaigns-csv", target);
    if (csvBtn) {
      csvBtn.onclick = () => {
        const rows = model.data?.list || [];
        if (!rows.length) { toast("当前列表暂无可导出数据"); return; }
        exportCsv(`afs-campaigns-page-${model.page}.csv`,
          ["活动 ID", "活动名称", "平台", "花费", "收入", "利润", "ROAS", "规则", "审核状态"],
          rows.map(r => [
            r.adResourceId, r.campaign || r.title, Array.isArray(r.adPlatform) ? r.adPlatform.join("/") : r.adPlatform,
            n(r.adSpend).toFixed(2), n(r.grossRevenue).toFixed(2),
            (r.profit != null ? n(r.profit) : n(r.grossRevenue) - n(r.adSpend)).toFixed(2),
            ratio(n(r.grossRevenue), n(r.adSpend))?.toFixed(4) ?? "",
            r.ruleName || "", (campaignStateMeta[r.state] || [r.state])[0]
          ]));
        toast("活动列表 CSV 已导出");
      };
    }
  }

  if (view === "settings" && !simulated) $("#add-config", target).onclick = () => editConfig(null, false, () => refresh(1));
  wireBusinessActions(target, context, view, null, () => refresh(1));
  try {
    if (simulated || model.data) render();
    else await refresh();
  } catch (error) {
    if (results.isConnected) errorIn(results, error.message);
  }
}

function editConfig(row, readOnly, refresh) {
  const content = dialog(readOnly ? "查看配置" : row ? "编辑配置" : "新增配置", `<form>
    <div class="form-grid"><label>站点${row ? `<input name="site" readonly value="${e(row.site)}">` : '<select name="site" required><option value="">正在读取…</option></select>'}</label>
      <label>配置键<input name="name" required value="${e(row?.name)}" ${row ? "readonly" : ""}></label>
      <label class="all">说明<input name="desc" required value="${e(row?.desc)}" ${readOnly ? "readonly" : ""}></label>
      <label class="all">配置值<textarea name="value" required rows="5" ${readOnly ? "readonly" : ""}>${e(row?.value)}</textarea></label></div>
    <div id="config-error"></div>${readOnly ? "" : '<div class="form-actions"><button class="button" type="submit">保存配置</button></div>'}</form>`);
  if (readOnly) return;
  if (!row) loadSiteSelect($("form", content), message => errorIn($("#config-error", content), message));
  $("form", content).onsubmit = event => {
    event.preventDefault();
    const form = event.currentTarget;
    busy($("button", form), async () => {
      const values: Record<string, any> = formValues(form);
      values.value = new FormData(form).get("value");
      const result = await request(`/cms/config/${row ? "edit" : "add"}`, { body: values });
      if (typeof result === "string" && result) throw new Error(result);
      closeDialog();
      toast("配置已保存");
      await refresh();
    }, message => {
      const target = $("#config-error", content);
      if (target) errorIn(target, message);
      else toast(message);
    });
  };
}
