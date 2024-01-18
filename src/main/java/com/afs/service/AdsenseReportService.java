package com.afs.service;

import com.afs.dao.AdResourceMapper;
import com.afs.dao.AdsenseDataMapper;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.AdsenseData;
import com.afs.utils.DateUtils;
import com.afs.utils.JsonUtils;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.adsense.v2.Adsense;
import com.google.api.services.adsense.v2.AdsenseScopes;
import com.google.api.services.adsense.v2.model.ReportResult;
import com.google.api.services.adsense.v2.model.Row;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 文档：
 * https://developers.google.com/adsense/management/metrics-dimensions?hl=zh-cn
 * https://github.com/googleads/googleads-adsense-examples/tree/main/v2/java
 */
@Slf4j
@Service
public class AdsenseReportService {

    public static Map<String, String> adsenseDataMap = adsenseAccount("AFS_ADSENSE_DEFAULT_");

    public static Map<String, String> infozoneaiMap = adsenseAccount("AFS_ADSENSE_INFOZONE_");

    public static Map<String, String> bestreplyMap = adsenseAccount("AFS_ADSENSE_BESTREPLY_");

    public static Map<String, String> cccAfsMap = adsenseAccount("AFS_ADSENSE_CCC_");

    public static Map<String, String> valibooksMap = adsenseAccount("AFS_ADSENSE_VALIBOOKS_");
    
    private static Map<String, String> adsenseAccount(String prefix) {
        return Map.of(
                "pubId", System.getenv().getOrDefault(prefix + "PUBLISHER_ID", ""),
                "dir", System.getenv().getOrDefault(prefix + "TOKEN_DIRECTORY", ""),
                "jsonName", System.getenv().getOrDefault(prefix + "OAUTH_FILE", ""),
                "mail", System.getenv().getOrDefault(prefix + "ACCOUNT", ""),
                "appName", System.getenv().getOrDefault(prefix + "APP_NAME", "AFS"));
    }

    @Value("${env}")
    String env;

    @Autowired
    AdsenseDataMapper adsenseDataMapper;
    @Autowired
    DistributeLockService distributeLockService;
    @Autowired
    AdResourceMapper adResourceMapper;

