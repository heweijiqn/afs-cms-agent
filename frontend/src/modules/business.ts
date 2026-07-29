import { request } from "./api.js";
import { $, escape as e, busy, dialog, errorIn, pageHead, table, toast, listSearch, wireListSearch,
  metric, numeric, toneBadge, roasBadge, profitDisplay, starRating, exportCsv, ratio, n, defaultDateRange, clampStartToLastDays } from "./ui.js";
import { operations, localMaintenanceOperations } from "./operation-definitions.js";
import { setReportChoices, wireReportChoices } from "./report-options.js";
import { mountRuleEditor } from "./rule-editor.js";
import { adPlatforms, fieldSource, wireFormChoices, loadSiteFilter } from "./form-choices.js";

const groups = {
  campaigns: "广告活动与审核", articles: "文章发布与审核", materials: "素材与文件", copy: "广告文案",
  categories: "内容与素材分类", publishers: "AdSense 账号", channels: "AdSense 渠道",
  platforms: "平台广告账号", operators: "后台操作员", rules: "投放规则", reports: "多维报表",
  dictionaries: "基础资料", feedback: "用户反馈", system: "系统运维"
};
const wideKinds = new Set(["textarea", "list", "integers", "objects", "jsonListText"]);
const sensitiveKey = /password|secret|token|cookie|signature|api.?key|authorization|credential/i;
const truth = value => value === true || value === "true" || value === 1;
const query = values => new URLSearchParams(values).toString();
const isRecord = value => value && typeof value === "object" && !Array.isArray(value);
const recordKeys = {
  "campaign-edit": ["adResourceId"], "tiktok-edit": ["adResourceId"], "campaign-audit": ["adResourceId"],
  "campaign-bind": ["adResourceId"], "campaign-unbind": ["adResourceId"], "campaign-archive": ["adResourceId"],
  "campaign-delete": ["adResourceId"], "campaign-info": ["adResourceId"], "campaign-group": ["adResourceId"],
  "campaign-rule-logs": ["adResourceId"],
  "campaign-publish": ["adResourceId"],
  "article-edit": ["articleId"], "article-audit": ["articleId"], "article-on-off": ["articleId"],
  "article-cancel": ["articleId"], "article-archive": ["articleId"], "article-delete": ["articleId"],
  "article-info": ["articleId"], "article-keywords": ["articleId"],
  "material-edit": ["id"], "material-delete": ["idList"], "copy-edit": ["id"], "copy-delete": ["id"],
  "publisher-edit": ["pubId"], "channel-delete": ["pubId", "channelId"],
  "operator-edit": ["name"], "operator-details": ["name"],
  "rule-edit": ["id"], "rule-delete": ["id"], "language-edit": ["id"]
};
const ruleTemplates = {
  protect: { action: "CLOSE_CAMPAIGN", condition: "AD_SPEND >= 100 && ROI < 0.8" },
  grow: { action: "CHANGE_BUDGET", condition: "AD_SPEND >= 80 && ROI >= 1.5", budgetAction: "INCREASE", budgetPercent: 10 },
  reduce: { action: "CHANGE_BUDGET", condition: "AD_SPEND >= 80 && ROI < 1", budgetAction: "DECREASE", budgetPercent: 10 }
};

function visible(section, context) {
  const actions = context.localCms && section === "system"
    ? [...operations.system.filter(action => action.id.startsWith("cms-health")), ...localMaintenanceOperations]
    : operations[section] || [];
  return actions.filter(action =>
    (action.id !== "campaign-publish" || context.localCms) &&
    (context.mode === "simulation" || !action.root || context.account?.root === 1));
}

function get(context, section, actionId) {
  return visible(section, context).find(action => action.id === actionId);
}

function available(section, context, row) {
  const actions = visible(section, context);
  return actions.filter(action => {
    const keys = recordKeys[action.id];
    if (!row) return !keys && !["campaign-lookup", "language-list"].includes(action.id);
    if (!appliesToRecord(action.id, row)) return false;
    return keys?.every(key => {
      const value = row[key === "idList" ? "id" : key];
      return value != null && String(value).trim() !== "";
    });
  });
}

function appliesToRecord(id, row) {
  const platforms = Array.isArray(row.adPlatform) ? row.adPlatform : [row.adPlatform];
  const tiktok = platforms.some(platform => String(platform).toLowerCase() === "tiktok");
  const archived = row.state === "ARCHIVE";
  const bound = Number(row.ruleId) > 0;
  switch (id) {
    case "campaign-edit": return !archived && !tiktok;
    case "tiktok-edit": return !archived && tiktok;
    case "campaign-group": return tiktok;
    case "campaign-audit": return row.state === "AUDITING";
    case "campaign-publish": return row.state === "PASS" && !row.platformCampaign &&
      ["", "LOCAL_ONLY", "PLATFORM_REJECTED"].includes(row.adPlatformState || "") && platforms.length === 1 && adPlatforms.includes(platforms[0]);
    case "campaign-bind": return !archived && row.state === "PASS";
    case "campaign-unbind": return !archived && bound;
    case "campaign-archive": case "article-archive": case "article-edit": return !archived;
    case "article-audit": return ["AUDITING", "UPDATE_AUDITING"].includes(row.state);
    case "article-cancel": return ["UPDATE_AUDITING", "UPDATE_REJECT"].includes(row.state);
    case "article-on-off": case "article-keywords": return ["PASS", "UPDATE_AUDITING", "UPDATE_REJECT"].includes(row.state);
    default: return true;
  }
}

const rowLabels = {
  "campaign-info": "详情", "article-info": "详情", "operator-details": "详情",
  "campaign-edit": "编辑", "tiktok-edit": "编辑", "article-edit": "编辑", "rule-edit": "编辑",
  "material-edit": "编辑", "copy-edit": "编辑", "publisher-edit": "编辑", "operator-edit": "编辑", "language-edit": "编辑",
  "rule-delete": "删除", "copy-delete": "删除", "material-delete": "删除", "channel-delete": "删除",
  "campaign-audit": "审核", "article-audit": "审核"
};

function primaryActions(actions) {
  return actions.filter(action => rowLabels[action.id]).sort((a, b) =>
    ["详情", "审核", "编辑", "删除"].indexOf(rowLabels[a.id]) - ["详情", "审核", "编辑", "删除"].indexOf(rowLabels[b.id])).slice(0, 2);
}

