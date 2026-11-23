import json, time
from client import CmsClient
c=CmsClient()
out={"stage":"A5b_A6b","checks":[],"steps":{},"created":{}}
def chk(i,desc,exp,r,ok,detail=None):
    e={"id":i,"desc":desc,"expected":exp,"status":getattr(r,"status",None),"code":getattr(r,"code",None),"ok":bool(ok),
       "msg":(r.body or {}).get("msg") if isinstance(getattr(r,"body",None),dict) else None}
    if detail is not None: e["detail"]=detail
    out["checks"].append(e); print(f"[{'PASS' if ok else 'FAIL'}] {i} {desc} -> {e['status']} code={e['code']} {e.get('msg') or ''}")
    return e

# --- cleanup: archive the 3 QA localization drafts
drafts=(c.get("/cms/agent/overseas/localization/drafts").data or {}).get("list") or []
mine=[d for d in drafts if d.get("campaignId")=="1850127310500007919" and d.get("campaignName")=="Foldable treadmill for small apartments"]
arch=[]
for d in mine:
    r=c.post(f"/cms/agent/overseas/localization/drafts/{d['id']}/status",{"status":"ARCHIVED"})
    arch.append({"id":d["id"],"status":r.status,"code":r.code,"new":(r.data or {}).get("status")})
out["steps"]["A5b_archive_drafts"]=arch
after=[x for x in ((c.get("/cms/agent/overseas/localization/drafts").data or {}).get("list") or []) if x.get("id") in {d["id"] for d in mine}]
chk("A5.13","QA localization drafts archived (no delete endpoint exists)","all ARCHIVED",r,all(x.get("status")=="ARCHIVED" for x in after),{"drafts":[(x.get("id"),x.get("status")) for x in after]})
# verify the ad_text sync side effect did not recreate text after re-publish
txt=c.post("/cms/ad/text/list",{"name":"Erst prüfen","pageIndex":1,"pageSize":10})
chk("A5.14","status coercion side effect (ad_text insert) cleaned up","0 rows",txt,((txt.data or {}).get("totalSize") or 0)==0,{"totalSize":(txt.data or {}).get("totalSize")})

# --- automation evaluate with a threshold that can actually match today's partial-day data
valid={"name":"QA 夜间低 ROAS 降价（阈值验证）","ruleType":"STOP_LOSS","scopePlatform":"TikTok",
       "conditionJson":json.dumps({"minSpend":0.01,"maxConversions":1000000000,"maxRoas":0.8,"timezone":"UTC"}),
       "actionType":"PAUSE_CAMPAIGN","actionParamsJson":json.dumps({"pauseReason":"QA dry-run threshold probe"}),
       "dryRun":True,"enabled":True}
r=c.post("/cms/agent/overseas/automation/rules",valid)
rules=(r.data or {}).get("rules") or []
rule=next((x for x in rules if x.get("name")==valid["name"]),None)
out["created"]["rule"]=rule
chk("A6.16","create low-threshold dry-run rule","200 + listed",r,r.code==0 and rule is not None,{"rule":rule})
ev=c.post("/cms/agent/overseas/automation/evaluate",{},timeout=300)
evd=ev.data or {}
out["steps"]["A6b_evaluate"]=ev.body
chk("A6.17","evaluate triggers the low-threshold rule","triggeredCount>0",ev,ev.code==0 and (evd.get("triggeredCount") or 0)>0,
    {k:evd.get(k) for k in ("evaluatedRules","triggeredCount","simulatedCount","executedCount","skippedMissingData")})
logs=(c.get("/cms/agent/overseas/automation/logs?limit=100").data or {}).get("list") or []
ours=[x for x in logs if x.get("ruleName")==valid["name"]]
out["steps"]["A6b_logs"]=ours
chk("A6.18","evaluation wrote execution logs for the QA rule","1+ SIMULATED log rows",ev,len(ours)>=1 and all(x.get("isSimulated") and x.get("executionStatus")=="SIMULATED" for x in ours),
    {"count":len(ours),"sample":ours[:3]})
# idempotent re-evaluate: claims prevent duplicates
ev2=c.post("/cms/agent/overseas/automation/evaluate",{},timeout=300)
logs2=(c.get("/cms/agent/overseas/automation/logs?limit=100").data or {}).get("list") or []
ours2=[x for x in logs2 if x.get("ruleName")==valid["name"]]
out["steps"]["A6b_evaluate2"]=ev2.body
chk("A6.19","re-evaluate same day does not duplicate logs (claim table)","same log count",ev2,len(ours2)==len(ours),
    {"before":len(ours),"after":len(ours2),"triggered":(ev2.data or {}).get("triggeredCount")})
if rule:
    d=c.post(f"/cms/agent/overseas/automation/rules/{rule['id']}/delete",{})
    listed=(c.get("/cms/agent/overseas/automation/rules").data or {}).get("list") or []
    chk("A6.20","delete threshold-probe rule","gone",d,d.code==0 and not any(x.get("id")==rule["id"] for x in listed),{"body":d.body})
open("tools/qa/out/overseas-sec_A56b.json","w",encoding="utf-8").write(json.dumps(out,ensure_ascii=False,indent=2))
print("saved")
