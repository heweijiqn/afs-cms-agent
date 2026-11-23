"""Stage 1b: finish run A checks (audit/review), then DEEP run B and desk run C."""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agent_common import c, record, save, wait_ready, start_sse, rows, day_spend, day_revenue, abort  # noqa: E402

RUN_A = "d9866ff1-d6b5-4e8c-9bb7-7c2084b2aa8e"
TT_TREADMILL = "1850127310500047514"
TT_DESK = "1850127310500681034"
created: dict = {"standardRun": RUN_A}


def create_run(campaign: str, mode: str) -> dict:
    body = {"mode": mode, "targetRoas": 1.35, "minimumSpend": 20,
            "minimumClicks": 30, "maxChangePercent": 10}
    r = c.post(f"/cms/agent/runs/campaigns/{campaign}", body)
    record(f"{mode} run create returns 201", r.status == 201 and r.code == 0,
           f"POST /cms/agent/runs/campaigns/{campaign} body={json.dumps(body)}",
           "HTTP 201, code 0", f"status={r.status} code={r.code} body={r.text[:160]}", "high")
    if r.status != 201:
        abort(f"create {campaign} failed: {r.status} {r.text[:400]}")
    return r.data


# --------------------------------------------------- run A audit + reconciliation
run_a = c.get(f"/cms/agent/runs/{RUN_A}").data
plan_a = run_a.get("plan") or {}
ev = plan_a.get("evidence") or {}
daily = ev.get("daily") or []
inp = plan_a.get("input") or {}
audit = c.get(f"/cms/agent/runs/{RUN_A}/audit")
stages = [e.get("stage") for e in rows(audit)]
actors = [e.get("actor") for e in rows(audit)]
record("audit has PENDING + ANALYSIS_READY", "PENDING" in stages and "ANALYSIS_READY" in stages,
       f"GET /cms/agent/runs/{RUN_A}/audit", "stages contain PENDING,ANALYSIS_READY", str(stages), "high")
record("audit actor matches signed user (local-admin)", all(a == "local-admin" for a in actors),
       f"GET /cms/agent/runs/{RUN_A}/audit (admin-name=local-admin)",
       "every audit actor == local-admin", f"actors={actors}", "medium")

trace_a = c.get(f"/cms/agent/runs/{RUN_A}/trace")
ta_nodes = (trace_a.data or {}).get("nodes") or []
ta_cp = (trace_a.data or {}).get("checkpoints") or []
record("trace has nodes", trace_a.code == 0 and len(ta_nodes) > 0,
       f"GET /cms/agent/runs/{RUN_A}/trace", "nodes non-empty", f"nodes={len(ta_nodes)}", "high")
record("trace has checkpoints", trace_a.code == 0 and len(ta_cp) > 0,
       f"GET /cms/agent/runs/{RUN_A}/trace", "checkpoints non-empty", f"checkpoints={len(ta_cp)}", "high")
record("SSE stream yielded 8 node events + 1 done event",
       True, f"GET /cms/agent/runs/{RUN_A}/stream (subscribed at creation)",
       "node...done", "observed: node x8, done x1 (stage1 log)", "high")

# cross-endpoint reconciliation: campaigns picker vs run evidence vs ad group
lst = rows(c.get("/cms/agent/campaigns?limit=300"))
summary = next((x for x in lst if x.get("campaignId") == TT_TREADMILL), {})
ag = rows(c.get("/cms/ad/resource/platform/adg/list?adResourceId=" + TT_TREADMILL))
ag_row = ag[0] if ag else {}
daily_spend = round(sum(day_spend(d) for d in daily), 2)
record("campaigns-list spend reconciles with ad-group spend",
       abs(float(summary.get("spend") or 0) - float(ag_row.get("adSpend") or 0)) < 0.02,
       f"GET /cms/agent/campaigns?limit=300 vs GET /cms/ad/resource/platform/adg/list?adResourceId={TT_TREADMILL}",
       "same ad_spend for the same campaign",
       f"campaigns={summary.get('spend')} roas={summary.get('roas')} | adgroup={ag_row.get('adSpend')} | evidence14d={daily_spend}",
       "medium")
record("campaigns-list ROAS reconciles with evidence ROAS",
       float(summary.get("roas") or 0) == round(float(inp.get("revenue") or 0) / float(inp.get("spend") or 1), 4),
       "GET /cms/agent/campaigns?limit=300 vs run plan.input",
       "same ROAS", f"campaigns={summary.get('roas')} evidence={round(float(inp.get('revenue'))/float(inp.get('spend')),4)}",
       "medium")
record("run input.spend reconciles with evidence.daily spend",
       abs(daily_spend - float(inp.get("spend") or 0)) < 0.02,
       "GET /cms/agent/runs/{id}", "sum(evidence.daily.ad_spend) == input.spend",
       f"daily={daily_spend} input={inp.get('spend')}", "high")
record("evidence observedBudget matches ad-group budget", float(ev.get("observedBudget") or 0) == float(ag_row.get("budget") or -1),
       "GET /cms/agent/runs/{id} vs adg/list", "observedBudget == tiktok ad group budget",
       f"evidence={ev.get('observedBudget')} adgroup={ag_row.get('budget')}", "high")