export function businessActions(section: string, context: any, row: any = null, index: any = null) {
  const actions = available(section, context, row);
  if (!actions.length) return "";
  const scope = `data-business-section="${e(section)}"`;
  if (!row && section === "campaigns") return `<button type="button" class="button" ${scope} data-business-choice="create">新建活动</button>`;
  if (!row) return '<div class="operation-buttons">' + actions.map(action =>
    `<button type="button" class="button secondary small" ${scope} data-module-action="${e(action.id)}">${e(action.title)}</button>`).join("") + '</div>';
  const primary = actions.length <= 2 ? actions : primaryActions(actions);
  const rowScope = `${scope} data-business-row="${index}"`;
  return `<div class="record-actions">${primary.map(action =>
    `<button type="button" class="table-link" ${rowScope} data-module-action="${e(action.id)}">${e(rowLabels[action.id] || action.title)}</button>`).join("")}
    ${actions.length > primary.length ? `<button type="button" class="button secondary small" ${rowScope} data-business-choice="more">更多</button>` : ""}</div>`;
}

export function wireBusinessActions(target: any, context: any, section: string, rows?: any, refresh?: any) {
  target.querySelectorAll(`[data-business-section="${section}"]`).forEach(button => {
    button.onclick = () => {
      const row = rows?.[Number(button.dataset.businessRow)];
      const action = get(context, section, button.dataset.moduleAction);
      if (action) {
        if (available(section, context, row).includes(action)) openOperation(context, action, row, refresh);
      } else if (button.dataset.businessChoice) actionPicker(context, section, row, refresh);
    };
  });
}

function actionPicker(context, section, row, refresh) {
  const actions = available(section, context, row);
  if (!row && section === "campaigns") {
    const content = dialog("选择投放平台", `<div class="operation-buttons">${adPlatforms.map(platform =>
      `<button type="button" class="button secondary" data-campaign-platform="${platform}">${platform}</button>`).join("")}</div>`);
    content.querySelectorAll("[data-campaign-platform]").forEach(button => {
      button.onclick = () => {
        const platform = button.dataset.campaignPlatform;
        const action = actions.find(item => item.id === (platform === "TikTok" ? "tiktok-new" : "campaign-new"));
        if (action) openOperation(context, { ...action, defaults: { ...action.defaults, adPlatform: platform } }, null, refresh);
      };
    });
    return;
  }
  const list = row ? actions.filter(action => !primaryActions(actions).includes(action)) : actions;
  const label = row && (row.campaign || row.title || row.name || row.articleId || row.pubId);
  const content = dialog(row ? "更多操作" : "新建活动", `${label ? `<p class="sub">${e(label)}</p>` : ""}
    <div class="operation-buttons">${list.map(action => `<button type="button" class="button secondary small${action.id.endsWith("-delete") ? " record-delete" : ""}"
      data-choice-action="${e(action.id)}">${e(action.id === "campaign-bind" && Number(row?.ruleId) > 0 ? "更换规则" : action.title)}</button>`).join("")}</div>`);
  content.querySelectorAll("[data-choice-action]").forEach(button => {
    button.onclick = () => openOperation(context, list.find(action => action.id === button.dataset.choiceAction), row, refresh);
  });
}

export async function workbench(target, context, section) {
  if (section === "dictionaries" && context.mode !== "simulation") {
    await dictionaryTabs(target, context);
    return;
  }
  const title = groups[section];
  const simulated = context.mode === "simulation";
  if (section === "feedback" && !simulated) {
    feedbackPage(target);
    return;
  }
  if (section === "system" && !simulated) {
    systemPage(target, context);
    return;
  }
  target.innerHTML = pageHead(title, simulated ? "当前处于只读演练环境，外部操作仅校验参数。" : "查询和维护当前模块的业务资料。")
    + `<section class="panel">${businessActions(section, context)}</section>`;
  wireBusinessActions(target, context, section);
}

function feedbackPage(target) {
  target.innerHTML = pageHead("用户反馈", "查看各站点收集的用户评分与反馈意见，辅助优化落地页体验。",
    '<button type="button" class="button secondary" id="export-feedback-csv">导出当前页 CSV</button>')
    + `<div id="feedback-kpis"></div><section class="panel">
    <form class="toolbar catalog-search">${listSearch("站点", "", true)}</form>
    <div class="feedback-error" aria-live="polite"></div><div class="feedback-results"><div class="placeholder">正在读取…</div></div></section>`;
  const form = $("form", target);
  const output = $(".feedback-results", target);
  const kpis = $("#feedback-kpis", target);
  const error = $(".feedback-error", target);
  const fail = message => { if (error.isConnected) errorIn(error, message); };
  let revision = 0;
  let lastRows = [];
  async function show(site = "", page = 1) {
    const current = ++revision;
    error.replaceChildren();
    const data = await request("/cms/feedback/list", { body: { site, pageIndex: page, pageSize: 20 } });
    if (!output.isConnected || current !== revision) return;
    lastRows = data.list || [];
    const rated = lastRows.map(r => Number(r.rate)).filter(v => Number.isFinite(v) && v > 0);
    const avg = rated.length ? rated.reduce((a, b) => a + b, 0) / rated.length : null;
    const positive = rated.length ? rated.filter(v => v >= 4).length / rated.length : null;
    kpis.innerHTML = `<div class="cards">
      ${metric("反馈总数", numeric(data.totalSize || 0), site ? `站点 ${site}` : "全部站点")}
      ${metric("本页平均评分", avg == null ? "—" : `${avg.toFixed(2)} / 5.0`, `样本 ${rated.length} 条`)}
      ${metric("好评率（≥4★）", positive == null ? "—" : `${(positive * 100).toFixed(1)}%`, "用户满意度")}
      ${metric("当前页数", `第 ${page} 页`, "每页 20 条")}
    </div>`;
    const pages = Math.max(1, Math.ceil((data.totalSize || 0) / 20));
    output.innerHTML = table(["站点", "评分", "语言", "反馈内容", "提交时间"], lastRows.map(row => [
      `<strong>${e(row.site || "—")}</strong>`,
      starRating(row.rate),
      e(row.locale || "—"),
      `<span class="catalog-cell" title="${e(row.value || "")}">${e(row.value || "—")}</span>`,
      e(row.ctime || "—")
    ]), "没有符合条件的反馈")
      + `<div class="pagination"><button type="button" class="button secondary small" data-page="${page - 1}" ${page <= 1 ? "disabled" : ""}>上一页</button>
        <span>第 ${page} / ${pages} 页 · 共 ${numeric(data.totalSize || 0)} 条</span>
        <button type="button" class="button secondary small" data-page="${page + 1}" ${page >= pages ? "disabled" : ""}>下一页</button></div>`;
    output.querySelectorAll("[data-page]").forEach(button => {
      button.onclick = () => busy(button, () => show(site, Number(button.dataset.page)), fail);
    });
  }
  $("#export-feedback-csv", target).onclick = () => {
    if (!lastRows.length) { toast("当前列表暂无可导出数据"); return; }
    exportCsv("afs-feedback.csv", ["站点", "评分", "语言", "反馈内容", "提交时间"],
      lastRows.map(r => [r.site, r.rate, r.locale, r.value, r.ctime]));
    toast("用户反馈 CSV 已导出");
  };
  wireListSearch(form, site => show(site), fail);
  loadSiteFilter(form, "", fail);
  show().catch(error => fail(error.message));
}

