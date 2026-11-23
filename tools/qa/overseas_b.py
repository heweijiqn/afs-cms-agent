"""Stage B7/B8/B9/B11/B12: auth, replay, input validation, public API, exposure.

Writes tools/qa/out/overseas-sec_B.json
"""
from __future__ import annotations

import hashlib
import json
import sys
import time
import uuid
from pathlib import Path
from urllib import error, request

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient, qs, read_local_key  # noqa: E402

BASE = "http://127.0.0.1:9901"
OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)
RES: dict = {"stage": "B", "started": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "checks": [], "steps": {}, "created": {}}
c = CmsClient()
KEY = read_local_key()


def save():
    RES["finished"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    (OUT / "overseas-sec_B.json").write_text(json.dumps(RES, ensure_ascii=False, indent=2), encoding="utf-8")


def chk(cid, desc, expected, status, body, ok, detail=None):
    entry = {"id": cid, "desc": desc, "expected": expected, "status": status, "ok": bool(ok)}
    if isinstance(body, dict):
        entry["code"] = body.get("code")
        entry["msg"] = body.get("msg")
    if detail is not None:
        entry["detail"] = detail
    RES["checks"].append(entry)
    print(f"[{'PASS' if ok else 'FAIL'}] {cid} {desc} -> {status} ok={ok}"
          + (f" code={entry.get('code')} msg={entry.get('msg')}" if isinstance(body, dict) else ""))
    return entry


def raw(method, path, body=None, headers=None, timeout=60, raw_body: bytes | None = None):
    data = raw_body
    hdrs = dict(headers or {})
    if body is not None and raw_body is None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hdrs.setdefault("Content-Type", "application/json")
    req = request.Request(BASE + path, data=data, method=method.upper(), headers=hdrs)
    started = time.perf_counter()
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            txt = resp.read().decode("utf-8", "replace")
            st, hs = resp.status, dict(resp.headers)
    except error.HTTPError as exc:
        txt = exc.read().decode("utf-8", "replace")
        st, hs = exc.code, dict(exc.headers)
    except Exception as exc:
        return -1, None, repr(exc), (time.perf_counter() - started) * 1000, {}
    try:
        parsed = json.loads(txt) if txt else None
    except json.JSONDecodeError:
        parsed = None
    return st, parsed, txt, (time.perf_counter() - started) * 1000, hs


def hdrs(name="local-admin", method="GET", path="/cms/agent/campaigns", secret=KEY, console=True,
         nonce=None, ts=None, signature="auto", omit=()):
    h = {
        "admin-name": name,
        "admin-nonce": nonce if nonce is not None else str(uuid.uuid4()),
        "admin-timestamp": str(ts if ts is not None else int(time.time() * 1000)),
    }
    if console:
        h["admin-console"] = "1"
    for k in omit:
        h.pop(k, None)
    if signature == "auto":
        pairs = sorted((k, v) for k, v in h.items() if k.startswith("admin-"))
        plain = path.split("?")[0] + "&" + method.upper() + "&" + "".join(f"{k}={v}&" for k, v in pairs) + secret + "&"
        h["admin-signature"] = hashlib.md5(plain.encode("utf-8")).hexdigest()
    elif signature is not None:
        h["admin-signature"] = signature
    return h


# ============================================================== B7 auth matrix
print("=== B7 auth ===")
st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(omit=("admin-signature",), signature=None))
chk("B7.1", "missing admin-signature -> 401", "401", st, body, st == 401, {"body": body, "ms": round(ms)})

st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(signature="0" * 32))
chk("B7.2", "wrong signature -> 401", "401", st, body, st == 401, {"body": body})

st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(name="nobody.qa"))
chk("B7.3", "unknown admin-name -> 401", "401", st, body, st == 401, {"body": body})

st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(name="alex.chen", secret="wrong-key"))
chk("B7.4", "known non-root user + wrong key -> 401", "401", st, body, st == 401, {"body": body})

