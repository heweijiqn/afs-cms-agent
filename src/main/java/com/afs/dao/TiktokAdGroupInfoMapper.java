package com.afs.dao;

import com.afs.dao.model.TiktokAdGroupInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface TiktokAdGroupInfoMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`tiktok_ad_group_info`";
    String Columns = "`id`, `ad_resource_id`, `channel_id`, `ad_group_id`, `ad_group_name`, `campaign_id`, `campaign_name`, `advertiser`, `ad_group_status`, `budget`, `conversion`, `gross_revenue`, `ad_spend`, `profit`, `roi`, `status`, `ctime`, `utime`";

    @Insert(" insert into " + Table + " (`ad_resource_id`, `channel_id`, `ad_group_id`, `ad_group_name`, `campaign_id`, `campaign_name`, " +
            "`advertiser`, `ad_group_status`, `budget`, `conversion`, `gross_revenue`, `ad_spend`, `profit`, `roi`) " +
            "values (#{adResourceId}, #{channelId}, #{adGroupId}, #{adgroupName}, #{campaignId}, #{campaignName}, #{advertiser}, " +
            " #{adGroupStatus}, #{budget}, #{conversion}, #{grossRevenue}, #{adSpend}, #{profit}, #{roi})" +
            " on duplicate key update ad_resource_id = #{adResourceId}, channel_id = #{channelId}, ad_group_name = #{adgroupName}, campaign_id = #{campaignId}," +
            " campaign_name = #{campaignName}, advertiser = #{advertiser}, ad_group_status = #{adGroupStatus}, budget = #{budget}, " +
            " conversion = #{conversion}, gross_revenue = #{grossRevenue}, ad_spend = #{adSpend}, profit = #{profit}, roi = #{roi}")
    int add(TiktokAdGroupInfo info);

    @Select("select " + Columns + " from " + Table + " where ad_resource_id = #{adResourceId} order by profit desc")
    List<TiktokAdGroupInfo> getByAdResourceId(@Param("adResourceId") String adResourceId);

    @Select("select " + Columns + " from " + Table)
    List<TiktokAdGroupInfo> getAll();

    @Select("select " + Columns + " from " + Table + " where ad_group_id = #{adGroupId}")
    TiktokAdGroupInfo getByAdGroupId(@Param("adGroupId") String adGroupId);

    @Select("select " + Columns + " from " + Table)
    List<TiktokAdGroupInfo> getByAdGroupStatus();

    @Update("update " + Table + " set `conversion` = #{conversion}, `ad_spend` = #{adSpend} where `ad_group_id` = #{adGroupId}")
    int updateTikTokByAdgroupId(TiktokAdGroupInfo info);

    @Update("update " + Table + " set `ad_group_status` = #{adStatus} where `ad_group_id` = #{adGroupId}")
    int updateTikTokAdGroupStatus(@Param("adGroupId") String adGroupId, @Param("adStatus") String adStatus);

    @Update("update " + Table + " set `gross_revenue` = #{grossRevenue}, `profit` = #{profit}, `roi` = #{roi} where `ad_group_id` = #{adGroupId}")
    int updateAdsenseDataByAdgroupId(TiktokAdGroupInfo info);

}