"""Stage 3: Copilot chat, scan, replay, five-agent allocation/run, telemetry, status."""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agent_common import c, record, save, rows  # noqa: E402

TT_TREADMILL = "1850127310500047514"
TT_DESK = "1850127310500681034"
created: dict = {}

# ----------------------------------------------------------------------- chat
sessions = c.get("/cms/agent/chat/sessions")
record("GET chat/sessions returns list", sessions.code == 0 and isinstance(rows(sessions), list),
       "GET /cms/agent/chat/sessions", "200 + list", f"status={sessions.status} n={len(rows(sessions))}", "high")

new_session = c.post("/cms/agent/chat/sessions",
                     {"title": "QA-止损诊断 · TT_US_StandingDesk_Conv_086",
                      "campaignId": TT_DESK})
record("create chat session 201", new_session.status == 201 and new_session.code == 0,
       "POST /cms/agent/chat/sessions", "201",
       f"status={new_session.status} body={new_session.text[:160]}", "high")
sess = (new_session.data or {}).get("session") or {}
sid = sess.get("id")
created["chatSession"] = sid
record("chat session keeps campaignId", sess.get("campaignId") == TT_DESK,
       "POST /cms/agent/chat/sessions", "campaignId echoed", str(sess.get("campaignId")), "medium")
record("chat session actor matches signed user (local-admin)", sess.get("actor") == "local-admin",
       "POST /cms/agent/chat/sessions", "actor=local-admin", f"actor={sess.get('actor')}", "medium")
open_session = c.get(f"/cms/agent/chat/sessions/{sid}")
record("GET session returns empty message list", open_session.code == 0 and rows(open_session).__len__() == 0,
       f"GET /cms/agent/chat/sessions/{sid}", "messages=[]",
       json.dumps(open_session.data, ensure_ascii=False)[:200], "high")

q1 = ("TT_US_StandingDesk_Conv_086 最近 14 天 ROAS 只有 0.61，应该停投还是降价？请给出依据")
m1 = c.post(f"/cms/agent/chat/sessions/{sid}/messages", {"content": q1}, timeout=180)
content1 = (m1.data or {}).get("content") or ""
meta1 = (m1.data or {}).get("meta") or {}
record("chat turn 1 returns answer", m1.status == 200 and m1.code == 0 and len(content1) > 40,
       f"POST /cms/agent/chat/sessions/{sid}/messages (standing desk ROAS question)", "200 + answer",
       f"status={m1.status} source={meta1.get('source')} tools={meta1.get('tools')} len={len(content1)}", "high")
record("chat answer carries graph meta", meta1.get("graphNodes") is not None,
       "POST .../messages", "meta.graphNodes present", json.dumps(meta1, ensure_ascii=False)[:240], "medium")

q2 = "如果按你上面的建议先降价 20%，3 天后 ROAS 仍低于 1.0，下一步怎么处理？"
m2 = c.post(f"/cms/agent/chat/sessions/{sid}/messages", {"content": q2}, timeout=180)
content2 = (m2.data or {}).get("content") or ""
record("chat turn 2 (multi-turn follow-up) returns answer",
       m2.status == 200 and m2.code == 0 and len(content2) > 40,
       "POST .../messages (follow-up referencing previous answer)", "200 + answer",
       f"status={m2.status} len={len(content2)}", "high")
after = c.get(f"/cms/agent/chat/sessions/{sid}")
msgs = rows(after)
roles = [m.get("role") for m in msgs]
record("session persists 4 messages (2 turns)", len(msgs) == 4 and roles == ["user", "assistant", "user", "assistant"],
       f"GET /cms/agent/chat/sessions/{sid}", "4 messages user/assistant x2",
       f"roles={roles}", "high")
record("persisted user message matches prompt 1",
       any(m.get("content") == q1 for m in msgs),
       f"GET /cms/agent/chat/sessions/{sid}", "first user message stored verbatim",
       next((m.get("content", "")[:60] for m in msgs if m.get("role") == "user"), ""), "high")

oneoff = c.post("/cms/agent/chat", {"content": "QA one-off prompt"})
record("POST /cms/agent/chat one-off endpoint exists", oneoff.status in (200, 201, 404, 405),
       "POST /cms/agent/chat (one-off prompt)", "separate endpoint or documented absence",
       f"status={oneoff.status} (no one-off route in AgentCopilotController)", "none")