const maintenanceDescriptions = {
  "adsense-source-report": "按渠道、国家与日期核对 AdSense 原始展示、点击及预估收入明细。",
  "campaign-recalculate": "按选定日期重新汇总各平台花费与收入，刷新活动列表中的利润与 ROAS。",
  "cms-daily-report": "汇总选定日期全部已通过活动的投放表现，生成运营日报。"
};

function systemPage(target, context) {
  const actions = visible("system", context).filter(action => !action.id.startsWith("cms-health"));
  target.innerHTML = pageHead("系统运维", "监控核心服务健康状态，执行报表核对、活动重算与日报汇总。")
    + `<section class="panel"><div class="panel-head"><div><h2>服务健康检查</h2><p class="sub">实时检测管理后台核心服务与内容 API 连通性。</p></div>
    <button class="button secondary small" type="button" data-refresh-health>刷新状态</button></div><div class="health-results" aria-live="polite"></div></section>
    ${actions.length ? `<section class="panel"><div class="panel-head"><div><h2>运维与数据工具</h2><p class="sub">所有数据重算与核对均基于当前工作区数据库执行。</p></div></div>
      <div class="ops-card-grid">${actions.map(action => `<article class="ops-card">
        <h3>${e(action.title)}</h3>
        <p class="sub">${e(maintenanceDescriptions[action.id] || "执行当前运维检查或数据同步操作。")}</p>
        <button type="button" class="button secondary small" data-business-section="system" data-module-action="${e(action.id)}">打开工具</button>
      </article>`).join("")}</div></section>` : ""}`;
  const output = $(".health-results", target);
  const refresh = $("[data-refresh-health]", target);
  refresh.onclick = () => busy(refresh, async () => {
    const checks = await Promise.allSettled([
      request("/health", { anonymous: true }),
      request("/api/service/health", { anonymous: true }),
      request("/healthX", { body: {} })
    ]);
    if (!output.isConnected) return;
    const labels = [
      ["管理后台服务", "核心 Web 与认证服务"],
      ["内容与上报 API", "对外内容查询与反馈上报接口"],
      ["数据写入接口", "POST 请求与签名校验链路"]
    ];
    output.innerHTML = table(["服务项", "说明", "状态"], checks.map((result, index) => [
      `<strong>${e(labels[index][0])}</strong>`,
      e(labels[index][1]),
      result.status === "fulfilled" ? toneBadge("运行正常", "teal") : toneBadge(`异常：${result.reason.message}`, "red")
    ]));
  }, message => errorIn(output, message));
  wireBusinessActions(target, context, "system");
  refresh.click();
}

async function dictionaryTabs(target, context) {
  const tabs = [
    ["language-list", "语言"], ["country-list", "国家和地区"], ["site-list", "站点"],
    ["manager-list", "投放负责人"], ["strategy-list", "文章处理策略"], ["directory-list", "栏目目录"]
  ];
  target.innerHTML = pageHead("基础资料", "维护系统支持的语言、国家地区代码、站点标识与投放负责人字典。") + `<section class="panel">
    <div class="panel-head"><div class="catalog-tabs" role="group" aria-label="资料类别">${tabs.map(([id, text]) =>
      `<button type="button" class="button secondary small" data-dictionary="${id}" aria-pressed="false">${text}</button>`).join("")}</div>
    <div class="dictionary-actions"></div></div>
    <div class="toolbar catalog-search"><label>快速筛选<input type="search" id="dict-quick-filter" placeholder="输入代码或名称实时过滤…"></label><span class="sub" id="dict-count"></span></div>
    <div class="dictionary-results" aria-live="polite"></div></section>`;
  const output = $(".dictionary-results", target);
  const actions = $(".dictionary-actions", target);
  const filterInput = $("#dict-quick-filter", target);
  const countEl = $("#dict-count", target);
  const buttons = target.querySelectorAll("[data-dictionary]");
  const add = get(context, "dictionaries", "language-new");
  let revision = 0;
  let currentId = tabs[0][0];
  let cachedRows = [];

  function paintFiltered() {
    const q = filterInput.value.trim().toLowerCase();
    const filtered = cachedRows.filter(row => {
      if (!q) return true;
      const text = typeof row === "object"
        ? `${row.value || ""} ${row.label || ""} ${row.regionCode || ""} ${row.name || ""}` : String(row);
      return text.toLowerCase().includes(q);
    });
    countEl.textContent = `共 ${numeric(filtered.length)} 条`;
    if (currentId === "language-list") {
      output.innerHTML = table(["语言代码", "语言名称", ...(add ? ["操作"] : [])], filtered.map((row, index) =>
        [`<code>${e(row.value)}</code>`, e(row.label), ...(add ? [businessActions("dictionaries", context, row, index)] : [])]), "没有匹配的语言");
      wireBusinessActions(output, context, "dictionaries", filtered, () => show(currentId));
    } else if (currentId === "country-list") {
      output.innerHTML = table(["地区代码", "国家或地区"], filtered.map(row => [`<code>${e(row.regionCode)}</code>`, e(row.name)]), "没有匹配的国家或地区");
    } else {
      output.innerHTML = table([tabs.find(([key]) => key === currentId)[1]], filtered.map(row => [
        e(typeof row === "object" ? row.label || row.name || row.value || JSON.stringify(row) : row)
      ]), "没有匹配的资料项");
    }
  }

  filterInput.oninput = paintFiltered;

  async function show(id) {
    currentId = id;
    filterInput.value = "";
    const current = ++revision;
    buttons.forEach(button => button.setAttribute("aria-pressed", String(button.dataset.dictionary === id)));
    actions.innerHTML = id === "language-list" && add ? '<button type="button" class="button small">新增语言</button>' : "";
    if (actions.firstElementChild) actions.firstElementChild.onclick = () => openOperation(context, add, null, () => show(id));
    output.innerHTML = '<div class="placeholder">正在读取…</div>';
    countEl.textContent = "";
    try {
      const data = await request(get(context, "dictionaries", id).path);
      if (!output.isConnected || current !== revision) return;
      cachedRows = Array.isArray(data) ? data : Array.isArray(data?.list) ? data.list : Object.entries(data || {}).map(([name, value]) => ({ name, value }));
      paintFiltered();
    } catch (error) {
      if (output.isConnected && current === revision) errorIn(output, error.message);
    }
  }
  buttons.forEach(button => { button.onclick = () => show(button.dataset.dictionary); });
  await show(tabs[0][0]);
}

function seedValue(row, action, field) {
  const fallback = action.defaults?.[field.key];
  if (!row) return fallback;
  const lookup = { siteList: row.siteList ?? row.sites, idList: row.id == null ? undefined : [row.id] };
  const value = row[field.key] ?? lookup[field.key] ?? fallback;
  if (field.key === "state" && action.id.endsWith("-audit")) return undefined;
  if (field.kind === "secret") return undefined;
  return value;
}

