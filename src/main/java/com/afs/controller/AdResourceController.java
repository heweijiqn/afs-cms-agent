package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.dao.*;
import com.afs.dao.model.*;
import com.afs.service.FeishuReportService;
import com.afs.service.TikTokApiService;
import com.afs.service.TiktokCountryIdService;
import com.afs.utils.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cms/ad/resource")
public class AdResourceController {

    @Autowired
    AdResourceMapper adResourceMapper;
    @Autowired
    ArticleInfoMapper articleInfoMapper;
    @Autowired
    FeishuReportService feishuReportService;
    @Autowired
    UserMapper userMapper;
    @Autowired
    AdsenseChannelMapper adsenseChannelMapper;
    @Autowired
    AdsenseAccountMapper adsenseAccountMapper;
    @Autowired
    AdResourceRuleMapper adResourceRuleMapper;
    @Autowired
    TiktokAdGroupInfoMapper tiktokAdGroupInfoMapper;
    @Autowired
    AdResourceRuleLogMapper adResourceRuleLogMapper;
    @Autowired
    TikTokApiService tikTokApiService;
    @Autowired
    Environment environment;

    @Data
    public static class AdResourceRequest {
        public String adResourceId;
        public String channelId;
        public String adPlatform;
        public String state;
        public String site;
        public String campaign;
        public String articleId;
        public String partner;
        public List<String> am;
        public String orderParam;
        public String orderDirection;
    }

    @PostMapping("/list")
    public ResponseUtils.Result getList(@RequestHeader(Consts.HeaderUserName) String userName, @RequestBody @Valid AdResourceRequest request,
                                        @RequestParam int pageIndex, @RequestParam int pageSize) {
        Page page = new Page();
        page.size = Math.min(100, Math.max(1, pageSize));
        page.index = pageIndex;
        int offset = Math.max(0, (pageIndex - 1)) * page.size;

        String partner = "";
        User user = userMapper.get(userName);
        if (user.getRoot() == User.RootUser && StringUtils.isNotEmpty(request.getPartner())) {
            partner = userMapper.get(request.getPartner()).getPartner();
        } else if (user.getRoot() == User.CommonUser) {
            partner = user.getPartner();
        }
        String orderParam = getOrderParam(request.getOrderParam());
        String orderDirection = getOrderDirection(request.getOrderDirection());

        List<AdResource> adResourceList = adResourceMapper.getListByQuery(request.getChannelId(),
                request.getAdResourceId(), request.getArticleId(), partner, request.getAm(), request.getAdPlatform(),
                request.getSite(), request.getState(), request.getCampaign(), orderParam, orderDirection, offset, page.size);
        page.list = adResourceList.stream().map(e -> toAdResourceVO(e)).collect(Collectors.toList());

        page.totalSize = adResourceMapper.countByQuery(request.getChannelId(), request.getAdResourceId(), request.getArticleId(),
                partner, request.getAm(), request.getAdPlatform(), request.getSite(), request.getState(), request.getCampaign());
        page.maxIndex = (int) Math.ceil((double) page.totalSize / page.size);
        return ResponseUtils.result(page);
    }

    private String getOrderParam(String orderParam) {
        if (StringUtils.isNotBlank(orderParam) && orderParam.equals("adSpend")) {
            return "ad_spend";
        } else if (StringUtils.isNotBlank(orderParam) && orderParam.equals("grossRevenue")) {
            return "gross_revenue";
        } else if (StringUtils.isNotBlank(orderParam) && orderParam.equals("profit")) {
            return "profit";
        } else if (StringUtils.isNotBlank(orderParam) && orderParam.equals("roi")) {
            return "roi";
        } else {
            return "id";
        }
    }


    private String getOrderDirection(String orderParam) {
        if (StringUtils.isNotBlank(orderParam) && orderParam.equals("asc")) {
            return "asc";
        } else if (StringUtils.isNotBlank(orderParam) && orderParam.equals("desc")) {
            return "desc";
        } else {
            return "desc";
        }
    }

    @Data
    public static class AdResourceVO {
        public Long id;
        public String accountId;
        public String adResourceId;
        public String title;
        public String articleId;
        public String channelId;
        public String campaign;
        public String am;
        public List<String> adPlatform;
        public String landPageUrl;
        public String partner;
        public String msg;
        public String country;
        public String keyword;
        public String adAccount;
        public String ruleName;
        public Long ruleId;
        public Double adSpend;
        public Double grossRevenue;
        public Double profit;
        public Double roi;
        public List<String> mediaList;
        public String adPlatformState;
        public String adPlatformMsg;

