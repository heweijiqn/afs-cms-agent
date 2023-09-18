package com.afs.dao;

import com.afs.dao.model.AdsenseChannel;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface AdsenseChannelMapper {

    String Table = "`adsense_channel`";
    String Columns = "`channel_id`, `pub_id`, `state`, `status`, `utime`, `ctime`";

    @Insert("insert into " + Table + " (`channel_id`, `pub_id`, `state`) values (#{channelId}, #{pubId}, #{state}) on duplicate key update status = 1")
    int add(AdsenseChannel adsenseChannel);

    @Delete("delete from " + Table + " where `channel_id` = #{channelId} and `pub_id` = #{pubId}")
    int del(@Param("channelId") String channelId, @Param("pubId") String pubId);

    @Select("SELECT COUNT(*) FROM " + Table)
    int getTotalCount();

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"channelId != null and channelId != ''\">" +
            "     and channel_id = #{channelId}" +
            "   </if>" +
            "   <if test=\"pubId != null and pubId != ''\">" +
            "     and pub_id = #{pubId}" +
            "   </if>" +
            "   <if test=\"state != null and state != ''\">" +
            "     and state = #{state}" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<AdsenseChannel> getListByQuery(@Param("state") String state, @Param("pubId") String pubId, @Param("channelId") String channelId,
                                        @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"channelId != null and channelId != ''\">" +
            "     and channel_id = #{channelId}" +
            "   </if>" +
            "   <if test=\"pubId != null and pubId != ''\">" +
            "     and pub_id = #{pubId}" +
            "   </if>" +
            "   <if test=\"state != null and state != ''\">" +
            "     and state = #{state}" +
            "   </if>" +
            "</where>" +
            "</script>")
    int countByQuery(@Param("state") String state, @Param("pubId") String pubId, @Param("channelId") String channelId);

    @Select("select " + Columns + " from " + Table + " where `state` = #{state} and pub_id = #{pubId} order by utime asc limit 1")
    AdsenseChannel getOneChannelIdByStateAndPubId(@Param("state") String state, @Param("pubId") String pubId);

    @Select("select " + Columns + " from " + Table + " where `state` = #{state} and pub_id = #{pubId} and utime < #{uTime} order by utime asc limit 1")
    AdsenseChannel getOneChannelIdByStateAndPubIdAndTime(@Param("state") String state, @Param("pubId") String pubId, @Param("uTime") String uTime);

    @Select("select count(1) from " + Table + " where `channel_id` = #{channelId} and pub_id = #{pubId}")
    long countByPubIdAndChannelId(@Param("channelId") String channelId, @Param("pubId") String pubId);

    @Update("update " + Table + " set state = #{state} where `channel_id` = #{channelId}")
    int updateByChannelId(@Param("channelId") String channelId, @Param("state") String state);

    @Update("update " + Table + " set state = #{state} where `channel_id` = #{channelId} and state != #{state}")
    int updateByChannelIdAndState(@Param("channelId") String channelId, @Param("state") String state);

}
