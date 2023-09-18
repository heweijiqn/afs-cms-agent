package com.afs.dao;

import com.afs.dao.model.CategoryChildInfo;
import com.afs.dao.model.CategoryInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CategoryChildMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`category_child`";
    String Columns = "`id`, `value`, `value_child`, `label_child`, `site`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table + " where value = #{value} and site = #{site}")
    List<CategoryChildInfo> getChildBySiteAndValue(@Param("site") String site, @Param("value") String value);

    @Select("select " + Columns + " from " + Table + " where value = #{value}")
    List<CategoryChildInfo> getChildByValue(@Param("value") String value);

    @Select("select " + Columns + " from " + Table + " where site = #{site}")
    List<CategoryChildInfo> getBySite(@Param("site") String site);

    @Insert(" insert into " + Table + " (`value`, `value_child`, `label_child`, `site`) values (#{value}, #{valueChild}, #{labelChild}, #{site}) on duplicate key update site = #{site}")
    int add(CategoryChildInfo categoryChildInfo);

}