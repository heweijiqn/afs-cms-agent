"""End-to-end LangGraph optimization workflow against a live campaign.

Covers: create run -> SSE node stream -> AI completion -> approval guardrails ->
budget simulation -> idempotent execution -> ledger -> rollback -> local budget sync.
"""
from __future__ import annotations

import json
import sys
import threading
import time
import uuid
from pathlib import Path
from urllib import request as urlrequest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient, qs

OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)

CAMPAIGN = sys.argv[1] if len(sys.argv) > 1 else "1850127310500007919"  # TT_US_FoldableTreadmill_RSOC_001
c = CmsClient()
checks: list[dict] = []


def check(name: str, ok: bool, detail="") -> None:
    checks.append({"name": name, "ok": bool(ok), "detail": detail})
    print(("PASS " if ok else "FAIL ") + name + (f" :: {detail}" if detail else ""))


def wait_ready(run_id: str, timeout: float = 180.0) -> dict:
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        r = c.get(f"/cms/agent/runs/{run_id}")
        if r.code == 0:
            last = r.data
            source = (((last.get("plan") or {}).get("explanation") or {}).get("source") or "")
            if source and source != "PENDING":
                return last
        time.sleep(2)
    return last or {}


def sse(run_id: str, collected: list, errors: list, timeout: float = 90.0) -> None:
    path = f"/cms/agent/runs/{run_id}/stream"
    headers = {"Accept": "text/event-stream", **c.auth_headers("GET", path)}
    req = urlrequest.Request(c.base + path, headers=headers, method="GET")
    try:
        with urlrequest.urlopen(req, timeout=timeout) as resp:
            event = "message"
            data_lines: list[str] = []
            started = time.time()
            for raw in resp:
                if time.time() - started > timeout:
                    break
                line = raw.decode("utf-8", "replace").rstrip("\r\n")
                if line == "":
                    if data_lines:
                        payload = "\n".join(data_lines)
                        try:
                            parsed = json.loads(payload)
                        except Exception:
                            parsed = payload
                        collected.append((event, parsed, round(time.time() - started, 2)))
                    event = "message"
                    data_lines = []
                elif line.startswith("event:"):
                    event = line[6:].strip()
                elif line.startswith("data:"):
                    data_lines.append(line[5:].lstrip())
    except Exception as exc:  # stream closes after completion / timeout
        errors.append(repr(exc))


print(f"== campaign {CAMPAIGN}")
before = c.get(qs("/cms/ad/resource/platform/adg/list", adResourceId=CAMPAIGN))
print("ad group before:", json.dumps(before.data, ensure_ascii=False)[:300])

create = c.post(f"/cms/agent/runs/campaigns/{CAMPAIGN}",
                {"mode": "STANDARD", "targetRoas": 1.35, "minimumSpend": 20,
                 "minimumClicks": 30, "maxChangePercent": 10})
check("create campaign run returns 201", create.status == 201, f"status={create.status} code={create.code}")
if create.code != 0:
    print(create.text[:1000])
    raise SystemExit(1)
run = create.data
run_id = run["id"]
print("run", run_id, "state", run["state"], "revision", run.get("proposalRevision"))
plan = run.get("plan") or {}
print("input", json.dumps(plan.get("input"), ensure_ascii=False))
print("proposal", json.dumps(plan.get("proposal"), ensure_ascii=False))

events: list = []
errors: list = []
t = threading.Thread(target=sse, args=(run_id, events, errors, 120), daemon=True)
t.start()

ready = wait_ready(run_id, 180)
t.join(timeout=130)
expl = ((ready.get("plan") or {}).get("explanation") or {})
print("explanation.source", expl.get("source"), "model", expl.get("model"),
      "tokens", expl.get("inputTokens"), expl.get("outputTokens"), "nodes", expl.get("graphNodes"))
print("sse events", len(events), [e[0] for e in events][:20], "errors", errors)
check("AI analysis finished (not PENDING)", expl.get("source") not in ("", "PENDING"), expl.get("source"))
check("SSE stream produced node events", len(events) > 0, f"{len(events)} events")
check("explanation text present", bool((expl.get("text") or "").strip()), f"{len(expl.get('text') or '')} chars")
check("graph node trace present", bool(expl.get("graphNodes")), str(expl.get("graphNodes")))

trace = c.get(f"/cms/agent/runs/{run_id}/trace")
check("trace endpoint returns nodes", trace.code == 0 and bool(trace.data.get("nodes")),
      f"nodes={len((trace.data or {}).get('nodes') or [])}")
check("trace has checkpoints", trace.code == 0 and len((trace.data or {}).get("checkpoints") or []) > 0,
      f"checkpoints={len((trace.data or {}).get('checkpoints') or [])}")

run = c.get(f"/cms/agent/runs/{run_id}").data
revision = run["proposalRevision"]
proposal_hash = run["proposalHash"]
print("revision", revision, "hash", proposal_hash, "state", run["state"])

# approval guardrails
bad = c.post(f"/cms/agent/runs/{run_id}/review",
             {"decision": "APPROVED", "note": "版本校验", "expectedRevision": revision + 5})
check("review rejects stale revision", bad.status == 409, f"status={bad.status} msg={bad.body}")
bad = c.post(f"/cms/agent/runs/{run_id}/review",
             {"decision": "APPROVED", "note": "指纹校验", "expectedHash": "deadbeef" * 8})
