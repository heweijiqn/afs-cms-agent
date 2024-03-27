package com.afs.task;

import com.afs.dao.*;
import com.afs.dao.model.*;
import com.afs.service.DistributeLockService;
import com.afs.service.HttpService;
import com.afs.service.TikTokApiService;
import com.afs.service.TiktokAdDataService;
import com.afs.utils.AppUtils;
import com.afs.utils.DateUtils;
import com.afs.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdResourceRuleTask {

    @Value("${env}")
    String env;
    @Autowired
    AdResourceMapper adResourceMapper;
    @Autowired
    AdResourceRuleMapper adResourceRuleMapper;
    @Autowired
    TiktokDataMapper tiktokDataMapper;
    @Autowired
    TikTokApiService tikTokApiService;
    @Autowired
    AdResourceRuleLogMapper adResourceRuleLogMapper;
    @Autowired
    AdsenseDataMapper adsenseDataMapper;
    @Autowired
    TiktokAdGroupInfoMapper tiktokAdGroupInfoMapper;
    @Autowired
    HttpService httpService;
    @Autowired
    DistributeLockService distributeLockService;

    @Async
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void handleAdResourceRule() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("handleAdResourceRule", "handleAdResourceRule_value", 5 * 60 * 1000L);
        log.info("handleAdGroupInfoNew lock={}", lock);
        if (!lock) {
            return;
        }
        List<AdResource> list = adResourceMapper.getListBindRule(AdResource.PASS);//todo:xxx
        log.info("handleAdResourceRule. list size={}", list.size());
        for (AdResource adResource : list) {
            try {
                if (isTodayNotDone(adResource.getLastActionTime())) {
                    handleOne(adResource);
                }
            } catch (Exception e) {
                log.error("error to handleAdResourceRule, adResourceId={}", adResource.getAdResourceId(), e);
            }
        }
    }

    private boolean isTodayNotDone(String lastActionTime) throws ParseException {
        if (StringUtils.isBlank(lastActionTime)) {
            return true;
        }
        long todayTimestamp = DateUtils.getTimestampTodayStart();
        long lastActionTimestamp = DateUtils.getTimestampFromString(lastActionTime);

        if (lastActionTimestamp < todayTimestamp) {
            return true;
        } else {
            return false;
        }
    }

    private int getRuleDayNum(String startRuleTime) throws ParseException {
        long startTimestamp = DateUtils.getTimestampFromString(startRuleTime);
        int dayNum = (int) ((System.currentTimeMillis() - startTimestamp) / (24 * 60 * 60 * 1000));
        return dayNum;
    }

    private void handleOne(AdResource adResource) throws ParseException {
        Long ruleId = adResource.getRuleId();
        AdResourceRule adResourceRule = adResourceRuleMapper.getById(ruleId);
        log.info("handleAdResourceRule. adResourceId={}, ruleId={}, type={}", adResource.getAdResourceId(), adResourceRule.getId(), adResourceRule.getType());
        if (adResourceRule.getType().equals(AdResourceRule.REPEAT) || adResourceRule.getType().equals(AdResourceRule.CONT)) {
            int actionTime = adResourceRule.getActionTime();
            Long actionTimestamp = DateUtils.getTimestampTodayStart() + actionTime * 60 * 60 * 1000;
            if (System.currentTimeMillis() >= actionTimestamp) {
                String ruleStr = adResourceRule.getRule();
                List<AdResourceRuleItem> adResourceRuleItemList = JsonUtils.toList(ruleStr, AdResourceRuleItem.class);
                Integer dayNum = getRuleDayNum(adResource.getStartRuleTime());
                AdResourceRuleItem adResourceRuleItem = getDayRuleItem(adResourceRuleItemList, dayNum);
                handleRuleDetailList(adResourceRuleItem.getDetailList(), adResource);
            }
        } else {
            log.error("handleAdResourceRule error. no type={}", adResourceRule.getType());
        }
    }

    private AdResourceRuleItem getDayRuleItem(List<AdResourceRuleItem> adResourceRuleItemList, Integer dayNum) {//todo
        for (AdResourceRuleItem item : adResourceRuleItemList) {
            if (dayNum >= item.getDayStart() && dayNum <= item.getDayEnd()) {
                return item;
            }
        }
        return adResourceRuleItemList.get(adResourceRuleItemList.size() - 1);
    }

    private void handleRuleDetailList(List<AdResourceRuleItem.AdResourceRuleItemDetail> list, AdResource adResource) throws ParseException {
        List<TiktokAdGroupInfo> tiktokAdGroupInfoList = tiktokAdGroupInfoMapper.getByAdResourceId(adResource.getAdResourceId());
        log.info("handleRuleDetailList. adResourceId={}, tiktokAdGroupInfoList size={}", adResource.getAdResourceId(), tiktokAdGroupInfoList.size());
        for (TiktokAdGroupInfo info : tiktokAdGroupInfoList) {
            for (AdResourceRuleItem.AdResourceRuleItemDetail detail : list) {
                Map<String, Object> infoMap = new HashMap<>();
                infoMap.put("AD_SPEND", info.getAdSpend());
                infoMap.put("REVENUE", info.getGrossRevenue());
                infoMap.put("PROFIT", info.getProfit());
                infoMap.put("ROI", info.getRoi());
                log.info("handleRuleDetailList. adResourceId={}, infoMap={}, condition={}", adResource.getAdResourceId(), infoMap, detail.getCondition());
                if(AppUtils.parseExpression(detail.getCondition(), infoMap)) {
                    if ("CLOSE_CAMPAIGN".equals(detail.getAction())) {
                        log.info("handleAdResourceRule closeAdGroup. adResourceId={}, adGroupId={}", adResource.getAdResourceId(), info.getAdGroupId());
                        String opStatus = tikTokApiService.getAdGroupOpStatus(info.getAdvertiser(), info.getAdGroupId());
                        if (opStatus.equals("ENABLE")) {
                            Map<String, String> headerMap = new HashMap<>();
                            headerMap.put("Access-Token", TiktokAdDataService.mToken);
                            Map<String, Object> bodyMap = new HashMap<>();
                            bodyMap.put("advertiser_id", info.getAdvertiser());
                            bodyMap.put("adgroup_ids", new String[]{info.getAdGroupId()});
                            bodyMap.put("operation_status", "DISABLE");
                            String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), TikTokApiService.UpdateAdgroupStatusUrl);
                            log.debug("closeCampaign response={}", response);
                            updateAdGroupBb(info.getAdGroupId(), response);
                            addAdResourceRuleLog(adResource, info, detail.getAction(), detail.getCondition(), JsonUtils.toJsonString(bodyMap), response);
                        }
                    } else if ("CHANGE_BUDGET".equals(detail.getAction())) {
                        log.info("handleAdResourceRule changeAdGroupBudget. adResourceId={}, adGroupId={}", adResource.getAdResourceId(), info.getAdGroupId());
                        Map<String, String> headerMap = new HashMap<>();
                        headerMap.put("Access-Token", TiktokAdDataService.mToken);
                        Map<String, Object> bodyMap = new HashMap<>();
                        bodyMap.put("advertiser_id", info.getAdvertiser());
                        Map<String, Object> budgetItemMap = new HashMap<>();
                        budgetItemMap.put("adgroup_id", info.getAdGroupId());
                        budgetItemMap.put("budget", getBudgetChange(detail.getBudgetAction(), detail.getBudgetPercent(), info.getBudget()));
                        bodyMap.put("budget", Arrays.asList(budgetItemMap));
                        String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), TikTokApiService.UpdateAdgroupUrl);
                        log.debug("changeCampaignBudget response={}", response);
                        Map<String, Object> responseMap = JsonUtils.toMap(response);
                        addAdResourceRuleLog(adResource, info, detail.getAction(), detail.getCondition(), JsonUtils.toJsonString(bodyMap), response);
                    } else {
                        log.error("handleAdResourceRule error. no action={}", detail.getAction());
                    }
                    log.info("break");
                    break;// 条件满足一个跳出循环, 因为条件互斥。
                }
            }
        }
        adResourceMapper.updateLastActionTime(DateUtils.formatDate(System.currentTimeMillis()), adResource.getAdResourceId());
    }

    private void updateAdGroupBb(String adGroupId, String response) {
        try {
            Map<String, Object> responseMap = JsonUtils.toMap(response);
            Integer code = (Integer)responseMap.get("code");
            if (code == 0) {
                Map<String, Object> dataMap = (Map<String, Object>) responseMap.get("data");
                String status = (String) dataMap.get("status");
                int n = tiktokAdGroupInfoMapper.updateTikTokAdGroupStatus(adGroupId, status);
                log.info("updateAdGroupBb n={}, adGroupId={}, status={}", n, adGroupId, status);
            }
        } catch (Exception e) {
            log.error("updateAdGroupBb error. adGroupId={}, response={}", adGroupId, response);
        }
    }

    private Double getBudgetChange(String budgetAction, Integer budgetPercent, Double budget) {
        if ("INCREASE".equals(budgetAction)) {
            return budget + budget * budgetPercent / 100;
        } else if ("DECREASE".equals(budgetAction)) {
            return budget - budget * budgetPercent / 100;
        } else {
            return budget;
        }
    }

    private void addAdResourceRuleLog(AdResource adResource, TiktokAdGroupInfo info, String action, String condition,
                                      String httpBody, String httpResponse) throws ParseException {
        AdResourceRuleLog adResourceRuleLog = new AdResourceRuleLog();
        adResourceRuleLog.setAdResourceId(adResource.getAdResourceId());
        adResourceRuleLog.setCampaignId(info.getCampaignId());
        adResourceRuleLog.setCampaignName(info.getCampaignName());
        adResourceRuleLog.setRuleId(adResource.getRuleId());
        adResourceRuleLog.setDayNum(getRuleDayNum(adResource.getStartRuleTime()));
        adResourceRuleLog.setRuleName(adResourceRuleMapper.getById(adResource.getRuleId()).getName());
        String actionAlias = "CLOSE_CAMPAIGN".equals(action) ? "CLOSE_ADGROUP" : action;
        adResourceRuleLog.setAction(actionAlias);
        adResourceRuleLog.setAdGroupId(info.getAdGroupId());
        adResourceRuleLog.setAdGroupName(info.getAdgroupName());
        adResourceRuleLog.setCondition(condition);
        adResourceRuleLog.setHttpBody(httpBody);
        adResourceRuleLog.setHttpResponse(httpResponse);
        adResourceRuleLogMapper.add(adResourceRuleLog);
    }
}