now = int(time.time() * 1000)
old_ts = now - 13 * 60 * 60 * 1000
st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(ts=old_ts))
chk("B7.5", "tampered timestamp 13h in the past -> 401", "401", st, body, st == 401, {"body": body, "ts": old_ts})
fut_ts = now + 13 * 60 * 60 * 1000
st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(ts=fut_ts))
chk("B7.6", "tampered timestamp 13h in the future -> 401", "401", st, body, st == 401, {"body": body, "ts": fut_ts})
edge_ts = now - 11 * 60 * 60 * 1000
st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(ts=edge_ts))
chk("B7.7", "11h-old signed timestamp still accepted (12h window)", "200", st, body, st == 200,
    {"body": body if st != 200 else (body or {}).get("code"), "ts": edge_ts})

st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(nonce="not-a-uuid"))
chk("B7.8", "malformed nonce 'not-a-uuid' -> accepted (no nonce format check)", "200 (no format validation)",
    st, body, st == 200, {"body": body if st != 200 else (body or {}).get("code")})
st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(nonce=""))
chk("B7.9", "empty nonce -> 401", "401", st, body, st == 401, {"body": body})
st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(nonce="a" * 4000))
chk("B7.10", "4000-char nonce accepted", "200 (length unchecked) -> oversized input tolerated", st, body, st == 200,
    {"body": body if st != 200 else (body or {}).get("code")})

st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(console=False))
chk("B7.11", "valid signature without admin-console works for normal endpoint", "200", st, body, st == 200,
    {"code": (body or {}).get("code")})
st, body, txt, ms, _ = raw("GET", "/cms/agent/overseas/accounts?currency=USD", headers=hdrs(console=False))
chk("B7.12", "admin-console omitted on overseas read endpoint", "200", st, body, st == 200,
    {"code": (body or {}).get("code")})

print("--- non-root (alex.chen) authorization ---")
nonroot = dict(name="alex.chen")
for cid, path, method, payload in [
    ("B7.13", "/cms/user/list", "GET", None),
    ("B7.14", "/cms/console/operators?pageIndex=1&pageSize=5", "GET", None),
    ("B7.15", "/cms/console/maintenance/options", "GET", None),
]:
    st, body, txt, ms, _ = raw(method, path, body=payload, headers=hdrs(method=method, path=path, **nonroot))
    chk(cid, f"non-root {method} {path} with console header -> 403", "403", st, body, st == 403, {"body": body})
    st2, body2, txt2, ms2, _ = raw(method, path, body=payload, headers=hdrs(method=method, path=path, console=False, **nonroot))
    chk(cid + "b", f"non-root {method} {path} without console header -> 401", "401", st2, body2, st2 == 401, {"body": body2})

rule_path = "/cms/ad/resource/rule/add"
st, body, txt, ms, _ = raw("POST", rule_path, body={}, headers=hdrs(method="POST", path=rule_path, **nonroot))
chk("B7.16", "non-root POST /cms/ad/resource/rule/add with console header -> 403", "403", st, body, st == 403,
    {"body": body})
st2, body2, txt2, ms2, _ = raw("POST", rule_path, body={}, headers=hdrs(method="POST", path=rule_path, console=False, **nonroot))
chk("B7.16b", "non-root POST /cms/ad/resource/rule/add WITHOUT console header -> must be 401/403",
    "401/403 (root-only console action)", st2, body2, st2 in (401, 403),
    {"body": body2, "note": "CONSOLE_ADMIN_ACTIONS is only enforced when the optional admin-console header is '1'; "
                            "omitting it skips the root check entirely"})

st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(**nonroot))
chk("B7.17", "non-root can read /cms/agent/campaigns", "200", st, body, st == 200,
    {"code": (body or {}).get("code"), "rows": len(((body or {}).get("data") or {}).get("list") or [])
     if isinstance((body or {}).get("data"), dict) else None})

st, body, txt, ms, _ = raw("GET", "/cms/agent/overseas/automation/rules",
                            headers=hdrs(path="/cms/agent/overseas/automation/rules", **nonroot))
