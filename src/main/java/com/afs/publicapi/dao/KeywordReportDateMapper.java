package com.afs.publicapi.dao;

import com.afs.publicapi.dao.model.KeywordReportData;
import org.apache.ibatis.annotations.*;

public interface KeywordReportDateMapper {

    String Table = "`keyword_report_data`";

    String Columns = "`id`,`pd`,`article_id`,`keyword`,`status`,`ctime`,`utime`";

    @Insert("insert into " + Table + " (`pd`, `article_id`, `keyword`) values (#{pd}, #{articleId}, #{keyword})")
    int add(KeywordReportData data);
}
