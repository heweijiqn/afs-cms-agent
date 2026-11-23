"""Merge stage evidence into tools/qa/out/overseas-sec.json + overseas-sec.md."""
from __future__ import annotations

import json
from pathlib import Path

OUT = Path(__file__).resolve().parent / "out"
FILES = ["overseas-sec_A.json", "overseas-sec_A56.json", "overseas-sec_A56b.json", "overseas-sec_A56c.json",
         "overseas-sec_B.json", "overseas-sec_B7b.json", "overseas-sec_B10.json", "overseas-sec_state.json"]

SUPERSEDED = {("overseas-sec_B.json", "B7.12"), ("overseas-sec_B.json", "B8.3")}

checks: list[dict] = []
steps: dict = {}
created: dict = {}
for name in FILES:
    p = OUT / name
    if not p.exists():
        continue
    d = json.loads(p.read_text(encoding="utf-8"))
    for c in d.get("checks") or []:
        if (name, c.get("id")) in SUPERSEDED:
            c = dict(c, superseded=True, note="superseded by corrected rerun (signature was built for the wrong path/body)")
        c["source"] = name
        checks.append(c)
    for k, v in (d.get("steps") or {}).items():
        steps[f"{name}:{k}"] = v
    for k, v in (d.get("created") or {}).items():
        created.setdefault(k, v)

latest = {}
for c in checks:
    if c.get("superseded"):
        continue
    latest[c["id"]] = c

AREA = [
    ("A1", "Read endpoints baseline", ("A1.1", "A1.2", "A1.3", "A1.4")),
    ("A2", "Dated FX rates & display conversion", tuple(f"A2.{i}" for i in range(1, 11))),
    ("A3", "Cohort import / payback / forecast", tuple(f"A3.{i}" for i in range(1, 16))),
    ("A4", "Landing & account observations, stale flags", tuple(f"A4.{i}" for i in range(1, 14))),
    ("A5", "Localization DCO + compliance audit", tuple(f"A5.{i}" for i in range(1, 15))),
    ("A6", "Smart automation rules / schedule / logs", tuple(f"A6.{i}" for i in range(1, 25))),
    ("B7", "Auth matrix & non-root authorization", ("B7.1", "B7.2", "B7.3", "B7.4", "B7.5", "B7.6", "B7.7", "B7.8",
                                                   "B7.9", "B7.10", "B7.11", "B7.12", "B7.13", "B7.13b", "B7.14",
                                                   "B7.14b", "B7.15", "B7.15b", "B7.16", "B7.16b", "B7.16c", "B7.16d",
                                                   "B7.17", "B7.18", "B7.19")),
    ("B8", "Signature replay / integrity", ("B8.1", "B8.2", "B8.3", "B8.4")),
    ("B9", "Input validation & abuse resistance", tuple(f"B9.{i}" for i in range(1, 22))),
    ("B10", "Idempotency & concurrency races", tuple(f"B10.{i}" for i in range(1, 10))),
    ("B11", "Public API & dead local-api console", tuple(f"B11.{i}" for i in range(1, 13))),
    ("B12", "Security headers, exposure, traversal", tuple(f"B12.{i}" for i in range(1, 11))),
]

matrix = []
for area, title, ids in AREA:
    rows = [latest[i] for i in ids if i in latest]
    p = sum(1 for r in rows if r.get("ok"))
    matrix.append({"area": area, "title": title, "total": len(rows), "pass": p, "fail": len(rows) - p,
                   "fail_ids": [r["id"] for r in rows if not r.get("ok")]})

