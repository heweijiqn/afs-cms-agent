package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class TiktokAdGroupInfo {//todo: unique channlel_id in adresource

    public static final String ADGROUP_STATUS_DELIVERY_OK = "ADGROUP_STATUS_DELIVERY_OK";

    Long id;
    String adResourceId;
    String channelId;
    String adGroupId;
    String adgroupName;
    String campaignId;
    String campaignName;
    String advertiser;
    String adGroupStatus;
    Double budget;
    Double conversion;
    Double grossRevenue;
    Double adSpend;
    Double profit;
    Double roi;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
