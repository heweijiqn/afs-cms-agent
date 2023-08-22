package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class GoogleData {
    Long id;
    String date;
    String countryId;
    String countryCode;
    String campaignId;
    String campaignName;
    String landingUrl;
    String channelId;
    String clicks;
    String spend;
    String impressions;
    String conversion;
    String advertiser;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
