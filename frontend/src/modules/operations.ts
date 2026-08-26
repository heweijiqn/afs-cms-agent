import { request } from "./api.js";
import { $, escape as e, numeric, pageHead, metric, table, formValues, errorIn, clampStartToLastDays } from "./ui.js";
import { mountChart } from "./chart.js";

let disposeChart = () => {};

export function releaseLogChart() {
  disposeChart();
}

const allEvents = row => row.trace + row.debug + row.info + row.warn + row.error;
const labels = ["告警", "错误"];
const colors = ["#cc912d", "#bb455c"];

export async function logReport(target, context, isCurrent, login) {
  target.innerHTML = pageHead("历史运行日志", "从已导入的 AFS CMS 主日志按日期汇总。", '<a class="button secondary" href="#reports">返回投放报表</a>');
  const body = document.createElement("div");
  target.append(body);
  if (!context.hasLogReport) {
    body.innerHTML = '<div class="placeholder">当前暂无系统日志汇总数据。</div>';
    return;
  }
  body.innerHTML = '<div class="placeholder">正在读取日志汇总…</div>';
  try {
    const report = await request("/cms/console/log-report");
    if (!isCurrent()) return;
    const days = report.days || [];
    if (!days.length) {
      body.innerHTML = '<div class="placeholder">归档中没有可展示的日志记录。</div>';
      return;
    }
    const first = days[0].date;
    const last = days.at(-1).date;
    const defaultStart = clampStartToLastDays(first, last, 7);
    body.innerHTML = `<section class="panel"><div class="panel-head"><div><h2>归档范围</h2><p class="sub">${e(first)} 至 ${e(last)} · ${numeric(report.files)} 份主日志 · ${numeric(days.length)} 个有记录日期</p></div></div>
      <form class="form-grid"><label>开始日期<input type="date" name="start" value="${e(defaultStart)}" min="${e(first)}" max="${e(last)}" required></label>
      <label>结束日期<input type="date" name="end" value="${e(last)}" min="${e(first)}" max="${e(last)}" required></label>
      <button class="button" type="submit">筛选日期</button></form><div id="log-filter-error"></div></section><div id="log-results"></div>`;
    const results = $("#log-results", body);
    function display(start, end) {
      const rows = days.filter(row => row.date >= start && row.date <= end);
      const sum = reducer => rows.reduce((total, row) => total + reducer(row), 0);
      disposeChart();
      if (!rows.length) {
        results.innerHTML = '<div class="placeholder">所选日期没有日志记录。</div>';
        return;
      }
      results.innerHTML = `<div class="cards">${metric("日志条数", numeric(sum(allEvents)))}${metric("WARN 告警", numeric(sum(row => row.warn)))}${metric("ERROR 错误", numeric(sum(row => row.error)))}${metric("有记录日期", numeric(rows.length))}</div>
        <section class="panel"><div class="panel-head"><h2>告警与错误趋势</h2><div class="trend-legend log-legend"><span>告警</span><span>错误</span></div></div>
        <canvas class="chart" tabindex="0" aria-label="按日志日期显示告警和错误数量"></canvas><p class="chart-caption" aria-live="polite">悬停或按方向键查看每日数量</p></section>
        <section class="panel"><div class="panel-head"><h2>逐日日志</h2></div>${table(["日志日期", "总数", "DEBUG / TRACE", "INFO", "WARN", "ERROR"],
          rows.map(row => [e(row.date), numeric(allEvents(row)), numeric(row.debug + row.trace), numeric(row.info), numeric(row.warn), numeric(row.error)].map(e)))}</section>`;
      const points = rows.map(row => ({ date: row.date, spend: row.warn, revenue: row.error }));
      disposeChart = mountChart($(".chart", results), $(".chart-caption", results), points, numeric, { labels, colors });
    }
    display(defaultStart, last);
    $("form", body).onsubmit = event => {
      event.preventDefault();
      const { start, end } = formValues(event.currentTarget);
      const error = $("#log-filter-error", body);
      if (start > end) {
        errorIn(error, "结束日期必须晚于或等于开始日期");
        return;
      }
      error.replaceChildren();
      display(start, end);
    };
  } catch (error) {
    if (!isCurrent()) return;
    if (error.status === 401 && context.requireStaffLogin) {
      body.innerHTML = '<div class="placeholder">登录后查看日志汇总。 <button type="button" class="button small">登录</button></div>';
      $("button", body).onclick = login;
    } else errorIn(body, error.message);
  }
}

