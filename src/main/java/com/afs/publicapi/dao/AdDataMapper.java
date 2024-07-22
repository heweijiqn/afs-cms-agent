package com.afs.publicapi.dao;

import com.afs.publicapi.dao.model.AdData;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AdDataMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ad_data`";
    String Columns = "`id`, `app_id`, `ad_id`, `day_time`, `country`, `pv`, `total`, `ecpm`, `page_site`, `page_pv`, `click`, `page_ecpm`, `other`, `status`, `ctime`, `utime`";


    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"appId != null and appId!=''\">" +
            "     and app_id = #{appId}" +
            "   </if>" +
            "   <if test=\"timeStart != null\">" +
            "     and `day_time` &gt;= #{timeStart}" +
            "   </if>" +
            "   <if test=\"timeEnd != null\">" +
            "     and `day_time` &lt;= #{timeEnd}" +
            "   </if>" +
            "</where>" +
            "</script>")
    List<AdData> getAllDataByParam(@Param("appId") String appId, @Param("timeStart") Long timeStart, @Param("timeEnd") Long timeEnd);
}