chk("B7.18", "non-root can read overseas automation rules", "200", st, body, st == 200, {"code": (body or {}).get("code")})
st, body, txt, ms, _ = raw("POST", "/cms/agent/overseas/currencies",
                            body={"date": "2026-11-23", "source": "QA", "rates": {"EUR": 0.94}},
                            headers=hdrs(method="POST", path="/cms/agent/overseas/currencies", **nonroot))
chk("B7.19", "non-root POST /overseas/currencies (RootAuth) -> 403/401", "401/403", st, body, st in (401, 403),
    {"body": body})
save()

# ============================================================ B8 replay / nonce
print("\n=== B8 signature replay ===")
r0 = c.get("/cms/agent/campaigns")
replay_h = {k: v for k, v in c.last_headers.items() if k.startswith("admin-")}
RES["steps"]["B8_captured_headers"] = {"nonce": replay_h.get("admin-nonce"),
                                       "timestamp": replay_h.get("admin-timestamp"),
                                       "signature": replay_h.get("admin-signature")}
replays = []
for i in range(3):
    st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=replay_h)
    replays.append({"attempt": i + 1, "status": st, "code": (body or {}).get("code")})
time.sleep(1)
st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=replay_h)
replays.append({"attempt": 4, "status": st, "code": (body or {}).get("code"), "delayed_s": 1})
RES["steps"]["B8_replays"] = replays
chk("B8.1", "same nonce+timestamp+signature replayed after the original", "server should reject reuse (or document 12h replay window)",
    replays[0]["status"], {"code": replays[0]["code"]}, all(x["status"] == 200 for x in replays),
    {"replays": replays, "note": "nonce is only checked for blank; no nonce store exists -> unlimited replay within 12h"})

st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns?limit=1", headers=replay_h)
chk("B8.2", "replay of signed GET with a different query string still succeeds", "signature should bind the query string",
    st, body, st == 200, {"note": "verifyAdminAuth() only hashes requestURI + method + admin-* headers; query string is unsigned"})

# body is not signed either: same signed POST headers, different body
post_path = "/cms/ad/resource/list?pageIndex=1&pageSize=3"
rA = c.post(post_path, {"adPlatform": ["TikTok"]})
post_h = {k: v for k, v in c.last_headers.items() if k.startswith("admin-")}
st1, b1, _, _, _ = raw("POST", post_path, body={"adPlatform": ["TikTok"]}, headers=post_h)
st2, b2, _, _, _ = raw("POST", post_path, body={"adPlatform": ["Meta"]}, headers=post_h)
n1 = len((((b1 or {}).get("data") or {}).get("list")) or [])
n2 = len((((b2 or {}).get("data") or {}).get("list")) or [])
chk("B8.3", "signed POST replayed with a different JSON body is accepted", "body should be covered by the signature",
    st2, b2, st1 == 200 and st2 == 200, {"sameBodyRows": n1, "changedBodyRows": n2,
                                         "note": "AuthInterceptor has '// TODO add body'"})

# nonce reuse with a fresh timestamp/signature (normal client behaviour) still works
reuse_nonce = str(uuid.uuid4())
st, body, txt, ms, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(nonce=reuse_nonce))
st2, body2, txt2, ms2, _ = raw("GET", "/cms/agent/campaigns", headers=hdrs(nonce=reuse_nonce))
chk("B8.4", "nonce reuse across two independently signed requests", "nonce should be single-use",
    st2, body2, st == 200 and st2 == 200, {"nonce": reuse_nonce, "note": "no nonce table / cache in codebase"})
save()

# ======================================================== B9 input validation
print("\n=== B9 input validation ===")
sqli_q = "/cms/agent/overseas/cohort?platform=" + "TikTok'%20OR%201=1%20--&days=30&currency=USD"
st, body, txt, ms, _ = raw("GET", sqli_q, headers=hdrs(path="/cms/agent/overseas/cohort"))
chk("B9.1", "SQLi-like string in cohort platform query", "sane 200 with no injection / no 500",
    st, body, st == 200 and (body or {}).get("code") == 0, {"rows": len((((body or {}).get("data") or {}).get("cohorts")) or [])})

