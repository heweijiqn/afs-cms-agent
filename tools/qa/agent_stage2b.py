"""Stage 2b: custom Input payload, validation statuses, batch review (scenario runs already created)."""
from __future__ import annotations

import json
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agent_common import c, record, save, rows  # noqa: E402

created: dict = {}
TT_TREADMILL = "1850127310500047514"

sc = c.get("/cms/agent/runs/scenarios")
srows = rows(sc)
ids = [s.get("id") for s in srows]
record("GET /runs/scenarios returns catalog",
       sc.code == 0 and {"healthy", "underperforming", "low-volume"} <= set(ids),
       "GET /cms/agent/runs/scenarios", "healthy/underperforming/low-volume",
       f"status={sc.status} ids={ids}", "high")
record("scenario catalog carries campaignId/currency/budget",
       all({"id", "campaignId", "currency", "currentBudget"} <= set(s) for s in srows),
       "GET /cms/agent/runs/scenarios", "id/campaignId/currency/currentBudget",
       json.dumps(srows[:2], ensure_ascii=False), "low")

# scenario without Idempotency-Key: record the real status/code pair
no_key = c.post("/cms/agent/runs/scenarios/healthy", {})
record("scenario without Idempotency-Key -> HTTP 400",
       no_key.status == 400,
       "POST /cms/agent/runs/scenarios/healthy (no Idempotency-Key header)",
       "HTTP 400 missing required header",
       f"HTTP {no_key.status} code={no_key.code} body={no_key.text[:120]}", "medium")

# ------------------------------------------------------- custom Input payload
home = {
    "site": "cccooperate", "channel": "4630219184",
    "campaignId": "TT_US_HomeSecuritySystems_Conv_250", "currency": "USD",
    "start": "2026-11-07", "end": "2026-11-20",
    "spend": 2140.55, "revenue": 3012.80,
    "impressions": 388420, "clicks": 6712, "conversions": 1642,
    "targetRoas": 1.30, "minimumSpend": 20, "minimumClicks": 30, "maxChangePercent": 10,
}
before_ids = {r.get("id") for r in rows(c.get("/cms/agent/runs"))}
custom = c.post("/cms/agent/runs", home)
record("custom Input run create 201", custom.status == 201 and custom.code == 0,
       "POST /cms/agent/runs (14-day US home-security payload)", "201",
       f"status={custom.status} code={custom.code} body={custom.text[:140]}", "high")
custom_id = (custom.data or {}).get("id")
created["customRun"] = custom_id
cplan = (custom.data or {}).get("plan") or {}
cinput = cplan.get("input") or {}
cmetrics = cplan.get("metrics") or {}
record("custom run echoes all input fields",
       all(str(cinput.get(k)) == str(home[k]) for k in home),
       "GET /cms/agent/runs/{id}", "input identical to payload",
       json.dumps(cinput, ensure_ascii=False), "high")
roas = 3012.80 / 2140.55
record("custom run metrics ROAS reconciles", abs(float(cmetrics.get("roas") or 0) - roas) < 0.0005,
       "GET /cms/agent/runs/{id}", f"metrics.roas≈{roas:.4f}", f"roas={cmetrics.get('roas')}", "high")
record("custom run metrics CTR reconciles", abs(float(cmetrics.get("ctr") or 0) - 6712 / 388420) < 0.000002,
       "GET /cms/agent/runs/{id}", "ctr≈0.017280", f"ctr={cmetrics.get('ctr')}", "medium")
record("custom run metrics CVR reconciles", abs(float(cmetrics.get("cvr") or 0) - 1642 / 6712) < 0.000002,
       "GET /cms/agent/runs/{id}", "cvr≈0.244636", f"cvr={cmetrics.get('cvr')}", "medium")
record("custom run metrics CPA reconciles", abs(float(cmetrics.get("cpa") or 0) - 2140.55 / 1642) < 0.0005,
       "GET /cms/agent/runs/{id}", "cpa≈1.3036", f"cpa={cmetrics.get('cpa')}", "medium")
ev = cplan.get("evidence")
ev_days = (ev or {}).get("daily") or [] if isinstance(ev, dict) else []
record("custom run evidence belongs to the requested campaign",
       not ev_days or str((ev or {}).get("campaignId")) == str(cinput.get("campaignId")),
       "GET /cms/agent/runs/{id}", "evidence.campaignId == input.campaignId (or empty)",
       f"evidence.campaignId={(ev or {}).get('campaignId') if isinstance(ev, dict) else None} days={len(ev_days)}",
       "medium")

# --------------------------------------------------------- invalid payload 4xx
cases = {
    "clicks > impressions": {**home, "clicks": 500000, "conversions": 400000},
    "conversions > clicks": {**home, "conversions": 9000},
    "bad currency (US)": {**home, "currency": "US"},
    "bad date order": {**home, "start": "2026-11-20", "end": "2026-11-07"},
    "oversized campaignId": {**home, "campaignId": "QA-" + "X" * 122},
    "negative spend": {**home, "spend": -5},
    "maxChangePercent 50": {**home, "maxChangePercent": 50},
}
for name, payload in cases.items():
    res = c.post("/cms/agent/runs", payload)
    record(f"400 for {name}", res.status == 400,
           f"POST /cms/agent/runs ({name})", "HTTP 400",
           f"HTTP {res.status} code={res.code} body={res.text[:140]}", "high")

