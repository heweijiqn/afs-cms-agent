package com.afs.publicapi.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AppInfo {
    public static final int StatusDeleted = 0;
    public static final int StatusEnabled = 1;
    public static final int isOnAppStore = 1;
    public static final int isNotOnAppStore = 0;
    public Long id;
    public String appId;
    public String name;
    public String os;
    public float ratio;
    public String appSecret;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
    public Date utime;
}