st, body, txt, ms, _ = raw("GET", "/cms/console/operator?name=" + "'%20OR%201=1%20--",
                            headers=hdrs(path="/cms/console/operator"))
chk("B9.2", "SQLi-like string in console operator name query", "404 unknown operator (parameterized)", st, body, st == 404,
    {"body": body})

sqli_body = {"campaign": "TT_US_'); DROP TABLE ad_resource;--", "site": "' OR 1=1 --", "title": "'; DROP TABLE ad_resource;--"}
st, body, txt, ms, _ = raw("POST", "/cms/ad/resource/list?pageIndex=1&pageSize=5", body=sqli_body,
                            headers=hdrs(method="POST", path="/cms/ad/resource/list"))
chk("B9.3", "SQLi-like strings in ad-resource list body", "200 empty/limited result, table intact", st, body,
    st == 200 and (body or {}).get("code") == 0, {"rows": len((((body or {}).get("data") or {}).get("list")) or [])})
st2, body2, txt2, ms2, _ = raw("POST", "/cms/ad/resource/list?pageIndex=1&pageSize=5", body={},
                                headers=hdrs(method="POST", path="/cms/ad/resource/list"))
total = ((body2 or {}).get("data") or {}).get("totalSize")
chk("B9.4", "ad_resource table intact after SQLi attempts", "non-zero totalSize", st2, body2,
    st2 == 200 and isinstance(total, int) and total > 0, {"totalSize": total})

xss_title = "QA-XSS <script>alert('qa')</script> <img src=x onerror=alert(1)>"
st, body, txt, ms, _ = raw("POST", "/cms/article/add",
                            body={"title": xss_title, "textList": "[]", "category": "home-fitness",
                                  "lang": "en", "insideShow": 1, "terms": "qa xss", "firstP": "QA"},
                            headers=hdrs(method="POST", path="/cms/article/add"))
art_id = ((body or {}).get("data") or {}).get("articleId")
RES["created"]["xss_article"] = {"articleId": art_id, "status": st, "title": xss_title}
chk("B9.5", "XSS payload accepted as article title", "accepted but must be stored/escaped safely (no execution server-side)",
    st, body, st == 200 and (body or {}).get("code") == 0 and bool(art_id), {"articleId": art_id})
if art_id:
    st, body, txt, ms, _ = raw("GET", "/cms/article/search/info?articleId=" + art_id,
                                headers=hdrs(path="/cms/article/search/info"))
    stored = json.dumps(body, ensure_ascii=False)
    chk("B9.6", "stored XSS title is returned as JSON text (no HTML execution path in API)", "JSON-encoded, content-type json",
        st, body, st == 200 and "script" in stored, {"stored": stored[:300],
                                                     "note": "raw <script> is stored; safety depends on console rendering/escaping"})
    st, body, txt, ms, _ = raw("POST", "/cms/article/del", body={"articleId": art_id},
                                headers=hdrs(method="POST", path="/cms/article/del"))
    chk("B9.7", "QA XSS article cleaned up", "deleted", st, body, st == 200 and (body or {}).get("code") == 0,
        {"articleId": art_id, "body": body})
    st, body, txt, ms, _ = raw("GET", "/cms/article/search/info?articleId=" + art_id,
                                headers=hdrs(path="/cms/article/search/info"))
    chk("B9.8", "deleted QA article no longer readable", "empty result", st, body,
        st == 200 and not (((body or {}).get("data") or {}).get("list") or []), {"body": body})

# pagination bounds
for cid, path, exp in [
    ("B9.9", "/cms/console/operators?pageIndex=0&pageSize=5", 400),
    ("B9.10", "/cms/console/operators?pageIndex=1&pageSize=1000", 400),
    ("B9.11", "/cms/console/operators?pageIndex=1&pageSize=100", 200),
]:
    st, body, txt, ms, _ = raw("GET", path, headers=hdrs(path="/cms/console/operators"))
    chk(cid, f"pagination bounds {path}", str(exp), st, body, st == exp, {"body": body if st >= 400 else "ok"})

