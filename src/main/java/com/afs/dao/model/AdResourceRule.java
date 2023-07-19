package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
public class AdResourceRule {
    public static final int StatusDeleted = 0;
    public static final int StatusEnabled = 1;

    public static String REPEAT = "REPEAT";//重复
    public static String CONT = "CONT";//连续

    public Long id;
    public String name;
    public String type;
    public String actionCycle;
    public Integer actionTime;
    public String rule;
    public String creator;
    public Double budgetChange;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