DEFECTS = [
    {
        "id": "D1", "severity": "High", "area": "authz",
        "title": "Non-root user escalates to root-only console actions by omitting the admin-console header",
        "endpoint": "POST /cms/ad/resource/rule/add (all AuthInterceptor.CONSOLE_ADMIN_ACTIONS)",
        "request": "admin-name: alex.chen, admin-nonce/timestamp/signature signed with the same local key, NO admin-console header, "
                   "body {\"name\":\"QA-AUTHZ-PROBE-alex-chen\",\"type\":\"BUDGET\",\"actionCycle\":\"DAY\",\"actionTime\":1,"
                   "\"rule\":[{\"qa\":\"probe\"}],\"budgetChange\":0.0}",
        "expected": "401 or 403 - the console admin action list must be root-only regardless of an optional client header",
        "actual": "HTTP 200 code=0: rule id 257 created with creator=alex.chen (deleted afterwards). With admin-console: 1 the same call returns 403 权限不够",
        "root_cause": "AuthInterceptor: rootRequired = @RootAuth || (localWorkspace && LOCAL_MAINTENANCE_ROUTES) || "
                      "(console && CONSOLE_ADMIN_ACTIONS.contains(path)); admin-console is client supplied and optional",
        "repro": "python tools/qa/overseas_b7b.py (checks B7.16c/B7.16d)",
        "evidence": ["B7.16b", "B7.16c", "B7.16d"],
    },
    {
        "id": "D2", "severity": "High", "area": "integrity",
        "title": "Request signature does not bind the query string or the JSON body",
        "endpoint": "every /cms/** endpoint (AuthInterceptor.verifyAdminAuth)",
        "request": "capture admin-* headers from a signed GET /cms/agent/campaigns, then replay them on "
                   "/cms/agent/campaigns?limit=1 ; capture headers of POST /cms/ad/resource/list?pageIndex=1&pageSize=3 and replay with a different body",
        "expected": "a captured signature must only authorise the exact request (path + query + body)",
        "actual": "both tampers return 200; only requestURI + METHOD + admin-* headers are hashed ('// TODO add body')",
        "root_cause": "plain = requestURI & METHOD & admin-* headers & secret",
        "repro": "python tools/qa/overseas_b.py (B8.2) and tools/qa/overseas_b7b.py (B8.3)",
        "evidence": ["B8.2", "B8.3"],
    },
    {
        "id": "D3", "severity": "Medium", "area": "auth",
        "title": "No nonce replay protection - a captured signed request can be replayed for the whole 12h window",
        "endpoint": "every /cms/** endpoint",
        "request": "replay the identical captured admin-nonce/admin-timestamp/admin-signature 4x over ~1s and reuse a nonce in two independent signed calls",
        "expected": "nonce should be single-use (replay cache) so a captured request cannot be reused",
        "actual": "all replays return 200; the nonce is only checked for blank (StringUtils.isBlank) and never stored",
        "root_cause": "TODO check _n left unimplemented; window is |now-ts| <= 12h",
        "repro": "python tools/qa/overseas_b.py (B8.1, B8.4)",
        "evidence": ["B8.1", "B8.4"],
    },
    {
        "id": "D4", "severity": "Medium", "area": "api-contract",
        "title": "All uncaught exceptions / bean-validation failures / malformed JSON return HTTP 200 code=-1 'internal error'",
        "endpoint": "global ResponseAdviser.exceptionHandler (com.afs.controller.filter)",
        "request": "GET /cms/agent/runs/not-a-uuid ; POST /cms/agent/runs/{uuid}/simulate {\"currentBudget\":0} ; "
                   "POST /cms/agent/runs/{uuid}/execute {\"currentBudget\":-1} ; malformed JSON body ; 20MB JSON body",
        "expected": "400 for bad input, 413 for oversized body, 5xx for real faults",
        "actual": "HTTP 200 + {\"code\":-1,\"msg\":\"internal error\"} in every case; only ResponseStatusException keeps its status",
        "root_cause": "exceptionHandler(Throwable) returns ResponseUtils.error(-1,'internal error') with @ResponseStatus unset -> 200",
        "repro": "python tools/qa/overseas_b.py (B9.12, B9.15-B9.18, B9.21)",
        "evidence": ["B9.12", "B9.15", "B9.16", "B9.17", "B9.18", "B9.21", "B7.16b"],
    },
    {
        "id": "D5", "severity": "Medium", "area": "feature-dead",
        "title": "Documented 'API 数据管理' console endpoint is unregistered (dead code) and would not show public-API traffic anyway",
        "endpoint": "GET/POST /cms/console/local-api",
        "request": "GET /cms/console/local-api (signed) ; POST /cms/console/local-api {\"kind\":\"config\",\"site\":\"cccooperate\",\"name\":\"qa_probe\",\"value\":\"1\"}",
        "expected": "200 with {settings, events} listing local_api_setting/local_api_event rows",
        "actual": "GET -> 404 (SPA shell), POST -> 405 (static resource handler). LocalContentApi is never registered: "
                  "CmsRuntimeConfiguration @ComponentScan does not include com.afs.agent and "
                  "LocalWorkspaceConfiguration @Import omits LocalContentApi",
        "root_cause": "bean never created in local-cms; additionally the live public API writes to legacy tables "
                      "(fd / keyword_report_date / ip_info) while LocalContentApi reads local_api_event/local_api_setting",
        "repro": "python tools/qa/overseas_b.py (B11.10, B11.11); python tools/qa/smoke.py line /cms/console/local-api",
        "evidence": ["B11.10", "B11.11"],
    },
    {
        "id": "D6", "severity": "Medium", "area": "localization",
        "title": "Unknown draft status silently defaults to PUBLISHED (fail-open) and triggers ad_text sync",
        "endpoint": "POST /cms/agent/overseas/localization/drafts/{id}/status",
        "request": "{\"status\":\"HACKED\"}",
        "expected": "400 for an unsupported status (or fall back to DRAFT), never publish",
        "actual": "200 {\"status\":\"PUBLISHED\"}; syncLocalizationToAdText() inserted a row into ad_text (id 485, removed during cleanup)",
        "root_cause": "String safe = whitelist.contains(status) ? status : \"PUBLISHED\"",
        "repro": "python tools/qa/overseas_a56.py (A5.10); tools/qa/_cleanup_local.py",
        "evidence": ["A5.10"],
    },
    {
        "id": "D7", "severity": "Medium", "area": "cohort",
        "title": "Cohort lookback is age-blind: with days=30 (or the default 14) a matured D30 cohort is invisible",
        "endpoint": "GET /cms/agent/overseas/cohort",
        "request": "GET /cms/agent/overseas/cohort?platform=TikTok&days=30&currency=USD",
        "expected": "a D0-D30 analyzer must show cohorts old enough to have a D30 observation",
        "actual": "only cohorts with cohort_date >= today-30 are loaded; the 2026-10-20 cohort (D30 matured) is dropped, "
                  "so d30Roas/avgD30Roas are null under days=30; days=90 shows 11 cohorts incl. D30",
        "root_cause": "WHERE cohort_date >= today-lookback (should be cohort_date >= today-lookback-maxAge or filter on observation date)",
        "repro": "python tools/qa/overseas_a.py (A3.4, A3.5, A3.6)",
        "evidence": ["A3.4", "A3.5", "A3.6"],
    },
    {
        "id": "D8", "severity": "Medium", "area": "automation",
        "title": "Smart-rule engine cannot express a budget reduction (the requested 'night low-ROAS price cut' rule is uncreatable)",
        "endpoint": "POST /cms/agent/overseas/automation/rules",
        "request": "actionType=REDUCE_BUDGET, params {\"percent\":8} ; then actionType=ADJUST_BUDGET, params {\"budgetDeltaPct\":-8}",
        "expected": "a rule that lowers budget by 8% (documented feature: 夜间低 ROAS 降价)",
        "actual": "400 不支持的规则或动作 ; 400 预算调整幅度须在 ±50% 以内 - ADJUST_BUDGET only accepts 0..+50%, the only negative path is OFF_PEAK bid discount",
        "root_cause": "action vocabulary PAUSE_CAMPAIGN/ADJUST_BUDGET/SWAP_CREATIVE + delta range [0,50]",
        "repro": "python tools/qa/overseas_a56.py (A6.2, A6.3)",
        "evidence": ["A6.2", "A6.3"],
    },
    {
        "id": "D9", "severity": "Medium", "area": "localization",
        "title": "One generate request costs 1+N model calls and holds a DB transaction open for minutes",
        "endpoint": "POST /cms/agent/overseas/localization/generate",
        "request": "{\"campaignId\":\"1850127310500007919\",\"topic\":\"Foldable treadmill for small apartments\",\"targetCountry\":\"DE\",\"targetLanguage\":\"de\",\"platform\":\"TikTok\"}",
        "expected": "one request -> one model call (or at least bounded, non-transactional)",
        "actual": "233937 ms for one request; telemetry: overseas_localization_agent +1 and compliance_radar_agent +5 "
                  "(3 auto-audits of the 3 generated variants + 2 explicit audit calls); method is @Transactional, so a write "
                  "transaction/connection is held across every LLM round-trip",
        "root_cause": "generateLocalizedWithLlm() then auditComplianceInternal() per variant inside one @Transactional method",
        "repro": "python tools/qa/overseas_a56.py (A5.1, A5.12)",
        "evidence": ["A5.1", "A5.12"],
    },
    {
        "id": "D10", "severity": "Low", "area": "compliance",
        "title": "Deterministic compliance fallback is English-only - German prohibited claims pass when the LLM is unavailable",
        "endpoint": "POST /cms/agent/overseas/localization/audit (fallback auditWithRules)",
        "request": "{\"text\":\"Garantiert 100% Heilung! In 7 Tagen schmerzfrei ohne Operation - kaufen Sie jetzt!!!\",\"platform\":\"TikTok\",\"targetCountry\":\"DE\"}",
        "expected": "keyword screening should also flag localised health/guarantee claims",
        "actual": "with AI up: riskScore 18 / VIOLATION with 5 policy codes (AI_ASSISTED_REVIEW). The regex fallback matches only "
                  "English tokens (100% guaranteed / cure / get rich quick ...) plus an exclamation rule -> the same text would score 85 COMPLIANT",
        "root_cause": "auditWithRules() English literal list only",
        "repro": "python tools/qa/overseas_a56.py (A5.5, A5.7) + code review of AdOverseasOpsService.auditWithRules",
        "evidence": ["A5.5", "A5.7"],
    },
    {
        "id": "D11", "severity": "Low", "area": "fx",
        "title": "Missing source-currency rate silently yields displayBalance=null with no reason field",
        "endpoint": "GET /cms/agent/overseas/accounts",
        "request": "GET /cms/agent/overseas/accounts?currency=USD before any EUR/GBP/JPY rate was imported",
        "expected": "a per-row missingRate/message so the operator knows why the balance is blank",
        "actual": "displayBalance=null only; fx.rate() ResponseStatusException is swallowed by a try/catch",
        "root_cause": "OverseasEvidenceService.accounts(): catch(ResponseStatusException) { m.put(\"displayBalance\", null) }",
        "repro": "python tools/qa/overseas_a.py (A4.2 pristine, A4.3)",
        "evidence": ["A4.2", "A4.3"],
    },
    {
        "id": "D12", "severity": "Low", "area": "fx",
        "title": "Currency code normalisation is inconsistent between endpoints",
        "endpoint": "GET /cms/agent/overseas/accounts vs GET /cms/agent/overseas/cohort",
        "request": "currency=eur (lower case) on both",
        "expected": "same normalisation (accept lower case or reject everywhere)",
        "actual": "accounts -> 400 请先导入 eur 的有日期汇率 ; cohort -> 200 (upper-cases internally)",
        "root_cause": "OverseasDataService.rate() does not upper-case, cohort() does",
        "repro": "python tools/qa/overseas_a.py (A2.9, A2.10)",
        "evidence": ["A2.9", "A2.10"],
    },
    {
        "id": "D13", "severity": "Low", "area": "headers",
        "title": "No HTTP security headers on console or API responses",
        "endpoint": "/console, /index.html, /api/**, /cms/**",
        "request": "GET /console ; GET /api/article/home/list ; GET /cms/agent/campaigns",
        "expected": "Content-Security-Policy, X-Frame-Options, X-Content-Type-Options at minimum",
        "actual": "none of them; CSP exists only as a <meta http-equiv> tag inside index.html, so JSON/API responses and "
                  "framing of the console are unprotected",
        "root_cause": "no SecurityFilterChain / header writer configured (StaffSecurity disabled without require-staff-login)",
        "repro": "python tools/qa/overseas_b.py (B12.1)",
        "evidence": ["B12.1"],
    },
    {
        "id": "D14", "severity": "Low", "area": "errors",
        "title": "/error is behind AuthInterceptor - unauthenticated error dispatch returns 401 JSON",
        "endpoint": "GET /error",
        "request": "GET /error with no admin headers",
        "expected": "error rendering independent of authorization",
        "actual": "401 {\"code\":401,...}; combined with the 404->/index.html ErrorPage this makes non-SPA 404/405 responses indistinguishable",
        "root_cause": "AuthInterceptor is registered for /** with no exclude for /error",
        "repro": "python tools/qa/overseas_b.py (B12.7)",
        "evidence": ["B12.7"],
    },
    {
        "id": "D15", "severity": "Low", "area": "media",
        "title": "/media/** is served without authentication",
        "endpoint": "GET /media/{uuid}.png",
        "request": "GET /media/<existing file> with no admin headers",
        "expected": "protected or explicitly documented public",
        "actual": "200 and the full file; traversal is blocked (400/404) and the key file cannot be read",
        "root_cause": "AfsConfig resource handler; HandlerInterceptor is skipped for non-HandlerMethod requests",
        "repro": "python tools/qa/overseas_b.py (B12.8, B12.9, B12.10)",
        "evidence": ["B12.8", "B12.9", "B12.10"],
    },
    {
        "id": "D16", "severity": "Info", "area": "automation",
        "title": "Rule evaluation silently no-ops on the current day (0 triggers, success response)",
        "endpoint": "POST /cms/agent/overseas/automation/evaluate",
        "request": "{} with 5 enabled dry-run rules (incl. a QA probe with minSpend=0.01 and an OFF_PEAK rule with a full-day window)",
        "expected": "at least one trigger / an explicit 'no current-day data' signal",
        "actual": "200 {evaluatedRules:5, triggeredCount:0, simulatedCount:0, skippedMissingData:1024}; today's report rows carry "
                  "zero spend and the OFF_PEAK budget join yields 0, so no branch matches and nothing is logged. Not a code bug, "
                  "but the feature is not demonstrable on a fresh day and the API gives no reason",
        "root_cause": "evaluation depends on same-day spend/budget; no backfill and no reason aggregation",
        "repro": "python tools/qa/overseas_a56.py (A6.6) + overseas_a56b/c",
        "evidence": ["A6.6", "A6.7", "A6.17", "A6.18"],
    },
]

