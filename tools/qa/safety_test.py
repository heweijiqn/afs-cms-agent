"""Execution safety-gate tests: SHADOW, kill switch, daily cap, single-change cap."""
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
CAMPAIGN = "1850127310501377906"  # TT_US_CreditBuilderCard_Conv_174
c = CmsClient()
checks = []


def check(name, ok, detail=""):
    checks.append({"name": name, "ok": bool(ok), "detail": str(detail)})
    print(("PASS " if ok else "FAIL ") + name + (" :: " + str(detail) if detail else ""))


def wait_ready(run_id, timeout=180):
    end = time.time() + timeout
    while time.time() < end:
        r = c.get(f"/cms/agent/runs/{run_id}")
        src = ((r.data or {}).get("plan", {}).get("explanation", {}) or {}).get("source")
        if src and src != "PENDING":
            return r.data
        time.sleep(2)
    return r.data


def set_policy(**body):
    before = c.get("/cms/agent/safety").data
    r = c.post("/cms/agent/safety", body)
    return before, r


def execute(run_id, revision, proposal_hash, budget, key=None):
    return c.post(f"/cms/agent/runs/{run_id}/execute",
                  {"currentBudget": budget, "expectedRevision": revision, "expectedHash": proposal_hash},
                  headers={"Idempotency-Key": key or str(uuid.uuid4())})


original = c.get("/cms/agent/safety").data
print("original policy", {k: original[k] for k in ["safetyMode", "killSwitch", "dailyBudgetDeltaCap", "maxSingleChangePct"]})

create = c.post(f"/cms/agent/runs/campaigns/{CAMPAIGN}", {"mode": "STANDARD", "targetRoas": 1.35,
                "minimumSpend": 20, "minimumClicks": 30, "maxChangePercent": 10})
check("create run", create.status == 201, create.status)
run = wait_ready(create.data["id"])
run_id = run["id"]
proposal = run["plan"]["proposal"]
observed = (run["plan"].get("evidence") or {}).get("observedBudget")
print("proposal", proposal, "observedBudget", observed, "revision", run["proposalRevision"])
approved = c.post(f"/cms/agent/runs/{run_id}/review",
                  {"decision": "APPROVED", "note": "QA 安全阀测试：先批准，再验证各道执行闸门",
                   "expectedRevision": run["proposalRevision"], "expectedHash": run["proposalHash"]})
check("approve run", approved.code == 0, approved.status)
run = approved.data
rev, h, budget = run["proposalRevision"], run["proposalHash"], float(observed)

try:
    # 1. SHADOW blocks execution
    before, r = set_policy(safetyMode="SHADOW")
    blocked = execute(run_id, rev, h, budget)
    check("SHADOW mode blocks execution", blocked.status == 409, f"status={blocked.status} msg={(blocked.body or {}).get('msg')}")
    set_policy(safetyMode="COPILOT")

    # 2. Kill switch blocks execution
    c.post("/cms/agent/safety/kill-switch", {"active": True, "reason": "QA 熔断测试"})
    blocked = execute(run_id, rev, h, budget)
    check("kill switch blocks execution (423)", blocked.status == 423, f"status={blocked.status} msg={(blocked.body or {}).get('msg')}")
    c.post("/cms/agent/safety/kill-switch", {"active": False, "reason": ""})
    kill_off = c.get("/cms/agent/safety").data.get("killSwitch")
    check("kill switch restored", kill_off is False, kill_off)

    # 3. daily cumulative delta cap
    set_policy(dailyBudgetDeltaCap=1.00)
    blocked = execute(run_id, rev, h, budget)
    check("daily budget delta cap blocks execution", blocked.status == 409, f"status={blocked.status} msg={(blocked.body or {}).get('msg')}")
    set_policy(dailyBudgetDeltaCap=500.00)

    # 4. single change cap below proposal percent
    pct = abs(int(proposal.get("changePercent") or 0))
    if pct > 1:
        set_policy(maxSingleChangePct=max(1, pct - 1))
        blocked = execute(run_id, rev, h, budget)
        check("single-change cap blocks over-cap execution", blocked.status == 409,
              f"status={blocked.status} proposalPct={pct} msg={(blocked.body or {}).get('msg')}")
        set_policy(maxSingleChangePct=20)

    # 5. fingerprint mismatch
    blocked = c.post(f"/cms/agent/runs/{run_id}/execute",
                     {"currentBudget": budget, "expectedRevision": rev, "expectedHash": "0" * 64},
                     headers={"Idempotency-Key": str(uuid.uuid4())})
    check("fingerprint mismatch blocks execution", blocked.status == 409, f"status={blocked.status}")

    # 6. normal execution after restoring policy
    ok = execute(run_id, rev, h, budget)
    check("execution succeeds once policy is restored", ok.code == 0 and ok.data["state"] == "EXECUTED",
          f"status={ok.status} state={(ok.data or {}).get('state')}")
    ledger = (c.get(f"/cms/agent/runs/{run_id}/ledger").data or {}).get("list") or []
    check("blocked attempts left no ledger rows", len(ledger) == 1, f"ledger rows={len(ledger)}")
    rb = c.post(f"/cms/agent/runs/{run_id}/rollback", {"reason": "QA 安全阀测试结束，恢复预算"})
    check("rollback restores state", rb.code == 0 and rb.data["state"] == "ROLLED_BACK", f"status={rb.status}")
finally:
    c.post("/cms/agent/safety", {"safetyMode": original["safetyMode"],
                                 "dailyBudgetDeltaCap": original["dailyBudgetDeltaCap"],
                                 "maxSingleChangePct": original["maxSingleChangePct"],
                                 "maxLlmCallsPerDay": original["maxLlmCallsPerDay"]})
    c.post("/cms/agent/safety/kill-switch", {"active": False, "reason": ""})

final = c.get("/cms/agent/safety").data
check("safety policy restored to original", final["safetyMode"] == original["safetyMode"] and
      float(final["dailyBudgetDeltaCap"]) == float(original["dailyBudgetDeltaCap"]) and
      int(final["maxSingleChangePct"]) == int(original["maxSingleChangePct"]), json.dumps(final))
(OUT / "safety.json").write_text(json.dumps({"runId": run_id, "checks": checks}, ensure_ascii=False, indent=2), encoding="utf-8")
failed = [x for x in checks if not x["ok"]]
print(f"\n{len(checks)-len(failed)}/{len(checks)} safety checks passed")
sys.exit(1 if failed else 0)
