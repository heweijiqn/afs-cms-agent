import { request, requestList } from "./api.js";
import { setReportChoices } from "./report-options.js";

export const adPlatforms = ["Facebook", "Google", "Taboola", "TikTok"];
const pairs = (rows, key, label = key) => rows.map(row => [row[key], row[label] || row[key]]);
const strings = rows => rows.map(value => [value, value]);
const paged = async (path, params = {}, inQuery = false) => {
  const rows = [];
  const pageSize = 100;
  for (let pageIndex = 1; ; pageIndex++) {
    const paging = { pageIndex, pageSize };
    const pagingQuery = { pageIndex: String(pageIndex), pageSize: String(pageSize) };
    const data = await request(inQuery ? path + "?" + new URLSearchParams(pagingQuery) : path,
      { body: inQuery ? params : { ...params, ...paging } });
    const batch = data?.list || [];
    rows.push(...batch);
    if (!batch.length || rows.length >= (data.totalSize ?? rows.length)) return rows;
  }
};

const sources = {
  articles: async () => pairs(await paged("/cms/article/list", {}, true), "articleId", "title"),
  countries: async () => pairs(await requestList("/cms/region/list"), "regionCode", "name"),
  languages: async () => pairs(await requestList("/cms/lang/list"), "value", "label"),
  managers: async () => strings(await requestList("/cms/am/list")),
  publishers: async () => pairs(await paged("/cms/adsense/account/list"), "pubId"),
  sites: async () => strings(await requestList("/cms/site/list")),
  categories: async () => pairs(await requestList("/cms/category/list"), "value", "label"),
  materialCategories: async () => pairs((await requestList("/cms/ad/material/category/list")).flatMap(row => [row, ...(row.childList || [])]), "value", "label"),
  // 审核选择应展示本地已绑定的全部可用渠道；INIT 过滤会把历史 ACTIVE 渠道全部排除。
  channels: async () => (await paged("/cms/adsense/channel/list", {})).map(row =>
    [row.channelId, `${row.channelId} · ${row.pubId}`]),
  placements: () => [["PLACEMENT_TIKTOK", "TikTok"], ["PLACEMENT_PANGLE", "Pangle"], ["PLACEMENT_GLOBAL_APP_BUNDLE", "全球应用组合"]],
  adTexts: async () => strings((await paged("/cms/ad/text/list")).flatMap(row => row.textList || [])),
};

const fieldSources = {
  articleId: "articles", lang: "languages", country: "countries", countryList: "countries", countryCode: "countries",
  site: "sites", siteList: "sites", am: "managers", adAccountList: "accounts", adAccount: "accounts", materialList: "materialList",
  placementList: "placements"
};

export function fieldSource(action, key) {
  if (action.id === "report-dimension" || action.id === "adsense-source-report") return null;
  if (key === "pubId") return action.id === "channel-new" ? "publishers" : null;
  if (key === "channelId") return action.id === "campaign-audit" ? "channels" : null;
  if (key === "accountId" && action.id.startsWith("tiktok-")) return "pixels";
  if (key === "advertiser" && action.id === "campaign-publish") return "accounts";
  if (key === "credentialAlias" && action.id === "campaign-publish") return "channelCredentials";
  if (key === "country" && action.id.startsWith("tiktok-")) return "tiktokCountries";
  if (key === "category") return action.id.startsWith("material-") ? "materialCategories" : "categories";
  if (action.id.startsWith("material-") && ["template", "dimension", "ratio"].includes(key)) return "material:" + key;
  if (key === "textList" && action.id.startsWith("tiktok-")) return "adTexts";
  return fieldSources[key];
}

