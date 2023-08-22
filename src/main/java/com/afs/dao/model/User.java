package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.ToString;

import java.util.Date;

@Data
public class User {
    public static final int StatusValid = 1;
    public static final int StatusInvalid = 0;
    public static final int RootUser = 1;
    public static final int CommonUser = 0;
    public static final String DefaultPassword = "123456";
    public Long id;
    public String name = "";
    @ToString.Exclude
    public String password = "";
    public String partner;
    public Boolean autoArticle;
    public Boolean autoCampaign;
    public Boolean showMaterial;
    public String siteList;
    public String adAccountList;
    public String desc;
    public String displayName;
    public String email;
    public String phone;
    public String department;
    public String jobTitle;
    public String timezone;
    public String avatarColor;
    public int root = RootUser;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date lastLoginAt;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
