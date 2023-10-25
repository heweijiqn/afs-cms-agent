package com.afs.dao;

import com.afs.dao.model.ConfigItem;
import com.afs.dao.model.Fd;
import com.afs.dao.model.LangInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FdMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`feedback`";
    String Columns = "`id`, `site`, `rate`, `locale`, `value`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table)
    List<Fd> getAll();

    @Insert(" insert into " + Table + " (`site`, `value`) values (#{site}, #{value})")
    int add(Fd fd);

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
    List<Fd> getList(@Param("site") String site, @Param("sites") List<String> sites, @Param("offset") int offset, @Param("size") int size);

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