CREATED = {
    "fx_rates": "115 rows ad_fx_rate (23 dates 2026-11-01..2026-11-23 x USD/EUR/GBP/JPY/CNY, source ECB; values EUR 0.92->0.94, GBP 0.78->0.80, JPY 150->152, CNY 7.12->7.25)",
    "account_snapshots": "5 rows ad_account_snapshot (TikTok 7384910284751029003 USD, Facebook 1084927361040003 EUR, Taboola 1482910403 GBP, TikTok 7384910284751029002 USD restricted, Google 6428910303 JPY stale 2026-11-18)",
    "landing_observations": "4 rows ad_landing_observation (TikTok campaigns 1850127310500007919/47514/720629, 2026-11-19..23, ttfb 180-420ms, cvr 0.18-0.31)",
    "cohort_observations": "48 rows for 3 real TikTok campaigns x 3 cohort dates (ages 0,1,3,7,14,30 where matured) + 5 rows QA-COHORT-3AGES / QA-COHORT-2AGES",
    "localization_drafts": "3 rows ad_localization_draft id 1,2,3 (DE/de/TikTok, campaign 1850127310500007919) - archived",
    "automation_rules": "3 QA rules created and deleted (REDUCE_BUDGET rejected; 2 probe rules deleted). Only the 4 seeded rules remain",
    "agent_run": "1 optimization_run (scenario healthy, state SIMULATED, revision 1) - never executed/rolled back; no delete API",
    "deleted_after_use": "article QA-XSS (created+deleted), ad_text row 485 (created by status coercion, deleted), ad_resource_rule 257 (authz PoC, deleted)",
    "not_cleared": "ad_automation_execution_log: pre-existing 17 rows exported to tools/qa/out/automation-logs-before.json; clear skipped by rule. No new rows were produced by evaluation",
}


