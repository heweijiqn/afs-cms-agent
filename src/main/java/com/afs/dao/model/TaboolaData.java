package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class TaboolaData {
    Long id;
    String date;
    String countryCode;
    String campaignId;
    String campaignName;
    String landingUrl;
    String channelId;
    String clicks;
    String spend;
    String impressions;
    String conversion;
    String accountName;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
