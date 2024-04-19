package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.dao.*;
import com.afs.service.FeishuReportService;
import com.afs.service.HttpService;
import com.afs.utils.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.afs.dao.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms/article")
public class ArticleController {

    private static final String cacheKey = System.getenv().getOrDefault("AFS_ARTICLE_CACHE_TOKEN", "");
    private static final String cloudflareKey = System.getenv().getOrDefault("AFS_CLOUDFLARE_API_TOKEN", "");
    private static final String cloudflareEmail = System.getenv().getOrDefault("AFS_CLOUDFLARE_AUTH_EMAIL", "");
    private static final String zoneMap = System.getenv().getOrDefault("AFS_CLOUDFLARE_ZONE_MAP", "{}");
    
    @Autowired
    ArticleInfoMapper articleInfoMapper;
    @Autowired
    GoogleBigqueryKeywordMapper googleBigqueryKeywordMapper;
    @Autowired
    KeywordReportDateMapper keywordReportDateMapper;
    @Autowired
    FeishuReportService feishuReportService;
    @Autowired
    AdResourceMapper adResourceMapper;
    @Autowired
    UserMapper userMapper;
    @Autowired
    HttpService httpService;
    @Autowired
    Environment environment;

    @Data
    public static class ArticleInfoVO {
        public String articleId;
        public String title;
        public String imgUrl;
        public String terms;
        public String category;
        public String firstP;
        public String textList;
        public String lang;
        public String site;
        public String state;
        public String msg;
        public String partner;
        public Integer insideShow;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date onlineTime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date utime;
    }

    @Data
    public static class ArticleInfoRequest {
        public String articleId;
        public String title;
        public String lang;
        public String state;
        public String site;
        public String partner;
    }

    @PostMapping("/list")
    public ResponseUtils.Result getList(@RequestHeader(Consts.HeaderUserName) String userName, @RequestBody @Valid ArticleInfoRequest request,
                                        @RequestParam int pageIndex, @RequestParam int pageSize) {
        Page page = new Page();
        page.size = Math.max(10, pageSize);
        page.index = pageIndex;
        int offset = Math.max(0, (pageIndex - 1)) * pageSize;
        String partner = "";
        User user = userMapper.get(userName);
        if (user.getRoot() == User.RootUser && StringUtils.isNotEmpty(request.getPartner())) {
            partner = userMapper.get(request.getPartner()).getPartner();
        } else if (user.getRoot() == User.CommonUser){
            partner = user.getPartner();
        }
        List<ArticleInfo> articleInfoList =
                articleInfoMapper.getListByQuery(partner, request.getSite(), request.getArticleId(), request.getTitle(), request.getLang(), request.getState(), offset, pageSize);
        page.list = articleInfoList.stream().map(e -> toArticleInfoRootVO(e)).collect(Collectors.toList());

        page.totalSize = articleInfoMapper.countByQuery(partner, request.getSite(), request.getArticleId(), request.getTitle(), request.getLang(), request.getState());
        page.maxIndex = page.totalSize / pageSize;
        return ResponseUtils.result(page);
    }

