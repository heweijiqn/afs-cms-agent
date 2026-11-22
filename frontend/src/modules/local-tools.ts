import { request, requestList } from "./api.js";
import { $, escape as e, busy, errorIn, formValues, pageHead, table, toast, toneBadge, dateTime } from "./ui.js";

const providerLabels = {
  OPENAI: "AI 服务平台",
  ALIYUN_OSS: "阿里云 OSS",
  TIKTOK: "TikTok Ads",
  GOOGLE: "Google Ads",
  META: "Meta Ads",
  TABOOLA: "Taboola"
};

export async function channelVault(target, context, current) {
  target.innerHTML = pageHead("渠道密钥", "加密管理广告平台与 AI 接口凭据，支持连通性检测与平台投放对接。");
  if (context.mode !== "simulation" && !context.localCms) {
    target.insertAdjacentHTML("beforeend", '<div class="placeholder">请在授权管理环境中配置渠道凭据。</div>');
    return;
  }
  if (context.localCms) target.insertAdjacentHTML("beforeend", `<section class="panel">
    <h2>AI 服务配置</h2>
    <p class="sub">配置聊天模型用于智能分析与副驾驶对话，可选填备选模型提升稳定性；图片模型用于广告素材生图。已保存配置后，修改模型无需重复填写 API Key。</p>
    <form id="ai-platform-form" class="form-grid">
      <label>API Base URL<input name="baseUrl" required maxlength="2048" placeholder="https://api.openai.com/v1"></label>
      <label>API Key<input name="apiKey" type="password" required maxlength="2048" autocomplete="new-password" placeholder="输入接口密钥"></label>
      <label>主聊天模型<input name="chatModel" maxlength="200" placeholder="例如 deepseek-chat / gpt-4o"></label>
      <label>备选模型（可选）<input name="fallbackChatModel" maxlength="200" placeholder="主模型异常时自动切换"></label>
      <label>随机度 Temperature（可选）<input name="temperature" type="number" min="0" max="2" step="0.1" placeholder="0.2"></label>
      <label>图片模型（可选）<input name="imageModel" maxlength="200" placeholder="例如 gpt-image-1"></label>
      <div><button type="submit" class="button">保存配置</button></div>
    </form><div id="ai-platform-error"></div></section>
    <section class="panel">
    <h2>阿里云 OSS 存储配置</h2>
    <p class="sub">配置阿里云 OSS 后，素材与文件上传将自动使用 OSS 存储并立即生效（无需重启）；未配置时本地模式默认保存至本机目录。已保存后修改域名或 Bucket 无需重复填写密钥。</p>
    <form id="oss-platform-form" class="form-grid">
      <label>OSS Endpoint<input name="endpoint" required maxlength="2048" placeholder="https://oss-cn-hangzhou.aliyuncs.com"></label>
      <label>Bucket 名称<input name="bucket" required maxlength="200" placeholder="例如 afs-media-prod"></label>
      <label>AccessKey ID<input name="accessKeyId" type="password" required maxlength="512" autocomplete="new-password" placeholder="输入 AccessKey ID"></label>
      <label>AccessKey Secret<input name="accessKeySecret" type="password" required maxlength="512" autocomplete="new-password" placeholder="输入 AccessKey Secret"></label>
      <label class="all">公网访问域名（可选）<input name="publicBaseUrl" maxlength="2048" placeholder="留空则默认使用 https://{bucket}.{endpoint}，也可填写自定义 CDN 域名"></label>
      <div><button type="submit" class="button">保存 OSS 配置</button></div>
    </form><div id="oss-platform-error"></div></section>`);
  target.insertAdjacentHTML("beforeend", `<section class="panel">
    <h2>广告平台凭据</h2>
    <p class="sub">凭据采用 AES-256-GCM 加密存储于安全密钥库，保存后可通过下方列表验证接口连通性。</p>
    <form id="channel-form" class="form-grid">
    <label>广告平台<select name="provider"><option value="TIKTOK">TikTok Ads</option><option value="GOOGLE">Google Ads</option><option value="META">Meta Ads</option><option value="TABOOLA">Taboola</option></select></label>
    <label>账号别名<input name="alias" required maxlength="80" placeholder="用于区分不同广告账号"></label>
    <label class="all">密钥或凭据 JSON<textarea name="secret" required rows="4" maxlength="24000" autocomplete="off" spellcheck="false"></textarea></label>
    <p class="sub all" data-google-credential hidden>Google Ads 请保存 JSON，包含 accessToken、developerToken，使用经理账号时另填 loginCustomerId。</p>
    <div><button type="submit" class="button">保存密钥</button></div></form><div id="channel-error"></div></section>
    <section class="panel"><h2>已保存的凭据</h2><div id="channel-results"></div></section>
    <div id="channel-probes" aria-live="polite"></div>`);
  const output = $("#channel-results", target);
  const failure = $("#channel-error", target);
  const form = $("#channel-form", target);
  form.elements.provider.onchange = () => {
    $("[data-google-credential]", form).hidden = form.elements.provider.value !== "GOOGLE";
  };
  const probes = $("#channel-probes", target);
  async function load() {
    try {
      const rows = await requestList("/cms/console/channel-credentials");
      if (!current()) return;
      output.innerHTML = table(["平台", "别名", "状态", "更新时间", ...(context.localCms ? ["操作"] : [])], rows.map((row, index) => [
        `<strong>${e(providerLabels[row.provider] || row.provider)}</strong>`,
        `<code>${e(row.alias)}</code>`,
        toneBadge("已加密保存", "teal"),
        e(dateTime(row.updatedAt)),
        ...(context.localCms ? [`<button type="button" class="button secondary small" data-channel-check="${index}">验证连接</button>`] : [])
      ]), "还没有保存渠道密钥");
      output.querySelectorAll("[data-channel-check]").forEach(button => {
        const { provider, alias } = rows[Number(button.dataset.channelCheck)];
        button.onclick = () => busy(button, async () => {
          const body = { provider, alias };
          const result = await request("/cms/console/channel-credentials/check", { body });
          if (!current()) return;
          const id = button.dataset.channelCheck;
          const ok = result.httpStatus >= 200 && result.httpStatus < 300;
          const card = `<section class="panel" data-probe-result="${id}"><div class="panel-head"><h2>${e(providerLabels[provider] || provider)} · ${e(alias)}</h2>
            ${toneBadge(result.httpStatus ? `HTTP ${result.httpStatus}` : "连接失败", ok ? "teal" : "red")}</div>
            <pre class="probe-body"></pre></section>`;
          probes.querySelector(`[data-probe-result="${id}"]`)?.remove();
          probes.insertAdjacentHTML("beforeend", card);
          $(".probe-body", probes.lastElementChild).textContent = result.body;
        }, message => errorIn(failure, message));
      });
    } catch (error) {
      if (current()) errorIn(output, error.message);
    }
  }
  form.onsubmit = event => {
    event.preventDefault();
    busy($("button", form), async () => {
      failure.replaceChildren();
      const values: Record<string, any> = formValues(form);
      values.secret = new FormData(form).get("secret");
      await request("/cms/console/channel-credentials", { body: values });
      $("textarea", form).value = "";
      toast("凭据已加密保存");
      await load();
    }, message => errorIn(failure, message));
  };
  const aiForm = $("#ai-platform-form", target);
  async function loadAiConfiguration() {
    if (!aiForm) return;
    try {
      const saved = await request("/cms/console/channel-credentials/ai");
      if (!current() || !saved.configured) return;
      for (const key of ["baseUrl", "chatModel", "fallbackChatModel", "imageModel"]) {
        if (aiForm.elements[key]) aiForm.elements[key].value = saved[key] ?? "";
      }
      if (aiForm.elements.temperature && saved.temperature != null) {
        aiForm.elements.temperature.value = String(saved.temperature);
      }
      // 已保存过密钥时允许留空直接更新其他配置项，后端会自动沿用原加密密钥。
      aiForm.elements.apiKey.required = false;
      aiForm.elements.apiKey.placeholder = "已保存（留空则沿用原密钥，填写则替换）";
    } catch (error) {
      if (current()) errorIn($("#ai-platform-error", target), error.message);
    }
  }
  if (aiForm) aiForm.onsubmit = event => {
    event.preventDefault();
    const aiFailure = $("#ai-platform-error", target);
    busy($("button", aiForm), async () => {
      aiFailure.replaceChildren();
      const settings = Object.fromEntries(new FormData(aiForm));
      await request("/cms/console/channel-credentials", {
        body: { provider: "OPENAI", alias: "openai-ai", secret: JSON.stringify(settings) }
      });
      aiForm.elements.apiKey.value = "";
      toast("AI 服务配置已保存");
      await Promise.all([load(), loadAiConfiguration()]);
    }, message => errorIn(aiFailure, message));
  };
  const ossForm = $("#oss-platform-form", target);
  async function loadOssConfiguration() {
    if (!ossForm) return;
    try {
      const saved = await request("/cms/console/channel-credentials/oss");
      if (!current() || !saved.configured) return;
      for (const key of ["endpoint", "bucket", "publicBaseUrl"]) {
        if (ossForm.elements[key]) ossForm.elements[key].value = saved[key] ?? "";
      }
      ossForm.elements.accessKeyId.required = false;
      ossForm.elements.accessKeyId.placeholder = saved.accessKeyIdMasked
        ? `已保存 ${saved.accessKeyIdMasked}（留空则沿用原凭据）`
        : "已保存（留空则沿用原凭据，填写则替换）";
      ossForm.elements.accessKeySecret.required = false;
      ossForm.elements.accessKeySecret.placeholder = "已保存（留空则沿用原密钥，填写则替换）";
    } catch (error) {
      if (current()) errorIn($("#oss-platform-error", target), error.message);
    }
  }
  if (ossForm) ossForm.onsubmit = event => {
    event.preventDefault();
    const ossFailure = $("#oss-platform-error", target);
    busy($("button", ossForm), async () => {
      ossFailure.replaceChildren();
      const settings = Object.fromEntries(new FormData(ossForm));
      await request("/cms/console/channel-credentials", {
        body: { provider: "ALIYUN_OSS", alias: "aliyun-oss", secret: JSON.stringify(settings) }
      });
      ossForm.elements.accessKeyId.value = "";
      ossForm.elements.accessKeySecret.value = "";
      toast("阿里云 OSS 配置已保存");
      await Promise.all([load(), loadOssConfiguration()]);
    }, message => errorIn(ossFailure, message));
  };
  await Promise.all([load(), loadAiConfiguration(), loadOssConfiguration()]);
}

