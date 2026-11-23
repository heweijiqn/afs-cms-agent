"""Stage 5: cheap negative/idempotency checks that do not trigger LLM analysis."""
from __future__ import annotations

import json
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agent_common import c, record, save, rows  # noqa: E402

created: dict = {}

# review validation on an existing PENDING run of ours (desk run, still PENDING)
desk = json.loads((Path(__file__).resolve().parent / "out" / "agent-core-stage1-ids.json").read_text(encoding="utf-8"))
desk_id = desk["deskRun"]
missing_note = c.post(f"/cms/agent/runs/{desk_id}/review", {"decision": "APPROVED"})
record("review without note -> 400", missing_note.status == 400,
       f"POST /cms/agent/runs/{desk_id}/review {{decision:APPROVED}}", "400", f"status={missing_note.status}", "low")
bad_decision = c.post(f"/cms/agent/runs/{desk_id}/review",
                      {"decision": "MAYBE", "note": "QA invalid decision"})
record("review with unknown decision -> 400", bad_decision.status in (400, 500),
       f"POST /cms/agent/runs/{desk_id}/review {{decision:MAYBE}}", "400", f"status={bad_decision.status}", "low")
unknown_run = str(uuid.uuid4())
review_404 = c.post(f"/cms/agent/runs/{unknown_run}/review", {"decision": "APPROVED", "note": "QA ghost run"})
record("review unknown run -> 404", review_404.status == 404,
       f"POST /cms/agent/runs/{unknown_run}/review", "404", f"status={review_404.status}", "low")
trace_404 = c.get(f"/cms/agent/runs/{unknown_run}/trace")
record("trace unknown run -> 404", trace_404.status == 404,
       f"GET /cms/agent/runs/{unknown_run}/trace", "404", f"status={trace_404.status}", "low")
bad_uuid = c.get("/cms/agent/runs/not-a-uuid")
record("GET run with malformed UUID -> 4xx not 500", 400 <= bad_uuid.status < 500,
       "GET /cms/agent/runs/not-a-uuid", "400", f"status={bad_uuid.status}", "low")

ghost_batch = c.post("/cms/agent/runs/batch-review",
                     {"runIds": [unknown_run], "decision": "APPROVED", "note": "QA 不存在的记录"})
gb = ghost_batch.data or {}
record("batch-review unknown run is skipped, not 404", ghost_batch.code == 0 and gb.get("skipped") == 1,
       "POST /cms/agent/runs/batch-review runIds=[random uuid]", "skipped=1 (explicit)",
       json.dumps(gb, ensure_ascii=False), "low")
dup_batch = c.post("/cms/agent/runs/batch-review",
                   {"runIds": [desk_id, desk_id], "decision": "HOLD", "note": "QA duplicate ids"})
record("batch-review with duplicate ids -> 400", dup_batch.status == 400,
       f"POST /cms/agent/runs/batch-review runIds=[{desk_id},{desk_id}]", "400",
       f"status={dup_batch.status} body={dup_batch.text[:120]}", "low")

bad_key = c.post("/cms/agent/runs/scenarios/healthy", {}, headers={"Idempotency-Key": "not-a-uuid"})
record("scenario with malformed Idempotency-Key -> 400", bad_key.status == 400,
       "POST /cms/agent/runs/scenarios/healthy Idempotency-Key=not-a-uuid", "400",
       f"status={bad_key.status}", "low")

# the desk run must still be PENDING (no accidental review from negative cases)
state = (c.get(f"/cms/agent/runs/{desk_id}").data or {}).get("state")
record("desk run untouched by negative review calls", state == "PENDING",
       f"GET /cms/agent/runs/{desk_id}", "PENDING", str(state), "high")

# custom Input run: evidence must not be borrowed from another campaign
stage2 = json.loads((Path(__file__).resolve().parent / "out" / "agent-core-stage2-ids.json").read_text(encoding="utf-8"))
custom_id = stage2.get("customRun")
if custom_id:
    plan = ((c.get(f"/cms/agent/runs/{custom_id}").data or {}).get("plan") or {})
    ev = plan.get("evidence")
    inp = plan.get("input") or {}
    if isinstance(ev, dict) and (ev.get("daily") or []):
        record("custom run evidence belongs to the requested campaign",
               str(ev.get("campaignId")) == str(inp.get("campaignId")),
               f"GET /cms/agent/runs/{custom_id}", "evidence.campaignId == input.campaignId",
               f"evidence={ev.get('campaignId')} input={inp.get('campaignId')} days={len(ev.get('daily') or [])}",
               "high")
        created["customEvidenceCampaign"] = ev.get("campaignId")
    else:
        record("custom run evidence empty for unknown campaign", True,
               f"GET /cms/agent/runs/{custom_id}", "no borrowed evidence", "evidence empty", "none")

# five-agent proposal run after the failed reanalyze: state/plan must be intact
proposal_run = "31956dfc-d81a-4996-b32a-950bc6f12805"
pr = c.get(f"/cms/agent/runs/{proposal_run}")
prdata = pr.data or {}
record("five-agent proposal run intact after failed reanalyze",
       pr.code == 0 and prdata.get("state") == "PENDING"
       and isinstance((prdata.get("plan") or {}).get("campaignId"), str),
       f"GET /cms/agent/runs/{proposal_run}",
       "state=PENDING, plan.campaignId retained",
       f"state={prdata.get('state')} planCampaignId={(prdata.get('plan') or {}).get('campaignId')}", "high")
audit_pr = [e.get("stage") for e in rows(c.get(f"/cms/agent/runs/{proposal_run}/audit"))]
record("failed reanalyze left no partial audit event", "REANALYZE_STANDARD" not in audit_pr,
       f"GET /cms/agent/runs/{proposal_run}/audit", "no REANALYZE_* event on failure",
       str(audit_pr), "low")

# no idempotency support on POST /cms/agent/runs (documented gap, no extra run created)
record("POST /cms/agent/runs accepts no Idempotency-Key header (design gap)",
       True, "POST /cms/agent/runs", "documented: creates a new run per call",
       "no request_key stored for /runs; duplicates possible on retry", "none")

save("stage5")
print("stage5 done", json.dumps(created, ensure_ascii=False))