function fieldMarkup(field, value, locked = false) {
  const { key, title, kind, required, options } = field;
  const attrs = `name="${e(key)}"${required ? " required" : ""}`;
  const mark = required ? '<span class="field-required" aria-hidden="true">*</span>' : "";
  let control;
  if (locked) {
    const displayed = e(Array.isArray(value) ? value.join(", ") : value);
    return `<div class="locked-record"><span>${e(title)}</span><div>${displayed}</div>
      <input type="hidden" name="${e(key)}" value="${displayed}"></div>`;
  } else if (field.lookup || field.source) {
    const multiple = ["list", "integers", "objects"].includes(kind) || field.multiple;
    const lookup = field.source ? ` data-choice-source="${e(field.source)}" data-choice-value="${e(JSON.stringify(value ?? null))}"${kind === "integers" ? ' data-numeric-choice="true"' : ""}` : "";
    control = `<select ${attrs}${lookup}${multiple ? ' multiple class="multi-value" tabindex="-1" aria-hidden="true"' : ""}>${multiple ? "" : '<option value="">正在读取…</option>'}</select>`;
    if (multiple) return `<div class="multi-field"><span>${e(title.replace(/（(?:每行一项|可多选)）/g, ""))} ${mark}</span>${control}
      <details class="multi-dropdown"><summary>请选择</summary><div class="multi-options"></div></details></div>`;
  } else if (kind === "select" || kind === "boolean") {
    const choices = kind === "select" ? options.map(option => Array.isArray(option) ? option : [option, option]) : [["true", "是"], ["false", "否"]];
    const normalized = kind === "boolean" && value != null ? String(truth(value)) : String(value ?? "");
    if (normalized && !choices.some(([id]) => normalized === String(id))) choices.push([normalized, normalized]);
    control = `<select ${attrs}><option value="">请选择</option>${choices.map(([id, label]) =>
      `<option value="${e(id)}" ${normalized === String(id) ? "selected" : ""}>${e(label)}</option>`).join("")}</select>`;
  } else if (wideKinds.has(kind)) {
    let shown = value ?? "";
    if (Array.isArray(value)) shown = kind === "list" || kind === "integers" ? value.join("\n") : JSON.stringify(value, null, 2);
    if (kind === "jsonListText") {
      try {
        const paragraphs = Array.isArray(value) ? value : JSON.parse(shown);
        if (Array.isArray(paragraphs) && paragraphs.every(item => typeof item === "string")) shown = paragraphs.join("\n\n");
      } catch { /* Plain-text article drafts are already editable. */ }
    }
    const placeholder = options ? JSON.stringify(options, null, 2) : "";
    control = `<textarea ${attrs} ${kind === "jsonListText" ? `class="article-body" data-original-body="${e(typeof value === "string" ? value : JSON.stringify(value ?? []))}"` : ""} rows="${kind === "objects" ? 5 : 3}" placeholder="${e(placeholder)}">${e(shown)}</textarea>`;
  } else if (["file", "image"].includes(kind)) {
    control = `<input type="file" ${attrs}${kind === "image" ? ' accept="image/*"' : ""}>`;
  } else {
    const numeric = ["positive", "integer", "number"].includes(kind);
    const type = numeric ? "number" : kind === "secret" ? "password" : kind === "date" ? "date" : "text";
    const step = numeric ? ` step="${kind === "number" ? "any" : "1"}"` : "";
    const minimum = kind === "positive" ? ' min="1"' : "";
    let resolvedValue = value;
    if (kind === "date" && !resolvedValue) {
      const range = defaultDateRange(7);
      resolvedValue = /start/i.test(key) ? range.start : range.end;
    }
    control = `<input type="${type}" ${attrs}${step}${minimum} value="${e(resolvedValue)}"${kind === "secret" ? ' autocomplete="new-password"' : ""}>`;
  }
  return `<label${wideKinds.has(kind) ? ' class="all"' : ""}><span>${e(title)} ${mark}</span>${control}</label>`;
}

function setField(body, key, value) {
  const [parent, child] = key.split(".");
  if (parent === "__proto__" || parent === "constructor") throw new Error("无效字段");
  if (child) {
    body[parent] ||= {};
    body[parent][child] = value;
  } else {
    body[parent] = value;
  }
}

function parseField(field, raw) {
  const { kind, title, required } = field;
  if (raw instanceof File) {
    if (!raw.size) throw new Error(`请选择${title}`);
    return raw;
  }
  const normalized = String(raw ?? "").trim();
  if (!normalized) {
    if (required) throw new Error(`请填写${title}`);
    return undefined;
  }
  if (kind === "secret") return raw;
  if (kind === "boolean") return normalized === "true";
  if (["positive", "integer", "number"].includes(kind)) {
    const value = Number(normalized);
    if (!Number.isFinite(value) || (kind !== "number" && !Number.isSafeInteger(value))
        || (kind === "positive" && value < 1)) throw new Error(`${title}必须是有效数字`);
    return value;
  }
  if (kind === "list" || kind === "integers") {
    const rows = normalized.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
    if (kind === "list") return rows;
    const numbers = rows.map(Number);
    if (numbers.some(number => !Number.isSafeInteger(number) || number < 1)) throw new Error(`${title}必须填写正整数`);
    return numbers;
  }
  if (kind === "jsonListText") return JSON.stringify(normalized.split(/\r?\n\s*\r?\n/).map(text => text.trim()));
  if (kind === "objects") {
    let values;
    try {
      values = JSON.parse(normalized);
    } catch {
      throw new Error(`${title}的 JSON 格式有误`);
    }
    if (!Array.isArray(values) || (required && !values.length) || (kind === "objects" && !values.every(isRecord))) {
      throw new Error(`${title}需要一个有效的非空数组`);
    }
    return values;
  }
  return normalized;
}

function collectFields(action: any, group: any): Record<string, any> {
  const payload: Record<string, any> = {};
  for (const field of action.fields) {
    const element = $(`[name="${field.key}"]`, group);
    if (element.disabled) continue;
    if (field.kind === "jsonListText" && element.value === element.defaultValue && element.dataset.originalBody) {
      setField(payload, field.key, element.dataset.originalBody);
      continue;
    }
    if (element.dataset.choiceSource === "materialList") {
      const materials = [...element.selectedOptions].map(option => element.choiceRecords.get(option.value));
      if (!materials.length || materials.some(item => !item)) throw new Error("请选择有效的广告素材");
      setField(payload, field.key, materials);
      continue;
    }
    const raw = element.multiple ? [...element.selectedOptions].map(option => option.value).join(field.key === "country" && field.kind === "text" ? "," : "\n")
      : element.type === "file" ? element.files[0] : element.value;
    let value = parseField(field, raw);
    if (value === undefined) continue;
    if (action.numbers?.includes(field.key)) value = Number(value);
    if (action.split?.includes(field.key)) value = value.split(",");
    setField(payload, field.key, value);
  }
  return payload;
}