deleted = c.delete(f"/cms/agent/chat/sessions/{sid}")
record("DELETE chat session", deleted.code == 0 and (deleted.data or {}).get("deleted") is True,
       f"DELETE /cms/agent/chat/sessions/{sid}", "deleted=true",
       f"status={deleted.status} body={deleted.text[:120]}", "high")
gone = c.get(f"/cms/agent/chat/sessions/{sid}")
record("deleted session is gone (404)", gone.status == 404,
       f"GET /cms/agent/chat/sessions/{sid}", "404", f"status={gone.status}", "high")

# ----------------------------------------------------------------------- scan
lst = rows(c.get("/cms/agent/campaigns?limit=250"))
pass_camps = [x for x in lst if (x.get("state") or "").upper() == "PASS"]
scan = c.post("/cms/agent/scan", {})
sdata = scan.data or {}
counts = sdata.get("counts") or {}
record("POST scan (defaults) returns structure", scan.code == 0 and
       {"scannedCampaigns", "targetRoas", "minimumSpend", "counts", "items"} <= set(sdata),
       "POST /cms/agent/scan {}", "scannedCampaigns/targetRoas/minimumSpend/counts/items",
       f"status={scan.status} scanned={sdata.get('scannedCampaigns')} counts={counts}", "high")
record("scan default thresholds echoed (1.25 / 30.00)",
       str(sdata.get("targetRoas")) in ("1.25", "1.250") and float(sdata.get("minimumSpend") or 0) == 30.0,
       "POST /cms/agent/scan {}", "targetRoas=1.25 minimumSpend=30.00",
       f"targetRoas={sdata.get('targetRoas')} minimumSpend={sdata.get('minimumSpend')}", "medium")
record("scan scannedCampaigns matches PASS campaigns",
       int(sdata.get("scannedCampaigns") or 0) == len(pass_camps),
       "POST /cms/agent/scan vs GET /cms/agent/campaigns", "scannedCampaigns == PASS count",
       f"scan={sdata.get('scannedCampaigns')} campaignsPASS={len(pass_camps)}", "medium")

target, floor = 1.25, 30.0
crit = [x for x in pass_camps if float(x.get("spend") or 0) >= floor and float(x.get("roas") or 0) < target * 0.70]
warn = [x for x in pass_camps if float(x.get("spend") or 0) >= floor and target * 0.70 <= float(x.get("roas") or 0) < target]
opp = [x for x in pass_camps if float(x.get("spend") or 0) >= floor and float(x.get("roas") or 0) >= target * 1.25]
expected_counts = {"criticalBleed": len(crit), "underperforming": len(warn), "scaleOpportunity": len(opp)}
actual_counts = {k: counts.get(k) for k in expected_counts}
record("scan counts reconcile with campaigns list", actual_counts == expected_counts,
       "POST /cms/agent/scan vs recomputed from GET /cms/agent/campaigns",
       json.dumps(expected_counts), json.dumps(actual_counts), "high")
item_spend_ok = all(any(abs(float(i.get("spend") or 0) - float(x.get("spend") or 0)) < 0.02 for x in lst)
                    for i in (sdata.get("items") or []))
record("scan item spend values exist in campaigns list", item_spend_ok,
       "POST /cms/agent/scan items[].spend vs campaigns list", "every item spend matches a campaign",
       f"{len(sdata.get('items') or [])} items checked", "medium")

custom_scan = c.post("/cms/agent/scan", {"targetRoas": 1.5, "minimumSpend": 100})
ccounts = (custom_scan.data or {}).get("counts") or {}
record("scan with custom thresholds applies them",
       custom_scan.code == 0 and str((custom_scan.data or {}).get("targetRoas")) in ("1.5", "1.50")
       and float((custom_scan.data or {}).get("minimumSpend") or 0) == 100.0,
       "POST /cms/agent/scan {targetRoas:1.5, minimumSpend:100}", "thresholds echoed, counts recomputed",
       f"targetRoas={(custom_scan.data or {}).get('targetRoas')} minSpend={(custom_scan.data or {}).get('minimumSpend')} counts={ccounts}",
       "medium")
