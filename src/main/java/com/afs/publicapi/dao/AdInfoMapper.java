package com.afs.publicapi.dao;

import com.afs.publicapi.dao.model.AdInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface AdInfoMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ad_info`";
    String Columns = "`id`, `ad_id`, `app_id`, `name`, `ratio`, `type`, `on_off`, `status`, `ctime`, `utime`";

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"appId != null and appId!=''\">" +
            "     and app_id = #{appId}" +
            "   </if>" +
            "   <if test=\"type != null\">" +
            "     and type = #{type}" +
            "   </if>" +
            "   <if test=\"onOff != null\">" +
            "     and on_off = #{onOff}" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<AdInfo> getListByQuery(@Param("appId") String appId, @Param("type") Integer type, @Param("onOff") Integer onOff,
                         @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"appId != null and appId!=''\">" +
            "     and app_id = #{appId}" +
            "   </if>" +
            "   <if test=\"type != null\">" +
            "     and type = #{type}" +
            "   </if>" +
            "   <if test=\"onOff != null\">" +
            "     and on_off = #{onOff}" +
            "   </if>" +
            "</where>" +
            "</script>")
    int countByQuery(@Param("appId") String appId, @Param("type") Integer type, @Param("onOff") Integer onOff);

    @Insert(" insert into " + Table + "" +
            " (`ad_id`, `app_id`, `name`, `ratio`, `type`, `on_off`)" +
            " values (#{adId}, #{appId}, #{name}, #{ratio}, #{type}, #{onOff}) " +
            " on duplicate key update status = 1")
    int add(AdInfo adInfo);

    @Update("update " + Table + " set `on_off` = #{onOff} where `ad_id` = #{adId}")
    int updateOnOff(@Param("onOff") Integer onOff, @Param("adId") String adId);

    @Update("<script>" +
            " update " + Table + " " +
            " <set>" +
            "   <if test=\"name != null\">" +
            "     name = #{name}," +
            "   </if>" +
            "   <if test=\"type != null\">" +
            "     type = #{type}," +
            "   </if>" +
            "   <if test=\"onOff != null\">" +
            "     on_off = #{onOff}," +
            "   </if>" +
            " </set>" +
            " where ad_id = #{adId}" +
            "</script>")
    int updateAdInfo(AdInfo adInfo);


    @Delete("delete from " + Table + " where `ad_id` = #{adId}")
    int delete(@Param("adId") String adId);


}