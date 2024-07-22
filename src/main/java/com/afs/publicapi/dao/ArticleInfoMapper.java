package com.afs.publicapi.dao;

import com.afs.publicapi.dao.model.ArticleInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ArticleInfoMapper {

    int INSIDE_SHOW = 1;
    int INSIDE_HIDE = 0;

    String Table = "`article_info`";
    String Columns = "`id`, `article_id`, `title`, `terms`, `category`, `strategy`, `img_url`, `first_part`, `text_list`, `lang`, `state`, `other_info`, `online`, `inside_show`, `status`, `site`, `ctime`, `utime`";

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"articleIdList != null and articleIdList.size > 0\">" +
            "     and article_id in (<foreach collection='articleIdList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            "</script>")
    List<ArticleInfo> getListByArticleId(@Param("articleIdList") List<String> articleIdList);

    @Select("select article_id from " + Table + " where state = #{state}")
    List<String> getAllArticleIdByState(@Param("state") String state);

    @Select("select " + Columns + " from " + Table + " where article_id = #{articleId}")
    ArticleInfo getByArticleId(@Param("articleId") String articleId);

    @Select("select distinct category from " + Table + " where site =#{site} and state = #{state} and inside_show = #{show}")
    List<String> getByArticleCategory(@Param("site") String site, @Param("show") Integer show, @Param("state") String state);

    @Select("select " + Columns + " from " + Table + " where inside_show = #{show} and site =#{site} and state = #{state} and category like concat(#{categoryPre}, '%')")
    List<ArticleInfo> getByCategoryAndInsideShow(@Param("site") String site, @Param("categoryPre") String categoryPre, @Param("show") Integer show, @Param("state") String state);

    @Select("select " + Columns + " from " + Table + " where title like concat('%', #{keyword}, '%') and inside_show = #{show} and site =#{site} and state = #{state} limit #{size}")
    List<ArticleInfo> getByKeywordFromTitle(@Param("site") String site, @Param("keyword") String keyword, @Param("show") Integer show, @Param("state") String state, @Param("size") Integer size);

    @Select("select " + Columns + " from " + Table + " where first_part like concat('%', #{keyword}, '%') and inside_show = #{show} and state = #{state} and site =#{site} limit #{size}")
    List<ArticleInfo> getByKeywordFromFirstP(@Param("site") String site, @Param("keyword") String keyword, @Param("show") Integer show, @Param("state") String state, @Param("size") Integer size);

    @Select("select " + Columns + " from " + Table + " where text_list like concat('%', #{keyword}, '%') and inside_show = #{show} and site =#{site} and state = #{state} limit #{size}")
    List<ArticleInfo> getByKeywordFromText(@Param("site") String site, @Param("keyword") String keyword, @Param("show") Integer show, @Param("state") String state, @Param("size") Integer size);

    @Select("select " + Columns + " from " + Table + " where inside_show = #{show} and site =#{site} and state = #{state} order by id desc limit #{size}")
    List<ArticleInfo> getByIdDesc(@Param("site") String site, @Param("size") Integer size, @Param("show") Integer show, @Param("state") String state);

}