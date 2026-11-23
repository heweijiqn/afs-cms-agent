"""Dataset integrity audit over the live local CMS APIs."""
from __future__ import annotations

import collections
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient, qs

OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)
c = CmsClient()
findings: list[dict] = []


def note(severity, title, detail):
    findings.append({"severity": severity, "title": title, "detail": detail})
    print(f"[{severity}] {title}: {detail}")


rows: list[dict] = []
page = 1
while True:
    r = c.post(qs("/cms/ad/resource/list", pageIndex=page, pageSize=100), {})
    if r.code != 0:
        note("high", "ad_resource list failed", f"page={page} status={r.status} body={r.text[:300]}")
        break
    chunk = (r.data or {}).get("list") or []
    rows.extend(chunk)
    if len(chunk) < 100 or page >= 10:
        break
    page += 1

print(f"campaign rows={len(rows)}")
by_platform = collections.Counter()
by_state = collections.Counter()
names = collections.Counter()
channels = collections.Counter()
sites = set(c.get("/cms/site/list").data.get("list") or [])
countries = collections.Counter()
bad_math = []
bad_country = []
unknown_site = []
bad_state = []
no_channel = []
no_landing = []
zero_or_negative = []
for row in rows:
    by_platform.update(row.get("adPlatform") or [])
    by_state[row.get("state")] += 1
    names[row.get("campaign")] += 1
    channels[row.get("channelId")] += 1
    countries[row.get("country")] += 1
    site = next((s for s in sites if s in (row.get("landPageUrl") or "")), None)
    if row.get("campaign") and site is None and row.get("landPageUrl"):
        unknown_site.append(row.get("campaign"))
    if row.get("country") and not re.fullmatch(r"[A-Z]{2}", row.get("country")):
        bad_country.append((row.get("campaign"), row.get("country")))
    spend, revenue = float(row.get("adSpend") or 0), float(row.get("grossRevenue") or 0)
    profit, roi = float(row.get("profit") or 0), float(row.get("roi") or 0)
    if abs((revenue - spend) - profit) > 0.02:
        bad_math.append((row.get("campaign"), spend, revenue, profit, revenue - spend))
    elif spend > 0 and abs(revenue / spend - roi) > 0.011:
        bad_math.append((row.get("campaign"), "roi", round(revenue / spend, 4), roi))
    if spend < 0 or revenue < 0:
        zero_or_negative.append((row.get("campaign"), spend, revenue))
    if row.get("state") == "PASS" and not row.get("channelId"):
        no_channel.append(row.get("campaign"))
    if row.get("state") == "PASS" and not row.get("landPageUrl"):
        no_landing.append(row.get("campaign"))
    if row.get("state") == "AUDITING" and spend > 0:
        bad_state.append((row.get("campaign"), spend))

print("platforms", dict(by_platform))
print("states", dict(by_state))
print("countries", dict(countries))
print("distinct sites in landing URLs", len(unknown_site), "rows without a recognised site")

if bad_country:
    note("medium", "non-ISO country values in campaign data", bad_country[:10])
if bad_math:
    note("high", "campaign totals do not reconcile", bad_math[:10])
if no_channel:
    note("medium", "PASS campaign without AdSense channel", no_channel[:10])
if no_landing:
    note("medium", "PASS campaign without landing page", no_landing[:10])
if bad_state:
    note("medium", "AUDITING campaign already has spend", bad_state[:10])
dups = [n for n, k in names.items() if k > 1]
if dups:
    note("medium", "duplicate campaign names", dups[:10])
dup_channels = [(ch, k) for ch, k in channels.items() if ch and k > 1]
if dup_channels:
    note("high", "channelId is shared by multiple campaigns (attribution collision)", dup_channels[:10])

# agent campaign view vs ad_resource view
agent_rows = c.get("/cms/agent/campaigns?limit=300").data.get("list") or []
agent_spend = sum(float(x.get("spend") or 0) for x in agent_rows)
raw_spend = sum(float(x.get("adSpend") or 0) for x in rows)
print(f"agent view rows={len(agent_rows)} spend={agent_spend:.2f}; ad_resource spend={raw_spend:.2f}")
if abs(agent_spend - raw_spend) > 1:
    note("medium", "agent campaign totals differ from CMS campaign list", f"{agent_spend} vs {raw_spend}")

# AdSense daily coverage
opts = c.get("/cms/console/maintenance/options")
channels2 = (opts.data or {}).get("channels") or []
dates = (opts.data or {}).get("dates") or []
print("adsense channels", len(channels2), "dates", len(dates), dates[:3], dates[-3:])
if dates and dates[0] != "2026-11-20":
    note("low", "latest AdSense data date is stale", str(dates[0]))
if len(dates) and len(dates) < 14:
    note("medium", "less than 14 days of AdSense data", str(len(dates)))
channel_country = collections.defaultdict(set)
for ch in channels2:
    channel_country[ch["id"]].add(ch.get("countryCode"))
print("channels covering multiple countries", {k: sorted(v) for k, v in channel_country.items() if len(v) > 1})

# daily reconciliation endpoint for latest date
date = dates[0] if dates else "2026-11-20"
daily = c.get(qs("/cms/console/maintenance/daily", date=date))
items = (daily.data or {}).get("list") or []
print(f"daily {date}: {len(items)} activity rows, updatable={sum(1 for x in items if x.get('canUpdate'))}")
daily_spend = sum(float(x.get("spend") or 0) for x in items)
daily_revenue = sum(float(x.get("revenue") or 0) for x in items)
print(f"daily spend={daily_spend:.2f} revenue={daily_revenue:.2f}")
bad_daily = [x for x in items if abs(float(x.get("profit") or 0) - (float(x.get("revenue") or 0) - float(x.get("spend") or 0))) > 0.02]
if bad_daily:
    note("high", "daily reconciliation profit mismatch", bad_daily[:5])

summary = {
    "campaignRows": len(rows),
    "platforms": dict(by_platform),
    "states": dict(by_state),
    "countries": dict(countries),
    "adsenseDates": dates,
    "adsenseChannels": len(channels2),
    "daily": {"date": date, "rows": len(items), "spend": daily_spend, "revenue": daily_revenue},
    "findings": findings,
}
(OUT / "data-audit.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"\n{len(findings)} data issues -> {OUT/'data-audit.json'}")
