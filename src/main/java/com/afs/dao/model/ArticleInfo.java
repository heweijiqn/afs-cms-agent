package com.afs.dao.model;

import com.afs.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class ArticleInfo {
    public static final int StatusDeleted = 0;
    public static final int StatusEnabled = 1;
    public static final int TypeIcon = 1;
    public static final int TypeInter = 2;
    public static final int TypeDraw = 3;
    public static final int TypeSplash = 4;
    public static final int Online = 1;
    public static final int Offline = 0;

    public static String AUDITING = "AUDITING";
    public static String PASS = "PASS";
    public static String REJECT = "REJECT";
    public static String ARCHIVE = "ARCHIVE";//归档
    public static String UPDATE_AUDITING = "UPDATE_AUDITING";
    public static String UPDATE_REJECT = "UPDATE_REJECT";

    public Long id;
    public String articleId;
    String title;
    String imgUrl;
    String terms;
    String category;
    String strategy;
    String site;
    String firstPart;
    String textList;
    String lang;
    String partner;
    String state;
    Integer online;
    Integer insideShow;
    String otherInfo;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date onlineTime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;

    public static String getOtherInfoAfterAddInfo(Map<String, Object> map, String otherInfo) {
        if (StringUtils.isNotBlank(otherInfo)) {
            Map<String, Object> mapDb = JsonUtils.toMap(otherInfo);
            mapDb.putAll(map);
            return JsonUtils.toJsonString(mapDb);
        } else {
            return JsonUtils.toJsonString(map);
        }
    }

    public static String getOtherInfoAfterAddInfo(String key, String value, String otherInfo) {
        if (StringUtils.isNotBlank(otherInfo)) {
            Map<String, Object> map = JsonUtils.toMap(otherInfo);
            map.put(key, value);
            return JsonUtils.toJsonString(map);
        } else {
            Map<String, Object> map = new HashMap<>();
            map.put(key, value);
            return JsonUtils.toJsonString(map);
        }
    }

    public static String getInfoFromOtherInfo(String key, String otherInfo) {
        if (StringUtils.isNotBlank(otherInfo)) {
            Map<String, Object> map = JsonUtils.toMap(otherInfo);
            return map.get(key) != null ? (String) map.get(key) : "";
        } else {
            return "";
        }
    }
}
