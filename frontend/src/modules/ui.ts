export const $ = (selector: string, root: ParentNode = document): any => root.querySelector(selector);
export const escape = (value: any): string => String(value ?? "").replace(/[&<>"']/g,
  character => (({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" } as Record<string, string>)[character]));
export const n = (value: any): number => Number(value ?? 0);
export const numeric = (value: any): string => n(value).toLocaleString("zh-CN");
export const ratio = (num: any, denom: any): number | null => denom > 0 ? num / denom : null;
export const multiple = (value: any): string => value == null ? "—" : `${n(value).toFixed(2)}×`;
export const percent = (value: any): string => value == null ? "—" : `${(n(value) * 100).toFixed(2)}%`;
export const money = (value: any, currency: string = "USD"): string => {
  if (value == null) return "—";
  try {
    return new Intl.NumberFormat("zh-CN", { style: "currency", currency, currencyDisplay: "code" }).format(n(value));
  } catch {
    return `${n(value).toFixed(2)} ${currency}`;
  }
};
export const dateTime = value => value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—";

function toIsoDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function defaultDateRange(days = 7, anchorIso = "") {
  const span = Math.max(1, Number(days) || 7);
  const endDate = anchorIso ? new Date(`${anchorIso}T00:00:00`) : new Date();
  if (!anchorIso) endDate.setDate(endDate.getDate() - 1);
  if (Number.isNaN(endDate.getTime())) {
    const fallback = new Date();
    fallback.setDate(fallback.getDate() - 1);
    const startFallback = new Date(fallback);
    startFallback.setDate(startFallback.getDate() - (span - 1));
    return { start: toIsoDate(startFallback), end: toIsoDate(fallback) };
  }
  const startDate = new Date(endDate);
  startDate.setDate(startDate.getDate() - (span - 1));
  return { start: toIsoDate(startDate), end: toIsoDate(endDate) };
}

export function clampStartToLastDays(firstIso, lastIso, days = 7) {
  if (!lastIso) return firstIso || defaultDateRange(days).start;
  const candidate = defaultDateRange(days, lastIso).start;
  return firstIso && candidate < firstIso ? firstIso : candidate;
}
export const statusNames = { PENDING: "待审批", APPROVED: "已通过", REJECTED: "已驳回", SIMULATED: "已试算",
  EXECUTED: "已执行", ROLLED_BACK: "已回滚",
  ANALYSIS_READY: "分析已生成", ANALYSIS_FAILED: "分析降级",
  REANALYZE_STANDARD: "重新分析", REANALYZE_DEEP: "深度重新分析" };
const tones = { PENDING: "amber", APPROVED: "teal", REJECTED: "red", SIMULATED: "gray", EXECUTED: "teal", ROLLED_BACK: "red" };

export function badge(value) {
  return `<span class="badge ${tones[value] || "gray"}">${escape(statusNames[value] || value)}</span>`;
}

export function toneBadge(label, tone = "gray") {
  return `<span class="badge ${escape(tone)}">${escape(label)}</span>`;
}

export function roasBadge(value) {
  if (value == null || !Number.isFinite(Number(value))) return "—";
  const num = Number(value);
  const tone = num >= 1.25 ? "teal" : num >= 1.0 ? "amber" : "red";
  return `<span class="badge ${tone} mono">${num.toFixed(2)}×</span>`;
}

export function profitDisplay(value, currency = "") {
  if (value == null || !Number.isFinite(Number(value))) return "—";
  const num = Number(value);
  const cls = num > 0 ? "num-pos" : num < 0 ? "num-neg" : "muted";
  const sign = num > 0 ? "+" : num < 0 ? "-" : "";
  const formatted = Math.abs(num).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return `<span class="${cls} mono">${sign}${currency ? `${escape(currency)} ` : ""}${formatted}</span>`;
}

export function starRating(rate) {
  const num = Number(rate);
  if (!Number.isFinite(num) || num <= 0) return '<span class="muted">—</span>';
  const rounded = Math.max(0, Math.min(5, Math.round(num)));
  const tone = num >= 4 ? "teal" : num >= 3 ? "amber" : "red";
  return `<span class="badge ${tone}">${"★".repeat(rounded)}${"☆".repeat(5 - rounded)} ${num.toFixed(1)}</span>`;
}

export function exportCsv(filename, headers, rows) {
  const cell = value => {
    const text = String(value ?? "").replace(/\r?\n/g, " ");
    return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
  };
  const lines = [headers.map(cell).join(","), ...rows.map(row => row.map(cell).join(","))];
  const blob = new Blob(["\uFEFF" + lines.join("\r\n")], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function pageHead(title: any, caption: any = "", actions: any = ""): string {
  return `<div class="page-head"><div><h1>${escape(title)}</h1><p>${escape(caption)}</p></div><div class="action-set">${actions}</div></div>`;
}

export function listSearch(label: any, value: any = "", choices: boolean = false, extraFields: string = ""): string {
  return `<label>${escape(label)}${choices ? '<select name="filter"><option value="">全部站点</option></select>' : `<input type="search" name="filter" value="${escape(value)}" maxlength="150" placeholder="输入关键词搜索…">`}</label>${extraFields}
    <button type="submit" class="button">搜索</button>
    <button type="button" class="button secondary" data-clear-search>清除</button>`;
}

export function wireListSearch(form: any, search: any, onError?: any): void {
  form.onsubmit = event => {
    event.preventDefault();
    return busy(form.querySelector('[type="submit"]'), () => search(form.elements.filter.value.trim()), onError);
  };
  form.querySelector("[data-clear-search]").onclick = () => {
    form.elements.filter.value = "";
    form.requestSubmit();
  };
}

export function sparklineSvg(values: number[], tone: "teal" | "indigo" | "red" = "teal"): string {
  const nums = (values || []).map(v => Number(v)).filter(v => Number.isFinite(v));
  if (nums.length < 2) return "";
  const min = Math.min(...nums);
  const max = Math.max(...nums);
  const span = max - min || 1;
  const w = 140;
  const h = 30;
  const pad = 3;
  const pts = nums.map((v, i) => {
    const x = pad + (i * (w - pad * 2)) / (nums.length - 1);
    const y = h - pad - ((v - min) / span) * (h - pad * 2);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  const area = `${pad},${h - pad} ${pts.join(" ")} ${w - pad},${h - pad}`;
  return `<svg class="kpi-sparkline spark-${tone}" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" aria-hidden="true">
    <polygon class="spark-area" points="${area}" />
    <polyline class="spark-line" points="${pts.join(" ")}" />
  </svg>`;
}

export function metric(label, value, extra = "", sparklineHtml = "") {
  return `<div class="metric"><div class="metric-label">${escape(label)}</div><div class="metric-value">${escape(value)}</div>${
    extra ? `<div class="metric-extra">${escape(extra)}</div>` : ""
  }${sparklineHtml}</div>`;
}

export function table(columns, rows, empty = "暂无记录") {
  if (!rows.length) return `<div class="placeholder">${escape(empty)}</div>`;
  return `<div class="table-wrap" tabindex="0"><table><thead><tr>${columns.map(column =>
    typeof column === "object" && column && "html" in column
      ? `<th scope="col"${column.className ? ` class="${escape(column.className)}"` : ""}>${column.html}</th>`
      : `<th scope="col">${escape(column)}</th>`
  ).join("")}</tr></thead>
    <tbody>${rows.map(row => `<tr>${row.map(cell => `<td>${cell}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
}

export function errorIn(target, message) {
  target.innerHTML = `<div class="error" role="alert">${escape(message)}</div>`;
}

let toastTimeout;
export function toast(message) {
  const node = $("#toast");
  node.textContent = message;
  node.hidden = false;
  clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => { node.hidden = true; }, 4300);
}

export async function busy(button, action, failure = toast) {
  if (button.disabled) return;
  button.disabled = true;
  try {
    await action();
  } catch (error) {
    failure(error.message || "请求失败");
  } finally {
    button.disabled = false;
  }
}

export function dialog(title, markup) {
  const window = $("#modal");
  $("#modal-title").textContent = title;
  const content = $("#modal-content");
  content.innerHTML = markup;
  if (!window.open) window.showModal();
  return content;
}

export function closeDialog() {
  $("#modal").close();
}

export function formValues(form: HTMLFormElement): Record<string, string> {
  return Object.fromEntries([...new FormData(form)].map(([key, value]) => [key, String(value).trim()]));
}


// 轻量 Markdown 渲染：先转义再用白名单标签重建，避免模型输出直接注入 HTML。
export function renderMarkdown(text) {
  const source = String(text ?? "");
  if (!source.trim()) return "";
  const inline = value => value
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  const lines = escape(source).split(/\r?\n/);
  const output = [];
  let listType = null;
  let paragraphs = [];
  let tableRows = null;
  let codeBlock = null;
  const flushParagraph = () => {
    if (!paragraphs.length) return;
    output.push(`<p>${paragraphs.map(inline).join("<br>")}</p>`);
    paragraphs = [];
  };
  const flushList = () => {
    if (!listType) return;
    output.push(`</${listType}>`);
    listType = null;
  };
  const flushTable = () => {
    if (!tableRows) return;
    const [head, ...body] = tableRows;
    output.push(`<table><thead><tr>${head.map(cell => `<th>${inline(cell)}</th>`).join("")}</tr></thead><tbody>${
      body.map(row => `<tr>${row.map(cell => `<td>${inline(cell)}</td>`).join("")}</tr>`).join("")}</tbody></table>`);
    tableRows = null;
  };
  const flushAll = () => { flushParagraph(); flushList(); flushTable(); };
  const splitRow = line => line.replace(/^\|/, "").replace(/\|$/, "").split("|").map(cell => cell.trim());
  for (const line of lines) {
    if (codeBlock) {
      if (/^\s*```/.test(line)) { output.push(`<pre>${codeBlock.join("\n")}</pre>`); codeBlock = null; }
      else codeBlock.push(line);
      continue;
    }
    if (/^\s*```/.test(line)) { flushAll(); codeBlock = []; continue; }
    const trimmed = line.trim();
    if (!trimmed) { flushAll(); continue; }
    const heading = trimmed.match(/^(#{1,6})\s+(.*)$/);
    if (heading) {
      flushAll();
      const level = Math.min(6, Math.max(3, heading[1].length + 2));
      output.push(`<h${level}>${inline(heading[2])}</h${level}>`);
      continue;
    }
    if (/^([-*_])\1{2,}$/.test(trimmed)) { flushAll(); output.push("<hr>"); continue; }
    if (/^\|.*\|$/.test(trimmed)) {
      flushParagraph(); flushList();
      const cells = splitRow(trimmed);
      if (cells.every(cell => /^:?-{2,}:?$/.test(cell))) continue;
      tableRows = tableRows || [];
      tableRows.push(cells);
      continue;
    }
    flushTable();
    const bullet = trimmed.match(/^[-*+]\s+(.*)$/);
    if (bullet) {
      flushParagraph();
      if (listType !== "ul") { flushList(); output.push("<ul>"); listType = "ul"; }
      output.push(`<li>${inline(bullet[1])}</li>`);
      continue;
    }
    const numbered = trimmed.match(/^\d+[.)]\s+(.*)$/);
    if (numbered) {
      flushParagraph();
      if (listType !== "ol") { flushList(); output.push("<ol>"); listType = "ol"; }
      output.push(`<li>${inline(numbered[1])}</li>`);
      continue;
    }
    flushList();
    paragraphs.push(trimmed);
  }
  flushAll();
  if (codeBlock) output.push(`<pre>${codeBlock.join("\n")}</pre>`);
  return output.join("");
}