package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Imported attribution snapshots: one acquisition cohort per campaign/date, cumulative revenue per age. */
@Service
class OverseasDataService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    static final Map<String,String> SYMBOLS = Map.of("USD","$","CNY","¥","EUR","€","GBP","£","JPY","¥");
    OverseasDataService(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }

    @Transactional
    public Map<String,Object> importCohorts(JsonNode rows) {
        if (!rows.isArray() || rows.isEmpty() || rows.size()>10000) throw bad("请选择包含 1～10000 行的队列文件");
        Set<String> keys=new HashSet<>();
        for (JsonNode row: rows) {
            String campaign=text(row,"campaignId"), platform=text(row,"platform");
            LocalDate date;
            try { date=LocalDate.parse(text(row,"cohortDate")); } catch(Exception ex) { throw bad("cohortDate 须为 YYYY-MM-DD"); }
            int age=integer(row,"ageDay"), users=integer(row,"acquisitions");
            double spend=number(row,"spendUsd"), revenue=number(row,"cumulativeRevenueUsd");
            if (date.isAfter(LocalDate.now()) || age<0 || age>90 || date.plusDays(age).isAfter(LocalDate.now())) throw bad("观测日期不能晚于今天，ageDay 须为 0～90");
            if (users<=0 || spend<0 || revenue<0) throw bad("获客数须大于 0，花费与累计收入不能为负");
            Integer active=row.hasNonNull("activeUsers") && !row.path("activeUsers").asText().isBlank() ? integer(row,"activeUsers"):null;
            if(active!=null && (active<0 || active>users)) throw bad("留存人数须在 0～获客人数之间");
            String key=campaign+"|"+date+"|"+age;
            if(!keys.add(key)) throw bad("文件中有重复的活动、获客日期和观测天数");
            db.update("MERGE INTO ad_cohort_observation(campaign_id,platform,cohort_date,age_day,acquisitions,spend_usd,revenue_usd,active_users) KEY(campaign_id,cohort_date,age_day) VALUES(?,?,?,?,?,?,?,?)",campaign,platform,date.toString(),age,users,spend,revenue,active);
        }
        // Validate full persisted cohorts, including earlier snapshots and corrected imports.
        var all=db.queryForList("SELECT * FROM ad_cohort_observation ORDER BY campaign_id,cohort_date,age_day");
        Map<String,Object> previous=null;
        for(var row:all) {
            if(previous!=null && Objects.equals(row.get("campaign_id"),previous.get("campaign_id")) && Objects.equals(row.get("cohort_date"),previous.get("cohort_date"))) {
                if(num(row,"revenue_usd")<num(previous,"revenue_usd")) throw bad("同一队列累计收入不能下降");
                if(num(row,"spend_usd")!=num(previous,"spend_usd") || num(row,"acquisitions")!=num(previous,"acquisitions") || !Objects.equals(row.get("platform"),previous.get("platform"))) throw bad("同一队列的渠道、初始花费与获客人数须一致");
            }
            previous=row;
        }
        return Map.of("saved",rows.size(),"source","IMPORTED_ATTRIBUTION");
    }

    @Transactional
    public Map<String,Object> saveRates(JsonNode payload) {
        LocalDate date;
        try {date=LocalDate.parse(text(payload,"date"));}catch(Exception ex){throw bad("汇率日期须为 YYYY-MM-DD");}
        if(date.isAfter(LocalDate.now())) throw bad("汇率日期不能晚于今天");
        String source=text(payload,"source");
        JsonNode rates=payload.path("rates");
        if(!rates.isObject() || rates.isEmpty()) throw bad("请填写币种汇率");
        var names=rates.fieldNames();
        while(names.hasNext()) {
            String currency=names.next().toUpperCase(Locale.ROOT);
            double rate=rates.path(currency).asDouble(Double.NaN);
            if(!SYMBOLS.containsKey(currency) || !Double.isFinite(rate) || rate<=0 || (currency.equals("USD") && rate!=1)) throw bad("不支持的币种或无效汇率（1 USD 对应目标币种数量）");
            db.update("MERGE INTO ad_fx_rate(currency,rate_date,rate,source) KEY(currency,rate_date) VALUES(?,?,?,?)",currency,date.toString(),rate,source);
        }
        return currencies();
    }
    Map<String,Object> currencies() {
        var rates=new LinkedHashMap<String,Object>();rates.put("USD",1);
        var observations=new LinkedHashMap<String,Object>();
        for(var row:db.queryForList("SELECT * FROM ad_fx_rate ORDER BY rate_date")) {
            String cur=String.valueOf(row.get("currency"));rates.put(cur,row.get("rate"));
            observations.put(cur,Map.of("date",row.get("rate_date"),"source",row.get("source")));
        }
        return Map.of("rates",rates,"symbols",SYMBOLS,"baseCurrency","USD","observations",observations,"source","USER_SUPPLIED_DATED_RATES");
    }
    double rate(String currency) {
        if("USD".equals(currency)) return 1;
        var rows=db.queryForList("SELECT rate,rate_date FROM ad_fx_rate WHERE currency=? ORDER BY rate_date DESC LIMIT 1",currency);
        if(rows.isEmpty()) throw bad("请先导入 "+currency+" 的有日期汇率");
        return num(rows.get(0),"rate");
    }

    @SuppressWarnings("unchecked")
    Map<String,Object> convertDisplay(Map<String,Object> input,String currency) {
        String cur=currency==null||currency.isBlank()?"USD":currency.toUpperCase(Locale.ROOT);
        double factor=rate(cur);
        var result=(Map<String,Object>)convertValue(input,"",factor);
        result.put("currency",cur);result.put("currencySymbol",SYMBOLS.getOrDefault(cur,cur));result.put("exchangeRates",currencies());return result;
    }
    private Object convertValue(Object value,String key,double factor) {
        if(value instanceof Map<?,?> map){var out=new LinkedHashMap<String,Object>();map.forEach((k,v)->out.put(String.valueOf(k),convertValue(v,String.valueOf(k),factor)));return out;}
        if(value instanceof List<?> list)return list.stream().map(v->convertValue(v,key,factor)).toList();
        if(value instanceof Number n && Set.of("totalSpend","totalRevenue","totalProfit","cpa","cpc","cpm","spend","revenue","profit","dailyBudget","todaySpend","remainingBudget","totalDailyBudget","totalTodaySpend").contains(key))return n.doubleValue()*factor;
        return value;
    }

    Map<String,Object> cohort(String platform,Integer days,String currency) {
        String cur=currency==null||currency.isBlank()?"USD":currency.toUpperCase(Locale.ROOT);
        double fx=rate(cur); int lookback=days==null?14:Math.max(1,Math.min(90,days));
        // Cohort rows must be visible long enough for their D30 observation to mature.
        int horizon=Math.max(lookback,30);
        var snapshots=db.queryForList("SELECT * FROM ad_cohort_observation WHERE cohort_date>=? ORDER BY campaign_id,cohort_date,age_day",LocalDate.now().minusDays((long)lookback+horizon).toString());
        Map<String,List<Map<String,Object>>> groups=new LinkedHashMap<>();
        for(var row:snapshots) {
            if(platform!=null&&!platform.isBlank()&&!platform.equalsIgnoreCase(String.valueOf(row.get("platform")))) continue;
            groups.computeIfAbsent(row.get("campaign_id")+"|"+row.get("cohort_date"),k->new ArrayList<>()).add(row);
        }
        List<Map<String,Object>> cohorts=new ArrayList<>(); double totalSpend=0,totalUsers=0,paybackSum=0;int recovered=0;
        int[] ages={0,1,3,7,14,30}; double[] revenueSums=new double[6],spendSums=new double[6],usersSums=new double[6];
        for(var group:groups.values()) {
            var first=group.get(0);double spend=num(first,"spend_usd"),users=num(first,"acquisitions");
            Map<String,Object> row=new LinkedHashMap<>(); row.put("campaignId",first.get("campaign_id")); row.put("cohortDate",first.get("cohort_date"));row.put("platform",first.get("platform"));
            row.put("spend",spend*fx);row.put("acquisitions",users);row.put("currencySymbol",SYMBOLS.getOrDefault(cur,cur));row.put("currency",cur);
            Integer payback=null;
            for(var obs:group) if(spend>0&&num(obs,"revenue_usd")>=spend) {payback=((Number)obs.get("age_day")).intValue();break;}
            row.put("paybackDays",payback);row.put("breakEvenStatus",payback==null?"NOT_YET_RECOVERED":"RECOVERED");
            // Fit only observed cumulative revenue, never fill unobserved measured cells.
            var forecast=forecast(group,spend);
            row.put("forecast",forecast);
            if(payback!=null){paybackSum+=payback;recovered++;}
            for(int i=0;i<ages.length;i++) {
                int age=ages[i];var obs=group.stream().filter(o->num(o,"age_day")==age).findFirst().orElse(null);
                row.put("d"+age+"Roas",obs==null||spend<=0?null:num(obs,"revenue_usd")/spend);
                row.put("d"+age+"Retention",obs==null||obs.get("active_users")==null?null:num(obs,"active_users")/users*100);
                if(obs!=null){revenueSums[i]+=num(obs,"revenue_usd");spendSums[i]+=spend;usersSums[i]+=users;}
            }
            cohorts.add(row);totalSpend+=spend;totalUsers+=users;
        }
        var summary=new LinkedHashMap<String,Object>();summary.put("currencySymbol",SYMBOLS.getOrDefault(cur,cur));summary.put("currency",cur);
        summary.put("totalSpend",totalSpend*fx);summary.put("totalAcquisitions",totalUsers);summary.put("avgCac",totalUsers>0?totalSpend*fx/totalUsers:null);
        summary.put("avgPaybackDays",recovered>0?paybackSum/recovered:null);
        for(int i=0;i<ages.length;i++) summary.put("avgD"+ages[i]+"Roas",spendSums[i]>0?revenueSums[i]/spendSums[i]:null);
        summary.put("ltvCacRatio",spendSums[5]>0?revenueSums[5]/spendSums[5]:null);
        summary.put("ltv30",usersSums[5]>0?revenueSums[5]*fx/usersSums[5]:null);
        summary.put("narrative",cohorts.isEmpty()?"暂无归因队列数据。请导入按获客日期归属的累计收入和留存观测。普通按日投放报表不能替代 Cohort 数据。":"仅使用已导入的归因观测；空白表示该年龄暂无观测。回本天数为首次观测到回本的天数，尚未观测到的 D30 不作预测。");
        summary.put("windowDays",lookback);
        summary.put("horizonDays",horizon);
        return Map.of("summary",summary,"cohorts",cohorts,"source","IMPORTED_ATTRIBUTION","rates",currencies());
    }
    private Map<String,Object> forecast(List<Map<String,Object>> observations,double spend) {
        if(observations.size()<3||spend<=0||num(observations.get(observations.size()-1),"age_day")<7) return Map.of("available",false,"reason","至少需要 3 个年龄观测且覆盖到 D7");
        if(num(observations.get(observations.size()-1),"age_day")>=30) return Map.of("available",false,"reason","已有 D30 或更晚观测，无需外推 D30");
        double x=0,y=0;int n=observations.size();
        for(var o:observations){x+=Math.log1p(num(o,"age_day"));y+=num(o,"revenue_usd");}x/=n;y/=n;
        double xx=0,xy=0,yy=0;
        for(var o:observations){double dx=Math.log1p(num(o,"age_day"))-x,dy=num(o,"revenue_usd")-y;xx+=dx*dx;xy+=dx*dy;yy+=dy*dy;}
        if(xx<=0||yy<=0||xy<=0)return Map.of("available",false,"reason","收入趋势不足以拟合");
        double slope=xy/xx,intercept=y-slope*x,r2=xy*xy/(xx*yy);
        if(r2<.8)return Map.of("available",false,"reason","拟合稳定性不足（R²<0.8）");
        double last=num(observations.get(n-1),"revenue_usd"),estimate=Math.max(last,intercept+slope*Math.log1p(30));
        double estimatedPayback=Math.expm1((spend-intercept)/slope);
        var model=new LinkedHashMap<String,Object>();model.put("available",true);model.put("model","cumulativeRevenue = intercept + slope * ln(1+age)");model.put("rSquared",r2);model.put("sampleAges",n);model.put("projectedD30Roas",estimate/spend);
        model.put("projectedPaybackDays",Double.isFinite(estimatedPayback)&&estimatedPayback>=num(observations.get(n-1),"age_day")&&estimatedPayback<=90?Math.ceil(estimatedPayback):null);
        model.put("notice","基于单队列已观测收入的趋势外推；不等同实际回收，不能单独作为停投或追投依据。");return model;
    }
    static double num(Map<String,Object> row,String key) {Object val=row.get(key);return val instanceof Number?((Number)val).doubleValue():0;}
    private static String text(JsonNode row,String key){String s=row.path(key).asText("").strip();if(s.isBlank()||s.length()>200)throw bad("缺少或无效字段："+key);return s;}
    private static double number(JsonNode row,String key){double n=row.path(key).asDouble(Double.NaN);if(!Double.isFinite(n))throw bad("缺少或无效数字："+key);return n;}
    private static int integer(JsonNode row,String key){double n=number(row,key);if(n!=Math.rint(n)||n>Integer.MAX_VALUE||n<Integer.MIN_VALUE)throw bad("字段须为整数："+key);return (int)n;}
}
