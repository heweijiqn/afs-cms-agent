package com.afs.service;

import com.afs.dao.AdAccountInfoMapper;
import com.afs.dao.AdsenseDataMapper;
import com.afs.dao.TiktokDataMapper;
import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.TiktokData;
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
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TiktokAdDataService {

    @Value("${env}")
    String env;
    @Autowired
    AdAccountInfoMapper adAccountInfoMapper;
    @Autowired
    HttpService httpService;
    @Autowired
    TiktokDataMapper tiktokDataMapper;
    @Autowired
    AdsenseDataMapper adsenseDataMapper;
    @Autowired
    DistributeLockService distributeLockService;
    @Autowired
    AdResourceService adResourceService;


    public static String mToken = System.getenv().getOrDefault("AFS_TIKTOK_ACCESS_TOKEN", "");

    @Async
    @Scheduled(fixedDelay = 16 * 60 * 1000, initialDelay = 16 * 60 * 1000)
    public void refreshCampaignFromTiktok() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("refreshCampaignFromTiktok", "refreshCampaignFromTiktok_value", 14 * 60 * 1000L);
        log.info("refreshCampaignFromTiktok lock={}", lock);
        if (!lock) {
            return;
        }

        List<AdAccountInfo> list = adAccountInfoMapper.getByAdPlatform("TikTok");
        for (AdAccountInfo info : list) {
            try {
                Map<String, String> hMap = new HashMap<>();
                hMap.put("Access-Token", mToken);
                int page = 1;
                int pageSize = 1000;
                int totalNumber = Integer.MAX_VALUE;

                while ((page - 1) * pageSize < totalNumber) {
                    String url = "https://business-api.tiktok.com/open_api/v1.3/ad/get/?advertiser_id=" + info.getAccountId()
                            + "&page_size=" + pageSize + "&page=" + page;
                    String response = httpService.get(url, hMap);
                    log.debug("refreshCampaignFromTiktok response={}", response);
                    Map<String, Object> map = JsonUtils.toMap(response);
                    Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
                    List<Map> dataList = (List<Map>) dataMap.get("list");
                    AppUtils.logTiktokTotalNumber(dataMap, "refreshCampaignFromTiktok. " + info.getAccountId());
                    for (Map itemMap : dataList) {
                        String campaignId = (String) itemMap.get("campaign_id");
                        String landingPageUrl = (String) itemMap.get("landing_page_url");
                        if (StringUtils.isNotBlank(landingPageUrl) && landingPageUrl.contains("channel=")) {
                            String channelId = getChannelId(landingPageUrl);
                            tiktokDataMapper.updateCampaignInfo(landingPageUrl, channelId, campaignId);
                        }
                    }

                    Map<String, Object> pageInfo = (Map<String, Object>) dataMap.get("page_info");
                    totalNumber = (Integer) pageInfo.get("total_number");
                    page++;
                }
                log.info("refreshCampaignFromTiktok success. accountId={}", info.getAccountId());
            } catch (Exception e) {
                log.error("refreshCampaignFromTiktok error. accountId={}", info.getAccountId(), e);
            }
        }

        refreshSmartCampaignFromTiktok(list);

        List<TiktokData> ttList = tiktokDataMapper.getListGroupByDateAndCountryAndChannelId();//后台campaignid和channelid一对一，channelid和平台campaign一对多
        log.debug("updateTbDataToAdsense ttList={}", ttList);

        for (TiktokData data : ttList) {
            String landingUrl = tiktokDataMapper.getLandingUrlByChannelId(data.getChannelId());
            String customChannelId = adResourceService.getCusChannelId(data.getChannelId(), landingUrl);
            int n = adsenseDataMapper.updateTTInfo(data.getClicks(), data.getSpend(), data.getImpressions(), data.getConversion(), data.getCountryCode(), customChannelId, data.getDate());
            log.debug("updateTTDataToAdsense list={}, n={}, date={}, data={}", list, n, data.getDate(), data);
        }
    }


    private void refreshSmartCampaignFromTiktok(List<AdAccountInfo> list) {
        for (AdAccountInfo info : list) {
            try {
                String date = DateUtils.getFormatDayFromToday(0);
                log.info("refreshSmartCampaignFromTiktok date={}, accountId={}", date, info.getAccountId());
                List<String> campaignIds = tiktokDataMapper.getCampaignIdNoChannelIdByDateAndAdvertiser(date, info.getAccountId());
                if (CollectionUtils.isEmpty(campaignIds)) {
                    continue;
                }
                String campaignIdListStr = campaignIds.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
                Map<String, String> hMap = new HashMap<>();
                hMap.put("Access-Token", mToken);
                String url = "https://business-api.tiktok.com/open_api/v1.3/campaign/spc/get/?advertiser_id=" + info.getAccountId() + "&campaign_ids=[" + campaignIdListStr + "]";
                String response = httpService.get(url, hMap);
                log.debug("refreshSmartCampaignFromTiktok response={}", response);
                Map<String, Object> map = JsonUtils.toMap(response);
                Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
                List<Map> dataList = dataMap.get("list") != null ? (List<Map>) dataMap.get("list") : new ArrayList<>();
                for (Map itemMap : dataList) {
                    List<Map> landingPageUrls = itemMap.get("landing_page_urls") != null ? (List<Map>) itemMap.get("landing_page_urls") : new ArrayList<>();
                    List<String> urlList = landingPageUrls.stream().map(e -> (String) e.get("landing_page_url")).collect(Collectors.toList());

                    String campaignId = (String) itemMap.get("campaign_id");
                    if (!CollectionUtils.isEmpty(urlList)) {
                        String landingPageUrl = urlList.get(0);
                        if (landingPageUrl.contains("channel=")) {
                            String channelId = getChannelId(landingPageUrl);
                            log.info("refreshSmartCampaignFromTiktok to add. landingPageUrl={}, channelId={}, campaignId={}", landingPageUrl, channelId, campaignId);
                            tiktokDataMapper.updateCampaignInfo(landingPageUrl, channelId, campaignId);
                        }
                    }
                }
                log.info("refreshSmartCampaignFromTiktok success. accountId={}", info.getAccountId());
            } catch (Exception e) {
                log.error("refreshSmartCampaignFromTiktok error. accountId={}", info.getAccountId(), e);
            }
        }
    }

    private static String getChannelId(String landingPageUrl) {
        log.debug("getChannelId landingPageUrl={}", landingPageUrl);
        String[] array = landingPageUrl.split("channel=");
        String channelId;
        if (array[1].contains("&")) {
            channelId = array[1].substring(0, array[1].indexOf("&"));
        } else {
            channelId = array[1];
        }
        if (StringUtils.isNotBlank(channelId)) {
            channelId = channelId.replace(" ", "");
        }
        return channelId;
    }

    @Async
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 15 * 60 * 1000)
    public void refreshAdDataFromTiktok() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("refreshAdDataFromTiktok", "refreshAdDataFromTiktok_value", 12 * 60 * 1000L);
        log.info("refreshCampaignFromTiktok lock={}", lock);
        if (!lock) {
            return;
        }
        log.info("refreshAdDataFromTiktok start");
        LocalDate today = LocalDate.now();
        LocalDate dateAgo = today.minusDays(1);
        String dateEnd = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dateStart = dateAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        List<AdAccountInfo> list = adAccountInfoMapper.getByAdPlatform("TikTok");

        for (AdAccountInfo info : list) {
            try {
                String urlPre = "https://business-api.tiktok.com/open_api/v1.3/report/integrated/get/?advertiser_id=" + info.getAccountId() + "&service_type=AUCTION&page=1&data_level=AUCTION_CAMPAIGN&report_type=BASIC&dimensions=[\"campaign_id\",\"country_code\",\"stat_time_day\"]&metrics=[\"impressions\", \"clicks\", \"spend\", \"conversion\", \"campaign_name\"]";
                String url = urlPre + "&start_date=" + dateStart + "&end_date=" + dateEnd + "&page_size=1000&page=" + 1;
                Map<String, String> hMap = new HashMap<>();
                hMap.put("Access-Token", mToken);
                String response = httpService.get(url, hMap);
                log.debug("refreshAdDataFromTiktok response={}", response);
                Map<String, Object> map = JsonUtils.toMap(response);
                Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
                List<Map> dataList = (List<Map>) dataMap.get("list");
                AppUtils.logTiktokTotalNumber(dataMap, "refreshAdDataFromTiktok. " + info.getAccountId());
                for (Map itemMap : dataList) {
                    Map<String, Object> dimenMap = (Map<String, Object>) itemMap.get("dimensions");
                    String countryCode = (String) dimenMap.get("country_code");
                    String campaignId = (String) dimenMap.get("campaign_id");
                    String statTimeDay = (String) dimenMap.get("stat_time_day");
                    Map<String, Object> metricMap = (Map<String, Object>) itemMap.get("metrics");
                    String spend = (String) metricMap.get("spend");
                    String impressions = (String) metricMap.get("impressions");
                    String campaignName = (String) metricMap.get("campaign_name");
                    String conversion = (String) metricMap.get("conversion");
                    String clicks = (String) metricMap.get("clicks");
                    TiktokData tiktokData = new TiktokData();
                    tiktokData.setDate(statTimeDay.split(" ")[0]);
                    tiktokData.setCampaignId(campaignId);
                    tiktokData.setCampaignName(campaignName);
                    tiktokData.setCountryCode(countryCode);
                    tiktokData.setClicks(clicks);
                    tiktokData.setImpressions(impressions);
                    tiktokData.setSpend(spend);
                    tiktokData.setConversion(conversion);
                    tiktokData.setAdvertiser(info.getAccountId());
                    tiktokDataMapper.add(tiktokData);
                }
                log.info("refreshAdDataFromTiktok success. accountId={}", info.getAccountId());
            } catch (Exception e) {
                log.error("refreshAdDataFromTiktok error. accountId={}", info.getAccountId(), e);
            }
        }
        log.info("refreshAdDataFromTiktok end");
    }
}