        //tt
        public Object optimizationGoal;
        public Object platformCampaign;
        public List<String> platformCampaignIds;
        public String localCampaignId;
        public Object optimizationEvent;
        public Object bidPriceType;
        public Object bidPriceValue;
        public Object placementList;
        public Object campaignBudgetModel;
        public Object campaignBudget;
        public Object adGroupBudgetModel;
        public Object adGroupBudget;
        public Object textList;
        public Object textCount;
        public Object isSmartCreative;//
        public Object materialList;
        public Object adAccountList;
        public Object campaignCountEachAccount;
        public Object adCountEachCampaign;

        public String state;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        public Date ctime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        public Date utime;
    }

    private AdResourceVO toAdResourceVO(AdResource e) {
        AdResourceVO adResourceVO = new AdResourceVO();
        adResourceVO.setId(e.getId());
        adResourceVO.setAdResourceId(e.getAdResourceId());
        adResourceVO.setArticleId(e.getArticleId());
        adResourceVO.setTitle(e.getTitle());
        adResourceVO.setChannelId(e.getChannelId());
        adResourceVO.setCampaign(e.getCampaign());
        adResourceVO.setAm(e.getAm());
        adResourceVO.setLandPageUrl(e.getLandPageUrl());
        adResourceVO.setAdPlatform(JsonUtils.toList(e.getAdPlatform(), String.class));
        adResourceVO.setMsg(e.getAuditMsg());
        adResourceVO.setAccountId(e.getAccountId());
        adResourceVO.setPartner(e.getPartner());
        adResourceVO.setCountry(e.getCountry());
        adResourceVO.setKeyword(e.getKeyword());
        adResourceVO.setAdAccount(e.getAdAccount());
        if (e.getRuleId() != null && e.getRuleId() != 0) {
            adResourceVO.setRuleId(e.getRuleId());
            AdResourceRule adResourceRule = adResourceRuleMapper.getById(e.getRuleId());
            adResourceVO.setRuleName(adResourceRule != null ? adResourceRule.getName() : "");
        }
        adResourceVO.setMediaList(StringUtils.isNotBlank(e.getMediaList()) ? JsonUtils.toList(e.getMediaList(), String.class) : new ArrayList<>());
        adResourceVO.setState(e.getState());
        adResourceVO.setAdSpend(e.getAdSpend());
        adResourceVO.setAdPlatformState(e.getAdPlatformState());
        adResourceVO.setAdPlatformMsg(e.getAdPlatformMsg());
        adResourceVO.setGrossRevenue(AppUtils.doubleToDouble2(e.getGrossRevenue()));
        adResourceVO.setProfit(AppUtils.doubleToDouble2(e.getProfit()));
        adResourceVO.setRoi(AppUtils.doubleToDouble2(e.getRoi()));
        adResourceVO.setCtime(e.getCtime());
        adResourceVO.setUtime(e.getUtime());

        if (StringUtils.isNotBlank(e.getAdPlatformInfo())) {
            Map<String, Object> adPlatformInfoMap = JsonUtils.toMap(e.getAdPlatformInfo());
            adResourceVO.setPlatformCampaign(adPlatformInfoMap.get("platformCampaign"));
            if (adPlatformInfoMap.containsKey("localCampaignId")) adResourceVO.setLocalCampaignId(e.getAdResourceId());
            if (adPlatformInfoMap.get("createResultInfo") instanceof Map<?, ?> creation
                    && creation.get("campaignIdList") instanceof List<?> ids) {
                adResourceVO.setPlatformCampaignIds(ids.stream().map(String::valueOf)
                        .filter(id -> id.matches("[0-9]+")).distinct().toList());
            }
            adResourceVO.setOptimizationGoal(adPlatformInfoMap.get("optimizationGoal"));
            adResourceVO.setOptimizationEvent(adPlatformInfoMap.get("optimizationEvent"));
            adResourceVO.setBidPriceType(adPlatformInfoMap.get("bidPriceType"));
            adResourceVO.setBidPriceValue(adPlatformInfoMap.get("bidPriceValue"));
            adResourceVO.setPlacementList(adPlatformInfoMap.get("placementList"));
            adResourceVO.setCampaignBudgetModel(adPlatformInfoMap.get("campaignBudgetModel"));
            adResourceVO.setCampaignBudget(adPlatformInfoMap.get("campaignBudget"));
            adResourceVO.setAdGroupBudgetModel(adPlatformInfoMap.get("adGroupBudgetModel"));
            adResourceVO.setAdGroupBudget(adPlatformInfoMap.get("adGroupBudget"));
            adResourceVO.setTextList(adPlatformInfoMap.get("textList"));
            adResourceVO.setTextCount(adPlatformInfoMap.get("textCount"));
            adResourceVO.setIsSmartCreative(adPlatformInfoMap.get("isSmartCreative"));
            adResourceVO.setMaterialList(adPlatformInfoMap.get("materialUrlList"));
            adResourceVO.setAdAccountList(adPlatformInfoMap.get("adAccountList"));
            adResourceVO.setCampaignCountEachAccount(adPlatformInfoMap.get("campaignCountEachAccount"));
            adResourceVO.setAdCountEachCampaign(adPlatformInfoMap.get("adCountEachCampaign"));
        }
        return adResourceVO;
    }

