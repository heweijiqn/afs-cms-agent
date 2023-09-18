package com.afs.dao;

import com.afs.dao.model.AdData;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.ArticleInfo;
import com.afs.dao.model.CategoryChildInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;


public interface AdResourceMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ad_resource`";
    String Columns = "`id`, `account_id`, `title`, `ad_resource_id`, `article_id`, `channel_id`, `campaign`, `am`, " +
            "`ad_platform`, `land_page_url`, `ad_desc`, `audit_msg`, `media_list`, `state`, `partner`, " +
            "`status`, `country`, `keyword`, `ad_account`,`ad_spend`,`gross_revenue`,`profit`,`roi`, `rule_id`, " +
            "`last_action_time`, `start_rule_time`, `ad_platform_info`, `ad_platform_state`, `ad_platform_msg`, `ctime`, `utime`";

    @Insert("insert into " + Table + " (`account_id`, `ad_resource_id`, `title`, `article_id`, `channel_id`, `campaign`, `am`, `ad_platform`, `land_page_url`, `ad_desc`, `audit_msg`, `partner`, `media_list`, `state`, `country`, `keyword`, `ad_account`, `ad_platform_info`) " +
            " values (#{accountId}, #{adResourceId}, #{title}, #{articleId}, #{channelId}, #{campaign}, #{am}, #{adPlatform}, #{landPageUrl}, #{adDesc}, #{auditMsg}, #{partner}, #{mediaList}, #{state}, #{country}, #{keyword}, #{adAccount}, #{adPlatformInfo}) ")
    int add(AdResource adResource);

    @Select("select count(*) from " + Table)
    long count();

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1 and channel_id != '' " +
            "   <if test=\"adResourceIdList != null and adResourceIdList.size > 0\">" +
            "     and ad_resource_id in (<foreach collection='adResourceIdList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"campaign != null and campaign != ''\">" +
            "     and campaign like concat('%', #{campaign}, '%')" +
            "   </if>" +
            "   <if test=\"partner != null and partner != ''\">" +
            "     and partner = #{partner}" +
            "   </if>" +
            "   <if test=\"amList != null and amList.size > 0\">" +
            "     and am in (<foreach collection='amList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"adPlatform != null and adPlatform != ''\">" +
            "     and ad_platform like concat('%', #{adPlatform}, '%')" +
            "   </if>" +
            "   <if test=\"articleIdList != null and articleIdList.size > 0\">" +
            "     and article_id in (<foreach collection='articleIdList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            "</script>")
    List<AdResource> getListByQueryForGetChannelId(@Param("articleIdList") List articleIdList,
                                                   @Param("partner") String partner,
                                                   @Param("adResourceIdList") List adResourceIdList,
                                                   @Param("campaign") String campaign,
                                                   @Param("adPlatform") String adPlatform,
                                                   @Param("amList") List amList);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"channelId != null and channelId != ''\">" +
            "     and channel_id = #{channelId}" +
            "   </if>" +
            "   <if test=\"campaign != null and campaign != ''\">" +
            "     and campaign like concat('%', #{campaign}, '%')" +
            "   </if>" +
            "   <if test=\"platform != null and platform != ''\">" +
            "     and ad_platform like concat('%', #{platform}, '%')" +
            "   </if>" +
            "   <if test=\"state != null and state != ''\">" +
            "     and state = #{state}" +
            "   </if>" +
            "   <if test=\"partner != null and partner != ''\">" +
            "     and partner = #{partner}" +
            "   </if>" +
            "   <if test=\"amList != null and amList.size > 0\">" +
            "     and am in (<foreach collection='amList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"articleId != null and articleId != ''\">" +
            "     and article_id = #{articleId}" +
            "   </if>" +
            "   <if test=\"site != null and site != ''\">" +
            "     and land_page_url like concat('%', #{site}, '%')" +
            "   </if>" +
            "   <if test=\"adResourceId != null and adResourceId != ''\">" +
            "     and ad_resource_id = #{adResourceId}" +
            "   </if>" +
            "</where>" +
            " order by ${orderParam} ${orderDirection} limit #{offset}, #{size}" +
            "</script>")
    List<AdResource> getListByQuery(@Param("channelId") String channelId, @Param("adResourceId") String adResourceId,
                                    @Param("articleId") String articleId, @Param("partner") String partner,
                                    @Param("amList") List amList, @Param("platform") String platform,
                                    @Param("site") String site,
                                    @Param("state") String state, @Param("campaign") String campaign,
                                    @Param("orderParam") String orderParam, @Param("orderDirection") String orderDirection,
                                    @Param("offset") int offset, @Param("size") int size);

    @Select("select " + Columns + " from " + Table + " where ad_resource_id = #{adResourceId}")
    AdResource getByAdResourceId(@Param("adResourceId") String adResourceId);

    @Select("select " + Columns + " from " + Table + " where state = #{state} and rule_id != ''")
    List<AdResource> getListBindRule(@Param("state") String state);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " ad_platform like concat('%', #{platform}, '%') " +
            "   <if test=\"stateList != null and stateList.size > 0\">" +
            "     and state in (<foreach collection='stateList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            "</script>")
    List<AdResource> getListByPlatformAndStateList(@Param("platform") String platform, @Param("stateList") List<String> stateList);

    @Select("select " + Columns + " from " + Table + " where ad_platform like concat('%', #{platform}, '%') and state = #{state}")
    List<AdResource> getListByPlatformAndState(@Param("platform") String platform, @Param("state") String state);

    @Select("select " + Columns + " from " + Table + " where ad_platform like concat('%', #{platform}, '%') and state = #{state} and utime > #{uTime}")
    List<AdResource> getListByPlatformAndStateAndUTime(@Param("platform") String platform, @Param("state") String state, @Param("uTime") String uTime);

    @Select("select " + Columns + " from " + Table + " where ad_platform like concat('%', #{platform}, '%')")
    List<AdResource> getListByPlatform(@Param("platform") String platform);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"adResourceIdList != null and adResourceIdList.size > 0\">" +
            "     and ad_resource_id in (<foreach collection='adResourceIdList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            "</script>")
    List<AdResource> getByAdResourceIdList(@Param("adResourceIdList") List<String> adResourceIdList);

    @Select("select " + Columns + " from " + Table + " where partner = #{partner} and channel_id != ''")
    List<AdResource> getByPartner(@Param("partner") String partner);

    @Select("select " + Columns + " from " + Table + " where article_id = #{articleId}")
    List<AdResource> getByArticleId(@Param("articleId") String articleId);

    @Select("select count(1) from " + Table + " where channel_id = #{channelId} and state = #{state}")
    int countByChannelIdByState(@Param("channelId") String channelId, @Param("state") String state);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " channel_id = #{channelId} and ad_resource_id != #{adResourceId} " +
            "   <if test=\"stateList != null and stateList.size > 0\">" +
            "     and state in (<foreach collection='stateList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            "</script>")
    int countOtherAdResourceIdByChannelIdByStateList(@Param("adResourceId") String adResourceId,
                                                     @Param("channelId") String channelId, @Param("stateList") List<String> stateList);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " channel_id = #{channelId}" +
            "   <if test=\"stateList != null and stateList.size > 0\">" +
            "     and state in (<foreach collection='stateList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            "</script>")
    int countByChannelIdByStateList(@Param("channelId") String channelId, @Param("stateList") List<String> stateList);

    @Select("select " + Columns + " from " + Table + " where channel_id = #{channelId} and state = #{state} limit 1")
    AdResource getOneByChannelIdAndState(@Param("channelId") String channelId, @Param("state") String state);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"channelId != null and channelId != ''\">" +
            "     and channel_id = #{channelId}" +
            "   </if>" +
            "   <if test=\"campaign != null and campaign != ''\">" +
            "     and campaign like concat('%', #{campaign}, '%')" +
            "   </if>" +
            "   <if test=\"platform != null and platform != ''\">" +
            "     and ad_platform like concat('%', #{platform}, '%')" +
            "   </if>" +
            "   <if test=\"state != null and state != ''\">" +
            "     and state = #{state}" +
            "   </if>" +
            "   <if test=\"partner != null and partner != ''\">" +
            "     and partner = #{partner}" +
            "   </if>" +
            "   <if test=\"amList != null and amList.size > 0\">" +
            "     and am in (<foreach collection='amList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"articleId != null and articleId != ''\">" +
            "     and article_id = #{articleId}" +
            "   </if>" +
            "   <if test=\"site != null and site != ''\">" +
            "     and land_page_url like concat('%', #{site}, '%')" +
            "   </if>" +
            "   <if test=\"adResourceId != null and adResourceId != ''\">" +
            "     and ad_resource_id = #{adResourceId}" +
            "   </if>" +
            "</where>" +
            "</script>")
    int countByQuery(@Param("channelId") String channelId, @Param("adResourceId") String adResourceId,
                     @Param("articleId") String articleId, @Param("partner") String partner,
                     @Param("amList") List amList, @Param("platform") String platform,
                     @Param("site") String site, @Param("state") String state,
                     @Param("campaign") String campaign);

    @Update("update " + Table + " set `channel_id` = #{channelId}, `land_page_url` = #{landPageUrl}, state = #{state} where `ad_resource_id` = #{adResourceId}")
    int auditPass(@Param("channelId") String channelId, @Param("landPageUrl") String landPageUrl, @Param("state") String state, @Param("adResourceId") String adResourceId);

    @Update("update " + Table + " set `audit_msg` = #{auditMsg}, state = #{state} where `ad_resource_id` = #{adResourceId}")
    int auditReject(@Param("auditMsg") String auditMsg, @Param("state") String state, @Param("adResourceId") String adResourceId);

    @Delete("delete from " + Table + " where `ad_resource_id` = #{adResourceId}")
    int del(@Param("adResourceId") String adResourceId);

    @Update("update " + Table + " set state = #{state} where `ad_resource_id` = #{adResourceId}")
    int archive(@Param("state") String state, @Param("adResourceId") String adResourceId);

    @Update("update " + Table + " set rule_id = #{ruleId}, start_rule_time = #{startRuleTime} where `ad_resource_id` = #{adResourceId}")
    int bindRule(@Param("ruleId") Long ruleId, @Param("startRuleTime") String startRuleTime, @Param("adResourceId") String adResourceId);

    @Update("update " + Table + " set rule_id = null, start_rule_time = '' where `ad_resource_id` = #{adResourceId}")
    int unBindRule(@Param("adResourceId") String adResourceId);

    @Update("<script>" +
            " update " + Table + " " +
            " <set>" +
            "   <if test=\"campaign != null\">" +
            "     campaign = #{campaign}," +
            "   </if>" +
            "   <if test=\"country != null\">" +
            "     country = #{country}," +
            "   </if>" +
            "   <if test=\"keyword != null\">" +
            "     keyword = #{keyword}," +
            "   </if>" +
            " </set>" +
            " where ad_resource_id = #{adResourceId}" +
            "</script>")
    int updateCampaign(@Param("country") String country, @Param("keyword") String keyword,
                       @Param("campaign") String campaign, @Param("adResourceId") String adResourceId);

    @Update("<script>" +
            " update " + Table + " " +
            " <set>" +
            "   <if test=\"campaign != null\">" +
            "     campaign = #{campaign}," +
            "   </if>" +
            "   <if test=\"country != null\">" +
            "     country = #{country}," +
            "   </if>" +
            "   <if test=\"keyword != null\">" +
            "     keyword = #{keyword}," +
            "   </if>" +
            "   <if test=\"landPageUrl != null\">" +
            "     land_page_url = #{landPageUrl}," +
            "   </if>" +
            "   <if test=\"adPlatformInfo != null\">" +
            "     ad_platform_info = #{adPlatformInfo}," +
            "   </if>" +
            " </set>" +
            " where ad_resource_id = #{adResourceId}" +
            "</script>")
    int updateForTiktok(AdResource adResource);

    @Select("select ad_resource_id, title from " + Table + " where `ad_resource_id` like concat('%', #{adResourceId}, '%')")
    List<AdResource> searchAdResourceId(@Param("adResourceId") String adResourceId);

    @Select("select ad_resource_id, title, campaign from " + Table + " where `campaign` like concat('%', #{campaign}, '%')")
    List<AdResource> searchAdResourceCampaign(@Param("campaign") String campaign);

    @Select("SELECT article_id FROM " + Table + " WHERE ad_resource_id = #{adResourceId}")
    String getArticleIdByAdResourceId(@Param("adResourceId") String adResourceId);

    @Update("update " + Table + " set ad_spend = #{adSpend}, gross_revenue = #{grossRevenue}, profit = #{profit}, roi = #{roi} where `ad_resource_id` = #{adResourceId}")
    int updateLastData(@Param("adResourceId") String adResourceId, @Param("adSpend") Double adSpend, @Param("grossRevenue") Double grossRevenue,
                       @Param("profit") Double profit, @Param("roi") Double roi);

    @Select("<script>" +
            "select channel_id, state, count(*) as num from " + Table +
            "<where>" +
            "   <if test=\"stateList != null and stateList.size > 0\">" +
            "      state in (<foreach collection='stateList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            " group by channel_id, state having num > 1" +
            "</script>")
    List<Map<String, Object>> getInvalidAdResource(@Param("stateList") List<String> stateList);

    @Update("update " + Table + " set `last_action_time` = #{lastActionTime} where `ad_resource_id` = #{adResourceId}")
    int updateLastActionTime(@Param("lastActionTime") String lastActionTime, @Param("adResourceId") String adResourceId);

    @Update("update " + Table + " set `ad_platform_state` = #{adPlatformState}, `ad_platform_msg` = #{adPlatformMsg} where `ad_resource_id` = #{adResourceId}")
    int updateAdPlatformStateAndMsg(@Param("adPlatformState") String adPlatformState, @Param("adPlatformMsg") String adPlatformMsg, @Param("adResourceId") String adResourceId);

    @Update("update " + Table + " set `ad_platform_state` = #{adPlatformState} where `ad_resource_id` = #{adResourceId}")
    int updateAdPlatformState(@Param("adPlatformState") String adPlatformState, @Param("adResourceId") String adResourceId);

    @Update("update " + Table + " set `ad_platform_info` = #{adPlatformInfo} where `ad_resource_id` = #{adResourceId}")
    int updateAdPlatformInfo(@Param("adPlatformInfo") String adPlatformInfo, @Param("adResourceId") String adResourceId);

    @Update("update " + Table + " set ad_platform_state = 'PLATFORM_CREATING', ad_platform_msg = '' "
            + "where ad_resource_id = #{id} and status = 1 and state = 'PASS' "
            + "and coalesce(ad_platform_state, '') in ('', 'LOCAL_ONLY', 'PLATFORM_REJECTED')")
    int claimPlatformCampaign(@Param("id") String id);

    @Update("update " + Table + " set ad_platform_state = 'PLATFORM_PAUSED', ad_platform_info = #{info}, ad_platform_msg = #{message} "
            + "where ad_resource_id = #{id} and ad_platform_state = 'PLATFORM_CREATING'")
    int completePlatformCampaign(@Param("id") String id, @Param("info") String info, @Param("message") String message);
}
