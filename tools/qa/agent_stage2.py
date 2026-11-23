"""Stage 2: scenarios + idempotency, custom Input payload, validation 400s, batch review."""
from __future__ import annotations

import json
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agent_common import c, record, save, abort  # noqa: E402

created: dict = {}

# ------------------------------------------------------------------- scenarios
sc = c.get("/cms/agent/runs/scenarios")
ids = [s.get("id") for s in (sc.data or [])] if isinstance(sc.data, list) else []
record("GET /runs/scenarios returns catalog", sc.code == 0 and {"healthy", "underperforming", "low-volume"} <= set(ids),
       "GET /cms/agent/runs/scenarios", "healthy/underperforming/low-volume",
       f"status={sc.status} ids={ids}", "high")

k1 = str(uuid.uuid4())
r1 = c.post("/cms/agent/runs/scenarios/healthy", {}, headers={"Idempotency-Key": k1})
record("scenario healthy create 201", r1.status == 201 and r1.code == 0,
       f"POST /cms/agent/runs/scenarios/healthy Idempotency-Key={k1}", "201",
       f"status={r1.status} id={(r1.data or {}).get('id')}", "high")
if r1.status != 201:
    abort(f"scenario create failed {r1.status} {r1.text[:300]}")
r1_id = (r1.data or {}).get("id")
created["scenarioHealthy"] = r1_id
record("scenario run evidence.scenario == healthy",
       ((r1.data or {}).get("plan") or {}).get("evidence", {}).get("scenario") == "healthy",
       "GET /cms/agent/runs/{id}", "evidence.scenario=healthy",
       str(((r1.data or {}).get("plan") or {}).get("evidence", {}).get("scenario")), "medium")

r1b = c.post("/cms/agent/runs/scenarios/healthy", {}, headers={"Idempotency-Key": k1})
record("same Idempotency-Key returns same run id",
       (r1b.data or {}).get("id") == r1_id and r1b.code == 0,
       f"POST /cms/agent/runs/scenarios/healthy Idempotency-Key={k1} (2nd)",
       "same run id", f"status={r1b.status} id={(r1b.data or {}).get('id')}", "blocker")
record("idempotent replay still reports 201", r1b.status == 201,
       "POST /cms/agent/runs/scenarios/healthy (replay)", "201", f"status={r1b.status}", "low")

conflict = c.post("/cms/agent/runs/scenarios/underperforming", {}, headers={"Idempotency-Key": k1})
record("reuse key for different scenario -> 409",
       conflict.status == 409,
       f"POST /cms/agent/runs/scenarios/underperforming Idempotency-Key={k1} (used for healthy)",
       "409", f"status={conflict.status} body={conflict.text[:160]}", "blocker")

k2 = str(uuid.uuid4())
r2 = c.post("/cms/agent/runs/scenarios/healthy", {}, headers={"Idempotency-Key": k2})
r2_id = (r2.data or {}).get("id")
created["scenarioHealthy2"] = r2_id
record("different key + same scenario creates new run",
       r2.status == 201 and r2_id and r2_id != r1_id,
       f"POST /cms/agent/runs/scenarios/healthy Idempotency-Key={k2}", "new run id",
       f"status={r2.status} id={r2_id} (first={r1_id})", "high")

k3 = str(uuid.uuid4())
r3 = c.post("/cms/agent/runs/scenarios/underperforming", {}, headers={"Idempotency-Key": k3})
r3_id = (r3.data or {}).get("id")
created["scenarioUnderperforming"] = r3_id
record("scenario underperforming create 201", r3.status == 201 and r3.code == 0,
       f"POST /cms/agent/runs/scenarios/underperforming key={k3}", "201",
       f"status={r3.status} id={r3_id} proposal={json.dumps(((r3.data or {}).get('plan') or {}).get('proposal'), ensure_ascii=False)}", "high")

k4 = str(uuid.uuid4())
r4 = c.post("/cms/agent/runs/scenarios/low-volume", {}, headers={"Idempotency-Key": k4})
r4_id = (r4.data or {}).get("id")
created["scenarioLowVolume"] = r4_id
record("scenario low-volume create 201", r4.status == 201 and r4.code == 0,
       f"POST /cms/agent/runs/scenarios/low-volume key={k4}", "201",
       f"status={r4.status} id={r4_id} proposal={json.dumps(((r4.data or {}).get('plan') or {}).get('proposal'), ensure_ascii=False)}", "high")

