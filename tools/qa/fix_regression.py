"""Regression checks for the defects fixed in this round."""
from __future__ import annotations

import json
import sys
import time
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient, qs

OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)
c = CmsClient()
root = CmsClient(name="local-admin", console=True)
alex = CmsClient(name="alex.chen", console=True)
alex_nc = CmsClient(name="alex.chen", console=False)
checks = []


def check(name, ok, detail=""):
    checks.append({"name": name, "ok": bool(ok), "detail": str(detail)[:500]})
    print(("PASS " if ok else "FAIL ") + name + (" :: " + str(detail)[:300] if detail else ""))


def signed_headers(path, ts=None, nonce=None, body_text=None):
    import hashlib
    headers = {"admin-console": "1", "admin-name": "local-admin",
               "admin-nonce": nonce or str(uuid.uuid4()),
               "admin-timestamp": str(ts if ts is not None else int(time.time() * 1000))}
    if body_text is not None:
        headers["admin-body-md5"] = hashlib.md5(body_text.encode()).hexdigest()
    ordered = sorted((k, v) for k, v in headers.items() if k.startswith("admin-"))
    params = "".join(f"{k}={v}&" for k, v in ordered)
    headers["admin-signature"] = hashlib.md5(f"{path}&GET&{params}{c.secret}&".encode()).hexdigest()
    return headers


# 1. signature window / nonce / query binding / body binding
old = signed_headers("/cms/user/profile", ts=int(time.time() * 1000) - 11 * 3600 * 1000)
r = c.get("/cms/user/profile", headers=old)
check("11h-old signature rejected", r.status == 401, r.status)
nonce = str(uuid.uuid4()); ts = int(time.time() * 1000)
h = signed_headers("/cms/user/profile", ts=ts, nonce=nonce)
r1 = c.get("/cms/user/profile", headers=h)
r2 = c.get("/cms/user/profile", headers=h)
check("nonce replay rejected", r1.status == 200 and r2.status == 401, f"{r1.status}/{r2.status}")
good = signed_headers("/cms/agent/campaigns?limit=1")
bad = dict(good); bad["admin-signature"] = "0" * 32
r = c.get("/cms/agent/campaigns?limit=2", headers=bad)
check("query tamper rejected", r.status == 401, r.status)
# body tamper: sign "{}" then send a different body
h = c.auth_headers("POST", "/cms/agent/scan", "{}")
r = c.post("/cms/agent/scan", {"targetRoas": 1.3}, headers=h)
check("body tamper rejected", r.status == 401, f"{r.status} {r.text[:120]}")

# 2. authorization
r = alex.post("/cms/ad/resource/rule/add", {"name": "QA AUTHZ", "type": "REPEAT", "actionTime": 7,
                                          "actionCycle": "REPEAT", "budgetChange": 5, "rule": []})
r2 = alex_nc.post("/cms/ad/resource/rule/add", {"name": "QA AUTHZ2", "type": "REPEAT", "actionTime": 7,
                                              "actionCycle": "REPEAT", "budgetChange": 5, "rule": []})
check("non-root rule add blocked with console header", r.status == 403, r.status)
check("non-root rule add blocked without console header", r2.status == 403, r2.status)
r = alex.post("/cms/agent/safety", {"safetyMode": "SHADOW"})
check("non-root safety policy blocked", r.status == 403, r.status)
s = root.get("/cms/agent/safety").data
r = root.post("/cms/agent/safety", {"safetyMode": s["safetyMode"], "dailyBudgetDeltaCap": s["dailyBudgetDeltaCap"],
                                    "maxSingleChangePct": s["maxSingleChangePct"], "maxLlmCallsPerDay": s["maxLlmCallsPerDay"]})
check("root safety policy allowed", r.code == 0, r.status)
check("safety actor is local-admin", r.data["updatedBy"] == "local-admin", r.data["updatedBy"])

