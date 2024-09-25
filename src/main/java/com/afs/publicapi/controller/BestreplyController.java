package com.afs.publicapi.controller;

import com.afs.publicapi.controller.filter.NoAuth;
import com.afs.publicapi.dao.ArticleInfoMapper;
import com.afs.publicapi.dao.ConfigMapper;
import com.afs.publicapi.dao.model.ArticleInfo;
import com.afs.publicapi.dao.model.ConfigItem;
import com.afs.publicapi.service.ArticleInfoService;
import com.afs.publicapi.utils.AppUtils;
import com.afs.publicapi.utils.DateUtils;
import com.afs.publicapi.utils.JsonUtils;
import com.afs.publicapi.utils.ResponseUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequestMapping("/api/bestreply")
public class BestreplyController {

    @Autowired
    ArticleInfoMapper articleInfoMapper;
    @Autowired
    ConfigMapper configMapper;
    @Autowired
    ArticleInfoService articleInfoService;

    @NoAuth
    @GetMapping("/hot/words/list")
    public ResponseUtils.Result getHotWordList(@RequestHeader(value = "Current-Site", required = false) String site) {
        ConfigItem configItem = configMapper.getConfig(site, "homeHotWords");
        if (configItem == null) return ResponseUtils.result(List.of());
        List<String> list = JsonUtils.toList(configItem.getValue(), String.class);
        return ResponseUtils.result(list);
    }

    @NoAuth
    @GetMapping("/category/list")
    public ResponseUtils.Result getCategoryList(@RequestHeader(value = "Current-Site", required = false) String site) {
        ConfigItem configItem = configMapper.getConfig(site, "homeCategoryList");
        if (configItem == null) return ResponseUtils.result(List.of());
        List<String> list = JsonUtils.toList(configItem.getValue(), String.class);
        return ResponseUtils.result(list);
    }

    @Data
    public static class ArticleInfoVO {
        public String author;
        public String articleId;
        public String title;
        public String terms;
        public String category;
        public String site;
        public String imgUrl;
        public String textList;
        public String lang;
        public String firstP;
        public Integer insideShow;
        public String ptime;
        public Integer views;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
        public Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
        public Date utime;
    }

    @NoAuth
    @GetMapping("/category/article/list")
    public ResponseUtils.Result getCategoryList(@RequestHeader(value = "Current-Site") String site, @RequestParam String category) {
        List<ArticleInfo> articleInfoList = articleInfoMapper.getByCategoryAndInsideShow(site, category, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS);
        List<ArticleInfoVO> articleInfoVOList = articleInfoList.subList(0, Math.min(articleInfoList.size(), 50)).stream().map(e -> toArticleInfoVO(e)).collect(Collectors.toList());
        return ResponseUtils.result(articleInfoVOList);
    }

    @NoAuth
    @GetMapping("/related/article/list")
    public ResponseUtils.Result getCategoryArticleList(@RequestHeader(value = "Current-Site") String site, @RequestParam String articleId) {
        ArticleInfo articleInfo = articleInfoMapper.getByArticleId(articleId);
        if (articleInfo == null || articleInfo.getCategory() == null) return ResponseUtils.result(List.of());
        List<ArticleInfo> articleInfoList = articleInfoMapper.getByCategoryAndInsideShow(site, getCategoryPre(articleInfo.getCategory()), ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS);
        if (CollectionUtils.isEmpty(articleInfoList)) {
            articleInfoList = articleInfoService.getRandom(10);
        }
        List<ArticleInfoVO> articleInfoVOList = articleInfoList.subList(0, Math.min(articleInfoList.size(), 50)).stream().map(e -> toArticleInfoVO(e)).collect(Collectors.toList());
        return ResponseUtils.result(articleInfoVOList);
    }

    @NoAuth
    @GetMapping("/search/article/list")
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

    private String getCategoryPre(String category) {
        if (StringUtils.isBlank(category)) return "";
        String[] array = category.split(":");
        return array[0];
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
        articleInfoVO.setSite(articleInfo.getSite());
        articleInfoVO.setFirstP(articleInfo.getFirstPart());
        articleInfoVO.setCtime(articleInfo.getCtime());
        articleInfoVO.setUtime(articleInfo.getUtime());

        Object views = ArticleInfo.getInfoFromOtherInfo("views", articleInfo.getOtherInfo());
        articleInfoVO.setViews(views != null ? (Integer)views : 0);
        Object ptime = ArticleInfo.getInfoFromOtherInfo("ptime", articleInfo.getOtherInfo());
        articleInfoVO.setPtime(ptime != null ? DateUtils.formatDate(((Integer) ptime) * 1000L) : "2024-09-10 13:00:30");
        Object author = ArticleInfo.getInfoFromOtherInfo("author", articleInfo.getOtherInfo());
        articleInfoVO.setAuthor(author != null ? (String) author : "");
        return articleInfoVO;
    }

    @NoAuth
    @GetMapping("/article/detail")
    public ResponseUtils.Result getArticleDetail(@RequestParam String articleId) {
        log.info("getArticleDetail articleId={}", articleId);
        articleId = AppUtils.getArticleIdFixed(articleId);
        ArticleInfo articleInfo = articleInfoMapper.getByArticleId(articleId);

        if (articleInfo == null) return ResponseUtils.error(-1, "Article not found");
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
        articleInfoVO.setCtime(articleInfo.getCtime());
        articleInfoVO.setUtime(articleInfo.getUtime());
        articleInfoVO.setSite(articleInfo.getSite());

        Object views = ArticleInfo.getInfoFromOtherInfo("views", articleInfo.getOtherInfo());
        articleInfoVO.setViews(views != null ? (Integer)views : 0);
        Object ptime = ArticleInfo.getInfoFromOtherInfo("ptime", articleInfo.getOtherInfo());
        articleInfoVO.setPtime(ptime != null ? DateUtils.formatDate(((Integer) ptime) * 1000L) : "2024-09-10 13:00:30");
        Object author = ArticleInfo.getInfoFromOtherInfo("author", articleInfo.getOtherInfo());
        articleInfoVO.setAuthor(author != null ? (String) author : "");
        return articleInfoVO;
    }

    @NoAuth
    @GetMapping("/feed/list")
    public ResponseUtils.Result getFeedList(@RequestHeader(value = "Current-Site") String site) {
        ConfigItem configItem = configMapper.getConfig(site, "feedList");
        if (configItem == null) return ResponseUtils.result(List.of());
        List<String> list = JsonUtils.toList(configItem.getValue(), String.class);
        if (CollectionUtils.isEmpty(list)) return ResponseUtils.result(List.of());
        List<ArticleInfo> articleInfoList = articleInfoMapper.getListByArticleId(list);
        List<ArticleInfoVO> articleInfoVOList = articleInfoList.stream().map(e -> toArticleInfoVO(e)).collect(Collectors.toList());
        return ResponseUtils.result(articleInfoVOList);
    }

    @NoAuth
    @GetMapping("/feed/article/detail")
    public ResponseUtils.Result getFeedArticleDetail(@RequestParam String articleId) {
        log.debug("getArticleDetail articleId={}", articleId);
        ArticleInfo articleInfo = articleInfoMapper.getByArticleId(articleId);
        if (articleInfo == null) return ResponseUtils.error(-1, "Article not found");
        ArticleInfoVO articleInfoVO = toArticleInfoVOForDetail(articleInfo);
        return ResponseUtils.result(articleInfoVO);
    }
}
