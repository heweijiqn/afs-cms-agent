package com.afs.task;

import com.afs.dao.*;
import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.TiktokAdGroupInfo;
import com.afs.dao.model.TiktokData;
import com.afs.service.AdResourceService;
import com.afs.service.AdsenseReportService;
import com.afs.service.DistributeLockService;
import com.afs.service.TikTokApiService;
import com.afs.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdGroupInfoAndLastDayAdResourceTask {

    @Value("${env}")
    String env;
    @Autowired
    AdResourceMapper adResourceMapper;
    @Autowired
    TiktokDataMapper tiktokDataMapper;
    @Autowired
    TikTokApiService tikTokApiService;
    @Autowired
    TiktokAdGroupInfoMapper tiktokAdGroupInfoMapper;
    @Autowired
    AdAccountInfoMapper adAccountInfoMapper;
    @Autowired
    AdsenseDataMapper adsenseDataMapper;
    @Autowired
    AdResourceService adResourceService;
    @Autowired
    DistributeLockService distributeLockService;
    @Autowired
    AdsenseReportService adsenseReportService;

    @Async
    @Scheduled(cron = "0 30 0 * * ?")
//    @Scheduled(fixedDelay = 60 * 60 * 60 * 1000)//test
    public void handleAdGroupInfoNew() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("handleAdGroupInfoNew", "handleAdGroupInfoNew_value", 10 * 60 * 1000L);
        log.info("handleAdGroupInfoNew lock={}", lock);
        if (!lock) {
            return;
        }
        adsenseReportService.loopAdsenseDataInfoImpl();
        updateAdResourceLastDayData();
        updateAdGroupLastDayData();
    }

    public void updateAdGroupLastDayData() {
        List<AdAccountInfo> list = adAccountInfoMapper.getByAdPlatform("TikTok");
        for (AdAccountInfo accountInfo : list) {
            try {
                List<Map> adGroupMapList = tikTokApiService.getAdGroupList(accountInfo.getAccountId());
                for (Map adGroupMap : adGroupMapList) {
                    try {
                        TiktokAdGroupInfo info = new TiktokAdGroupInfo();
                        info.setAdGroupId((String) adGroupMap.get("adgroup_id"));
                        info.setAdgroupName((String) adGroupMap.get("adgroup_name"));
                        info.setAdvertiser((String) adGroupMap.get("advertiser_id"));
                        info.setCampaignName((String) adGroupMap.get("campaign_name"));
                        info.setCampaignId((String) adGroupMap.get("campaign_id"));
                        info.setBudget((Double) adGroupMap.get("budget"));
                        info.setAdGroupStatus((String) adGroupMap.get("secondary_status"));
                        info.setConversion(0d);
                        info.setGrossRevenue(0d);
                        info.setAdSpend(0d);
                        info.setProfit(0d);//todo:
                        info.setRoi(0d);

                        TiktokData tiktokData = tiktokDataMapper.getOneByCampaignId(info.getCampaignId());
                        if (tiktokData != null) {//找不到ars的不填加
                            info.setChannelId(tiktokData.getChannelId());
                            AdResource adResource = adResourceMapper.getOneByChannelIdAndState(tiktokData.getChannelId(), AdResource.PASS);
                            if (adResource != null) { //归档后不填加
                                info.setAdResourceId(adResource.getAdResourceId());
                                tiktokAdGroupInfoMapper.add(info);
                            }
                        }
                    } catch (Exception e) {
                        log.error("handleAdGroupInfoNew error. accountId={}", accountInfo.getAccountId(), e);
                    }
                }

                List<Map> mapList = tikTokApiService.getAdGroupReportList(accountInfo.getAccountId(), DateUtils.getFormatDayFromToday(-1), DateUtils.getFormatDayFromToday(-1));
                for (Map itemMap : mapList) {
                    try {
                        Map<String, Object> dimenMap = (Map<String, Object>) itemMap.get("dimensions");
                        String adGroupId = (String) dimenMap.get("adgroup_id");
                        Map<String, Object> metricMap = (Map<String, Object>) itemMap.get("metrics");
                        Double conversion = Double.valueOf((String) metricMap.get("conversion"));
                        Double spend = Double.valueOf((String) metricMap.get("spend"));
                        Double impressions = Double.valueOf((String) metricMap.get("impressions"));
                        Double clicks = Double.valueOf((String) metricMap.get("clicks"));
                        TiktokAdGroupInfo info = new TiktokAdGroupInfo();
                        info.setAdGroupId(adGroupId);
                        info.setConversion(conversion);
                        info.setAdSpend(spend);
                        log.info("updateAdGroupData adGroupId={}, conversion={}, adSpend={}, impressions={}, clicks={}", info.getAdGroupId(), conversion, spend, impressions, clicks);
                        tiktokAdGroupInfoMapper.updateTikTokByAdgroupId(info);
                    } catch (Exception e) {
                        log.error("handleAdGroupInfoNew getAdGroupReportList error. accountId={}", accountInfo.getAccountId(), e);
                    }
                }

                List<TiktokAdGroupInfo> allGroupInfoList = tiktokAdGroupInfoMapper.getByAdGroupStatus();
                for (TiktokAdGroupInfo info : allGroupInfoList) {//
                    try {
                        log.info("updateAdGroupData info={}", info);
                        AdResource adResource = adResourceMapper.getByAdResourceId(info.getAdResourceId());
                        Double grossRevenue = adResource.getGrossRevenue();
                        List<TiktokAdGroupInfo> groupInfoList = tiktokAdGroupInfoMapper.getByAdResourceId(info.getAdResourceId());//后台camp对应的所有group
                        double totalConversion = 0d;
                        for (TiktokAdGroupInfo groupInfo : groupInfoList) {
                            totalConversion += groupInfo.getConversion();
                        }
                        info.setGrossRevenue(totalConversion != 0 ? (info.getConversion()/totalConversion) * grossRevenue : 0d);
                        log.info("updateAdGroupData adResourceId={}, adGroupId={}, conversion = {}, totalConversion={}, grossRevenue={}", adResource.getAdResourceId(), info.getAdGroupId(), info.getConversion(), totalConversion, grossRevenue);
                        info.setProfit(info.getGrossRevenue() - info.getAdSpend());
                        info.setRoi(info.getAdSpend() != 0 ? info.getGrossRevenue()/info.getAdSpend() : 0d);
                        log.info("updateAdGroupData adResourceId={}, adGroupId={}, profit={}, roi={}", adResource.getAdResourceId(), info.getAdGroupId(), info.getProfit(), info.getRoi());
                        tiktokAdGroupInfoMapper.updateAdsenseDataByAdgroupId(info);
                    } catch (Exception e) {
                        log.error("handleAdGroupInfoNew updateAdsenseDataByAdgroupId error. info={}", info, e);
                    }
                }
            } catch (Exception e) {
                log.error("handleAdGroupInfoNew error. accountId={}", accountInfo.getAccountId(), e);
            }
        }
        log.info("updateAdGroupLastDayData end.");
    }

    public void updateAdResourceLastDayData() {
        List<AdResource> passList = adResourceMapper.getListByPlatformAndState("TikTok", AdResource.PASS);
        String lastDayStart = DateUtils.getFormatDayBeforeToday(1);
        List<AdResource> archiveList = adResourceMapper.getListByPlatformAndStateAndUTime("TikTok", AdResource.ARCHIVE, lastDayStart);
        List<AdResource> list = new ArrayList<>();
        list.addAll(passList);
        list.addAll(archiveList);

        log.info("updateAdResourceLastDayData list={}, passList={}, archiveList={}", list.size(), passList.size(), archiveList.size());
        for (AdResource adResource : list) {
            try {
                String lastDayStr = DateUtils.getFormatDayFromToday(-1);
                String channelId = adResourceService.getCusChannelId(adResource.getAdResourceId(), adResource.getChannelId(), adResource.getLandPageUrl());
                Map<String, Object> dataMap = adsenseDataMapper.getListByDateAndChannelId(channelId, lastDayStr, lastDayStr);
                log.debug("updateAdResourceLastDayData adResource={}, dataMap={}", adResource.getAdResourceId(), dataMap);
                if (dataMap != null && !dataMap.isEmpty()) {
                    Double ttSpend = (Double) dataMap.get("tt_spend");
                    Double grossRevenue = (Double) dataMap.get("gross_revenue");
                    double roi = 0.0;
                    if (ttSpend != 0) {
                        roi = grossRevenue/ttSpend;
                    }
                    double profit = grossRevenue - ttSpend;
                    log.info("updateAdResourceLastDayData adResourceId={}, ttSpend={}, grossRevenue={}, profit={}, roi={}", adResource.getAdResourceId(), ttSpend, grossRevenue, profit, roi);
                    adResourceMapper.updateLastData(adResource.getAdResourceId(), ttSpend, grossRevenue, profit, roi);
                } else {
                    log.info("updateAdResourceLastDayData adResourceId={}, no data", adResource.getAdResourceId());
                    adResourceMapper.updateLastData(adResource.getAdResourceId(), 0.0, 0.0, 0.0, 0.0);
                }
            } catch (Exception e) {
                log.error("updateAdResourceLastDayData error", e);
            }
        }
        log.info("updateAdResourceLastDayData end.");
    }
}
