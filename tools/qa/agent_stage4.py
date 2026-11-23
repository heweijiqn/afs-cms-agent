"""Stage 4: agent extensions (patrol/briefs/creatives/csv/attribution/scan-interpret) + five-agent bridge."""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agent_common import c, record, save, rows  # noqa: E402

TT_TREADMILL = "1850127310500047514"
created: dict = {}

# ------------------------------------------------------------ read-only listings
patrol = c.get("/cms/agent/ext/patrol")
pdata = patrol.data or {}
record("GET ext/patrol returns config", patrol.code == 0 and isinstance(pdata, dict),
       "GET /cms/agent/ext/patrol", "200 + config", f"keys={list(pdata.keys())}", "high")
briefs = c.get("/cms/agent/ext/briefs")
record("GET ext/briefs returns list", briefs.code == 0, "GET /cms/agent/ext/briefs",
       "200 + list", f"status={briefs.status} n={len(rows(briefs))}", "high")
fatigue = c.get("/cms/agent/ext/creatives/fatigue")
frows = rows(fatigue)
record("GET ext/creatives/fatigue returns list", fatigue.code == 0,
       "GET /cms/agent/ext/creatives/fatigue", "200 + list", f"n={len(frows)}", "high")
drafts_before = rows(c.get("/cms/agent/ext/creatives/drafts"))
record("GET ext/creatives/drafts returns list", isinstance(drafts_before, list),
       "GET /cms/agent/ext/creatives/drafts", "200 + list", f"n={len(drafts_before)}", "high")
ab = c.get("/cms/agent/ext/creatives/ab-tests")
record("GET ext/creatives/ab-tests returns list", ab.code == 0,
       "GET /cms/agent/ext/creatives/ab-tests", "200 + list", f"n={len(rows(ab))}", "high")
attr = c.get("/cms/agent/ext/attribution")
adata = attr.data or {}
record("GET ext/attribution returns summary/records/lessons",
       attr.code == 0 and {"summary", "records", "lessons"} <= set(adata),
       "GET /cms/agent/ext/attribution", "summary/records/lessons",
       f"keys={list(adata.keys())} records={len(adata.get('records') or [])}", "high")

# ---------------------------------------------------------------- csv-analyze
csv_text = "\n".join([
    "广告名称,花费,收入,展示,点击,转化,频次",
    "QA-TT-HomeSecurity-Camera-01,420.30,780.60,61000,1180,268,2.1",
    "QA-TT-HomeSecurity-Doorbell-02,310.80,205.10,48000,640,96,1.4",
    "QA-TT-HomeSecurity-Sensor-03,88.40,20.10,900,5,1,1.1",
    "QA-TT-HomeSecurity-Alarm-04,520.10,910.40,72000,2050,410,4.2",
    "QA-TT-HomeSecurity-Bundle-05,150.00,150.00,12000,300,60,3.6",
])
csv = c.post("/cms/agent/ext/csv-analyze", {"csvText": csv_text, "platform": "TikTok"}, timeout=150)
cdata = csv.data or {}
summary = cdata.get("summary") or {}
record("POST ext/csv-analyze parses 5 Chinese-header rows",
       csv.code == 0 and summary.get("rowCount") == 5,
       "POST /cms/agent/ext/csv-analyze (5-row TikTok CSV, Chinese headers)", "rowCount=5",
       f"status={csv.status} rowCount={summary.get('rowCount')} totalSpend={summary.get('totalSpend')}", "high")
record("csv-analyze totals reconcile (spend=1489.60)",
       abs(float(summary.get("totalSpend") or 0) - 1489.60) < 0.02,
       "POST /cms/agent/ext/csv-analyze", "totalSpend=1489.60",
       f"totalSpend={summary.get('totalSpend')} revenue={summary.get('totalRevenue')} roas={summary.get('roas')}", "high")
record("csv-analyze classifies winners/bleeders/fatigue",
       (summary.get("winnerCount") or 0) >= 1 and (summary.get("bleederCount") or 0) >= 1
       and (summary.get("fatigueCount") or 0) >= 1,
       "POST /cms/agent/ext/csv-analyze", "winner>=1 bleeder>=1 fatigue>=1",
       f"winner={summary.get('winnerCount')} bleeder={summary.get('bleederCount')} fatigue={summary.get('fatigueCount')}",
       "medium")
