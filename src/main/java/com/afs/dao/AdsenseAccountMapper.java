package com.afs.dao;

import com.afs.dao.model.AdsenseChannel;
import com.afs.dao.model.AdsenseAccount;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface AdsenseAccountMapper {
    String Table = "`adsense_account`";
    String Columns = "`pub_id`, `site`, `status`, `ctime`, `utime`";

    @Insert("insert into " + Table + " (`pub_id`, `site`) values (#{pubId}, #{site}) on duplicate key update status = 1")
    int add(AdsenseAccount adsenseAccount);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<AdsenseAccount> getListByQuery(@Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " status = 1" +
            "</where>" +
            "</script>")
    int countByQuery();

    @Select("select " + Columns + " from " + Table + " where `site` like concat('%', #{site}, '%') order by id desc limit 1")
    AdsenseAccount getBySite(@Param("site") String site);

    @Select("select count(1) from " + Table + " where pub_id = #{pubId}")
    long countByPubId(@Param("pubId") String pubId);

    @Update("update " + Table + " set site = #{site} where `pub_id` = #{pubId}")
    int updateSite(@Param("pubId") String pubId, @Param("site") String site);

}