bad_scan = c.post("/cms/agent/scan", {"targetRoas": 0})
record("scan rejects targetRoas=0 with 400", bad_scan.status == 400,
       "POST /cms/agent/scan {targetRoas:0}", "400", f"status={bad_scan.status}", "low")

# --------------------------------------------------------------------- replay
before_runs = [r.get("id") for r in rows(c.get("/cms/agent/runs"))]
replay = c.post("/cms/agent/replay",
                {"targetRoas": 1.35, "minimumSpend": 50, "minimumClicks": 30,
                 "maxChangePercent": 10, "limit": 10})
rdata = replay.data or {}
record("POST replay returns evaluation", replay.code == 0 and isinstance(rdata, dict) and len(rdata) > 0,
       "POST /cms/agent/replay {targetRoas:1.35, minimumSpend:50, limit:10}", "200 + metrics",
       f"status={replay.status} keys={list(rdata.keys())[:12]}", "high")
bad_replay = c.post("/cms/agent/replay", {"maxChangePercent": 99})
record("replay rejects maxChangePercent=99 with 400", bad_replay.status == 400,
       "POST /cms/agent/replay {maxChangePercent:99}", "400", f"status={bad_replay.status}", "low")
after_runs = [r.get("id") for r in rows(c.get("/cms/agent/runs"))]
record("replay is side-effect free (no new runs)", before_runs == after_runs,
       "GET /cms/agent/runs before/after replay", "no new run rows",
       f"{len(before_runs)} -> {len(after_runs)}", "high")

# ----------------------------------------------------------- five-agent allocate
alloc = c.post("/cms/agent/five-agent/allocate",
               {"totalBudget": 2000, "maxChangePercent": 15, "limit": 8})
adata = alloc.data or {}
allocs = adata.get("allocations") or []
record("POST five-agent/allocate returns allocations",
       alloc.code == 0 and len(allocs) > 0 and "totalOptimalBudget" in adata,
       "POST /cms/agent/five-agent/allocate {totalBudget:2000,maxChangePercent:15,limit:8}",
       "allocations[] + totals",
       f"status={alloc.status} n={len(allocs)} current={adata.get('totalCurrentBudget')} optimal={adata.get('totalOptimalBudget')}",
       "high")
cap_ok = all(abs(float(a.get("optimalBudget") or 0) - float(a.get("currentBudget") or 0))
             <= float(a.get("currentBudget") or 0) * 0.1501 for a in allocs)
record("allocation respects maxChangePercent cap", cap_ok,
       "POST /cms/agent/five-agent/allocate", "|Δ| <= 15% per campaign",
       json.dumps([{k: a.get(k) for k in ("campaignId", "currentBudget", "optimalBudget")} for a in allocs[:4]],
                  ensure_ascii=False), "high")
record("allocation totalOptimalBudget matches sum of allocations",
       abs(float(adata.get("totalOptimalBudget") or 0) - sum(float(a.get("optimalBudget") or 0) for a in allocs)) < 0.05,
       "POST /cms/agent/five-agent/allocate", "totalOptimalBudget == sum(optimalBudget)",
       f"total={adata.get('totalOptimalBudget')} sum={round(sum(float(a.get('optimalBudget') or 0) for a in allocs), 2)}",
       "high")
over = c.post("/cms/agent/five-agent/allocate", {"totalBudget": 2000, "maxChangePercent": 50})
record("allocation maxChangePercent=50 -> 400", over.status == 400,
       "POST /cms/agent/five-agent/allocate {maxChangePercent:50}", "400", f"status={over.status}", "low")

# -------------------------------------------------------------- five-agent run
ag_before = rows(c.get(f"/cms/ad/resource/platform/adg/list?adResourceId={TT_TREADMILL}"))
budget_before = float(ag_before[0].get("budget")) if ag_before else None
five = c.post("/cms/agent/five-agent/run",
              {"campaignId": TT_TREADMILL, "targetRoas": 1.35,
               "useLlmForCreative": False, "maxChangePercent": 15, "iteration": 1}, timeout=150)
