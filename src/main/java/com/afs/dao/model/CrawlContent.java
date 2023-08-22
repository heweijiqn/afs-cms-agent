package com.afs.dao.model;

import lombok.Data;

import java.util.Date;

@Data
public class CrawlContent {
    public static final int StatusInit = 0;
    public static final int StatusCrawlDetailDone = 1;
    public static final int StatusCrawlTransDone = 2;

    public String fid;
    public String content;
    public String source;
    public Integer online;
    public Integer status;
    public String category;
    public Date uTime;
    public Date cTime;
}
