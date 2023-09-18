package com.afs.dao;

import com.afs.dao.model.AdResource;
import com.afs.dao.model.ArticleInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ArticleInfoMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`article_info`";
    String Columns = "`id`, `article_id`, `title`, `terms`, `category`, `strategy`, `img_url`, `first_part`, `text_list`, `lang`, `other_info`, `online`, `inside_show`, `site`, `state`, `partner`, `status`, `online_time`, `ctime`, `utime`";

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"articleId != null and articleId != ''\">" +
            "     and article_id like concat('%', #{articleId}, '%')" +
            "   </if>" +
            "   <if test=\"articleTitle != null and articleTitle != ''\">" +
            "     and title like concat('%', #{articleTitle}, '%')" +
            "   </if>" +
            "   <if test=\"lang != null\">" +
            "     and lang = #{lang}" +
            "   </if>" +
            "   <if test=\"state != null and state != ''\">" +
            "     and state = #{state}" +
            "   </if>" +
            "   <if test=\"site != null\">" +
            "     and site = #{site}" +
            "   </if>" +
            "   <if test=\"partner != null and partner != ''\">" +
            "     and partner = #{partner}" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<ArticleInfo> getListByQuery(@Param("partner") String partner, @Param("site") String site, @Param("articleId") String articleId, @Param("articleTitle") String articleTitle,
                                     @Param("lang") String lang, @Param("state") String state,
                                     @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"articleId != null and articleId != ''\">" +
            "     and article_id like concat('%', #{articleId}, '%')" +
            "   </if>" +
            "   <if test=\"articleTitle != null and articleTitle != ''\">" +
            "     and title like concat('%', #{articleTitle}, '%')" +
            "   </if>" +
            "   <if test=\"lang != null\">" +
            "     and lang = #{lang}" +
            "   </if>" +
            "   <if test=\"state != null and state != ''\">" +
            "     and state = #{state}" +
            "   </if>" +
            "   <if test=\"site != null\">" +
            "     and site = #{site}" +
            "   </if>" +
            "   <if test=\"partner != null and partner != ''\">" +
            "     and partner = #{partner}" +
            "   </if>" +
            "</where>" +
            "</script>")
    int countByQuery(@Param("partner") String partner, @Param("site") String site, @Param("articleId") String articleId, @Param("articleTitle") String articleTitle,
                     @Param("lang") String lang, @Param("state") String state);

    @Insert(" insert into " + Table + "" +
            " (`article_id`, `title`, `terms`, `category`, `strategy`, `site`, `img_url`, `first_part`, `text_list`, `lang`, `online`, `inside_show`, `other_info`, `state`, `partner`, `online_time`)" +
            " values (#{articleId}, #{title}, #{terms}, #{category}, #{strategy}, #{site}, #{imgUrl}, #{firstPart}, #{textList}, #{lang}, #{online}, #{insideShow}, #{otherInfo}, #{state}, #{partner}, #{onlineTime}) " +
            " on duplicate key update status = 1")
    int add(ArticleInfo articleInfo);

    @Update("update " + Table + " set `online` = #{online} where `article_id` = #{articleId}")
    int updateOnOff(@Param("online") Integer online, @Param("articleId") String articleId);

    @Update("<script>" +
            " update " + Table + " " +
            " <set>" +
            "   <if test=\"title != null\">" +
            "     title = #{title}," +
            "   </if>" +
            "   <if test=\"imgUrl != null\">" +
            "     img_url = #{imgUrl}," +
            "   </if>" +
            "   <if test=\"insideShow != null\">" +
            "     inside_show = #{insideShow}," +
            "   </if>" +
            "   <if test=\"state != null\">" +
            "     state = #{state}," +
            "   </if>" +
            "   <if test=\"terms != null\">" +
            "     terms = #{terms}," +
            "   </if>" +
            "   <if test=\"category != null\">" +
            "     category = #{category}," +
            "   </if>" +
            "   <if test=\"textList != null\">" +
            "     text_list = #{textList}," +
            "   </if>" +
            "   <if test=\"lang != null\">" +
            "     lang = #{lang}," +
            "   </if>" +
            "   <if test=\"firstPart != null\">" +
            "     first_part = #{firstPart}," +
            "   </if>" +
            "   <if test=\"otherInfo != null\">" +
            "     other_info = #{otherInfo}," +
            "   </if>" +
            " </set>" +
            " where article_id = #{articleId}" +
            "</script>")
    int updateArticleInfo(ArticleInfo adInfo);

    @Select("select " + Columns + " from " + Table + " where article_id = #{articleId}")
    ArticleInfo getByArticleId(@Param("articleId") String articleId);

    @Delete("delete from " + Table + " where `article_id` = #{articleId}")
    int delete(@Param("articleId") String articleId);

    @Delete("delete from " + Table + " where `article_id` = #{articleId} and state = #{state}")
    int deleteByArticleIdAndState(@Param("articleId") String articleId, @Param("state") String state);

    @Update("update " + Table + " set `state` = #{state} where `article_id` = #{articleId}")
    int updateState(@Param("state") String state, @Param("articleId") String articleId);

    @Update("update " + Table + " set `state` = #{state}, `site` = #{site}, `online_time` = CURRENT_TIMESTAMP where `article_id` = #{articleId}")
    int auditArticlePass(@Param("articleId") String articleId, @Param("state") String state, @Param("site") String site);

    @Update("update " + Table + " set `state` = #{state}, other_info = #{otherInfo} where `article_id` = #{articleId}")
    int auditArticleReject(@Param("articleId") String articleId, @Param("state") String state, @Param("otherInfo") String otherInfo);

    @Update("update " + Table + " set state = #{state} where `article_id` = #{articleId}")
    int archive(@Param("state") String state, @Param("articleId") String articleId);

    @Select("select " + Columns + " from " + Table + " where site =#{site}")
    List<ArticleInfo> getBySite(@Param("site") String site);

    @Update("update " + Table + " set `other_info` = #{otherInfo} where `article_id` = #{articleId}")
    int updateOtherInfo(@Param("otherInfo") String otherInfo, @Param("articleId") String articleId);

    @Select("select article_id, title from " + Table + " where `article_id` like concat('%', #{articleId}, '%')")
    List<ArticleInfo> searchByArticleId(@Param("articleId") String articleId);

    @Select("select `site` from " + Table + " where article_id = #{articleId}")
    String getSiteByArticleId(@Param("articleId") String articleId);

}