export async function localApi(target, context, current) {
  target.innerHTML = pageHead("API 数据管理", "在管理台查询接口记录，维护站点配置和报表渠道配置。");
  if (context.mode !== "simulation") return;
  target.insertAdjacentHTML("beforeend", `<section class="panel"><form id="api-form" class="form-grid">
    <label>配置类型<select name="kind"><option value="config">站点配置</option><option value="report">报表渠道</option></select></label>
    <label>站点<input name="site" required maxlength="80"></label>
    <label>配置名称或渠道<input name="name" required maxlength="100"></label>
    <label class="all">配置值<textarea name="value" required rows="4" maxlength="16000"></textarea></label>
    <div><button type="submit" class="button">保存配置</button></div></form><div id="api-error"></div></section>
    <section class="panel"><h2>配置与上报记录</h2><div id="api-output"></div></section>`);
  const form = $("#api-form", target);
  const output = $("#api-output", target);
  const failure = $("#api-error", target);
  async function load() {
    try {
      const data = await request("/cms/console/local-api");
      if (!current()) return;
      output.innerHTML = table(["类型", "站点", "配置键"], data.settings.map(row => [
        row.kind === "report" ? "报表渠道" : "站点配置", row.site, row.name].map(e)), "暂无配置记录")
        + table(["上报类型", "站点", "内容", "时间"], data.events.map(row =>
          [row.kind, row.site, row.payload, dateTime(row.createdAt)].map(e)), "暂无反馈、关键词或 IP 上报");
    } catch (error) {
      if (current()) errorIn(output, error.message);
    }
  }
  form.onsubmit = event => {
    event.preventDefault();
    busy($("button", form), async () => {
      failure.replaceChildren();
      await request("/cms/console/local-api", { body: formValues(form) });
      toast("配置已保存");
      await load();
    }, message => errorIn(failure, message));
  };
  await load();
}
