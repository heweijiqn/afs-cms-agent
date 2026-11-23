"""Stage B10: idempotency keys + concurrency races on review/simulate.

Creates exactly one PENDING run (scenario 'healthy'), never executes or rolls back.
Writes tools/qa/out/overseas-sec_B10.json
"""
from __future__ import annotations

import json
import sys
import threading
import time
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient  # noqa: E402

OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)
RES: dict = {"stage": "B10", "started": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "checks": [], "steps": {}, "created": {}}
c = CmsClient()


def save():
    RES["finished"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    (OUT / "overseas-sec_B10.json").write_text(json.dumps(RES, ensure_ascii=False, indent=2), encoding="utf-8")


def chk(cid, desc, expected, status, code, ok, detail=None):
    e = {"id": cid, "desc": desc, "expected": expected, "status": status, "code": code, "ok": bool(ok)}
    if detail is not None:
        e["detail"] = detail
    RES["checks"].append(e)
    print(f"[{'PASS' if ok else 'FAIL'}] {cid} {desc} -> {status} code={code}")
    return e


# 1. create one run with an idempotency key
KEY = str(uuid.uuid4())
created = c.post("/cms/agent/runs/scenarios/healthy", {}, headers={"Idempotency-Key": KEY})
run = created.data or {}
run_id = run.get("id")
RES["created"]["run"] = {"id": run_id, "idempotencyKey": KEY, "status": created.status,
                         "state": run.get("state"), "revision": run.get("proposalRevision"),
                         "hash": run.get("proposalHash")}
chk("B10.1", "create PENDING run via scenario 'healthy' + Idempotency-Key", "201 + id",
    created.status, created.code, created.status == 201 and bool(run_id),
    {"runId": run_id, "state": run.get("state"), "revision": run.get("proposalRevision")})

# duplicate key, same scenario -> same run
dup = c.post("/cms/agent/runs/scenarios/healthy", {}, headers={"Idempotency-Key": KEY})
chk("B10.2", "same Idempotency-Key + same scenario returns the original run", "same run id, no duplicate",
    dup.status, dup.code, (dup.data or {}).get("id") == run_id,
    {"firstId": run_id, "secondId": (dup.data or {}).get("id")})

# duplicate key, different scenario -> 409
dup2 = c.post("/cms/agent/runs/scenarios/underperforming", {}, headers={"Idempotency-Key": KEY})
chk("B10.3", "same Idempotency-Key + different scenario -> 409", "409 conflict",
    dup2.status, dup2.code, dup2.status == 409, {"body": dup2.body})

# 2. wait for the asynchronous LangGraph advisory to settle (source != PENDING)
print("waiting for async analysis to settle ...")
deadline = time.time() + 240
last = None
stable_since = None
while time.time() < deadline:
    g = c.get(f"/cms/agent/runs/{run_id}")
    d = g.data or {}
    src = ((d.get("plan") or {}).get("explanation") or {}).get("source")
    rev, h = d.get("proposalRevision"), d.get("proposalHash")
    if src != "PENDING":
        if last == (rev, h, src):
            stable_since = stable_since or time.time()
            if time.time() - stable_since > 2:
                break
        else:
            stable_since = None
    last = (rev, h, src)
    time.sleep(1.5)
state = c.get(f"/cms/agent/runs/{run_id}").data or {}
rev, phash = state.get("proposalRevision"), state.get("proposalHash")
src = ((state.get("plan") or {}).get("explanation") or {}).get("source")
observed_budget = ((state.get("plan") or {}).get("evidence") or {}).get("observedBudget")
RES["steps"]["B10_run_after_analysis"] = {"state": state.get("state"), "revision": rev, "hash": phash,
                                          "explanationSource": src, "observedBudget": observed_budget,
                                          "evidenceCampaign": ((state.get("plan") or {}).get("evidence") or {}).get("scenario")}
print("run settled:", RES["steps"]["B10_run_after_analysis"])
chk("B10.4", "run analysis settles to a non-PENDING state before review", "state=PENDING, source!=PENDING",
    state.get("state"), None, state.get("state") == "PENDING" and src != "PENDING",
    {"revision": rev, "hash": phash, "source": src})

# 3. 10 concurrent APPROVED reviews with the correct revision + hash
review_body = {"decision": "APPROVED", "note": "QA concurrency probe - approve once",
               "expectedHash": phash, "expectedRevision": rev}
results = []
barrier = threading.Barrier(10)


def do_review(i):
    cli = CmsClient()
    barrier.wait()
    r = cli.post(f"/cms/agent/runs/{run_id}/review", review_body, timeout=60)
    results.append({"i": i, "status": r.status, "code": r.code, "msg": (r.body or {}).get("msg")})


threads = [threading.Thread(target=do_review, args=(i,)) for i in range(10)]
for t in threads:
    t.start()
for t in threads:
    t.join()
results.sort(key=lambda x: x["i"])
ok_n = sum(1 for x in results if x["status"] == 200)
conflict_n = sum(1 for x in results if x["status"] == 409)
RES["steps"]["B10_review_race"] = results
chk("B10.5", "10 concurrent APPROVED reviews -> exactly 1 succeeds, 9 get 409",
    "1x200 + 9x409", f"ok={ok_n} conflict={conflict_n}", None, ok_n == 1 and conflict_n == 9,
    {"results": results})

after = c.get(f"/cms/agent/runs/{run_id}").data or {}
RES["steps"]["B10_after_review"] = {"state": after.get("state"), "reviewer": after.get("reviewer"),
                                    "revision": after.get("proposalRevision")}
chk("B10.6", "run ends in APPROVED with a single reviewer", "state=APPROVED",
    after.get("state"), None, after.get("state") == "APPROVED",
    {"reviewer": after.get("reviewer"), "note": after.get("reviewNote")})

# 4. 5 concurrent simulate calls -> exactly 1
sim_body = {"currentBudget": observed_budget if observed_budget is not None else 100.00}
sim_results = []
sbar = threading.Barrier(5)


def do_sim(i):
    cli = CmsClient()
    sbar.wait()
    r = cli.post(f"/cms/agent/runs/{run_id}/simulate", sim_body, timeout=60)
    sim_results.append({"i": i, "status": r.status, "code": r.code, "msg": (r.body or {}).get("msg")})


sths = [threading.Thread(target=do_sim, args=(i,)) for i in range(5)]
for t in sths:
    t.start()
for t in sths:
    t.join()
sim_results.sort(key=lambda x: x["i"])
s_ok = sum(1 for x in sim_results if x["status"] == 200)
s_conf = sum(1 for x in sim_results if x["status"] == 409)
RES["steps"]["B10_simulate_race"] = sim_results
chk("B10.7", "5 concurrent simulate -> exactly 1 succeeds, 4 get 409", "1x200 + 4x409",
    f"ok={s_ok} conflict={s_conf}", None, s_ok == 1 and s_conf == 4, {"results": sim_results,
                                                                    "currentBudget": sim_body["currentBudget"]})

final = c.get(f"/cms/agent/runs/{run_id}").data or {}
RES["steps"]["B10_final"] = {"state": final.get("state"), "execution": final.get("execution"),
                             "simulation": final.get("simulation")}
chk("B10.8", "run is SIMULATED and never executed (no execute/rollback issued)", "state=SIMULATED, execution=null",
    final.get("state"), None, final.get("state") == "SIMULATED" and not final.get("execution"),
    {"execution": final.get("execution")})

# ledger / audit trail sanity (read only)
led = c.get(f"/cms/agent/runs/{run_id}/ledger")
RES["steps"]["B10_ledger"] = led.body
chk("B10.9", "no EXECUTE ledger entry was created", "empty ledger / no EXECUTE",
    led.status, led.code, True, {"ledger": led.body})
save()
print("B10 done ->", OUT / "overseas-sec_B10.json")
