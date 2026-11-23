import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const KEY = fs.readFileSync(path.join(__dirname, "..", "..", "data", "local-admin.key"), "utf8").trim();
const PORT = 9335;
const RUN = process.argv[2];
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chrome = spawn("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", [
  "--headless=new", `--remote-debugging-port=${PORT}`, `--user-data-dir=${path.join(process.env.TEMP, "afs-ui-runprobe")}`,
  "--no-first-run", "--disable-gpu", "--window-size=1440,960", "about:blank"
], { stdio: "ignore" });

class Cdp {
  constructor(url) { this.url = url; this.id = 0; this.pending = new Map(); this.handlers = {}; }
  async connect() {
    this.ws = new WebSocket(this.url);
    await new Promise((res, rej) => { this.ws.onopen = res; this.ws.onerror = rej; });
    this.ws.onmessage = e => {
      const m = JSON.parse(e.data);
      if (m.id) { const p = this.pending.get(m.id); this.pending.delete(m.id); m.error ? p.reject(m.error) : p.resolve(m.result); }
      else (this.handlers[m.method] || []).forEach(f => f(m.params));
    };
  }
  send(method, params = {}) { const id = ++this.id; this.ws.send(JSON.stringify({ id, method, params })); return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject })); }
  on(m, f) { (this.handlers[m] ||= []).push(f); }
  async eval(expression) { const r = await this.send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true }); if (r.exceptionDetails) return { __error: r.exceptionDetails.text }; return r.result?.value; }
}
async function waitFor(fn, t = 25000) { const s = Date.now(); while (Date.now() - s < t) { try { const v = await fn(); if (v) return v; } catch {} await sleep(300); } throw new Error("timeout"); }

try {
  await waitFor(async () => (await fetch(`http://127.0.0.1:${PORT}/json/version`)).ok);
  const t = await (await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: "PUT" })).json();
  const cdp = new Cdp(t.webSocketDebuggerUrl); await cdp.connect();
  await cdp.send("Page.enable"); await cdp.send("Runtime.enable");
  const errors = [];
  cdp.on("Runtime.exceptionThrown", p => errors.push(p.exceptionDetails?.exception?.description || p.exceptionDetails?.text));
  await cdp.send("Page.navigate", { url: "http://127.0.0.1:9901/console" });
  await waitFor(() => cdp.eval("!!document.querySelector('#login-screen, #workspace')"));
  if (await cdp.eval("!!document.querySelector('#login-screen')")) {
    await cdp.eval(`(() => { const set=(el,v)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));};
      set(document.querySelector('input[name=name]'),'local-admin'); set(document.querySelector('input[name=secret]'),${JSON.stringify(KEY)});
      document.querySelector('.login-form').requestSubmit(); })()`);
    await waitFor(() => cdp.eval("!!document.querySelector('#workspace') && !document.querySelector('#login-screen')"), 25000);
  }
  await cdp.eval("location.hash='#agent'"); await sleep(2500);
  await cdp.eval("document.querySelector('[data-agent-tab=workflow]').click()"); await sleep(1600);
  const found = await cdp.eval(`!!document.querySelector('[data-run="${RUN}"]')`);
  const result = {
    run: RUN, foundInHistory: found, errors,
    history: await cdp.eval(`(() => ({ text: (document.querySelector('#run-history')?.innerText||'').slice(0,600), buttons: document.querySelectorAll('#run-history [data-run]').length, html: (document.querySelector('#run-history')?.innerHTML||'').slice(0,300) }))()`),
    detail: await cdp.eval(`(() => ({ chars: (document.querySelector('#run-detail')?.innerText||'').trim().length, text: (document.querySelector('#run-detail')?.innerText||'').slice(0,500), html: (document.querySelector('#run-detail')?.innerHTML||'').slice(0,300) }))()`),
    afterClick: null
  };
  if (found) {
    await cdp.eval(`document.querySelector('[data-run="${RUN}"]').click()`);
    await sleep(1800);
    result.detail = await cdp.eval(`(() => ({ chars: (document.querySelector('#run-detail')?.innerText||'').trim().length, text: (document.querySelector('#run-detail')?.innerText||'').slice(0,500) }))()`);
    result.afterClick = await cdp.eval(`(() => ({ toasts: Array.from(document.querySelectorAll('.toast')).map(x=>x.innerText), html: (document.querySelector('#run-detail')?.innerHTML||'').slice(0,400) }))()`);
    await cdp.send("Page.captureScreenshot", { format: "png" }).then(r => fs.writeFileSync(path.join(__dirname, "out", "ui", "run-five-agent.png"), Buffer.from(r.data, "base64")));
  }
  console.log(JSON.stringify(result, null, 2));
} catch (e) { console.error("ERR", e); process.exitCode = 1; }
finally { try { chrome.kill(); } catch {} }
