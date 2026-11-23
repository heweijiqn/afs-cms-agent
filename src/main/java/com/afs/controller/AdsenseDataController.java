package com.afs.controller;

import com.afs.dao.AdResourceMapper;
import com.afs.dao.AdsenseAccountMapper;
import com.afs.dao.AdsenseDataMapper;
import com.afs.dao.UserMapper;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.AdsenseAccount;
import com.afs.dao.model.User;
import com.afs.service.AdResourceService;
import com.afs.utils.AppUtils;
import com.afs.utils.Consts;
import com.afs.utils.JsonUtils;
import com.afs.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cms/ad/data")
public class AdsenseDataController {

    static List<String> mNatureUserList = Arrays.asList("NaturePower-admin", "NaturePower-1",
            "NaturePower-2", "NaturePower-3", "NaturePower-4", "NaturePower-5", "NaturePower-6");

    static List<String> mValibooksList = Arrays.asList("liyurou", "zhangxiao", "wangxueying", "duanmengjin");

    @Autowired
    AdsenseDataMapper adsenseDataMapper;
    @Autowired
    AdResourceMapper adResourceMapper;
    @Autowired
    UserMapper userMapper;
    @Autowired
    AdsenseAccountMapper adsenseAccountMapper;
    @Autowired
    AdResourceService adResourceService;

    @Data
    public static class FilterBody {
        public String pubId;
        public List<String> campaignId;
        public String campaign;
        public String channelId;
        public List<String> articleId;
        public String partner;
        public String adPlatform;
        public List<String> am;
        public List<String> country;
        @NotBlank
        public String dateStart;
        @NotBlank
        public String dateEnd;
    }

//    @NoAuth
    @PostMapping("/filter/list")
    public ResponseUtils.Result getAdFilterDataList(@RequestHeader(Consts.HeaderUserName) String userName, @RequestBody @Valid FilterBody searchBody) {
        if (mNatureUserList.contains(userName)) {//todo:临时处理
            searchBody.setPubId("pub-5732465227544576");
        }
        if (mValibooksList.contains(userName)) {//todo:临时处理
            searchBody.setPubId("pub-7093248230123551");
        }
        if (getDayBetween(searchBody.getDateStart(), searchBody.getDateEnd()) > 365) {
            return ResponseUtils.error( -1, "查询日期太长，只支持365天以内");
        }
        String searchUserName = searchBody.getPartner();//传的是name
        User user = userMapper.get(userName);
        String partner = null;
        if (StringUtils.isNotBlank(searchUserName) && user.getRoot() == User.RootUser) {
            partner = userMapper.get(searchUserName).getPartner();
        }
        if (user.getRoot() == User.CommonUser) {
            partner = user.getPartner();
        }
        log.info("userName={}, partner={}", user.getName(), partner);
        List<String> channelIdList = getChannelId(searchBody.getPubId(), searchBody.getChannelId(), partner, searchBody.getArticleId(),
                searchBody.getCampaignId(), searchBody.getCampaign(), searchBody.getAdPlatform(), searchBody.getAm());

        log.info("getAdFilterDataList channelIdList={}, searchBody={}", channelIdList, searchBody);
        List<Map<String, Object>> adDataList = adsenseDataMapper.getListByFilterAndDimenByDate(
                channelIdList, CollectionUtils.isEmpty(searchBody.getCountry()) ? null : searchBody.getCountry(), searchBody.getDateStart(), searchBody.getDateEnd());
        log.debug("getAdFilterDataList adDataList={}", adDataList);
        List<FilterAdDataVO> mapList = adDataList.stream().map(e -> toFilterAdDataVO(e)).collect(Collectors.toList());
        return ResponseUtils.result(mapList);
    }