st, body, txt, ms, _ = raw("GET", "/cms/agent/runs/not-a-uuid", headers=hdrs(path="/cms/agent/runs/not-a-uuid"))
chk("B9.12", "invalid UUID for run path variable", "sane 400 (no internal error masking)",
    st, body, st == 400, {"body": body, "note": "Throwable advice converts uncaught exceptions into HTTP 200 + code=-1"})

st, body, txt, ms, _ = raw("POST", "/cms/agent/overseas/currencies",
                            body={"date": "2026-02-30", "source": "ECB", "rates": {"EUR": 0.93}},
                            headers=hdrs(method="POST", path="/cms/agent/overseas/currencies"))
chk("B9.13", "invalid calendar date 2026-02-30 for FX rate", "400", st, body, st == 400, {"body": body})
st, body, txt, ms, _ = raw("POST", "/cms/agent/overseas/cohort/import",
                            body=[{"campaignId": "QA-DATE", "platform": "TikTok", "cohortDate": "2026-02-30",
                                   "ageDay": 0, "acquisitions": 1, "spendUsd": 1.0, "cumulativeRevenueUsd": 0.0}],
                            headers=hdrs(method="POST", path="/cms/agent/overseas/cohort/import"))
chk("B9.14", "invalid calendar date 2026-02-30 for cohort", "400", st, body, st == 400, {"body": body})

neg_uuid = str(uuid.uuid4())
st, body, txt, ms, _ = raw("POST", f"/cms/agent/runs/{neg_uuid}/simulate", body={"currentBudget": 0},
                            headers=hdrs(method="POST", path=f"/cms/agent/runs/{neg_uuid}/simulate"))
chk("B9.15", "simulate with zero budget -> validation 4xx (no execution)", "400", st, body, st == 400, {"body": body})
st, body, txt, ms, _ = raw("POST", f"/cms/agent/runs/{neg_uuid}/simulate", body={"currentBudget": -50.00},
                            headers=hdrs(method="POST", path=f"/cms/agent/runs/{neg_uuid}/simulate"))
chk("B9.16", "simulate with negative budget -> validation 4xx", "400", st, body, st == 400, {"body": body})
st, body, txt, ms, _ = raw("POST", f"/cms/agent/runs/{neg_uuid}/execute",
                            body={"currentBudget": -1, "expectedRevision": 1},
                            headers=hdrs(method="POST", path=f"/cms/agent/runs/{neg_uuid}/execute"))
chk("B9.17", "execute with negative budget -> 4xx before any mutation", "400", st, body, st == 400, {"body": body})

# 20MB JSON body
big = "A" * (20 * 1024 * 1024)
big_body = json.dumps({"date": "2026-11-23", "source": big, "rates": {"EUR": 0.94}}).encode("utf-8")
st, body, txt, ms, hs = raw("POST", "/cms/agent/overseas/currencies", raw_body=big_body,
                             headers=hdrs(method="POST", path="/cms/agent/overseas/currencies"), timeout=180)
chk("B9.18", "20MB JSON body rejected without 500/OOM", "sane 4xx (or 413)", st, body, st in (400, 413, 414),
    {"bytes": len(big_body), "ms": round(ms), "contentType": hs.get("Content-Type"),
     "note": "server keeps running; oversized field rejected by text() length guard"})
st, body, txt, ms, _ = raw("GET", "/api/service/health", timeout=20)
chk("B9.19", "server healthy after oversized body", "200 ok", st, body, st == 200, {"body": body})

# header-injection / weird content types on JSON endpoints
st, body, txt, ms, _ = raw("POST", "/cms/agent/overseas/currencies",
                            raw_body=b'{"date":"2026-11-23","source":"ECB","rates":{"EUR":"0.94"}}',
                            headers={**hdrs(method="POST", path="/cms/agent/overseas/currencies"),
                                     "Content-Type": "application/json"})
