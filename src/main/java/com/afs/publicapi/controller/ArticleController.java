package com.afs.publicapi.controller;

import com.afs.publicapi.controller.filter.NoAuth;
import com.afs.publicapi.dao.ArticleInfoMapper;
import com.afs.publicapi.dao.ConfigMapper;
import com.afs.publicapi.dao.model.ArticleInfo;
import com.afs.publicapi.dao.model.ConfigItem;
import com.afs.publicapi.utils.AppUtils;
import com.afs.publicapi.utils.JsonUtils;
import com.afs.publicapi.utils.ResponseUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jayway.jsonpath.internal.function.numeric.Min;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequestMapping("/api/article")
public class ArticleController {

    @Autowired
    ArticleInfoMapper articleInfoMapper;
    @Autowired
    ConfigMapper configMapper;

    @NoAuth
    @GetMapping("/home/list")
    public ResponseUtils.Result getHomeList(@RequestHeader(value = "Current-Site") String site) {
        ConfigItem configItem = configMapper.getConfig(site, "homeList");
        if (configItem == null) {
            return ResponseUtils.result(new ArrayList<>());
        }
        String configJson = configItem.getValue();
        List<Map> list = JsonUtils.toList(configJson, Map.class);

        for (Map map : list) {
            List<ArticleInfo> articleInfoList = articleInfoMapper.getListByArticleId((List<String>) map.get("list"));
            List<ArticleInfoVO> articleInfoVOList = articleInfoList.stream().map(e -> toArticleInfoHomeVO(e)).collect(Collectors.toList());
            map.put("list", articleInfoVOList);
        }

        return ResponseUtils.result(list);
    }

    private ArticleInfoVO toArticleInfoHomeVO(ArticleInfo articleInfo) {
        ArticleInfoVO articleInfoVO = new ArticleInfoVO();
        articleInfoVO.setArticleId(articleInfo.getArticleId());
        articleInfoVO.setTitle(articleInfo.getTitle());
        articleInfoVO.setTerms(articleInfo.getTerms());
        articleInfoVO.setCategory(articleInfo.getCategory());
        articleInfoVO.setInsideShow(articleInfo.getInsideShow());
        articleInfoVO.setLang(articleInfo.getLang());
        articleInfoVO.setImgUrl(articleInfo.getImgUrl());
//        articleInfoVO.setTextList(articleInfo.getTextList());
        articleInfoVO.setSite(articleInfo.getSite());
//        articleInfoVO.setFirstP(articleInfo.getFirstPart());
        articleInfoVO.setCtime(articleInfo.getCtime());
        articleInfoVO.setUtime(articleInfo.getUtime());
        return articleInfoVO;
    }

    private ArticleInfoVO toArticleInfoVO(ArticleInfo articleInfo) {
        ArticleInfoVO articleInfoVO = new ArticleInfoVO();
        articleInfoVO.setArticleId(articleInfo.getArticleId());
        articleInfoVO.setTitle(articleInfo.getTitle());
        articleInfoVO.setTerms(articleInfo.getTerms());
        articleInfoVO.setCategory(articleInfo.getCategory());
        articleInfoVO.setInsideShow(articleInfo.getInsideShow());
        articleInfoVO.setLang(articleInfo.getLang());
        articleInfoVO.setImgUrl(articleInfo.getImgUrl());
//        articleInfoVO.setTextList(articleInfo.getTextList());
        articleInfoVO.setSite(articleInfo.getSite());
        articleInfoVO.setFirstP(articleInfo.getFirstPart());
        articleInfoVO.setCtime(articleInfo.getCtime());
        articleInfoVO.setUtime(articleInfo.getUtime());
        return articleInfoVO;
    }

    @Data
    public static class ArticleInfoVO {
        public String articleId;
        public String title;
        public String terms;
        public String category;
        public String site;
        public String imgUrl;
        public String textList;
        public String lang;
        public String firstP;
        public Boolean websiteShow;
        public Integer insideShow;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
        public Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
        public Date utime;
    }

    @NoAuth
    @GetMapping("/category/list")
    public ResponseUtils.Result getCategoryList(@RequestHeader(value = "Current-Site") String site, @RequestParam String category) {
        List<ArticleInfo> articleInfoList = articleInfoMapper.getByCategoryAndInsideShow(site, category, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS);
        List<ArticleInfoVO> articleInfoVOList = articleInfoList.subList(0, Math.min(articleInfoList.size(), 50)).stream().map(this::toArticleInfoVO).collect(Collectors.toList());
        return ResponseUtils.result(articleInfoVOList);
    }

