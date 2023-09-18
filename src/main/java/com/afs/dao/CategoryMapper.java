package com.afs.dao;

import com.afs.dao.model.CategoryInfo;
import com.afs.dao.model.LangInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CategoryMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`category`";
    String Columns = "`id`, `value`, `label`, `site`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table)
    List<CategoryInfo> getAll();

    @Select("select " + Columns + " from " + Table + " where site = #{site}")
    List<CategoryInfo> getBySite(@Param("site") String site);

    @Select("select " + Columns + " from " + Table)
    List<CategoryInfo> get();

    @Insert(" insert into " + Table + " (`value`, `label`, `site`) values (#{value}, #{label}, #{site}) on duplicate key update site = #{site}")
    int add(CategoryInfo categoryInfo);

}