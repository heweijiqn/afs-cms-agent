package com.afs.service;

import com.afs.dao.*;
import com.afs.dao.model.MetaData;
import com.afs.utils.AppUtils;
import com.afs.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * https://developers.facebook.com/docs/marketing-apis/ 文档
 * https://developers.facebook.com/docs/marketing-api/reference/v23.0
 * https://developers.facebook.com/docs/marketing-api/reference/ad-account/ads/
 * https://developers.facebook.com/docs/marketing-api/insights
 * 1，获取所有campaign报告
 * 2，获取所有campaign对应的channelId
 * 3，更新adsense数据
 * ads信息表，campaign数据表
 * https://graph.facebook.com/v23.0/act_{ad-account-id}/ads
 * https://graph.facebook.com/v19.0/act_<AD_ACCOUNT_ID>/insights?level=campaign&fields=campaign_id,campaign_name,impressions,clicks,spend,reach,actions&time_range[since]=2024-06-01&time_range[until]=2024-06-03&access_token=<ACCESS_TOKEN>
 */
@Slf4j
@Service
public class MetaAdDataService {

    static String mMetaToken = System.getenv().getOrDefault("AFS_META_ACCESS_TOKEN", "");

    static List<String> mMetaAccountIdList = Arrays.asList("act_1051488583574088",
            "act_1212042990650538", "act_1002308748358028", "act_4141612256111923",
            "act_614666958304638", "act_1207514914483655");

    @Value("${env}")
    String env;
    @Autowired
    HttpService httpService;
    @Autowired
    MetaDataMapper metaDataMapper;
    @Autowired
    AdsenseDataMapper adsenseDataMapper;
    @Autowired
    AdResourceService adResourceService;
    @Autowired
    DistributeLockService distributeLockService;

    @Async
    @Scheduled(fixedDelay = 16 * 60 * 1000, initialDelay = 16 * 60 * 1000)
//    @Scheduled(fixedDelay = 23 * 60 * 1000)
    public void refreshCampaignFromMeta() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("refreshCampaignFromMeta", "refreshCampaignFromMeta_value", 12 * 60 * 1000L);
        log.info("refreshCampaignFromMeta lock={}", lock);
        if (!lock) {
            return;
        }

        for (String accountId : mMetaAccountIdList) {
            try {
                String url = "https://graph.facebook.com/v23.0/" + accountId + "/ads?fields=id,name,adset_id,campaign_id,status,creative&limit=1000&access_token=" + mMetaToken;
                String response = httpService.get(url);
                log.debug("refreshCampaignFromMeta response={}", response);
                Map<String, Object> map = JsonUtils.toMap(response);
                List<Map> dataList = (List<Map>) map.get("data");
                for (Map itemMap : dataList) {
                    String campaignId = (String) itemMap.get("campaign_id");
                    Map<String, Object> creativeMap = (Map) itemMap.get("creative");
                    String creativeId = (String) creativeMap.get("id");
                    String creativeUrl = "https://graph.facebook.com/v23.0/" + creativeId + "?fields=id,name,object_story_spec&limit=1000&access_token=" + mMetaToken;
                    String creativeResponse = httpService.get(creativeUrl);
                    log.info("refreshCampaignFromMeta creativeResponse={}", creativeResponse);
                    Map<String, Object> creativeResponseMap = JsonUtils.toMap(creativeResponse);
                    if (creativeResponseMap.get("object_story_spec") != null) {
                        Map<String, Object> storySpecMap = (Map) creativeResponseMap.get("object_story_spec");
                        if (storySpecMap.get("link_data") != null) {
                            Map<String, Object> linkDataMap = (Map) storySpecMap.get("link_data");
                            String link = (String) linkDataMap.get("link");
                            if (StringUtils.isNotBlank(link) && link.contains("channel=")) {
                                String channelId = AppUtils.getChannelIdByLandingPageUrl(link);
                                metaDataMapper.updateCampaignInfo(link, channelId, campaignId);
                            }
                        }
                    }
                }
                log.info("refreshCampaignFromMeta success. accountId={}", accountId);
            } catch (Exception e) {
                log.error("refreshCampaignFromMeta error. accountId={}", accountId, e);
            }
        }
        List<MetaData> metaDataList = metaDataMapper.getListGroupByDateAndCountryAndChannelId();//后台campaignid和channelid一对一，channelid和平台campaign一对多
        log.debug("updateMetaDataToAdsense metaDataList={}", metaDataList);
        for (MetaData data : metaDataList) {
            String landingUrl = metaDataMapper.getLandingUrlByChannelId(data.getChannelId());
            String customChannelId = adResourceService.getCusChannelId(data.getChannelId(), landingUrl);
            int n = adsenseDataMapper.updateTTInfo(data.getClicks(), data.getSpend(), data.getImpressions(), data.getConversion(),
                    data.getCountryCode(),customChannelId, data.getDate());
            log.info("updateMetaDataToAdsense n={}, customChannelId={}, date={}, data={}", n, customChannelId, data.getDate(), data);
        }
    }

    @Async
    @Scheduled(fixedDelay = 14 * 60 * 1000, initialDelay = 14 * 60 * 1000)
