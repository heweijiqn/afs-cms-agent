package com.afs.publicapi.dao;

import com.afs.publicapi.dao.model.ReportData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ReportDataMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`report_data_config`";
    String Columns = "`id`, `channel`, `value`, `desc`, `site`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table + " where `channel` = #{channel} and site = #{site}")
    ReportData getByChannel(@Param("site") String site, @Param("channel") String channel);
}