export function operationPayload(action: any, form: any): any {
  if (action.batch) return [...form.querySelectorAll(".operation-batch")].map(group => collectFields(action, group));
  const payload: Record<string, any> = collectFields(action, form);
  if (action.id === "campaign-audit" && payload.state === "PASS" && (!payload.channelId || !payload.landPageUrl)) {
    throw new Error("审核通过时必须填写渠道 ID 和落地页 URL");
  }
  if (action.id === "article-audit" && payload.state === "PASS" && !payload.site) {
    throw new Error("审核通过时必须填写站点");
  }
  if (action.id.startsWith("tiktok-")) {
    if (payload.campaignBudgetModel !== "BUDGET_MODE_INFINITE" && payload.campaignBudget == null) throw new Error("当前预算模式需要填写活动预算");
    if (payload.bidPriceType !== "BID_TYPE_NO_BID" && payload.bidPriceValue == null) throw new Error("当前出价类型需要填写出价值");
  }
  if (payload.dateStart && payload.dateEnd && payload.dateStart > payload.dateEnd) throw new Error("结束日期不能早于开始日期");
  return payload;
}

const resultFieldLabels = {
  id: "编号", date: "日期", country: "国家/地区", countryCode: "国家/地区", campaign: "活动名称",
  campaignId: "活动 ID", campaignName: "活动名称", adResourceId: "活动 ID", articleId: "文章 ID",
  channelId: "渠道 ID", title: "标题", site: "站点", lang: "语言", state: "状态", status: "状态",
  adPlatform: "投放平台", adSpend: "花费", ad_spend: "花费", spend: "花费", grossRevenue: "收入",
  gross_revenue: "收入", revenue: "收入", profit: "利润", roi: "ROAS", roas: "ROAS",
  ad_impressions: "展示", impressions: "展示", ad_clicks: "点击", clicks: "点击",
  ad_conversion: "转化", conversion: "转化", conversions: "转化",
  ruleId: "规则 ID", ruleName: "规则名称", action: "执行动作", dayNum: "天数", condition: "触发条件",
  adGroupId: "广告组 ID", adGroupName: "广告组名称", adGroupStatus: "广告组状态", budget: "预算",
  advertiser: "广告账号", name: "账号/名称", desc: "说明", partner: "合作方", sites: "授权站点",
  siteList: "授权站点", autoArticle: "文章自动审核", autoCampaign: "活动自动审核", showMaterial: "查看素材",
  firstP: "首段摘要", textList: "正文内容", terms: "关键词", category: "分类", online: "上线状态",
  ctime: "创建时间", utime: "更新时间", url: "地址", message: "结果"
};

