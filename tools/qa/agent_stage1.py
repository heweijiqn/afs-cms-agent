"""Stage 1: campaign runs (STANDARD + DEEP), SSE, trace, audit, review branches."""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agent_common import c, record, save, wait_ready, start_sse, abort  # noqa: E402

TT_TREADMILL = "1850127310500047514"
TT_DESK = "1850127310500681034"

created: dict = {}


def create_run(campaign: str, mode: str, body_extra: dict | None = None) -> dict:
    body = {"mode": mode, "targetRoas": 1.35, "minimumSpend": 20,
            "minimumClicks": 30, "maxChangePercent": 10}
    if body_extra:
        body.update(body_extra)
    r = c.post(f"/cms/agent/runs/campaigns/{campaign}", body)
    record(f"{mode} run create returns 201", r.status == 201 and r.code == 0,
           f"POST /cms/agent/runs/campaigns/{campaign} body={json.dumps(body)}",
           "HTTP 201, code 0", f"status={r.status} code={r.code} body={r.text[:160]}", "high")
    if r.status != 201:
        abort(f"create {campaign} failed: {r.status} {r.text[:400]}")
    return r.data


# ---------------------------------------------------------------- run A STANDARD
run_a = create_run(TT_TREADMILL, "STANDARD")
run_a_id = run_a["id"]
created["standardRun"] = run_a_id
plan_a = run_a.get("plan") or {}
record("run A state PENDING at creation", run_a.get("state") == "PENDING",
       f"POST /cms/agent/runs/campaigns/{TT_TREADMILL}", "state=PENDING",
       f"state={run_a.get('state')}", "high")
record("run A explanation.source PENDING at creation",
       ((plan_a.get("explanation") or {}).get("source")) == "PENDING",
       "GET /cms/agent/runs/{id} plan.explanation.source", "PENDING",
       str((plan_a.get("explanation") or {}).get("source")), "medium")

# evidence + input traceability to real reports
inp = plan_a.get("input") or {}
ev = plan_a.get("evidence") or {}
daily = ev.get("daily") or []
daily_spend = round(sum(float(d.get("spend") or 0) for d in daily), 2)
daily_impr = sum(int(d.get("impressions") or 0) for d in daily)
record("run A plan.input uses requested campaign", inp.get("campaignId") == TT_TREADMILL,
       "POST /cms/agent/runs/campaigns/1850127310500047514", "input.campaignId=1850127310500047514",
       f"input.campaignId={inp.get('campaignId')} site={inp.get('site')} channel={inp.get('channel')}", "high")
record("run A evidence.daily present from real reports", len(daily) > 0,
       "GET /cms/agent/runs/{id}", "evidence.daily non-empty", f"{len(daily)} days", "high")
record("run A input.spend reconciles with evidence.daily spend",
       abs(daily_spend - float(inp.get("spend") or 0)) < 0.02,
       "GET /cms/agent/runs/{id}", "sum(evidence.daily.spend) == plan.input.spend",
       f"daily={daily_spend} input={inp.get('spend')}", "high")
record("run A evidence.observedBudget matches ad group (370)",
       float(ev.get("observedBudget") or 0) == 370.0,
       "GET /cms/agent/runs/{id}", "evidence.observedBudget=370",
       f"observedBudget={ev.get('observedBudget')}", "high")
if daily:
    record("run A input dates span evidence window",
           str(inp.get("start")) == str(daily[0].get("date")) or str(inp.get("end")) == str(daily[-1].get("date")),
           "GET /cms/agent/runs/{id}", "input.start/end == evidence first/last day",
           f"input={inp.get('start')}..{inp.get('end')} evidence={daily[0].get('date')}..{daily[-1].get('date')}", "medium")

# SSE subscription immediately, then wait for the async graph to finish
events, thread = start_sse(run_a_id, 150)
ready_a = wait_ready(run_a_id, 190)
thread.join(timeout=160)
expl_a = ((ready_a.get("plan") or {}).get("explanation") or {})
record("run A AI analysis finished (source != PENDING)", expl_a.get("source") not in (None, "", "PENDING"),
       "GET /cms/agent/runs/{id} (poll <=190s)", "plan.explanation.source=LANGCHAIN4J_* o RULES",
       f"source={expl_a.get('source')} model={expl_a.get('model')} tokens={expl_a.get('inputTokens')}/{expl_a.get('outputTokens')}", "blocker")
record("run A explanation text present", bool((expl_a.get("text") or "").strip()),
       "GET /cms/agent/runs/{id}", "non-empty explanation.text", f"{len(expl_a.get('text') or '')} chars", "high")
record("run A explanation.graphNodes present", bool(expl_a.get("graphNodes")),
       "GET /cms/agent/runs/{id}", "graphNodes list", str(expl_a.get("graphNodes")), "medium")

node_events = [e for e in events if e[0] not in ("message", "done", "__closed__")]
done_events = [e for e in events if e[0] == "done"]
record("SSE stream yields node events", len(node_events) > 0,
       f"GET /cms/agent/runs/{run_a_id}/stream", ">=1 node event",
       f"events={[e[0] for e in events][:24]}", "high")