chk("B9.20", "string-typed rate value rejected (type confusion)", "400", st, body, st == 400, {"body": body})
st, body, txt, ms, _ = raw("POST", "/cms/agent/overseas/currencies", raw_body=b"not-json",
                            headers={**hdrs(method="POST", path="/cms/agent/overseas/currencies"),
                                     "Content-Type": "application/json"})
chk("B9.21", "malformed JSON body -> 400 (not 500)", "400", st, body, st == 400, {"body": body})
save()

# ============================================================ B11 public API
print("\n=== B11 public API + local-api console ===")
site = "cccooperate"
h_site = {"Current-Site": site}
pub = {}
for cid, path in [
    ("B11.1", "/api/article/home/list"),
    ("B11.2", "/api/article/search/list?searchKeyword=treadmill"),
    ("B11.3", "/api/category/list"),
    ("B11.4", "/api/config/get?name=afs-placement-020"),
    ("B11.5", "/api/ip/get?ip=203.0.113.77"),
]:
    st, body, txt, ms, hs = raw("GET", path, headers={**h_site, "Accept": "application/json"})
    ok = st == 200 and (body or {}).get("code") == 0
    pub[path] = {"status": st, "code": (body or {}).get("code"), "rows": (body or {}).get("data")}
    chk(cid, f"public GET {path}", "200 code 0", st, body, ok,
        {"payload": str(body)[:300], "contentType": hs.get("Content-Type")})

st, body, txt, ms, _ = raw("POST", "/api/feedback/add",
                            body={"value": "QA feedback: foldable treadmill page useful", "rate": 5, "locale": "de-DE"},
                            headers={**h_site, "Content-Type": "application/json"})
chk("B11.6", "public POST /api/feedback/add", "200 code 0", st, body, st == 200 and (body or {}).get("code") == 0, {"body": body})
st, body, txt, ms, _ = raw("POST", "/api/keyword/report",
                            body={"articleId": "afs-article-001", "keyword": "qa foldable treadmill"},
                            headers={**h_site, "Content-Type": "application/json"})
chk("B11.7", "public POST /api/keyword/report", "200 code 0", st, body, st == 200 and (body or {}).get("code") == 0, {"body": body})
st, body, txt, ms, _ = raw("POST", "/api/ip/report", body={"ip": "203.0.113.77"},
                            headers={**h_site, "Content-Type": "application/json"})
chk("B11.8", "public POST /api/ip/report", "200 code 0", st, body, st == 200 and (body or {}).get("code") == 0, {"body": body})
st, body, txt, ms, _ = raw("GET", "/api/ip/get?ip=203.0.113.77", headers=h_site)
chk("B11.9", "ip report round-trips through /api/ip/get", "todayReport=true", st, body,
    st == 200 and ((body or {}).get("data") or {}).get("todayReport") is True, {"body": body})

for cid, method, path, payload in [
    ("B11.10", "GET", "/cms/console/local-api", None),
    ("B11.11", "POST", "/cms/console/local-api", {"kind": "config", "site": site, "name": "qa_probe", "value": "1"}),
]:
    st, body, txt, ms, hs = raw(method, path, body=payload,
                                 headers={**hdrs(method=method, path=path), "Content-Type": "application/json"})
    chk(cid, f"{method} {path} (documented 'API 数据管理' endpoint)", "200 with settings+events",
        st, body, st == 200, {"status": st, "snippet": (txt or "")[:200],
                              "note": "LocalContentApi is never registered: com.afs.agent is not component-scanned and "
                                      "LocalWorkspaceConfiguration @Import list omits LocalContentApi"})

st, body, txt, ms, _ = raw("GET", "/cms/console/log-report", headers=hdrs(path="/cms/console/log-report"))
chk("B11.12", "adjacent console endpoint /cms/console/log-report still registered", "200", st, body, st == 200,
    {"code": (body or {}).get("code")})
save()

