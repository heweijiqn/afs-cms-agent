package com.afs.dao;

import com.afs.dao.model.AdText;
import com.afs.dao.model.Fd;
import com.afs.dao.model.Material;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface AdTextMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ad_text`";
    String Columns = "`id`, `name`, `ad_text`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table)
    List<AdText> getAll();

    @Insert(" insert into " + Table + " (`name`, `ad_text`) values (#{name}, #{adText})")
    int add(AdText adText);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            "   <if test=\"name != null and name != ''\">" +
            "     and name like concat('%', #{name}, '%')" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<AdText> getList(@Param("name") String name, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(*) from " + Table +
            "<where>" +
            "   <if test=\"name != null and name != ''\">" +
            "     name like concat('%', #{name}, '%')" +
            "   </if>" +
            "</where>" +
            "</script>")
    int count(@Param("name") String name);

    @Update("<script>" +
            " update " + Table + " " +
            " <set>" +
            "   <if test=\"name != null\">" +
            "     name = #{name}," +
            "   </if>" +
            "   <if test=\"adText != null\">" +
            "     ad_text = #{adText}," +
            "   </if>" +
            " </set>" +
            " where id = #{id}" +
            "</script>")
    int update(AdText adText);

    @Delete("delete from " + Table + " where `id` = #{id}")
    int del(@Param("id") Long id);
}