    @NoAuth
    @GetMapping("/platform/adg/list")
    public ResponseUtils.Result getAdgList(@RequestHeader(Consts.HeaderUserName) String userName, @RequestParam String adResourceId) {
        List<TiktokAdGroupInfo> infoList = tiktokAdGroupInfoMapper.getByAdResourceId(adResourceId);
        List<AdGroupInfoVO> voList = infoList.stream().map(e -> toAdGroupInfoVO(e)).collect(Collectors.toList());
        return ResponseUtils.result(voList);
    }

    @Data
    public static class AdGroupInfoVO {
        public Long id;
        public String adgroupId;
        public String advertiser;
        public String adgroupName;
        public Double budget;
        public Double adSpend;
        public Double conversion;
        public Double grossRevenue;
        public Double profit;
        public Double roi;
        public String adGroupStatus;
        public String campaignName;
        public String campaignId;
    }

    private AdGroupInfoVO toAdGroupInfoVO(TiktokAdGroupInfo e) {
        AdGroupInfoVO vo = new AdGroupInfoVO();
        vo.setId(e.getId());
        vo.setAdgroupId(e.getAdGroupId());
        vo.setAdvertiser(e.getAdvertiser());
        vo.setAdgroupName(e.getAdgroupName());
        vo.setAdGroupStatus(e.getAdGroupStatus());
        vo.setCampaignId(e.getCampaignId());
        vo.setCampaignName(e.getCampaignName());
        vo.setBudget(e.getBudget());
        vo.setAdSpend(e.getAdSpend());
        vo.setConversion(e.getConversion());
        vo.setGrossRevenue(AppUtils.doubleToDouble2(e.getGrossRevenue()));
        vo.setProfit(AppUtils.doubleToDouble2(e.getProfit()));
        vo.setRoi(AppUtils.doubleToDouble2(e.getRoi()));
        return vo;
    }


    @Data
    public static class AddBody {
//        @NotBlank
        public String accountId;
        @NotBlank
        public String articleId;
//        @NotBlank
        public String am;
        @NotNull
        public List<String> adPlatform;
        @NotBlank
        public String campaign;
        @NotBlank
        public String title;
        public String country;
        public String keyword;
        public String adAccount;
    }

    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody @Valid AddBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        AdResource adResource = new AdResource();
        String arsId = "ars_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        adResource.setAdResourceId(arsId);
        adResource.setTitle(body.getTitle());
        adResource.setArticleId(body.getArticleId());
        adResource.setChannelId("");
        adResource.setAm(StringUtils.isNotBlank(body.getAm()) ? body.getAm() : "");
        adResource.setCampaign(body.getCampaign());
        String adPlatformStr = JsonUtils.toJsonString(body.adPlatform);
        adResource.setAdPlatform(adPlatformStr);
        adResource.setLandPageUrl("");
        adResource.setAdDesc("");
        adResource.setAuditMsg("");
        adResource.setMediaList("");
        adResource.setCountry(StringUtils.isNotBlank(body.getCountry()) ? body.getCountry() : "");
        adResource.setKeyword(StringUtils.isNotBlank(body.getKeyword()) ? body.getKeyword() : "");
        adResource.setAdAccount(StringUtils.defaultString(body.getAdAccount()));
        adResource.setAccountId(StringUtils.isNotBlank(body.getAccountId()) ? body.getAccountId() : "");
        adResource.setState(AdResource.AUDITING);
        User user = userMapper.get(userName);
        adResource.setPartner(user.getPartner());
        adResource.setStatus(AdResource.StatusEnabled);

