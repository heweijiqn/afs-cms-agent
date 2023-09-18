package com.afs.dao;

import com.afs.dao.model.AdsenseData;
import com.afs.dao.model.Fd;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface AdsenseDataMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`adsense_data`";
    String Columns = "`id`, `date`, `product_code`, `country_code`, `custom_channel_id`, `adsense_data`, `status`, `ctime`, `utime`, " +
            "cost_per_click, impressions, ad_requests, funnel_impressions, funnel_clicks, funnel_rpm, clicks, funnel_requests, matched_ad_requests, impressions_ctr, estimated_earnings, individual_ad_impressions";

    @Insert(" insert into " + Table + " (`date`, `product_code`, `country_code`, `custom_channel_id`, `adsense_data`, " +
            "`cost_per_click`, `impressions`, `ad_requests`, `funnel_impressions`, `funnel_clicks`, `funnel_rpm`, `clicks`, `funnel_requests`, `matched_ad_requests`, `impressions_ctr`, `estimated_earnings`, `individual_ad_impressions`) " +
            "values (#{date}, #{product_code}, #{country_code}, #{customChannelId}, #{adsense_data}, #{cost_per_click}, #{impressions}, #{ad_requests}, " +
            " #{funnel_impressions}, #{funnel_clicks},  #{funnel_rpm},  #{clicks},  #{funnel_requests},  #{matched_ad_requests},  #{impressions_ctr},  #{estimated_earnings},  #{individual_ad_impressions}) on duplicate key update adsense_data = #{adsense_data}," +
            " cost_per_click = #{cost_per_click}, impressions = #{impressions}, ad_requests = #{ad_requests}, funnel_impressions = #{funnel_impressions}, funnel_clicks = #{funnel_clicks}," +
            " funnel_rpm = #{funnel_rpm}, clicks = #{clicks}, funnel_requests = #{funnel_requests}, matched_ad_requests = #{matched_ad_requests}, impressions_ctr = #{impressions_ctr}, estimated_earnings = #{estimated_earnings}, individual_ad_impressions = #{individual_ad_impressions}")
    int add(AdsenseData adsenseData);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"dateStart != null and dateStart != '' and dateEnd != null and dateEnd != ''\">" +
            "     and `date` &gt;= #{dateStart} and `date` &lt;= #{dateEnd}" +
            "   </if>" +
            "   <if test=\"product_code != null and product_code != ''\">" +
            "     and product_code = #{product_code}" +
            "   </if>" +
            "   <if test=\"country_code != null and country_code != ''\">" +
            "     and country_code = #{country_code}" +
            "   </if>" +
            "   <if test=\"custom_channel_id != null and custom_channel_id != ''\">" +
            "     and custom_channel_id = #{custom_channel_id}" +
            "   </if>" +
            "</where>" +
            "</script>")
    List<Map<String, Object>> getList(@Param("dateStart") String dateStart, @Param("dateEnd") String dateEnd, @Param("product_code") String product_code,
                      @Param("country_code") String country_code, @Param("custom_channel_id") String custom_channel_id);

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1 " +
            "   <if test=\"ts != null and te != ''\">" +
            "     and `date` &gt;= #{ts}" +
            "   </if>" +
            "   <if test=\"te != null and te != ''\">" +
            "     and `date` &lt;= #{te}" +
            "   </if>" +
            "</where>" +
            "</script>")
    List<AdsenseData> getListByDate(@Param("ts") String ts, @Param("te") String te);


    @Select("<script>" +
            "select sum(estimated_earnings) as gross_revenue, " +
            "sum(tt_spend + tb_spend + gg_spend + fb_spend) as tt_spend " +
            " from " + Table +
            "<where>" +
            " status = 1 and custom_channel_id = #{customChannelId} " +
            "   <if test=\"ts != null and te != ''\">" +
            "     and `date` &gt;= #{ts}" +
            "   </if>" +
            "   <if test=\"te != null and te != ''\">" +
            "     and `date` &lt;= #{te}" +
            "   </if>" +
            "</where>" +
            "</script>")
    Map<String, Object> getListByDateAndChannelId(@Param("customChannelId") String customChannelId, @Param("ts") String ts, @Param("te") String te);

    @Select("<script>" +
            "select date, sum(estimated_earnings) as gross_revenue, " +
            "sum(tt_clicks) as tt_clicks, " +
            "sum(tt_spend) as tt_spend, " +
            "sum(tt_impressions) as tt_impressions, " +
            "sum(tt_conversion) as tt_conversion, " +
            "sum(tb_clicks) as tb_clicks, " +
            "sum(tb_spend) as tb_spend, " +
            "sum(tb_impressions) as tb_impressions, " +
            "sum(tb_conversion) as tb_conversion, " +
            "sum(gg_clicks) as gg_clicks, " +
            "sum(gg_spend) as gg_spend, " +
            "sum(gg_impressions) as gg_impressions, " +
            "sum(gg_conversion) as gg_conversion, " +
            "sum(fb_clicks) as fb_clicks, " +
            "sum(fb_spend) as fb_spend, " +
            "sum(fb_impressions) as fb_impressions, " +
            "sum(fb_conversion) as fb_conversion, " +
            "sum(funnel_impressions) as funnel_impressions, " +
            "sum(funnel_clicks) as funnel_clicks, " +
            "100 * (sum(funnel_clicks)/nullif(sum(funnel_impressions), 0)) as funnel_ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(funnel_impressions), 0)) as funnel_eCPM, " +
            "sum(individual_ad_impressions) as impressions, " +
            "sum(clicks) as clicks, " +
            "100 * (sum(clicks)/nullif(sum(individual_ad_impressions), 0)) as ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(individual_ad_impressions), 0)) as eCPM, " +
            "sum(estimated_earnings)/nullif(sum(clicks), 0) as cost_per_click " +
            " from " + Table +
            "<where>" +
//            " status = 1 " +
            " status = 1 and custom_channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
            "   <if test=\"ts != null and te != ''\">" +
            "     and `date` &gt;= #{ts}" +
            "   </if>" +
            "   <if test=\"te != null and te != ''\">" +
            "     and `date` &lt;= #{te}" +
            "   </if>" +
            "   <if test=\"countryList != null and countryList.size > 0\">" +
            "     and country_code in (<foreach collection='countryList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            " group by date order by date desc" +
            "</script>")
    List<Map<String, Object>> getListByFilterAndDimenByDate(@Param("channelIdList") List<String> channelIdList, @Param("countryList") List<String> countryList,
                                                            @Param("ts") String ts, @Param("te") String te);

    @Select("<script>" +
            "select country_code, sum(estimated_earnings) as gross_revenue, " +
            "sum(tt_clicks) as tt_clicks, " +
            "sum(tt_spend) as tt_spend, " +
            "sum(tt_impressions) as tt_impressions, " +
            "sum(tt_conversion) as tt_conversion, " +
            "sum(tb_clicks) as tb_clicks, " +
            "sum(tb_spend) as tb_spend, " +
            "sum(tb_impressions) as tb_impressions, " +
            "sum(tb_conversion) as tb_conversion, " +
            "sum(gg_clicks) as gg_clicks, " +
            "sum(gg_spend) as gg_spend, " +
            "sum(gg_impressions) as gg_impressions, " +
            "sum(gg_conversion) as gg_conversion, " +
            "sum(fb_clicks) as fb_clicks, " +
            "sum(fb_spend) as fb_spend, " +
            "sum(fb_impressions) as fb_impressions, " +
            "sum(fb_conversion) as fb_conversion, " +
            "sum(funnel_impressions) as funnel_impressions, " +
            "sum(funnel_clicks) as funnel_clicks, " +
            "100 * (sum(funnel_clicks)/nullif(sum(funnel_impressions), 0)) as funnel_ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(funnel_impressions), 0)) as funnel_eCPM, " +
            "sum(individual_ad_impressions) as impressions, " +
            "sum(clicks) as clicks, " +
            "100 * (sum(clicks)/nullif(sum(individual_ad_impressions), 0)) as ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(individual_ad_impressions), 0)) as eCPM, " +
            "sum(estimated_earnings)/nullif(sum(clicks), 0) as cost_per_click " +
            " from " + Table +
            "<where>" +
//            " status = 1 " +
            " status = 1 and custom_channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
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
            "select date, country_code, sum(estimated_earnings) as gross_revenue, " +
            "sum(tt_clicks) as tt_clicks, " +
            "sum(tt_spend) as tt_spend, " +
            "sum(tt_impressions) as tt_impressions, " +
            "sum(tt_conversion) as tt_conversion, " +
            "sum(tb_clicks) as tb_clicks, " +
            "sum(tb_spend) as tb_spend, " +
            "sum(tb_impressions) as tb_impressions, " +
            "sum(tb_conversion) as tb_conversion, " +
            "sum(gg_clicks) as gg_clicks, " +
            "sum(gg_spend) as gg_spend, " +
            "sum(gg_impressions) as gg_impressions, " +
            "sum(gg_conversion) as gg_conversion, " +
            "sum(fb_clicks) as fb_clicks, " +
            "sum(fb_spend) as fb_spend, " +
            "sum(fb_impressions) as fb_impressions, " +
            "sum(fb_conversion) as fb_conversion, " +
            "sum(funnel_impressions) as funnel_impressions, " +
            "sum(funnel_clicks) as funnel_clicks, " +
            "100 * (sum(funnel_clicks)/nullif(sum(funnel_impressions), 0)) as funnel_ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(funnel_impressions), 0)) as funnel_eCPM, " +
            "sum(individual_ad_impressions) as impressions, " +
            "sum(clicks) as clicks, " +
            "100 * (sum(clicks)/nullif(sum(individual_ad_impressions), 0)) as ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(individual_ad_impressions), 0)) as eCPM, " +
            "sum(estimated_earnings)/nullif(sum(clicks), 0) as cost_per_click " +
            " from " + Table +
            "<where>" +
//            " status = 1 " +
            " status = 1 and custom_channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
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
            "select country_code, custom_channel_id, sum(estimated_earnings) as gross_revenue, " +
            "sum(tt_clicks) as tt_clicks, " +
            "sum(tt_spend) as tt_spend, " +
            "sum(tt_impressions) as tt_impressions, " +
            "sum(tt_conversion) as tt_conversion, " +
            "sum(tb_clicks) as tb_clicks, " +
            "sum(tb_spend) as tb_spend, " +
            "sum(tb_impressions) as tb_impressions, " +
            "sum(tb_conversion) as tb_conversion, " +
            "sum(gg_clicks) as gg_clicks, " +
            "sum(gg_spend) as gg_spend, " +
            "sum(gg_impressions) as gg_impressions, " +
            "sum(gg_conversion) as gg_conversion, " +
            "sum(fb_clicks) as fb_clicks, " +
            "sum(fb_spend) as fb_spend, " +
            "sum(fb_impressions) as fb_impressions, " +
            "sum(fb_conversion) as fb_conversion, " +
            "sum(funnel_impressions) as funnel_impressions, " +
            "sum(funnel_clicks) as funnel_clicks, " +
            "100 * (sum(funnel_clicks)/nullif(sum(funnel_impressions), 0)) as funnel_ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(funnel_impressions), 0)) as funnel_eCPM, " +
            "sum(individual_ad_impressions) as impressions, " +
            "sum(clicks) as clicks, " +
            "100 * (sum(clicks)/nullif(sum(individual_ad_impressions), 0)) as ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(individual_ad_impressions), 0)) as eCPM, " +
            "sum(estimated_earnings)/nullif(sum(clicks), 0) as cost_per_click " +
            " from " + Table +
            "<where>" +
//            " status = 1 " +
            " status = 1 and custom_channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
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
            "select custom_channel_id, sum(estimated_earnings) as gross_revenue, " +
            "sum(tt_clicks) as tt_clicks, " +
            "sum(tt_spend) as tt_spend, " +
            "sum(tt_impressions) as tt_impressions, " +
            "sum(tt_conversion) as tt_conversion, " +
            "sum(tb_clicks) as tb_clicks, " +
            "sum(tb_spend) as tb_spend, " +
            "sum(tb_impressions) as tb_impressions, " +
            "sum(tb_conversion) as tb_conversion, " +
            "sum(gg_clicks) as gg_clicks, " +
            "sum(gg_spend) as gg_spend, " +
            "sum(gg_impressions) as gg_impressions, " +
            "sum(gg_conversion) as gg_conversion, " +
            "sum(fb_clicks) as fb_clicks, " +
            "sum(fb_spend) as fb_spend, " +
            "sum(fb_impressions) as fb_impressions, " +
            "sum(fb_conversion) as fb_conversion, " +
            "sum(funnel_impressions) as funnel_impressions, " +
            "sum(funnel_clicks) as funnel_clicks, " +
            "100 * (sum(funnel_clicks)/nullif(sum(funnel_impressions), 0)) as funnel_ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(funnel_impressions), 0)) as funnel_eCPM, " +
            "sum(individual_ad_impressions) as impressions, " +
            "sum(clicks) as clicks, " +
            "100 * (sum(clicks)/nullif(sum(individual_ad_impressions), 0)) as ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(individual_ad_impressions), 0)) as eCPM, " +
            "sum(estimated_earnings)/nullif(sum(clicks), 0) as cost_per_click " +
            " from " + Table +
            "<where>" +
//            " status = 1 " +
            " status = 1 and custom_channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
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
            " group by custom_channel_id order by custom_channel_id desc" +
            "</script>")
    List<Map<String, Object>> getListByDimenChannelId(@Param("channelIdList") List<String> channelIdList, @Param("countryList") List<String> countryList, @Param("ts") String ts, @Param("te") String te);

    @Select("<script>" +
            "select date, custom_channel_id, sum(estimated_earnings) as gross_revenue, " +
            "sum(tt_clicks) as tt_clicks, " +
            "sum(tt_spend) as tt_spend, " +
            "sum(tt_impressions) as tt_impressions, " +
            "sum(tt_conversion) as tt_conversion, " +
            "sum(tb_clicks) as tb_clicks, " +
            "sum(tb_spend) as tb_spend, " +
            "sum(tb_impressions) as tb_impressions, " +
            "sum(tb_conversion) as tb_conversion, " +
            "sum(gg_clicks) as gg_clicks, " +
            "sum(gg_spend) as gg_spend, " +
            "sum(gg_impressions) as gg_impressions, " +
            "sum(gg_conversion) as gg_conversion, " +
            "sum(fb_clicks) as fb_clicks, " +
            "sum(fb_spend) as fb_spend, " +
            "sum(fb_impressions) as fb_impressions, " +
            "sum(fb_conversion) as fb_conversion, " +
            "sum(funnel_impressions) as funnel_impressions, " +
            "sum(funnel_clicks) as funnel_clicks, " +
            "100 * (sum(funnel_clicks)/nullif(sum(funnel_impressions), 0)) as funnel_ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(funnel_impressions), 0)) as funnel_eCPM, " +
            "sum(individual_ad_impressions) as impressions, " +
            "sum(clicks) as clicks, " +
            "100 * (sum(clicks)/nullif(sum(individual_ad_impressions), 0)) as ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(individual_ad_impressions), 0)) as eCPM, " +
            "sum(estimated_earnings)/nullif(sum(clicks), 0) as cost_per_click " +
            " from " + Table +
            "<where>" +
//            " status = 1 " +
            " status = 1 and custom_channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
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


    @Select("<script>" +
            "select date, sum(estimated_earnings) as gross_revenue, " +
            "sum(tt_clicks) as tt_clicks, " +
            "sum(tt_spend) as tt_spend, " +
            "sum(tt_impressions) as tt_impressions, " +
            "sum(tt_conversion) as tt_conversion, " +
            "sum(tb_clicks) as tb_clicks, " +
            "sum(tb_spend) as tb_spend, " +
            "sum(tb_impressions) as tb_impressions, " +
            "sum(tb_conversion) as tb_conversion, " +
            "sum(gg_clicks) as gg_clicks, " +
            "sum(gg_spend) as gg_spend, " +
            "sum(gg_impressions) as gg_impressions, " +
            "sum(gg_conversion) as gg_conversion, " +
            "sum(fb_clicks) as fb_clicks, " +
            "sum(fb_spend) as fb_spend, " +
            "sum(fb_impressions) as fb_impressions, " +
            "sum(fb_conversion) as fb_conversion, " +
            "sum(funnel_impressions) as funnel_impressions, " +
            "sum(funnel_clicks) as funnel_clicks, " +
            "100 * (sum(funnel_clicks)/nullif(sum(funnel_impressions), 0)) as funnel_ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(funnel_impressions), 0)) as funnel_eCPM, " +
            "sum(individual_ad_impressions) as impressions, " +
            "sum(clicks) as clicks, " +
            "100 * (sum(clicks)/nullif(sum(individual_ad_impressions), 0)) as ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(individual_ad_impressions), 0)) as eCPM, " +
            "sum(estimated_earnings)/nullif(sum(clicks), 0) as cost_per_click " +
            " from " + Table +
            "<where>" +
//            " status = 1 " +
            " status = 1 and custom_channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
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
            "select date, country_code, custom_channel_id, sum(estimated_earnings) as gross_revenue, " +
            "sum(tt_clicks) as tt_clicks, " +
            "sum(tt_spend) as tt_spend, " +
            "sum(tt_impressions) as tt_impressions, " +
            "sum(tt_conversion) as tt_conversion, " +
            "sum(tb_clicks) as tb_clicks, " +
            "sum(tb_spend) as tb_spend, " +
            "sum(tb_impressions) as tb_impressions, " +
            "sum(tb_conversion) as tb_conversion, " +
            "sum(gg_clicks) as gg_clicks, " +
            "sum(gg_spend) as gg_spend, " +
            "sum(gg_impressions) as gg_impressions, " +
            "sum(gg_conversion) as gg_conversion, " +
            "sum(fb_clicks) as fb_clicks, " +
            "sum(fb_spend) as fb_spend, " +
            "sum(fb_impressions) as fb_impressions, " +
            "sum(fb_conversion) as fb_conversion, " +
            "sum(funnel_impressions) as funnel_impressions, " +
            "sum(funnel_clicks) as funnel_clicks, " +
            "100 * (sum(funnel_clicks)/nullif(sum(funnel_impressions), 0)) as funnel_ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(funnel_impressions), 0)) as funnel_eCPM, " +
            "sum(individual_ad_impressions) as impressions, " +
            "sum(clicks) as clicks, " +
            "100 * (sum(clicks)/nullif(sum(individual_ad_impressions), 0)) as ctr, " +
            "1000 * (sum(estimated_earnings)/nullif(sum(individual_ad_impressions), 0)) as eCPM, " +
            "sum(estimated_earnings)/nullif(sum(clicks), 0) as cost_per_click " +
            " from " + Table +
            "<where>" +
//            " status = 1 " +
            " status = 1 and custom_channel_id in (<foreach collection='channelIdList' item='e' separator=','>#{e}</foreach>)" +
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
            " group by date, country_code, custom_channel_id order by date, country_code, custom_channel_id desc" +
            "</script>")
    List<Map<String, Object>> getListByDimen(@Param("channelIdList") List<String> channelIdList, @Param("countryList") List<String> countryList, @Param("ts") String ts, @Param("te") String te);

    @Update("update " + Table + " set `tt_clicks` = #{clicks}, `tt_spend` = #{spend}, `tt_impressions` = #{impressions}, `tt_conversion` = #{conversion} where `custom_channel_id` = #{channelId} and `country_code` = #{countryCode} and `date` = #{date}")
    int updateTTInfo(@Param("clicks") String clicks, @Param("spend") String spend,
                     @Param("impressions") String impressions, @Param("conversion") String conversion,
                     @Param("countryCode") String countryCode, @Param("channelId") String channelId, @Param("date") String date);

    @Update("update " + Table + " set `tb_clicks` = #{clicks}, `tb_spend` = #{spend}, `tb_impressions` = #{impressions}, `tb_conversion` = #{conversion} where `custom_channel_id` = #{channelId} and `country_code` = #{countryCode} and `date` = #{date}")
    int updateTaboolaInfo(@Param("clicks") String clicks, @Param("spend") String spend,
                     @Param("impressions") String impressions, @Param("conversion") String conversion,
                     @Param("countryCode") String countryCode, @Param("channelId") String channelId, @Param("date") String date);

    @Update("update " + Table + " set `ad_resource_id` = #{adResourceId} where `id` = #{id}")
    int updateAdResourceId(@Param("adResourceId") String adResourceId, @Param("id") Long id);

}