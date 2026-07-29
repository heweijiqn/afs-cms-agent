import { request, requestList } from "./api.js";
import { $, escape as e, n, numeric, percent, money, toneBadge, roasBadge, table, formValues, errorIn, toast, busy } from "./ui.js";
import { openCatalogSection } from "./catalog.js";

// ============================================================================
// 1. Copilot Expert Playbooks & Actionable Campaign Chips
// ============================================================================

export interface CopilotPlaybook {
  id: string;
  name: string;
  badge: string;
  instruction: string;
  samplePrompt: string;
}

export const COPILOT_PLAYBOOKS: CopilotPlaybook[] = [
  {
    id: "default",
    name: "全能投放助手",
    badge: "默认",
    instruction: "",
    samplePrompt: "总结全盘投放表现、各平台 ROAS 对比，以及盈利和亏损前 5 的活动。"
  },
  {
    id: "cold-start",
    name: "冷启动放量专家",
    badge: "放量",
    instruction: "【专家模式：冷启动放量专家】请严格遵循小步快跑原则（单次调幅≤15%~20%、不破坏学习期），优先筛出高潜放量活动并给出扩量节奏与护栏建议。",
    samplePrompt: "筛出目前最适合放量的前 5 个高 ROAS 活动，分别给出 3 天扩量节奏和单次加预算幅度。"
  },
  {
    id: "bleed-doctor",
    name: "亏损止血医生",
    badge: "止损",
    instruction: "【专家模式：亏损止血医生】请重点定位持续亏损、ROAS<1.0、消耗过快的出血活动，按止损优先级输出降预算/暂停/补齐规则的具体方案。",
    samplePrompt: "诊断目前亏损最严重的活动，按紧急程度排序并给出具体的止损降预算比例。"
  },
  {
    id: "creative-ctr",
    name: "素材 CTR 诊断师",
    badge: "创意",
    instruction: "【专家模式：素材 CTR 诊断师】请聚焦点击率衰退、频次过高与受众疲劳问题，给出分平台（Google/Meta/TikTok）的 Hook 改写与素材轮换方案。",
    samplePrompt: "哪些活动存在明显的转化或点击率衰退？针对这些活动给出 3 组新创意方向。"
  },
  {
    id: "budget-allocator",
    name: "跨平台预算分配官",
    badge: "预算",
    instruction: "【专家模式：跨平台预算分配官】请对比 Google、Facebook、TikTok 边际 ROI，给出跨平台预算再分配比例与预期净利提升测算。",
    samplePrompt: "对比 Google、Facebook、TikTok 的整体投入产出，给出下周跨平台预算再分配建议。"
  },
  {
    id: "cro-advisor",
    name: "落地页 CRO 顾问",
    badge: "转化",
    instruction: "【专家模式：落地页 CRO 顾问】请重点排查高点击低转化（CTR 正常但 CVR 偏低）的活动，从首屏匹配度、加载速度与转化路径给出优化清单。",
    samplePrompt: "找出点击不低但变现转化偏弱的活动，分析可能的落地页承接问题和改进措施。"
  }
];

export function extractMentionedCampaignIds(text: string): string[] {
  if (!text) return [];
  const matches = text.match(/\b(?:120\d{12,}|245\d{7,}|401\d{5,}|ars_[a-zA-Z0-9_]+|cmp_[a-zA-Z0-9_]+)\b/g) || [];
  return [...new Set(matches)].slice(0, 4);
}

// ============================================================================
// 2. Agent Toolbox (Patrol & Daily Brief | CSV Analyzer | Creative Lab)
// ============================================================================

let activeTool: "patrol" | "csv" | "creative" = "patrol";

const SAMPLE_CSV = `Campaign Name,Amount Spent,Conversion Value,Impressions,Clicks,Conversions,Frequency
FB_US_DentalImplants_Scale,420.50,685.20,38400,1180,142,2.1
FB_CA_HearingAids_CBO,310.00,228.40,42100,360,24,4.2
FB_AU_LasikVision_ABO,195.80,312.60,18500,590,68,1.8
FB_GB_ClearAligners_Test,165.40,118.20,26300,210,16,3.8
FB_DE_SeniorCare_Retarget,280.00,435.90,22400,820,95,2.4`;

