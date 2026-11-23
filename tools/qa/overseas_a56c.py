import json
from client import CmsClient
c=CmsClient()
out={"stage":"A6c","checks":[],"steps":{},"created":{}}
def chk(i,desc,exp,r,ok,detail=None):
    e={"id":i,"desc":desc,"expected":exp,"status":getattr(r,"status",None),"code":getattr(r,"code",None),"ok":bool(ok),
       "msg":(r.body or {}).get("msg") if isinstance(getattr(r,"body",None),dict) else None}
    if detail is not None: e["detail"]=detail
    out["checks"].append(e); print(f"[{'PASS' if ok else 'FAIL'}] {i} {desc} -> {e['status']} code={e['code']}")
    return e
# inspect previous evaluate counts
prev=json.load(open("tools/qa/out/overseas-sec_A56b.json",encoding="utf-8"))
print("prev evaluate:", {k:v for k,v in (prev["steps"]["A6b_evaluate"].get("data") or {}).items() if k!="logs" and k!="events"})
print("prev evaluate2:", {k:v for k,v in (prev["steps"]["A6b_evaluate2"].get("data") or {}).items() if k!="logs" and k!="events"})
# OFF_PEAK_SAVER ignores spend and only needs budget>0 + time window
rule={"name":"QA 非高峰降价（窗口验证）","ruleType":"OFF_PEAK_SAVER","scopePlatform":"TikTok",
      "conditionJson":json.dumps({"offPeakHours":"00:00-23:59","timezone":"UTC","minSpend":0}),
      "actionType":"ADJUST_BUDGET","actionParamsJson":json.dumps({"bidDiscountPct":8,"budgetDeltaPct":0,"maxDailyBudget":600}),
      "dryRun":True,"enabled":True}
r=c.post("/cms/agent/overseas/automation/rules",rule)
rl=(r.data or {}).get("rules") or []
new=next((x for x in rl if x.get("name")==rule["name"]),None)
out["created"]["rule"]=new
chk("A6.21","create OFF_PEAK_SAVER probe rule","200",r,r.code==0 and new is not None,{"rule":new})
ev=c.post("/cms/agent/overseas/automation/evaluate",{},timeout=300)
evd=ev.data or {}
out["steps"]["A6c_evaluate"]={k:v for k,v in evd.items() if k not in ("logs","events")}
out["steps"]["A6c_events"]=evd.get("events")
chk("A6.22","evaluate with a time-window rule that ignores spend","triggeredCount>0",ev,(evd.get("triggeredCount") or 0)>0,
    {k:evd.get(k) for k in ("evaluatedRules","triggeredCount","simulatedCount","executedCount","skippedMissingData")})
logs=(c.get("/cms/agent/overseas/automation/logs?limit=100").data or {}).get("list") or []
ours=[x for x in logs if x.get("ruleName")==rule["name"]]
out["steps"]["A6c_logs"]=ours
chk("A6.23","probe rule produced execution logs","1+ rows",ev,len(ours)>=1,{"count":len(ours),"sample":ours[:3]})
if new:
    d=c.post(f"/cms/agent/overseas/automation/rules/{new['id']}/delete",{})
    chk("A6.24","delete probe rule","gone",d,d.code==0,{"body":d.body})
open("tools/qa/out/overseas-sec_A56c.json","w",encoding="utf-8").write(json.dumps(out,ensure_ascii=False,indent=2))
print("saved")