    private List<String> getChannelId(String pubId, String channelId, String partner, List<String> articleIdList, List<String> campaignIdList,
                                      String campaign, String adPlatform, List<String> amList) {
        List<AdResource> adResourceList = adResourceMapper.getListByQueryForGetChannelId(articleIdList, partner, campaignIdList, campaign, adPlatform, amList);
        log.debug("getChannelId adResourceList list={}, articleId={}, partner={}, campaignId={}", adResourceList, articleIdList, partner, campaignIdList);
        List<String> channelIdList = adResourceList.stream()
                .map(e -> adResourceService.getCusChannelId(e.getAdResourceId(), e.getChannelId(), e.getLandPageUrl()))
                .filter(a -> StringUtils.isBlank(pubId) || a.contains(pubId)).collect(Collectors.toList());

        if (StringUtils.isNotBlank(channelId)) {
            List<String> matching = channelIdList.stream().distinct()
                    .filter(id -> id.equals("partner-" + pubId + ":" + channelId)
                            || id.equals(pubId + ":" + channelId))
                    .collect(Collectors.toList());
            return matching.isEmpty() ? Arrays.asList("placeholder") : matching;
        }

        if (CollectionUtils.isEmpty(channelIdList)) {
            return Arrays.asList("placeholder");
        }
        return new ArrayList<>(new HashSet<>(channelIdList));
    }

    @Data
    public static class FilterAdDataVO {
        public String date;
        public Double gross_revenue;
        public Integer funnel_impressions;
        public Integer funnel_clicks;
        public Double funnel_ctr;
        public Double funnel_eCPM;
        public Integer impressions;
        public Integer clicks;
        public Double ctr;
        public Double eCPM;
        public Double cost_per_click;

        public Integer ad_impressions;
        public Integer ad_conversion;
        public Double ad_spend;
        public Integer ad_clicks;
        public Double ad_ctr;
        public Double ad_cvr;
        public Double roi;
        public Double profit;
        public Double riskValue;
        public Double cpa;
    }

    private static double number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) value = row.get(column.toLowerCase(Locale.ROOT));
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private FilterAdDataVO toFilterAdDataVO(Map<String, Object> map) {
        log.debug("toFilterAdDataVO map={}", map);
        FilterAdDataVO adDataVO = new FilterAdDataVO();
        adDataVO.setDate((String) map.get("date"));
        adDataVO.setGross_revenue(Double.parseDouble(String.format("%.2f", number(map, "gross_revenue"))));
        adDataVO.setFunnel_impressions(Integer.parseInt(String.format("%.0f", number(map, "funnel_impressions"))));
        adDataVO.setFunnel_clicks(Integer.parseInt(String.format("%.0f", number(map, "funnel_clicks"))));
        adDataVO.setFunnel_ctr(Double.parseDouble(String.format("%.2f",number(map, "funnel_ctr"))));
        adDataVO.setFunnel_eCPM(Double.parseDouble(String.format("%.2f", number(map, "funnel_eCPM"))));
        adDataVO.setImpressions(Integer.parseInt(String.format("%.0f", number(map, "impressions"))));
        adDataVO.setClicks(Integer.parseInt(String.format("%.0f", number(map, "clicks"))));
        adDataVO.setCtr(Double.parseDouble(String.format("%.2f", number(map, "ctr"))));
        adDataVO.setECPM(Double.parseDouble(String.format("%.2f", number(map, "eCPM"))));
        adDataVO.setCost_per_click(Double.parseDouble(String.format("%.2f", number(map, "cost_per_click"))));

        Double tt_clicks = number(map, "tt_clicks") + number(map, "tb_clicks")
                + number(map, "gg_clicks") + number(map, "fb_clicks");
        Double tt_conversion = number(map, "tt_conversion") + number(map, "tb_conversion")
                + number(map, "gg_conversion") + number(map, "fb_conversion");
        Double tt_spend = number(map, "tt_spend") + number(map, "tb_spend")
                + number(map, "gg_spend") + number(map, "fb_spend");
        Double tt_impressions = number(map, "tt_impressions") + number(map, "tb_impressions")
                + number(map, "gg_impressions") + number(map, "fb_impressions");

        adDataVO.setAd_clicks(Integer.parseInt(String.format("%.0f", tt_clicks)));
        adDataVO.setAd_conversion(Integer.parseInt(String.format("%.0f", tt_conversion)));
        adDataVO.setAd_spend(Double.parseDouble(String.format("%.2f",  tt_spend)));
        adDataVO.setAd_impressions(Integer.parseInt(String.format("%.0f", tt_impressions)));

        double adCtr = 0.0;
        if (tt_impressions != 0) {
            adCtr = tt_clicks/tt_impressions;
        }
        adDataVO.setAd_ctr(Double.parseDouble(String.format("%.2f", adCtr)));

        double adCvr = 0.0;
        if (tt_clicks != 0) {
            adCvr = tt_conversion/tt_clicks;
        }
        adDataVO.setAd_cvr(Double.parseDouble(String.format("%.2f", adCvr)));

        double roi = 0.0;
        if (tt_spend != 0) {
            roi = number(map, "gross_revenue")/tt_spend;
        }
        adDataVO.setRoi(Double.parseDouble(String.format("%.2f", roi)));

        double profit = number(map, "gross_revenue") - tt_spend;
        adDataVO.setProfit(Double.parseDouble(String.format("%.2f", profit)));

        double cpa = 0.0;
        if (tt_conversion != 0) {
            cpa = number(map, "gross_revenue")/tt_conversion;
        }
        adDataVO.setCpa(Double.parseDouble(String.format("%.2f", cpa)));

        double riskValue = 0.0;
        if (tt_conversion != 0) {
            riskValue = number(map, "clicks")/tt_conversion;
        }
        adDataVO.setRiskValue(Double.parseDouble(String.format("%.2f", riskValue)));
        return adDataVO;
    }

    private long getDayBetween(String startDateString, String endDateString) {
        LocalDate startDate = LocalDate.parse(startDateString);
        LocalDate endDate = LocalDate.parse(endDateString).plusDays(1);
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        return daysBetween;
    }

    @Data
    public static class DimensionBody {
        public String pubId;
        public List<String> campaignId;
        public String campaign;
        public List<String> country;
        public List<String> articleId;
        public String partner;
        public String channelId;
        public String adPlatform;
        public List<String> am;
        @NotBlank
        public String dateStart;
        @NotBlank
        public String dateEnd;
        public List<String> dimensionList;
    }

