package com.afs.dao;

import com.afs.dao.model.AppInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface AppInfoMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`app_info`";
    String Columns = "`id`, `app_id`, `name`, `os`, `ratio`, `app_secret`, `app_store_url`, `is_on_app_store`, `status`, `ctime`, `utime`";

    @Select(" select " + Columns + " from " + Table + " where `name` = #{name}")
    AppInfo get(@Param("pname") String pname, @Param("name") String name);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"appIdList != null and appIdList.size > 0\">" +
            "     and app_id in (<foreach collection='appIdList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<AppInfo> getListByQuery(@Param("appIdList") List<String> appIdList, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " status = 1" +
            "</where>" +
            "</script>")
    int countByQuery();

    @Insert(" insert into " + Table + "" +
            " (`app_id`, `name`, `os`, `ratio`, `app_secret`, `app_store_url`, `is_on_app_store`)" +
            " values (#{appId}, #{name}, #{os}, #{ratio}, #{appSecret}, #{appStoreUrl}, #{isOnAppStore}) " +
            " on duplicate key update status = 1")
    int add(@Param("appId") String appId, @Param("name") String name, @Param("os") String os,
            @Param("ratio") Float ratio, @Param("appSecret") String appSecret,
            @Param("appStoreUrl") String appStoreUrl, @Param("isOnAppStore") Integer isOnAppStore);

    @Update("update " + Table + " set `status` = #{status} where `pname` = #{pname} and `name` = #{name}")
    int updateStatus(@Param("pname") String pname, @Param("name") String name, @Param("status") int status);

    @Select(" select " + Columns + " from " + Table + " where `app_id` = #{appId} and status = 1")
    AppInfo getByAppId(@Param("appId") String appId);

    @Select(" select " + Columns + " from " + Table + " where status = " + VALID)
    List<AppInfo> getAll();
}