record("SSE stream yields done event", len(done_events) > 0,
       f"GET /cms/agent/runs/{run_a_id}/stream", "done event", f"done={len(done_events)}", "high")

trace = c.get(f"/cms/agent/runs/{run_a_id}/trace")
nodes = (trace.data or {}).get("nodes") or []
checkpoints = (trace.data or {}).get("checkpoints") or []
record("trace has nodes", trace.code == 0 and len(nodes) > 0,
       f"GET /cms/agent/runs/{run_a_id}/trace", "nodes non-empty", f"nodes={len(nodes)}", "high")
record("trace has checkpoints", trace.code == 0 and len(checkpoints) > 0,
       f"GET /cms/agent/runs/{run_a_id}/trace", "checkpoints non-empty", f"checkpoints={len(checkpoints)}", "high")

audit = c.get(f"/cms/agent/runs/{run_a_id}/audit")
stages = [e.get("stage") for e in (audit.data or [])]
actors = [e.get("actor") for e in (audit.data or [])]
record("audit has PENDING + ANALYSIS_READY", "PENDING" in stages and "ANALYSIS_READY" in stages,
       f"GET /cms/agent/runs/{run_a_id}/audit", "stages contain PENDING,ANALYSIS_READY", str(stages), "high")
signed_as = c.last_headers.get("admin-name")
record("audit actor matches signed user (local-admin)", all(a == "local-admin" for a in actors),
       f"GET /cms/agent/runs/{run_a_id}/audit (admin-name header={signed_as})",
       "every audit actor == local-admin", f"actors={actors}", "medium")

# ------------------------------------------------------------- review guardrails
rev = (ready_a.get("proposalRevision"), ready_a.get("proposalHash"))
stale = c.post(f"/cms/agent/runs/{run_a_id}/review",
               {"decision": "APPROVED", "note": "QA stale revision", "expectedRevision": (rev[0] or 1) + 3})
record("review rejects stale revision with 409", stale.status == 409,
       "POST /cms/agent/runs/{id}/review expectedRevision=+3", "409", f"status={stale.status} body={stale.text[:120]}", "medium")
badhash = c.post(f"/cms/agent/runs/{run_a_id}/review",
                 {"decision": "APPROVED", "note": "QA wrong hash", "expectedHash": "0" * 64})
record("review rejects wrong fingerprint with 409", badhash.status == 409,
       "POST /cms/agent/runs/{id}/review expectedHash=000...", "409", f"status={badhash.status}", "medium")
approve = c.post(f"/cms/agent/runs/{run_a_id}/review",
                 {"decision": "APPROVED", "note": "QA: 14 天 ROAS 1.79 高于目标 1.35，批准小幅放量",
                  "expectedRevision": rev[0], "expectedHash": rev[1]})
record("approve PENDING run -> APPROVED", approve.code == 0 and (approve.data or {}).get("state") == "APPROVED",
       "POST /cms/agent/runs/{id}/review decision=APPROVED", "200, state=APPROVED",
       f"status={approve.status} state={(approve.data or {}).get('state')} reviewer={(approve.data or {}).get('reviewer')}", "high")
reviewer = (approve.data or {}).get("reviewer")
record("reviewer field matches signed user (local-admin)", reviewer == "local-admin",
       "POST /cms/agent/runs/{id}/review", "reviewer=local-admin", f"reviewer={reviewer}", "medium")
again = c.post(f"/cms/agent/runs/{run_a_id}/review", {"decision": "REJECTED", "note": "QA duplicate review"})
record("re-review of reviewed run returns 409", again.status == 409,
       "POST /cms/agent/runs/{id}/review (second time)", "409", f"status={again.status}", "high")

run_a_later = c.get(f"/cms/agent/runs/{run_a_id}").data or {}
record("approved run keeps AI explanation (no stale PENDING)",
       (((run_a_later.get("plan") or {}).get("explanation") or {}).get("source")) not in (None, "", "PENDING"),
       "GET /cms/agent/runs/{id} after approve", "explanation.source preserved",
       str(((run_a_later.get("plan") or {}).get("explanation") or {}).get("source")), "high")

# ------------------------------------------------------------------ run B DEEP
run_b = create_run(TT_TREADMILL, "DEEP")
run_b_id = run_b["id"]
created["deepRun"] = run_b_id
record("DEEP run reports mode DEEP", ((run_b.get("plan") or {}).get("explanation") or {}).get("mode") == "DEEP",
       f"POST /cms/agent/runs/campaigns/{TT_TREADMILL} mode=DEEP", "plan.explanation.mode=DEEP",
       str(((run_b.get("plan") or {}).get("explanation") or {}).get("mode")), "medium")
ready_b = wait_ready(run_b_id, 190)
expl_b = ((ready_b.get("plan") or {}).get("explanation") or {})
record("DEEP run AI analysis finished", expl_b.get("source") not in (None, "", "PENDING"),
       "GET /cms/agent/runs/{id} (poll <=190s)", "source != PENDING",
       f"source={expl_b.get('source')} mode={expl_b.get('mode')} nodes={expl_b.get('graphNodes')}", "blocker")