    @NoAuth
    @GetMapping("/ccc/search/list")
    public ResponseUtils.Result getCccSearchList(@RequestHeader(value = "Current-Site") String site, @RequestParam String searchKeyword) {
        Map<String, Object> map = new HashMap<>();
        log.info("site={}, searchKeyword={}", site, searchKeyword);
        List<ArticleInfo> articleInfoList = articleInfoMapper.getByKeywordFromTitle(site, searchKeyword, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS, 20);
        if (articleInfoList.size() < 20) {
            articleInfoList.addAll(articleInfoMapper.getByKeywordFromFirstP(site, searchKeyword, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS, 20));
            articleInfoList = new ArrayList<>(new LinkedHashSet<>(articleInfoList));
        }
//        if (articleInfoList.size() < 20) {
//            articleInfoList.addAll(articleInfoMapper.getByKeywordFromText(site, searchKeyword, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS, 20));
//            articleInfoList = new ArrayList<>(new LinkedHashSet<>(articleInfoList));
//        }
        List<ArticleInfoVO> articleInfoVOList = articleInfoList.subList(0, Math.min(articleInfoList.size(), 20)).stream().map(this::toArticleInfoVO).collect(Collectors.toList());
        map.put("list", articleInfoVOList);

        if (CollectionUtils.isEmpty(articleInfoList)) {
            List<ArticleInfo> articleInfoRecList = articleInfoMapper.getByIdDesc(site, 10, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS);
            List<ArticleInfoVO> articleInfoVORecList = articleInfoRecList.stream().map(this::toArticleInfoVO).collect(Collectors.toList());
            map.put("recList", articleInfoVORecList);
        } else {
            map.put("recList", new ArrayList<>());
        }
        return ResponseUtils.result(map);
    }

    @NoAuth
    @GetMapping("/search/list")
    public ResponseUtils.Result getSearchList(@RequestHeader(value = "Current-Site") String site, @RequestParam String searchKeyword) {
        List<ArticleInfo> articleInfoList = articleInfoMapper.getByKeywordFromTitle(site, searchKeyword, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS, 20);
        if (articleInfoList.size() < 20) {
            articleInfoList.addAll(articleInfoMapper.getByKeywordFromFirstP(site, searchKeyword, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS, 20));
            articleInfoList = new ArrayList<>(new LinkedHashSet<>(articleInfoList));
        }
//        if (articleInfoList.size() < 20) {
//            articleInfoList.addAll(articleInfoMapper.getByKeywordFromText(site, searchKeyword, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS, 20));
//            articleInfoList = new ArrayList<>(new LinkedHashSet<>(articleInfoList));
//        }
        if (articleInfoList.size() < 20) {
            articleInfoList.addAll(articleInfoMapper.getByIdDesc(site, 20, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS));
            articleInfoList = new ArrayList<>(new LinkedHashSet<>(articleInfoList));
        }

        List<ArticleInfoVO> articleInfoVOList = articleInfoList.subList(0, Math.min(articleInfoList.size(), 20)).stream().map(this::toArticleInfoVO).collect(Collectors.toList());
        return ResponseUtils.result(articleInfoVOList);
    }

    @NoAuth
    @GetMapping("/detail")
    public ResponseUtils.Result getArticleDetail(@RequestHeader(required = false, value = "User-Agent") String userAgent,
                                                 @RequestHeader(required = false, value = "referer") String referer,
                                                 @RequestHeader(value = "Current-Site") String site, @RequestParam String articleId) {
        log.info("getArticleDetail articleId={}, site={}, userAgent={}, referer={}", articleId, site, userAgent, referer);

        articleId = AppUtils.getArticleIdFixed(articleId);

        ArticleInfo articleInfo = articleInfoMapper.getByArticleId(articleId);
        if (articleInfo == null || "ARCHIVE".equals(articleInfo.getState())) {
            if (articleInfo == null) {
                log.error("articleId={} not found", articleId);
            }
            Map<String, Object> map = new HashMap<>();
            map.put("websiteShow", false);
            return ResponseUtils.result(map);
        }
        ArticleInfoVO articleInfoVO = toArticleInfoVOForDetail(articleInfo);
        return ResponseUtils.result(articleInfoVO);
    }

    private ArticleInfoVO toArticleInfoVOForDetail(ArticleInfo articleInfo) {
        ArticleInfoVO articleInfoVO = new ArticleInfoVO();
        articleInfoVO.setArticleId(articleInfo.getArticleId());
        articleInfoVO.setTitle(articleInfo.getTitle());
        articleInfoVO.setTerms(articleInfo.getTerms());
        articleInfoVO.setCategory(articleInfo.getCategory());
        articleInfoVO.setInsideShow(articleInfo.getInsideShow());
        articleInfoVO.setLang(articleInfo.getLang());
        articleInfoVO.setImgUrl(articleInfo.getImgUrl());
        articleInfoVO.setTextList(articleInfo.getTextList());
        articleInfoVO.setFirstP(articleInfo.getFirstPart());
        articleInfoVO.setWebsiteShow(true);
        articleInfoVO.setCtime(articleInfo.getCtime());
        articleInfoVO.setUtime(articleInfo.getUtime());
        return articleInfoVO;
    }

}
