package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class LangInfo {
    Long id;
    String value;
    String label;
    String site;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}
