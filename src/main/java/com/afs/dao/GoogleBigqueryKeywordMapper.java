package com.afs.dao;

import com.afs.dao.model.AppInfo;
import com.afs.dao.model.Fd;
import com.afs.dao.model.GoogleBigqueryData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface GoogleBigqueryKeywordMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`google_bigquery_keyword_data`";
    String Columns = "`id`, `pd`, `article_id`, `key_word`, `count`, `status`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table)
    List<Fd> getAll();

    @Insert(" insert into " + Table + " (`pd`, `article_id`, `key_word`, `count`) values (#{pd}, #{articleId}, #{keyWord}, #{count}) on duplicate key update count = #{count}")
    int add(GoogleBigqueryData googleBigqueryData);

    @Select(" select " + Columns + " from " + Table + " where `article_id` = #{articleId} and `pd` >= #{ts} order by pd desc")
    List<GoogleBigqueryData> getListByArticleId(@Param("articleId") String articleId, @Param("ts") String ts);
}