export async function renderAgentToolbox(container: HTMLElement, onDiagnose: (cid: string, btn: HTMLElement) => void) {
  container.innerHTML = `
    <div class="agent-tabs" id="toolbox-subtabs">
      <button type="button" class="tab-btn" aria-pressed="${activeTool === "patrol"}" data-tool="patrol">定时巡检与每日战报</button>
      <button type="button" class="tab-btn" aria-pressed="${activeTool === "csv"}" data-tool="csv">外部 CSV 报表诊断</button>
      <button type="button" class="tab-btn" aria-pressed="${activeTool === "creative"}" data-tool="creative">创意工坊与素材疲劳</button>
    </div>
    <div id="toolbox-body"><div class="placeholder">正在加载…</div></div>
  `;

  container.querySelectorAll("[data-tool]").forEach((btn: HTMLButtonElement) => {
    btn.onclick = () => {
      activeTool = (btn.dataset.tool as any) || "patrol";
      renderAgentToolbox(container, onDiagnose);
    };
  });

  const body = $("#toolbox-body", container) as HTMLElement;
  if (activeTool === "patrol") await renderPatrolPanel(body);
  else if (activeTool === "csv") renderCsvAnalyzerPanel(body);
  else await renderCreativeLabPanel(body, onDiagnose);
}

async function renderPatrolPanel(target: HTMLElement) {
  try {
    const [cfg, briefs] = await Promise.all([
      request("/cms/agent/ext/patrol"),
      requestList("/cms/agent/ext/briefs")
    ]);
    if (!target.isConnected) return;

    target.innerHTML = `
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>无人值守定时巡检与每日经营战报</h2>
            <p class="sub">自动按周期扫描异常活动、汇总每日战报，并支持真实推送至飞书 / 钉钉 / 企业微信群机器人。</p>
          </div>
          <div class="action-set">
            <button type="button" class="button secondary" id="btn-gen-brief">生成站内战报</button>
            <button type="button" class="button" id="btn-gen-push-brief">生成并推送到群机器人</button>
          </div>
        </div>
        <form id="patrol-config-form" class="form-grid">
          <label>定时巡检开关
            <select name="enabled">
              <option value="true" ${cfg.enabled ? "selected" : ""}>已开启（后台自动巡检）</option>
              <option value="false" ${!cfg.enabled ? "selected" : ""}>已暂停</option>
            </select>
          </label>
          <label>巡检间隔（分钟）
            <input type="number" name="intervalMinutes" min="15" max="1440" value="${e(cfg.intervalMinutes ?? 60)}">
          </label>
          <label>每日战报推送时段（0-23点）
            <input type="number" name="dailyBriefHour" min="0" max="23" value="${e(cfg.dailyBriefHour ?? 9)}">
          </label>
          <label>推送通道
            <select name="channel">
              <option value="FEISHU" ${cfg.channel === "FEISHU" ? "selected" : ""}>飞书机器人</option>
              <option value="DINGTALK" ${cfg.channel === "DINGTALK" ? "selected" : ""}>钉钉机器人</option>
              <option value="WECOM" ${cfg.channel === "WECOM" ? "selected" : ""}>企业微信</option>
              <option value="WEBHOOK" ${cfg.channel === "WEBHOOK" ? "selected" : ""}>自定义 Webhook</option>
            </select>
          </label>
          <label class="all">群机器人 Webhook URL（填入真实地址即可直接推送到群聊）
            <input name="webhookUrl" value="${e(cfg.webhookUrl || "")}" placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxx">
          </label>
          <div class="form-actions all">
            <button type="submit" class="button secondary">保存巡检与推送配置</button>
            <span class="muted">上次巡检：${e(cfg.lastRunAt ? String(cfg.lastRunAt).slice(0, 19).replace("T", " ") : "尚未执行")}</span>
          </div>
        </form>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div><h2>历史经营战报</h2><p class="sub">自动汇总全盘花费、收益、防火墙状态、明星放量榜与急需止损榜。</p></div>
        </div>
        <div id="brief-list-wrap">
          ${briefs.length ? briefs.map((b: any) => `
            <article class="brief-card">
              <div class="brief-card-head">
                <div>
                  <strong>${e(b.title)}</strong>
                  <span class="muted"> · ${e(String(b.createdAt || "").slice(0, 19).replace("T", " "))}</span>
                </div>
                <div class="action-set">
                  ${toneBadge(b.channel || "INBOX", "gray")}
                  ${toneBadge(b.deliveryStatus || "GENERATED", b.deliveryStatus === "DELIVERED" ? "teal" : b.deliveryStatus === "GENERATED" ? "gray" : "amber")}
                </div>
              </div>
              <pre class="brief-pre">${e(b.summaryMarkdown || "")}</pre>
              ${b.deliveryDetail ? `<div class="muted brief-foot">回执：${e(b.deliveryDetail)}</div>` : ""}
            </article>`).join("") : '<div class="placeholder">暂无经营战报，点击右上角「生成站内战报」立即汇总当前全盘数据。</div>'}
        </div>
      </section>
    `;

    const form = $("#patrol-config-form", target) as HTMLFormElement;
    form.onsubmit = (ev) => {
      ev.preventDefault();
      const v = formValues(form);
      busy($('button[type="submit"]', form), async () => {
        await request("/cms/agent/ext/patrol", {
          body: {
            enabled: v.enabled === "true",
            intervalMinutes: Number(v.intervalMinutes || 60),
            dailyBriefHour: Number(v.dailyBriefHour || 9),
            channel: v.channel,
            webhookUrl: v.webhookUrl,
            autoPropose: false
          }
        });
        toast("定时巡检与推送配置已保存");
        await renderPatrolPanel(target);
      });
    };

    const runBrief = (push: boolean, btn: HTMLButtonElement) => busy(btn, async () => {
      const res = await request("/cms/agent/ext/brief/generate", { body: { pushWebhook: push } });
      toast(res.deliveryDetail || "经营战报已生成");
      await renderPatrolPanel(target);
    });

    $("#btn-gen-brief", target).onclick = (ev) => runBrief(false, ev.currentTarget as HTMLButtonElement);
    $("#btn-gen-push-brief", target).onclick = (ev) => runBrief(true, ev.currentTarget as HTMLButtonElement);
  } catch (err: any) {
    if (target.isConnected) errorIn(target, err?.message || "加载定时巡检失败");
  }
}

