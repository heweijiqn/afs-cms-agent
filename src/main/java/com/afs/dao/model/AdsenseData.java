package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AdsenseData {
    public static final int StatusDeleted = 0;
    public static final int StatusEnabled = 1;
    public Long id;
    public String date;
    public String product_code;
    public String country_code;
    public String customChannelId;
    public String adResourceId;
    public String adsense_data;
    public Double cost_per_click;
    public Double impressions;
    public Double ad_requests;
    public Double funnel_impressions;
    public Double clicks;
    public Double funnel_clicks;
    public Double funnel_rpm;
    public Double funnel_requests;
    public Double matched_ad_requests;
    public Double impressions_ctr;
    public Double estimated_earnings;
    public Double individual_ad_impressions;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}