        if (user.getAutoCampaign() || user.getRoot() == User.RootUser) {
            String site = articleInfoMapper.getSiteByArticleId(adResource.getArticleId());
            log.info("getChannelId site={}", site);
            String channelId = getAutoAuditChannelId(site);
            if (StringUtils.isBlank(channelId)) {
                return ResponseUtils.error(-2, "暂无可使用的channelId，无法自动过审，请将不使用的Campaign归档或联系管理员");
            } else {
                adResourceMapper.add(adResource);
                Map<String, String> rMap = new HashMap<>();
                rMap.put("channelId", channelId);
                rMap.put("site", site);
                rMap.put("adResourceId", arsId);
                return ResponseUtils.result(rMap);
            }
        } else {
            adResourceMapper.add(adResource);
            String content = "Auditing Campaign! userName=" + userName + ". campaignId=" + arsId + ". partner=" + user.partner;
            feishuReportService.reportUserAlert(content, FeishuReportService.mAuditAlertUrl);
            return ResponseUtils.result(new java.util.LinkedHashMap<>(java.util.Map.of("adResourceId", arsId)));
        }
    }

    private String getAutoAuditChannelId(String site) {
        AdsenseAccount adsenseAccount = adsenseAccountMapper.getBySite(site);
        log.info("getChannelId adsenseChannel1={}", adsenseAccount);
        if (adsenseAccount == null) {
            return "";
        }
        AdsenseChannel adsenseChannel = adsenseChannelMapper.getOneChannelIdByStateAndPubId(AdsenseChannel.INIT, adsenseAccount.getPubId());
        log.info("getChannelId adsenseChannel2={}", adsenseChannel);
        if (adsenseChannel == null) {
            adsenseChannel = adsenseChannelMapper.getOneChannelIdByStateAndPubIdAndTime(AdsenseChannel.ARCHIVE, adsenseAccount.getPubId(),
                    DateUtils.formatDate(DateUtils.getTimestampTodayStart()));
        }
        log.info("getChannelId adsenseChannel3={}", adsenseChannel);
        if (adsenseChannel == null) {
            return "";
        }
        int n = adsenseChannelMapper.updateByChannelIdAndState(adsenseChannel.getChannelId(), AdsenseChannel.ONLINE);
        if (n <= 0) {
            return "";
        }
        log.info("getChannelId n={}, adsenseChannel4={}", n, adsenseChannel);
        return adsenseChannel.getChannelId();
    }

    @Data
    public static class TiktokAddBody {
        @NotBlank
        public String articleId;
        @NotBlank
        public String title;
        @NotEmpty
        public List<String> adAccountList;
        public String keyword;
        @NotBlank
        public String campaign;
        @NotBlank
        public String accountId;//pixelId
        @NotNull
        public Boolean isSmartCreative;//
        public Integer textCount;//
        @NotBlank
        public String optimizationGoal;
        @NotBlank
        public String optimizationEvent;
        @NotBlank
        public String bidPriceType;
        public Float bidPriceValue;
        @NotBlank
        public String country;
        @NotEmpty
        public List<String> placementList;
        @NotBlank
        public String campaignBudgetModel;
        public Float campaignBudget;
        @NotBlank
        public String adGroupBudgetModel;
        @NotNull
        public Float adGroupBudget;
        @NotEmpty
        public List<String> textList;
        @NotEmpty
        public List<TiktokMaterialObject> materialList;
        @NotNull
        public Integer campaignCountEachAccount;
        @NotNull
        public Integer adCountEachCampaign;
    }

    @Data
    public static class TiktokMaterialObject {
        private Long id;
        private String adMaterialId;

        private String name;
        private String userName;
        private Object countryList;
        private String lang;
        private String type;
        private String dimension;
        private Object category;
        private String ratio;
        private String template;
        private String cover;
        private String url;
        private Integer size;
        private Integer duration;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        private Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        private Date utime;
    }

    @NoAuth
    @PostMapping("/tiktok/add")
    public ResponseUtils.Result addTiktok(@RequestBody @Valid TiktokAddBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        log.info("addTiktok body={}", body);
        if (!body.getCampaignBudgetModel().equals("BUDGET_MODE_INFINITE") && body.getCampaignBudget() == null) {
            return ResponseUtils.error(-1, "请设置Campaign预算");
        }
        if (!body.getBidPriceType().equals("BID_TYPE_NO_BID") && body.getBidPriceValue() == null) {
            return ResponseUtils.error(-1, "请设置出价");
        }
        if (body.getIsSmartCreative() && !(body.getPlacementList().contains("PLACEMENT_PANGLE")
                && body.getPlacementList().contains("PLACEMENT_TIKTOK"))) {
            return ResponseUtils.error(-1, "smart creative placement is not right");
        }
        boolean local = environment.acceptsProfiles(Profiles.of("local-cms"));
        Map<String, String> pixelMap = local ? Collections.emptyMap() :
                tikTokApiService.getPixelIdByCode(body.getAdAccountList().get(0), body.getAccountId());
        if (!local) {
            if (StringUtils.isBlank(pixelMap.get("pixelId"))) {
                return ResponseUtils.error(-1, "pixelId获取失败，请检查pixelId是否正确");
            }
            if (!StringUtils.defaultString(pixelMap.get("eventType")).contains(body.getOptimizationEvent())) {
                return ResponseUtils.error(-1, "优化事件和pixel不匹配");
            }
        }
//        if (body.getMaterialList()) {
//            return ResponseUtils.error(-1, "优化事件和pixel不匹配");
//        }
        if (body.getIsSmartCreative() && body.getMaterialList().size() < body.getAdCountEachCampaign()) {
            return ResponseUtils.error(-1, "素材数量有问题，请检查");
        }
        if (body.getIsSmartCreative() && (body.getTextCount() == null || body.getTextCount() < 1
                || body.getTextList().size() < body.getTextCount())) {
            return ResponseUtils.error(-1, "文案数量有问题，请检查");
        }

        for (String country : body.getCountry().split(",")) {
            if (!TiktokCountryIdService.CODE_ID_MAP.keySet().contains(country)) {
                return ResponseUtils.error(-1, "请检查Country是否正确");
            }
        }

        AdResource adResource = new AdResource();
        String arsId = "ars_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        adResource.setAdResourceId(arsId);
        adResource.setTitle(body.getTitle());
        adResource.setArticleId(body.getArticleId());
        adResource.setChannelId("");
        adResource.setAm("");
        adResource.setCampaign(body.getCampaign());
        adResource.setAdPlatform(JsonUtils.toJsonString(Arrays.asList("TikTok")));
        adResource.setLandPageUrl("");
        adResource.setAdDesc("");
        adResource.setAuditMsg("");
        adResource.setMediaList("");
        adResource.setCountry(StringUtils.isNotBlank(body.getCountry()) ? body.getCountry() : "");
        adResource.setKeyword(StringUtils.isNotBlank(body.getKeyword()) ? body.getKeyword() : "");
        adResource.setAdAccount("");
        adResource.setAccountId(StringUtils.isNotBlank(body.getAccountId()) ? body.getAccountId() : "");
        adResource.setState(AdResource.AUDITING);
        User user = userMapper.get(userName);
        adResource.setPartner(user.getPartner());
        adResource.setStatus(AdResource.StatusEnabled);

        Map<String, Object> infoMap = new HashMap<>();
        infoMap.put("adAccountList", body.getAdAccountList());
        infoMap.put("optimizationGoal", body.getOptimizationGoal());
        infoMap.put("accountId", body.getAccountId());
        infoMap.put("isSmartCreative", body.getIsSmartCreative());
        infoMap.put("textCount", body.getTextCount());
        infoMap.put("optimizationEvent", body.getOptimizationEvent());
        infoMap.put("bidPriceType", body.getBidPriceType());
        infoMap.put("bidPriceValue", body.getBidPriceValue());
        infoMap.put("placementList", body.getPlacementList());
        infoMap.put("campaignBudgetModel", body.getCampaignBudgetModel());
        infoMap.put("campaignBudget", body.getCampaignBudget());
        infoMap.put("adGroupBudgetModel", body.getAdGroupBudgetModel());
        infoMap.put("adGroupBudget", body.getAdGroupBudget());
        infoMap.put("textList", body.getTextList());
        infoMap.put("materialUrlList", body.getMaterialList());
        infoMap.put("campaignCountEachAccount", body.getCampaignCountEachAccount());
        infoMap.put("adCountEachCampaign", body.getAdCountEachCampaign());
        infoMap.put("pixelId", pixelMap.get("pixelId"));
        adResource.setAdPlatformInfo(JsonUtils.toJsonString(infoMap));

        if (user.getAutoCampaign() || user.getRoot() == User.RootUser) {
            String site = articleInfoMapper.getSiteByArticleId(adResource.getArticleId());
            log.info("getChannelId site={}", site);
            String channelId = getAutoAuditChannelId(site);
//            channelId = (1000000000L + new Random().nextInt(10))  + "";
            if (StringUtils.isBlank(channelId)) {
                return ResponseUtils.error(-2, "暂无可使用的channelId，无法自动过审，请将不使用的Campaign归档或联系管理员");
            } else {
                adResourceMapper.add(adResource);
                Map<String, String> rMap = new HashMap<>();
                rMap.put("channelId", channelId);
                rMap.put("site", site);
                rMap.put("adResourceId", arsId);
                return ResponseUtils.result(rMap);
            }
        } else {
            adResourceMapper.add(adResource);
            String content = "Auditing Campaign! userName=" + userName + ". campaignId=" + arsId + ". partner=" + user.partner;
            feishuReportService.reportUserAlert(content, FeishuReportService.mAuditAlertUrl);
            return ResponseUtils.result(new java.util.LinkedHashMap<>(java.util.Map.of("adResourceId", arsId)));
        }
    }


    @Data
    public static class AuditBody {
        @NotBlank
        public String adResourceId;
        @NotBlank
        public String state;
        public String channelId;
        public String landPageUrl;
        public String msg = "";
    }

    @PostMapping("/audit")
    public ResponseUtils.Result audit(@RequestHeader(Consts.HeaderUserName) String userName, @RequestBody @Valid AuditBody body) {
        User user = userMapper.get(userName);
        if (user.getRoot() == User.CommonUser && !user.getAutoCampaign()) {
            return ResponseUtils.error(-1, "no permission");
        }
        if (body.getState().equals(AdResource.PASS) && StringUtils.isNotBlank(body.getLandPageUrl()) && !isValidLandPageUrl(body.getLandPageUrl())) {
            return ResponseUtils.error(-1, "落地页网址不合规");
        }
        if (body.getState().equals(AdResource.PASS) && hasOtherAdResourceUseChannelIdNew(body.getAdResourceId(), body.getChannelId())) {
            return ResponseUtils.error(-1, "channelId 已经被占用");
        }
        if (body.getState().equals(AdResource.PASS) && StringUtils.isBlank(body.getChannelId())) {
            return ResponseUtils.error(-1, "channelId 不合规");
        }
        if (body.getState().equals(AdResource.PASS)
                && adsenseChannelMapper.getListByQuery(null, null, body.getChannelId(), 0, 1).isEmpty()) {
            return ResponseUtils.error(-1, "channelId 不存在或未在当前站点配置");
        }
        if (body.getState().equals(AdResource.PASS) && StringUtils.isBlank(body.getLandPageUrl())) {
            return ResponseUtils.error(-1, "landPageUrl 不合规");
        }

        int n = 0;
        if (body.getState().equals(AdResource.PASS)) {
            n = adResourceMapper.auditPass(body.getChannelId(), body.getLandPageUrl(), body.getState(), body.getAdResourceId());
            if (n > 0) {
                adsenseChannelMapper.updateByChannelId(body.getChannelId(), AdsenseChannel.ONLINE);
                createAdPlatformAd(body.getAdResourceId());
            }
        }
        if (body.getState().equals(AdResource.REJECT)) {
            n = adResourceMapper.auditReject(body.getMsg(), body.getState(), body.getAdResourceId());
        }
        if (n > 0) {
            adResourceMapper.recordAudit(body.getAdResourceId(), userName);
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    public void createAdPlatformAd(String adResourceId) {
        AdResource adResource = adResourceMapper.getByAdResourceId(adResourceId);
        if (StringUtils.isNotBlank(adResource.getAdPlatformInfo())) {
            if (environment.acceptsProfiles(Profiles.of("local-cms"))) {
                adResourceMapper.updateAdPlatformStateAndMsg("LOCAL_ONLY", "本地活动已保存，平台投放未启用", adResourceId);
                return;
            }
            Map<String, Object> infoMap = JsonUtils.toMap(adResource.getAdPlatformInfo());

            infoMap.put("adResourceId", adResourceId);
            infoMap.put("campaignName", adResource.getCampaign());
            infoMap.put("pixelCode", adResource.getAccountId());
            infoMap.put("country", adResource.getCountry());
            infoMap.put("landPageUrl", adResource.getLandPageUrl());
            tikTokApiService.createCampaignAndAdGroupAndAd(infoMap);
        }
    }

    private boolean hasOtherAdResourceUseChannelIdNew(String adResourceId, String channelId) {
        int n = adResourceMapper.countOtherAdResourceIdByChannelIdByStateList(adResourceId, channelId, Arrays.asList(AdResource.PASS, AdResource.AUDITING));
        log.info("hasOtherAdResourceUseChannelIdNew channelId={}, n={}", channelId, n);
        if (n > 0) {
            return true;
        } else {
            return false;
        }
    }

//    private boolean hasOtherAdResourceUseChannelId(String channelId) {
//        int n = adResourceMapper.countByChannelIdByStateList(channelId, Arrays.asList(AdResource.PASS, AdResource.AUDITING));
//        log.info("hasOtherAdResourceUseChannelId channelId={}, n={}", channelId, n);
//        if (n > 1) {
//            return true;
//        } else {
//            return false;
//        }
//    }

    private boolean isValidLandPageUrl(String landPageUrl) {
        String[] array = landPageUrl.split("channel=", 2);
        if (array.length < 2) return false;
        String channelId;
        if (array[1].contains("&")) {
            channelId = array[1].substring(0, array[1].indexOf("&"));
        } else {
            channelId = array[1];
        }
        return NumberUtils.isCreatable(channelId);
    }

    @Data
    public static class TiktokEditBody {
        @NotBlank
        public String adResourceId;
        public String keyword;
        @NotBlank
        public String campaign;
        @NotEmpty
        public List<String> adAccountList;
        @NotBlank
        public String accountId;//pixelId
        @NotNull
        public Boolean isSmartCreative;//
        public Integer textCount;
        @NotBlank
        public String optimizationGoal;
        @NotBlank
        public String optimizationEvent;
        @NotBlank
        public String bidPriceType;
        public Float bidPriceValue;
        @NotBlank
        public String country;
        @NotEmpty
        public List<String> placementList;
        @NotBlank
        public String campaignBudgetModel;
        public Float campaignBudget;
        @NotBlank
        public String adGroupBudgetModel;
        @NotNull
        public Float adGroupBudget;
        @NotEmpty
        public List<String> textList;
        @NotEmpty
        public List<TiktokMaterialObject> materialList;
        @NotNull
        public Integer campaignCountEachAccount;
        @NotNull
        public Integer adCountEachCampaign;
    }

    @PostMapping("/tiktok/edit")
    public ResponseUtils.Result tiktokEdit(@RequestBody @Valid TiktokEditBody body) {
        if (!body.getCampaignBudgetModel().equals("BUDGET_MODE_INFINITE") && body.getCampaignBudget() == null) {
            return ResponseUtils.error(-1, "请设置Campaign预算");
        }
        if (!body.getBidPriceType().equals("BID_TYPE_NO_BID") && body.getBidPriceValue() == null) {
            return ResponseUtils.error(-1, "请设置出价");
        }
        boolean local = environment.acceptsProfiles(Profiles.of("local-cms"));
        Map<String, String> pixelMap = local ? Collections.emptyMap() :
                tikTokApiService.getPixelIdByCode(body.getAdAccountList().get(0), body.getAccountId());
        if (!local) {
            if (StringUtils.isBlank(pixelMap.get("pixelId"))) {
                return ResponseUtils.error(-1, "pixelId获取失败，请检查pixelId是否正确");
            }
            if (!StringUtils.defaultString(pixelMap.get("eventType")).contains(body.getOptimizationEvent())) {
                return ResponseUtils.error(-1, "优化事件和pixel不匹配");
            }
        }
        for (String country : body.getCountry().split(",")) {
            if (!TiktokCountryIdService.CODE_ID_MAP.keySet().contains(country)) {
                return ResponseUtils.error(-1, "请检查Country是否正确");
            }
        }

        AdResource adResource = new AdResource();
        adResource.setAdResourceId(body.getAdResourceId());
        adResource.setCampaign(body.getCampaign());
        adResource.setCountry(body.getCountry());
        adResource.setKeyword(StringUtils.isNotBlank(body.getKeyword()) ? body.getKeyword() : "");
        adResource.setAccountId(body.getAccountId());

        Map<String, Object> infoMap = new HashMap<>();
        infoMap.put("adAccountList", body.getAdAccountList());
        infoMap.put("optimizationGoal", body.getOptimizationGoal());
        infoMap.put("accountId", body.getAccountId());
        infoMap.put("isSmartCreative", body.getIsSmartCreative());
        infoMap.put("textCount", body.getTextCount());
        infoMap.put("optimizationEvent", body.getOptimizationEvent());
        infoMap.put("bidPriceType", body.getBidPriceType());
        infoMap.put("bidPriceValue", body.getBidPriceValue());
        infoMap.put("placementList", body.getPlacementList());
        infoMap.put("campaignBudgetModel", body.getCampaignBudgetModel());
        infoMap.put("campaignBudget", body.getCampaignBudget());
        infoMap.put("adGroupBudgetModel", body.getAdGroupBudgetModel());
        infoMap.put("adGroupBudget", body.getAdGroupBudget());
        infoMap.put("textList", body.getTextList());
        infoMap.put("materialUrlList", body.getMaterialList());
        infoMap.put("campaignCountEachAccount", body.getCampaignCountEachAccount());
        infoMap.put("adCountEachCampaign", body.getAdCountEachCampaign());
        infoMap.put("pixelId", pixelMap.get("pixelId"));
        adResource.setAdPlatformInfo(JsonUtils.toJsonString(infoMap));
        int n = adResourceMapper.updateForTiktok(adResource);
        if (n > 0) {
            createAdPlatformAd(body.getAdResourceId());
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    @Data
    public static class EditBody {
        @NotBlank
        public String campaign;
        @NotBlank
        public String adResourceId;
        public String keyword;
        public String country;
    }

    @PostMapping("/edit")
    public ResponseUtils.Result edit(@RequestBody @Valid EditBody body) {
        int n = adResourceMapper.updateCampaign(body.getCountry(), body.getKeyword(), body.getCampaign(), body.getAdResourceId());
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }


    @Data
    public static class DelBody {
        @NotBlank
        public String adResourceId;
    }

    @PostMapping("/del")
    public ResponseUtils.Result del(@RequestBody @Valid DelBody body) {
        AdResource adResource = adResourceMapper.getByAdResourceId(body.getAdResourceId());
        log.info("del adResource={}", adResource);
        int n = adResourceMapper.del(body.getAdResourceId());
        if (n > 0) {
            if (!hasOtherAdResourceUseChannelIdNew(adResource.getAdResourceId(), adResource.getChannelId())) {
                adsenseChannelMapper.updateByChannelId(adResource.getChannelId(), AdsenseChannel.INIT);
            }
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    @Data
    public static class ArchiveBody {
        @NotBlank
        public String adResourceId;
    }

    @PostMapping("/archive")
    public ResponseUtils.Result archive(@RequestBody @Valid ArchiveBody body) {
        log.info("archive adResourceId={}", body.getAdResourceId());
        int n = adResourceMapper.archive(AdResource.ARCHIVE, body.getAdResourceId());
        if (n > 0) {
            AdResource adResource = adResourceMapper.getByAdResourceId(body.getAdResourceId());
            log.info("archive adResource={}", adResource);
            if (!hasOtherAdResourceUseChannelIdNew(adResource.getAdResourceId(), adResource.getChannelId())) {
                int m = adsenseChannelMapper.updateByChannelId(adResource.getChannelId(), AdsenseChannel.ARCHIVE);
                log.info("archive adResourceId={}, m={}", body.getAdResourceId(), m);
            }
            if (!environment.acceptsProfiles(Profiles.of("local-cms")) && StringUtils.isNotBlank(adResource.getAdPlatformInfo())) {
                Map<String, Object> map = JsonUtils.toMap(adResource.getAdPlatformInfo());
                if (map.get("createResultInfo") != null) {
                    log.info("archive createResultInfo={}", map.get("createResultInfo"));
                    Map<String, Object> createResultInfoMap = (Map<String, Object>) map.get("createResultInfo");
                    if (createResultInfoMap.get("campaignIdList") != null && createResultInfoMap.get("advertiserId") != null) {
                        tikTokApiService.closeCampaign((String) createResultInfoMap.get("advertiserId"), ((List<String>)createResultInfoMap.get("campaignIdList")).toArray());
                    }
                }
            }
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    @Data
    public static class BindRuleBody {
        @NotBlank
        public String adResourceId;
        @NotNull
        public Long ruleId;
    }

    @PostMapping("/rule/bind")
    public ResponseUtils.Result bindRule(@RequestBody @Valid BindRuleBody body) {
        log.info("bindRule body={}", body);
        int n = adResourceMapper.bindRule(body.getRuleId(), DateUtils.formatDate(System.currentTimeMillis()), body.getAdResourceId());
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    @Data
    public static class UnbindRuleBody {
        @NotBlank
        public String adResourceId;
    }

    @NoAuth
    @PostMapping("/rule/unbind")
    public ResponseUtils.Result unbindRule(@RequestBody @Valid UnbindRuleBody body) {
        log.info("bindRule body={}", body);
        int n = adResourceMapper.unBindRule(body.getAdResourceId());
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    @Data
    public static class RuleLogVO {
        public Long id;
        public Long ruleId;
        public String ruleName;
        public String campaignName;
        public String campaignId;
    }

    @GetMapping("/rule/log")
    public ResponseUtils.Result ruleActionLog(@RequestParam String adResourceId) {
        List<AdResourceRuleLog> list = adResourceRuleLogMapper.getByAdResourceId(adResourceId);
        return ResponseUtils.result(list);
    }

    @GetMapping("/search/info")
    public ResponseUtils.Result searchTitle(@RequestParam String adResourceId) {
        if (StringUtils.isBlank(adResourceId)) return ResponseUtils.result(new ArrayList<>());
        List<AdResource> list = adResourceMapper.searchAdResourceId(adResourceId);
        List<Map> resultList = new ArrayList<>();
        for (AdResource adResource : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("adResourceId", adResource.getAdResourceId());
            map.put("title", adResource.getTitle());
            map.put("campaign", adResource.getCampaign());
            resultList.add(map);
        }
        return ResponseUtils.result(resultList);
    }

    @GetMapping("/search/info/campaign")
    public ResponseUtils.Result searchTitleByTitle(@RequestParam String campaign) {
        if (StringUtils.isBlank(campaign)) return ResponseUtils.result(new ArrayList<>());
        List<AdResource> list = adResourceMapper.searchAdResourceCampaign(campaign);
        List<Map> resultList = new ArrayList<>();
        for (AdResource adResource : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("adResourceId", adResource.getAdResourceId());
            map.put("title", adResource.getTitle());
            map.put("campaign", adResource.getCampaign());
            resultList.add(map);
        }
        return ResponseUtils.result(resultList);
    }
}
