package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
class OverseasEvidenceService {
    private final JdbcTemplate db;
    private final OverseasDataService fx;
    OverseasEvidenceService(JdbcTemplate db,OverseasDataService fx){this.db=db;this.fx=fx;}
    private static ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
    private static String text(JsonNode n,String k){String s=n.path(k).asText("").strip();if(s.isBlank()||s.length()>2048)throw bad("缺少或无效字段："+k);return s;}
    private static double value(JsonNode n,String k){double d=n.path(k).asDouble(Double.NaN);if(!Double.isFinite(d)||d<0)throw bad("字段须为非负数字："+k);return d;}
    private static long count(JsonNode n,String k){double d=value(n,k);if(d!=Math.rint(d)||d>1e12)throw bad("计数字段须为整数："+k);return (long)d;}
    @Transactional
    public Map<String,Object> importRows(String kind,JsonNode rows){
        if(!rows.isArray()||rows.isEmpty()||rows.size()>10000)throw bad("请选择 1～10000 行观测数据");
        Set<String> keys=new HashSet<>();
        for(JsonNode r:rows){
            LocalDate date;try{date=LocalDate.parse(text(r,"reportDate"));}catch(Exception ex){throw bad("reportDate 格式须为 YYYY-MM-DD");}
            if(date.isAfter(LocalDate.now()))throw bad("报告日期不能晚于今天");
            switch(kind){
                case "experiments" -> {
                    long draft=count(r,"draftId"),impressions=count(r,"impressions"),clicks=count(r,"clicks"),conversions=count(r,"conversions");
                    String variant=text(r,"variant");
                    if(!keys.add(draft+"|"+variant+"|"+date))throw bad("文件中实验观测重复");
                    if(clicks>impressions||conversions>clicks)throw bad("点击不能超过曝光，转化不能超过点击");
                    Integer exists=db.queryForObject("SELECT COUNT(*) FROM ad_creative_draft WHERE id=?",Integer.class,draft);
                    if(exists==null||exists==0)throw bad("请选择现有创意草稿编号");
                    db.update("MERGE INTO ad_experiment_observation(draft_id,variant,report_date,impressions,clicks,conversions,spend_usd,revenue_usd) KEY(draft_id,variant,report_date) VALUES(?,?,?,?,?,?,?,?)",draft,variant,date.toString(),impressions,clicks,conversions,value(r,"spendUsd"),value(r,"revenueUsd"));
                }
                case "accounts" -> {
                    String account=text(r,"accountId"),platform=text(r,"platform"),currency=text(r,"currency").toUpperCase(Locale.ROOT);
                    if(!keys.add(account+"|"+platform+"|"+date))throw bad("文件中账户观测重复");
                    if(!OverseasDataService.SYMBOLS.containsKey(currency))throw bad("不支持的币种");
                    Integer exists=db.queryForObject("SELECT COUNT(*) FROM ad_account_info WHERE account_id=? AND LOWER(ad_platform)=LOWER(?)",Integer.class,account,platform);
                    if(exists==null||exists==0)throw bad("账户未在账号库登记，或所属平台不符");
                    db.update("MERGE INTO ad_account_snapshot(account_id,platform,report_date,currency,balance,daily_spend,account_status,rejected_ads) KEY(account_id,platform,report_date) VALUES(?,?,?,?,?,?,?,?)",account,platform,date.toString(),currency,value(r,"balance"),value(r,"dailySpend"),text(r,"accountStatus"),count(r,"rejectedAds"));
                }
                case "landing" -> {
                    String campaign=text(r,"campaignId"),url=text(r,"url");
                    if(!keys.add(campaign+"|"+date))throw bad("文件中落地页观测重复");
                    Integer exists=db.queryForObject("SELECT COUNT(*) FROM ad_resource WHERE ad_resource_id=? AND status=1",Integer.class,campaign);
                    if(exists==null||exists==0)throw bad("活动不在活动库中");
                    java.net.URI parsed;try{parsed=java.net.URI.create(url);}catch(Exception ex){throw bad("落地页地址无效");}
                    if(parsed.getHost()==null||!List.of("https","http").contains(parsed.getScheme()))throw bad("落地页须为 HTTP(S) URL");
                    long sessions=count(r,"sessions"),bounces=count(r,"bounces"),conversions=count(r,"conversions");
                    if(bounces>sessions||conversions>sessions)throw bad("跳出或转化人数不能超过访问人数");
                    db.update("MERGE INTO ad_landing_observation(campaign_id,report_date,url,ttfb_ms,sessions,bounces,conversions) KEY(campaign_id,report_date) VALUES(?,?,?,?,?,?,?)",campaign,date.toString(),url,value(r,"ttfbMs"),sessions,bounces,conversions);
                }
                default -> throw bad("不支持的观测类型");
            }
        }
        return Map.of("saved",rows.size(),"source","USER_IMPORTED_OBSERVATIONS");
    }
    List<Map<String,Object>> accounts(String currency){
        double target=fx.rate(currency);List<Map<String,Object>> result=new ArrayList<>();
        for(var r:db.queryForList("SELECT s.* FROM ad_account_snapshot s WHERE report_date=(SELECT MAX(t.report_date) FROM ad_account_snapshot t WHERE t.account_id=s.account_id AND t.platform=s.platform) ORDER BY report_date DESC")){
            var m=new LinkedHashMap<String,Object>(r);String cur=String.valueOf(r.get("currency"));
            try{m.put("displayBalance",OverseasDataService.num(r,"balance")/fx.rate(cur)*target);}catch(ResponseStatusException ex){m.put("displayBalance",null);}
            m.put("displayCurrency",currency);double spend=OverseasDataService.num(r,"daily_spend"),balance=OverseasDataService.num(r,"balance");
            m.put("daysRemaining",spend>0?balance/spend:null);
            long stale=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(String.valueOf(r.get("report_date"))),LocalDate.now());
            m.put("health",stale>1?"STALE":!List.of("ACTIVE","ENABLED").contains(String.valueOf(r.get("account_status")).toUpperCase(Locale.ROOT))?"ACCOUNT_RESTRICTED":spend>0&&balance/spend<2?"LOW_BALANCE":OverseasDataService.num(r,"rejected_ads")>0?"REJECTED_ADS":"NO_ALERT");result.add(m);
        }return result;
    }
    Map<String,Object> landing(){
        List<Map<String,Object>> result=new ArrayList<>();
        for(var r:db.queryForList("SELECT * FROM ad_landing_observation ORDER BY report_date DESC LIMIT 300")){
            var m=new LinkedHashMap<String,Object>(r);double sessions=OverseasDataService.num(r,"sessions");
            m.put("bounceRate",sessions>0?OverseasDataService.num(r,"bounces")/sessions:null);
            m.put("cvr",sessions>0?OverseasDataService.num(r,"conversions")/sessions:null);result.add(m);
        }
        List<Map<String,Object>> valid=result.stream().filter(r->OverseasDataService.num(r,"sessions")>0).toList();
        Double correlation=null;
        if(valid.size()>=3){double x=valid.stream().mapToDouble(r->OverseasDataService.num(r,"ttfb_ms")).average().orElse(0),y=valid.stream().mapToDouble(r->OverseasDataService.num(r,"cvr")).average().orElse(0),xx=0,yy=0,xy=0;
            for(var r:valid){double dx=OverseasDataService.num(r,"ttfb_ms")-x,dy=OverseasDataService.num(r,"cvr")-y;xx+=dx*dx;yy+=dy*dy;xy+=dx*dy;}
            if(xx>0&&yy>0)correlation=xy/Math.sqrt(xx*yy);
        }
        var out=new LinkedHashMap<String,Object>();out.put("observations",result);out.put("ttfbCvrCorrelation",correlation);out.put("notice","TTFB 与 CVR 按活动/日观测计算相关性；至少需要 3 条有效观测，相关性不代表因果。未自动访问外部落地页。");return out;
    }
    List<Map<String,Object>> experiments(){
        var grouped=db.queryForList("SELECT o.draft_id,o.variant,SUM(o.impressions) AS impressions,SUM(o.clicks) AS clicks,SUM(o.conversions) AS conversions,SUM(o.spend_usd) AS spend,SUM(o.revenue_usd) AS revenue,MIN(o.report_date) AS start_date,MAX(o.report_date) AS end_date FROM ad_experiment_observation o GROUP BY o.draft_id,o.variant ORDER BY o.draft_id,o.variant");
        Map<Long,List<Map<String,Object>>> groups=new LinkedHashMap<>();for(var r:grouped)groups.computeIfAbsent(((Number)r.get("draft_id")).longValue(),k->new ArrayList<>()).add(r);
        List<Map<String,Object>> result=new ArrayList<>();
        for(var entry:groups.entrySet()){
            var arms=entry.getValue();var control=arms.stream().filter(r->"A".equals(r.get("variant"))).findFirst().orElse(null);if(control==null)continue;
            var draft=db.queryForList("SELECT * FROM ad_creative_draft WHERE id=?",entry.getKey());if(draft.isEmpty())continue;
            for(var variant:arms){if(variant==control)continue;
                double n1=OverseasDataService.num(control,"impressions"),n2=OverseasDataService.num(variant,"impressions"),k1=OverseasDataService.num(control,"clicks"),k2=OverseasDataService.num(variant,"clicks");
                double ctr1=n1>0?k1/n1:0,ctr2=n2>0?k2/n2:0,pool=n1+n2>0?(k1+k2)/(n1+n2):0,se=n1>0&&n2>0?Math.sqrt(pool*(1-pool)*(1/n1+1/n2)):0;
                double z=se>0?Math.abs(ctr2-ctr1)/se:0,p=se>0?Math.min(1,2*(1-normalCdf(z))):1,adjusted=Math.min(1,p*Math.max(1,arms.size()-1));
                boolean enough=k1>=10&&k2>=10&&n1-k1>=10&&n2-k2>=10;
                String start=String.valueOf(control.get("start_date")),end=String.valueOf(control.get("end_date"));
                boolean samePeriod=start.equals(variant.get("start_date"))&&end.equals(variant.get("end_date"));
                boolean won=enough&&samePeriod&&adjusted<.05&&ctr2>ctr1;
                Map<String,Object> m=new LinkedHashMap<>();m.put("draftId",entry.getKey());m.put("experimentCode","EXP-"+entry.getKey()+" A/"+variant.get("variant"));m.put("variantHeadline",draft.get(0).get("headline"));m.put("platform",draft.get(0).get("platform"));m.put("campaignName",draft.get(0).get("campaign_name"));
                m.put("trafficSplit","导入的实际曝光占比");m.put("controlImpressions",(long)n1);m.put("variantImpressions",(long)n2);m.put("targetSample",20000);m.put("sampleProgressPct",Math.min(100,(n1+n2)/20000*100));
                m.put("controlCtr",ctr1);m.put("variantCtr",ctr2);m.put("ctrLiftPct",ctr1>0?(ctr2/ctr1-1)*100:0);
                double cost1=OverseasDataService.num(control,"spend"),cost2=OverseasDataService.num(variant,"spend"),roas1=cost1>0?OverseasDataService.num(control,"revenue")/cost1:0,roas2=cost2>0?OverseasDataService.num(variant,"revenue")/cost2:0;
                m.put("controlRoas",cost1>0?roas1:null);m.put("variantRoas",cost2>0?roas2:null);m.put("roasLiftPct",roas1>0?(roas2/roas1-1)*100:0);
                m.put("confidencePct",(1-adjusted)*100);m.put("adjustedPValue",adjusted);m.put("verdict",won?"VARIANT_WINNING":"INCONCLUSIVE");m.put("verdictLabel",!samePeriod?"对照与实验观测周期不一致":!enough?"样本不足以使用正态近似":won?"CTR 检验通过（Bonferroni 校正 p<0.05）；请结合转化回收人工决策":"未证实 CTR 提升");
                m.put("lifecycleState",won?"READY_TO_CONCLUDE":"RUNNING");m.put("lifecycleLabel",won?"待人工复核":"继续观测");m.put("startDate",start);m.put("endDate",end);result.add(m);
            }
        }return result;
    }
    private static double normalCdf(double z){double t=1/(1+.2316419*z);double d=.3989422804014327*Math.exp(-z*z/2);return 1-d*t*(.319381530+t*(-.356563782+t*(1.781477937+t*(-1.821255978+t*1.330274429))));}
}