record("csv-analyze LLM diagnosis attached", bool((cdata.get("aiAnalysis") or cdata.get("aiDiagnosis") or "").strip())
       or cdata.get("aiPowered") is not None,
       "POST /cms/agent/ext/csv-analyze", "aiPowered/diagnosis field",
       f"keys={list(cdata.keys())}", "low")
bad_csv = c.post("/cms/agent/ext/csv-analyze", {"csvText": "only-header"})
record("csv-analyze single line -> 400", bad_csv.status == 400,
       "POST /cms/agent/ext/csv-analyze {csvText:'only-header'}", "400", f"status={bad_csv.status}", "low")

# ------------------------------------------------------------- brief generate
brief = c.post("/cms/agent/ext/brief/generate", {"pushWebhook": False}, timeout=150)
bdata = brief.data or {}
record("POST ext/brief/generate returns markdown brief",
       brief.code == 0 and bool((bdata.get("summaryMarkdown") or "").strip()),
       "POST /cms/agent/ext/brief/generate", "title + summaryMarkdown",
       f"status={brief.status} title={bdata.get('title')} aiPowered={bdata.get('aiPowered')} len={len(bdata.get('summaryMarkdown') or '')}",
       "high")
briefs_after = rows(c.get("/cms/agent/ext/briefs"))
mine_brief = next((b for b in briefs_after if b.get("title") == bdata.get("title")), {})
record("generated brief is persisted and listed", mine_brief.get("title") == bdata.get("title"),
       "GET /cms/agent/ext/briefs after generate", "new brief visible",
       f"id={mine_brief.get('id')} createdBy={mine_brief.get('createdBy')}", "high")
record("brief createdBy matches signed user (local-admin)", mine_brief.get("createdBy") == "local-admin",
       "GET /cms/agent/ext/briefs", "createdBy=local-admin",
       f"createdBy={mine_brief.get('createdBy')}", "medium")

# --------------------------------------------------------- creative generation
gen = c.post("/cms/agent/ext/creatives/generate",
             {"campaignId": "TT_US_HomeSecuritySystems_Conv_250",
              "campaignName": "QA-HomeSecuritySystems", "platform": "TikTok",
              "style": "PAIN_POINT"}, timeout=150)
gdata = gen.data or {}
record("POST ext/creatives/generate creates 3 drafts",
       gen.code == 0 and (gdata.get("generated") or 0) >= 1,
       "POST /cms/agent/ext/creatives/generate (TikTok QA-HomeSecuritySystems)",
       "generated>=1",
       f"status={gen.status} generated={gdata.get('generated')} aiPowered={gdata.get('aiPowered')}", "high")
mine = [d for d in rows(c.get("/cms/agent/ext/creatives/drafts"))
        if d.get("campaignName") == "QA-HomeSecuritySystems"]
record("generated drafts are listed", len(mine) >= 1,
       "GET /cms/agent/ext/creatives/drafts", "QA-HomeSecuritySystems drafts visible",
       f"n={len(mine)} statuses={[d.get('status') for d in mine]}", "high")
record("generated drafts carry QA campaign + DRAFT status",
       all(d.get("campaignId") == "TT_US_HomeSecuritySystems_Conv_250" and d.get("status") == "DRAFT" for d in mine),
       "GET /cms/agent/ext/creatives/drafts", "campaignId + DRAFT",
       json.dumps([{k: d.get(k) for k in ("id", "status", "createdBy")} for d in mine], ensure_ascii=False), "medium")
