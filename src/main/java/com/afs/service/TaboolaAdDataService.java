package com.afs.service;

import com.afs.dao.AdAccountInfoMapper;
import com.afs.dao.AdsenseDataMapper;
import com.afs.dao.TaboolaDataMapper;
import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.TaboolaData;
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

@Slf4j
@Service
public class TaboolaAdDataService {

    @Value("${env}")
    String env;
    @Autowired
    AdAccountInfoMapper adAccountInfoMapper;
    @Autowired
    HttpService httpService;
    @Autowired
    TaboolaDataMapper taboolaDataMapper;
    @Autowired
    AdsenseDataMapper adsenseDataMapper;
    @Autowired
    DistributeLockService distributeLockService;
    @Autowired
    AdResourceService adResourceService;

    @Async
    @Scheduled(fixedDelay = 3 * 60 * 60 * 1000)
    public void refreshToken() {
        if (!env.equals("prod")) {
            return;
        }

        List<AdAccountInfo> list = adAccountInfoMapper.getByAdPlatform("Taboola");
        for (AdAccountInfo info : list) {
            try {
                String clientId = JsonUtils.getInfoFromJson("clientId", info.getInfo());
                String clientSecret = JsonUtils.getInfoFromJson("clientSecret", info.getInfo());
                String response = httpService.getTaboolaToken("https://backstage.taboola.com/backstage/oauth/token", clientId, clientSecret);
                Map<String, Object> map = JsonUtils.toMap(response);
                String token = (String) map.get("access_token");
                String jsonStr = JsonUtils.getJsonAfterAddInfo("token", token, info.getInfo());
                adAccountInfoMapper.updateInfo(jsonStr, info.getId());
            } catch (Exception e) {
                log.error("refreshToken error. name={}", info.getName(), e);
            }
        }
        log.info("refreshToken end success.");
    }

    public void refreshTaboolaCampaignInfoImpl(String name, String token) {
        List<TaboolaData>  list = taboolaDataMapper.getListByDate(name);
        for (TaboolaData taboolaData : list) {
            Map<String, String> hMap = new HashMap<>();
            hMap.put("Authorization", "Bearer " + token);
            String url = "https://backstage.taboola.com/backstage/api/1.0/" + name + "/campaigns/" + taboolaData.getCampaignId() + "/items";
            log.debug("refreshTaboolaCampaignInfoImpl url={}", url);
            String response = httpService.get(url, hMap);
            log.debug("refreshTaboolaCampaignInfoImpl response={}", response);
            Map<String, Object> map = JsonUtils.toMap(response);
            List<Map> resultsList = (List<Map>) map.get("results");
            for (Map<String, Object> resultsMap : resultsList) {
                String campaignId = (String) resultsMap.get("campaign_id");
                String landingPageUrl = (String) resultsMap.get("url");
                String channelId = getChannelId(landingPageUrl);
                taboolaDataMapper.updateCampaignInfo(landingPageUrl, channelId, campaignId);
            }
        }

        List<TaboolaData> taboolaDataList = taboolaDataMapper.getListGroupByDateAndCountryAndChannelId();
        log.debug("updateTbDataToAdsense taboolaDataList={}", taboolaDataList);

        for (TaboolaData data : taboolaDataList) {
            String landingUrl = taboolaDataMapper.getLandingUrlByChannelId(data.getChannelId());
            String customChannelId = adResourceService.getCusChannelId(data.getChannelId(), landingUrl);
            int n = adsenseDataMapper.updateTaboolaInfo(data.getClicks(), data.getSpend(), data.getImpressions(), data.getConversion(), data.getCountryCode(), customChannelId, data.getDate());
            log.debug("updateTbDataToAdsense list={}, n={}, date={}, data={}", list, n, data.getDate(), data);
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
        if ("1582847545=DentalImplants".equals(channelId)) {
            channelId = "1582847545";
        }
        return channelId;
    }

    @Async
    @Scheduled(fixedDelay = 19 * 60 * 1000, initialDelay = 19 * 60 * 1000)
    public void refreshTaboolaData() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("refreshTaboolaData", "refreshTaboolaData_value", 18 * 60 * 1000L);
        log.info("refreshTaboolaData lock={}", lock);
        if (!lock) {
            return;
        }

        List<AdAccountInfo> list = adAccountInfoMapper.getByAdPlatform("Taboola");
        for (AdAccountInfo info : list) {
            String accountName = info.getName();
            String token = JsonUtils.getInfoFromJson("token", info.getInfo());
            try {
                refreshTaboolaDataImpl(accountName, token);
            } catch (Exception e) {
                log.error("refreshTaboolaData error. name={}", accountName, e);
            }
            try {
                refreshTaboolaCampaignInfoImpl(accountName, token);
            } catch (Exception e) {
                log.error("refreshTaboolaCampaignInfo error. name={}", accountName, e);
            }
        }
    }

    private void refreshTaboolaDataImpl(String name, String token) {
        LocalDate today = LocalDate.now();
        for (int indexDay = 0; indexDay <= 1 ; indexDay++ ) {
            LocalDate indexLocalDate = today.minusDays(indexDay);
            String indexDayString = indexLocalDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String countryBrUrl = "https://backstage.taboola.com/backstage/api/1.0/" + name + "/reports/campaign-summary/dimensions/country_breakdown?" + "start_date=" + indexLocalDate + "&end_date=" + indexDayString;
            Map<String, String> hMap = new HashMap<>();
            hMap.put("Authorization", "Bearer " + token);
            String countryBrResponse = httpService.get(countryBrUrl, hMap);
            Map<String, Object> resMap = JsonUtils.toMap(countryBrResponse);
            List<Map> resultsList = (List<Map>) resMap.get("results");
            for (Map<String, Object> resultsMap : resultsList) {
                String countryCode = (String) resultsMap.get("country");
                String campaignBrUrl = "https://backstage.taboola.com/backstage/api/1.0/" + name + "/reports/campaign-summary/dimensions/campaign_breakdown?" + "start_date=" + indexLocalDate + "&end_date=" + indexDayString + "&country=" + countryCode;
                Map<String, String> hMapN = new HashMap<>();
                hMapN.put("Authorization", "Bearer " + token);
                String campaignResponse = httpService.get(campaignBrUrl, hMapN);
                Map<String, Object> map = JsonUtils.toMap(campaignResponse);
                List<Map> dataList = (List<Map>) map.get("results");
                for (Map itemMap : dataList) {
                    String campaignName = (String) itemMap.get("campaign_name");
                    String campaignId = (String) itemMap.get("campaign");
                    Double spend = (Double) itemMap.get("spent");
                    Integer impressions = (Integer) itemMap.get("impressions");
                    Double conversion = (Double) itemMap.get("conversions_value");
                    Integer clicks = (Integer) itemMap.get("clicks");
                    try {
                        TaboolaData data = new TaboolaData();
                        data.setDate(indexDayString);
                        data.setCampaignId(campaignId);
                        data.setCampaignName(campaignName);
                        data.setCountryCode(countryCode);
                        data.setClicks(clicks + "");
                        data.setImpressions(impressions + "");
                        data.setSpend(spend + "");
                        data.setConversion(conversion + "");
                        data.setAccountName(name);
                        taboolaDataMapper.add(data);
                    } catch (Exception e) {
                        log.error("refreshTaboolaDataImpl error. itemMap={}", itemMap, e);
                    }
                }
            }
        }
    }
}
