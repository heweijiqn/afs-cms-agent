import { requestList } from "./api.js";
import { $, escape as e, errorIn, pageHead, table } from "./ui.js";

const guides = [
  {
    platform: "Google Ads",
    fields: "展示、点击、花费、转化量、转化价值",
    docs: "https://developers.google.com/google-ads/api/docs/reporting/uireports",
    note: "API 的 cost_micros 需除以 1,000,000，金额保留账号币种。"
  },
  {
    platform: "TikTok Ads",
    fields: "花费、展示、点击、转化量、转化成本",
    docs: "https://business-api.tiktok.com/portal/docs/supported-metrics-for-a-dimension-in-basic-reports/v1.3",
    note: "可用指标取决于报表维度。对比转化时记录优化事件和归因窗口。"
  },
  {
    platform: "Meta Ads",
    fields: "花费、展示、点击、转化动作及其价值",
    docs: "https://developers.facebook.com/docs/marketing-api/insights/",
    note: "转化动作按事件类型读取。保留点击口径和归因窗口。"
  },
  {
    platform: "Taboola",
    fields: "展示、点击、花费、转化",
    docs: "https://developers.taboola.com/backstage-api/docs/campaign-summary-standard-columns",
    note: "可见 CTR 采用可见展示作为分母，花费字段为 spent。"
  }
];

const sourceLink = (url, label) =>
  `<a class="table-link" href="${e(url)}" target="_blank" rel="noopener noreferrer">${e(label)}</a>`;

function metricText({ value, unit }) {
  const number = value.toLocaleString("zh-CN", { maximumFractionDigits: 4 });
  if (unit === "ratio") return number + "×";
  if (unit === "EUR") return "€" + number;
  if (unit === "change_pct") return (value > 0 ? "+" : "") + number + "%";
  return number + "%";
}

export async function platformReference(target, context, isCurrent) {
  target.innerHTML = pageHead("海外平台投放参考", "汇总主流海外广告平台的公开标杆案例与指标口径，辅助设定目标 ROAS 与归因标准。",
    '<a class="button secondary" href="#reports">返回投放报表</a>')
    + `<section class="panel"><div class="panel-head"><h2>行业标杆案例</h2></div>
      <div id="public-case-results" aria-live="polite"><div class="placeholder">正在读取案例库…</div></div></section>`
    + `<section class="panel"><div class="panel-head"><h2>主流平台指标口径</h2></div>${
      table(["平台", "核心指标", "核算与归因要点", "官方文档"], guides.map(item => [
        `<strong>${e(item.platform)}</strong>`, e(item.fields), e(item.note),
        sourceLink(item.docs, "查看文档")
      ]), "暂无报表资料")}</section>`;

  const results = $("#public-case-results", target);
  if (!context.localCms) {
    results.innerHTML = '<div class="placeholder">当前模式暂无已入库的标杆案例。</div>';
    return;
  }
  try {
    const rows = await requestList("/cms/console/public-ad-cases");
    if (!isCurrent()) return;
    const cases = new Map();
    for (const metric of rows) {
      if (!cases.has(metric.caseId)) cases.set(metric.caseId, []);
      cases.get(metric.caseId).push(metric);
    }
    results.innerHTML = table(
      ["平台", "广告主", "统计范围", "披露指标", "来源"],
      [...cases.values()].map(metrics => {
        const [item] = metrics;
        return [e(item.platform), e(item.advertiser), e(item.scope),
          metrics.map(metric => `<div>${e(metric.name)} <strong>${e(metricText(metric))}</strong></div>`).join(""),
          sourceLink(item.source, "官方案例")];
      }), "暂无案例");
  } catch (error) {
    if (isCurrent()) errorIn(results, error.message);
  }
}
