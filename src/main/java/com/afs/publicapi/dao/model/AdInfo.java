package com.afs.publicapi.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AdInfo {
    public static final int StatusDeleted = 0;
    public static final int StatusEnabled = 1;
    public static final int TypeIcon = 1;
    public static final int TypeInter = 2;
    public static final int On = 1;
    public static final int Off = 0;
    public static String OsAndroid = "Android";
    public static String OsIOS = "IOS";
    public Long id;
    public String adId;
    public String appId;
    public String name;
    public float ratio;
    public int type;
    public int onOff;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
    public Date utime;
}