def md_matrix() -> str:
    lines = ["| Area | Scope | Checks | Pass | Fail | Failing checks |", "|---|---|---:|---:|---:|---|"]
    for m in matrix:
        lines.append(f"| {m['area']} | {m['title']} | {m['total']} | {m['pass']} | {m['fail']} | "
                     f"{', '.join(m['fail_ids']) or '-'} |")
    tot = sum(m["total"] for m in matrix)
    pas = sum(m["pass"] for m in matrix)
    lines.append(f"| **Total** |  | **{tot}** | **{pas}** | **{tot - pas}** |  |")
    return "\n".join(lines)


def md_matrix_detail() -> str:
    lines = ["| ID | Check | Expected | HTTP | code | Verdict |", "|---|---|---|---:|---:|---|"]
    for area, title, ids in AREA:
        for i in ids:
            c = latest.get(i)
            if not c:
                continue
            verdict = "PASS" if c.get("ok") else "**FAIL**"
            exp = (c.get("expected") or "").replace("|", "/")[:80]
            desc = (c.get("desc") or "").replace("|", "/")[:90]
            lines.append(f"| {i} | {desc} | {exp} | {c.get('status')} | {c.get('code')} | {verdict} |")
    return "\n".join(lines)


def md_defects() -> str:
    out = []
    for d in DEFECTS:
        out.append(f"### {d['id']} - [{d['severity']}] {d['title']}\n")
        out.append(f"- **Endpoint:** `{d['endpoint']}`")
        out.append(f"- **Exact request:** {d['request']}")
        out.append(f"- **Expected:** {d['expected']}")
        out.append(f"- **Actual:** {d['actual']}")
        out.append(f"- **Root cause:** {d['root_cause']}")
        out.append(f"- **Reproducible via:** `{d['repro']}` (evidence ids: {', '.join(d['evidence'])})")
        out.append("")
    return "\n".join(out)


