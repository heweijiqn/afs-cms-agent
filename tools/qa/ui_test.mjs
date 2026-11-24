// Headless Chrome UI walkthrough for the AFS console, driven over raw CDP.
// No external dependencies: Node 24 WebSocket + fetch only.
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(__dirname, "out", "ui");
fs.mkdirSync(OUT, { recursive: true });

const CHROME = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const PORT = 9333;
const PROFILE = path.join(process.env.TEMP || ".", `afs-ui-profile-${Date.now()}`);
const BASE = "http://127.0.0.1:9901";
const KEY = fs.readFileSync(path.join(__dirname, "..", "..", "data", "local-admin.key"), "utf8").trim();

const sleep = ms => new Promise(r => setTimeout(r, ms));

const chrome = spawn(CHROME, [
  "--headless=new", `--remote-debugging-port=${PORT}`, `--user-data-dir=${PROFILE}`,
  "--no-first-run", "--no-default-browser-check", "--disable-gpu", "--disable-dev-shm-usage",
  "--hide-scrollbars", "--mute-audio", "--window-size=1440,960", "about:blank"
], { stdio: "ignore", detached: false });

async function waitFor(fn, timeout = 30000, interval = 300) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    try { const v = await fn(); if (v) return v; } catch { /* retry */ }
    await sleep(interval);
  }
  throw new Error("timeout");
}

async function json(pathname, init) {
  const res = await fetch(`http://127.0.0.1:${PORT}${pathname}`, init);
  return res.json();
}

class Cdp {
  constructor(url) { this.url = url; this.id = 0; this.pending = new Map(); this.listeners = new Map(); }
  async connect() {
    this.ws = new WebSocket(this.url);
    await new Promise((res, rej) => { this.ws.onopen = res; this.ws.onerror = rej; });
    this.ws.onmessage = ev => {
      const msg = JSON.parse(ev.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
      } else if (msg.method) {
        for (const fn of this.listeners.get(msg.method) || []) fn(msg.params);
      }
    };
  }
  send(method, params = {}) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }
  on(method, fn) {
    if (!this.listeners.has(method)) this.listeners.set(method, []);
    this.listeners.get(method).push(fn);
  }
  async eval(expression, awaitPromise = true) {
    const r = await this.send("Runtime.evaluate", { expression, awaitPromise, returnByValue: true });
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.text + " " + (r.exceptionDetails.exception?.description || ""));
    return r.result?.value;
  }
}

