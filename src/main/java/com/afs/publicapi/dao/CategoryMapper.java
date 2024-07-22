package com.afs.publicapi.dao;

import com.afs.publicapi.dao.model.CategoryInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CategoryMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`category`";
    String Columns = "`id`, `value`, `label`,  `site`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table + " where site = #{site}")
    List<CategoryInfo> getBySite(@Param("site") String site);

}