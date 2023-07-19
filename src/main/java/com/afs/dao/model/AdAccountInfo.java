package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AdAccountInfo {
    public static final int StatusDeleted = 0;
    public static final int StatusEnabled = 1;
    public Long id;
    public String name;
    public String accountId;
    public String adPlatform;
    public String partner;
    public String userName;
    public String info;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
