"""Read-only endpoint smoke sweep for the running local CMS.

Every call is a GET or an explicitly read-only POST. Results are printed as a
compact table and written to tools/qa/out/smoke.json for the final report.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient, qs

OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)

c = CmsClient()

# discover a real campaign id first
listing = c.post("/cms/ad/resource/list?pageIndex=1&pageSize=5", {"adPlatform": ["TikTok"]})
campaign_id = ""
rules = []
if listing.code == 0:
    rows = listing.data.get("list") or []
    if rows:
        campaign_id = rows[0].get("adResourceId") or ""
rules_resp = c.post("/cms/ad/resource/rule/list?pageIndex=1&pageSize=5", {})
if rules_resp.code == 0:
    rules = rules_resp.data.get("list") or []

checks = [
    ("GET", "/cms/console/bootstrap", None),
    ("GET", "/cms/user/login", None),
    ("GET", "/cms/user/profile", None),
    ("GET", "/cms/user/list", None),
    ("GET", "/cms/site/list", None),
    ("GET", "/cms/lang/list", None),
    ("GET", "/cms/region/list", None),
    ("GET", "/cms/dir/list", None),
    ("GET", "/cms/category/list", None),
    ("GET", "/cms/strategy/list", None),
    ("GET", "/cms/am/list", None),
    ("GET", qs("/cms/console/operators", pageIndex=1, pageSize=10), None),
    ("GET", qs("/cms/console/operator", name="alex.chen"), None),
    ("GET", qs("/cms/console/ad-accounts", pageIndex=1, pageSize=10), None),
    ("GET", "/cms/console/campaign-choices", None),
    ("GET", "/cms/console/channel-credentials", None),
    ("GET", "/cms/console/channel-credentials/ai", None),
    ("GET", "/cms/console/channel-credentials/oss", None),
    ("GET", "/cms/console/report-defaults", None),
    ("GET", "/cms/console/report-options", None),
    ("GET", "/cms/console/public-ad-cases", None),
    ("GET", "/cms/console/local-api", None),
    ("GET", "/cms/console/archive-data", None),
    ("GET", "/cms/console/log-report", None),
    ("GET", "/cms/console/maintenance/options", None),
    ("GET", "/cms/console/maintenance/daily", None),
    ("GET", "/cms/ad/material/category/list", None),
    ("GET", qs("/cms/ad/resource/platform/adg/list", adResourceId=campaign_id), None),
    ("GET", qs("/cms/ad/resource/search/info", adResourceId=campaign_id[:8]), None),
    ("GET", qs("/cms/ad/resource/search/info/campaign", campaign="TT"), None),
    ("GET", qs("/cms/ad/resource/rule/log", adResourceId=campaign_id), None),
    ("GET", "/cms/adsense/account/pubId/list", None),
    ("GET", "/cms/agent/campaigns", None),
    ("GET", qs("/cms/agent/campaigns", platform="TikTok", limit=20), None),
    ("GET", "/cms/agent/status", None),
    ("GET", "/cms/agent/runs", None),
    ("GET", "/cms/agent/safety", None),
    ("GET", "/cms/agent/telemetry", None),
    ("GET", "/cms/agent/runs/scenarios", None),
    ("GET", "/cms/agent/chat/sessions", None),
    ("GET", "/cms/agent/ext/patrol", None),
    ("GET", "/cms/agent/ext/briefs", None),
    ("GET", "/cms/agent/ext/creatives/fatigue", None),
    ("GET", "/cms/agent/ext/creatives/drafts", None),
    ("GET", "/cms/agent/ext/creatives/ab-tests", None),
    ("GET", "/cms/agent/ext/attribution", None),
    ("GET", "/cms/agent/ops/funnel", None),
    ("GET", "/cms/agent/ops/funnel?platform=Meta", None),
    ("GET", "/cms/agent/ops/pacing", None),
    ("GET", "/cms/agent/ops/alerts/rules", None),
    ("GET", "/cms/agent/ops/alerts/events", None),
    ("GET", "/cms/agent/ops/dayparting", None),
    ("GET", "/cms/agent/overseas/accounts", None),
    ("GET", "/cms/agent/overseas/landing", None),
    ("GET", "/cms/agent/overseas/experiments", None),
    ("GET", "/cms/agent/overseas/cohort", None),
    ("GET", "/cms/agent/overseas/currencies", None),
    ("GET", "/cms/agent/overseas/automation/rules", None),
    ("GET", "/cms/agent/overseas/automation/logs", None),
    ("GET", "/cms/agent/overseas/automation/schedule", None),
    ("GET", "/cms/agent/overseas/localization/drafts", None),
    ("GET", "/daily/report", None),
    ("GET", "/api/service/health", None),
    ("GET", "/api/article/home/list?site=cccooperate&size=3", None),
    ("GET", "/api/category/list?site=cccooperate", None),
    ("GET", "/api/config/get?name=placement-020&site=cccooperate", None),
    ("GET", "/api/ip/get?ip=203.0.113.77", None),
    ("POST", "/cms/agent/scan", {"targetRoas": 1.25, "minimumSpend": 30}),
    ("POST", "/cms/agent/replay", {"targetRoas": 1.25, "minimumSpend": 30, "minimumClicks": 30, "maxChangePercent": 10, "limit": 20}),
    ("POST", "/cms/agent/five-agent/allocate", {"maxChangePercent": 15, "limit": 10}),
    ("POST", "/cms/agent/ext/scan-interpret", {"contextText": "3 个活动 ROAS 低于 1.0"}),
    ("POST", "/cms/agent/ext/csv-analyze", {"platform": "TikTok", "csvText": "campaign,spend,revenue,clicks,conversions\nTT_US_HomeSecurity_Conv_250,120.40,198.30,940,118\n"}),
]

results = []
for method, path, body in checks:
    extra_headers = {"Current-Site": "cccooperate"} if path.startswith("/api/") and "/service/health" not in path else None
    r = c.call(method, path, body, headers=extra_headers)
    entry = {
        "method": method,
        "path": path,
        "status": r.status,
        "code": r.code,
        "ms": round(r.elapsed_ms, 1),
        "msg": (r.body or {}).get("msg") if isinstance(r.body, dict) else None,
        "bytes": len(r.text or ""),
    }
    results.append(entry)
    flag = "OK " if r.ok and r.code == 0 else "!! "
    print(f"{flag}{method:4} {path[:78]:78} {r.status:3} code={r.code} {r.elapsed_ms:7.0f}ms"
          + (f" msg={entry['msg']}" if entry["msg"] else ""))
    if r.code != 0 and r.body is not None:
        print("      ", json.dumps(r.body, ensure_ascii=False)[:300])

(OUT / "smoke.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"\n{sum(1 for x in results if x['code']==0)}/{len(results)} passed -> {OUT/'smoke.json'}")
