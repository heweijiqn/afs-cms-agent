package com.afs.publicapi.controller;

import com.afs.publicapi.controller.filter.NoAuth;
import com.afs.publicapi.dao.KeywordReportDateMapper;
import com.afs.publicapi.dao.model.KeywordReportData;
import com.afs.publicapi.utils.DateUtils;
import com.afs.publicapi.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.Date;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequestMapping("/api/keyword")
public class KeywordReportController {

    @Autowired
    KeywordReportDateMapper keywordReportDateMapper;
    @Autowired
    com.afs.service.LocalApiEventStore localApiEventStore;

    @Data
    public static class AddBody{
        @NotBlank
        String articleId;
        @NotBlank
        String keyword;
    }

    private KeywordReportData toReportData(AddBody body){
        KeywordReportData data = new KeywordReportData();
        data.setPd(DateUtils.getDateStr(new Date()));
        data.setArticleId(body.getArticleId());
        data.setKeyword(body.getKeyword());
        return data;
    }

    @NoAuth
    @PostMapping("/report")
    public ResponseUtils.Result add(@RequestHeader(value = "Current-Site") String site, @RequestBody AddBody body) {
        log.info("add site={}, body={}", site, body);
        KeywordReportData data = toReportData(body);
        keywordReportDateMapper.add(data);
        localApiEventStore.record("keyword", site,
                java.util.Map.of("articleId", body.getArticleId(), "keyword", body.getKeyword()));
        return ResponseUtils.ok();
    }
}
