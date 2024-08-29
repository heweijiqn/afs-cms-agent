package com.afs.publicapi.service;

import com.afs.publicapi.dao.ArticleInfoMapper;
import com.afs.publicapi.dao.model.ArticleInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@DependsOnDatabaseInitialization
public class ArticleInfoService {

    @Autowired
    ArticleInfoMapper articleInfoMapper;

    public List<ArticleInfo> getRandom(int size) {
        List<String> candidates = new ArrayList<>(articleInfoMapper.getAllArticleIdByState(ArticleInfo.PASS));
        if (size <= 0 || candidates.isEmpty()) return Collections.emptyList();
        Collections.shuffle(candidates);
        return articleInfoMapper.getListByArticleId(candidates.subList(0, Math.min(size, candidates.size())));
    }
}
