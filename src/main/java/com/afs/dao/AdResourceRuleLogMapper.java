package com.afs.dao;

import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.AdResourceRule;
import com.afs.dao.model.AdResourceRuleLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface AdResourceRuleLogMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ad_resource_rule_log`";
    String Columns = "`id`, `ad_resource_id`, `ad_group_id`, `ad_group_name`, `campaign_id`, `campaign_name`, `rule_id`," +
            " `rule_name`, `action`, `day_num`, `condition`, `httpBody`, `httpResponse`,  `status`, `ctime`, `utime`";

    @Insert("insert into " + Table + " (`ad_resource_id`, `ad_group_id`, `ad_group_name`, `campaign_id`, `campaign_name`, `rule_id`," +
            " `rule_name`, `action`, `day_num`, `condition`, `httpBody`, `httpResponse`) " +
            " values (#{adResourceId}, #{adGroupId}, #{adGroupName}, #{campaignId}, #{campaignName}, #{ruleId}, #{ruleName}, " +
            " #{action}, #{dayNum}, #{condition}, #{httpBody}, #{httpResponse})")
    int add(AdResourceRuleLog adResourceRuleLog);

    @Update("<script>" +
            " update " + Table + " " +
            " <set>" +
            "   <if test=\"httpResponse != null\">" +
            "     http_response = #{httpResponse}," +
            "   </if>" +
            "   <if test=\"httpBody != null\">" +
            "     http_body = #{httpBody}," +
            "   </if>" +
            " </set>" +
            " where id = #{id}" +
            "</script>")
    int updateAdAccountInfo(AdResourceRuleLog adResourceRuleLog);

    @Select("SELECT " + Columns + " FROM " + Table + " WHERE ad_resource_id = #{adResourceId} order by ctime desc")
    List<AdResourceRuleLog> getByAdResourceId(@Param("adResourceId") String adResourceId);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"name != null and name != ''\">" +
            "     and name like concat('%', #{name}, '%')" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<AdResourceRule> getListByQuery(@Param("name") String name, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"name != null and name != ''\">" +
            "     and name like concat('%', #{name}, '%')" +
            "   </if>" +
            "</where>" +
            "</script>")
    int countByQuery(@Param("name") String name);

    @Delete("delete from " + Table + " where `name` = #{name}")
    int del(@Param("name") String name);
}