record("DEEP analysis retains DEEP mode after completion", expl_b.get("mode") == "DEEP",
       "GET /cms/agent/runs/{id}", "mode=DEEP", f"mode={expl_b.get('mode')}", "medium")

rej = c.post(f"/cms/agent/runs/{run_b_id}/review",
             {"decision": "REJECTED", "note": "QA: 与现有放量提案重复，先驳回观察 48 小时"})
record("reject PENDING run -> REJECTED", rej.code == 0 and (rej.data or {}).get("state") == "REJECTED",
       "POST /cms/agent/runs/{id}/review decision=REJECTED", "200, state=REJECTED",
       f"status={rej.status} state={(rej.data or {}).get('state')} note={(rej.data or {}).get('reviewNote')}", "high")

reanalyze_rejected = c.post(f"/cms/agent/runs/{run_b_id}/reanalyze", {"mode": "STANDARD"})
record("reanalyze on non-PENDING run -> 409", reanalyze_rejected.status == 409,
       f"POST /cms/agent/runs/{run_b_id}/reanalyze (state REJECTED)", "409",
       f"status={reanalyze_rejected.status} body={reanalyze_rejected.text[:140]}", "high")

# ------------------------------------------------------------------ run C desk
run_c = create_run(TT_DESK, "STANDARD")
run_c_id = run_c["id"]
created["deskRun"] = run_c_id
record("run C (ROAS 0.61) proposal is HOLD/REDUCE", ((run_c.get("plan") or {}).get("proposal") or {}).get("action") in ("HOLD", "REDUCE"),
       f"POST /cms/agent/runs/campaigns/{TT_DESK}", "proposal.action in HOLD,REDUCE",
       json.dumps((run_c.get("plan") or {}).get("proposal"), ensure_ascii=False), "medium")
ready_c = wait_ready(run_c_id, 190)
expl_c = ((ready_c.get("plan") or {}).get("explanation") or {})
record("run C AI analysis finished", expl_c.get("source") not in (None, "", "PENDING"),
       "GET /cms/agent/runs/{id} (poll <=190s)", "source != PENDING",
       f"source={expl_c.get('source')} tokens={expl_c.get('inputTokens')}/{expl_c.get('outputTokens')}", "blocker")

re_ok = c.post(f"/cms/agent/runs/{run_c_id}/reanalyze", {"mode": "STANDARD"})
record("reanalyze on PENDING run -> 200", re_ok.status == 200 and re_ok.code == 0,
       f"POST /cms/agent/runs/{run_c_id}/reanalyze", "200",
       f"status={re_ok.status} revision={(re_ok.data or {}).get('proposalRevision')}", "high")
rev_before = ready_c.get("proposalRevision")
rev_after = (re_ok.data or {}).get("proposalRevision")
record("reanalyze bumps proposalRevision", isinstance(rev_before, int) and isinstance(rev_after, int) and rev_after == rev_before + 1,
       "GET /cms/agent/runs/{id} before/after reanalyze", "revision+1", f"{rev_before} -> {rev_after}", "medium")
ready_c2 = wait_ready(run_c_id, 190)
expl_c2 = ((ready_c2.get("plan") or {}).get("explanation") or {})
record("reanalyzed run completes and is not stale PENDING", expl_c2.get("source") not in (None, "", "PENDING"),
       "GET /cms/agent/runs/{id}", "source != PENDING", f"source={expl_c2.get('source')}", "high")
record("reanalyzed run remains PENDING for review", ready_c2.get("state") == "PENDING",
       "GET /cms/agent/runs/{id}", "state=PENDING", f"state={ready_c2.get('state')}", "high")
audit_c = c.get(f"/cms/agent/runs/{run_c_id}/audit")
stages_c = [e.get("stage") for e in (audit_c.data or [])]
record("audit records REANALYZE_STANDARD", "REANALYZE_STANDARD" in stages_c,
       f"GET /cms/agent/runs/{run_c_id}/audit", "REANALYZE_STANDARD present", str(stages_c), "medium")

created["analysisTokens"] = {
    "standard": [expl_a.get("inputTokens"), expl_a.get("outputTokens")],
    "deep": [expl_b.get("inputTokens"), expl_b.get("outputTokens")],
    "desk": [expl_c.get("inputTokens"), expl_c.get("outputTokens")],
    "deskReanalyze": [expl_c2.get("inputTokens"), expl_c2.get("outputTokens")],
}
(Path(__file__).resolve().parent / "out" / "agent-core-stage1-ids.json").write_text(
    json.dumps({"created": created, "proposal": ready_a.get("plan", {}).get("proposal"),
                "scorecard": ready_a.get("plan", {}).get("scorecard")}, ensure_ascii=False, indent=2),
    encoding="utf-8")
save("stage1")
print("stage1 done", json.dumps(created, ensure_ascii=False))