//    @Scheduled(fixedDelay = 26 * 60 * 1000)
    public void refreshAdDataFromMeta() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("refreshAdDataFromMeta", "refreshAdDataFromMeta_value", 10 * 60 * 1000L);
        log.info("refreshCampaignFromMeta lock={}", lock);
        if (!lock) {
            return;
        }

        log.info("refreshAdDataFromMeta start");
        LocalDate today = LocalDate.now();
        LocalDate dateAgo = today.minusDays(1);
        String dateEnd = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dateStart = dateAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        for (String accountId : mMetaAccountIdList) {
            try {
                String url = "https://graph.facebook.com/v23.0/" + accountId + "/insights?level=campaign" +
                        "&fields=campaign_id,campaign_name,impressions,clicks,spend,reach,actions" +
                        "&breakdowns=country&time_increment=1&time_range[since]=" + dateStart + "&time_range[until]=" + dateEnd + "" +
                        "&limit=1000&access_token=" + mMetaToken;
                String response = httpService.get(url);
                log.debug("refreshAdDataFromMeta response={}", response);
                Map<String, Object> map = JsonUtils.toMap(response);
                List<Map> dataList = (List<Map>) map.get("data");
                for (Map itemMap : dataList) {
                    String countryCode = (String) itemMap.get("country");
                    String campaignId = (String) itemMap.get("campaign_id");
                    String campaignName = (String) itemMap.get("campaign_name");
                    String dataDate = (String) itemMap.get("date_start");
                    String spend = (String) itemMap.get("spend");
                    String impressions = (String) itemMap.get("impressions");
                    String clicks = (String) itemMap.get("clicks");
                    MetaData data = new MetaData();
                    data.setDate(dataDate);
                    data.setCampaignId(campaignId);
                    data.setCampaignName(campaignName);
                    data.setCountryCode(countryCode);
                    data.setClicks(clicks);
                    data.setImpressions(impressions);
                    data.setSpend(spend);
                    data.setConversion(getConversion(itemMap.get("actions")));
                    data.setAdvertiser(accountId);
                    metaDataMapper.add(data);
                }
                log.info("refreshAdDataFromMeta success. accountId={}", accountId);
            } catch (Exception e) {
                log.error("refreshAdDataFromMeta error. accountId={}", accountId, e);
            }
        }
        log.info("refreshAdDataFromMeta end");
    }

    private String getConversion(Object actionsInfo) {
        String conversion = "0";
        if (actionsInfo != null) {
            List<Map<String, Object>> actionsMapList = (List<Map<String, Object>>) actionsInfo;
            for (Map<String, Object> actionsMap : actionsMapList) {
                if ("add_to_cart".equals(actionsMap.get("action_type"))) {
                    conversion = (String) actionsMap.get("value");
                    break;
                }
            }
        }
        return conversion;
    }
}
