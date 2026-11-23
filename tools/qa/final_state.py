import json, time
from client import CmsClient, qs
c=CmsClient()
out={"stage":"final_state","checks":[],"state":{}}
def chk(i,desc,r,ok,detail=None):
    e={"id":i,"desc":desc,"status":r.status if hasattr(r,"status") else r,"code":r.code if hasattr(r,"code") else None,"ok":bool(ok)}
    if detail: e["detail"]=detail
    out["checks"].append(e); print(i,"ok=",ok, detail if detail and not ok else "")

rules=(c.get("/cms/agent/overseas/automation/rules").data or {}).get("list") or []
out["state"]["rules"]=[{"id":x.get("id"),"name":x.get("name"),"dryRun":x.get("dryRun"),"enabled":x.get("enabled")} for x in rules]
chk("F1","no QA automation rule left behind",rules,not any("QA" in str(x.get("name")) for x in rules),out["state"]["rules"])
sched=c.get("/cms/agent/overseas/automation/schedule")
out["state"]["schedule"]=sched.data
chk("F2","automation schedule restored (disabled)",sched,(sched.data or {}).get("enabled") is False)
drafts=(c.get("/cms/agent/overseas/localization/drafts").data or {}).get("list") or []
out["state"]["drafts"]=[{"id":d.get("id"),"status":d.get("status"),"country":d.get("targetCountry"),"lang":d.get("targetLanguage")} for d in drafts]
chk("F3","QA localization drafts archived",drafts,all(d.get("status")=="ARCHIVED" for d in drafts))
accs=(c.get("/cms/agent/overseas/accounts?currency=USD").data or {}).get("list") or []
out["state"]["accounts"]=[{k:x.get(k) for k in ("account_id","platform","report_date","currency","balance","daily_spend","displayBalance","displayCurrency","daysRemaining","health")} for x in accs]
land=(c.get("/cms/agent/overseas/landing").data or {})
out["state"]["landing"]={"rows":len(land.get("observations") or []),"correlation":land.get("ttfbCvrCorrelation"),
  "sample":(land.get("observations") or [])[:2]}
cur=(c.get("/cms/agent/overseas/currencies").data or {})
out["state"]["currencies"]={"rates":cur.get("rates"),"observations":cur.get("observations")}
coh30=(c.get("/cms/agent/overseas/cohort?platform=TikTok&days=30&currency=USD").data or {})
coh90=(c.get("/cms/agent/overseas/cohort?platform=TikTok&days=90&currency=USD").data or {})
out["state"]["cohort"]={"cohorts30":len(coh30.get("cohorts") or []),"cohorts90":len(coh90.get("cohorts") or []),
  "summary30":coh30.get("summary"),"summary90":(coh90.get("summary") or {})}
# leftover QA artefacts
txt=c.post("/cms/ad/text/list",{"name":"","pageIndex":1,"pageSize":10})
arts=c.post("/cms/article/list?pageIndex=1&pageSize=20",{"title":"QA-XSS"})
art_ids=[x.get("articleId") for x in (((arts.data or {}).get("list")) or [])]
rr=c.post("/cms/ad/resource/rule/list?pageIndex=1&pageSize=50",{"name":"QA-AUTHZ"})
rule_rows=[(x.get("id"),x.get("name")) for x in (((rr.data or {}).get("list")) or [])]
out["state"]["leftovers"]={"xss_articles":art_ids,"authz_rules":rule_rows}
chk("F4","no QA XSS article left",arts,not art_ids,{"ids":art_ids})
chk("F5","no QA ad rule left",rr,not rule_rows,{"rules":rule_rows})
open("tools/qa/out/overseas-sec_state.json","w",encoding="utf-8").write(json.dumps(out,ensure_ascii=False,indent=2))
print("saved")
