const SESSION_STORAGE_KEY = "afs_cms_session_v1";
const LAST_ACCOUNT_KEY = "afs_cms_last_account";

const realm: { mode: string | null; credentials: { name: string; secret: string } | null } = {
  mode: null,
  credentials: null
};

export interface RequestOptions {
  body?: any;
  method?: string;
  key?: string;
  site?: string;
  token?: string;
  anonymous?: boolean;
}

export interface StreamOptions {
  body?: any;
  method?: string;
  onEvent?: (event: string, data: any) => void;
  signal?: AbortSignal;
}

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export function setMode(mode: string | null) {
  realm.mode = mode;
}

export function setCredentials(name: string, secret: string, persistSession = false) {
  realm.credentials = name && secret ? { name, secret } : null;
  try {
    if (name && secret) {
      localStorage.setItem(LAST_ACCOUNT_KEY, name);
      if (persistSession) {
        sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ name, secret }));
      }
    } else {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
    }
  } catch {
    // Ignore storage errors in restricted browser contexts.
  }
}

export function restoreSessionCredentials(): boolean {
  if (realm.credentials) return true;
  try {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return false;
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed.name === "string" && typeof parsed.secret === "string" && parsed.name && parsed.secret) {
      realm.credentials = { name: parsed.name, secret: parsed.secret };
      return true;
    }
  } catch {
    // Ignore corrupted session storage.
  }
  return false;
}

export function getLastAccountName(fallback = ""): string {
  try {
    return localStorage.getItem(LAST_ACCOUNT_KEY) || fallback;
  } catch {
    return fallback;
  }
}

export function accountName() {
  return realm.credentials?.name || "";
}

export function requestNonce() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const value = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
  return [value.slice(0, 8), value.slice(8, 12), value.slice(12, 16), value.slice(16, 20), value.slice(20)].join("-");
}

function authorization(path, method, credentials, bodyText?: string) {
  if (!credentials) return {};
  const { name, secret } = credentials;
  if (realm.mode === "simulation") {
    const bytes = new TextEncoder().encode(`${name}:${secret}`);
    return { Authorization: "Basic " + btoa(String.fromCharCode(...bytes)) };
  }
  const headers: Record<string, string> = {
    "admin-console": "1",
    "admin-name": name,
    "admin-nonce": requestNonce(),
    "admin-timestamp": String(Date.now())
  };
  if (bodyText !== undefined) headers["admin-body-md5"] = window.md5(bodyText);
  const sorted = Object.keys(headers).sort();
  const parameters = sorted.map(key => `${key}=${headers[key]}&`).join("");
  const endpoint = new URL(path, location.origin);
  return { ...headers, "admin-signature": window.md5(`${endpoint.pathname}${endpoint.search}&${method}&${parameters}${secret}&`) };
}

function validateEndpoint(path) {
  const endpoint = new URL(path, location.origin);
  const maintenance = ["/health", "/healthX", "/daily/report", "/open/endless/ad/resource/archive",
    "/open/adsense/data/list", "/open/ad/resource/do/last-day"];
  if (endpoint.origin !== location.origin || !(endpoint.pathname.startsWith("/cms/") ||
      endpoint.pathname.startsWith("/api/") ||
      maintenance.includes(endpoint.pathname))) throw new Error("无效的接口路径");
  return endpoint;
}

export async function request(path: string, { body, method: explicitMethod, key, site, token, anonymous = false }: RequestOptions = {}): Promise<any> {
  const endpoint = validateEndpoint(path);
  const method = explicitMethod || (body === undefined ? "GET" : "POST");
  const multipart = body instanceof FormData;
  const bodyText = body !== undefined && !multipart ? JSON.stringify(body) : undefined;
  const headers = { Accept: "application/json", ...(anonymous ? {} : authorization(path, method, realm.credentials, bodyText)) };
  if (site) headers["Current-Site"] = site;
  if (token && endpoint.pathname.startsWith("/open/")) headers.Authorization = token;
  if (key) headers["Idempotency-Key"] = key;
  if ((method === "POST" || method === "PUT") && body !== undefined && !multipart) {
    headers["Content-Type"] = "application/json";
  }
  const response = await fetch(path, {
    method, headers, cache: "no-store", credentials: "same-origin",
    body: multipart ? body : bodyText
  });
  const data = response.headers.get("content-type")?.includes("application/json") ? await response.json() : null;
  if (!response.ok) {
    const explanation = { 401: "登录已失效或账号不正确", 403: "当前账号没有执行此操作的权限",
      404: "接口或记录不存在", 409: "记录状态已改变，请刷新后重试" };
    throw new ApiError(explanation[response.status] || data?.msg || data?.message || `请求失败（HTTP ${response.status}）`, response.status);
  }
  if (!data || (Number.isInteger(data.code) && data.code !== 0)) {
    throw new ApiError(data?.msg || "CMS 返回了无效的响应", response.status);
  }
  return Number.isInteger(data.code) ? data.data : data;
}

/**
 * Authenticated SSE stream reader over fetch() so console MD5 signatures and Basic auth work cleanly.
 */
export async function streamRequest(path: string, { body, method: explicitMethod, onEvent, signal }: StreamOptions = {}): Promise<void> {
  validateEndpoint(path);
  const method = explicitMethod || (body === undefined ? "GET" : "POST");
  const bodyText = body !== undefined ? JSON.stringify(body) : undefined;
  const headers = {
    Accept: "text/event-stream",
    ...authorization(path, method, realm.credentials, bodyText)
  };
  if (bodyText !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(path, {
    method, headers, cache: "no-store", credentials: "same-origin", signal,
    body: bodyText
  });
  if (!response.ok) {
    const data = response.headers.get("content-type")?.includes("application/json")
      ? await response.json().catch(() => null) : null;
    throw new ApiError(data?.msg || data?.message || `流式连接失败（HTTP ${response.status}）`, response.status);
  }
  if (!response.body) return;
  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let eventName = "message";
  let dataLines = [];

  const dispatch = () => {
    if (!dataLines.length) return;
    const raw = dataLines.join("\n");
    dataLines = [];
    let parsed = raw;
    try { parsed = JSON.parse(raw); } catch { /* keep text */ }
    if (onEvent) onEvent(eventName || "message", parsed);
    eventName = "message";
  };

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? "";
      for (const line of lines) {
        if (line === "") {
          dispatch();
        } else if (line.startsWith("event:")) {
          eventName = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
          dataLines.push(line.slice(5).trimStart());
        }
      }
    }
    if (buffer.startsWith("data:")) dataLines.push(buffer.slice(5).trimStart());
    dispatch();
  } finally {
    try { reader.releaseLock(); } catch { /* ignore */ }
  }
}

export async function requestList(path) {
  const data = await request(path);
  const rows = Array.isArray(data) ? data : data?.list;
  if (!Array.isArray(rows)) throw new Error("接口返回的列表格式不正确");
  return rows;
}
