package com.afs.publicapi.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AdData {
    public static final int StatusDeleted = 0;
    public static final int StatusEnabled = 1;
    public Long id;
    public String appId;
    public String adId;
    public Long dayTime;
    public String country;
    public Double pv;
    public Double total;
    public Double ecpm;
    public String pageSite;
    public Double pagePv;
    public Double click;
    public Double pageEcpm;
    public String other;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
    public Date utime;
}
