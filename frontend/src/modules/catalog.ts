import { request } from "./api.js";
import { businessActions, wireBusinessActions } from "./business.js";
import { loadSiteFilter, loadSiteSelect } from "./form-choices.js";
import { $, escape as e, busy, closeDialog, dialog, errorIn, formValues, numeric, pageHead, table, toast, toneBadge, listSearch, wireListSearch } from "./ui.js";

const PAGE_SIZE = 10;

const articleStates = {
  PASS: ["已通过", "teal"], AUDITING: ["待审核", "amber"], UPDATE_AUDITING: ["更新待审", "amber"],
  REJECT: ["已驳回", "red"], UPDATE_REJECT: ["更新驳回", "red"], DRAFT: ["草稿", "gray"], ARCHIVE: ["已归档", "gray"]
};
const channelStates = {
  ONLINE: ["在线", "teal"], ACTIVE: ["启用", "teal"], INIT: ["待激活", "amber"], OFFLINE: ["停用", "gray"]
};
const materialTypes = { IMAGE: ["图片", "teal"], VIDEO: ["视频", "amber"] };

function renderBadgeField(value, map) {
  if (value == null || value === "") return '<span class="muted">—</span>';
  const info = map[String(value).toUpperCase()];
  return info ? toneBadge(info[0], info[1]) : toneBadge(String(value), "gray");
}

const groups: Record<string, any> = {
  content: {
    title: "内容管理", description: "统一管理落地页文章、广告素材、文案组与内容分类。",
    sections: {
      articles: { title: "文章", url: "/cms/article/list", pageIn: "query", filter: ["title", "文章标题"],
        fields: [
          ["标题", "title"], ["文章 ID", "articleId"], ["站点", "site"], ["语言", "lang"],
          ["状态", row => ({ html: renderBadgeField(row.state, articleStates) })], ["更新时间", "utime"]
        ],
        sample: [{ title: "示例落地页文章", articleId: "demo-article-01", site: "demo-site", lang: "en", state: "DRAFT" }] },
      materials: { title: "广告素材", url: "/cms/ad/material/list", pageIn: "body", filter: ["keyword", "关键词"], previewMedia: true,
        fields: [
          ["名称", "name"], ["素材 ID", "adMaterialId"],
          ["类型", row => ({ html: renderBadgeField(row.type, materialTypes) })],
          ["语言", "lang"], ["尺寸", "dimension"], ["负责人", "userName"]
        ],
        sample: [{ name: "示例竖版创意", adMaterialId: "demo-asset-01", type: "IMAGE", lang: "en", dimension: "1080×1920", userName: "demo-operator" }] },
      copy: { title: "广告文案", url: "/cms/ad/text/list", pageIn: "body", filter: ["name", "文案名称"], editor: "copy", rootEdit: true,
        fields: [
          ["文案组", "name"],
          ["条数", row => Array.isArray(row.textList) ? `${row.textList.length} 条` : "—"],
          ["文案内容", "textList"], ["更新时间", "utime"]
        ],
        sample: [{ id: 1, name: "示例推广文案", textList: ["了解更多", "查看使用指南"] }] },
      categories: { title: "文章分类", url: "/cms/category/list",
        fields: [
          ["名称", "label"], ["标识", "value"],
          ["子分类数", row => `${row.childList?.length || 0} 个`],
          ["子分类", row => row.childList?.map(child => child.label)]
        ],
        sample: [{ label: "示例分类", value: "demo-category", childList: [{ label: "示例子分类" }] }] }
    }
  },
  accounts: {
    title: "账号与渠道", description: "集中管理广告平台账号、AdSense 发布商渠道与后台操作员权限。",
    sections: {
      publishers: { title: "AdSense 账号", url: "/cms/adsense/account/list", pageIn: "body",
        fields: [["发布商 ID", "pubId"], ["绑定站点", "siteList"], ["更新时间", "utime"]],
        sample: [{ pubId: "pub-demo", siteList: ["demo-site"] }] },
      importedApps: { title: "归档应用", archiveOnly: true,
        fields: [["应用 ID", "appId"], ["名称", "name"], ["系统", "os"], ["来源", "source"]] },
      channels: { title: "AdSense 渠道", url: "/cms/adsense/channel/list", pageIn: "body", filter: ["channelId", "渠道 ID"],
        fields: [
          ["渠道 ID", "channelId"], ["发布商 ID", "pubId"],
          ["状态", row => ({ html: renderBadgeField(row.state, channelStates) })], ["更新时间", "utime"]
        ],
        sample: [{ channelId: "demo-channel", pubId: "pub-demo", state: "ACTIVE" }] },
      platforms: { title: "平台广告账号", url: "/cms/console/ad-accounts", pageIn: "get", restricted: true,
        fields: [
          ["名称", "name"], ["记录 ID", "id"], ["广告账号 ID", "accountId"], ["平台", "platform"],
          ["合作方", "partner"], ["负责人", "operator"],
          ["状态", row => ({ html: row.active ? toneBadge("启用", "teal") : toneBadge("停用", "gray") })]
        ],
        sample: [{ name: "示例广告账号", accountId: "demo-ad-account", platform: "DEMO", partner: "demo-partner", operator: "demo-operator", active: true }] },
      operators: { title: "后台操作员", url: "/cms/console/operators", pageIn: "get", restricted: true,
        fields: [
          ["账号", "name"], ["说明", "desc"], ["合作方", "partner"], ["站点", "sites"],
          ["状态", row => ({ html: row.status === 1 ? toneBadge("启用", "teal") : toneBadge("停用", "gray") })]
        ],
        sample: [{ name: "demo-operator", desc: "示例操作员", partner: "demo-partner", sites: ["demo-site"], status: 1 }] }
    }
  },
  reportconfig: {
    title: "报表配置", description: "按站点查看和维护报表渠道映射配置。",
    sections: {
      channels: { title: "渠道配置", url: "/cms/report/config/list", pageIn: "body", filter: ["site", "站点"], editor: "report",
        fields: [["站点", "site"], ["渠道", "channel"], ["说明", "desc"], ["更新时间", "utime"]],
        sample: [{ site: "demo-site", channel: "demo-channel", desc: "示例报表渠道", value: "{}" }] }
    }
  }
};
const lastSection = new Map();

