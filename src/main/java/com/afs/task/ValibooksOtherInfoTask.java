package com.afs.task;

import com.afs.dao.ArticleInfoMapper;
import com.afs.dao.AuthorCategoryMapper;
import com.afs.dao.model.ArticleInfo;
import com.afs.dao.model.AuthorCategoryInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ValibooksOtherInfoTask {
    @Autowired
    ArticleInfoMapper articleInfoMapper;
    @Autowired
    AuthorCategoryMapper authorCategoryMapper;

//    @Async
//    @Scheduled(fixedDelay = 27 * 60 * 60 * 1000, initialDelay = 60 * 60 * 1000)
    void addValibooksOtherInfo() {
        List<ArticleInfo> list = articleInfoMapper.getBySite("valibooks");
        for (ArticleInfo info : list) {
            Map<String, Object> map = new HashMap<>();
            int randomNumber = 1000 + new Random().nextInt(9001);
            map.put("views", randomNumber);
            map.put("ptime", randomTime());
            AuthorCategoryInfo categoryInfo = authorCategoryMapper.getByCategory(info.getCategory());
//            log.info("addValibooksOtherInfo-1 cate={}, categoryInfo={}", info.getCategory(), categoryInfo);
            map.put("author", categoryInfo == null ? "" : categoryInfo.getAuthor());
//            log.info("addValibooksOtherInfo0 map={}", map);
//            log.info("addValibooksOtherInfo1 otherInfo={}", info.getOtherInfo());
            String otherInfo = ArticleInfo.getOtherInfoAfterAddInfo(map, info.getOtherInfo());
//            log.info("addValibooksOtherInfo2 otherInfo={}", otherInfo);
            articleInfoMapper.updateOtherInfo(otherInfo, info.getArticleId());
        }
    }

    private long randomTime() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        long currentTimestamp = calendar.getTimeInMillis() / 1000;
        calendar.add(Calendar.MONTH, -3);
        long threeMonthsAgoTimestamp = calendar.getTimeInMillis() / 1000;
        long randomTimestamp = threeMonthsAgoTimestamp + (long) (new Random().nextDouble() * (currentTimestamp - threeMonthsAgoTimestamp));
        return randomTimestamp;
    }
}
