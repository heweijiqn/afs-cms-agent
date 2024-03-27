package com.afs.task;

import com.afs.dao.ArticleInfoMapper;
import com.afs.dao.model.ArticleInfo;
import com.afs.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AddDataTask {

    @Autowired
    ArticleInfoMapper articleInfoMapper;

    @Data
    static class ArticleInfoTemp {
        public Long id;
        public String articleId;
        String title;
        String imgUrl;
        String terms;
        String category;
        String strategy;
        String site;
        String firstP;
        List<Object> textList;
        String lang;
        Integer online;
        Integer insideShow;
        String otherInfo;
        public int status;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date utime;
    }

//    @Async
//    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000)
    void exeTask() {
        try {
            InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("data/article-data.json");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            bufferedReader.close();
            inputStream.close();

            List<ArticleInfoTemp> list = JsonUtils.toList(stringBuilder.toString(), ArticleInfoTemp.class);
            for (ArticleInfoTemp articleInfoTemp : list) {
                ArticleInfo articleInfo = toArticleInfo(articleInfoTemp);
                articleInfoMapper.add(articleInfo);
            }
        } catch (Throwable throwable) {
            log.error("ad data task error", throwable);
        }
    }

    private ArticleInfo toArticleInfo(ArticleInfoTemp articleInfoTemp) {
        ArticleInfo articleInfo = new ArticleInfo();
        String articleId = "article_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        articleInfo.setArticleId(articleId);
        articleInfo.setTitle(articleInfoTemp.title);
        articleInfo.setTextList(JsonUtils.toJsonString(articleInfoTemp.textList));
        articleInfo.setImgUrl(articleInfoTemp.imgUrl);
        articleInfo.setTerms(articleInfoTemp.terms);
        articleInfo.setCategory(articleInfoTemp.category);
        articleInfo.setStrategy("");
        articleInfo.setSite("niceranker");
        articleInfo.setInsideShow(articleInfoTemp.insideShow);
        articleInfo.setFirstPart(articleInfoTemp.firstP);
        articleInfo.setOnline(articleInfoTemp.online);
        articleInfo.setLang(articleInfoTemp.lang);
        articleInfo.setOtherInfo("");
        articleInfo.setStatus(ArticleInfo.StatusEnabled);
        return articleInfo;
    }
}