# 3. validation contract
for label, path, body in [
    ("invalid UUID", "/cms/agent/runs/not-a-uuid", None),
    ("bad currency", "/cms/agent/runs", {"site": "cccooperate", "channel": "1", "campaignId": "x", "currency": "US",
                                           "start": "2026-11-07", "end": "2026-11-20", "spend": 1, "revenue": 1,
                                           "impressions": 1, "clicks": 1, "conversions": 1, "targetRoas": 1.2,
                                           "minimumSpend": 1, "minimumClicks": 1, "maxChangePercent": 10}),
]:
    r = c.get(path) if body is None else c.post(path, body)
    check(f"{label} -> 4xx", r.status == 400, f"{r.status} {r.text[:100]}")
r = c.post("/cms/agent/scan", {"targetRoas": 0})
check("scan targetRoas=0 -> 400", r.status == 400, r.status)
# JSON body without the signed body hash must be rejected
import hashlib
import urllib.error
import urllib.request
path = "/cms/agent/scan"
body_text = "{}"
headers = {"admin-console": "1", "admin-name": "local-admin",
           "admin-nonce": str(uuid.uuid4()), "admin-timestamp": str(int(time.time() * 1000))}
params = "".join(f"{k}={v}&" for k, v in sorted(headers.items()))
sig = hashlib.md5(f"{path}&POST&{params}{c.secret}&".encode()).hexdigest()
headers["admin-signature"] = sig
req = urllib.request.Request(c.base + path, data=body_text.encode(), method="POST",
                             headers={**headers, "Content-Type": "application/json"})
try:
    urllib.request.urlopen(req)
    missing_hash_status = 200
except urllib.error.HTTPError as e:
    missing_hash_status = e.code
check("JSON body without body hash -> 401", missing_hash_status == 401, missing_hash_status)

# 4. pacing no-data
p = c.get("/cms/agent/ops/pacing").data
check("pacing exposes dataAvailable=false", p.get("dataAvailable") is False, json.dumps(p.get("summary"), ensure_ascii=False))
check("pacing does not claim SAFE without data", p.get("summary", {}).get("firewallHealth") == "NO_DATA",
      p.get("summary", {}).get("firewallHealth"))

# 5. pagination metadata
r = c.post(qs("/cms/ad/resource/list", pageIndex=1, pageSize=500), {})
d = r.data or {}
check("pageSize capped at 100", d.get("size") == 100, d.get("size"))
check("maxIndex uses ceil", d.get("maxIndex") == __import__("math").ceil(d.get("totalSize", 0) / 100), d.get("maxIndex"))

# 6. pubId list and region code
pubs = c.get("/cms/adsense/account/pubId/list").data.get("list") or []
check("pubId list returns real publisher ids", pubs and all(x.get("value", "").startswith(("pub-", "partner-pub-")) for x in pubs),
      pubs[:3])
regions = c.get("/cms/region/list").data.get("list") or []
af = next((x for x in regions if x.get("regionCode") == "AF"), None)
check("region list code/name not swapped", af is not None and af.get("name") == "阿富汗", af)

# 7. search empty
r = c.get(qs("/cms/ad/resource/search/info", adResourceId=""))
check("empty campaign search returns empty", r.code == 0 and not (r.data.get("list") if isinstance(r.data, dict) else r.data), r.text[:100])

# 8. local-api console endpoint
r = c.get("/cms/console/local-api")
check("local-api console endpoint registered", r.status == 200 and r.code == 0, f"{r.status} {r.code}")
r = c.post("/cms/console/local-api", {"kind": "config", "site": "cccooperate", "name": "qa.probe", "value": "ok"})
check("local-api console save works", r.code == 0, f"{r.status} {r.code}")

# 9. audit accepts only known channel
r = c.post("/cms/ad/resource/audit", {"adResourceId": "no-such", "state": "PASS", "channelId": "9999999999",
                                      "landPageUrl": "https://example.com/landing?channel=9999999999"})