# ================================================== B12 headers / exposure
print("\n=== B12 headers & exposure ===")
security_headers = ["Content-Security-Policy", "X-Frame-Options", "X-Content-Type-Options", "Strict-Transport-Security",
                    "Referrer-Policy", "Permissions-Policy", "X-XSS-Protection"]
probes = {}
for path, hd in [("/console", {}), ("/index.html", {}),
                 ("/api/article/home/list", {"Current-Site": site}),
                 ("/cms/agent/campaigns", hdrs(path="/cms/agent/campaigns"))]:
    st, body, txt, ms, hs = raw("GET", path, headers=hd)
    probes[path] = {"status": st, "headers": {h: hs.get(h) for h in security_headers},
                    "contentType": hs.get("Content-Type")}
RES["steps"]["B12_headers"] = probes
present = {h for p in probes.values() for h, v in p["headers"].items() if v}
chk("B12.1", "security headers on console HTML and API responses", "CSP + X-Frame-Options + X-Content-Type-Options",
    probes["/console"]["status"], {"headers": probes["/console"]["headers"]},
    {"Content-Security-Policy", "X-Frame-Options", "X-Content-Type-Options"}.issubset(present),
    {"present": sorted(present), "note": "CSP exists only as a <meta> tag inside index.html; no HTTP response headers anywhere"})

for cid, path, hd in [
    ("B12.2", "/actuator", {}), ("B12.3", "/actuator/env", {}), ("B12.4", "/actuator/health", {}),
    ("B12.5", "/h2-console/", {}), ("B12.6", "/h2-console/login.do", {}),
]:
    st, body, txt, ms, hs = raw("GET", path, headers=hd)
    chk(cid, f"exposure probe {path}", "404 (not exposed)", st, body, st == 404,
        {"snippet": (txt or "")[:120], "note": "404 is rewritten to the SPA shell by an ErrorPage -> /index.html"})

st, body, txt, ms, hs = raw("GET", "/error")
chk("B12.7", "/error requires auth (error dispatch passes through AuthInterceptor)", "should render error JSON without auth",
    st, body, st == 401, {"body": body})

media_files = sorted(Path("data/media").glob("*")) if Path("data/media").exists() else []
if media_files:
    name = media_files[0].name
    st, body, txt, ms, hs = raw("GET", "/media/" + name)
    chk("B12.8", "existing /media file is served", "200", st, body, st == 200, {"file": name, "bytes": len(txt or "")})
    traversal = [
        ("/media/../data/local-admin.key", 404),
        ("/media/..%2f..%2fdata%2flocal-admin.key", 400),
        ("/media/%2e%2e/%2e%2e/data/local-admin.key", 400),
        ("/media/..%5c..%5cdata%5clocal-admin.key", 400),
        ("/media/%252e%252e/%252e%252e/data/local-admin.key", 404),
    ]
    tr_results = []
    for path, exp in traversal:
        st, body, txt, ms, hs = raw("GET", path)
        tr_results.append({"path": path, "status": st, "blocked": st in (400, 404),
                           "leaked": "local-admin" in (txt or "") or "admin-signature" in (txt or "")})
    RES["steps"]["B12_traversal"] = tr_results
    chk("B12.9", "directory traversal on /media/** cannot read data/local-admin.key", "400/404, no key material",
        tr_results[0]["status"], {"results": tr_results},
        all(x["blocked"] and not x["leaked"] for x in tr_results))
    # media is served without authentication
    st, body, txt, ms, hs = raw("GET", "/media/" + name, headers={"admin-name": ""})
    chk("B12.10", "/media/** is reachable without any auth headers", "public (documented) or protected",
        st, body, st == 200, {"note": "AfsConfig registers /media/** resource handler; HandlerInterceptor "
                                       "skips non-HandlerMethod requests -> uploads are world-readable on localhost"})
else:
    chk("B12.8", "/media sample file", "file present", -1, {}, False, {"note": "data/media empty"})
save()
print("\nB done ->", OUT / "overseas-sec_B.json")
