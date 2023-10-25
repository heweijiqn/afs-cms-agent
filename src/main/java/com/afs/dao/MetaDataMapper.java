package com.afs.dao;

import com.afs.dao.model.MetaData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface MetaDataMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`meta_data`";
    String Columns = "`id`, `date`, `country_code`, `campaign_id`, `campaign_name`, `landing_url`, `channel_id`, `clicks`, `spend`, `impressions`, `conversion`, `ctime`, `utime`, `advertiser`";

    @Insert(" insert into " + Table + " (`date`, `country_code`, `campaign_id`, `campaign_name`, `clicks`, `spend`, `impressions`, `conversion`, `advertiser`) values " +
            " (#{date}, #{countryCode}, #{campaignId}, #{campaignName}, #{clicks}, #{spend}, #{impressions}, #{conversion}, #{advertiser})" +
            " on duplicate key update campaign_name = #{campaignName}, clicks = #{clicks}, spend = #{spend}, impressions = #{impressions}, conversion = #{conversion}, advertiser = #{advertiser}")
    int add(MetaData data);

    @Update("update " + Table + " set `landing_url` = #{landingUrl}, `channel_id` = #{channelId} where `campaign_id` = #{campaignId}")
    int updateCampaignInfo(@Param("landingUrl") String landingUrl, @Param("channelId") String channelId, @Param("campaignId") String campaignId);

    @Select("<script>" +
            "select " + Columns + " from " + Table + " where account_name = #{accountName}" +
            "</script>")
    List<MetaData> getListByDate(@Param("accountName") String accountName);

    @Select(" select landing_url from " + Table + " where `channel_id` = #{channelId} order by id desc limit 1")
    String getLandingUrlByChannelId(@Param("channelId") String channelId);

    @Select("<script>" +
            "select date, country_code, channel_id, sum(impressions) as impressions, " +
            "sum(conversion) as conversion, " +
            "sum(spend) as spend, " +
            "sum(clicks) as clicks " +
            " from " + Table +
            " group by date, country_code, channel_id" +
            "</script>")
    List<MetaData> getListGroupByDateAndCountryAndChannelId();

    @Select("<script>" +
            "select date, sum(impressions) as ad_impressions, " +
            "sum(conversion) as ad_conversion, " +
            "sum(spend) as ad_spend, " +
            "sum(clicks) as ad_clicks " +
            " from " + Table +
            "<where>" +
            " 1 = 1 and channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
            "   <if test=\"ts != null\">" +
            "     and `date` &gt;= #{ts}" +
            "   </if>" +
            "   <if test=\"te != null\">" +
            "     and `date` &lt;= #{te}" +
            "   </if>" +
            "   <if test=\"countryList != null and countryList.size > 0\">" +
            "     and country_code in (<foreach collection='countryList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            " group by date order by date desc" +
            "</script>")
    List<Map<String, Object>> getListByDimenDate(@Param("channelIdList") List<String> channelIdList, @Param("countryList") List<String> countryList, @Param("ts") String ts, @Param("te") String te);

    @Select("<script>" +
            "select date, sum(impressions) as ad_impressions, " +
            "sum(conversion) as ad_conversion, " +
            "sum(spend) as ad_spend, " +
            "sum(clicks) as ad_clicks " +
            " from " + Table +
            "<where>" +
            " 1 = 1 and channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
            "   <if test=\"ts != null\">" +
            "     and `date` &gt;= #{ts}" +
            "   </if>" +
            "   <if test=\"te != null\">" +
            "     and `date` &lt;= #{te}" +
            "   </if>" +
            "   <if test=\"countryList != null and countryList.size > 0\">" +
            "     and country_code in (<foreach collection='countryList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            " group by date, country_code order by date desc" +
            "</script>")
    List<Map<String, Object>> getListByDimenDateAndCountry(@Param("channelIdList") List<String> channelIdList, @Param("countryList") List<String> countryList, @Param("ts") String ts, @Param("te") String te);

    @Select("<script>" +
            "select date, sum(impressions) as ad_impressions, " +
            "sum(conversion) as ad_conversion, " +
            "sum(spend) as ad_spend, " +
            "sum(clicks) as ad_clicks " +
            " from " + Table +
            "<where>" +
            " 1 = 1 and channel_id in in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
            "   <if test=\"ts != null\">" +
            "     and `date` &gt;= #{ts}" +
            "   </if>" +
            "   <if test=\"te != null\">" +
            "     and `date` &lt;= #{te}" +
            "   </if>" +
            "   <if test=\"countryList != null and countryList.size > 0\">" +
            "     and country_code in (<foreach collection='countryList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            " group by country_code order by country_code asc" +
            "</script>")
    List<Map<String, Object>> getListByDimenCountry(@Param("channelIdList") List<String> channelIdList, @Param("countryList") List<String> countryList, @Param("ts") String ts, @Param("te") String te);

    @Select("<script>" +
            "select date, sum(impressions) as ad_impressions, " +
            "sum(conversion) as ad_conversion, " +
            "sum(spend) as ad_spend, " +
            "sum(clicks) as ad_clicks " +
            " from " + Table +
            "<where>" +
            " 1 = 1 and channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
            "   <if test=\"ts != null\">" +
            "     and `date` &gt;= #{ts}" +
            "   </if>" +
            "   <if test=\"te != null\">" +
            "     and `date` &lt;= #{te}" +
            "   </if>" +
            "   <if test=\"countryList != null and countryList.size > 0\">" +
            "     and country_code in (<foreach collection='countryList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            " group by country_code, custom_channel_id order by country_code desc" +
            "</script>")
    List<Map<String, Object>> getListByDimenCountryAndChannelId(@Param("channelIdList") List<String> channelIdList, @Param("countryList") List<String> countryList, @Param("ts") String ts, @Param("te") String te);

    @Select("<script>" +
            "select date, sum(impressions) as ad_impressions, " +
            "sum(conversion) as ad_conversion, " +
            "sum(spend) as ad_spend, " +
            "sum(clicks) as ad_clicks " +
            " from " + Table +
            "<where>" +
            " 1 = 1 and channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
            "   <if test=\"ts != null\">" +
            "     and `date` &gt;= #{ts}" +
            "   </if>" +
            "   <if test=\"te != null\">" +
            "     and `date` &lt;= #{te}" +
            "   </if>" +
            "   <if test=\"countryList != null and countryList.size > 0\">" +
            "     and country_code in (<foreach collection='countryList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            " group by country_code, custom_channel_id order by country_code desc" +
            "</script>")
    List<Map<String, Object>> getListByDimenChannelId(@Param("channelIdList") List<String> channelIdList, @Param("countryList") List<String> countryList, @Param("ts") String ts, @Param("te") String te);

    @Select("<script>" +
            "select date, sum(impressions) as ad_impressions, " +
            "sum(conversion) as ad_conversion, " +
            "sum(spend) as ad_spend, " +
            "sum(clicks) as ad_clicks " +
            " from " + Table +
            "<where>" +
            " 1 = 1 and channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
            "   <if test=\"ts != null\">" +
            "     and `date` &gt;= #{ts}" +
            "   </if>" +
            "   <if test=\"te != null\">" +
            "     and `date` &lt;= #{te}" +
            "   </if>" +
            "   <if test=\"countryList != null and countryList.size > 0\">" +
            "     and country_code in (<foreach collection='countryList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            " group by date, custom_channel_id order by date desc" +
            "</script>")
    List<Map<String, Object>> getListByDimenDateAndChannelId(@Param("channelIdList") List<String> channelIdList, @Param("countryList") List<String> countryList, @Param("ts") String ts, @Param("te") String te);

}