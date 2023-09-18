package com.afs.dao;

import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.AppInfo;
import com.afs.dao.model.ArticleInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AdAccountInfoMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ad_account_info`";
    String Columns = "`id`, `name`, `user_name`, `partner`, `account_id`, `ad_platform`, `info`, `status`, `ctime`, `utime`";

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<AdAccountInfo> getListByQuery(@Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " status = 1" +
            "</where>" +
            "</script>")
    int countByQuery();

    @Insert(" insert into " + Table + " (`name`, `user_name`, `partner`, `account_id`, `ad_platform`, `info`) values (#{name}, #{userName}, #{partner}, #{accountId}, #{adPlatform}, #{info}) on duplicate key update status = 1")
    int add(AdAccountInfo adAccountInfo);

    @Update("<script>" +
            " update " + Table + " " +
            " <set>" +
            "   <if test=\"name != null\">" +
            "     name = #{name}," +
            "   </if>" +
            "   <if test=\"accountId != null\">" +
            "     account_id = #{accountId}," +
            "   </if>" +
            "   <if test=\"adPlatform != null\">" +
            "     ad_platform = #{adPlatform}," +
            "   </if>" +
            "   <if test=\"info != null\">" +
            "     info = #{info}," +
            "   </if>" +
            " </set>" +
            " where id = #{id}" +
            "</script>")
    int updateAdAccountInfo(AdAccountInfo info);

    @Update("update " + Table + " set `name` = #{name} where `id` = #{id}")
    int updateName(@Param("id") Long id, @Param("name") String name);

    @Update("update " + Table + " set `info` = #{info} where `id` = #{id}")
    int updateInfo(@Param("info") String info, @Param("id") Long id);

    @Select(" select " + Columns + " from " + Table + " where `account_id` = #{accountId} and status = 1")
    AdAccountInfo getByAccountId(@Param("accountId") String accountId);

    @Select(" select " + Columns + " from " + Table + " where `id` = #{id} and status = 1")
    AdAccountInfo getById(@Param("id") Long id);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"idList != null and idList.size > 0\">" +
            "     and id in (<foreach collection='idList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            "</script>")
    List<AdAccountInfo> getByIds(@Param("idList") List idList);

    @Select(" select count(1) from " + Table + " where `name` = #{name} and status = 1")
    long countByName(@Param("name") String name);

    @Select(" select count(1) from " + Table + " where `account_id` = #{accountId} and status = 1")
    long countByAdAccountId(@Param("accountId") String accountId);

    @Select(" select " + Columns + " from " + Table + " where ad_platform = #{adPlatform} and status = " + VALID)
    List<AdAccountInfo> getByAdPlatform(@Param("adPlatform") String adPlatform);
}
