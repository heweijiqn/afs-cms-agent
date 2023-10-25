package com.afs.dao;

import com.afs.dao.model.GoogleBigqueryData;
import com.afs.dao.model.KeywordReportData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface KeywordReportDateMapper {

    String Table = "`keyword_report_data`";

    String Columns = "`id`,`pd`,`article_id`,`keyword`,`status`,`ctime`,`utime`";

    @Insert("insert into " + Table + " (`pd`, `article_id`, `keyword`) values (#{pd}, #{articleId}, #{keyword})")
    int add(KeywordReportData data);

    @Select(" select `pd`,`article_id`,`keyword`, count(*) as `count` from " + Table + " where `article_id` = #{articleId} and `pd` >= #{ts} group by `pd`,`article_id`,`keyword` order by pd desc")
    List<KeywordReportData> getCountDataList(@Param("articleId") String articleId, @Param("ts") String ts);
}
