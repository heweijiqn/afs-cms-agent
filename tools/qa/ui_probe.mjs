import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const KEY = fs.readFileSync(path.join(__dirname, "..", "..", "data", "local-admin.key"), "utf8").trim();
const PORT = 9334;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chrome = spawn("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", [
  "--headless=new", `--remote-debugging-port=${PORT}`, `--user-data-dir=${path.join(process.env.TEMP, "afs-ui-probe")}`,
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
  async eval(expression) { const r = await this.send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true }); return r.result?.value; }
}
async function waitFor(fn, t = 20000) { const s = Date.now(); while (Date.now() - s < t) { try { const v = await fn(); if (v) return v; } catch {} await sleep(300); } throw new Error("timeout"); }

try {
  await waitFor(async () => (await fetch(`http://127.0.0.1:${PORT}/json/version`)).ok);
  const t = await (await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: "PUT" })).json();
  const cdp = new Cdp(t.webSocketDebuggerUrl); await cdp.connect();
  await cdp.send("Page.enable"); await cdp.send("Runtime.enable"); await cdp.send("Log.enable");
  const csp = [];
  cdp.on("Log.entryAdded", p => { if (p.entry?.level === "error" && /Content Security Policy/.test(p.entry.text)) csp.push(p.entry.text.slice(0, 260)); });
  await cdp.send("Page.navigate", { url: "http://127.0.0.1:9901/console" });
  await waitFor(() => cdp.eval("!!document.querySelector('#login-screen, #workspace')"));
  if (await cdp.eval("!!document.querySelector('#login-screen')")) {
    await cdp.eval(`(() => { const set=(el,v)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));};
      set(document.querySelector('input[name=name]'),'local-admin'); set(document.querySelector('input[name=secret]'),${JSON.stringify(KEY)});
      document.querySelector('.login-form').requestSubmit(); })()`);
    await waitFor(() => cdp.eval("!!document.querySelector('#workspace') && !document.querySelector('#login-screen')"), 25000);
  }
  await cdp.eval("location.hash='#overview'"); await sleep(2500);
  const info = await cdp.eval(`(() => {
    return Array.from(document.querySelectorAll('#main [style]')).map(el => {
      const inline = el.style;
      const cs = getComputedStyle(el);
      return { tag: el.tagName, cls: String(el.className).slice(0,60), attr: el.getAttribute('style'),
        cssText: inline.cssText, inlineMargin: inline.marginTop, computedMargin: cs.marginTop,
        inlineDisplay: inline.display, computedDisplay: cs.display, inlineGrid: inline.gridColumn, computedGrid: cs.gridColumn,
        inlineFont: inline.fontSize, computedFont: cs.fontSize, text: (el.innerText||'').slice(0,50) };
    });
  })()`);
  console.log(JSON.stringify({ cspCount: csp.length, csp: [...new Set(csp)], elements: info }, null, 2));
} catch (e) { console.error("ERR", e); process.exitCode = 1; }
finally { try { chrome.kill(); } catch {} }
