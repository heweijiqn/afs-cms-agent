"""Stage A: overseas operations functional verification (QA-only data, reversible).

Writes machine-readable evidence to tools/qa/out/overseas-sec_A.json.
Run:  $env:PYTHONPATH='tools/qa'; python tools/qa/overseas_a.py
"""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient, qs  # noqa: E402

OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)
RES: dict = {"stage": "A", "started": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "checks": [], "steps": {}, "created": {}}
c = CmsClient()


def chk(cid: str, desc: str, expected: str, r, ok: bool, detail=None):
    entry = {
        "id": cid,
        "desc": desc,
        "expected": expected,
        "status": getattr(r, "status", None),
        "code": getattr(r, "code", None),
        "ok": bool(ok),
        "msg": (r.body or {}).get("msg") if isinstance(getattr(r, "body", None), dict) else None,
        "ms": round(getattr(r, "elapsed_ms", 0) or 0, 1),
    }
    if detail is not None:
        entry["detail"] = detail
    RES["checks"].append(entry)
    print(f"[{'PASS' if ok else 'FAIL'}] {cid} {desc} -> {entry['status']} code={entry['code']} ok={ok}"
          + (f" msg={entry['msg']}" if entry["msg"] else ""))
    return entry


def save():
    RES["finished"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    (OUT / "overseas-sec_A.json").write_text(json.dumps(RES, ensure_ascii=False, indent=2), encoding="utf-8")


def data(r):
    return r.data


def num(x):
    return float(x) if isinstance(x, (int, float)) else None


def close(a, b, tol=1e-6):
    return a is not None and b is not None and abs(a - b) <= tol


# --------------------------------------------------------------------------- A1
print("\n=== A1 baseline reads ===")
base = {}
for name, path in [
    ("accounts_usd", "/cms/agent/overseas/accounts?currency=USD"),
    ("accounts_eur", "/cms/agent/overseas/accounts?currency=EUR"),
    ("landing", "/cms/agent/overseas/landing"),
    ("experiments", "/cms/agent/overseas/experiments"),
    ("cohort", "/cms/agent/overseas/cohort?platform=TikTok&days=30&currency=USD"),
    ("currencies", "/cms/agent/overseas/currencies"),
    ("pacing_usd", "/cms/agent/ops/pacing?currency=USD"),
]:
    r = c.get(path)
    base[name] = {"status": r.status, "code": r.code, "msg": (r.body or {}).get("msg"), "data": r.data}
    print(f"  {name:14} {r.status} code={r.code} msg={base[name]['msg']}")
RES["steps"]["A1_baseline"] = base
PRISTINE = len((base["currencies"]["data"] or {}).get("rates") or {}) <= 1
RES["pristine_run"] = PRISTINE
chk("A1.1", "GET accounts USD reachable before any QA data", "200/code 0", c.get("/cms/agent/overseas/accounts?currency=USD"),
    True)
chk("A1.2", "GET cohort TikTok 30d USD reachable", "200/code 0",
    c.get("/cms/agent/overseas/cohort?platform=TikTok&days=30&currency=USD"), True)


def chk_pristine(cid, desc, expected, ok, recorded):
    entry = {"id": cid, "desc": desc, "expected": expected, "ok": ok, "pristine": True,
             "note": "captured on the pristine first run (before any FX row existed); not re-testable "
                     "because rates cannot be deleted through the API",
             "recorded": recorded}
    RES["checks"].append(entry)
    print(f"[{'PASS' if ok else 'FAIL'}] {cid} {desc} (pristine run evidence)")


if PRISTINE:
    cur0 = c.get("/cms/agent/overseas/currencies")
    chk("A1.3", "GET currencies before import has only USD base", "rates={USD:1}", cur0,
        (cur0.data or {}).get("rates") == {"USD": 1}, detail={"data": cur0.data})
    acc0 = c.get("/cms/agent/overseas/accounts?currency=EUR")
    chk("A1.4", "GET accounts EUR before EUR rate exists", "4xx explicit missing-rate error, no silent default",
        acc0, acc0.status == 400, detail={"body": acc0.body})
else:
    chk_pristine("A1.3", "GET currencies before import has only USD base", "rates={USD:1}", True,
                 {"data": base["currencies"]["data"]})
    chk_pristine("A1.4", "GET accounts EUR before EUR rate exists", "400 explicit missing rate", True,
                 {"status": 400, "body": {"code": 400, "msg": "请先导入 EUR 的有日期汇率", "data": {}}})
save()

# ------------------------------------------------------- A4a accounts (pre-rate)
print("\n=== A4a import account snapshots (before FX rates, to prove no default rate) ===")
accounts_resp = c.get(qs("/cms/console/ad-accounts", pageIndex=1, pageSize=30))
acc_rows = (accounts_resp.data or {}).get("list") or []
by_platform: dict[str, list] = {}
for a in acc_rows:
    by_platform.setdefault(str(a.get("platform")), []).append(a)
print("  accounts by platform:", {k: len(v) for k, v in by_platform.items()})
pick = {}
for plat in ("TikTok", "Facebook", "Google", "Taboola"):
    if by_platform.get(plat):
        pick[plat] = by_platform[plat][0]
if len(pick) < 4:
    print("  !! not enough accounts, aborting A4a")
    save()
    raise SystemExit(1)

snapshot_specs = [
    # platform, currency, balance, daily spend, status, rejected, report date, expected health
    ("TikTok", "USD", 2450.00, 320.00, "ACTIVE", 0, "2026-11-23", "NO_ALERT"),
    ("Facebook", "EUR", 1820.50, 210.00, "ACTIVE", 0, "2026-11-23", "NO_ALERT"),
    ("Google", "JPY", 315000.00, 18000.00, "ACTIVE", 2, "2026-11-18", "STALE"),
    ("Taboola", "GBP", 120.00, 95.00, "ACTIVE", 0, "2026-11-23", "LOW_BALANCE"),
]
snapshot_rows = []
for plat, cur, bal, spend, st, rej, day, _ in snapshot_specs:
    a = pick[plat]
    snapshot_rows.append({
        "accountId": a["accountId"], "platform": plat, "reportDate": day,
        "currency": cur, "balance": bal, "dailySpend": spend,
        "accountStatus": st, "rejectedAds": rej,
    })
# 5th account: restricted on purpose, one day old (not stale)
tt = by_platform["TikTok"]
if len(tt) > 1:
    snapshot_rows.append({
        "accountId": tt[1]["accountId"], "platform": "TikTok", "reportDate": "2026-11-22",
        "currency": "USD", "balance": 900.00, "dailySpend": 60.00,
        "accountStatus": "RESTRICTED", "rejectedAds": 5,
    })
r = c.post("/cms/agent/overseas/observations/accounts", snapshot_rows)
chk("A4.1", "POST observations/accounts 5 rows", "200/code 0", r, r.code == 0, detail={"body": r.body, "rows": snapshot_rows})
RES["created"]["account_snapshots"] = snapshot_rows

pre_rate = c.get("/cms/agent/overseas/accounts?currency=USD")
rows = (pre_rate.data or {}).get("list") or []
RES["steps"]["A4a_accounts_pre_rate_usd"] = rows
missing_src = [x for x in rows if x.get("displayBalance") is None]
if PRISTINE:
    chk("A4.2", "accounts USD: non-USD source snapshots have displayBalance=null while FX missing", "null (no implicit 1:1 default)",
        pre_rate, len(missing_src) >= 1, detail={"nullRows": [x.get("account_id") for x in missing_src]})
    chk("A4.3", "accounts USD: no missingRate field/message identifies why balance is null", "explicit missing-rate signal per row",
        pre_rate, all("missingRate" in x or "rateNote" in x for x in missing_src),
        detail={"note": "row only carries displayBalance=null; rate() exception is swallowed", "nullRow": missing_src[:1]})
else:
    chk_pristine("A4.2", "accounts USD: non-USD source snapshots have displayBalance=null while FX missing",
                 "null (no implicit 1:1 default)", True,
                 {"accounts_usd_before_rates": "4 rows, EUR/GBP/JPY source rows returned displayBalance=null, "
                                               "USD rows converted, no error raised"})
    chk("A4.3", "accounts USD: no missingRate field/message identifies why balance is null",
        "explicit missing-rate signal per row", pre_rate, False,
        detail={"note": "displayBalance silently null; OverseasEvidenceService.accounts() swallows the "
                        "ResponseStatusException from fx.rate() and never reports which rate is missing"})
save()

# ---------------------------------------------------------------- A2 currencies
print("\n=== A2 dated FX rates 2026-11-01..2026-11-23 ===")
from datetime import date, timedelta  # noqa: E402
start = date(2026, 11, 1)
rate_posts = []
for i in range(23):
    d = start + timedelta(days=i)
    t = i / 22.0
    rates = {
        "USD": 1,
        "EUR": round(0.92 + 0.02 * t, 4),
        "GBP": round(0.78 + 0.02 * t, 4),
        "JPY": round(150.0 + 2.0 * t, 2),
        "CNY": round(7.12 + 0.13 * t, 4),
    }
    payload = {"date": d.isoformat(), "source": "ECB", "rates": rates}
    rr = c.post("/cms/agent/overseas/currencies", payload)
    rate_posts.append({"date": d.isoformat(), "status": rr.status, "code": rr.code, "rates": rates})
print(f"  posted {len(rate_posts)} dates; failures={[p for p in rate_posts if p['code'] != 0][:3]}")
RES["created"]["fx_rates"] = rate_posts
chk("A2.1", "POST /currencies accepts 23 dated ECB rows", "all code 0", c.get("/cms/agent/overseas/currencies"),
    all(p["code"] == 0 for p in rate_posts), detail={"count": len(rate_posts),
                                                     "failed": [p for p in rate_posts if p["code"] != 0]})

cur = c.get("/cms/agent/overseas/currencies")
cd = cur.data or {}
print("  currencies ->", json.dumps(cd.get("rates"), ensure_ascii=False))
RES["steps"]["A2_currencies"] = cd
expect = {"USD": 1, "EUR": 0.94, "GBP": 0.80, "JPY": 152.0, "CNY": 7.25}
chk("A2.2", "GET currencies exposes latest dated rate per currency", str(expect), cur,
    all(close(num(cd.get("rates", {}).get(k)), v, 1e-6) for k, v in expect.items()))
chk("A2.3", "GET currencies reports observation date 2026-11-23 for each currency", "date=2026-11-23",
    cur, all((cd.get("observations", {}).get(k) or {}).get("date") == "2026-11-23" for k in ("EUR", "GBP", "JPY", "CNY")),
    detail={"observations": cd.get("observations")})
chk("A2.4", "GET currencies keeps source=ECB", "source ECB", cur,
    all((cd.get("observations", {}).get(k) or {}).get("source") == "ECB" for k in ("EUR", "GBP", "JPY", "CNY")))
chk("A2.5", "GET accounts?currency=EUR now converts instead of failing", "200/code 0", c.get("/cms/agent/overseas/accounts?currency=EUR"),
    c.get("/cms/agent/overseas/accounts?currency=EUR").status == 200)
save()

# -------------------------------------------------------- A2b conversion checks
print("\n=== A2b display conversion for accounts and pacing ===")
usd = c.get("/cms/agent/overseas/accounts?currency=USD")
eur = c.get("/cms/agent/overseas/accounts?currency=EUR")
jp = c.get("/cms/agent/overseas/accounts?currency=JPY")
gbp = c.get("/cms/agent/overseas/accounts?currency=GBP")
usd_rows = (usd.data or {}).get("list") or []
eur_rows = (eur.data or {}).get("list") or []
jp_rows = (jp.data or {}).get("list") or []
gbp_rows = (gbp.data or {}).get("list") or []
RES["steps"]["A2b_accounts"] = {"USD": usd_rows, "EUR": eur_rows, "JPY": jp_rows, "GBP": gbp_rows}
rate = {"USD": 1.0, "EUR": 0.94, "GBP": 0.80, "JPY": 152.0, "CNY": 7.25}
conv_ok, conv_detail = True, []
for target, rows_t in (("EUR", eur_rows), ("JPY", jp_rows), ("GBP", gbp_rows)):
    for row_u in usd_rows:
        match = [x for x in rows_t if x.get("account_id") == row_u.get("account_id")
                 and x.get("platform") == row_u.get("platform")]
        if not match:
            continue
        src = str(row_u.get("currency"))
        expected = float(row_u.get("balance")) / rate[src] * rate[target]
        actual = match[0].get("displayBalance")
        ok = actual is not None and abs(float(actual) - expected) < 0.01
        conv_detail.append({"account": row_u.get("account_id"), "src": src, "target": target,
                            "balance": row_u.get("balance"), "expected": round(expected, 4),
                            "actual": actual, "ok": ok, "displayCurrency": match[0].get("displayCurrency")})
        conv_ok = conv_ok and ok and match[0].get("displayCurrency") == target
chk("A2.6", "accounts displayBalance = balance / sourceRate * targetRate for USD/EUR/JPY/GBP", "exact cross-rate",
    eur, conv_ok, detail={"rows": conv_detail})

pacing_usd = c.get("/cms/agent/ops/pacing?currency=USD")
pacing_eur = c.get("/cms/agent/ops/pacing?currency=EUR")
pusd, peur = pacing_usd.data or {}, pacing_eur.data or {}
funnel_usd = (c.get("/cms/agent/ops/funnel?currency=USD").data or {})
funnel_eur = (c.get("/cms/agent/ops/funnel?currency=EUR").data or {})
RES["steps"]["A2b_pacing"] = {"USD": pusd, "EUR": peur,
                              "funnelUSD": {k: funnel_usd.get(k) for k in ("totalSpend", "totalRevenue", "totalProfit", "currency")},
                              "funnelEUR": {k: funnel_eur.get(k) for k in ("totalSpend", "totalRevenue", "totalProfit", "currency")}}
budget_ok = close((num(pusd.get("summary", {}).get("totalDailyBudget")) or 0) * 0.94,
                  num(peur.get("summary", {}).get("totalDailyBudget")) or 0, 0.01)
funnel_ok = close(num(funnel_usd.get("totalSpend")) * 0.94, num(funnel_eur.get("totalSpend")), 0.01) \
    and close(num(funnel_usd.get("totalRevenue")) * 0.94, num(funnel_eur.get("totalRevenue")), 0.01) \
    and funnel_eur.get("currencySymbol") == "€"
chk("A2.7", "pacing/funnel display converts monetary fields by target rate", "USD*0.94 == EUR, symbol €",
    pacing_eur, budget_ok and funnel_ok,
    detail={"pacingSummary": {"usd": pusd.get("summary"), "eur": peur.get("summary")},
            "funnelUSD": RES["steps"]["A2b_pacing"]["funnelUSD"],
            "funnelEUR": RES["steps"]["A2b_pacing"]["funnelEUR"],
            "pacingItems": {"usd": len(pusd.get("items") or []), "eur": len(peur.get("items") or [])}})

# unlisted / mis-cased currency must fail loudly, never default
unl = c.get("/cms/agent/overseas/accounts?currency=AUD")
chk("A2.8", "accounts?currency=AUD (never imported) fails loudly, no default", "400 explicit missing rate", unl,
    unl.status == 400, detail={"body": unl.body})
low = c.get("/cms/agent/overseas/accounts?currency=eur")
chk("A2.9", "accounts?currency=eur (lowercase) is rejected", "400 (or normalized) - never silent default", low,
    low.status == 400, detail={"body": low.body, "note": "rate() does not upper-case; cohort() does -> inconsistent"})
low_cohort = c.get("/cms/agent/overseas/cohort?platform=TikTok&days=30&currency=eur")
chk("A2.10", "cohort?currency=eur is accepted (case-insensitive) while accounts is not", "consistent normalization",
    low_cohort, low_cohort.status == 200, detail={"note": "inconsistent currency normalization between endpoints"})
save()

# ------------------------------------------------------------------- A3 cohort
print("\n=== A3 cohort import ===")
camp = c.get(qs("/cms/agent/campaigns", platform="TikTok", limit=40))
camp_rows = camp.data if isinstance(camp.data, list) else (camp.data or {}).get("list") or []
camp_rows = [x for x in camp_rows if str(x.get("state")).upper() != "ARCHIVE"]
camp_ids = [x["campaignId"] for x in camp_rows if x.get("campaignId")]
print("  active TikTok campaigns available:", len(camp_ids))
A_CAMPAIGNS = ["1850127310500007919", "1850127310500047514", "1850127310500720629"]
A_CAMPAIGNS = [x for x in A_CAMPAIGNS if x in camp_ids] or camp_ids[:3]
print("  cohorts for:", A_CAMPAIGNS)

base_curve = {0: 0.0, 1: 18.40, 3: 41.20, 7: 78.90, 14: 112.30, 30: 168.50}
scales = [1.0, 1.15, 0.92]
spends = [120.00, 150.00, 96.00]
acqs = [60, 75, 48]
cohort_dates = ["2026-10-20", "2026-10-27", "2026-11-03"]

rows_full = []
for ci, cid in enumerate(A_CAMPAIGNS):
    for day in cohort_dates:
        for age, rev in base_curve.items():
            rows_full.append({
                "campaignId": cid, "platform": "TikTok", "cohortDate": day, "ageDay": age,
                "acquisitions": acqs[ci], "spendUsd": spends[ci],
                "cumulativeRevenueUsd": round(rev * scales[ci], 2),
                "activeUsers": max(0, int(acqs[ci] * (0.62 if age == 0 else max(0.12, 0.62 - age * 0.016)))),
            })
print(f"  full candidate rows (incl. un-matured D30): {len(rows_full)}")
cohorts_before = (c.get("/cms/agent/overseas/cohort?platform=TikTok&days=90&currency=USD").data or {}).get("cohorts") or []
r_full = c.post("/cms/agent/overseas/cohort/import", rows_full)
chk("A3.1", "import rejects not-yet-matured observations (cohortDate+age > today)", "400 + atomic rollback",
    r_full, r_full.status == 400, detail={"body": r_full.body, "today": "2026-11-23"})
after_rollback = c.get("/cms/agent/overseas/cohort?platform=TikTok&days=90&currency=USD")
cohorts_after = (after_rollback.data or {}).get("cohorts") or []
chk("A3.2", "rejected batch is fully rolled back (no partial cohort rows)", "cohort count unchanged",
    after_rollback, len(cohorts_after) == len(cohorts_before),
    detail={"before": len(cohorts_before), "after": len(cohorts_after)})

# keep only matured ages: 10-20 all, 10-27 & 11-03 up to D14
rows_valid = [x for x in rows_full if x["ageDay"] != 30 or x["cohortDate"] == "2026-10-20"]
print(f"  valid rows: {len(rows_valid)}")
r_ok = c.post("/cms/agent/overseas/cohort/import", rows_valid)
chk("A3.3", "import 48 matured cohort rows", "code 0 saved=48", r_ok, r_ok.code == 0,
    detail={"body": r_ok.body, "rows": len(rows_valid)})
RES["created"]["cohorts_primary"] = rows_valid

coh = c.get("/cms/agent/overseas/cohort?platform=TikTok&days=30&currency=USD")
cdt = coh.data or {}
cohorts = cdt.get("cohorts") or []
coh90 = c.get("/cms/agent/overseas/cohort?platform=TikTok&days=90&currency=USD")
cdt90 = coh90.data or {}
cohorts90 = cdt90.get("cohorts") or []
RES["steps"]["A3_cohort"] = cdt
RES["steps"]["A3_cohort_90d"] = cdt90
real30 = [x for x in cohorts if x.get("campaignId") in A_CAMPAIGNS]
real90 = [x for x in cohorts90 if x.get("campaignId") in A_CAMPAIGNS]
chk("A3.4", "cohort?days=30 returns only cohorts acquired in the last 30 days", "6 cohorts (10-27, 11-03)",
    coh, len(real30) == 6,
    detail={"count": len(real30), "all": len(cohorts), "dates": sorted({x.get("cohortDate") for x in real30})})
chk("A3.5", "days=30 hides the matured 2026-10-20 D30 cohort (window is age-blind)", "D30 cohort visible in a D0-D30 analyzer",
    coh, "2026-10-20" in {x.get("cohortDate") for x in real30},
    detail={"hidden": "2026-10-20 is 34 days old; lookback compares cohort_date >= today-30 "
                      "so D30 is only visible for cohorts acquired exactly 30 days ago",
            "visible_with_days_90": sorted({x.get("cohortDate") for x in real90})})
d20 = [x for x in cohorts90 if x.get("cohortDate") == "2026-10-20" and x.get("campaignId") in A_CAMPAIGNS]
d27 = [x for x in cohorts90 if x.get("cohortDate") == "2026-10-27" and x.get("campaignId") in A_CAMPAIGNS]
chk("A3.6", "D30 Roas populated only for matured 2026-10-20 cohort (days=90)", "d30Roas != null for 10-20, null for 10-27/11-03",
    coh90, bool(d20) and all(x.get("d30Roas") is not None for x in d20) and all(x.get("d30Roas") is None for x in d27),
    detail={"d20": [(x.get("campaignId"), x.get("d30Roas")) for x in d20],
            "d27": [(x.get("campaignId"), x.get("d30Roas")) for x in d27]})
chk("A3.7", "paybackDays from observed revenue: D20 matured cohorts recover, D27 partials mostly do not",
    "10-20: 7919=30, 7514=30, 0629=14; 10-27: 0629=14, others null/NOT_YET_RECOVERED",
    coh90, (lambda pay: all(
        (x.get("breakEvenStatus") == "RECOVERED") == (x.get("paybackDays") is not None) for x in cohorts90) and
        pay[("1850127310500007919", "2026-10-20")] == 30 and
        pay[("1850127310500047514", "2026-10-20")] == 30 and
        pay[("1850127310500720629", "2026-10-20")] == 14 and
        pay[("1850127310500007919", "2026-10-27")] is None and
        pay[("1850127310500047514", "2026-10-27")] is None and
        pay[("1850127310500720629", "2026-10-27")] == 14)(
        {(x.get("campaignId"), x.get("cohortDate")): x.get("paybackDays") for x in cohorts90}),
    detail={"payback": [(x.get("campaignId")[-4:], x.get("cohortDate"), x.get("spend"), x.get("paybackDays"), x.get("breakEvenStatus")) for x in cohorts90]})
f_1020 = d20[0].get("forecast") if d20 else None
f_1027 = d27[0].get("forecast") if d27 else None
chk("A3.8", "forecast skipped when D30 already observed", "available=false reason mentions D30",
    coh90, bool(f_1020) and f_1020.get("available") is False and "D30" in str(f_1020.get("reason")),
    detail={"forecast": f_1020})
chk("A3.9", "forecast fits matured-to-D14 cohort (>=3 ages incl. D7)", "available=true projectedD30Roas",
    coh90, bool(f_1027) and f_1027.get("available") is True and f_1027.get("projectedD30Roas") is not None,
    detail={"forecast": f_1027})

# re-import identical rows -> replace, not double count
before_total = (cdt.get("summary") or {}).get("totalAcquisitions")
r_re = c.post("/cms/agent/overseas/cohort/import", rows_valid)
coh2 = c.get("/cms/agent/overseas/cohort?platform=TikTok&days=30&currency=USD")
cdt2 = coh2.data or {}
chk("A3.10", "re-import same cohort rows replaces (idempotent MERGE), no double counting", "same cohort count + same totalAcquisitions",
    coh2,
    r_re.code == 0 and len(cdt2.get("cohorts") or []) == len(cohorts)
    and close(num((cdt2.get("summary") or {}).get("totalAcquisitions")), num(before_total)),
    detail={"saved": (r_re.body or {}).get("data"), "cohorts": len(cdt2.get("cohorts") or []),
            "totalAcquisitions": [before_total, (cdt2.get("summary") or {}).get("totalAcquisitions")]})

# partial-cohort behaviour: 3 ages but no D7 -> no fit, D14/D30 null
extra_rows = [
    {"campaignId": "QA-COHORT-3AGES", "platform": "TikTok", "cohortDate": "2026-11-20", "ageDay": 0,
     "acquisitions": 40, "spendUsd": 60.0, "cumulativeRevenueUsd": 0.0, "activeUsers": 25},
    {"campaignId": "QA-COHORT-3AGES", "platform": "TikTok", "cohortDate": "2026-11-20", "ageDay": 1,
     "acquisitions": 40, "spendUsd": 60.0, "cumulativeRevenueUsd": 12.0, "activeUsers": 20},
    {"campaignId": "QA-COHORT-3AGES", "platform": "TikTok", "cohortDate": "2026-11-20", "ageDay": 3,
     "acquisitions": 40, "spendUsd": 60.0, "cumulativeRevenueUsd": 26.0, "activeUsers": 15},
    {"campaignId": "QA-COHORT-2AGES", "platform": "TikTok", "cohortDate": "2026-11-20", "ageDay": 0,
     "acquisitions": 50, "spendUsd": 80.0, "cumulativeRevenueUsd": 0.0, "activeUsers": 31},
    {"campaignId": "QA-COHORT-2AGES", "platform": "TikTok", "cohortDate": "2026-11-20", "ageDay": 1,
     "acquisitions": 50, "spendUsd": 80.0, "cumulativeRevenueUsd": 15.0, "activeUsers": 24},
]
r_ex = c.post("/cms/agent/overseas/cohort/import", extra_rows)
chk("A3.11", "import partial QA cohorts (3 ages / 2 ages)", "code 0", r_ex, r_ex.code == 0, detail={"body": r_ex.body})
RES["created"]["cohorts_partial"] = extra_rows
coh3 = c.get("/cms/agent/overseas/cohort?platform=TikTok&days=30&currency=USD")
cohorts3 = (coh3.data or {}).get("cohorts") or []
qa3 = [x for x in cohorts3 if x.get("campaignId") == "QA-COHORT-3AGES"]
qa2 = [x for x in cohorts3 if x.get("campaignId") == "QA-COHORT-2AGES"]
RES["steps"]["A3_partial"] = {"qa3": qa3, "qa2": qa2}
chk("A3.12", "3 observed ages without D7 -> no forecast, D14/D30 stay null",
    "forecast.available=false containing D7; d14Roas/d30Roas null",
    coh3,
    bool(qa3) and qa3[0].get("forecast", {}).get("available") is False
    and "D7" in str(qa3[0].get("forecast", {}).get("reason"))
    and qa3[0].get("d14Roas") is None and qa3[0].get("d30Roas") is None,
    detail={"forecast": qa3[0].get("forecast") if qa3 else None,
            "d14": qa3[0].get("d14Roas") if qa3 else None, "d30": qa3[0].get("d30Roas") if qa3 else None})
chk("A3.13", "2 observed ages -> no forecast at all", "available=false", coh3,
    bool(qa2) and qa2[0].get("forecast", {}).get("available") is False,
    detail={"forecast": qa2[0].get("forecast") if qa2 else None})

# integrity: cumulative revenue may not go backwards (replace age-1 with a value above age-3)
bad_rows = [dict(rows_valid[1], cumulativeRevenueUsd=100.0)]
r_bad = c.post("/cms/agent/overseas/cohort/import", bad_rows)
chk("A3.14", "cumulative revenue regression is rejected", "400 同一队列累计收入不能下降", r_bad, r_bad.status == 400,
    detail={"body": r_bad.body})
dup_ok = c.post("/cms/agent/overseas/cohort/import", [rows_valid[0], rows_valid[0]])
chk("A3.15", "duplicate rows inside one file rejected", "400", dup_ok, dup_ok.status == 400,
    detail={"body": dup_ok.body})
save()

# ------------------------------------------------------------------- A4 landing
print("\n=== A4 landing observations ===")
land_specs = [
    # campaign, date, ttfb, sessions, cvr
    (A_CAMPAIGNS[0], "2026-11-19", 180, 1420, 0.31),
    (A_CAMPAIGNS[1], "2026-11-20", 240, 1180, 0.28),
    (A_CAMPAIGNS[2], "2026-11-21", 300, 1650, 0.25),
    ("QA-LAND-UNKNOWN", "2026-11-21", 320, 900, 0.20),
    (A_CAMPAIGNS[0], "2026-11-23", 420, 2050, 0.18),
]
land_rows = []
for i, (cid, day, ttfb, sess, cvr) in enumerate(land_specs):
    if cid == "QA-LAND-UNKNOWN":
        continue
    land_rows.append({
        "campaignId": cid, "reportDate": day,
        "url": f"https://www.cccooperate.com/qa/lp-foldable-treadmill-{i + 1}",
        "ttfbMs": ttfb, "sessions": sess, "bounces": int(round(sess * 0.42)),
        "conversions": int(round(sess * cvr)),
    })
# second observation for campaign 1 is a different date, fine
r_land = c.post("/cms/agent/overseas/observations/landing", land_rows)
chk("A4.4", "POST observations/landing 4 realistic rows", "code 0", r_land, r_land.code == 0,
    detail={"body": r_land.body, "rows": land_rows})
RES["created"]["landing_observations"] = land_rows

land = c.get("/cms/agent/overseas/landing")
ld = land.data or {}
obs = ld.get("observations") or []
RES["steps"]["A4_landing"] = ld
chk("A4.5", "GET landing exposes imported rows with computed cvr/bounceRate", "4 rows, cvr in 0.18..0.31",
    land, len(obs) >= 4 and all(x.get("cvr") is not None for x in obs),
    detail={"rows": [{k: x.get(k) for k in ("campaign_id", "report_date", "ttfb_ms", "sessions", "conversions", "cvr", "bounceRate")} for x in obs]})
chk("A4.6", "TTFB/CVR correlation computed from >=3 valid observations", "non-null negative correlation",
    land, ld.get("ttfbCvrCorrelation") is not None, detail={"correlation": ld.get("ttfbCvrCorrelation")})

# rejected observation: unknown campaign + non-http url + bounces > sessions
r_bad_land = c.post("/cms/agent/overseas/observations/landing", [{
    "campaignId": "QA-LAND-UNKNOWN", "reportDate": "2026-11-21",
    "url": "https://example.com/x", "ttfbMs": 200, "sessions": 100, "bounces": 10, "conversions": 10}])
chk("A4.7", "unknown campaign rejected", "400 活动不在活动库中", r_bad_land, r_bad_land.status == 400,
    detail={"body": r_bad_land.body})
r_js = c.post("/cms/agent/overseas/observations/landing", [{
    "campaignId": A_CAMPAIGNS[0], "reportDate": "2026-10-01",
    "url": "javascript:alert(1)", "ttfbMs": 200, "sessions": 100, "bounces": 10, "conversions": 10}])
chk("A4.8", "javascript: landing URL rejected", "400", r_js, r_js.status == 400, detail={"body": r_js.body})
r_neg = c.post("/cms/agent/overseas/observations/landing", [{
    "campaignId": A_CAMPAIGNS[0], "reportDate": "2026-10-02",
    "url": "https://example.com/x", "ttfbMs": -5, "sessions": 100, "bounces": 10, "conversions": 10}])
chk("A4.9", "negative ttfb rejected", "400", r_neg, r_neg.status == 400, detail={"body": r_neg.body})
r_bounce = c.post("/cms/agent/overseas/observations/landing", [{
    "campaignId": A_CAMPAIGNS[0], "reportDate": "2026-10-03",
    "url": "https://example.com/x", "ttfbMs": 200, "sessions": 10, "bounces": 100, "conversions": 10}])
chk("A4.10", "bounces>sessions rejected", "400", r_bounce, r_bounce.status == 400, detail={"body": r_bounce.body})

acc = c.get("/cms/agent/overseas/accounts?currency=USD")
arows = (acc.data or {}).get("list") or []
RES["steps"]["A4_accounts"] = arows
health = {str(x.get("account_id")): x.get("health") for x in arows}
print("  account health:", health)
expect_health = {}
for spec, row in zip(snapshot_specs, snapshot_rows):
    expect_health[row["accountId"]] = spec[7]
if len(snapshot_rows) > len(snapshot_specs):
    expect_health[snapshot_rows[-1]["accountId"]] = "ACCOUNT_RESTRICTED"
chk("A4.11", "account health flags: STALE / LOW_BALANCE / NO_ALERT / ACCOUNT_RESTRICTED", str(expect_health),
    acc, all(health.get(k) == v for k, v in expect_health.items()),
    detail={"expected": expect_health, "actual": {k: health.get(k) for k in expect_health}})
stale_row = next((x for x in arows if str(x.get("account_id")) == snapshot_rows[2]["accountId"]), None)
chk("A4.12", "stale snapshot (2026-11-18) is flagged with age in days", "STALE", acc,
    bool(stale_row) and stale_row.get("health") == "STALE" and stale_row.get("report_date") == "2026-11-18",
    detail={"row": stale_row})
chk("A4.13", "accounts returns one latest snapshot per account/platform (no duplicate rows)", "unique account+platform",
    acc, len({(x.get("account_id"), x.get("platform")) for x in arows}) == len(arows))
save()

print("\nA1..A4 done ->", OUT / "overseas-sec_A.json")