export function openCatalogSection(view: string, sectionKey: string) {
  lastSection.set(view, sectionKey);
  location.hash = "#" + view;
}

function readField(row, field) {
  const value = typeof field === "function" ? field(row) : row[field];
  if (value && typeof value === "object" && "html" in value) return value;
  if (Array.isArray(value)) return value.join(" / ") || "—";
  return value == null || value === "" ? "—" : String(value);
}

export function catalog(target, context, view, isCurrent) {
  const group = groups[view];
  const simulated = context.mode === "simulation";
  const root = context.account?.root === 1;
  const sections = Object.entries<any>(group.sections).filter(([, item]) =>
    (!item.archiveOnly || simulated) && (!item.restricted || simulated || root));
  const allowed = new Map<string, any>(sections);
  let key = lastSection.get(view);
  if (!allowed.has(key)) key = sections[0][0];
  let generation = 0;
  target.innerHTML = pageHead(group.title, group.description,
    view === "reportconfig" ? '<a class="button secondary" href="#settings">返回站点配置</a>' : "");
  const frame = document.createElement("section");
  frame.className = "panel";
  target.append(frame);

  function renderSection() {
    const section = allowed.get(key);
    const operationGroup = view === "reportconfig" ? view : key;
    const editable = !simulated && section.editor === "report";
    const articleDetail = simulated && key === "articles";
    const viewable = editable || (section.editor && (key !== "copy" || !root || simulated)) || articleDetail;
    let currentFilter = "";
    frame.innerHTML = `<div class="panel-head catalog-header">
      <div class="catalog-tabs" role="group" aria-label="目录类别">${sections.map(([id, item]) => `<button type="button" class="button secondary small" data-catalog="${id}" aria-pressed="${key === id}">${e(item.title)}</button>`).join("")}</div>
      <div class="action-set">${businessActions(operationGroup, context)}${editable ? '<button type="button" class="button small" id="new-catalog-item">新增</button>' : ""}</div></div>
      ${section.filter ? `<form class="toolbar catalog-search">${listSearch(section.filter[1], "", section.filter[0] === "site" && !simulated)}</form>` : ""}
      <div class="catalog-results"><div class="placeholder">正在读取…</div></div>`;
    const output = $(".catalog-results", frame);
    const activeRequest = ++generation;
    const displayed = () => isCurrent() && activeRequest === generation && output.isConnected;

    async function show(page = 1) {
      if (!displayed()) return;
      const pageData: Record<string, string> = { pageIndex: String(page), pageSize: String(PAGE_SIZE) };
      const numericPageData = { pageIndex: page, pageSize: PAGE_SIZE };
      const params = section.filter && currentFilter ? { [section.filter[0]]: currentFilter } : {};
      let response: any;
      if (simulated) {
        const archive = context.archiveData || {};
        const normalize = (row: any) => Object.fromEntries(Object.entries(row).map(([name, value]) =>
          [name === name.toUpperCase() ? name.toLowerCase() : name, value]));
        const source = ({
          articles: archive.articles,
          importedApps: archive.applications,
          operators: archive.users,
          categories: (archive.categories || []).map(normalize).map((category: any) => ({
            label: category.label, value: category.code,
            childList: (archive.childCategories || []).map(normalize)
              .filter((child: any) => child.site === category.site && child.parentCode === category.code)
          }))
        } as Record<string, any>)[key] || [];
        const results = source.map(normalize).filter((row: any) => !section.filter ||
          String(row[section.filter[0]] || row.name || "").toLowerCase().includes(currentFilter.toLowerCase()));
        response = { list: results, totalSize: results.length };
      } else {
        if (section.pageIn === "get" || section.pageIn === undefined) {
          response = await request(section.url + (section.pageIn === "get" ? "?" + new URLSearchParams(pageData) : ""));
        } else {
          const paramsInUrl = section.pageIn === "query";
          response = await request(section.url + (paramsInUrl ? "?" + new URLSearchParams(pageData) : ""),
            { body: paramsInUrl ? params : { ...params, ...numericPageData } });
        }
      }
      if (!displayed()) return;
      const rows = Array.isArray(response) ? response : response?.list || [];
      const hasMediaPreview = Boolean(section.previewMedia && rows.some(row => row.url));
      const hasActions = viewable || hasMediaPreview || rows.some(row => businessActions(operationGroup, context, row, 0));
      const columns = section.fields.map(([title]) => title).concat(hasActions ? ["操作"] : []);
      const cells = rows.map((row, index) => section.fields.map(([, field]) => {
        const val = readField(row, field);
        if (val && typeof val === "object" && "html" in val) return val.html;
        return `<span class="catalog-cell" title="${e(val)}">${e(val)}</span>`;
      }).concat(hasActions ? [`<div class="action-set">
        ${section.previewMedia && row.url ? `<button type="button" class="table-link" data-preview-media="${index}">预览</button>` : ""}
        ${viewable ? `<button type="button" class="table-link" data-item="${index}">${editable ? "编辑" : "查看"}</button>` : ""}
        ${businessActions(operationGroup, context, row, index)}</div>`] : []));
      const pages = Math.max(1, Math.ceil(Number(response?.totalSize || 0) / PAGE_SIZE));
      output.innerHTML = table(columns, cells, "没有符合条件的记录")
        + (!simulated && section.pageIn ? `<div class="pagination"><button type="button" class="button secondary small" data-page="${page - 1}" ${page <= 1 ? "disabled" : ""}>上一页</button>
          <span>第 ${page} / ${pages} 页 · 共 ${numeric(response?.totalSize)} 条</span><button type="button" class="button secondary small" data-page="${page + 1}" ${page >= pages ? "disabled" : ""}>下一页</button></div>` : "");
      output.querySelectorAll("[data-page]").forEach(button => {
        button.onclick = () => busy(button, () => show(Number(button.dataset.page)), toast);
      });
      output.querySelectorAll("[data-preview-media]").forEach(button => {
        const row = rows[Number(button.dataset.previewMedia)];
        button.onclick = () => previewMaterial(row);
      });
      output.querySelectorAll("[data-item]").forEach(button => {
        const row = rows[Number(button.dataset.item)];
        button.onclick = () => {
          if (articleDetail) {
            busy(button, async () => {
              const data = await request("/api/article/detail?" + new URLSearchParams({ articleId: row.articleId }));
              dialog(row.title, `<pre>${e(data.firstP || "")}\n\n${e(data.textList || "")}</pre>`);
            }, toast);
          } else edit(section.editor, row, !editable, () => show(page));
        };
      });
      wireBusinessActions(output, context, operationGroup, rows, () => show(page));
    }

    frame.querySelectorAll<HTMLElement>("[data-catalog]").forEach(button => button.onclick = () => {
      if (key === button.dataset.catalog) return;
      key = button.dataset.catalog;
      lastSection.set(view, key);
      renderSection();
    });
    const form = $("form", frame);
    if (form?.elements.filter.tagName === "SELECT") loadSiteFilter(form, "", toast);
    if (form) wireListSearch(form, value => {
      currentFilter = value;
      return show();
    });
    if (editable) $("#new-catalog-item", frame).onclick = () => edit(section.editor, null, false, () => show(1));
    wireBusinessActions(frame, context, operationGroup, null, () => show(1));
    show().catch(error => { if (displayed()) errorIn(output, error.message); });
  }
  renderSection();
}