function renderCsvAnalyzerPanel(target: HTMLElement) {
  target.innerHTML = `
    <section class="panel">
      <div class="panel-head">
        <div>
          <h2>外部 CSV 报表一键诊断</h2>
          <p class="sub">上传或粘贴 Facebook Ads / Google Ads / TikTok 导出的 CSV 报表，自动识别字段并输出放量明星、出血止损与素材疲劳诊断。</p>
        </div>
        <button type="button" class="button secondary" id="btn-load-sample-csv">载入示例 CSV</button>
      </div>
      <form id="csv-analyze-form" class="form-grid">
        <label>数据来源平台
          <select name="platform">
            <option value="Auto">自动识别</option>
            <option value="Facebook">Facebook / Meta Ads</option>
            <option value="Google">Google Ads</option>
            <option value="TikTok">TikTok Ads</option>
          </select>
        </label>
        <label>上传 CSV 文件
          <input type="file" id="csv-file-input" accept=".csv,text/csv">
        </label>
        <label class="all">CSV 内容（支持中英文表头：Campaign / Spend / Revenue / Impressions / Clicks / Conversions / Frequency）
          <textarea name="csvText" rows="7" placeholder="粘贴 CSV 文本，或点击上方选择文件 / 载入示例 CSV…"></textarea>
        </label>
        <div class="form-actions all">
          <button type="submit" class="button">开始智能诊断</button>
        </div>
      </form>
      <div id="csv-result-wrap"></div>
    </section>
  `;

  const form = $("#csv-analyze-form", target) as HTMLFormElement;
  const textarea = form.elements.namedItem("csvText") as HTMLTextAreaElement;
  const fileInput = $("#csv-file-input", target) as HTMLInputElement;
  const resWrap = $("#csv-result-wrap", target) as HTMLElement;

  $("#btn-load-sample-csv", target).onclick = () => {
    textarea.value = SAMPLE_CSV;
    toast("已载入 Facebook 导出示例 CSV，点击「开始智能诊断」查看结果");
  };

  fileInput.onchange = async () => {
    const file = fileInput.files?.[0];
    if (!file) return;
    textarea.value = await file.text();
    toast(`已读取文件：${file.name}`);
  };

  form.onsubmit = (ev) => {
    ev.preventDefault();
    const v = formValues(form);
    busy($('button[type="submit"]', form), async () => {
      const data = await request("/cms/agent/ext/csv-analyze", {
        body: { csvText: textarea.value, platform: v.platform }
      });
      const s = data.summary || {};
      const verdictMap: Record<string, [string, string]> = {
        SCALE: ["建议放量", "teal"],
        PAUSE: ["急需止损", "red"],
        REFRESH: ["素材疲劳", "amber"],
        HOLD: ["平稳观察", "gray"]
      };
      resWrap.innerHTML = `
        <div class="funnel-kpi-strip csv-kpi-strip">
          <div><span class="muted">解析活动数</span><strong class="mono">${numeric(s.rowCount)}</strong></div>
          <div><span class="muted">总花费</span><strong class="mono">${money(s.totalSpend)}</strong></div>
          <div><span class="muted">总收入</span><strong class="mono">${money(s.totalRevenue)}</strong></div>
          <div><span class="muted">综合 ROAS</span>${roasBadge(s.roas)}</div>
          <div><span class="muted">放量 / 止损 / 疲劳</span><strong>${toneBadge(`${s.winnerCount} 放量`, "teal")} ${toneBadge(`${s.bleederCount} 止损`, "red")} ${toneBadge(`${s.fatigueCount} 疲劳`, "amber")}</strong></div>
        </div>
        ${data.aiDiagnosis ? `<section class="memory-lessons">
          <h3>✦ AI 报表深度诊断总结 ${data.aiPowered ? toneBadge("AI 实时生成", "teal") : ""}</h3>
          <pre class="brief-pre">${e(data.aiDiagnosis)}</pre>
        </section>` : ""}
        ${table(
          ["活动名称", "花费", "收入", "利润", "ROAS", "CTR / CVR", "频次", "诊断结论", "可执行建议"],
          (data.rows || []).map((r: any) => {
            const [vl, vt] = verdictMap[r.verdict] || [r.verdict, "gray"];
            return [
              `<strong>${e(r.name)}</strong>`,
              `<span class="mono">${money(r.spend)}</span>`,
              `<span class="mono">${money(r.revenue)}</span>`,
              `<span class="mono ${n(r.profit) >= 0 ? "profit-pos" : "profit-neg"}">${n(r.profit) >= 0 ? "+" : ""}${money(r.profit)}</span>`,
              roasBadge(r.roas),
              `<span class="mono">${percent(r.ctr)} / ${percent(r.cvr)}</span>`,
              `<span class="mono">${r.frequency ? Number(r.frequency).toFixed(1) : "—"}</span>`,
              toneBadge(vl, vt),
              `<span class="muted">${e(r.suggestion)}</span>`
            ];
          }),
          "未解析到有效活动行"
        )}
      `;
    }, msg => errorIn(resWrap, msg));
  };
}

