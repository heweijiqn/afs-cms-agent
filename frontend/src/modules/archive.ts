import { request } from "./api.js";
import { $, escape as e, errorIn, formValues, metric, numeric, pageHead, table, defaultDateRange, clampStartToLastDays } from "./ui.js";

const PAGE_SIZE = 20;
const columns: Array<[string, string, Array<[string, string]>]> = [
  ["metrics", "广告记录", [["日期", "date"], ["应用 ID", "appId"], ["广告位 ID", "adId"], ["国家或地区", "country"],
    ["PV", "pv"], ["TOTAL", "total"], ["eCPM", "ecpm"], ["状态", "status"]]],
  ["applications", "应用", [["来源", "source"], ["记录 ID", "id"], ["应用 ID", "appId"], ["名称", "name"], ["系统", "os"], ["比例", "ratio"], ["状态", "status"]]],
  ["adUnits", "广告位", [["记录 ID", "id"], ["广告位 ID", "adId"], ["应用 ID", "appId"], ["名称", "name"], ["类型", "adType"], ["开关", "onOff"], ["状态", "status"]]],
  ["users", "用户记录", [["记录 ID", "id"], ["用户名", "name"], ["权限代码", "root"], ["状态", "status"], ["创建时间", "createdAt"]]],
  ["access", "应用访问记录", [["记录 ID", "id"], ["用户名", "userName"], ["应用 ID", "appId"], ["访问等级", "accessLevel"], ["创建时间", "createdAt"]]],
  ["categories", "文章分类", [["站点字段", "site"], ["分类代码", "code"], ["名称", "label"]]],
  ["childCategories", "文章子分类", [["站点字段", "site"], ["父分类", "parentCode"], ["分类代码", "code"], ["名称", "label"]]],
  ["articles", "文章", [["标题", "title"], ["分类", "category"], ["语言", "lang"], ["上线状态", "online"]]]
];

function value(row, key) {
  return row[key] ?? row[key.toUpperCase()] ?? "—";
}

let tab = "metrics";

export async function archiveReport(target, context, isCurrent, login, reportView = false) {
  target.innerHTML = pageHead(reportView ? "投放报表" : "归档数据库", "历史归档提供 PV、TOTAL 和 eCPM。档案未包含花费或转化。",
    reportView ? '<a class="button secondary" href="#dimensions">多维报表</a><a class="button secondary" href="#platform-reference">海外平台投放参考</a>' :
      '<a class="button secondary" href="#reports">返回投放报表</a>');
  const wrap = document.createElement("div");
  target.append(wrap);
  wrap.innerHTML = '<div class="placeholder">正在读取历史归档数据…</div>';

  if (!context.hasArchiveData) {
    wrap.innerHTML = '<div class="placeholder">当前暂无已导入的历史归档数据。</div>';
    return;
  }

  try {
    const data = await request("/cms/console/archive-data");
    if (!isCurrent()) return;
    const metrics = data.metrics || [];
    if (reportView) tab = "metrics";
    const first = metrics[0]?.date || "";
    const last = metrics.at(-1)?.date || "";
    const fallbackRange = defaultDateRange(7);
    let start = last ? clampStartToLastDays(first, last, 7) : fallbackRange.start;
    let end = last || fallbackRange.end;
    let index = 1;

    function draw() {
      const [, title, fields] = columns.find(([key]) => key === tab);
      const rows = (data[tab] || []).filter(row => tab !== "metrics" || ((!start || row.date >= start) && (!end || row.date <= end)));
      const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
      index = Math.min(pages, index);
      const selected = rows.slice((index - 1) * PAGE_SIZE, index * PAGE_SIZE);
      const stored = columns.reduce((sum, [key]) => sum + (data[key] || []).length, 0);
      const summary = tab === "metrics" ? `<div class="cards">${metric("归档广告记录", numeric(rows.length))}
        ${metric("PV 合计", numeric(rows.reduce((sum, row) => sum + Number(value(row, "pv") === "—" ? 0 : value(row, "pv")), 0)))}
        ${metric("有记录日期", numeric(new Set(rows.map(row => row.date)).size))}${metric("全部归档记录", numeric(stored))}</div>` : "";

      wrap.innerHTML = `<section class="panel"><div class="catalog-tabs" role="group" aria-label="归档表">
        ${columns.map(([key, label]) => `<button type="button" class="button secondary small" data-archive-table="${key}" aria-pressed="${tab === key}">
          ${e(label)} · ${numeric(data[key]?.length ?? 0)}</button>`).join("")}</div></section>`
        + (tab === "metrics" ? `<section class="panel"><div class="panel-head"><div><h2>历史广告指标</h2>
          <p class="sub">档案日期 ${e(first || "—")} 至 ${e(last || "—")}。TOTAL 和 eCPM 沿用快照原字段。</p></div></div>
        <form class="form-grid"><label>开始日期<input type="date" name="start" value="${e(start)}"></label>
          <label>结束日期<input type="date" name="end" value="${e(end)}"></label>
          <button type="submit" class="button">筛选日期</button></form><div id="archive-filter-error"></div></section>` : "")
        + summary + `<section class="panel"><div class="panel-head"><h2>${e(title)}</h2></div>
          ${table(fields.map(([label]) => label), selected.map(row => fields.map(([, key]) => e(value(row, key)))), "所选范围没有记录")}
          <div class="pagination"><button type="button" class="button secondary small" data-archive-page="${index - 1}" ${index === 1 ? "disabled" : ""}>上一页</button>
          <span>第 ${index} / ${pages} 页 · ${numeric(rows.length)} 条</span><button type="button" class="button secondary small" data-archive-page="${index + 1}" ${index === pages ? "disabled" : ""}>下一页</button></div></section>`;
      wrap.querySelectorAll<HTMLElement>("[data-archive-table]").forEach(button => {
        button.onclick = () => { tab = button.dataset.archiveTable || "metrics"; index = 1; draw(); };
      });
      wrap.querySelectorAll<HTMLElement>("[data-archive-page]").forEach(button => {
        button.onclick = () => { index = Number(button.dataset.archivePage); draw(); };
      });
      const form = $("form", wrap);
      if (form) form.onsubmit = event => {
        event.preventDefault();
        const range = formValues(form);
        if (range.start && range.end && range.start > range.end) {
          errorIn($("#archive-filter-error", wrap), "结束日期不能早于开始日期");
          return;
        }
        ({ start, end } = range);
        index = 1;
        draw();
      };
    }

    draw();
  } catch (error) {
    if (!isCurrent()) return;
    if (error.status === 401 && context.requireStaffLogin) {
      wrap.innerHTML = '<div class="placeholder">登录后查看归档。 <button type="button" class="button small">登录</button></div>';
      $("button", wrap).onclick = login;
    } else errorIn(wrap, error.message);
  }
}

