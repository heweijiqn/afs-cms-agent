package com.afs.dao.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AdsenseChannel {
    public static final String INIT = "INIT";
    public static String ARCHIVE = "ARCHIVE";//归档
    public static final String ONLINE = "ONLINE";

    private String channelId;
    private String pubId;
    private String state;
    private Integer status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