    @Async
    @Scheduled(fixedDelay = 11 * 60 * 1000, initialDelay = 11 * 60 * 1000)
    private void loopAdsenseDataInfo() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("loopAdsenseDataInfo", "loopAdsenseDataInfo_value", 10 * 60 * 1000L);
        log.info("loopAdsenseDataInfo lock={}", lock);
        if (!lock) {
            return;
        }
        loopAdsenseDataInfoImpl();
    }

    public void loopAdsenseDataInfoImpl() {
        String pubId = adsenseDataMap.get("pubId");
        String dir = adsenseDataMap.get("dir");
        String jsonName = adsenseDataMap.get("jsonName");
        String mail = adsenseDataMap.get("mail");
        String appName = adsenseDataMap.get("appName");
        try {
            loopAdsenseInfoImpl(pubId,  dir, jsonName, mail, appName);
            log.info("loopAdsenseDataInfo end");
        } catch (Exception e) {
            log.error("loopAdsenseInfo error. adsenseDataMap={}", adsenseDataMap, e);
        }
    }

    @Async
    @Scheduled(fixedDelay = 13 * 60 * 1000, initialDelay = 13 * 60 * 1000)
    private void loopInfozoneaiInfo() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("loopInfozoneaiInfo", "loopInfozoneaiInfo_value", 10 * 60 * 1000L);
        log.info("loopInfozoneaiInfo lock={}", lock);
        if (!lock) {
            return;
        }
        String pubId = infozoneaiMap.get("pubId");
        String dir = infozoneaiMap.get("dir");
        String jsonName = infozoneaiMap.get("jsonName");
        String mail = infozoneaiMap.get("mail");
        String appName = infozoneaiMap.get("appName");
        try {
            loopAdsenseInfoImpl(pubId,  dir, jsonName, mail, appName);
            log.info("loopInfozoneaiInfo end");
        } catch (Exception e) {
            log.error("loopAdsenseInfo error. infozoneaiMap={}", infozoneaiMap, e);
        }
    }

    @Async
    @Scheduled(fixedDelay = 9 * 60 * 1000, initialDelay = 9 * 60 * 1000)
    private void loopCccAfsInfo() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("loopCccAfsInfo", "loopCccAfsInfo_value", 8 * 60 * 1000L);
        log.info("loopCccAfsInfo lock={}", lock);
        if (!lock) {
            return;
        }
        String pubId = cccAfsMap.get("pubId");
        String dir = cccAfsMap.get("dir");
        String jsonName = cccAfsMap.get("jsonName");
        String mail = cccAfsMap.get("mail");
        String appName = cccAfsMap.get("appName");
        try {
            loopAdsenseInfoImpl(pubId,  dir, jsonName, mail, appName);
            log.info("loopCccAfsInfo end");
        } catch (Exception e) {
            log.error("loopAdsenseInfo error. cccAfsMap={}", cccAfsMap, e);
        }
    }

    @Async
    @Scheduled(fixedDelay = 12 * 60 * 1000, initialDelay = 12 * 60 * 1000)
    private void loopValibooksInfo() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("loopValibooksInfo", "loopValibooksInfo_value", 10 * 60 * 1000L);
        log.info("loopValibooksInfo lock={}", lock);
        if (!lock) {
            return;
        }
        String pubId = valibooksMap.get("pubId");
        String dir = valibooksMap.get("dir");
        String jsonName = valibooksMap.get("jsonName");
        String mail = valibooksMap.get("mail");
        String appName = valibooksMap.get("appName");
        try {
            loopAdsenseInfoImpl(pubId,  dir, jsonName, mail, appName);
            log.info("loopValibooksInfo end");
        } catch (Exception e) {
            log.error("loopValibooksInfo error. valibooksMap={}", valibooksMap, e);
        }
    }

    @Async
    @Scheduled(fixedDelay = 11 * 60 * 1000, initialDelay = 11 * 60 * 1000)
    private void loopBestReplyInfo() {
        if (!env.equals("prod")) {
            return;
        }

        boolean lock = distributeLockService.tryLock("loopBestReplyInfo", "loopBestReplyInfo_value", 10 * 60 * 1000L);
        log.info("loopBestReplyInfo lock={}", lock);
        if (!lock) {
            return;
        }
        String pubId = bestreplyMap.get("pubId");
        String dir = bestreplyMap.get("dir");
        String jsonName = bestreplyMap.get("jsonName");
        String mail = bestreplyMap.get("mail");
        String appName = bestreplyMap.get("appName");
        try {
            loopAdsenseInfoImpl(pubId,  dir, jsonName, mail, appName);
            log.info("loopBestReplyInfo end");
        } catch (Exception e) {
            log.error("loopBestReplyInfo error. bestreplyMap={}", bestreplyMap, e);
        }
    }

    private void loopAdsenseInfoImpl(String pubId, String dir, String jsonName, String mail, String appName) throws Exception {
        if (pubId.isBlank() || dir.isBlank() || jsonName.isBlank() || mail.isBlank()) return;
        log.info("loopAdsenseInfo start");

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new java.io.File(System.getProperty("user.home"), dir));
        GoogleClientSecrets clientSecrets;
        try (var reader = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(jsonName))) {
            clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), reader);
        }
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JacksonFactory.getDefaultInstance(), clientSecrets,
                Collections.singleton(AdsenseScopes.ADSENSE_READONLY)).setDataStoreFactory(
                dataStoreFactory).build();
        // authorize
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize(mail);
        Adsense adsense = new Adsense.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), credential).setApplicationName(appName).build();

        LocalDate today = LocalDate.now();
        LocalDate dateAgo = today.minusDays(1);
        String dateEnd = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dateStart = dateAgo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        try {
            ReportResult reportResult = run(adsense, "accounts/" + pubId, "accounts/" + pubId + "/adclients/partner-" + pubId,
                    dateStart, dateEnd);
            log.debug("loopAdsenseInfo reportResult={}", reportResult);
            Map<String, String> cache = new HashMap<>();
            for (Row row : reportResult.getRows()) {
                Map<String, Object> hMap = new HashMap<>();
                Map<String, Object> map = new HashMap<>();
                for (int i = 0; i < row.getCells().size(); i++) {
                    String hName = reportResult.getHeaders().get(i).getName().toLowerCase();
                    String value = row.getCells().get(i).getValue();
                    if ("date".equals(hName)) {
                        hMap.put("date", value);
                    }
                    if ("product_code".equals(hName)) {
                        hMap.put("product_code", value);
                    }
                    if ("country_code".equals(hName)) {
                        hMap.put("country_code", value);
                    }
                    if ("custom_channel_id".equals(hName)) {
                        hMap.put("custom_channel_id", value);
                    }
                    map.put(reportResult.getHeaders().get(i).getName().toLowerCase(), row.getCells().get(i).getValue());
                }
                AdsenseData adsenseData = toAdsenseData(hMap, map, cache);
                log.debug("adsenseData={}", adsenseData);
                adsenseDataMapper.add(adsenseData);
            }
            log.info("loopAdsenseInfo cache={}", cache);
            log.info("loopAdsenseInfo end");
        } catch (Exception exception) {
            log.error("adsense data sync error", exception);
        }
    }

    private AdsenseData toAdsenseData(Map<String, Object> hMap, Map<String, Object> map, Map<String, String> cacheMap) {
        AdsenseData adsenseData = new AdsenseData();
        adsenseData.setDate((String) hMap.get("date"));
        adsenseData.setCountry_code((String) hMap.get("country_code"));

        String adResourceId = cacheMap.get(adsenseData.getCustomChannelId());
        if (StringUtils.isBlank(adResourceId)) {
            adResourceId = getAdResourceId((String) hMap.get("custom_channel_id"));
            cacheMap.put((String) hMap.get("custom_channel_id"), adResourceId);
        }
        adsenseData.setAdResourceId(adResourceId);
        adsenseData.setCustomChannelId((String) hMap.get("custom_channel_id"));
        adsenseData.setProduct_code((String) hMap.get("product_code"));
        adsenseData.setAdsense_data(JsonUtils.toJsonString(map));

        adsenseData.setCost_per_click((Double.parseDouble((String)map.get("cost_per_click"))));
        adsenseData.setImpressions(Double.parseDouble((String)map.get("impressions")));
        adsenseData.setAd_requests(Double.parseDouble((String)map.get("ad_requests")));
        adsenseData.setFunnel_impressions(Double.parseDouble((String)map.get("funnel_impressions")));
        adsenseData.setClicks(Double.parseDouble((String)map.get("clicks")));
        adsenseData.setFunnel_clicks(Double.parseDouble((String)map.get("funnel_clicks")));
        adsenseData.setFunnel_rpm(Double.parseDouble((String)map.get("funnel_rpm")));
        adsenseData.setFunnel_requests(Double.parseDouble((String)map.get("funnel_requests")));
        adsenseData.setMatched_ad_requests(Double.parseDouble((String)map.get("matched_ad_requests")));
        adsenseData.setImpressions_ctr(Double.parseDouble((String)map.get("impressions_ctr")));
        adsenseData.setEstimated_earnings(Double.parseDouble((String)map.get("estimated_earnings")));
        adsenseData.setIndividual_ad_impressions(Double.parseDouble((String)map.get("individual_ad_impressions")));
        return adsenseData;
    }

    private String getAdResourceId(String customChannelId) {
        try {
            if (StringUtil.isNotBlank(customChannelId) && customChannelId.contains(":")) {
                String[] cusChannelIdArr =customChannelId.split(":");
                String channelId = cusChannelIdArr[1];
                AdResource adResource = adResourceMapper.getOneByChannelIdAndState(channelId, AdResource.PASS);
                if (adResource != null) {
                    return adResource.getAdResourceId();
                }
            }
        } catch (Exception e) {
            log.error("getAdResourceId error. customChannelId={}", customChannelId, e);
        }
        return "";
    }

    private String[] getDateParameter(String date) {
        String[] dateArray = date.split("-");
        dateArray[1] = dateArray[1].replaceFirst("^0+(?!$)", "");
        dateArray[2] = dateArray[2].replaceFirst("^0+(?!$)", "");
        return dateArray;
    }

    public ReportResult run(Adsense adsense, String accountId, String adClientId, String dateStart, String dateEnd) throws Exception {

        String[] startArray = getDateParameter(dateStart);
        String[] endArray = getDateParameter(dateEnd);

        Adsense.Accounts.Reports.Generate request = adsense.accounts().reports().generate(accountId);
        // Specify the startDate and endDate for the report.
        request.setDateRange("CUSTOM");
        request.setStartDateYear(Integer.parseInt(startArray[0])).setStartDateMonth(Integer.parseInt(startArray[1])).setStartDateDay(Integer.parseInt(startArray[2]));
        request.setEndDateYear(Integer.parseInt(endArray[0])).setEndDateMonth(Integer.parseInt(endArray[1])).setEndDateDay(Integer.parseInt(endArray[2]));
        // Specify the desired ad client using a filter.
        request.setFilters(Arrays.asList("AD_CLIENT_ID==" + escapeFilterParameter(adClientId)));
        // Specify the desired metrics and dimensions.
        request.setMetrics(Arrays.asList("ESTIMATED_EARNINGS", "FUNNEL_REQUESTS", "FUNNEL_IMPRESSIONS", "FUNNEL_CLICKS", "FUNNEL_RPM", "AD_REQUESTS", "MATCHED_AD_REQUESTS", "IMPRESSIONS", "IMPRESSIONS_CTR", "CLICKS", "COST_PER_CLICK", "INDIVIDUAL_AD_IMPRESSIONS"));
        request.setDimensions(Arrays.asList("DATE", "PRODUCT_CODE", "COUNTRY_CODE", "CUSTOM_CHANNEL_ID"));
        // Sort by ascending date.
        request.setOrderBy(Arrays.asList("+DATE"));
        // Run report.
        ReportResult response = request.execute();
//        List<Row> rows = response.getRows();
        return response;
    }

    public static String escapeFilterParameter(String parameter) {
        // Get the last entry of the parameter name (after the last forward slash) for filtering.
        parameter = parameter.substring(parameter.lastIndexOf("/") + 1);
        // Replace special characters with the escaped version and return the string.
        return parameter.replace("\\", "\\\\").replace(",", "\\,");
    }

}