missing_key = c.post("/cms/agent/runs/scenarios/healthy", {})
record("scenario without Idempotency-Key -> 400", missing_key.status == 400,
       "POST /cms/agent/runs/scenarios/healthy (no Idempotency-Key header)", "400",
       f"status={missing_key.status}", "low")

unknown = c.post("/cms/agent/runs/scenarios/not-a-scenario", {}, headers={"Idempotency-Key": str(uuid.uuid4())})
record("unknown scenario -> 404", unknown.status == 404,
       "POST /cms/agent/runs/scenarios/not-a-scenario", "404",
       f"status={unknown.status} body={unknown.text[:120]}", "medium")

# ------------------------------------------------------- custom Input payload
home = {
    "site": "cccooperate", "channel": "4630219184",
    "campaignId": "TT_US_HomeSecuritySystems_Conv_250", "currency": "USD",
    "start": "2026-11-07", "end": "2026-11-20",
    "spend": 2140.55, "revenue": 3012.80,
    "impressions": 388420, "clicks": 6712, "conversions": 1642,
    "targetRoas": 1.30, "minimumSpend": 20, "minimumClicks": 30, "maxChangePercent": 10,
}
before_ids = {r.get("id") for r in (c.get("/cms/agent/runs").data or [])}
custom = c.post("/cms/agent/runs", home)
record("custom Input run create 201", custom.status == 201 and custom.code == 0,
       "POST /cms/agent/runs (14-day US home-security payload)", "201",
       f"status={custom.status} id={(custom.data or {}).get('id')} body={custom.text[:140]}", "high")
cplan = (custom.data or {}).get("plan") or {}
cinput = cplan.get("input") or {}
cmetrics = cplan.get("metrics") or {}
record("custom run echoes all input fields", all(str(cinput.get(k)) == str(home[k]) for k in
       ("site", "channel", "campaignId", "currency", "start", "end", "spend", "revenue",
        "impressions", "clicks", "conversions", "targetRoas", "minimumSpend", "minimumClicks", "maxChangePercent")),
       "GET /cms/agent/runs/{id}", "input identical to payload",
       json.dumps(cinput, ensure_ascii=False), "high")
created["customRun"] = (custom.data or {}).get("id")
roas = 3012.80 / 2140.55
record("custom run metrics ROAS reconciles", abs(float(cmetrics.get("roas") or 0) - roas) < 0.0005,
       "GET /cms/agent/runs/{id}", f"metrics.roas≈{roas:.4f}", f"roas={cmetrics.get('roas')}", "high")
ctr = 6712 / 388420
record("custom run metrics CTR reconciles", abs(float(cmetrics.get("ctr") or 0) - ctr) < 0.000002,
       "GET /cms/agent/runs/{id}", f"metrics.ctr≈{ctr:.6f}", f"ctr={cmetrics.get('ctr')}", "medium")
cvr = 1642 / 6712
record("custom run metrics CVR reconciles", abs(float(cmetrics.get("cvr") or 0) - cvr) < 0.000002,
       "GET /cms/agent/runs/{id}", f"metrics.cvr≈{cvr:.6f}", f"cvr={cmetrics.get('cvr')}", "medium")
cpa = 2140.55 / 1642
record("custom run metrics CPA reconciles", abs(float(cmetrics.get("cpa") or 0) - cpa) < 0.0005,
       "GET /cms/agent/runs/{id}", f"metrics.cpa≈{cpa:.4f}", f"cpa={cmetrics.get('cpa')}", "medium")
record("custom run has no fabricated evidence for unknown campaign",
       cplan.get("evidence") is None or (cplan.get("evidence") or {}).get("daily") in (None, []),
       "GET /cms/agent/runs/{id}", "evidence null/empty for non-existent campaign",
       json.dumps(cplan.get("evidence"))[:200], "low")

# --------------------------------------------------------- invalid payload 400s
cases = {
    "clicks > impressions": {**home, "clicks": 500000, "conversions": 400000},
    "conversions > clicks": {**home, "conversions": 9000},
    "bad currency (US)": {**home, "currency": "US"},
    "bad date order": {**home, "start": "2026-11-20", "end": "2026-11-07"},
    "oversized campaignId": {**home, "campaignId": "QA-" + "X" * 122},
}
for name, payload in cases.items():
    res = c.post("/cms/agent/runs", payload)
    record(f"400 for {name}", res.status == 400,
           f"POST /cms/agent/runs ({name})", "400",
           f"status={res.status} body={res.text[:160]}", "high")

