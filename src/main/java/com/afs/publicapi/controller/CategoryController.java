package com.afs.publicapi.controller;

import com.afs.publicapi.controller.filter.NoAuth;
import com.afs.publicapi.dao.ArticleInfoMapper;
import com.afs.publicapi.dao.CategoryMapper;
import com.afs.publicapi.dao.model.ArticleInfo;
import com.afs.publicapi.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    @Autowired
    CategoryMapper categoryMapper;
    @Autowired
    ArticleInfoMapper articleInfoMapper;

    @NoAuth
    @GetMapping("/list")
    public ResponseUtils.Result getList(@RequestHeader(value = "Current-Site", required = false) String site) {
        if (StringUtils.isBlank(site)) {
            site = "niceranker";
        }
        Set<String> list = new LinkedHashSet<>();
        categoryMapper.getBySite(site).forEach(category -> list.add(category.getValue()));
        for (String category : articleInfoMapper.getByArticleCategory(site, ArticleInfoMapper.INSIDE_SHOW, ArticleInfo.PASS)) {
            if (category != null) list.add(category.split(":", 2)[0]);
        }
        log.debug("getList site={}", site);
        return ResponseUtils.result(list);
    }
}