function previewMaterial(row) {
  const safeUrl = /^(\/media\/|https?:\/\/)/i.test(row.url || "") ? row.url : "";
  const meta = [
    ["素材 ID", row.adMaterialId], ["类型", (materialTypes[row.type] || [row.type])[0]],
    ["分辨率", row.dimension], ["比例", row.ratio], ["语言", row.lang], ["国家", row.country],
    ["文件地址", row.url]
  ];
  const mediaEl = safeUrl && row.type === "IMAGE"
    ? `<div class="media-preview-box"><img class="generated-image" src="${e(safeUrl)}" alt="${e(row.name || "素材预览")}" referrerpolicy="no-referrer"></div>`
    : safeUrl && row.type === "VIDEO"
      ? `<p class="sub"><a class="table-link" href="${e(safeUrl)}" target="_blank" rel="noopener noreferrer">在新窗口打开视频地址</a></p>`
      : "";
  dialog(row.name || "素材预览", `${mediaEl}${table(["属性", "内容"], meta.map(([k, v]) => [e(k), e(v || "—")]))}`);
}

function edit(editor, row, readOnly, refresh) {
  const copy = editor === "copy";
  const content = dialog(readOnly ? "查看" : row ? "编辑" : "新增", `<form><div class="form-grid">${copy
    ? `<label class="all">文案组<input name="name" value="${e(row?.name)}" required ${readOnly ? "readonly" : ""}></label>
       <label class="all">广告文案（每行一条）<textarea name="text" rows="7" required ${readOnly ? "readonly" : ""}>${e(row?.textList?.join("\n"))}</textarea></label>`
    : `<label>站点${row ? `<input name="site" readonly value="${e(row.site)}">` : '<select name="site" required><option value="">正在读取…</option></select>'}</label>
       <label>渠道<input name="channel" value="${e(row?.channel)}" required></label>
       <label class="all">说明<input name="desc" value="${e(row?.desc)}" required ${readOnly ? "readonly" : ""}></label>
       <label class="all">配置值<textarea name="value" rows="6" required ${readOnly ? "readonly" : ""}>${e(row?.value)}</textarea></label>`}</div>
    <div class="editor-error"></div>${readOnly ? "" : '<div class="form-actions"><button type="submit" class="button">保存</button></div>'}</form>`);
  if (readOnly) return;
  if (!row && !copy) loadSiteSelect($("form", content), message => errorIn($(".editor-error", content), message));
  $("form", content).onsubmit = event => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = formValues(form);
    busy($("button", form), async () => {
      const body: Record<string, any> = copy ? { name: values.name, textList: values.text.split(/\r?\n/).map(line => line.trim()).filter(Boolean), ...(row ? { id: row.id } : {}) }
        : { ...values, ...(row ? { oldChannel: row.channel } : {}), value: new FormData(form).get("value") };
      if (copy && (!body.name || !body.textList.length)) throw new Error("请填写文案组名称和至少一条广告文案");
      const result = await request(`${copy ? "/cms/ad/text/console" : "/cms/report/config"}/${row ? "edit" : "add"}`, { body });
      if (typeof result === "string" && result) throw new Error(result);
      closeDialog();
      toast("已保存");
      await refresh();
    }, message => errorIn($(".editor-error", form), message));
  };
}