export async function wireFormChoices(form, action) {
  const controls = [...form.querySelectorAll("[data-choice-source]")];
  const submit = form.querySelector('[type="submit"]');
  const directory = controls.some(control => ["accounts", "pixels", "tiktokCountries"].includes(control.dataset.choiceSource))
    ? request("/cms/console/campaign-choices") : Promise.resolve({});
  const loaded = new Map();
  let materials;
  submit.disabled = true;

  function platform() {
    return form.dataset.campaignPlatform || (action.id.startsWith("tiktok-") ? "TikTok" : form.elements.adPlatform?.value);
  }

  async function fill(select) {
    const source = select.dataset.choiceSource;
    if (!loaded.has(source)) {
      const choices = source === "channelCredentials"
        ? requestList("/cms/console/channel-credentials").then(rows => {
          const provider = { TikTok: "TIKTOK", Facebook: "META", Google: "GOOGLE", Taboola: "TABOOLA" }[platform()];
          return rows.filter(row => row.provider === provider).map(row => [row.alias, row.alias]);
        })
        : source === "materialList" || source.startsWith("material:")
        ? (materials ||= paged("/cms/ad/material/list")).then(rows => source === "materialList"
          ? rows.map(row => [row.adMaterialId, row.name, row]) : strings(rows.map(row => row[source.slice(9)])))
        : source === "tiktokCountries" ? Promise.all([directory, sources.countries()]).then(([data, countries]) => {
        const labels = new Map(countries);
        return data.tiktokCountries.map(code => [code, labels.get(code) || code]);
      }) : sources[source] ? sources[source]() : directory.then(data => data[source]);
      loaded.set(source, choices);
    }
    let rows = await loaded.get(source);
    if (!select.isConnected) return;
    if (source === "accounts") {
      const scoped = ["adAccount", "advertiser"].includes(select.name) || action.id.startsWith("tiktok-");
      rows = rows.filter(row => !scoped || (platform() && row.platform?.toLowerCase() === platform().toLowerCase()))
        .map(row => [select.dataset.numericChoice ? String(row.id) : row.accountId, `${row.name || row.accountId} · ${row.accountId}`]);
    }
    if (source === "pixels") rows = rows.map(row => [row.pixelCode || row.pixelId, `${row.pixelCode || row.pixelId} · ${row.pixelId}`]);
    if (source === "placements" && form.elements.isSmartCreative.value === "true") {
      rows = rows.filter(([id]) => id !== "PLACEMENT_GLOBAL_APP_BUNDLE");
    }
    const initial = JSON.parse(select.dataset.choiceValue || "null");
    const selected = select.dataset.initialized
      ? [...select.selectedOptions].map(option => option.value).filter(Boolean)
      : (Array.isArray(initial) ? initial : select.multiple && typeof initial === "string" ? initial.split(",") : [initial])
          .filter(value => value != null && value !== "").map(value => String(value?.adMaterialId ?? value));
    if (!select.dataset.initialized && source !== "sites") {
      for (const id of selected) {
        if (!rows.some(row => String(row[0]) === id)) {
          const saved = Array.isArray(initial) && initial.find(value => value?.adMaterialId === id);
          rows = [...rows, [id, saved?.name || `${id}（已保存）`, saved || undefined]];
        }
      }
    }
    setReportChoices(select, rows.filter(([id]) => id != null && id !== "").map(([id, label]) => [String(id), String(label || id)]), "请选择");
    for (const option of select.options) option.selected = selected.includes(option.value);
    if (!select.multiple && select.selectedIndex < 0) select.value = "";
    if (source === "channelCredentials" && rows.length === 1) select.value = rows[0][0];
    select.choiceRecords = new Map(rows.map(([id, , record]) => [String(id), record]));
    select.dataset.initialized = "true";
    select.disabled = source === "accounts" && select.name === "adAccount" && !platform();
  }

  const refreshDependents = () => {
    const taboola = platform() === "Taboola";
    for (const key of ["clientId", "clientSecret"]) {
      const input = form.elements[key];
      if (input) input.disabled = input.closest("label").hidden = !taboola;
    }
    for (const [key, choice, show] of [["bidPriceValue", "bidPriceType", "BID_TYPE_CUSTOM"],
      ["campaignBudget", "campaignBudgetModel", "BUDGET_MODE_INFINITE"], ["textCount", "isSmartCreative", "true"]]) {
      const input = form.elements[key];
      if (!input) continue;
      const selected = form.elements[choice].value;
      const visible = Boolean(selected) && (key === "campaignBudget" ? selected !== show : selected === show);
      input.disabled = input.closest("label").hidden = !visible;
    }
  };
  refreshDependents();
  form.addEventListener("change", event => {
    refreshDependents();
    if (event.target.name === "adPlatform") {
      form.querySelectorAll('select[name="adAccount"]').forEach(select => { select.value = ""; void fill(select); });
    }
    if (event.target.name === "isSmartCreative" && form.elements.placementList?.dataset.choiceSource) {
      void fill(form.elements.placementList);
    }
  });
  const fillWithin = element => Promise.all([...element.querySelectorAll("[data-choice-source]")].map(fill));
  await fillWithin(form);
  if (form.isConnected) {
    if (action.id === "campaign-publish" && form.elements.credentialAlias.options.length < 2) {
      throw new Error("请先在「渠道密钥」保存该平台的凭据");
    }
    submit.disabled = false;
  }
  return fillWithin;
}

export async function loadSiteFilter(form: any, selected: string = "", onError: (message: string) => void = () => {}) {
  try {
    const sites = await sources.sites();
    if (!form.isConnected) return;
    setReportChoices(form.elements.filter, sites, "全部站点");
    form.elements.filter.value = sites.some(([id]) => id === selected) ? selected : "";
  } catch (error) {
    if (form.isConnected) onError(error.message);
  }
}

export async function loadSiteSelect(form, onError, preferred = "") {
  const submit = form.querySelector('[type="submit"]');
  submit.disabled = true;
  try {
    const sites = await sources.sites();
    if (!form.isConnected) return;
    setReportChoices(form.elements.site, sites, "请选择站点");
    if (sites.some(([id]) => id === preferred)) form.elements.site.value = preferred;
    submit.disabled = !sites.length;
    if (!sites.length) onError("暂无可用站点");
  } catch (error) {
    if (form.isConnected) onError(error.message);
  }
}
