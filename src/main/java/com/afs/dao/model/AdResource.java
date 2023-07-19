package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AdResource {
    public static final int StatusDeleted = 0;
    public static final int StatusEnabled = 1;
    public static String AUDITING = "AUDITING";
    public static String PASS = "PASS";
    public static String REJECT = "REJECT";
    public static String ARCHIVE = "ARCHIVE";//归档

    public static String AD_PLATFORM_SUCCESS = "AD_PLATFORM_SUCCESS";
    public static String AD_PLATFORM_FAIL = "AD_PLATFORM_FAIL";
    public static String AD_PLATFORM_ING = "AD_PLATFORM_ING";

    public Long id;
    public String accountId;
    public String adResourceId;
    public String title;
    public String articleId;
    public String channelId;
    public String am;
    public String campaign;
    public String adPlatform;
    public String landPageUrl;
    public String adDesc;
    public String auditMsg;
    public String mediaList;
    public String partner;
    public String country;
    public String keyword;
    public String adAccount;
    public Long ruleId;
    public String lastActionTime;
    public String startRuleTime;
    public Double adSpend;
    public Double grossRevenue;
    public Double profit;
    public Double roi;
    public String state;
    public String adPlatformInfo;
    public String adPlatformState;
    public String adPlatformMsg;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
