import { request } from "./api.js";
import { $, escape as e, busy, errorIn, formValues, pageHead, table } from "./ui.js";

const article = ["articleId", "文章 ID"];
const category = ["category", "分类"];
const keyword = ["searchKeyword", "搜索词"];
const ip = ["ip", "IP 地址"];
const portalActions = [
  ["热门关键词", "hot/words/list"], ["分类目录", "category/list"],
  ["分类文章", "category/article/list", [category]], ["相关文章", "related/article/list", [article]],
  ["搜索文章", "search/article/list", [keyword]], ["文章详情", "article/detail", [article]],
  ["Feed 列表", "feed/list"], ["Feed 详情", "feed/article/detail", [article]]
];
const groups = {
  article: { title: "通用文章", operations: [
    ["首页文章", "/api/article/home/list"], ["按分类查询", "/api/article/category/list", [category]],
    ["搜索文章", "/api/article/search/list", [keyword]], ["CCC 文章搜索", "/api/article/ccc/search/list", [keyword]],
    ["文章详情", "/api/article/detail", [article]], ["分类目录", "/api/category/list"]
  ] },
  ...Object.fromEntries([["bestreply", "Bestreply"], ["infozone", "Infozone"], ["valibooks", "Valibooks"]].map(([site, title]) =>
    [site, { title, site, operations: portalActions.map(([label, path, fields]) => [label, `/api/${site}/${path}`, fields]) }])),
  business: { title: "配置与上报", operations: [
    ["查询站点配置", "/api/config/get", [["name", "配置名称"]]],
    ["查询报表渠道配置", "/api/report/config/detail", [["channel", "渠道"]]],
    ["查询 IP 上报状态", "/api/ip/get", [ip]],
    ["提交 IP 上报", "/api/ip/report", [], [ip]],
    ["提交反馈", "/api/feedback/add", [], [["value", "反馈内容", "textarea"], ["rate", "评分", "number"], ["locale", "语言"]]],
    ["提交关键词上报", "/api/keyword/report", [], [article, ["keyword", "关键词"]]]
  ] },
  service: { title: "服务状态", operations: [
    ["服务首页", "/api/service/"], ["健康检查", "/api/service/health"], ["POST 健康检查", "/api/service/healthX", [], []]
  ] }
};

function result(data) {
  if (data == null) return '<p class="sub">操作已完成。</p>';
  if (!Array.isArray(data)) return `<pre>${e(JSON.stringify(data, null, 2))}</pre>`;
  if (data.length && data.every(row => typeof row === "object" && row !== null && !Array.isArray(row))) {
    const preferred = [["title", "标题"], ["articleId", "文章 ID"], ["category", "分类"], ["lang", "语言"], ["site", "站点"],
      ["label", "名称"], ["value", "标识"]];
    const columns = preferred.filter(([key]) => data.some(row => row[key] != null));
    if (columns.length) return table(columns.map(([, label]) => label), data.map(row =>
      columns.map(([key]) => e(row[key] ?? "—"))), "暂无记录");
  }
  return table(["结果"], data.map(item => [e(typeof item === "object" ? JSON.stringify(item) : item)]), "暂无记录");
}

export function apiWorkbench(target, current) {
  target.innerHTML = pageHead("API 数据管理", "查询站点内容与配置，提交反馈、关键词和访问上报。",
    '<a class="button secondary" href="#settings">站点配置</a><a class="button secondary" href="#feedback">查看反馈</a>')
    + '<section class="panel" id="api-controls"></section><section class="panel"><h2>结果</h2><div id="api-result" aria-live="polite"></div></section>';
  const controls = $("#api-controls", target);
  const output = $("#api-result", target);
  let selected = "article";
  let index = 0;
  let revision = 0;
  const siteNames = new Map();

  function render() {
    const generation = ++revision;
    const group = groups[selected];
    const [, path, queryFields = [], bodyFields] = group.operations[index];
    const fields = [...queryFields, ...(bodyFields || [])].map(([name, title, kind]) =>
      `<label>${e(title)}${kind === "textarea" ? `<textarea name="${name}" required maxlength="4000" rows="3"></textarea>`
        : `<input name="${name}" required maxlength="1024" ${kind === "number" ? 'type="number" min="1" max="5"' : ""}>`}</label>`).join("");
    controls.innerHTML = `<div class="panel-head"><div class="catalog-tabs" role="group" aria-label="API 模块">${Object.entries(groups).map(([key, value]) =>
      `<button type="button" class="button secondary small" data-api-group="${key}" aria-pressed="${key === selected}">${e(value.title)}</button>`).join("")}</div></div>
      <form class="form-grid"><label>操作<select name="operation">${group.operations.map(([label], i) =>
        `<option value="${i}" ${i === index ? "selected" : ""}>${e(label)}</option>`).join("")}</select></label>
      <label>站点<select name="site" required><option value="">正在读取…</option></select></label>
      ${fields}<div class="form-actions"><button class="button" type="submit">${bodyFields ? "提交" : "查询"}</button></div></form>
      <div class="sub">${bodyFields ? "POST" : "GET"} ${e(path)}</div><div id="api-operation-error"></div>`;
    output.innerHTML = '<div class="placeholder">选择操作后提交。</div>';
    const form = $("form", controls);
    loadSiteSelect(form, message => errorIn($("#api-operation-error", controls), message), siteNames.get(selected) || group.site);
    controls.querySelectorAll("[data-api-group]").forEach(button => button.onclick = () => {
      siteNames.set(selected, form.elements.site.value);
      selected = button.dataset.apiGroup;
      index = 0;
      render();
    });
    form.elements.operation.onchange = () => {
      siteNames.set(selected, form.elements.site.value);
      index = Number(form.elements.operation.value);
      render();
    };
    form.onsubmit = event => {
      event.preventDefault();
      const error = $("#api-operation-error", controls);
      const isCurrent = () => current() && generation === revision;
      busy($('button[type="submit"]', form), async () => {
        error.replaceChildren();
        const values = formValues(form);
        if (!/^[A-Za-z0-9._-]+$/.test(values.site)) throw new Error("站点标识只能包含字母、数字、点、下划线和连字符。");
        const query = Object.fromEntries(queryFields.map(([name]) => [name, values[name]]));
        const body = bodyFields && Object.fromEntries(bodyFields.map(([name, , kind]) =>
          [name, kind === "number" ? Number(values[name]) : values[name]]));
        const params = new URLSearchParams(query).toString();
        const data = await request(path + (params ? "?" + params : ""),
          { body, site: values.site, anonymous: true });
        if (isCurrent()) output.innerHTML = result(data);
      }, message => { if (isCurrent()) errorIn(error, message); });
    };
  }
  render();
}
import { loadSiteSelect } from "./form-choices.js";