    private ArticleInfoVO toArticleInfoRootVO(ArticleInfo e) {
        ArticleInfoVO articleInfoVO = new ArticleInfoVO();
        articleInfoVO.setArticleId(e.getArticleId());
        articleInfoVO.setTitle(e.getTitle());
        articleInfoVO.setLang(e.getLang());
        articleInfoVO.setTerms(e.getTerms());
        articleInfoVO.setCategory(e.getCategory());
        articleInfoVO.setFirstP(e.getFirstPart());
        articleInfoVO.setTextList(e.getTextList());
        articleInfoVO.setImgUrl(e.getImgUrl());
        articleInfoVO.setInsideShow(e.getInsideShow());
        articleInfoVO.setSite(e.getSite());
        articleInfoVO.setPartner(e.getPartner());
        articleInfoVO.setState(e.getState());
        articleInfoVO.setMsg(ArticleInfo.getInfoFromOtherInfo("msg", e.getOtherInfo()));
        articleInfoVO.setOnlineTime(e.getOnlineTime());
        articleInfoVO.setCtime(e.getCtime());
        articleInfoVO.setUtime(e.getUtime());

        if (ArticleInfo.UPDATE_AUDITING.equals(e.getState()) || ArticleInfo.UPDATE_REJECT.equals(e.getState())) {
            if (StringUtils.isNotBlank(e.getOtherInfo())) {
                Map<String, Object> map = JsonUtils.toMap(e.getOtherInfo());
                if (map.get("newVersionInfo") != null) {
                    Map<String, Object> newDataMap = JsonUtils.toMap((String) map.get("newVersionInfo"));
                    articleInfoVO.setTitle(newDataMap.get("title") != null ? (String) newDataMap.get("title") : e.getTitle());
                    articleInfoVO.setImgUrl(newDataMap.get("imgUrl") != null ? (String) newDataMap.get("imgUrl") : e.getImgUrl());
                    articleInfoVO.setTextList(newDataMap.get("textList") != null ? (String) newDataMap.get("textList") : e.getTextList());
                    articleInfoVO.setTerms(newDataMap.get("terms") != null ? (String) newDataMap.get("terms") : e.getTerms());
                    articleInfoVO.setCategory(newDataMap.get("category") != null ? (String) newDataMap.get("category") : e.getCategory());
                    articleInfoVO.setLang(newDataMap.get("lang") != null ? (String) newDataMap.get("lang") : e.getLang());
                    articleInfoVO.setInsideShow(newDataMap.get("insideShow") != null ? (Integer) newDataMap.get("insideShow") : e.getInsideShow());
                    articleInfoVO.setFirstP(newDataMap.get("firstP") != null ? (String) newDataMap.get("firstP") : e.getFirstPart());
                }
            }
        }
        return articleInfoVO;
    }

    @Data
    public static class AddBody {
        String title;
        String imgUrl;
        Integer insideShow;
        String terms;
        String category;
        String textList;
        String lang;
        String firstP;
    }

    @PostMapping("/add")
    public ResponseUtils.Result addArticleInfo(@RequestBody AddBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        ArticleInfo articleInfo = new ArticleInfo();
        String articleId = "article_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        articleInfo.setArticleId(articleId);
        articleInfo.setTitle(body.title);
        articleInfo.setTextList(body.textList);
        articleInfo.setImgUrl(body.imgUrl);
        articleInfo.setTerms(body.terms);
        articleInfo.setCategory(body.category);
        articleInfo.setStrategy("");
        User user = userMapper.get(userName);
        articleInfo.setPartner(user.getPartner());
        articleInfo.setState(ArticleInfo.AUDITING);
        articleInfo.setSite("");
        articleInfo.setOnline(1);
        articleInfo.setInsideShow(body.insideShow);
        articleInfo.setFirstPart(body.firstP);
        articleInfo.setTextList(body.textList);
        articleInfo.setLang(body.lang);
        articleInfo.setOnlineTime(new Date());
        articleInfo.setOtherInfo("");
        articleInfo.setStatus(ArticleInfo.StatusEnabled);
        int n = articleInfoMapper.add(articleInfo);
//        String content = "Auditing Article! userName=" + userName + ". articleId=" + articleId + ". partner=" + user.partner;
//        feishuReportService.reportUserAlert(content, FeishuReportService.mAuditAlertUrl);
        if (n > 0) {
            Map<String, String> map = new HashMap<>();
            map.put("articleId", articleId);
            return ResponseUtils.result(map);
        } else {
            return ResponseUtils.result("更新失败");
        }
    }

    @Data
    public static class CancelUpdateBody {
        @NotBlank
        String articleId;
    }

