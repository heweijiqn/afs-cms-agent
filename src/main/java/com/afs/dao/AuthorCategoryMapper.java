package com.afs.dao;

import com.afs.dao.model.AuthorCategoryInfo;
import com.afs.dao.model.LangInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AuthorCategoryMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`author_category_map`";
    String Columns = "`id`, `category`, `author`, `site`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table)
    List<AuthorCategoryInfo> getAll();

    @Select("select " + Columns + " from " + Table + " where site = #{site}")
    List<AuthorCategoryInfo> getBySite(@Param("site") String site);

    @Select("select " + Columns + " from " + Table + " where category = #{category}")
    AuthorCategoryInfo getByCategory(@Param("category") String category);

    @Select("select " + Columns + " from " + Table)
    List<AuthorCategoryInfo> get();

    @Insert(" insert into " + Table + " (`value`, `label`, `site`) values (#{value}, #{label}, #{site})")
    int add(AuthorCategoryInfo info);

    @Update("update " + Table + " set `value` = #{value}, `label` = #{label} where `id` = #{id}")
    int updateLabelAndValue(@Param("value") String value, @Param("label") String label, @Param("id") Long id);
}