after_ids = {r.get("id") for r in (c.get("/cms/agent/runs").data or [])}
record("invalid payloads did not persist runs", after_ids == before_ids | ({(custom.data or {}).get("id")} - {None}),
       "GET /cms/agent/runs after 400s", "no new runs from rejected payloads",
       f"new ids={sorted(after_ids - before_ids)}", "high")

# ------------------------------------------------------------- batch review
batch = c.post("/cms/agent/runs/batch-review",
               {"runIds": [r3_id, r4_id], "decision": "APPROVED",
                "note": "QA 批量审批：两个场景样本回收达标，批量通过"})
bdata = batch.data or {}
record("batch-review 2 pending runs -> succeeded=2 skipped=0",
       batch.code == 0 and bdata.get("succeeded") == 2 and bdata.get("skipped") == 0,
       "POST /cms/agent/runs/batch-review runIds=[underperforming, low-volume]", "succeeded=2 skipped=0",
       json.dumps(bdata, ensure_ascii=False), "high")
record("batch-review processedIds lists both runs",
       set(bdata.get("processedIds") or []) == {r3_id, r4_id},
       "POST /cms/agent/runs/batch-review", "processedIds == both ids",
       str(bdata.get("processedIds")), "medium")
states = {}
for rid in (r3_id, r4_id):
    states[rid] = (c.get(f"/cms/agent/runs/{rid}").data or {}).get("state")
record("batch-approved runs are APPROVED", set(states.values()) == {"APPROVED"},
       "GET /cms/agent/runs/{id}", "both APPROVED", json.dumps(states), "high")

rebatch = c.post("/cms/agent/runs/batch-review",
                 {"runIds": [r3_id, r4_id], "decision": "REJECTED", "note": "QA 重复批量审批"})
record("re-review of batch-reviewed runs -> 409", rebatch.status == 409,
       "POST /cms/agent/runs/batch-review (same 2 runs, decision REJECTED)", "409",
       f"status={rebatch.status} body={rebatch.text[:160]}", "medium")
record("re-review did not change state",
       (c.get(f"/cms/agent/runs/{r3_id}").data or {}).get("state") == "APPROVED",
       "GET /cms/agent/runs/{id} after rejected re-review", "APPROVED",
       (c.get(f"/cms/agent/runs/{r3_id}").data or {}).get("state"), "high")

single = c.post(f"/cms/agent/runs/{r3_id}/review", {"decision": "REJECTED", "note": "QA single re-review"})
record("single re-review of batch-approved run -> 409", single.status == 409,
       f"POST /cms/agent/runs/{r3_id}/review", "409", f"status={single.status}", "high")

mixed = c.post("/cms/agent/runs/batch-review",
               {"runIds": [r1_id, r2_id], "decision": "APPROVED", "note": "QA 混合状态批量审批"})
mdata = mixed.data or {}
record("batch-review mixed states counts succeeded/skipped honestly",
       mixed.code == 0 and (mdata.get("succeeded") or 0) >= 1 and (mdata.get("succeeded", 0) + mdata.get("skipped", 0)) == 2,
       "POST /cms/agent/runs/batch-review runIds=[healthy, healthy2]", "succeeded+skipped=2",
       json.dumps(mdata, ensure_ascii=False), "medium")

empty = c.post("/cms/agent/runs/batch-review", {"runIds": [], "decision": "APPROVED", "note": "QA empty"})
record("batch-review empty runIds -> 400", empty.status == 400,
       "POST /cms/agent/runs/batch-review runIds=[]", "400", f"status={empty.status}", "low")

audit_b = c.get(f"/cms/agent/runs/{r3_id}/audit")
stages_b = [e.get("stage") for e in (audit_b.data or [])]
actors_b = [e.get("actor") for e in (audit_b.data or [])]
record("batch review writes APPROVED audit event", "APPROVED" in stages_b,
       f"GET /cms/agent/runs/{r3_id}/audit", "APPROVED in stages", str(stages_b), "medium")
record("batch audit actor matches signed user", all(a == "local-admin" for a in actors_b),
       f"GET /cms/agent/runs/{r3_id}/audit", "actor=local-admin", f"actors={actors_b}", "medium")

(Path(__file__).resolve().parent / "out" / "agent-core-stage2-ids.json").write_text(
    json.dumps(created, ensure_ascii=False, indent=2), encoding="utf-8")
save("stage2")
print("stage2 done", json.dumps(created, ensure_ascii=False))