md = f"""# AFS CMS - Overseas Operations & Security Test Report

Target: `http://127.0.0.1:9901` (profile `local-cms`, H2 file DB `data/cms-local`, run from `target/afs-cms.jar`)
Test window: 2026-11-23 16:45-17:10 (Asia/Shanghai) | Client: signed `tools/qa/client.py` (local-admin key)
Artifacts: `tools/qa/out/overseas-sec.json` (machine readable), this file, per-stage JSON (`overseas-sec_A*.json`, `_B*.json`).

## 0. Verdict

**{sum(m['pass'] for m in matrix)}/{sum(m['total'] for m in matrix)} checks passed.**
Overseas data plumbing (dated FX, cohort MERGE/forecast, landing/account observations, localization drafts) is largely correct and
well guarded. Four findings need fixing before this can be called production-ready:
the **`admin-console` authorization bypass (D1)**, the **unsigned query/body (D2)**, the **200+`code=-1` blanket
exception handler (D4)** and the **dead `API 数据管理` endpoint (D5)**. Two product gaps are also material: the
**age-blind cohort window (D7)** and the **impossible budget-reduction rule (D8)**.

## 1. Pass/fail matrix

{md_matrix()}

Notes:
- FAIL rows are either confirmed defects (mapped to D1-D16) or checks whose expected behaviour is the bug.
- `A3.5` fails *because* the days=30 window hides the matured D30 cohort (D7); `A4.3` fails because no missing-rate reason is returned (D11);
  `A5.12` fails on model-call amplification (D9); `A6.2`/`A6.3` fail on the missing budget-reduction action (D8).
- `A6.7`, `A6.17`, `A6.18`, `A6.22`, `A6.23` (no execution logs) are an *environment/data* limitation, not a code bug: no rule can match
  because today's report rows carry zero spend and the OFF_PEAK budget join yields 0 (D16). Historical logs (17 rows, 14:18 today) prove
  the logging path itself works; `logs/clear` was therefore skipped.
- `B9.20` (JSON `"0.94"` accepted for a numeric field) is lenient Jackson coercion, recorded as a minor hardening note rather than a defect.
- `B7.12` and `B8.3` were re-run with correctly signed paths/bodies (`overseas-sec_B7b.json`); the first-run copies are marked superseded.

### 1.1 Detailed checks

{md_matrix_detail()}

## 2. Confirmed defects

{md_defects()}

### Severity rationale / what is a design choice, not a bug
- The 12h timestamp window (B7.7) is a documented design choice, but without a nonce store it becomes a 12h replay window (D3).
- `/api/**` being public and `@NoAuth` reads being public is by design and consistent; it is only a finding where a **write/admin**
  action is reachable without the root check (D1).
- 404 -> SPA `index.html` is intentional for the SPA; only the `/error` 401 (D14) and the dead route (D5) are defects.
- H2 console and actuator are **not** exposed (B12.2-B12.6); path traversal on `/media/**` is **blocked** (B12.9).

## 3. IDs / data created and cleanup

| Item | Detail | Cleanup |
|---|---|---|
| FX rates | {CREATED['fx_rates']} | retained (QA evidence, keyed by currency+date; no delete API) |
| Account snapshots | {CREATED['account_snapshots']} | retained (MERGE-keyed; no delete API) |
| Landing observations | {CREATED['landing_observations']} | retained (MERGE-keyed; no delete API) |
| Cohort observations | {CREATED['cohort_observations']} | retained (MERGE-keyed; no delete API) |
| Localization drafts | {CREATED['localization_drafts']} | **archived** (no delete endpoint) |
| Automation rules | {CREATED['automation_rules']} | **deleted**; only ids 1-4 remain, schedule restored to `enabled=false` |
| Agent run | {CREATED['agent_run']} | retained (state SIMULATED, never executed) |
| Deleted after use | {CREATED['deleted_after_use']} | **deleted** |
| Logs | {CREATED['not_cleared']} | `logs/clear` deliberately **not** called |

Global safety policy / kill switch untouched; `local-admin` password untouched; no execute/rollback issued.

## 4. Uncovered areas
- `POST /cms/agent/overseas/observations/experiments` (needs pre-existing creative drafts + A/B variants) - not exercised.
- Non-dry-run rule execution path (would mutate `tiktok_ad_group_info`) - deliberately not executed; only dry-run probes.
- Execute / rollback of agent runs - forbidden by the brief.
- Browser-side rendering of the console (stored XSS execution, CSP meta behaviour) - no browser automation used;
  API returned the payload JSON-encoded (B9.6).
- Real LLM failover/fallback (`KEYWORD_RULES` path) - could not be forced while the gateway is configured; D10 is code-review based.
- Production/external profiles (`prod-afs`, `test-afs`), TLS, multi-node concurrency, DB-level isolation/lock timing.
- Sustained load / DoS beyond a single 20 MB JSON body; rate limiting is absent by inspection.
- `experiments` statistics math (Bonferroni p-value, lifecycle) and `forecast` R^2 edge cases beyond the tested shapes.
- Frontend console pages for overseas ops (only the APIs were verified).

## 5. Reproduction

```powershell
cd D:\\广告\\afs-cms
$env:PYTHONPATH='tools\\qa'
python tools/qa/overseas_a.py      # A1-A4 reads, FX, cohort, landing, accounts
python tools/qa/overseas_a56.py    # A5 localization (1 generate call) + A6 automation
python tools/qa/overseas_b.py      # B7-B12 auth, replay, validation, public API, headers
python tools/qa/overseas_b7b.py    # corrected B7.12/B8.3 + authz PoC (creates and deletes a rule)
python tools/qa/overseas_b10.py    # B10 idempotency + review/simulate races
python tools/qa/final_state.py     # post-run state + leftover check
python tools/qa/overseas_report.py # regenerates overseas-sec.json / .md
```
"""

(OUT / "overseas-sec.md").write_text(md, encoding="utf-8")
(OUT / "overseas-sec.json").write_text(json.dumps({
    "target": "http://127.0.0.1:9901",
    "profile": "local-cms",
    "generated_at": "2026-11-23T17:10:00+08:00",
    "summary": {"total": sum(m["total"] for m in matrix), "pass": sum(m["pass"] for m in matrix),
                "fail": sum(m["total"] - m["pass"] for m in matrix)},
    "matrix": matrix,
    "defects": DEFECTS,
    "checks": checks,
    "created_data": CREATED,
    "evidence_steps": steps,
    "uncovered": md.split("## 4. Uncovered areas")[1].split("## 5.")[0].strip().splitlines(),
}, ensure_ascii=False, indent=2), encoding="utf-8")
print("wrote", OUT / "overseas-sec.json", "and", OUT / "overseas-sec.md")
print(md_matrix())