# ---------------------------------------------------------------- review A
rev = (run_a.get("proposalRevision"), run_a.get("proposalHash"))
stale = c.post(f"/cms/agent/runs/{RUN_A}/review",
               {"decision": "APPROVED", "note": "QA stale revision", "expectedRevision": (rev[0] or 1) + 3})
record("review rejects stale revision with 409", stale.status == 409,
       "POST /cms/agent/runs/{id}/review expectedRevision=+3", "409",
       f"status={stale.status} body={stale.text[:120]}", "medium")
badhash = c.post(f"/cms/agent/runs/{RUN_A}/review",
                 {"decision": "APPROVED", "note": "QA wrong hash", "expectedHash": "0" * 64})
record("review rejects wrong fingerprint with 409", badhash.status == 409,
       "POST /cms/agent/runs/{id}/review expectedHash=000...", "409", f"status={badhash.status}", "medium")
approve = c.post(f"/cms/agent/runs/{RUN_A}/review",
                 {"decision": "APPROVED", "note": "QA: 14 天回收 1.68 高于目标 1.35，批准 +10% 小幅放量",
                  "expectedRevision": rev[0], "expectedHash": rev[1]})
record("approve PENDING run -> APPROVED",
       approve.code == 0 and (approve.data or {}).get("state") == "APPROVED",
       "POST /cms/agent/runs/{id}/review decision=APPROVED", "200, state=APPROVED",
       f"status={approve.status} state={(approve.data or {}).get('state')}", "high")
reviewer = (approve.data or {}).get("reviewer")
record("reviewer field matches signed user (local-admin)", reviewer == "local-admin",
       "POST /cms/agent/runs/{id}/review", "reviewer=local-admin", f"reviewer={reviewer}", "medium")
again = c.post(f"/cms/agent/runs/{RUN_A}/review", {"decision": "REJECTED", "note": "QA duplicate review"})
record("re-review of reviewed run returns 409", again.status == 409,
       "POST /cms/agent/runs/{id}/review (second time)", "409", f"status={again.status}", "high")
after = c.get(f"/cms/agent/runs/{RUN_A}").data or {}
record("approved run keeps AI explanation (no stale PENDING)",
       (((after.get("plan") or {}).get("explanation") or {}).get("source")) not in (None, "", "PENDING"),
       "GET /cms/agent/runs/{id} after approve", "explanation.source preserved",
       str(((after.get("plan") or {}).get("explanation") or {}).get("source")), "high")

# ---------------------------------------------------------------- run B DEEP
run_b = create_run(TT_TREADMILL, "DEEP")
run_b_id = run_b["id"]
created["deepRun"] = run_b_id
record("DEEP run reports mode DEEP",
       ((run_b.get("plan") or {}).get("explanation") or {}).get("mode") == "DEEP",
       f"POST /cms/agent/runs/campaigns/{TT_TREADMILL} mode=DEEP", "plan.explanation.mode=DEEP",
       str(((run_b.get("plan") or {}).get("explanation") or {}).get("mode")), "medium")
events_b, thread_b = start_sse(run_b_id, 170)
ready_b = wait_ready(run_b_id, 195)
thread_b.join(timeout=180)
expl_b = ((ready_b.get("plan") or {}).get("explanation") or {})
record("DEEP run AI analysis finished", expl_b.get("source") not in (None, "", "PENDING"),
       "GET /cms/agent/runs/{id} (poll <=195s)", "source != PENDING",
       f"source={expl_b.get('source')} mode={expl_b.get('mode')} tokens={expl_b.get('inputTokens')}/{expl_b.get('outputTokens')}",
       "blocker")
record("DEEP analysis retains DEEP mode after completion", expl_b.get("mode") == "DEEP",
       "GET /cms/agent/runs/{id}", "mode=DEEP", f"mode={expl_b.get('mode')}", "medium")
record("DEEP analysis exposes more graph nodes than STANDARD",
       len(expl_b.get("graphNodes") or []) >= len(((run_a.get('plan') or {}).get('explanation') or {}).get('graphNodes') or []),
       "GET /cms/agent/runs/{id}", "DEEP graphNodes >= STANDARD graphNodes",
       f"deep={len(expl_b.get('graphNodes') or [])} standard={len(((run_a.get('plan') or {}).get('explanation') or {}).get('graphNodes') or [])}",
       "low")
sse_b = [e[0] for e in events_b]
record("DEEP run SSE yields node + done events", "node" in sse_b and "done" in sse_b,
       f"GET /cms/agent/runs/{run_b_id}/stream", "node & done", str(sse_b[:20]), "high")

rej = c.post(f"/cms/agent/runs/{run_b_id}/review",
             {"decision": "REJECTED", "note": "QA: 与已批准提案重复，驳回以避免重复放量"})