    @PostMapping("/cancel/update")
    public ResponseUtils.Result cancelNewVersion(@RequestBody @Valid CancelUpdateBody body) {
        int n = articleInfoMapper.updateState(ArticleInfo.PASS, body.getArticleId());;
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error( -1, "撤回失败");
        }
    }

    @Data
    public static class EditBody {
        @NotBlank
        String articleId;
        String title;
        String imgUrl;
        Integer insideShow;
        String terms;
        String category;
        String textList;
        String lang;
        String firstP;
        String site;
    }

    @PostMapping("/edit")
    public ResponseUtils.Result updateArticleInfo(@RequestHeader(Consts.HeaderUserName) String userName, @RequestBody @Valid EditBody body) {
        ArticleInfo articleInfoDb = articleInfoMapper.getByArticleId(body.getArticleId());
        User user = userMapper.get(userName);
        int n = 0;
        if (user.autoArticle) {
            ArticleInfo articleInfo = new ArticleInfo();
            articleInfo.setTitle(body.title);
            articleInfo.setImgUrl(body.imgUrl);
            articleInfo.setInsideShow(body.insideShow);
            articleInfo.setTextList(body.textList);
            articleInfo.setTerms(body.terms);
            articleInfo.setCategory(body.category);
            articleInfo.setSite(body.getSite());
            articleInfo.setLang(body.getLang());
            articleInfo.setOnline(1);
            articleInfo.setFirstPart(body.firstP);
            articleInfo.setArticleId(body.articleId);
            articleInfo.setState(ArticleInfo.PASS);
            n = articleInfoMapper.updateArticleInfo(articleInfo);
        } else {
            if (ArticleInfo.AUDITING.equals(articleInfoDb.getState()) || ArticleInfo.REJECT.equals(articleInfoDb.getState())) {
                ArticleInfo articleInfo = new ArticleInfo();
                articleInfo.setTitle(body.title);
                articleInfo.setImgUrl(body.imgUrl);
                articleInfo.setInsideShow(body.insideShow);
                articleInfo.setTextList(body.textList);
                articleInfo.setTerms(body.terms);
                articleInfo.setCategory(body.category);
                articleInfo.setSite(body.getSite());
                articleInfo.setLang(body.getLang());
                articleInfo.setOnline(1);
                articleInfo.setFirstPart(body.firstP);
                articleInfo.setArticleId(body.articleId);
                articleInfo.setState(ArticleInfo.AUDITING);
                n = articleInfoMapper.updateArticleInfo(articleInfo);
            }
            if (ArticleInfo.PASS.equals(articleInfoDb.getState()) || ArticleInfo.UPDATE_AUDITING.equals(articleInfoDb.getState()) || ArticleInfo.UPDATE_REJECT.equals(articleInfoDb.getState())) {//add newVersion
                ArticleInfo articleInfo = new ArticleInfo();
                articleInfo.setArticleId(body.getArticleId());

                Map<String, Object> newVersionInfo = new HashMap<>();
                newVersionInfo.put("title",body.title);
                newVersionInfo.put("textList",body.textList);
                newVersionInfo.put("imgUrl",body.imgUrl);
                newVersionInfo.put("terms",body.terms);
                newVersionInfo.put("category",body.category);
                newVersionInfo.put("insideShow",body.insideShow);
                newVersionInfo.put("firstP",body.firstP);
                newVersionInfo.put("lang",body.lang);

                String otherInfo = ArticleInfo.getOtherInfoAfterAddInfo("newVersionInfo", JsonUtils.toJsonString(newVersionInfo), articleInfoDb.getOtherInfo());
                articleInfo.setOtherInfo(otherInfo);
                articleInfo.setState(ArticleInfo.UPDATE_AUDITING);
                log.info("updateArticleInfo articleInfo={}", articleInfo);
                n = articleInfoMapper.updateArticleInfo(articleInfo);
            }
        }
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error( -1, "更新失败");
        }
    }

    @Data
    public static class OnOffBody {
        @NotNull
        Integer onOff;
        @NotBlank
        String articleId;
    }

    @PostMapping("/update/onOff")
    public ResponseUtils.Result onOff(@Valid @RequestBody OnOffBody body) {
        int n = articleInfoMapper.updateOnOff(body.getOnOff(), body.articleId);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.result("更新失败");
        }
    }

    @Data
    public static class DelBody {
        @NotBlank
        String articleId;
    }

    @PostMapping("/del")
    public ResponseUtils.Result delAd(@Valid @RequestBody DelBody delBody) {
        int n = articleInfoMapper.delete(delBody.articleId);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "删除失败");
        }
    }

    @Data
    public static class AuditBody {
        @NotBlank
        public String articleId;
        @NotBlank
        public String state;
        public String site;
        public String msg;
    }

    @PostMapping("/audit")
    public ResponseUtils.Result audit(@RequestHeader(Consts.HeaderUserName) String userName, @RequestBody @Valid AuditBody body) {
        log.info("audit userName={}, body={}", userName, body);
        User user = userMapper.get(userName);
        if (user.getRoot() == User.CommonUser && !user.getAutoArticle()) {
            return ResponseUtils.error(-1, "no permission");
        }

        ArticleInfo articleInfo = articleInfoMapper.getByArticleId(body.getArticleId());
        int n = 0;
        if (body.getState().equals(ArticleInfo.PASS) && ArticleInfo.AUDITING.equals(articleInfo.getState())) {
            n = articleInfoMapper.auditArticlePass(body.getArticleId(), body.getState(), body.getSite());
        }
        if (body.getState().equals(ArticleInfo.PASS) && ArticleInfo.UPDATE_AUDITING.equals(articleInfo.getState())) {
            if (StringUtils.isNotBlank(articleInfo.getOtherInfo())) {
                Map<String, Object> map = JsonUtils.toMap(articleInfo.getOtherInfo());
                log.debug("audit map={}", map);
                if (map.get("newVersionInfo") != null) {
                    Map<String, Object> newDataMap = JsonUtils.toMap((String) map.get("newVersionInfo"));
                    articleInfo.setArticleId(body.getArticleId());
                    articleInfo.setTitle(newDataMap.get("title") != null ? (String) newDataMap.get("title") : articleInfo.getTitle());
                    articleInfo.setImgUrl(newDataMap.get("imgUrl") != null ? (String) newDataMap.get("imgUrl") : articleInfo.getImgUrl());
                    articleInfo.setTextList(newDataMap.get("textList") != null ? (String) newDataMap.get("textList") : articleInfo.getTextList());
                    articleInfo.setTerms(newDataMap.get("terms") != null ? (String) newDataMap.get("terms") : articleInfo.getTerms());
                    articleInfo.setCategory(newDataMap.get("category") != null ? (String) newDataMap.get("category") : articleInfo.getCategory());
                    articleInfo.setLang(newDataMap.get("lang") != null ? (String) newDataMap.get("lang") : articleInfo.getLang());
                    articleInfo.setOnline(1);
                    articleInfo.setState(ArticleInfo.PASS);
                    articleInfo.setInsideShow(newDataMap.get("insideShow") != null ? (Integer) newDataMap.get("insideShow") : articleInfo.getInsideShow());
                    articleInfo.setFirstPart(newDataMap.get("firstP") != null ? (String) newDataMap.get("firstP") : articleInfo.getFirstPart());
                    log.debug("audit articleInfo={}", articleInfo);
                    n = articleInfoMapper.updateArticleInfo(articleInfo);
                }
            }
        }
        if (body.getState().equals(ArticleInfo.REJECT) && ArticleInfo.AUDITING.equals(articleInfo.getState())) {
            String otherInfo = ArticleInfo.getOtherInfoAfterAddInfo("msg", body.getMsg(), articleInfo.getOtherInfo());
            n = articleInfoMapper.auditArticleReject(body.getArticleId(), body.getState(), otherInfo);
        }
        if (body.getState().equals(ArticleInfo.REJECT) && ArticleInfo.UPDATE_AUDITING.equals(articleInfo.getState())) {
            String otherInfo = ArticleInfo.getOtherInfoAfterAddInfo("msg", body.getMsg(), articleInfo.getOtherInfo());
            n = articleInfoMapper.auditArticleReject(body.getArticleId(), ArticleInfo.UPDATE_REJECT, otherInfo);
        }
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    @Data
    public static class ArchiveBody {
        @NotBlank
        public String articleId;
    }

    @PostMapping("/archive")
    public ResponseUtils.Result archive(@RequestBody @Valid ArchiveBody body) {
        int n = articleInfoMapper.archive(ArticleInfo.ARCHIVE, body.getArticleId());
        List<AdResource> list = adResourceMapper.getByArticleId(body.getArticleId());
        list.forEach(e -> adResourceMapper.archive(AdResource.ARCHIVE, e.getAdResourceId()));
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    @GetMapping("/search/info")
    public ResponseUtils.Result searchArticleId(@RequestParam String articleId) {
        List<ArticleInfo> list = articleInfoMapper.searchByArticleId(articleId);
        List<Map> resultList = new ArrayList<>();
        for (ArticleInfo info : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("articleId", info.getArticleId());
            map.put("title", info.getTitle());
            resultList.add(map);
        }
        return ResponseUtils.result(resultList);
    }

    @NoAuth
    @GetMapping("/keyword/gaData/list")
    public ResponseUtils.Result getGaData(@RequestParam String articleId,
            @RequestHeader(value = Consts.HeaderUserName, required = false) String userName) {
        if (environment.acceptsProfiles(Profiles.of("local-cms"))) {
            User user = userMapper.get(userName);
            ArticleInfo article = articleInfoMapper.getByArticleId(articleId);
            if (article == null || user == null || (user.getRoot() != User.RootUser
                    && !Objects.equals(user.getPartner(), article.getPartner()))) {
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在或无权查看");
            }
        }
        List<KeywordReportData> list = keywordReportDateMapper.getCountDataList(articleId, DateUtils.getFormatDayFromToday(-15));
        Set<String> pdSet = list.stream().map(e -> e.getPd()).collect(Collectors.toCollection(LinkedHashSet::new));

        List<Map> resultList = new ArrayList<>();
        for (String pd : pdSet) {
            Map<String, Object> resultMap = new HashMap<>();
            List<KeywordReportData> pdItemList = list.stream().filter(e -> e.getPd().equals(pd)).collect(Collectors.toList());
            int dayCountTotal = pdItemList.stream().mapToInt(KeywordReportData::getCount).sum();
            pdItemList.sort(Comparator.comparing(KeywordReportData::getCount).reversed());
            List<Map> dataList = new ArrayList<>();
            for (KeywordReportData info : pdItemList) {
                Map<String, Object> mapItem = new HashMap<>();
                mapItem.put("keyword", info.getKeyword());
                mapItem.put("count", info.getCount());
                mapItem.put("percent", dayCountTotal != 0 ? AppUtils.doubleToDouble2(info.getCount() / (double)dayCountTotal) : 0);
                dataList.add(mapItem);
            }
            resultMap.put("date", pd);
            resultMap.put("gaDataList", dataList);
            resultList.add(resultMap);
        }
        return ResponseUtils.result(resultList);
    }

    @Data
    public static class CloudflareCache {
        @NotBlank
        public String site;
        public List<String> data;
        public String type;
    }

    @NoAuth
    @PostMapping("/cache/clear")
    public ResponseUtils.Result clearCache(@RequestBody @Valid CloudflareCache body,
                                           @RequestHeader(value = Consts.HeaderUserName, required = false) String userName,
                                           @RequestHeader(value = "Token", required = false) String token) {
        User user = userMapper.get(userName);
        if (user == null &&
                (cacheKey.isBlank() || !cacheKey.equals(token))) {
            return ResponseUtils.error(-1, "权限验证失败");
        }
        log.info("clearCache body={}", body);
        if (cloudflareKey.isBlank()) return ResponseUtils.error(-1, "缓存连接器未配置");
        String zoneId = (String) JsonUtils.toMap(zoneMap).get(body.getSite());
        if (zoneId == null) {
            return ResponseUtils.error(-1, "未找到对应的ZoneID");
        }
        if (!body.getType().equals("files") && !body.getType().equals("prefixes") &&
                !body.getType().equals("tags") && !body.getType().equals("hosts")) {
            return ResponseUtils.error(-1, "无该清除方式");
        }
        String apiUrl = "https://api.cloudflare.com/client/v4/zones/" + zoneId + "/purge_cache";
        Map<String, String> headerMap = new HashMap<>();
        if (!cloudflareEmail.isBlank()) {
            headerMap.put("X-Auth-Email", cloudflareEmail);
        }
        headerMap.put("Authorization", "Bearer " + cloudflareKey);
        String requestBody = buildRequestBodyAsString(body.getType(), body.getData());
        String response = httpService.post(headerMap, requestBody, apiUrl);
        Map<String, Object> map = JsonUtils.toMap(response);
        log.info("clearCache response={}", response);
        if (map.get("success") != null && (Boolean) map.get("success")) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "clear fail");
        }
    }
    private String buildRequestBodyAsString(String type, List<String> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"").append(type).append("\": [");
        for (int i = 0; i < data.size(); i++) {
            sb.append("\"").append(data.get(i)).append("\"");
            if (i < data.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]}");
        return sb.toString();
    }
}