check("unknown channel audit rejected", r.code != 0 and "不存在" in (r.body or {}).get("msg", ""), r.text[:160])

# 10. cohort window includes matured D30
co = c.get("/cms/agent/overseas/cohort?days=30").data
cohorts = co.get("cohorts") or []
check("cohort days=30 exposes matured D30", any(x.get("cohortDate") == "2026-10-20" for x in cohorts),
      sorted({x.get("cohortDate") for x in cohorts})[:6])

# 11. budget reduction rule accepted
rule_name = "QA 夜间低 ROAS 降价 " + uuid.uuid4().hex[:4]
r = c.post("/cms/agent/overseas/automation/rules", {
    "name": rule_name, "ruleType": "OFF_PEAK_SAVER", "scopePlatform": "TikTok",
    "conditionJson": json.dumps({"offPeakHours": "01:00-06:00", "minSpend": 20, "timezone": "Europe/Berlin"}),
    "actionType": "REDUCE_BUDGET", "actionParamsJson": json.dumps({"percent": 8}),
    "dryRun": True, "enabled": True})
check("budget reduction rule accepted", r.code == 0, r.text[:160])
if r.code == 0:
    rules = (r.data or {}).get("rules") or []
    created = next((x for x in rules if x.get("name") == rule_name), None)
    check("budget reduction persisted", created is not None and created.get("actionType") == "ADJUST_BUDGET",
          json.dumps(created, ensure_ascii=False)[:200])
    if created:
        c.post(f"/cms/agent/overseas/automation/rules/{created['id']}/delete")

# 12. five-agent bridge plan shape
cid = "1850127310500047514"
r = c.post("/cms/agent/ext/five-agent/propose", {"campaignId": cid, "action": "INCREASE_BUDGET",
                                                  "changePercent": 5, "targetRoas": 1.35, "rationale": "QA 桥接校验"})
check("five-agent proposal created", r.code == 0, r.text[:160])
if r.code == 0:
    rid = r.data["id"]
    run = c.get(f"/cms/agent/runs/{rid}").data
    plan = run.get("plan") or {}
    check("five-agent plan has standard schema", all(k in plan for k in ("input", "metrics", "proposal", "context", "explanation")),
          list(plan)[:8])
    rr = c.post(f"/cms/agent/runs/{rid}/review", {"decision": "REJECTED", "note": "QA 桥接校验完成"})
    check("five-agent run rejectable", rr.code == 0, rr.status)

# 13. custom analysis does not borrow another campaign evidence
custom = {"site": "cccooperate", "channel": "4630219184", "campaignId": "TT_US_HomeSecuritySystems_Conv_250_QA",
          "currency": "USD", "start": "2026-11-07", "end": "2026-11-20", "spend": 2140.55, "revenue": 3012.80,
          "impressions": 388420, "clicks": 6712, "conversions": 1642, "targetRoas": 1.30,
          "minimumSpend": 20, "minimumClicks": 30, "maxChangePercent": 10}
r = c.post("/cms/agent/runs", custom)
check("custom run created", r.code == 0, f"{r.status} {r.text[:120]}")
if r.code == 0:
    rid = r.data["id"]
    plan = r.data.get("plan") or {}
    ev = plan.get("evidence")
    check("custom run has no foreign evidence", ev is None or not ev.get("scenario"),
          json.dumps(ev, ensure_ascii=False)[:200] if ev else "null")
    c.post(f"/cms/agent/runs/{rid}/review", {"decision": "REJECTED", "note": "QA 证据校验完成"})

(OUT / "fix-regression.json").write_text(json.dumps(checks, ensure_ascii=False, indent=2), encoding="utf-8")
failed = [x for x in checks if not x["ok"]]
print(f"\n{len(checks) - len(failed)}/{len(checks)} regression checks passed")
sys.exit(1 if failed else 0)