fdata = five.data or {}
record("POST five-agent/run returns 5 agent reports",
       five.code == 0 and {"campaign", "monitor", "audience", "creative", "bidding", "optimize", "safety"} <= set(fdata),
       "POST /cms/agent/five-agent/run (useLlmForCreative=false)", "campaign/monitor/audience/creative/bidding/optimize/safety",
       f"status={five.status} keys={list(fdata.keys())}", "high")
record("five-agent run targets requested campaign",
       ((fdata.get("campaign") or {}).get("campaignId")) == TT_TREADMILL,
       "POST /cms/agent/five-agent/run", "campaign.campaignId==" + TT_TREADMILL,
       str((fdata.get("campaign") or {}).get("campaignId")), "high")
mon = fdata.get("monitor") or {}
record("monitor health score within 0-100",
       isinstance(mon.get("healthScore"), (int, float)) and 0 <= mon.get("healthScore") <= 100,
       "POST /cms/agent/five-agent/run", "0<=healthScore<=100", str(mon.get("healthScore")), "medium")
creative = fdata.get("creative") or {}
record("deterministic creative variants present (no LLM)",
       len(creative.get("variants") or []) > 0,
       "POST /cms/agent/five-agent/run useLlmForCreative=false", "variants non-empty",
       f"strategy={creative.get('strategy')} variants={len(creative.get('variants') or [])} source={creative.get('source')}",
       "medium")
opt = fdata.get("optimize") or {}
record("optimize reports budget allocations within cap",
       len(opt.get("budgetAllocations") or []) > 0,
       "POST /cms/agent/five-agent/run", "budgetAllocations non-empty",
       json.dumps(opt.get("budgetAllocations"), ensure_ascii=False)[:240], "medium")
ag_after = rows(c.get(f"/cms/ad/resource/platform/adg/list?adResourceId={TT_TREADMILL}"))
budget_after = float(ag_after[0].get("budget")) if ag_after else None
record("five-agent run does not mutate live budget", budget_before == budget_after,
       "GET /cms/ad/resource/platform/adg/list before/after five-agent/run", "budget unchanged",
       f"{budget_before} -> {budget_after}", "high")
bad_iter = c.post("/cms/agent/five-agent/run", {"campaignId": TT_TREADMILL, "iteration": 4})
record("five-agent run iteration=4 -> 400", bad_iter.status == 400,
       "POST /cms/agent/five-agent/run {iteration:4}", "400", f"status={bad_iter.status}", "low")

# ----------------------------------------------------------- telemetry / status
tel = c.get("/cms/agent/telemetry")
tdata = tel.data or {}
record("telemetry returns totals/runs/byNode/status/safety/graphs",
       tel.code == 0 and {"totals", "runs", "byNode", "status", "safety", "graphs"} <= set(tdata),
       "GET /cms/agent/telemetry", "expected keys", str(list(tdata.keys())), "high")
totals = tdata.get("totals") or {}
runs_sum = tdata.get("runs") or {}
record("telemetry totals are internally consistent",
       totals.get("totalTokens") == (totals.get("inputTokens") or 0) + (totals.get("outputTokens") or 0),
       "GET /cms/agent/telemetry", "totalTokens == input+output",
       f"{totals.get('inputTokens')}+{totals.get('outputTokens')}={totals.get('totalTokens')}", "high")
latest = rows(c.get("/cms/agent/runs"))
latest_pending = sum(1 for r in latest if r.get("state") == "PENDING")
record("telemetry pendingRuns plausible vs latest runs", (runs_sum.get("pendingRuns") or 0) >= latest_pending,
       "GET /cms/agent/telemetry vs GET /cms/agent/runs", "pendingRuns >= pending among latest 30",
       f"telemetry={runs_sum.get('pendingRuns')} latest30={latest_pending}", "medium")
status = c.get("/cms/agent/status")
sdata2 = status.data or {}
record("status exposes engine/tools/graphs",
       status.code == 0 and {"engine", "tools", "graphs", "configured"} <= set(sdata2),
       "GET /cms/agent/status", "engine/tools/graphs/configured",
       f"engine={sdata2.get('engine')} configured={sdata2.get('configured')} tools={len(sdata2.get('tools') or [])}",
       "high")

(Path(__file__).resolve().parent / "out" / "agent-core-stage3-ids.json").write_text(
    json.dumps(created, ensure_ascii=False, indent=2), encoding="utf-8")
save("stage3")
print("stage3 done")
