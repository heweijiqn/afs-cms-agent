package com.afs.dao;

import com.afs.dao.model.ConfigItem;
import com.afs.dao.model.LangInfo;
import com.afs.dao.model.ReportData;
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

    @Insert(" insert into " + Table + " (`channel`, `value`, `desc`, `site`) values (#{channel}, #{value}, #{desc}, #{site})")
    int add(ReportData reportData);

    @Update("update " + Table + " set `channel` = #{channel}, `value` = #{value}, `desc`=#{desc} where `channel` = #{oldChannel} and site = #{site}")
    int update(ReportData reportData);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " site in <foreach collection='sites' item='registeredSite' open='(' separator=',' close=')'>#{registeredSite}</foreach>" +
            "   <if test=\"site != null and site != ''\">" +
            "     and site = #{site}" +
            "   </if>" +
            "   <if test=\"channel != null and channel != ''\">" +
            "     and channel = #{channel}" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<ReportData> getList(@Param("site") String site, @Param("channel") String channel, @Param("sites") List<String> sites, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(*) from " + Table +
            "<where>" +
            " site in <foreach collection='sites' item='registeredSite' open='(' separator=',' close=')'>#{registeredSite}</foreach>" +
            "   <if test=\"site != null and site != ''\">" +
            "     and site = #{site}" +
            "   </if>" +
            "   <if test=\"channel != null and channel != ''\">" +
            "     and channel = #{channel}" +
            "   </if>" +
            "</where>" +
            "</script>")
    int count(@Param("site") String site, @Param("channel") String channel, @Param("sites") List<String> sites);
}