function formatCellValue(value) {
  if (value == null || value === "") return "—";
  if (typeof value === "boolean") return value ? "是" : "否";
  if (Array.isArray(value)) return value.every(v => typeof v !== "object") ? value.join(" / ") || "—" : JSON.stringify(value);
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function renderResult(target, result, preview) {
  const replacer = (key, value) => sensitiveKey.test(key) ? "[已隐藏]" :
    (value instanceof File ? { name: value.name, bytes: value.size } : value);
  const safe = JSON.parse(JSON.stringify(result ?? {}, replacer));
  const rows = Array.isArray(safe) ? safe : safe.list;
  let display;
  if (!preview && Array.isArray(rows) && rows.length && rows.every(isRecord)) {
    const keys = [...new Set(rows.flatMap(row => Object.keys(row)))]
      .filter(k => !["httpBody", "httpResponse", "otherInfo"].includes(k)).slice(0, 12);
    if (keys.length) {
      display = table(keys.map(k => resultFieldLabels[k] || k),
        rows.map(row => keys.map(key => e(formatCellValue(row[key])))));
    }
  } else if (!preview && isRecord(safe) && Object.keys(safe).length) {
    const entries = Object.entries(safe).filter(([k, v]) => v != null && v !== "" && !["httpBody", "httpResponse"].includes(k));
    if (entries.length) {
      display = table(["项目", "内容"], entries.map(([k, v]) => [e(resultFieldLabels[k] || k), e(formatCellValue(v))]));
    }
  }
  if (!display) display = `<pre>${e(typeof safe === "string" ? safe : JSON.stringify(safe, null, 2))}</pre>`;
  target.innerHTML = `<div class="sub">${preview ? "模拟参数校验通过，未发送请求。" : "查询完成。"}</div>${display}`;
}

async function wireMaintenanceChoices(form, action) {
  const submit = form.querySelector('[type="submit"]');
  submit.disabled = true;
  const { channels, dates } = await request("/cms/console/maintenance/options");
  if (!form.isConnected) return;
  if (action.id !== "adsense-source-report") {
    setReportChoices(form.elements.date, dates.map(date => [date, date]), "请选择日期");
    form.elements.date.value = dates[0] || "";
    submit.disabled = dates.length === 0;
    if (!dates.length) throw new Error("报表库暂无可用日期");
    return;
  }
  const byChannel = new Map();
  for (const channel of channels) {
    if (!byChannel.has(channel.id)) byChannel.set(channel.id, []);
    byChannel.get(channel.id).push(channel);
  }
  const channelSelect = form.elements.reportChannel;
  const countrySelect = form.elements.countryCode;
  setReportChoices(channelSelect, [...byChannel.keys()].map(id => {
    const [publisher, channel] = id.replace(/^partner-/, "").split(":");
    return [id, `${channel} · ${publisher}`];
  }), "请选择渠道");
  const regions = new Intl.DisplayNames(["zh-CN"], { type: "region" });
  function updateDates() {
    const records = (byChannel.get(channelSelect.value) || [])
      .filter(row => !countrySelect.value || row.countryCode === countrySelect.value);
    const start = records.map(row => row.start).sort()[0] || "";
    const end = records.map(row => row.end).sort().at(-1) || "";
    const fallback = defaultDateRange(7);
    for (const name of ["dateStart", "dateEnd"]) {
      form.elements[name].min = start;
      form.elements[name].max = end;
    }
    form.elements.dateStart.value = end ? clampStartToLastDays(start, end, 7) : fallback.start;
    form.elements.dateEnd.value = end || fallback.end;
    submit.disabled = !records.length;
  }
  function updateCountries() {
    const records = byChannel.get(channelSelect.value) || [];
    setReportChoices(countrySelect, records.map(({ countryCode }) =>
      [countryCode, /^[A-Z]{2}$/.test(countryCode) ? `${regions.of(countryCode)} · ${countryCode}` : countryCode]), "全部国家 / 地区");
    countrySelect.value = "";
    updateDates();
  }
  channelSelect.value = channels[0]?.id || "";
  channelSelect.addEventListener("change", updateCountries);
  countrySelect.addEventListener("change", updateDates);
  updateCountries();
  if (!channels.length) throw new Error("报表库暂无有数据的 AdSense 渠道");
}

function renderMaintenanceResult(output, action, result) {
  const rows = Array.isArray(result) ? result : (result?.list || []);
  const integer = new Intl.NumberFormat("zh-CN");
  const money = new Intl.NumberFormat("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const dec = value => Number.isFinite(Number(value)) ? Number(value).toFixed(2) : "0.00";

  if (action.id === "adsense-source-report") {
    const totalRev = rows.reduce((sum, r) => sum + Number(r.revenue || 0), 0);
    const totalImp = rows.reduce((sum, r) => sum + Number(r.impressions || 0), 0);
    const totalClk = rows.reduce((sum, r) => sum + Number(r.clicks || 0), 0);
    const totalFunnelImp = rows.reduce((sum, r) => sum + Number(r.funnelImpressions || 0), 0);
    const totalFunnelClk = rows.reduce((sum, r) => sum + Number(r.funnelClicks || 0), 0);
    const exportBtn = rows.length
      ? '<button type="button" class="button secondary small" id="maint-export-csv">导出 CSV (Excel)</button>'
      : "";
    output.innerHTML = `<div class="result-toolbar">
        <p class="sub">共 ${rows.length} 条明细 · 预估收入 ${e(money.format(totalRev))} · 展示 ${e(integer.format(totalImp))} · 点击 ${e(integer.format(totalClk))}</p>
        ${exportBtn}
      </div>` + table(["日期", "国家 / 地区", "预估收入", "广告展示", "点击", "漏斗展示", "漏斗点击"],
      rows.map(row => [row.date, row.countryCode || "—", money.format(row.revenue), integer.format(row.impressions),
        integer.format(row.clicks), integer.format(row.funnelImpressions), integer.format(row.funnelClicks)].map(e)),
      "所选渠道、国家和日期范围暂无明细");
    const btn = $("#maint-export-csv", output);
    if (btn) {
      btn.onclick = () => {
        const headers = ["日期", "国家/地区", "预估收入", "广告展示", "点击", "漏斗展示", "漏斗点击"];
        const csvRows = rows.map(r => [
          r.date || "", r.countryCode || "", dec(r.revenue),
          Number(r.impressions || 0), Number(r.clicks || 0),
          Number(r.funnelImpressions || 0), Number(r.funnelClicks || 0)
        ]);
        csvRows.push(["汇总 (Total)", `${rows.length} 条`, dec(totalRev), totalImp, totalClk, totalFunnelImp, totalFunnelClk]);
        const rangeTag = rows.length ? `${rows[rows.length - 1].date}_${rows[0].date}` : new Date().toISOString().slice(0, 10);
        exportCsv(`AdSense明细-${rangeTag}.csv`, headers, csvRows);
        toast(`已导出 ${rows.length} 条 AdSense 明细（CSV）`);
      };
    }
    return;
  }

  const recalculate = action.id === "campaign-recalculate";
  const spend = rows.reduce((total, row) => total + Number(row.spend || 0), 0);
  const revenue = rows.reduce((total, row) => total + Number(row.revenue || 0), 0);
  const profit = revenue - spend;
  const blendedRoas = spend > 0 ? revenue / spend : 0;
  const reportDate = String(result?.date || new Date().toISOString().slice(0, 10));
  const summary = recalculate
    ? `${reportDate} · 已重算 ${result?.updated ?? rows.length} 条活动 · 花费 ${money.format(spend)} · 收入 ${money.format(revenue)} · 利润 ${money.format(profit)} · ROAS ${blendedRoas.toFixed(2)}×`
    : `${reportDate} · 共 ${rows.length} 条活动 · 花费 ${money.format(spend)} · 收入 ${money.format(revenue)} · 利润 ${money.format(profit)} · ROAS ${blendedRoas.toFixed(2)}×`;
  const exportBtn = rows.length
    ? `<button type="button" class="button secondary small" id="maint-export-csv">导出 ${recalculate ? "重算结果" : "投放日报"} CSV</button>`
    : "";

  output.innerHTML = `<div class="result-toolbar">
      <p class="sub">${e(summary)}</p>
      ${exportBtn}
    </div>` + table(
    ["广告活动", "渠道", "花费", "收入", "利润", "ROAS"],
    rows.map(row => [
      e(row.campaign || row.activityId || "未关联活动"),
      e(String(row.channel || "").replaceAll("partner-", "")),
      `<span class="mono">${e(money.format(row.spend))}</span>`,
      `<span class="mono">${e(money.format(row.revenue))}</span>`,
      profitDisplay(row.profit),
      roasBadge(row.roas)
    ]),
    recalculate ? "这天暂无可重算的已通过活动" : "这天暂无投放报表记录");

  const btn = $("#maint-export-csv", output);
  if (btn) {
    btn.onclick = () => {
      const headers = ["报表日期", "活动 ID", "广告活动名称", "渠道", "花费 (USD)", "收入 (USD)", "净利润 (USD)", "ROAS"];
      const csvRows = rows.map(row => [
        reportDate,
        row.activityId || "",
        row.campaign || row.activityId || "未关联活动",
        String(row.channel || "").replaceAll("partner-", ""),
        dec(row.spend),
        dec(row.revenue),
        dec(row.profit),
        `${dec(row.roas)}x`
      ]);
      csvRows.push([
        reportDate,
        "TOTAL",
        `汇总（共 ${rows.length} 条）`,
        "",
        dec(spend),
        dec(revenue),
        dec(profit),
        `${blendedRoas.toFixed(2)}x`
      ]);
      const filename = recalculate ? `活动重算结果-${reportDate}.csv` : `投放日报-${reportDate}.csv`;
      exportCsv(filename, headers, csvRows);
      toast(`已导出 ${filename}（含汇总行，可用 Excel 直接打开）`);
    };
  }
}

function platformCampaignFields(platform) {
  const choice = (title, options) => ({ key: "objective", title, kind: "select", required: true, options });
  const field = (key, title, kind = "text") => ({ key, title, kind, required: true });
  switch (platform) {
    case "TikTok": return [choice("广告目标", [["WEB_CONVERSIONS", "网站转化"], ["TRAFFIC", "流量"]])];
    case "Facebook": return [choice("广告目标", [["OUTCOME_TRAFFIC", "流量"], ["OUTCOME_SALES", "销售"], ["OUTCOME_LEADS", "潜在客户"]])];
    case "Google": return [field("budgetId", "现有 Campaign 预算编号"), choice("欧盟政治广告声明",
      [["DOES_NOT_CONTAIN_EU_POLITICAL_ADVERTISING", "不包含"], ["CONTAINS_EU_POLITICAL_ADVERTISING", "包含"]])];
    case "Taboola": return [field("branding", "品牌名称"), field("bid", "点击出价", "number"), field("spendingLimit", "月度预算", "number")];
    default: return [];
  }
}

function openOperation(context, action, row, refresh) {
  if (row && action.id === "campaign-info") {
    const identifiers = row.platformCampaign?.id || (row.platformCampaignIds || []).join(" / ");
    const spend = n(row.adSpend);
    const revenue = n(row.grossRevenue);
    const profit = row.profit != null ? n(row.profit) : revenue - spend;
    const roas = ratio(revenue, spend);
    const safeLanding = /^https?:\/\//i.test(row.landPageUrl || "")
      ? `<a class="table-link" href="${e(row.landPageUrl)}" target="_blank" rel="noopener noreferrer">${e(row.landPageUrl)}</a>`
      : e(row.landPageUrl || "—");
    const details = [
      ["活动 ID", `<code class="mono">${e(row.adResourceId || "—")}</code>`],
      ...(row.localCampaignId ? [["系统编号", `<code class="mono">${e(row.localCampaignId)}</code>`]] : []),
      ["广告标题", e(row.title || "—")],
      ["投放平台", e([].concat(row.adPlatform || []).join(" / ") || "—")],
      ["平台 Campaign ID", e(identifiers || "尚未同步平台")],
      ["渠道 ID", `<code class="mono">${e(row.channelId || "—")}</code>`],
      ["落地页地址", safeLanding],
      ["投放规则", row.ruleName ? toneBadge(row.ruleName, "teal") : '<span class="muted">未绑定</span>'],
      ["国家或地区", e(row.country || "—")],
      ["关键词", e(row.keyword || "—")],
      ["负责人", e(row.am || "—")],
      ["累计花费", `<span class="mono">${e(spend.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }))}</span>`],
      ["累计收入", `<span class="mono">${e(revenue.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }))}</span>`],
      ["净利润", profitDisplay(profit)],
      ["综合 ROAS", roasBadge(roas)]
    ];
    dialog(row.campaign || row.title || "活动详情", table(["项目", "内容"], details.map(([label, html]) => [e(label), html])));
    return;
  }
  const platform = action.id === "campaign-publish" ? [].concat(row.adPlatform)[0] : "";
  if (platform) action = { ...action, fields: action.fields.concat(platformCampaignFields(platform)) };
  const simulated = context.mode === "simulation";
  const confirm = action.confirm && !simulated;
  const isRead = action.method === "GET" || action.read;
  const affectsPlatform = ["campaign-audit", "tiktok-new", "tiktok-edit"].includes(action.id);
  const caption = action.id === "campaign-recalculate" && action.maintenance
    ? "默认选择最新有数据的日期。重算会更新活动列表中的花费、收入、利润和 ROAS。"
    : platform ? `这会在 ${platform} 广告账户创建暂停状态的 Campaign。` : simulated ? "仅校验参数，真实 CMS 和平台不会收到请求。"
    : confirm ? (affectsPlatform ? "此操作将同步至投放管理后台，请核对目标和参数后提交。" : "请核对目标记录和操作参数后提交。")
      : isRead ? "" : "提交后将保存到当前 CMS。";
  const lookups = action.id === "report-dimension" && !simulated;
  const choosesRule = action.id === "campaign-bind" && !simulated;
  const editsRule = action.id === "rule-new" || action.id === "rule-edit";
  const fields = item => action.fields.map(field => fieldMarkup(
    { ...field, lookup: (lookups && field.lookup) || (choosesRule && field.key === "ruleId"),
      source: !simulated && fieldSource(action, field.key),
      multiple: field.key === "country" && action.id.startsWith("tiktok-") }, seedValue(item, action, field),
    Boolean(item && recordKeys[action.id]?.includes(field.key)))).join("");
  const batch = action.batch ? `<div class="operation-batches"><fieldset class="operation-batch"><legend>第 1 组</legend>
    <div class="form-grid">${fields(row)}</div></fieldset></div><button type="button" class="button secondary small batch-add">添加一组</button>` : "";
  const content = dialog(action.title, `${caption ? `<p class="sub">${e(caption)}</p>` : ""}<form class="operation-form">
    ${action.id === "rule-new" ? `<label>条件模板<select name="ruleTemplate">
      <option value="">自定义条件</option><option value="protect">低回报时暂停广告组</option>
      <option value="grow">高回报时提高预算</option><option value="reduce">低回报时降低预算</option></select></label>` : ""}
    ${batch || `<div class="form-grid">${fields(row)}</div>`}
    ${editsRule ? '<section class="rule-editor"></section>' : ""}
    ${confirm ? '<label class="confirm-field"><input type="checkbox" required name="confirmed">我已核对目标和参数，确认执行此操作</label>' : ""}
    <div class="operation-error"></div><div class="form-actions"><button class="button" type="submit">${action.submitText || (simulated ? "检查参数" : isRead ? "查询" : "提交")}</button></div>
    </form><div class="operation-result" aria-live="polite"></div>`);
  const form = $("form", content);
  if (platform) form.dataset.campaignPlatform = platform;
  wireMultiSelects(form);
  const output = $(".operation-result", content);
  const error = $(".operation-error", content);
  const ruleEditor = editsRule ? mountRuleEditor($(".rule-editor", form), row?.rule) : null;
  const ready = form.querySelector("[data-choice-source]") || action.id === "platform-new" ? wireFormChoices(form, action).catch(failure => {
    if (form.isConnected) errorIn(error, failure.message);
  }) : Promise.resolve();
  const template = form.elements.ruleTemplate;
  if (template) template.onchange = () => {
    const detail = ruleTemplates[template.value];
    if (detail) ruleEditor.reset([{ dayStart: 1, dayEnd: 1, detailList: [detail] }]);
  };
  if (lookups) loadReportChoices(form).catch(failure => {
    if (form.isConnected) errorIn(error, failure.message);
  });
  if (choosesRule) loadRuleChoices(form, row.ruleId).catch(failure => {
    if (form.isConnected) errorIn(error, failure.message);
  });
  if (action.batch) {
    const batches = $(".operation-batches", form);
    $(".batch-add", form).onclick = () => {
      const group = document.createElement("fieldset");
      group.className = "operation-batch";
      group.innerHTML = `<legend>第 ${batches.children.length + 1} 组</legend><div class="form-grid">${fields(null)}</div>
        <button type="button" class="button secondary small batch-remove">移除此组</button>`;
      $(".batch-remove", group).onclick = () => {
        group.remove();
        [...batches.children].forEach((item, index) => { $("legend", item).textContent = `第 ${index + 1} 组`; });
      };
      batches.append(group);
      wireMultiSelects(group);
      ready.then((fill: any) => typeof fill === "function" ? fill(group) : undefined).catch(failure => errorIn(error, failure.message));
    };
  }
  form.onsubmit = event => {
    event.preventDefault();
    busy($('button[type="submit"]', form), async () => {
      error.replaceChildren();
      output.replaceChildren();
      const payload = operationPayload(action, form);
      if (row) {
        for (const key of recordKeys[action.id] || []) {
          const field = action.fields.find(field => field.key === key);
          const value = seedValue(row, action, field);
          payload[key] = Array.isArray(value) ? value : parseField(field, value);
        }
      }
      if (ruleEditor) payload.rule = ruleEditor.read();
      const token = action.path.startsWith("/open/") ? payload.authorization : undefined;
      if (token !== undefined) delete payload.authorization;
      if (simulated) {
        renderResult(output, { method: action.method, path: action.path, [action.method === "GET" ? "query" : "body"]: payload }, true);
        return;
      }
      let path = action.path;
      let body;
      if (action.method === "GET") {
        const search = query(payload);
        if (search) path += "?" + search;
      } else if (action.multipart) {
        body = new FormData();
        body.set("file", payload.file);
      } else {
        body = payload;
      }
      const response = await request(path, { body, token });
      if (typeof response === "string" && /失败|错误|无权|^error/i.test(response)) throw new Error(response);
      if (action.maintenance) {
        renderMaintenanceResult(output, action, response);
      } else if (action.id === "article-keywords") {
        const days = Array.isArray(response) ? response : response?.list || [];
        output.innerHTML = table(["日期", "关键词", "次数", "占比"], days.flatMap(day => (day.gaDataList || []).map(row =>
          [e(day.date), e(row.keyword), e(row.count), e(`${Number(row.percent * 100).toFixed(1)}%`)])), "近 15 天暂无关键词记录");
      } else if (platform) {
        output.innerHTML = table(["平台", "Campaign ID", "状态"], [[e(response.platform), e(response.campaignId), e(response.status)]]);
      } else if (action.id === "generate-image" && response?.url &&
          (/^\/media\/[a-z0-9-]+\.(?:png|jpg|gif|webp)$/i.test(response.url) || /^https:\/\//i.test(response.url))) {
        const url = e(response.url);
        output.innerHTML = `<p class="sub"><a href="${url}" target="_blank" rel="noopener noreferrer">打开生成图片</a></p>
          <img class="generated-image" src="${url}" alt="AI 生成的图片" referrerpolicy="no-referrer">`;
      } else renderResult(output, response ?? { message: "成功" }, false);
      if (action.reveal && response?.initialCredential) {
        output.insertAdjacentHTML("afterbegin", `<label>初始签名密钥（关闭窗口后不再显示）
          <input type="password" readonly autocomplete="off"></label><button class="button secondary small reveal-key" type="button">显示密钥</button>`);
        const key = $("input", output);
        key.value = response.initialCredential;
        $(".reveal-key", output).onclick = event => {
          key.type = key.type === "password" ? "text" : "password";
          event.currentTarget.textContent = key.type === "password" ? "显示密钥" : "隐藏密钥";
        };
        $("#modal").addEventListener("close", () => { key.value = ""; }, { once: true });
        $("button", form).remove();
      }
      if (!isRead) {
        toast(action.title + "已提交");
        if (refresh) await refresh();
      }
    }, message => errorIn(error, message));
  };
  if (action.maintenance) wireMaintenanceChoices(form, action).then(() => {
    if (form.isConnected && action.id === "adsense-source-report") form.requestSubmit();
  }).catch(failure => {
    if (form.isConnected) errorIn(error, failure.message);
  });
  if (row && action.method === "GET" && action.fields.every(field => recordKeys[action.id].includes(field.key))) form.requestSubmit();
}

function wireMultiSelects(root) {
  root.querySelectorAll(".multi-field").forEach(field => {
    const select = $("select", field);
    const details = $("details", field);
    const summary = $("summary", field);
    const options = $(".multi-options", field);
    const updateSummary = () => {
      const selected = [...select.selectedOptions];
      summary.textContent = selected.length === 1 ? selected[0].textContent
        : selected.length ? `已选择 ${selected.length} 项` : "请选择";
    };
    function render() {
      const choices = [...select.options];
      options.innerHTML = choices.length ? choices.map((option, index) =>
        `<label><input type="checkbox" data-option="${index}" ${option.selected ? "checked" : ""}> <span>${e(option.textContent)}</span></label>`).join("")
        : '<span class="sub">暂无选项</span>';
      updateSummary();
    }
    options.onchange = event => {
      const option = select.options[Number(event.target.dataset.option)];
      if (!option) return;
      option.selected = event.target.checked;
      updateSummary();
      select.dispatchEvent(new Event("change", { bubbles: true }));
    };
    select.addEventListener("invalid", () => { details.open = true; summary.focus(); });
    new MutationObserver(render).observe(select, { childList: true });
    render();
  });
}

async function loadRuleChoices(form, currentRule) {
  const submit = $('button[type="submit"]', form);
  const select = form.elements.ruleId;
  submit.disabled = true;
  const response = await request("/cms/ad/resource/rule/list?pageIndex=1&pageSize=1000", { body: {} });
  if (!form.isConnected) return;
  const rows = response.list || [];
  setReportChoices(select, rows.map(row => [String(row.id), row.name]), "请选择投放规则");
  if (rows.some(row => row.id === currentRule)) select.value = String(currentRule);
  submit.disabled = !rows.length;
  if (!rows.length) throw new Error("还没有可绑定的投放规则");
}

async function loadReportChoices(form) {
  const submit = $('button[type="submit"]', form);
  submit.disabled = true;
  const [options, defaults] = await Promise.all([
    request("/cms/console/report-options"), request("/cms/console/report-defaults")
  ]);
  if (!form.isConnected) return;
  const elements = form.elements;
  const fallback = defaultDateRange(7);
  elements.dateStart.value = defaults.start || elements.dateStart.value || fallback.start;
  elements.dateEnd.value = defaults.end || elements.dateEnd.value || fallback.end;
  wireReportChoices(form, options, defaults.pubId, elements.campaignId, rows => {
    for (const key of ["name", "channelId", "articleId", "country", "am"]) {
      const field = key === "name" ? "campaign" : key;
      setReportChoices(elements[field], rows.map(row => [row[key], row[key]]));
    }
    const platforms = rows.flatMap(row => row.platform.match(/[A-Za-z0-9_-]+/g) || []);
    setReportChoices(elements.adPlatform, platforms.map(id => [id, id]));
    const partners = new Set(rows.map(row => row.partner));
    setReportChoices(elements.partner, options.operators.filter(row => partners.has(row.partner)).map(row => [row.name, row.name]));
  });
  submit.disabled = !options.publishers.length;
  if (submit.disabled) throw new Error("当前账号没有可查询的发布商和活动");
}

