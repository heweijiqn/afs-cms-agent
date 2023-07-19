package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AdResourceRuleLog {
    public static final int StatusDeleted = 0;
    public static final int StatusEnabled = 1;

    public Long id;
    public String adResourceId;
    public String campaignId;
    public String campaignName;
    public Long ruleId;
    public Integer dayNum;
    public String ruleName;
    public String action;
    public String adGroupId;
    public String adGroupName;
    public String condition;
    public String httpBody;
    public String httpResponse;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
