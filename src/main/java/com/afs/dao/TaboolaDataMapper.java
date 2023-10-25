package com.afs.dao;

import com.afs.dao.model.TaboolaData;
import com.afs.dao.model.TiktokData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface TaboolaDataMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`taboola_data`";
    String Columns = "`id`, `date`, `country_code`, `campaign_id`, `campaign_name`, `landing_url`, `channel_id`, `clicks`, `spend`, `impressions`, `conversion`, `ctime`, `utime`, `account_name`";

    @Insert(" insert into " + Table + " (`date`, `country_code`, `campaign_id`, `campaign_name`, `clicks`, `spend`, `impressions`, `conversion`, `account_name`) values " +
            " (#{date}, #{countryCode}, #{campaignId}, #{campaignName}, #{clicks}, #{spend}, #{impressions}, #{conversion}, #{accountName})" +
            " on duplicate key update campaign_name = #{campaignName}, clicks = #{clicks}, spend = #{spend}, impressions = #{impressions}, conversion = #{conversion}, account_name = #{accountName}")
    int add(TaboolaData data);

    @Update("update " + Table + " set `landing_url` = #{landingUrl}, `channel_id` = #{channelId} where `campaign_id` = #{campaignId}")
    int updateCampaignInfo(@Param("landingUrl") String landingUrl, @Param("channelId") String channelId, @Param("campaignId") String campaignId);

    @Select("<script>" +
            "select " + Columns + " from " + Table + " where account_name = #{accountName}" +
            "</script>")
    List<TaboolaData> getListByDate(@Param("accountName") String accountName);

    @Select("<script>" +
            "select date, country_code, channel_id, sum(impressions) as impressions, " +
            "sum(conversion) as conversion, " +
            "sum(spend) as spend, " +
            "sum(clicks) as clicks " +
            " from " + Table +
            " group by date, country_code, channel_id" +
            "</script>")
    List<TaboolaData> getListGroupByDateAndCountryAndChannelId();

    @Select(" select landing_url from " + Table + " where `channel_id` = #{channelId} order by id desc limit 1")
    String getLandingUrlByChannelId(@Param("channelId") String channelId);

    @Select("<script>" +
            "select " + Columns + " from " + Table + " where date = #{date} and channel_id = ''" +
            "</script>")
    List<TaboolaData> getNoChannelIdByDate(@Param("date") String date);
}