after_ids = {r.get("id") for r in rows(c.get("/cms/agent/runs"))}
record("invalid payloads did not persist runs",
       after_ids == before_ids | ({custom_id} - {None}),
       "GET /cms/agent/runs after rejected payloads", "no new runs from rejected payloads",
       f"unexpected new ids={sorted(after_ids - before_ids - {custom_id})}", "high")

# ------------------------------------------------------------- batch review
r3 = "0ba3dc75-0bf1-4f4f-86cb-e51a7f60913d"   # scenario underperforming
r4 = "7465b84a-1860-44eb-aa67-d9f0655b5072"   # scenario low-volume
r1 = "95e55c17-bcc8-40b5-a778-23257c50b9ef"   # scenario healthy
r2 = "550df5e2-da06-4b17-a9b9-bc4b9f0386b5"   # scenario healthy (2nd key)
created.update({"scenarioHealthy": r1, "scenarioHealthy2": r2,
                "scenarioUnderperforming": r3, "scenarioLowVolume": r4})

batch = c.post("/cms/agent/runs/batch-review",
               {"runIds": [r3, r4], "decision": "APPROVED",
                "note": "QA 批量审批：两个场景样本回收达标，批量通过"})
bdata = batch.data or {}
record("batch-review 2 pending runs -> succeeded=2 skipped=0",
       batch.code == 0 and bdata.get("succeeded") == 2 and bdata.get("skipped") == 0,
       "POST /cms/agent/runs/batch-review runIds=[underperforming, low-volume]", "succeeded=2 skipped=0",
       json.dumps(bdata, ensure_ascii=False), "high")
record("batch-review processedIds lists both runs",
       set(bdata.get("processedIds") or []) == {r3, r4},
       "POST /cms/agent/runs/batch-review", "processedIds == both ids",
       str(bdata.get("processedIds")), "medium")
states = {rid: (c.get(f"/cms/agent/runs/{rid}").data or {}).get("state") for rid in (r3, r4)}
record("batch-approved runs are APPROVED", set(states.values()) == {"APPROVED"},
       "GET /cms/agent/runs/{id}", "both APPROVED", json.dumps(states), "high")

rebatch = c.post("/cms/agent/runs/batch-review",
                 {"runIds": [r3, r4], "decision": "REJECTED", "note": "QA 重复批量审批"})
rbd = rebatch.data or {}
record("re-review of batch-reviewed runs -> 409", rebatch.status == 409,
       "POST /cms/agent/runs/batch-review (same 2 runs, decision REJECTED)", "409",
       f"HTTP {rebatch.status} code={rebatch.code} body={rebatch.text[:160]}", "medium")
record("re-review did not change state",
       (c.get(f"/cms/agent/runs/{r3}").data or {}).get("state") == "APPROVED",
       "GET /cms/agent/runs/{id} after rejected re-review", "APPROVED",
       (c.get(f"/cms/agent/runs/{r3}").data or {}).get("state"), "high")
single = c.post(f"/cms/agent/runs/{r3}/review", {"decision": "REJECTED", "note": "QA single re-review"})
record("single re-review of batch-approved run -> 409", single.status == 409,
       f"POST /cms/agent/runs/{r3}/review", "409", f"status={single.status}", "high")

mixed = c.post("/cms/agent/runs/batch-review",
               {"runIds": [r1, r2], "decision": "APPROVED", "note": "QA 混合状态批量审批"})
mdata = mixed.data or {}
record("batch-review on 2 pending healthy scenarios succeeds",
       mixed.code == 0 and (mdata.get("succeeded", 0) + mdata.get("skipped", 0)) == 2,
       "POST /cms/agent/runs/batch-review runIds=[healthy, healthy2]", "succeeded+skipped=2",
       json.dumps(mdata, ensure_ascii=False), "medium")
empty = c.post("/cms/agent/runs/batch-review", {"runIds": [], "decision": "APPROVED", "note": "QA empty"})
record("batch-review empty runIds -> 400", empty.status == 400,
       "POST /cms/agent/runs/batch-review runIds=[]", "400",
       f"HTTP {empty.status} code={empty.code} body={empty.text[:120]}", "low")

audit_b = c.get(f"/cms/agent/runs/{r3}/audit")
stages_b = [e.get("stage") for e in rows(audit_b)]
actors_b = [e.get("actor") for e in rows(audit_b)]
record("batch review writes APPROVED audit event", "APPROVED" in stages_b,
       f"GET /cms/agent/runs/{r3}/audit", "APPROVED in stages", str(stages_b), "medium")
record("batch audit actor matches signed user", all(a == "local-admin" for a in actors_b),
       f"GET /cms/agent/runs/{r3}/audit", "actor=local-admin", f"actors={actors_b}", "medium")

# do scenario runs actually get analyzed?
r3run = c.get(f"/cms/agent/runs/{r3}").data or {}
r3src = ((r3run.get("plan") or {}).get("explanation") or {}).get("source")
record("scenario run received an AI analysis", r3src not in (None, "", "PENDING"),
       f"GET /cms/agent/runs/{r3}", "explanation.source != PENDING", f"source={r3src}", "medium")

(Path(__file__).resolve().parent / "out" / "agent-core-stage2-ids.json").write_text(
    json.dumps(created, ensure_ascii=False, indent=2), encoding="utf-8")
save("stage2")
print("stage2b done", json.dumps(created, ensure_ascii=False))
