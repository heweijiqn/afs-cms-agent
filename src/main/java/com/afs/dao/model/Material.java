package com.afs.dao.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class Material {
    private Long id;
    private String adMaterialId;
    private String name;
    private String userName;
    private String country;
    private String lang;
    private String type;
    private String dimension;
    private String ratio;
    private String category;
    private String template;
    private String cover;
    private String url;
    private Integer size;
    private Integer duration;
    public int status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date ctime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    public Date utime;
}