check("review rejects wrong fingerprint", bad.status == 409, f"status={bad.status} msg={bad.body}")

approved = c.post(f"/cms/agent/runs/{run_id}/review",
                  {"decision": "APPROVED", "note": "验证 14 天回收 1.66 高于目标 1.35，批准小幅放量",
                   "expectedRevision": revision, "expectedHash": proposal_hash})
check("approve with correct revision+hash", approved.code == 0 and approved.data["state"] == "APPROVED",
      f"status={approved.status} state={approved.data.get('state') if approved.data else None}")
reviewed = c.post(f"/cms/agent/runs/{run_id}/review", {"decision": "REJECTED", "note": "重复审批"})
check("second review rejected with 409", reviewed.status == 409, f"status={reviewed.status}")

# simulate
wrong_budget = c.post(f"/cms/agent/runs/{run_id}/simulate", {"currentBudget": 350})
check("simulate rejects budget mismatch", wrong_budget.status == 409, f"status={wrong_budget.status}")
sim = c.post(f"/cms/agent/runs/{run_id}/simulate", {"currentBudget": 400.00})
projected = ((sim.data or {}).get("simulation") or {}).get("projectedBudget")
check("simulate approved run at observed budget", sim.code == 0 and sim.data["state"] == "SIMULATED",
      f"status={sim.status} projected={projected}")
check("simulation projection matches changePercent", str(projected) in ("440.0", "440.00", "440", "440.0"), str(projected))

# execute
idem = str(uuid.uuid4())
ex = c.post(f"/cms/agent/runs/{run_id}/execute",
            {"currentBudget": 400.00, "expectedRevision": revision, "expectedHash": proposal_hash},
            headers={"Idempotency-Key": idem})
check("execute approved+simulated run", ex.code == 0 and ex.data["state"] == "EXECUTED",
      f"status={ex.status} state={ex.data.get('state') if ex.data else None}")
exec_payload = (ex.data or {}).get("execution") or {}
print("execution", json.dumps(exec_payload, ensure_ascii=False))
check("execution ledger references stored proposal", exec_payload.get("proposalRevision") == revision, str(exec_payload.get("proposalRevision")))
check("local TikTok budget synced", exec_payload.get("localBudgetSynced") is True, str(exec_payload.get("localBudgetSynced")))

after = c.get(qs("/cms/ad/resource/platform/adg/list", adResourceId=CAMPAIGN))
budget = (((after.data or {}).get("list") or [{}])[0]).get("budget")
check("ad group budget updated to 440", float(budget or 0) == 440.0, f"budget={budget}")

repeat = c.post(f"/cms/agent/runs/{run_id}/execute",
                {"currentBudget": 400.00, "expectedRevision": revision, "expectedHash": proposal_hash},
                headers={"Idempotency-Key": idem})
check("same idempotency key replays safely", repeat.code == 0 and repeat.data["state"] == "EXECUTED",
      f"status={repeat.status}")

conflict = c.post(f"/cms/agent/runs/{run_id}/execute",
                  {"currentBudget": 400.00, "expectedRevision": revision, "expectedHash": proposal_hash},
                  headers={"Idempotency-Key": str(uuid.uuid4())})
check("new key on executed revision is blocked", conflict.status == 409, f"status={conflict.status} msg={conflict.body}")

ledger = c.get(f"/cms/agent/runs/{run_id}/ledger")
entries = ledger.data or []
check("ledger has EXECUTE entry", any(e.get("actionType") == "EXECUTE" for e in entries), json.dumps(entries, ensure_ascii=False)[:400])

rollback = c.post(f"/cms/agent/runs/{run_id}/rollback", {"reason": "验证回滚：恢复放量前预算"})
check("rollback executed run", rollback.code == 0 and rollback.data["state"] == "ROLLED_BACK",
      f"status={rollback.status} state={rollback.data.get('state') if rollback.data else None}")
restored = c.get(qs("/cms/ad/resource/platform/adg/list", adResourceId=CAMPAIGN))
budget2 = (((restored.data or {}).get("list") or [{}])[0]).get("budget")
check("ad group budget restored to 400", float(budget2 or 0) == 400.0, f"budget={budget2}")
again = c.post(f"/cms/agent/runs/{run_id}/rollback", {"reason": "重复回滚"})
check("second rollback blocked", again.status == 409, f"status={again.status}")

audit = c.get(f"/cms/agent/runs/{run_id}/audit")
stages = [e.get("stage") for e in (audit.data or [])]
print("audit stages", stages)
check("audit trail covers lifecycle", all(s in stages for s in ["PENDING", "APPROVED", "SIMULATED", "EXECUTED", "ROLLED_BACK"]), str(stages))

summary = {
    "campaign": CAMPAIGN,
    "runId": run_id,
    "explanationSource": expl.get("source"),
    "model": expl.get("model"),
    "tokens": {"input": expl.get("inputTokens"), "output": expl.get("outputTokens")},
    "graphNodes": expl.get("graphNodes"),
    "toolCalls": expl.get("toolCalls"),
    "guardrail": expl.get("guardrail"),
    "sseEvents": [e[0] for e in events],
    "checks": checks,
}
(OUT / "agent-flow.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
failed = [x for x in checks if not x["ok"]]
print(f"\n{len(checks)-len(failed)}/{len(checks)} checks passed; run={run_id}")
sys.exit(1 if failed else 0)