async function renderCreativeLabPanel(target: HTMLElement, onDiagnose: (cid: string, btn: HTMLElement) => void) {
  try {
    const [fatigued, drafts, abTests] = await Promise.all([
      requestList("/cms/agent/ext/creatives/fatigue"),
      requestList("/cms/agent/ext/creatives/drafts"),
      requestList("/cms/agent/ext/creatives/ab-tests")
    ]);
    if (!target.isConnected) return;

    target.innerHTML = `
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>素材疲劳监测与创意工坊</h2>
            <p class="sub">自动识别转化衰退的疲劳活动，按平台一键生成 Google 搜索文案 / Meta 信息流 / TikTok 短视频分镜脚本并入库测试。</p>
          </div>
        </div>
        <form id="creative-gen-form" class="form-grid">
          <label>目标活动 / 产品主题
            <input name="campaignName" required placeholder="例如：US Dental Implants 高性价比方案">
          </label>
          <label>投放平台模板
            <select name="platform">
              <option value="Google">Google 搜索与展示文案</option>
              <option value="Facebook">Facebook / Meta 信息流</option>
              <option value="TikTok">TikTok 15秒短视频分镜脚本</option>
            </select>
          </label>
          <label>创意切入角度
            <select name="style">
              <option value="PAIN_POINT">痛点反转</option>
              <option value="DATA_PROOF">数据证言</option>
              <option value="URGENCY">限时优惠</option>
            </select>
          </label>
          <div class="form-actions">
            <button type="submit" class="button">生成 3 组新创意</button>
          </div>
        </form>
      </section>

      <section class="panel">
        <div class="panel-head"><div><h3>待换新疲劳活动（自动检测）</h3></div></div>
        ${table(
          ["活动名称", "平台", "花费", "ROAS", "疲劳指数", "诊断说明", "操作"],
          fatigued.map((f: any) => [
            `<strong>${e(f.name)}</strong><br><small class="muted mono">${e(f.campaignId)}</small>`,
            e(f.platform || "—"),
            `<span class="mono">${money(f.spend)}</span>`,
            roasBadge(f.roas),
            toneBadge(`${f.fatigueScore} / 100`, f.fatigueScore >= 75 ? "red" : "amber"),
            `<span class="muted">${e(f.reason)}</span>`,
            `<div class="record-actions">
              <button type="button" class="table-link" data-gen-for="${e(f.campaignId)}" data-gen-name="${e(f.name)}" data-gen-plat="${e(f.platform || "Google")}">一键生成创意</button>
              <button type="button" class="table-link" data-fat-diag="${e(f.campaignId)}">诊断</button>
            </div>`
          ]),
          "当前所有在投活动创意健康度良好"
        )}
      </section>

      <section class="panel" id="creative-ab-panel">
        <div class="panel-head">
          <div>
            <h3>A/B 测试实时对比看板（共 ${abTests.length} 组）</h3>
            <p class="sub">判定完成标准：累计曝光达到目标样本（20,000 次）且双样本统计置信度 ≥ 90% 时自动标记为「实验已完成 · 待确认结案」；点击「采纳胜出并入库」或「终止实验」后正式结案归档。</p>
          </div>
          <div class="action-set">
            ${toneBadge(`待结案 ${abTests.filter((x: any) => x.lifecycleState === "READY_TO_CONCLUDE").length}`, "teal")}
            ${toneBadge(`采集中 ${abTests.filter((x: any) => x.lifecycleState === "RUNNING").length}`, "amber")}
            ${toneBadge(`已结束 ${abTests.filter((x: any) => String(x.lifecycleState || "").startsWith("ENDED_")).length}`, "gray")}
          </div>
        </div>
        ${table(
          ["实验编号 / 创意标题", "实验状态", "样本收集进度 (A / B)", "CTR 对比 (A → B)", "ROAS 对比 (A → B)", "统计置信度 / 结论", "操作"],
          abTests.map((exp: any) => {
            const st = exp.lifecycleState;
            const stTone = st === "READY_TO_CONCLUDE" || st === "ENDED_ADOPTED" ? "teal" : st === "RUNNING" ? "amber" : "gray";
            const actionsHtml = st === "ENDED_ADOPTED"
              ? `<div class="record-actions"><button type="button" class="table-link" data-open-adtext>查看已入库文案 →</button></div>`
              : st === "ENDED_STOPPED"
                ? `<div class="record-actions"><button type="button" class="table-link" data-draft-st="${exp.draftId}" data-st="AB_TESTING">重新发起实验</button></div>`
                : `<div class="record-actions">
                    <button type="button" class="table-link" data-draft-st="${exp.draftId}" data-st="AB_ADOPTED">采纳胜出并入库</button>
                    <button type="button" class="table-link" data-draft-st="${exp.draftId}" data-st="AB_STOPPED">终止实验</button>
                  </div>`;
            return [
              `<strong>${e(exp.experimentCode)} · ${e(exp.variantHeadline)}</strong><br><small class="muted">${e(exp.platform)} · 分流 ${e(exp.trafficSplit)} · 主题：${e(exp.campaignName)}</small>`,
              toneBadge(exp.lifecycleLabel || "运行中", stTone),
              `<div class="pacing-cell"><progress class="pacing-meter tone-${exp.sampleProgressPct >= 100 ? "teal" : "amber"}" max="100" value="${exp.sampleProgressPct ?? 100}"></progress><span class="mono">${exp.sampleProgressPct ?? 100}%</span></div><small class="muted mono">${numeric(exp.controlImpressions)} / ${numeric(exp.variantImpressions)}（目标 ${numeric(exp.targetSample || 20000)}）</small>`,
              `<span class="mono">${percent(exp.controlCtr)} → <strong>${percent(exp.variantCtr)}</strong></span> ${toneBadge(`+${exp.ctrLiftPct}%`, "teal")}`,
              `<span class="mono">${e(exp.controlRoas)}× → <strong>${e(exp.variantRoas)}×</strong></span> ${toneBadge(`+${exp.roasLiftPct}%`, "teal")}`,
              `${toneBadge(`置信度 ${exp.confidencePct}%`, exp.verdict === "VARIANT_WINNING" ? "teal" : "amber")}<br><small class="muted">${e(exp.verdictLabel)}</small>`,
              actionsHtml
            ];
          }),
          "当前暂无 A/B 测试记录。在下方创意草稿箱点击「发起 A/B 测试」即可启动 50%/50% 分流对照实验。"
        )}
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h3>创意草稿箱（共 ${drafts.length} 条）</h3>
            <p class="sub">点击「入库发布」将同步至「内容管理 → 广告文案」；点击「发起 A/B 测试」将进入上方 A/B 实验看板进行分流对比。</p>
          </div>
          <button type="button" class="button secondary small" id="btn-go-adtext">查看已入库广告文案 →</button>
        </div>
        ${drafts.length ? `<div class="creative-draft-grid">${drafts.map((d: any) => {
          const isPublished = d.status === "PUBLISHED" || d.status === "AB_ADOPTED";
          const stTone = isPublished ? "teal" : d.status === "AB_TESTING" ? "amber" : "gray";
          const stLabel = d.status === "AB_ADOPTED" ? "A/B 胜出已入库" : d.status === "PUBLISHED" ? "已入库发布" : d.status === "AB_TESTING" ? "A/B 测试中" : d.status === "AB_STOPPED" ? "实验已终止" : "草稿";
          return `<article class="creative-draft-card">
            <div class="brief-card-head">
              <strong>${e(d.headline)}</strong>
              <div>${toneBadge(d.platform, "gray")} ${toneBadge(stLabel, stTone)}</div>
            </div>
            <div class="muted"><strong>Hook：</strong>${e(d.hookText || "—")}</div>
            <pre class="brief-pre">${e(d.bodyText || "")}</pre>
            <div class="brief-card-head">
              <span class="muted">CTA：<strong>${e(d.ctaText || "—")}</strong></span>
              <div class="record-actions">
                ${isPublished
                  ? `<button type="button" class="table-link" data-open-adtext>查看广告文案库 →</button>`
                  : `<button type="button" class="table-link" data-draft-st="${d.id}" data-st="PUBLISHED">入库发布</button>`}
                ${d.status === "AB_TESTING" || d.status === "AB_ADOPTED" || d.status === "AB_STOPPED"
                  ? `<button type="button" class="table-link" data-scroll-ab>查看实验详情 ↑</button>`
                  : `<button type="button" class="table-link" data-draft-st="${d.id}" data-st="AB_TESTING">发起 A/B 测试</button>`}
              </div>
            </div>
          </article>`;
        }).join("")}</div>` : '<div class="placeholder">暂无创意草稿，点击上方「生成 3 组新创意」或在疲劳活动列表点击「一键生成创意」。</div>'}
      </section>
    `;

    const form = $("#creative-gen-form", target) as HTMLFormElement;
    form.onsubmit = (ev) => {
      ev.preventDefault();
      const v = formValues(form);
      busy($('button[type="submit"]', form), async () => {
        const res = await request("/cms/agent/ext/creatives/generate", {
          body: { campaignId: "", campaignName: v.campaignName, platform: v.platform, style: v.style }
        });
        toast(res.aiPowered
          ? `✦ AI 大模型已围绕「${v.campaignName}」实时生成 ${res.generated} 组专属创意`
          : `已为 ${res.platform} 生成 ${res.generated} 组结构化创意`);
        await renderCreativeLabPanel(target, onDiagnose);
      });
    };

    target.querySelectorAll("[data-gen-for]").forEach((btn: HTMLButtonElement) => {
      btn.onclick = () => busy(btn, async () => {
        const res = await request("/cms/agent/ext/creatives/generate", {
          body: {
            campaignId: btn.dataset.genFor,
            campaignName: btn.dataset.genName,
            platform: btn.dataset.genPlat || "Google",
            style: "PAIN_POINT"
          }
        });
        toast(`已为 ${btn.dataset.genName} 生成 ${res.generated} 组新创意并入库草稿箱`);
        await renderCreativeLabPanel(target, onDiagnose);
      });
    });

    target.querySelectorAll("[data-fat-diag]").forEach((btn: HTMLButtonElement) => {
      btn.onclick = () => onDiagnose(btn.dataset.fatDiag || "", btn);
    });

    target.querySelectorAll("[data-draft-st]").forEach((btn: HTMLButtonElement) => {
      btn.onclick = () => busy(btn, async () => {
        const nextSt = btn.dataset.st;
        await request(`/cms/agent/ext/creatives/drafts/${btn.dataset.draftSt}/status`, {
          body: { status: nextSt }
        });
        toast(nextSt === "AB_TESTING"
          ? "已启动 50%/50% 分流 A/B 实验，可在上方实验看板查看进度与结论"
          : nextSt === "AB_STOPPED"
            ? "实验已结束（已手动终止）"
            : nextSt === "AB_ADOPTED"
              ? "实验已结案：胜出方案已同步至「内容管理 → 广告文案」"
              : "已入库至「内容管理 → 广告文案」");
        await renderCreativeLabPanel(target, onDiagnose);
        if (nextSt === "AB_TESTING") {
          $("#creative-ab-panel", target)?.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      });
    });

    target.querySelectorAll("[data-scroll-ab]").forEach((btn: HTMLButtonElement) => {
      btn.onclick = () => $("#creative-ab-panel", target)?.scrollIntoView({ behavior: "smooth", block: "center" });
    });

    const goAdTextBtn = $("#btn-go-adtext", target);
    if (goAdTextBtn) goAdTextBtn.onclick = () => openCatalogSection("content", "copy");
    target.querySelectorAll("[data-open-adtext]").forEach((btn: HTMLButtonElement) => {
      btn.onclick = () => openCatalogSection("content", "copy");
    });
  } catch (err: any) {
    if (target.isConnected) errorIn(target, err?.message || "加载创意工坊失败");
  }
}

// ============================================================================
// 3. Post-Execution Attribution & Episodic Decision Memory (Stats Tab)
// ============================================================================

export async function renderAttributionSection(container: HTMLElement) {
  try {
    const data = await request("/cms/agent/ext/attribution");
    if (!container.isConnected) return;
    const s = data.summary || {};
    const records = data.records || [];
    const lessons = data.lessons || [];

    container.innerHTML = `
      <section class="panel">
        <div class="panel-head">
          <div>
            <h2>执行后效果复盘与决策记忆</h2>
            <p class="sub">自动追踪已执行预算调整的前后 ROAS 变化与预测命中率，沉淀可复用的投放决策经验。</p>
          </div>
          ${toneBadge(`预测命中率 ${s.hitRatePct ?? 0}%`, "teal")}
        </div>
        <div class="funnel-kpi-strip">
          <div><span class="muted">已评估执行笔数</span><strong class="mono">${numeric(s.executedCount)}</strong></div>
          <div><span class="muted">决策预测命中率</span><strong class="mono">${e(s.hitRatePct ?? 0)}%</strong></div>
          <div><span class="muted">预估 3 日净利增量</span><strong class="mono ${n(s.estimatedProfitLift) >= 0 ? "profit-pos" : "profit-neg"}">${n(s.estimatedProfitLift) >= 0 ? "+" : ""}${money(s.estimatedProfitLift)}</strong></div>
          <div><span class="muted">已沉淀决策记忆</span><strong class="mono">${numeric(s.memoryLessonCount)} 条</strong></div>
        </div>
        ${table(
          ["执行时间", "活动", "动作", "预算变动", "执行前 → 后 ROAS", "复盘判定"],
          records.map((r: any) => [
            `<span class="mono muted">${e(String(r.executedAt || "").slice(0, 19).replace("T", " "))}</span>`,
            `<strong>${e(r.campaignName || r.campaignId)}</strong>`,
            toneBadge(r.actionType === "ROLLBACK" ? "已回滚" : (r.action || r.actionType), r.actionType === "ROLLBACK" ? "amber" : "gray"),
            `<span class="mono">${money(r.beforeBudget)} → ${money(r.afterBudget)}</span>`,
            `<span class="mono">${e(r.baselineRoas)}× → ${e(r.postRoas)}× (${n(r.roasDelta) >= 0 ? "+" : ""}${e(r.roasDelta)})</span>`,
            r.hit ? toneBadge("命中有效", "teal") : toneBadge("待复核", "amber")
          ]),
          "暂无已执行提案的复盘记录（在「分析与审批」批准并执行提案后自动记录）。"
        )}
        <div class="memory-lessons">
          <h3>智能体沉淀的决策记忆（Episodic Memory）</h3>
          <ul>${lessons.map((l: string) => `<li>${e(l)}</li>`).join("")}</ul>
        </div>
      </section>
    `;
  } catch { /* non-blocking */ }
}