const report = { checks: [], console: [], pageErrors: [], networkErrors: [], routes: [] };
const check = (name, ok, detail = "") => {
  report.checks.push({ name, ok: !!ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"} ${name}${detail ? " :: " + detail : ""}`);
};

try {
  await waitFor(() => json("/json/version").then(() => true).catch(() => false), 20000);
  const target = await json(`/json/new?${encodeURIComponent("about:blank")}`, { method: "PUT" });
  const cdp = new Cdp(target.webSocketDebuggerUrl);
  await cdp.connect();
  await cdp.send("Page.enable");
  await cdp.send("Runtime.enable");
  await cdp.send("Log.enable");
  await cdp.send("Network.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 1440, height: 960, deviceScaleFactor: 1, mobile: false });

  cdp.on("Runtime.consoleAPICalled", p => {
    const text = (p.args || []).map(a => a.value ?? a.description ?? "").join(" ");
    if (p.type === "error" || p.type === "warning") report.console.push({ type: p.type, text });
  });
  cdp.on("Runtime.exceptionThrown", p => report.pageErrors.push(p.exceptionDetails?.text + " " + (p.exceptionDetails?.exception?.description || "")));
  cdp.on("Log.entryAdded", p => {
    if (p.entry?.level === "error") report.console.push({ type: "log-error", text: p.entry.text + " " + (p.entry.url || "") });
  });
  cdp.on("Network.responseReceived", p => {
    const r = p.response;
    if (r.status >= 400 && !r.url.includes("/favicon")) report.networkErrors.push({ status: r.status, url: r.url });
  });

  await cdp.send("Page.navigate", { url: `${BASE}/console` });
  await waitFor(() => cdp.eval("document.readyState === 'complete'"));
  await waitFor(() => cdp.eval("!!document.querySelector('#login-screen, #workspace')"), 20000);

  const loginVisible = await cdp.eval("!!document.querySelector('#login-screen')");
  check("login screen rendered", loginVisible);
  check("console title", (await cdp.eval("document.title")).includes("AFS"), await cdp.eval("document.title"));
  await cdp.send("Page.captureScreenshot", { format: "png" }).then(r => fs.writeFileSync(path.join(OUT, "01-login.png"), Buffer.from(r.data, "base64")));

  if (loginVisible) {
    const filled = await cdp.eval(`(() => {
      const set = (el, v) => { const d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value'); d.set.call(el, v); el.dispatchEvent(new Event('input', {bubbles:true})); };
      const name = document.querySelector('input[name=name]');
      const secret = document.querySelector('input[name=secret]');
      if (!name || !secret) return 'missing-inputs';
      set(name, 'local-admin'); set(secret, ${JSON.stringify(KEY)});
      const form = document.querySelector('.login-form');
      form.requestSubmit();
      return 'submitted';
    })()`);
    check("login form accepts credentials", filled === "submitted", filled);
    await waitFor(() => cdp.eval("!!document.querySelector('#workspace.shell') && !document.querySelector('#login-screen')"), 25000);
    check("authenticated shell rendered", true);
    await sleep(1500);
    await cdp.send("Page.captureScreenshot", { format: "png" }).then(r => fs.writeFileSync(path.join(OUT, "02-overview.png"), Buffer.from(r.data, "base64")));
  }

  const routes = [
    ["overview", "运营总览"], ["campaigns", "广告活动"], ["reports", "投放报表"], ["rules", "投放规则"],
    ["agent", "智能体工作台"], ["content", "内容管理"], ["accounts", "账号与渠道"], ["vault", "渠道密钥"],
    ["settings", "站点配置"], ["dictionaries", "基础资料"], ["feedback", "用户反馈"], ["system", "系统运维"],
    ["profile", "个人资料"]
  ];
  for (const [route, label] of routes) {
    const before = { console: report.console.length, errors: report.pageErrors.length, network: report.networkErrors.length };
    await cdp.eval(`location.hash = ${JSON.stringify("#" + route)}`);
    await sleep(route === "agent" ? 2500 : 1800);
    const state = await cdp.eval(`(() => {
      const main = document.querySelector('#main');
      const text = (main?.innerText || '').trim();
      return {
        heading: document.querySelector('#main h1')?.innerText || '',
        title: document.title,
        textLength: text.length,
        placeholder: text.includes('正在加载视图'),
        busy: !!document.querySelector('#main .loading, #main .skeleton'),
        toasts: Array.from(document.querySelectorAll('.toast, .alert.error')).map(x => x.innerText).filter(Boolean).slice(0, 3),
        navActive: document.querySelector('nav a[aria-current=page]')?.getAttribute('data-nav') || '',
        inlineStyled: document.querySelectorAll('#main [style]').length
      };
    })()`);
    await cdp.send("Page.captureScreenshot", { format: "png" })
      .then(r => fs.writeFileSync(path.join(OUT, `route-${route}.png`), Buffer.from(r.data, "base64")));
    const newErrors = report.console.slice(before.console).filter(x => x.type !== "warning");
    const newPageErrors = report.pageErrors.slice(before.errors);
    const newNet = report.networkErrors.slice(before.network);
    report.routes.push({ route, label, state, newErrors, newPageErrors, newNet });
    const ok = state.textLength > 40 && !state.placeholder && state.heading.length > 0 &&
      !state.toasts.some(t => /失败|错误|internal error|无法/.test(t)) && newPageErrors.length === 0;
    check(`route ${route} renders`, ok,
      `heading=${JSON.stringify(state.heading)} chars=${state.textLength}` +
      (state.toasts.length ? ` toasts=${JSON.stringify(state.toasts)}` : "") +
      (newPageErrors.length ? ` pageErrors=${JSON.stringify(newPageErrors).slice(0, 300)}` : "") +
      (newNet.length ? ` http=${JSON.stringify(newNet).slice(0, 300)}` : ""));
  }

  // keyboard command palette
  await cdp.eval(`window.dispatchEvent(new KeyboardEvent('keydown',{key:'k',ctrlKey:true,bubbles:true}))`);
  await sleep(700);
  const palette = await cdp.eval(`(() => {
    const el = document.querySelector('.cmd-backdrop');
    return { open: !!el, items: document.querySelectorAll('.cmd-item').length, input: !!document.querySelector('.cmd-input') };
  })()`);
  check("command palette opens with Ctrl+K", palette.open && palette.items > 0, JSON.stringify(palette));
  if (palette.open) {
    await cdp.send("Page.captureScreenshot", { format: "png" }).then(r => fs.writeFileSync(path.join(OUT, "cmd-palette.png"), Buffer.from(r.data, "base64")));
    await cdp.eval(`(() => { const i = document.querySelector('.cmd-input'); i.focus(); i.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true})); })()`);
    await sleep(400);
    check("command palette closes with Escape", !(await cdp.eval(`!!document.querySelector('.cmd-backdrop')`)));
  }

  // overview chart actually paints pixels
  await cdp.eval(`location.hash = '#overview'`);
  await sleep(2000);
  const chart = await cdp.eval(`(() => {
    const c = document.querySelector('canvas.chart');
    const styled = Array.from(document.querySelectorAll('#main [style]'));
    const mismatched = [];
    for (const el of styled) {
      const inline = el.style;
      const props = ['marginTop','marginBottom','display','fontSize','gridColumn','padding'];
      for (const prop of props) {
        const declared = inline[prop];
        if (!declared) continue;
        const computed = getComputedStyle(el)[prop];
        const normalized = declared.replace(/px$/, '');
        if (!computed.startsWith(normalized)) {
          mismatched.push({ tag: el.tagName, cls: String(el.className).slice(0,40), prop, declared, computed });
          break;
        }
      }
    }
    if (!c) return { found: false, styledElements: styled.length, mismatched };
    const ctx = c.getContext('2d');
    let painted = 0;
    try {
      const d = ctx.getImageData(0, 0, c.width, c.height).data;
      for (let i = 3; i < d.length; i += 4) if (d[i] > 0) painted++;
    } catch (e) { return { found: true, error: String(e), styledElements: styled.length, mismatched }; }
    return { found: true, width: c.width, height: c.height, painted, styledElements: styled.length, mismatched };
  })()`);
  report.overviewInline = { styledElements: chart.styledElements, mismatched: chart.mismatched };
  check("overview chart paints data", chart.found && chart.width > 100 && chart.painted > 1000, JSON.stringify(chart).slice(0, 200));
  check("overview inline styles honored", (chart.styledElements || 0) === 0 || (chart.mismatched || []).length === 0,
    `styled=${chart.styledElements} mismatched=${JSON.stringify((chart.mismatched || []).slice(0, 3))}`);

  // exercise every agent workbench tab
  const beforeTabs = report.console.length;
  await cdp.eval(`location.hash = '#agent'`);
  await sleep(2500);
  const tabIds = await cdp.eval(`Array.from(document.querySelectorAll('[data-agent-tab]')).map(b => b.getAttribute('data-agent-tab'))`);
  check("agent workbench exposes 7 tabs", Array.isArray(tabIds) && tabIds.length === 7, JSON.stringify(tabIds));
  for (const tab of tabIds || []) {
    const before = report.console.length;
    await cdp.eval(`(() => { const b = document.querySelector('[data-agent-tab="${tab}"]'); if (b) b.click(); return true; })()`);
    await sleep(tab === "copilot" || tab === "five-agent" ? 1800 : 1000);
    const state = await cdp.eval(`(() => ({
      text: (document.querySelector('#agent-tab-body')?.innerText || '').trim(),
      pressed: document.querySelector('[data-agent-tab="${tab}"]')?.getAttribute('aria-pressed'),
      buttons: document.querySelectorAll('#agent-tab-body button').length,
      errors: Array.from(document.querySelectorAll('#agent-tab-body .error, #agent-tab-body .alert.error')).map(e => e.innerText)
    }))()`);
    await cdp.send("Page.captureScreenshot", { format: "png" })
      .then(r => fs.writeFileSync(path.join(OUT, `agent-tab-${tab}.png`), Buffer.from(r.data, "base64")));
    const newErr = report.console.slice(before).filter(x => x.type === "error" || x.type === "log-error");
    const newCsp = newErr.filter(x => /Content Security Policy/.test(x.text));
    report.routes.push({ route: `agent:${tab}`, state, newErrors: newErr });
    check(`agent tab ${tab} renders content`, state.text.length > 30 && state.pressed === "true",
      `chars=${state.text.length} pressed=${state.pressed} buttons=${state.buttons}` +
      (state.errors.length ? ` errors=${JSON.stringify(state.errors)}` : "") +
      (newCsp.length ? ` cspViolations=${newCsp.length}` : ""));
  }
  report.cspViolations = report.console.filter(x => /Content Security Policy/.test(x.text)).length;
  check("no CSP violations during walkthrough", report.cspViolations === 0, `${report.cspViolations} inline-style violations`);

  // modal lifecycle: open, scroll lock, native Escape close and cleanup
  await cdp.eval(`location.hash = '#agent'`);
  await sleep(1500);
  const opened = await cdp.eval(`(() => { const b = document.querySelector('#new-analysis'); if (b) b.click(); return !!b; })()`);
  await sleep(900);
  const modalOpen = await cdp.eval(`(() => { const m = document.querySelector('#modal'); return {
    open: !!m?.open, bodyLocked: document.body.classList.contains('modal-open'),
    title: (document.querySelector('#modal-title')?.textContent || '').trim(),
    hasForm: !!document.querySelector('#modal form')
  }; })()`);
  check("operation modal opens with scroll lock", opened && modalOpen.open && modalOpen.bodyLocked, JSON.stringify(modalOpen));
  await cdp.send("Input.dispatchKeyEvent", { type: "keyDown", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
  await cdp.send("Input.dispatchKeyEvent", { type: "keyUp", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
  await sleep(700);
  const modalClosed = await cdp.eval(`(() => { const m = document.querySelector('#modal'); return {
    open: !!m?.open, bodyLocked: document.body.classList.contains('modal-open'),
    contentChildren: document.querySelector('#modal-content')?.children.length || 0,
    title: (document.querySelector('#modal-title')?.textContent || '').trim()
  }; })()`);
  check("modal closes with Escape and releases scroll lock",
    !modalClosed.open && !modalClosed.bodyLocked && modalClosed.contentChildren === 0,
    JSON.stringify(modalClosed));

  // functional UI action: submit the anomaly scan form
  await cdp.eval(`document.querySelector('[data-agent-tab="scan"]').click()`);
  await sleep(1200);
  await cdp.eval(`document.querySelector('#scan-form').requestSubmit()`);
  await sleep(3500);
  const scan = await cdp.eval(`(() => ({
    text: (document.querySelector('#scan-output')?.innerText || '').trim().slice(0, 400),
    rows: document.querySelectorAll('#scan-output tbody tr').length,
    buttons: document.querySelectorAll('#scan-output button').length,
    loading: /正在执行/.test(document.querySelector('#scan-output')?.innerText || '')
  }))()`);
  check("anomaly scan UI returns results", !scan.loading && scan.text.length > 80, JSON.stringify(scan).slice(0, 260));

  // functional UI action: open an existing run from history
  await cdp.eval(`document.querySelector('[data-agent-tab="workflow"]').click()`);
  await sleep(1500);
  const history = await cdp.eval(`(() => ({ runs: document.querySelectorAll('#run-history [data-run]').length, first: document.querySelector('#run-history [data-run]')?.getAttribute('data-run') || '' }))()`);
  check("run history lists persisted runs", history.runs > 0, JSON.stringify(history));
  if (history.first) {
    await cdp.eval(`document.querySelector('#run-history [data-run]').click()`);
    await sleep(1800);
    const detail = await cdp.eval(`(() => ({ chars: (document.querySelector('#run-detail')?.innerText || '').trim().length, text: (document.querySelector('#run-detail')?.innerText || '').slice(0, 160) }))()`);
    check("run detail opens from history", detail.chars > 120, JSON.stringify(detail));
  }

  // quantify CSP impact on inline-styled panels (toolbox tab is still open)
  const cspImpact = await cdp.eval(`(() => {
    const els = Array.from(document.querySelectorAll('[style]'));
    const samples = [];
    let blocked = 0;
    for (const el of els) {
      const decl = el.getAttribute('style') || '';
      const margin = decl.match(/margin-top:\\s*([0-9.]+)px/);
      const display = decl.match(/display:\\s*([a-z-]+)/);
      const fs = decl.match(/font-size:\\s*([0-9.]+)px/);
      let mismatch = false;
      if (margin && getComputedStyle(el).marginTop !== margin[1] + 'px') mismatch = true;
      if (display && getComputedStyle(el).display !== display[1]) mismatch = true;
      if (fs && getComputedStyle(el).fontSize !== fs[1] + 'px') mismatch = true;
      if (mismatch) {
        blocked++;
        if (samples.length < 5) samples.push({ tag: el.tagName, cls: el.className, style: decl.slice(0, 90),
          computedMarginTop: getComputedStyle(el).marginTop, computedDisplay: getComputedStyle(el).display,
          computedFontSize: getComputedStyle(el).fontSize });
      }
    }
    return { styledElements: els.length, blocked, samples };
  })()`);
  report.cspImpact = cspImpact;
  check("inline styles are applied by the browser", !cspImpact || cspImpact.blocked === 0,
    `styled=${cspImpact?.styledElements} blocked=${cspImpact?.blocked} sample=${JSON.stringify(cspImpact?.samples?.[0] || {})}`);

  // responsive/mobile smoke
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await cdp.eval(`location.hash = '#overview'`);
  await sleep(1500);
  const mobile = await cdp.eval(`(() => {
    const sidebar = document.querySelector('.sidebar');
    const main = document.querySelector('#main');
    const overflow = document.documentElement.scrollWidth > window.innerWidth + 2;
    return { sidebarVisible: !!sidebar && sidebar.getBoundingClientRect().width > 0, mainWidth: main?.getBoundingClientRect().width || 0, overflow };
  })()`);
  await cdp.send("Page.captureScreenshot", { format: "png" }).then(r => fs.writeFileSync(path.join(OUT, "mobile-overview.png"), Buffer.from(r.data, "base64")));
  check("mobile layout has no horizontal overflow", !mobile.overflow, JSON.stringify(mobile));
} catch (err) {
  report.fatal = String(err?.stack || err);
  console.error("FATAL", report.fatal);
} finally {
  fs.writeFileSync(path.join(OUT, "ui-report.json"), JSON.stringify(report, null, 2));
  const failed = report.checks.filter(c => !c.ok);
  console.log(`\n${report.checks.length - failed.length}/${report.checks.length} UI checks passed`);
  try { chrome.kill(); } catch { /* ignore */ }
  process.exit(failed.length || report.fatal ? 1 : 0);
}
