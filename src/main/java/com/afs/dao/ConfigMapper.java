package com.afs.dao;

import com.afs.dao.model.ArticleInfo;
import com.afs.dao.model.CategoryInfo;
import com.afs.dao.model.ConfigItem;
import com.afs.dao.model.LangInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ConfigMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`config_item`";
    String Columns = "`id`, `name`, `value`, `desc`, `site`, `ctime`, `utime`";

    @Insert(" insert into " + Table + " (`name`, `value`, `desc`, `site`) values (#{name}, #{value}, #{desc}, #{site})")
    int add(ConfigItem configItem);

    @Update("update " + Table + " set `value` = #{value}, `desc`=#{desc} where `name` = #{name} and site = #{site}")
    int update(ConfigItem configItem);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " site in <foreach collection='sites' item='registeredSite' open='(' separator=',' close=')'>#{registeredSite}</foreach>" +
            "   <if test=\"site != null and site != ''\">" +
            "     and site = #{site}" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<ConfigItem> getList(@Param("site") String site, @Param("sites") List<String> sites, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(*) from " + Table +
            "<where>" +
            " site in <foreach collection='sites' item='registeredSite' open='(' separator=',' close=')'>#{registeredSite}</foreach>" +
            "   <if test=\"site != null and site != ''\">" +
            "     and site = #{site}" +
            "   </if>" +
            "</where>" +
            "</script>")
    int count(@Param("site") String site, @Param("sites") List<String> sites);
}