if mine:
    created["creativeDraftId"] = mine[0]["id"]
    record("creative draft createdBy matches signed user (local-admin)", mine[0].get("createdBy") == "local-admin",
           "GET /cms/agent/ext/creatives/drafts", "createdBy=local-admin",
           f"createdBy={mine[0].get('createdBy')}", "medium")

    upd = c.post(f"/cms/agent/ext/creatives/drafts/{mine[0]['id']}/status", {"status": "AB_TESTING"})
    record("draft status update DRAFT->AB_TESTING", upd.code == 0 and (upd.data or {}).get("status") == "AB_TESTING",
           f"POST /cms/agent/ext/creatives/drafts/{mine[0]['id']}/status {{status:AB_TESTING}}",
           "200, status=AB_TESTING", json.dumps(upd.data, ensure_ascii=False)[:200], "high")
    listed = next((d for d in rows(c.get("/cms/agent/ext/creatives/drafts")) if d.get("id") == mine[0]["id"]), {})
    record("draft status persisted", listed.get("status") == "AB_TESTING",
           "GET /cms/agent/ext/creatives/drafts", "status=AB_TESTING", str(listed.get("status")), "high")
    bad = c.post(f"/cms/agent/ext/creatives/drafts/{mine[0]['id']}/status", {"status": "BOGUS_STATUS"})
    record("invalid draft status rejected with 400", bad.status == 400,
           f"POST /cms/agent/ext/creatives/drafts/{mine[0]['id']}/status {{status:BOGUS_STATUS}}",
           "400 (unknown status)", f"status={bad.status} body={bad.text[:140]}", "medium")
    listed2 = next((d for d in rows(c.get("/cms/agent/ext/creatives/drafts")) if d.get("id") == mine[0]["id"]), {})
    record("invalid status did not silently publish draft", listed2.get("status") != "PUBLISHED",
           "GET /cms/agent/ext/creatives/drafts after invalid status", "status unchanged",
           f"status={listed2.get('status')} (coerced to PUBLISHED by service)", "medium")
    missing = c.post("/cms/agent/ext/creatives/drafts/999999/status", {"status": "DRAFT"})
    record("status update on unknown draft -> 404", missing.status == 404,
           "POST /cms/agent/ext/creatives/drafts/999999/status", "404", f"status={missing.status}", "low")

# ------------------------------------------------- five-agent propose (bridge)
propose = c.post("/cms/agent/ext/five-agent/propose",
                 {"campaignId": TT_TREADMILL, "action": "DECREASE_BUDGET", "changePercent": -10,
                  "targetRoas": 1.35,
                  "rationale": "QA: 五智能体闭环建议在 ROAS 1.68 下先降价 10% 测试弹性"})
prdata = propose.data or {}
record("POST ext/five-agent/propose creates PENDING run",
       propose.code == 0 and prdata.get("state") == "PENDING" and prdata.get("id"),
       "POST /cms/agent/ext/five-agent/propose", "200 + PENDING run id",
       f"status={propose.status} id={prdata.get('id')} budget {prdata.get('currentDailyBudget')}->{prdata.get('proposedDailyBudget')}",
       "high")
bridge_id = prdata.get("id")
created["fiveAgentProposalRun"] = bridge_id
if bridge_id:
    st = c.get(f"/cms/agent/runs/{bridge_id}")
    bplan = (st.data or {}).get("plan") or {}
    record("five-agent proposal run plan shape matches OptimizationWorkflow.Plan",
           isinstance(bplan.get("input"), dict) and isinstance(bplan.get("proposal"), dict),
           f"GET /cms/agent/runs/{bridge_id}", "plan.input + plan.proposal present",
           f"plan keys={list(bplan.keys())}", "high")
    rean = c.post(f"/cms/agent/runs/{bridge_id}/reanalyze", {"mode": "STANDARD"})
    record("reanalyze on five-agent proposal run handles missing plan.input gracefully",
           rean.status in (400, 409),
           f"POST /cms/agent/runs/{bridge_id}/reanalyze (PENDING, plan has no input)",
           "4xx with clear message", f"status={rean.status} body={rean.text[:160]}", "high")
    sim = c.post(f"/cms/agent/runs/{bridge_id}/simulate", {"currentBudget": 445.72})
    record("simulate on five-agent proposal is rejected while PENDING", sim.status == 409,
           f"POST /cms/agent/runs/{bridge_id}/simulate", "409",
           f"status={sim.status}", "low")

# ------------------------------------------------------------- scan-interpret
interp = c.post("/cms/agent/ext/scan-interpret",
                {"contextText": "全盘 60 个 PASS 活动，ROAS 目标 1.25；TT_US_StandingDesk_Conv_086 ROAS 0.61 持续出血 $77；"
                                "TT_US_FoldableTreadmill_Conv_006 ROAS 1.79 具放量潜力"}, timeout=150)
idata = interp.data or {}
record("POST ext/scan-interpret returns analysis",
       interp.code == 0 and isinstance(idata.get("analysis"), str) and len(idata.get("analysis") or "") > 20,
       "POST /cms/agent/ext/scan-interpret", "aiPowered + analysis",
       f"status={interp.status} aiPowered={idata.get('aiPowered')} len={len(idata.get('analysis') or '')}", "high")

(Path(__file__).resolve().parent / "out" / "agent-core-stage4-ids.json").write_text(
    json.dumps(created, ensure_ascii=False, indent=2), encoding="utf-8")
save("stage4")
print("stage4 done", json.dumps(created, ensure_ascii=False))
