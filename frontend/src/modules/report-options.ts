import { escape as e } from "./ui.js";

export function setReportChoices(select: any, rows: any[], placeholder: string = "不限") {
  const selected = new Set([...select.selectedOptions].map((option: any) => option.value));
  const values = new Map<string, string>(rows.filter(([id]) => id).map(([id, label]) => [String(id), String(label || id)]));
  select.innerHTML = (select.multiple ? "" : `<option value="">${e(placeholder)}</option>`) +
    [...values].sort((a, b) => a[1].localeCompare(b[1])).map(([id, label]) =>
      `<option value="${e(id)}"${selected.has(id) ? " selected" : ""}>${e(label)}</option>`).join("");
}

export function campaignChoices(rows) {
  return rows.map(row => {
    const label = row.name || "未命名活动";
    const ids = row.platformCampaignIds || [];
    const campaignId = ids.length ? ids.join(" / ") : row.localCampaignId;
    return [row.id, campaignId ? `${label} · ${campaignId}` : label];
  });
}

export function wireReportChoices(form: any, options: any, publisher: any, campaign: any, update?: any) {
  const pub = form.elements.pubId;
  setReportChoices(pub, options.publishers.map(id => [id, id]), "请选择发布商");
  pub.value = options.publishers.includes(publisher) ? publisher : options.publishers[0] || "";
  const campaigns = () => {
    const rows = options.campaigns.filter(row => row.pubId === pub.value);
    setReportChoices(campaign, campaignChoices(rows), "全部活动");
    update?.(rows);
  };
  pub.addEventListener("change", campaigns);
  campaigns();
}
