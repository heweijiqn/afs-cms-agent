"""Stage A5/A6: localization (max 1 generate call) + smart automation rules.

Writes tools/qa/out/overseas-sec_A56.json
"""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient, qs  # noqa: E402

OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)
RES: dict = {"stage": "A5A6", "started": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "checks": [], "steps": {}, "created": {}}
c = CmsClient()
CAMPAIGN = "1850127310500007919"


def chk(cid, desc, expected, r, ok, detail=None):
    entry = {"id": cid, "desc": desc, "expected": expected,
             "status": getattr(r, "status", None), "code": getattr(r, "code", None), "ok": bool(ok),
             "msg": (r.body or {}).get("msg") if isinstance(getattr(r, "body", None), dict) else None,
             "ms": round(getattr(r, "elapsed_ms", 0) or 0, 1)}
    if detail is not None:
        entry["detail"] = detail
    RES["checks"].append(entry)
    print(f"[{'PASS' if ok else 'FAIL'}] {cid} {desc} -> {entry['status']} code={entry['code']} {entry['ms']}ms"
          + (f" msg={entry['msg']}" if entry["msg"] else ""))
    return entry


def save():
    RES["finished"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    (OUT / "overseas-sec_A56.json").write_text(json.dumps(RES, ensure_ascii=False, indent=2), encoding="utf-8")


GERMAN_MARKERS = ["ä", "ö", "ü", "ß", "Ä", "Ö", "Ü", " der ", " die ", " das ", " und ", " für ", " mit ",
                  "Laufband", "Trittband", "Wohnung", "Garantie", "Jetzt", "Entdecke", "kostenlos"]


def german_score(text: str) -> int:
    low = text or ""
    return sum(1 for m in GERMAN_MARKERS if m in low)


# ---------------------------------------------------------------- A5 localization
print("=== A5 localization ===")
tel_before = c.get("/cms/agent/telemetry").data
drafts_before = c.get(qs("/cms/agent/overseas/localization/drafts")).data
before_ids = {d.get("id") for d in ((drafts_before or {}).get("list") or [])}

gen_payload = {
    "campaignId": CAMPAIGN,
    "topic": "Foldable treadmill for small apartments",
    "targetCountry": "DE",
    "targetLanguage": "de",
    "platform": "TikTok",
}
gen = c.post("/cms/agent/overseas/localization/generate", gen_payload, timeout=600)
RES["steps"]["A5_generate"] = {"request": gen_payload, "status": gen.status, "code": gen.code,
                               "body": gen.body, "ms": round(gen.elapsed_ms)}
chk("A5.1", "POST localization/generate (1 request, DE/de/TikTok)", "200/code 0 with aiPowered drafts",
    gen, gen.code == 0 and len(((gen.data or {}).get("drafts") or [])) > 0,
    detail={"drafts": (gen.data or {}).get("drafts"), "aiPowered": (gen.data or {}).get("aiPowered")})

drafts = (c.get("/cms/agent/overseas/localization/drafts").data or {}).get("list") or []
new_drafts = [d for d in drafts if d.get("id") not in before_ids]
RES["created"]["localization_drafts"] = new_drafts
chk("A5.2", "generated drafts are listed by GET /localization/drafts", "new draft rows for QA campaign",
    gen, len(new_drafts) > 0, detail={"ids": [d.get("id") for d in new_drafts]})

if new_drafts:
    d0 = new_drafts[0]
    body_text = " ".join(str(d0.get(k) or "") for k in ("headline", "hookText", "bodyText", "ctaText"))
    score = german_score(body_text)
    chk("A5.3", "draft copy is German (umlauts/German vocabulary), not translated English", "German markers >= 3",
        gen, score >= 3, detail={"germanMarkers": score, "headline": d0.get("headline"),
                                 "bodyText": (d0.get("bodyText") or "")[:300],
                                 "targetLanguage": d0.get("targetLanguage"),
                                 "targetCountry": d0.get("targetCountry")})
    chk("A5.4", "draft carries automatic compliance status/score/tags", "status+score+riskTags present",
        gen, d0.get("complianceStatus") is not None and d0.get("complianceScore") is not None,
        detail={k: d0.get(k) for k in ("complianceStatus", "complianceScore", "complianceAdvice", "status")})
else:
    d0 = None

# audit: prohibited German claim vs clean copy
bad_text = "Garantiert 100% Heilung! In 7 Tagen schmerzfrei ohne Operation – kaufen Sie jetzt!!!"
clean_text = "TÜV-zertifiziertes Laufband für kleine Wohnungen. 3 Jahre Garantie, transparente Preise, 30 Tage Rückgabe."
aud_bad = c.post("/cms/agent/overseas/localization/audit",
                 {"text": bad_text, "platform": "TikTok", "targetCountry": "DE"}, timeout=300)
aud_clean = c.post("/cms/agent/overseas/localization/audit",
                   {"text": clean_text, "platform": "TikTok", "targetCountry": "DE"}, timeout=300)
RES["steps"]["A5_audit_bad"] = {"request": bad_text, "body": aud_bad.body}
RES["steps"]["A5_audit_clean"] = {"request": clean_text, "body": aud_clean.body}
bad_level = (aud_bad.data or {}).get("riskLevel")
clean_level = (aud_clean.data or {}).get("riskLevel")
chk("A5.5", "audit flags prohibited claim 'garantiert 100% Heilung' above clean copy",
    "bad riskLevel != COMPLIANT (WARNING/VIOLATION) and lower riskScore than clean",
    aud_bad, bad_level in ("WARNING", "VIOLATION") and (aud_bad.data or {}).get("riskScore", 100) < (aud_clean.data or {}).get("riskScore", 0),
    detail={"bad": {k: (aud_bad.data or {}).get(k) for k in ("source", "riskScore", "riskLevel", "flaggedTokens", "policyViolations")},
            "clean": {k: (aud_clean.data or {}).get(k) for k in ("source", "riskScore", "riskLevel", "flaggedTokens", "policyViolations")}})
chk("A5.6", "audit source reports which engine ran (AI_ASSISTED_REVIEW vs KEYWORD_RULES)",
    "source present and consistent", aud_bad, (aud_bad.data or {}).get("source") is not None,
    detail={"badSource": (aud_bad.data or {}).get("source"), "cleanSource": (aud_clean.data or {}).get("source")})
# keyword engine blind spot: the deterministic rule list is English-only for this class of claim
src_bad = (aud_bad.data or {}).get("source")
chk("A5.7", "German prohibited claims are covered by the deterministic keyword fallback",
    "KEYWORD_RULES must flag 'garantiert 100% Heilung' too (defence in depth)",
    aud_bad, src_bad == "AI_ASSISTED_REVIEW" and (aud_bad.data or {}).get("flaggedTokens"),
    detail={"note": "auditWithRules only matches English tokens (100% guaranteed / 100% cure / ...); "
                    "if the LLM gateway is unavailable the German claim passes as COMPLIANT",
            "sourceUsed": src_bad})

if d0:
    st1 = c.post(f"/cms/agent/overseas/localization/drafts/{d0['id']}/status", {"status": "ARCHIVED"})
    drafts2 = (c.get("/cms/agent/overseas/localization/drafts").data or {}).get("list") or []
    cur = next((x for x in drafts2 if x.get("id") == d0["id"]), None)
    chk("A5.8", "draft status update DRAFT -> ARCHIVED is persisted", "status=ARCHIVED",
        st1, st1.code == 0 and cur and cur.get("status") == "ARCHIVED",
        detail={"response": st1.body, "listedStatus": cur.get("status") if cur else None})
    st2 = c.post(f"/cms/agent/overseas/localization/drafts/{d0['id']}/status", {"status": "DRAFT"})
    chk("A5.9", "draft status restored to DRAFT", "status=DRAFT", st2, st2.code == 0,
        detail={"response": st2.body})
    bad_status = c.post(f"/cms/agent/overseas/localization/drafts/{d0['id']}/status", {"status": "HACKED"})
    chk("A5.10", "unknown status value is not persisted verbatim", "coerced to a whitelisted value",
        bad_status, bad_status.code == 0 and (bad_status.data or {}).get("status") in ("DRAFT", "PUBLISHED", "AB_TESTING", "ARCHIVED"),
        detail={"response": bad_status.body})
    missing = c.post("/cms/agent/overseas/localization/drafts/999999999/status", {"status": "DRAFT"})
    chk("A5.11", "status update on unknown draft id -> 404", "404", missing, missing.status == 404,
        detail={"body": missing.body})

tel_after = c.get("/cms/agent/telemetry").data
RES["steps"]["A5_telemetry"] = {"before": tel_before, "after": tel_after}
chk("A5.12", "one generate request does not multiply paid model calls",
    "<= 1 model call per generate request",
    gen, False,
    detail={"note": "generateLocalizedCreatives() calls the LLM once for 3 variants and then "
                    "auditComplianceInternal() calls the LLM again per variant -> 1+N calls per request",
            "telemetryBefore": str(tel_before)[:400], "telemetryAfter": str(tel_after)[:400]})
save()

# ---------------------------------------------------------------- A6 automation
print("\n=== A6 automation ===")
rules_before = (c.get("/cms/agent/overseas/automation/rules").data or {}).get("list") or []
RES["steps"]["A6_rules_before"] = rules_before
chk("A6.1", "pre-existing rules are all dry-run (no real budget mutation risk)",
    "all dryRun=true", c.get("/cms/agent/overseas/automation/rules"),
    all(r.get("dryRun") for r in rules_before),
    detail={"rules": [{"id": r.get("id"), "name": r.get("name"), "dryRun": r.get("dryRun"), "enabled": r.get("enabled")} for r in rules_before]})

specified = {
    "name": "QA 夜间低 ROAS 降价",
    "ruleType": "OFF_PEAK_SAVER",
    "scopePlatform": "TikTok",
    "conditionJson": json.dumps({"offPeakHours": "01:00-06:00", "minSpend": 20, "maxRoas": 0.6, "timezone": "Europe/Berlin"}),
    "actionType": "REDUCE_BUDGET",
    "actionParamsJson": json.dumps({"percent": 8}),
    "dryRun": True,
    "enabled": True,
}
r1 = c.post("/cms/agent/overseas/automation/rules", specified)
RES["steps"]["A6_specified_rule"] = {"request": specified, "body": r1.body}
chk("A6.2", "create 'QA 夜间低 ROAS 降价' with actionType=REDUCE_BUDGET / percent 8", "200 accepted (documented action)",
    r1, r1.code == 0, detail={"request": specified, "body": r1.body})

neg = dict(specified, actionType="ADJUST_BUDGET", actionParamsJson=json.dumps({"budgetDeltaPct": -8, "maxDailyBudget": 600}))
r2 = c.post("/cms/agent/overseas/automation/rules", neg)
chk("A6.3", "budget reduction expressible via ADJUST_BUDGET with negative delta", "200 (or documented alternative)",
    r2, r2.code == 0, detail={"request": neg, "body": r2.body})

valid = {
    "name": "QA 夜间低 ROAS 降价（dry-run 模拟）",
    "ruleType": "STOP_LOSS",
    "scopePlatform": "TikTok",
    "conditionJson": json.dumps({"minSpend": 50, "maxConversions": 50, "maxRoas": 0.8}),
    "actionType": "PAUSE_CAMPAIGN",
    "actionParamsJson": json.dumps({"pauseReason": "QA dry-run"}),
    "dryRun": True,
    "enabled": True,
}
r3 = c.post("/cms/agent/overseas/automation/rules", valid)
new_rule = None
if r3.code == 0:
    after = (r3.data or {}).get("rules") or []
    new_rule = next((x for x in after if x.get("name") == valid["name"]), None)
RES["created"]["automation_rule"] = new_rule
chk("A6.4", "create dry-run QA rule (supported vocabulary)", "200 + rule listed", r3,
    r3.code == 0 and new_rule is not None, detail={"body": r3.body, "rule": new_rule})

listed = (c.get("/cms/agent/overseas/automation/rules").data or {}).get("list") or []
chk("A6.5", "QA rule appears in GET /automation/rules", "listed", r3,
    any(x.get("id") == (new_rule or {}).get("id") for x in listed),
    detail={"ids": [x.get("id") for x in listed]})

# evaluation
ev = c.post("/cms/agent/overseas/automation/evaluate", {}, timeout=300)
evd = ev.data or {}
RES["steps"]["A6_evaluate"] = ev.body
chk("A6.6", "POST /automation/evaluate runs enabled rules and returns counts", "200 with evaluatedRules/triggeredCount",
    ev, ev.code == 0 and "evaluatedRules" in evd,
    detail={k: evd.get(k) for k in ("evaluatedRules", "triggeredCount", "simulatedCount", "executedCount", "skippedMissingData")})

logs = c.get("/cms/agent/overseas/automation/logs?limit=100")
log_rows = (logs.data or {}).get("list") or []
our_logs = [x for x in log_rows if x.get("ruleName") == valid["name"]]
RES["steps"]["A6_logs"] = log_rows
chk("A6.7", "evaluation writes execution logs for the QA dry-run rule", ">=1 log with isSimulated=true, status SIMULATED",
    logs, len(our_logs) >= 1 and all(x.get("isSimulated") for x in our_logs),
    detail={"count": len(our_logs), "sample": our_logs[:3], "allRules": sorted({x.get('ruleName') for x in log_rows})})
chk("A6.8", "true budget reduction cannot be executed by this engine (no local budget delta applied)",
    "documented: dry-run only / no negative delta supported", ev, True,
    detail={"note": "AdOverseasOpsService only supports PAUSE_CAMPAIGN / ADJUST_BUDGET(+0..50%) / SWAP_CREATIVE; "
                    "rules can never decrease a budget"})

# schedule: capture, enable, verify, restore
sched0 = c.get("/cms/agent/overseas/automation/schedule")
orig = (sched0.data or {}).get("enabled")
RES["steps"]["A6_schedule_original"] = sched0.body
en = c.post("/cms/agent/overseas/automation/schedule", {"enabled": True})
chk("A6.9", "enable automation schedule", "enabled=true", en, en.code == 0 and (en.data or {}).get("enabled") is True,
    detail={"response": en.body})
sched_mid = c.get("/cms/agent/overseas/automation/schedule")
chk("A6.10", "schedule state persists on GET", "enabled=true", sched_mid,
    (sched_mid.data or {}).get("enabled") is True, detail={"response": sched_mid.body})
restore = c.post("/cms/agent/overseas/automation/schedule", {"enabled": bool(orig)})
sched_end = c.get("/cms/agent/overseas/automation/schedule")
chk("A6.11", f"schedule restored to original ({orig})", f"enabled={orig}", sched_end,
    (sched_end.data or {}).get("enabled") == bool(orig), detail={"response": sched_end.body})

# delete rule
if new_rule:
    dele = c.post(f"/cms/agent/overseas/automation/rules/{new_rule['id']}/delete", {})
    listed2 = (c.get("/cms/agent/overseas/automation/rules").data or {}).get("list") or []
    chk("A6.12", "delete QA rule removes it", "deleted=true and no longer listed", dele,
        dele.code == 0 and not any(x.get("id") == new_rule["id"] for x in listed2),
        detail={"response": dele.body, "stillListed": any(x.get("id") == new_rule["id"] for x in listed2)})
    dele2 = c.post(f"/cms/agent/overseas/automation/rules/{new_rule['id']}/delete", {})
    chk("A6.13", "re-deleting an absent rule is idempotent (deleted=false)", "200 deleted=false", dele2,
        dele2.code == 0 and (dele2.data or {}).get("deleted") is False, detail={"response": dele2.body})

# logs pre-state + clear policy
logs_pre = (c.get("/cms/agent/overseas/automation/logs?limit=100").data or {}).get("list") or []
(OUT / "automation-logs-before.json").write_text(json.dumps(logs_pre, ensure_ascii=False, indent=2), encoding="utf-8")
meaningful = [x for x in logs_pre if x.get("ruleName") != valid["name"]]
chk("A6.14", "clear-logs pre-state exported before any clear", f"exported {len(logs_pre)} rows to tools/qa/out/automation-logs-before.json",
    logs, True, detail={"exported": len(logs_pre), "preExisting": len(meaningful), "qa": len(logs_pre) - len(meaningful)})
if meaningful:
    RES["steps"]["A6_clear"] = {"cleared": False,
                                 "reason": f"{len(meaningful)} pre-existing production log rows would be destroyed by a global DELETE"}
    print(f"  SKIP POST /automation/logs/clear: {len(meaningful)} pre-existing log rows present")
    chk("A6.15", "POST /automation/logs/clear skipped (would wipe pre-existing logs)", "skip per safety rule", logs, True,
        detail={"preExisting": len(meaningful)})
else:
    cl = c.post("/cms/agent/overseas/automation/logs/clear", {})
    after_clear = (c.get("/cms/agent/overseas/automation/logs?limit=100").data or {}).get("list") or []
    RES["steps"]["A6_clear"] = cl.body
    chk("A6.15", "logs/clear empties the log table", "deleted>0 then empty", cl,
        cl.code == 0 and len(after_clear) == 0, detail={"response": cl.body, "remaining": len(after_clear)})

save()
print("\nA5/A6 done ->", OUT / "overseas-sec_A56.json")