//    @NoAuth
    @PostMapping("/dimension/list")
    public ResponseUtils.Result getAdDimensionDataList(@RequestHeader(Consts.HeaderUserName) String userName, @RequestBody @Valid DimensionBody searchBody) {
        if (mNatureUserList.contains(userName)) {//todo:临时处理
            searchBody.setPubId("pub-5732465227544576");
        }
        if (mValibooksList.contains(userName)) {//todo:临时处理
            searchBody.setPubId("pub-7093248230123551");
        }
        if (getDayBetween(searchBody.getDateStart(), searchBody.getDateEnd()) > 365) {
            return ResponseUtils.error( -1, "查询日期太长，只支持365天以内");
        }

        User user = userMapper.get(userName);
        String partner = null;
        if (StringUtils.isNotBlank(searchBody.getPartner()) && user.getRoot() == User.RootUser) {
            partner = userMapper.get(searchBody.getPartner()).getPartner();
        }
        if (user.getRoot() == User.CommonUser) {
            partner = user.getPartner();
        }

        List<String> channelIdList = getChannelId(searchBody.getPubId(), searchBody.getChannelId(), partner, searchBody.getArticleId(), searchBody.getCampaignId(), searchBody.getCampaign(), searchBody.getAdPlatform(), searchBody.getAm());
        log.debug("getAdDimensionDataList channelIdList={}, partner={}, articleId={}, campaignId={}", channelIdList, partner, searchBody.getArticleId(), searchBody.getCampaignId());

        List<Map<String, Object>> adDataList = new ArrayList<>();
        if ((CollectionUtils.isEmpty(searchBody.dimensionList) || Arrays.asList("date", "country", "campaignId").equals(searchBody.dimensionList))
                && !CollectionUtils.isEmpty(channelIdList)) {
            adDataList = adsenseDataMapper.getListByDimen(channelIdList, CollectionUtils.isEmpty(searchBody.getCountry()) ? null : searchBody.getCountry(),
                    searchBody.getDateStart(), searchBody.getDateEnd());
        }

        if (Arrays.asList("date").equals(searchBody.dimensionList)) {
            adDataList = adsenseDataMapper.getListByDimenDate(channelIdList, CollectionUtils.isEmpty(searchBody.getCountry()) ? null : searchBody.getCountry(),
                searchBody.getDateStart(), searchBody.getDateEnd());
        }

        if (Arrays.asList("date", "country").equals(searchBody.dimensionList)) {
            adDataList = adsenseDataMapper.getListByDimenDateAndCountry(channelIdList, CollectionUtils.isEmpty(searchBody.getCountry()) ? null : searchBody.getCountry(),
                    searchBody.getDateStart(), searchBody.getDateEnd());
        }

        if (Arrays.asList("country").equals(searchBody.dimensionList)) {
            adDataList = adsenseDataMapper.getListByDimenCountry(channelIdList, CollectionUtils.isEmpty(searchBody.getCountry()) ? null : searchBody.getCountry(),
                    searchBody.getDateStart(), searchBody.getDateEnd());
        }

        if (Arrays.asList("country", "campaignId").equals(searchBody.dimensionList) && !CollectionUtils.isEmpty(channelIdList)) {
            adDataList = adsenseDataMapper.getListByDimenCountryAndChannelId(channelIdList, CollectionUtils.isEmpty(searchBody.getCountry()) ? null : searchBody.getCountry(),
                    searchBody.getDateStart(), searchBody.getDateEnd());
        }

        if (Arrays.asList("campaignId").equals(searchBody.dimensionList) && !CollectionUtils.isEmpty(channelIdList)) {
            adDataList = adsenseDataMapper.getListByDimenChannelId(channelIdList, CollectionUtils.isEmpty(searchBody.getCountry()) ? null : searchBody.getCountry(),
                    searchBody.getDateStart(), searchBody.getDateEnd());
        }

        if (Arrays.asList("date", "campaignId").equals(searchBody.dimensionList) && !CollectionUtils.isEmpty(channelIdList)) {
            adDataList = adsenseDataMapper.getListByDimenDateAndChannelId(channelIdList, CollectionUtils.isEmpty(searchBody.getCountry()) ? null : searchBody.getCountry(),
                    searchBody.getDateStart(), searchBody.getDateEnd());
        }

        List<DimensionAdDataVO> resultList = adDataList.stream().map(e -> toDimensionAdDataVO(e)).collect(Collectors.toList());
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("list", resultList);
        resultMap.put("total", getTotalData(resultList));
        return ResponseUtils.result(resultMap);
    }

    private DimensionTotalDataVO getTotalData(List<DimensionAdDataVO> resultList) {
        DimensionTotalDataVO dimensionTotalDataVO = new DimensionTotalDataVO();
        Double sumGrossRevenue = 0d;
        Double sumFunnelImpressions = 0d;
        Double sumFunnelClicks = 0d;
        Double sumImpressions = 0d;
        Double sumClicks = 0d;
        Double sumAdImpressions = 0d;
        Double sumAdConversion = 0d;
        Double sumAdSpend = 0d;
        Double sumAdClicks = 0d;
        for (DimensionAdDataVO e : resultList) {
            sumGrossRevenue += Double.parseDouble(e.getGross_revenue());
            sumFunnelImpressions += Double.parseDouble(e.getFunnel_impressions());
            sumFunnelClicks += Double.parseDouble(e.getFunnel_clicks());
            sumImpressions += Double.parseDouble(e.getImpressions());
            sumClicks += Double.parseDouble(e.getClicks());
            sumAdImpressions += Double.parseDouble(e.getAd_impressions());
            sumAdConversion += Double.parseDouble(e.getAd_conversion());
            sumAdSpend += Double.parseDouble(e.getAd_spend());
            sumAdClicks += Double.parseDouble(e.getAd_clicks());
        }
        dimensionTotalDataVO.setGross_revenue(AppUtils.doubleToStr2(sumGrossRevenue));
        dimensionTotalDataVO.setFunnel_impressions(AppUtils.doubleToStr0(sumFunnelImpressions));
        dimensionTotalDataVO.setFunnel_clicks(AppUtils.doubleToStr0(sumFunnelClicks));
        dimensionTotalDataVO.setFunnel_ctr(AppUtils.doubleToStr2(sumFunnelClicks/sumFunnelImpressions*100));
        dimensionTotalDataVO.setFunnel_eCPM(AppUtils.doubleToStr2(sumGrossRevenue/sumFunnelImpressions*1000));
        dimensionTotalDataVO.setImpressions(AppUtils.doubleToStr0(sumImpressions));
        dimensionTotalDataVO.setClicks(AppUtils.doubleToStr0(sumClicks));
        dimensionTotalDataVO.setCtr(AppUtils.doubleToStr2(sumClicks/sumImpressions*100));
        dimensionTotalDataVO.setECPM(AppUtils.doubleToStr2(sumGrossRevenue/sumImpressions*1000));
        dimensionTotalDataVO.setCost_per_click(AppUtils.doubleToStr2(sumGrossRevenue/sumClicks));
        dimensionTotalDataVO.setAd_impressions(AppUtils.doubleToStr0(sumAdImpressions));
        dimensionTotalDataVO.setAd_conversion(AppUtils.doubleToStr0(sumAdConversion));
        dimensionTotalDataVO.setAd_spend(AppUtils.doubleToStr2(sumAdSpend));
        dimensionTotalDataVO.setAd_clicks(AppUtils.doubleToStr0(sumAdClicks));
        dimensionTotalDataVO.setAd_ctr(AppUtils.doubleToStr2(sumAdClicks/sumAdImpressions*100));
        dimensionTotalDataVO.setAd_cvr(AppUtils.doubleToStr2(sumAdConversion/sumAdClicks*100));
        dimensionTotalDataVO.setRoi(AppUtils.doubleToStr2(sumGrossRevenue/sumAdSpend));
        dimensionTotalDataVO.setProfit(AppUtils.doubleToStr2(sumGrossRevenue - sumAdSpend));
        dimensionTotalDataVO.setRiskValue(AppUtils.doubleToStr2(sumAdConversion != 0 ? sumClicks/sumAdConversion : 0));
        dimensionTotalDataVO.setCpa(AppUtils.doubleToStr2(sumAdConversion != 0 ? sumGrossRevenue/sumAdConversion : 0));
        return dimensionTotalDataVO;
    }

    private DimensionAdDataVO toDimensionAdDataVO(Map<String, Object> map) {
        log.debug("toDimensionAdDataVO map={}", map);
        DimensionAdDataVO adDataVO = new DimensionAdDataVO();
        adDataVO.setDate((String) map.get("date"));
        adDataVO.setCountry_code((String) map.get("country_code"));
        AdResource adResource = null;
        if (map.get("custom_channel_id") != null) {
            String channelId = getChannelIdFromCus((String) map.get("custom_channel_id"));
            adResource = adResourceMapper.getOneByChannelIdAndState(channelId, AdResource.PASS);
            log.debug("toDimensionAdDataVO channelId={}", map.get("custom_channel_id"));
            if (adResource != null) {
                adDataVO.setCampaignId(adResource.getAdResourceId());
                adDataVO.setCampaign(adResource.getCampaign());
            } else {
                log.warn("toDimensionAdDataVO map={}", map);
            }
        }
        adDataVO.setGross_revenue(String.format("%.2f", number(map, "gross_revenue")));
        adDataVO.setFunnel_impressions(String.format("%.0f", number(map, "funnel_impressions")));
        adDataVO.setFunnel_clicks(String.format("%.0f", number(map, "funnel_clicks")));
        adDataVO.setFunnel_ctr(String.format("%.2f", number(map, "funnel_ctr")));
        adDataVO.setFunnel_eCPM(String.format("%.2f", number(map, "funnel_eCPM")));
        adDataVO.setImpressions(String.format("%.0f", number(map, "impressions")));
        adDataVO.setClicks(String.format("%.0f", number(map, "clicks")));
        adDataVO.setCtr(String.format("%.2f", number(map, "ctr")));
        adDataVO.setECPM(String.format("%.2f",  number(map, "eCPM")));
        adDataVO.setCost_per_click(String.format("%.2f", number(map, "cost_per_click")));

        log.debug("toDimensionAdDataVO adResource={}", adResource);
        addAdInfoData(adDataVO, map);
        return adDataVO;
    }

    private String getChannelIdFromCus(String customChannelId) {
        if (StringUtils.isBlank(customChannelId)) {
            log.error("error to getChannelIdFromCus, customChannelId is empty:{}", customChannelId);
            return "";
        }
        if (!customChannelId.contains(":")) {
            log.error("error to getChannelIdFromCus, customChannelId is invalid:{}", customChannelId);
            return "";
        }
        String[] arr = customChannelId.split(":");
        return arr[1];
    }

    private void addAdInfoData(DimensionAdDataVO adDataVO, Map<String, Object> map) {
        log.debug("addTiktokData adDataVO={}, map={}", adDataVO, map);
        Double tt_clicks = number(map, "tt_clicks") + number(map, "tb_clicks")
                + number(map, "gg_clicks") + number(map, "fb_clicks");
        Double tt_conversion = number(map, "tt_conversion") + number(map, "tb_conversion")
                + number(map, "gg_conversion") + number(map, "fb_conversion");
        Double tt_spend = number(map, "tt_spend") + number(map, "tb_spend")
                + number(map, "gg_spend") + number(map, "fb_spend");
        Double tt_impressions = number(map, "tt_impressions") + number(map, "tb_impressions")
                + number(map, "gg_impressions") + number(map, "fb_impressions");

        adDataVO.setAd_clicks(String.format("%.0f", tt_clicks));
        adDataVO.setAd_conversion(String.format("%.0f", tt_conversion));
        adDataVO.setAd_spend(String.format("%.2f",  tt_spend));
        adDataVO.setAd_impressions(String.format("%.0f", tt_impressions));

        double adCtr = 0.0;
        if (tt_impressions != 0) {
            adCtr = tt_clicks/tt_impressions*100;
        }
        adDataVO.setAd_ctr(String.format("%.2f", adCtr));

        double adCvr = 0.0;
        if (tt_clicks != 0) {
            adCvr = tt_conversion/tt_clicks*100;
        }
        adDataVO.setAd_cvr(String.format("%.2f", adCvr));

        double roi = 0.0;
        if (tt_spend != 0) {
            roi = number(map, "gross_revenue")/tt_spend;
        }
        adDataVO.setRoi(String.format("%.2f", roi));

        double profit = number(map, "gross_revenue") - tt_spend;
        adDataVO.setProfit(String.format("%.2f", profit));

        double cpa = 0.0;
        if (tt_conversion != 0) {
            cpa = number(map, "gross_revenue")/tt_conversion;
        }
        adDataVO.setCpa(String.format("%.2f", cpa));

        double riskValue = 0.0;
        if (tt_conversion != 0) {
            riskValue = number(map, "clicks")/tt_conversion;
        }
        adDataVO.setRiskValue(String.format("%.2f", riskValue));
    }

    @Data
    public static class DimensionAdDataVO {
        public String date;
        public String country_code;
        public String campaignId;
        public String campaign;
        public String gross_revenue;
        public String funnel_impressions;
        public String funnel_clicks;
        public String funnel_ctr;
        public String funnel_eCPM;
        public String impressions;
        public String clicks;
        public String ctr;
        public String eCPM;
        public String cost_per_click;
        public String ad_impressions;
        public String ad_conversion;
        public String ad_spend;
        public String ad_clicks;
        public String ad_ctr;
        public String ad_cvr;
        public String roi;
        public String profit;
        public String riskValue;
        public String cpa;
    }

    @Data
    public static class DimensionTotalDataVO {
        public String gross_revenue;
        public String funnel_impressions;
        public String funnel_clicks;
        public String funnel_ctr;
        public String funnel_eCPM;
        public String impressions;
        public String clicks;
        public String ctr;
        public String eCPM;
        public String cost_per_click;
        public String ad_impressions;
        public String ad_conversion;
        public String ad_spend;
        public String ad_clicks;
        public String ad_ctr;
        public String ad_cvr;
        public String roi;
        public String profit;
        public String riskValue;
        public String cpa;
    }
}
