package com.afs.service;

import com.afs.dao.AdResourceMapper;
import com.afs.dao.model.AdResource;
import com.afs.utils.AppUtils;
import com.afs.utils.DateUtils;
import com.afs.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jetty.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TikTokApiService {
    static String mTempPath = "/home/ecs-user/work/temp/img/";

    static String UpdateCampaignStatusUrl = "https://business-api.tiktok.com/open_api/v1.3/campaign/status/update/";
    static String UpdateCampaignUrl = "https://business-api.tiktok.com/open_api/v1.3/campaign/update/";
    static String createCampaignUrl = "https://business-api.tiktok.com/open_api/v1.3/campaign/create/";
    static String GetCampaignUrl = "https://business-api.tiktok.com/open_api/v1.3/campaign/get/";
    public static String UpdateAdgroupUrl = "https://business-api.tiktok.com/open_api/v1.3/adgroup/budget/update/";
    public static String UpdateAdgroupStatusUrl = "https://business-api.tiktok.com/open_api/v1.3/adgroup/status/update/";
    static String GetAdgroupUrl = "https://business-api.tiktok.com/open_api/v1.3/adgroup/get/";
    static String GetPixelId = "https://business-api.tiktok.com/open_api/v1.3/pixel/list/";
    static String createAdGroupUrl = "https://business-api.tiktok.com/open_api/v1.3/adgroup/create/";
    static String uploadVideoUrl = "https://business-api.tiktok.com/open_api/v1.3/file/video/ad/upload/";
    static String uploadImageUrl = "https://business-api.tiktok.com/open_api/v1.3/file/image/ad/upload/";
    static String createAdUrl = "https://business-api.tiktok.com/open_api/v1.3/ad/create/";
    static String createAcoAdUrl = "https://business-api.tiktok.com/open_api/v1.3/ad/aco/create/";
    static String getPixelInfoUrl = "https://business-api.tiktok.com/open_api/v1.3/pixel/list/";
    static String getIdentifyUrl = "https://business-api.tiktok.com/open_api/v1.3/identity/get/";

    @Autowired
    HttpService httpService;
    @Autowired
    AdResourceMapper adResourceMapper;
    ExecutorService asyncExecutor = Executors.newFixedThreadPool(8);

    public void createCampaignAndAdGroupAndAd(Map<String, Object> map) {
        asyncExecutor.execute(() -> {
            List<String> adAccountList = (List<String>) map.get("adAccountList");
            for (String advertiserId : adAccountList) {
                map.put("advertiserId", advertiserId);
                boolean success = createCampaignAndAdGroupAndAdByEveryAdAccount(map);
                if (success) {
                    adResourceMapper.updateAdPlatformState(AdResource.AD_PLATFORM_SUCCESS, (String) map.get("adResourceId"));
                } else {
                    adResourceMapper.updateAdPlatformState(AdResource.AD_PLATFORM_FAIL, (String) map.get("adResourceId"));
                    break;
                }
            }
        });
        adResourceMapper.updateAdPlatformState(AdResource.AD_PLATFORM_ING, (String) map.get("adResourceId"));
    }

    public boolean createCampaignAndAdGroupAndAdByEveryAdAccount(Map<String, Object> map) {
        List<String> campaignIdList = new ArrayList<>();
        boolean isSuccess = true;
        int campaignCountEachAccount = (int) map.get("campaignCountEachAccount");
        boolean isSmartCreative = (Boolean) map.get("isSmartCreative");
        try {
            for (int i = 0; i < campaignCountEachAccount; i++) {
                String campaignId = createCampaign(map);
                if (StringUtil.isBlank(campaignId)) {
                    isSuccess = false;
                    break;
                }
                map.put("campaignId", campaignId);
                campaignIdList.add(campaignId);
                if (isSmartCreative) {
                    String adGroupId = createAdGroupSmartCreative(map);
                    if (StringUtil.isBlank(adGroupId)) {
                        isSuccess = false;
                        break;
                    }
                    map.put("adGroupId", adGroupId);
                    String creativeAdResultAdGroupId = createAdSmartCreative(map);
                    if (StringUtil.isBlank(creativeAdResultAdGroupId)) {
                        isSuccess = false;
                        break;
                    }
                } else {
                    String adGroupId = createAdGroup(map);
                    if (StringUtil.isBlank(adGroupId)) {
                        isSuccess = false;
                        break;
                    }
                    map.put("adGroupId", adGroupId);
                    List<String> adIds = createAd(map);
                    if (CollectionUtils.isEmpty(adIds)) {
                        isSuccess = false;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            isSuccess = false;
            adResourceMapper.updateAdPlatformStateAndMsg(AdResource.AD_PLATFORM_FAIL, e.getMessage(), (String) map.get("adResourceId"));
            log.error("createCampaignAndAdGroupAndAd error. map={}", map, e);
        } finally {
            if (!isSuccess && !CollectionUtils.isEmpty(campaignIdList)) {
                deleteCampaign((String) map.get("advertiserId"), campaignIdList);
            } else {
                AdResource adResource = adResourceMapper.getByAdResourceId((String) map.get("adResourceId"));
                Map<String, Object> createResultInfo = new HashMap<>();
                createResultInfo.put("campaignIdList", campaignIdList);
                createResultInfo.put("advertiserId", map.get("advertiserId"));
                String adPlatformInfoNew = JsonUtils.getJsonAfterAddInfo("createResultInfo", createResultInfo, adResource.getAdPlatformInfo());
                adResourceMapper.updateAdPlatformInfo(adPlatformInfoNew, (String) map.get("adResourceId"));
            }
        }
        return isSuccess;
    }

    public void deleteCampaign(String advertiserId, List<String> campaignIdList) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("advertiser_id", advertiserId);
        bodyMap.put("campaign_ids", campaignIdList);
        bodyMap.put("operation_status", "DELETE");
        String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), UpdateCampaignStatusUrl);
        log.info("deleteCampaign response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
    }

    private String createAdSmartCreative(Map<String, Object> map) {
        int adCountEachCampaign = (int) map.get("adCountEachCampaign");//自能创意的时候表示一个广告例的素材数
        List<Map> materialUrlList = (List<Map>)map.get("materialUrlList");
        Collections.shuffle(materialUrlList);
        List<Map> selectedMaterialList = materialUrlList.subList(0, adCountEachCampaign);

        int textCountEachCampaign = (int) map.get("textCount");//自能创意的时候表示一个广告例的素材数
        List<String> textList = (List<String>) map.get("textList");
        Collections.shuffle(textList);
        List<String> selectedTextList = textList.subList(0, textCountEachCampaign);

        List<Map<String, String>> materialMapList = uploadVideoAndImageSmartCreative(selectedMaterialList, (String) map.get("advertiserId"));

        Map<String, Object> identityMap = getIdentityInfo((String) map.get("advertiserId"));
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("advertiser_id", map.get("advertiserId"));
        bodyMap.put("adgroup_id", map.get("adGroupId"));
        bodyMap.put("call_to_action_list", Arrays.asList(new HashMap() {
            {
                put("call_to_action", "READ_MORE");
            }
        }));
        bodyMap.put("common_material", new HashMap() {
            {
                put("identity_type", identityMap.get("identity_type"));
                put("identity_id", identityMap.get("identity_id"));
                put("is_smart_creative", true);
                put("ad_name", map.get("campaignName") + "_" + AppUtils.getUUID_10() + "_api");
            }
        });
        bodyMap.put("landing_page_urls", Arrays.asList(new HashMap() {
            {
                put("landing_page_url", map.get("landPageUrl"));
            }
        }));
        List<Map> mapList = selectedTextList.stream().map(e -> new HashMap() {
            {
                put("title", e);
            }
        }).collect(Collectors.toList());
        bodyMap.put("title_list", mapList);
        List<Object> mediaInfoList = new ArrayList<>();
        for (Map<String, String> materialMap : materialMapList) {
            Map<String, Object> mediaInfoMap = new HashMap<>();
            Map<String, Object> mediaInfo = new HashMap<>();
            Map<String, Object> videoInfoMap = new HashMap<>();
            videoInfoMap.put("video_id", materialMap.get("videoId"));
//            videoInfoMap.put("file_name", "videoName_" + materialMap.get("videoId"));
            List<Object> imageInfoList = new ArrayList<>();
            Map<String, Object> imageInfoMap = new HashMap<>();
            imageInfoMap.put("web_uri", materialMap.get("imageId"));
//            imageInfoMap.put("file_name", "imageName_" + materialMap.get("imageId"));
            imageInfoList.add(imageInfoMap);
            mediaInfo.put("video_info", videoInfoMap);
            mediaInfo.put("image_info", imageInfoList);
            mediaInfoMap.put("media_info", mediaInfo);
            mediaInfoList.add(mediaInfoMap);
        }
        bodyMap.put("media_info_list", mediaInfoList);
        log.info("createAdSmartCreative bodyMap={}", JsonUtils.toJsonString(bodyMap));
        String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), createAcoAdUrl);
        log.info("createAdSmartCreative response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
        if (responseMap.containsKey("code") && (Integer) responseMap.get("code") != 0) {
            adResourceMapper.updateAdPlatformStateAndMsg(AdResource.AD_PLATFORM_FAIL, "创建creative Ad失败. msg=" + response, (String) map.get("adResourceId"));
        }
        Map dataMap = (Map) responseMap.get("data");
        String adgroupId = (String) dataMap.get("adgroup_id");
        log.info("createAdSmartCreative adgroupId={}", adgroupId);
        return adgroupId;
    }

    private List<Map<String, String>> uploadVideoAndImageSmartCreative(List<Map> materialUrlList, String advertiserId) {
        List<Map<String, String>> list = new ArrayList<>();
        for (Map map : materialUrlList) {
            Map<String, String> materialMap = new HashMap<>();
            String imageId = handleImageUpload((String) map.get("cover"), advertiserId, mTempPath + AppUtils.getUUID());
            String videoId = handleVideoUpload((String) map.get("url"), advertiserId, mTempPath + AppUtils.getUUID());
            materialMap.put("videoId", videoId);
            materialMap.put("imageId", imageId);
            list.add(materialMap);
        }
        return list;
    }

    private List<String> createAd(Map<String, Object> map) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("advertiser_id", map.get("advertiserId"));
        bodyMap.put("adgroup_id", map.get("adGroupId"));
        int adCountEachCampaign = (int) map.get("adCountEachCampaign");

        List<Map> creativeList = new ArrayList<>();
        for (int i = 0; i < adCountEachCampaign; i++) {
            Map<String, Object> creativeItemMap = new HashMap<>();
            creativeItemMap.put("ad_name", map.get("campaignName") + "_" + AppUtils.getUUID_10() + "_api");
            Map<String, Object> identityMap = getIdentityInfo((String) map.get("advertiserId"));
            creativeItemMap.put("identity_type", identityMap.get("identity_type"));
            creativeItemMap.put("identity_id", identityMap.get("identity_id"));
            creativeItemMap.put("ad_format", "SINGLE_VIDEO");
            creativeItemMap.put("video_id", getVideoRandom((List<Map>)map.get("materialUrlList"), (String) map.get("advertiserId")));
            creativeItemMap.put("image_ids", getImageRandom((List<Map>)map.get("materialUrlList"), (String) map.get("advertiserId")));
            creativeItemMap.put("ad_text", getTextRandom((List<String>)map.get("textList")));
            creativeItemMap.put("call_to_action", "READ_MORE");//todo
            creativeItemMap.put("landing_page_url", map.get("landPageUrl"));
            creativeList.add(creativeItemMap);
        }
        bodyMap.put("creatives", creativeList);
        String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), createAdUrl);
        log.info("createAd response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
        if (responseMap.containsKey("code") && (Integer) responseMap.get("code") != 0) {
            adResourceMapper.updateAdPlatformStateAndMsg(AdResource.AD_PLATFORM_FAIL, "创建Ad失败. msg=" + response, (String) map.get("adResourceId"));
        }
        Map dataMap = (Map) responseMap.get("data");
        List<String> adIds = (List<String>) dataMap.get("ad_ids");
        log.info("createAd adId={}", adIds);
        return adIds;
    }

    private String[] getImageRandom(List<Map> materialUrlList, String advertiserId) {
        Random random = new Random();
        Map<String, Object> materialMap = materialUrlList.get(random.nextInt(materialUrlList.size()));
        String url = (String) materialMap.get("cover");
        String imageId = handleImageUpload(url, advertiserId, mTempPath + AppUtils.getUUID());
        return new String[] {imageId};
    }

    private String getVideoRandom(List<Map> materialUrlList, String advertiserId) {
        Random random = new Random();
        Map<String, Object> materialMap = materialUrlList.get(random.nextInt(materialUrlList.size()));
        String url = (String) materialMap.get("url");
        String videoId = handleVideoUpload(url, advertiserId, mTempPath + AppUtils.getUUID());
        return videoId;
    }

    private String getTextRandom(List<String> textList) {
        Random random = new Random();
        return textList.get(random.nextInt(textList.size()));
    }

    private String createCampaign(Map<String, Object> map) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("advertiser_id", map.get("advertiserId"));
        bodyMap.put("objective_type", "WEB_CONVERSIONS");
        bodyMap.put("is_smart_performance_campaign", false);
        bodyMap.put("operation_status", "ENABLE");
        bodyMap.put("campaign_name", map.get("campaignName") + "_" + AppUtils.getUUID_10() + "_api");
        bodyMap.put("budget_mode", map.get("campaignBudgetModel")); //BUDGET_MODE_INFINITE
        if (!"BUDGET_MODE_INFINITE".equals(map.get("campaignBudgetModel"))) {
            bodyMap.put("budget", map.get("campaignBudget"));
        }
        log.info("createCampaign body={}", JsonUtils.toJsonString(bodyMap));
        String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), createCampaignUrl);
        log.info("createCampaign response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
        if (responseMap.containsKey("code") && (Integer) responseMap.get("code") != 0) {
            adResourceMapper.updateAdPlatformStateAndMsg(AdResource.AD_PLATFORM_FAIL, "创建Campaign失败. msg=" + response, (String)map.get("adResourceId"));
        }
        Map dataMap = (Map) responseMap.get("data");
        String campaignId = (String) dataMap.get("campaign_id");
        log.info("createCampaign campaignId={}", campaignId);
        return campaignId;
    }

    private String createAdGroupSmartCreative(Map<String, Object> map) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("advertiser_id", map.get("advertiserId"));
        bodyMap.put("campaign_id", map.get("campaignId"));
        bodyMap.put("adgroup_name", map.get("campaignName") + "_" + AppUtils.getUUID_10() + "_api");
        bodyMap.put("optimization_goal", map.get("optimizationGoal"));//优化目标,点击CLICK，TRAFFIC_LANDING_PAGE_VIEW，CONVERT
        bodyMap.put("bid_type", map.get("bidPriceType"));//BID_TYPE_CUSTOM， BID_TYPE_NO_BID
        if (map.get("bidPriceType").equals("BID_TYPE_CUSTOM")) {
            bodyMap.put("conversion_bid_price", map.get("bidPriceValue"));//bidPriceValue
        }
        bodyMap.put("placements", ((List)map.get("placementList")).toArray());////PLACEMENT_TIKTOK:tiktok平台
        bodyMap.put("location_ids", getCountryIdArray((String) map.get("country")));
        bodyMap.put("budget_mode", map.get("adGroupBudgetModel"));
        bodyMap.put("budget", map.get("adGroupBudget"));
        bodyMap.put("promotion_type", "WEBSITE");
        bodyMap.put("schedule_type", "SCHEDULE_FROM_NOW");
        bodyMap.put("schedule_start_time", DateUtils.formatDate(System.currentTimeMillis() - 8 * 60 * 60 * 1000));
        bodyMap.put("billing_event", "OCPM");
        bodyMap.put("attribution_event_count", "ONCE");
        bodyMap.put("pacing", "PACING_MODE_SMOOTH");
        bodyMap.put("operation_status", "ENABLE");
        bodyMap.put("creative_material_mode", "CUSTOM");
        bodyMap.put("pixel_id", map.get("pixelId"));
        bodyMap.put("optimization_event", map.get("optimizationEvent"));//SHOPPING
        log.info("createAdGroupSmartCreative boyd={}", JsonUtils.toJsonString(bodyMap));
        String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), createAdGroupUrl);
        log.info("createAdGroupSmartCreative response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
        if (responseMap.containsKey("code") && (Integer) responseMap.get("code") != 0) {
            adResourceMapper.updateAdPlatformStateAndMsg(AdResource.AD_PLATFORM_FAIL, "创建AdGroup失败. msg=" + response, (String) map.get("adResourceId"));
        }
        Map dataMap = (Map) responseMap.get("data");
        String adgroupId = (String) dataMap.get("adgroup_id");
        log.debug("createAdGroupSmartCreative adgroupId={}", adgroupId);
        return adgroupId;
    }

    private String createAdGroup(Map<String, Object> map) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("advertiser_id", map.get("advertiserId"));
        bodyMap.put("campaign_id", map.get("campaignId"));
        bodyMap.put("adgroup_name", map.get("campaignName") + "_" + AppUtils.getUUID_10() + "_api");
        bodyMap.put("optimization_goal", map.get("optimizationGoal"));//优化目标,点击CLICK，TRAFFIC_LANDING_PAGE_VIEW，CONVERT
        bodyMap.put("bid_type", map.get("bidPriceType"));//BID_TYPE_CUSTOM， BID_TYPE_NO_BID
        if (map.get("bidPriceType").equals("BID_TYPE_CUSTOM")) {
            bodyMap.put("conversion_bid_price", map.get("bidPriceValue"));//bidPriceValue
        }
        bodyMap.put("placements", ((List)map.get("placementList")).toArray());////PLACEMENT_TIKTOK:tiktok平台
        bodyMap.put("location_ids", getCountryIdArray((String) map.get("country")));
        bodyMap.put("budget_mode", map.get("adGroupBudgetModel"));
        bodyMap.put("budget", map.get("adGroupBudget"));
        bodyMap.put("promotion_type", "WEBSITE");
        bodyMap.put("schedule_type", "SCHEDULE_FROM_NOW");
        bodyMap.put("schedule_start_time", DateUtils.formatDate(System.currentTimeMillis() - 8 * 60 * 60 * 1000));
        bodyMap.put("billing_event", "OCPM");
        bodyMap.put("attribution_event_count", "ONCE");
        bodyMap.put("pacing", "PACING_MODE_SMOOTH");
        bodyMap.put("operation_status", "ENABLE");
        bodyMap.put("pixel_id", map.get("pixelId"));
        bodyMap.put("optimization_event", map.get("optimizationEvent"));//SHOPPING
        log.info("createAdGroup boyd={}", JsonUtils.toJsonString(bodyMap));
        String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), createAdGroupUrl);
        log.info("createAdGroup response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
        if (responseMap.containsKey("code") && (Integer) responseMap.get("code") != 0) {
            adResourceMapper.updateAdPlatformStateAndMsg(AdResource.AD_PLATFORM_FAIL, "创建AdGroup失败. msg=" + response, (String) map.get("adResourceId"));
        }
        Map dataMap = (Map) responseMap.get("data");
        String adgroupId = (String) dataMap.get("adgroup_id");
        log.debug("createAdGroup adgroupId={}", adgroupId);
        return adgroupId;
    }

    private Object[] getCountryIdArray(String country) {
        List<String> resultList = new ArrayList<>();
        String[] array = country.split(",");
        for (String code : array) {
            resultList.add(TiktokCountryIdService.CODE_ID_MAP.get(code));
        }
        return resultList.toArray();
    }

    public Map<String, Object> getIdentityInfo(String advertiserId) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        String response = httpService.get(getIdentifyUrl + "?advertiser_id=" + advertiserId, headerMap);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
        Map dataMap = (Map) responseMap.get("data");
        List<Map> identityList = (List<Map>) dataMap.get("identity_list");
        Map<String, Object> identityMap = (Map<String, Object>) identityList.get(0);
        return identityMap;
    }

    public Map<String, String> getPixelIdByCode(String advertiserId, String pixelCode) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        String response = httpService.get(getPixelInfoUrl + "?advertiser_id=" + advertiserId + "&code=" + pixelCode, headerMap);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
        Integer code = (Integer) responseMap.get("code");
        if (code != 0) {
            return new HashMap<>();
        }
        Map dataMap = (Map) responseMap.get("data");
        List<Map> pixelsList = (List<Map>) dataMap.get("pixels");
        if (pixelsList.isEmpty()) {
            return new HashMap<>();
        }
        log.info("getPixelIdByCode pixelsList={}", pixelsList);
        String pixelId = (String) pixelsList.get(0).get("pixel_id");
        List<Map> events = (List<Map>) pixelsList.get(0).get("events");
        String eventType = "";
        for (Map event : events) {
            eventType += (String) event.get("event_type");
        }
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("pixelId", pixelId);
        resultMap.put("eventType", eventType);
        return resultMap;
    }

    public String handleVideoUpload(String url, String advertiserId, String path) {
        try {
            httpService.downloadFile(url, path);

            Map<String, String> headerMap = new HashMap<>();
            headerMap.put("Access-Token", TiktokAdDataService.mToken);
            MultiValueMap<String,Object> multiValueMap = new LinkedMultiValueMap<>();
            multiValueMap.add("advertiser_id", advertiserId);
            multiValueMap.add("upload_type", "UPLOAD_BY_FILE");
            multiValueMap.add("video_file", new FileSystemResource(path));
            String md5 = DigestUtils.md5Hex(new FileInputStream(path));
            multiValueMap.add("video_signature", md5);
            multiValueMap.add("flaw_detect",true);
            multiValueMap.add("auto_fix_enabled",true);
            multiValueMap.add("auto_bind_enabled",true);
            String response = httpService.postFileForTikTok(headerMap, multiValueMap, uploadVideoUrl);
            log.debug("uploadVideo response={}", response);
            Map<String, Object> responseMap = JsonUtils.toMap(response);
            if (responseMap.containsKey("code") && (Integer) responseMap.get("code") != 0) {
                throw new RuntimeException("上传视频失败. msg=" + response);
            }
            List<Map> dataMap = (List<Map>) responseMap.get("data");
            Map<String, Object> mapItem = (Map) dataMap.get(0);
            String videoId = (String) mapItem.get("video_id");
            log.debug("uploadVideo videoId={}", videoId);
            new File(path).delete();
            return videoId;
        } catch (Exception e) {
            log.error("handleVideoUpload error={}", e);
            throw new RuntimeException("上传视频失败. msg=" + e.getMessage());
        }
    }

    public String handleImageUpload(String url, String advertiserId, String path) {
        try {
            httpService.downloadFile(url, path);
            Map<String, String> headerMap = new HashMap<>();
            headerMap.put("Access-Token", TiktokAdDataService.mToken);
            MultiValueMap<String,Object> multiValueMap = new LinkedMultiValueMap<>();
            multiValueMap.add("advertiser_id", advertiserId);
            multiValueMap.add("upload_type", "UPLOAD_BY_FILE");
            String md5 = DigestUtils.md5Hex(new FileInputStream(path));
            multiValueMap.add("image_signature", md5);
            multiValueMap.add("image_file", new FileSystemResource(path));

            String response = httpService.postFileForTikTok(headerMap, multiValueMap, uploadImageUrl);
            log.info("uploadVideo response={}", response);
            Map<String, Object> responseMap = JsonUtils.toMap(response);
            if (responseMap.containsKey("code") && (Integer) responseMap.get("code") != 0) {
                throw new RuntimeException("上传图片失败. msg=" + response);
            }
            Map dataMap = (Map) responseMap.get("data");
            String imageId = (String) dataMap.get("image_id");
            log.debug("uploadVideo imageId={}", imageId);
            new File(path).delete();
            return imageId;
        } catch (Exception e) {
            throw new RuntimeException("上传图片失败. msg=" + e.getMessage());
        }
    }

    public String uploadImage(String url, String advertiserId) {//ad-site-i18n-sg/20250523c7400a49875c37e842a8b517
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        MultiValueMap<String,Object> multiValueMap = new LinkedMultiValueMap<>();
        multiValueMap.add("advertiser_id", advertiserId);
        multiValueMap.add("upload_type", "UPLOAD_BY_URL");
        multiValueMap.add("file_name", UUID.randomUUID().toString().replace("-", ""));
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(url);
        URI uri = uriComponentsBuilder.build(true).toUri();

        multiValueMap.add("image_url", uri.toString());
        log.info("uploadImage uri={}", uri);
        String response = httpService.postFileForTikTok(headerMap, multiValueMap, uploadImageUrl);
        log.debug("uploadVideo response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
        if (responseMap.containsKey("code") && (Integer) responseMap.get("code") != 0) {
            throw new RuntimeException("上传图片失败. msg=" + response);
        }
        Map dataMap = (Map) responseMap.get("data");
        String imageId = (String) dataMap.get("image_id");
        log.debug("uploadVideo imageId={}", imageId);
        return imageId;
    }

    public String uploadVideo(String url, String advertiserId) {//v10033g50000d0ng0fnog65qc1vdki8g
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);

        MultiValueMap<String,Object> multiValueMap = new LinkedMultiValueMap<>();
        multiValueMap.add("advertiser_id", advertiserId);
        multiValueMap.add("upload_type", "UPLOAD_BY_URL");
        multiValueMap.add("file_name", UUID.randomUUID().toString().replace("-", ""));
        multiValueMap.add("flaw_detect",true);
        multiValueMap.add("auto_fix_enabled",true);
        multiValueMap.add("auto_bind_enabled",true);
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(url);
        URI uri = uriComponentsBuilder.build(true).toUri();
        multiValueMap.add("video_url", uri.toString());
        log.info("uploadVideo video_url={}", uri);
        String response = httpService.postFileForTikTok(headerMap, multiValueMap, uploadVideoUrl);
        log.debug("uploadVideo response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
        if (responseMap.containsKey("code") && (Integer) responseMap.get("code") != 0) {
            throw new RuntimeException("上传视频失败. msg=" + response);
        }
        List<Map> dataMap = (List<Map>) responseMap.get("data");
        Map<String, Object> mapItem = (Map) dataMap.get(0);
        String videoId = (String) mapItem.get("video_id");
        log.debug("uploadVideo videoId={}", videoId);
        return videoId;
    }

    public void closeAdGroup(String advertiserId, String[] adgroupIds) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("advertiser_id", advertiserId);
        bodyMap.put("adgroup_ids", adgroupIds);
        bodyMap.put("operation_status", "DISABLE");
        String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), UpdateAdgroupStatusUrl);
        log.debug("closeCampaign response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
    }

    public void closeCampaign(String advertiserId, Object[] campaignIds) {
        log.info("closeCampaign campaignIds={}", campaignIds);
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("advertiser_id", advertiserId);
        bodyMap.put("campaign_ids", campaignIds);
        bodyMap.put("operation_status", "DISABLE");
        String response = httpService.post(headerMap, JsonUtils.toJsonString(bodyMap), UpdateCampaignStatusUrl);
        log.info("closeCampaign response={}", response);
        Map<String, Object> responseMap = JsonUtils.toMap(response);
    }

    public List<Map> getAdGroupList(String advertiserId, String campaignId) throws Exception {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(GetAdgroupUrl)
                .queryParam("advertiser_id", advertiserId)
                .queryParam("filtering", "{\"campaign_ids\":[\"" + campaignId + "\"]}")
                .queryParam("page_size", 1000);
        log.info("getAdGroupList url={}", builder.build().toUri());
        String response = httpService.get(builder.build().toUri(), headerMap);
        log.info("getAdGroupList response={}", response);
        Map respMap = (Map) JsonUtils.toMap(response);
        Map dataMap = (Map) respMap.get("data");
        List<Map> adGroupList = (List<Map>) dataMap.get("list");
        log.debug("getAdGroupList response={}", response);
        AppUtils.logTiktokTotalNumber(dataMap, "getAdGroupList. " + advertiserId);
        return adGroupList;
    }

    public List<Map> getAdGroupList(String advertiserId) throws Exception {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Access-Token", TiktokAdDataService.mToken);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(GetAdgroupUrl)
                .queryParam("advertiser_id", advertiserId)
                .queryParam("page_size", 1000);
        log.info("getAdGroupList url={}", builder.build().toUri());
        String response = httpService.get(builder.build().toUri(), headerMap);
        log.info("getAdGroupList response={}", response);
        Map respMap = JsonUtils.toMap(response);
        Map dataMap = (Map) respMap.get("data");
        List<Map> adGroupList = (List<Map>) dataMap.get("list");
        log.debug("getAdGroupList response={}", response);
        AppUtils.logTiktokTotalNumber(dataMap, "getAdGroupList. " + advertiserId);
        return adGroupList;
    }

    public List<Map> getAdGroupReportList(String advertiserId, String dateStart, String dataEnd) {
        String urlPre = "https://business-api.tiktok.com/open_api/v1.3/report/integrated/get/?advertiser_id=" + advertiserId + "&service_type=AUCTION&page=1&data_level=AUCTION_ADGROUP&report_type=BASIC&dimensions=[\"adgroup_id\"]&metrics=[\"impressions\", \"clicks\", \"spend\", \"conversion\"]";
        String url = urlPre + "&start_date=" + dateStart + "&end_date=" + dataEnd + "&page_size=1000&page=" + 1;
        Map<String, String> hMap = new HashMap<>();
        hMap.put("Access-Token", TiktokAdDataService.mToken);
        String response = httpService.get(url, hMap);
        log.info("getAdGroupReportList response={}", response);
        Map respMap = JsonUtils.toMap(response);
        Map dataMap = (Map) respMap.get("data");
        List<Map> mapList = (List<Map>) dataMap.get("list");
        log.debug("getAdGroupReportList response={}", response);
        AppUtils.logTiktokTotalNumber(dataMap, "getAdGroupReportList. " + advertiserId);
        return mapList;
    }

    public String getAdGroupOpStatus(String advertiserId, String groupId) {
        String opStatus = "ENABLE";
        try {
            Map<String, String> headerMap = new HashMap<>();
            headerMap.put("Access-Token", TiktokAdDataService.mToken);
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(GetAdgroupUrl)
                    .queryParam("advertiser_id", advertiserId)
                    .queryParam("filtering", "{\"adgroup_ids\":[\"" + groupId + "\"]}");
            log.info("getAdGroupList url={}", builder.build().toUri());
            String response = httpService.get(builder.build().toUri(), headerMap);
            log.info("getAdGroupOpStatus response={}", response);
            Map respMap = JsonUtils.toMap(response);
            Map dataMap = (Map) respMap.get("data");
            List<Map> adGroupList = (List<Map>) dataMap.get("list");
            log.debug("getAdGroupList response={}", response);
            opStatus = (String) adGroupList.get(0).get("operation_status");
            AppUtils.logTiktokTotalNumber(dataMap, "getAdGroupOpStatus. " + advertiserId);
        } catch (Exception e) {
            log.error("getAdGroupOpStatus error. advertiserId={}, groupId={}", advertiserId, groupId, e);
        }
        return opStatus;
    }
}