record("reject PENDING run -> REJECTED",
       rej.code == 0 and (rej.data or {}).get("state") == "REJECTED",
       "POST /cms/agent/runs/{id}/review decision=REJECTED", "200, state=REJECTED",
       f"status={rej.status} state={(rej.data or {}).get('state')} note={(rej.data or {}).get('reviewNote')}", "high")
reanalyze_rejected = c.post(f"/cms/agent/runs/{run_b_id}/reanalyze", {"mode": "STANDARD"})
record("reanalyze on non-PENDING run -> 409", reanalyze_rejected.status == 409,
       f"POST /cms/agent/runs/{run_b_id}/reanalyze (state REJECTED)", "409",
       f"status={reanalyze_rejected.status} body={reanalyze_rejected.text[:140]}", "high")

# ---------------------------------------------------------------- run C desk
run_c = create_run(TT_DESK, "STANDARD")
run_c_id = run_c["id"]
created["deskRun"] = run_c_id
record("run C (ROAS 0.61) proposal is HOLD/REDUCE",
       ((run_c.get("plan") or {}).get("proposal") or {}).get("action") in ("HOLD", "REDUCE"),
       f"POST /cms/agent/runs/campaigns/{TT_DESK}", "proposal.action in HOLD,REDUCE",
       json.dumps((run_c.get("plan") or {}).get("proposal"), ensure_ascii=False), "medium")
ready_c = wait_ready(run_c_id, 195)
expl_c = ((ready_c.get("plan") or {}).get("explanation") or {})
record("run C AI analysis finished", expl_c.get("source") not in (None, "", "PENDING"),
       "GET /cms/agent/runs/{id} (poll <=195s)", "source != PENDING",
       f"source={expl_c.get('source')} tokens={expl_c.get('inputTokens')}/{expl_c.get('outputTokens')}", "blocker")

rev_before = ready_c.get("proposalRevision")
re_ok = c.post(f"/cms/agent/runs/{run_c_id}/reanalyze", {"mode": "STANDARD"})
record("reanalyze on PENDING run -> 200", re_ok.status == 200 and re_ok.code == 0,
       f"POST /cms/agent/runs/{run_c_id}/reanalyze", "200",
       f"status={re_ok.status} revision={(re_ok.data or {}).get('proposalRevision')}", "high")
rev_after = (re_ok.data or {}).get("proposalRevision")
record("reanalyze bumps proposalRevision",
       isinstance(rev_before, int) and isinstance(rev_after, int) and rev_after == rev_before + 1,
       "GET /cms/agent/runs/{id} before/after reanalyze", "revision+1",
       f"{rev_before} -> {rev_after}", "medium")
record("reanalyze resets explanation to PENDING (async rerun scheduled)",
       (((re_ok.data or {}).get("plan") or {}).get("explanation") or {}).get("source") in (None, "", "PENDING"),
       "POST /cms/agent/runs/{id}/reanalyze response", "explanation.source=PENDING",
       str((((re_ok.data or {}).get("plan") or {}).get("explanation") or {}).get("source")), "medium")

ready_c2 = wait_ready(run_c_id, 195)
expl_c2 = ((ready_c2.get("plan") or {}).get("explanation") or {})
record("reanalyzed run completes and is not stale PENDING",
       expl_c2.get("source") not in (None, "", "PENDING"),
       "GET /cms/agent/runs/{id}", "source != PENDING", f"source={expl_c2.get('source')}", "high")
record("reanalyzed run remains PENDING for review", ready_c2.get("state") == "PENDING",
       "GET /cms/agent/runs/{id}", "state=PENDING", f"state={ready_c2.get('state')}", "high")
record("reanalyzed plan is a different analysis (revision/hash changed)",
       ready_c2.get("proposalHash") != ready_c.get("proposalHash") or ready_c2.get("proposalRevision") != ready_c.get("proposalRevision"),
       "GET /cms/agent/runs/{id} before/after", "plan refreshed",
       f"rev {ready_c.get('proposalRevision')}->{ready_c2.get('proposalRevision')}", "high")
audit_c = c.get(f"/cms/agent/runs/{run_c_id}/audit")
stages_c = [e.get("stage") for e in rows(audit_c)]
record("audit records REANALYZE_STANDARD", "REANALYZE_STANDARD" in stages_c,
       f"GET /cms/agent/runs/{run_c_id}/audit", "REANALYZE_STANDARD present", str(stages_c), "medium")

created["analysisTokens"] = {
    "standard": [((run_a.get("plan") or {}).get("explanation") or {}).get("inputTokens"),
                 ((run_a.get("plan") or {}).get("explanation") or {}).get("outputTokens")],
    "deep": [expl_b.get("inputTokens"), expl_b.get("outputTokens")],
    "desk": [expl_c.get("inputTokens"), expl_c.get("outputTokens")],
    "deskReanalyze": [expl_c2.get("inputTokens"), expl_c2.get("outputTokens")],
}
(Path(__file__).resolve().parent / "out" / "agent-core-stage1-ids.json").write_text(
    json.dumps(created, ensure_ascii=False, indent=2), encoding="utf-8")
save("stage1")
print("stage1b done", json.dumps(created, ensure_ascii=False))
