package com.afs.dao;

import com.afs.dao.model.AdMaterialCategoryInfo;
import com.afs.dao.model.CategoryInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AdMaterialCategoryMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ad_material_category`";
    String Columns = "`id`, `value`, `label`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table)
    List<AdMaterialCategoryInfo> getAll();

    @Select("select " + Columns + " from " + Table + " where site = #{site}")
    List<AdMaterialCategoryInfo> getBySite(@Param("site") String site);

    @Select("select " + Columns + " from " + Table)
    List<AdMaterialCategoryInfo> get();

    @Insert(" insert into " + Table + " (`value`, `label`) values (#{value}, #{label}) on duplicate key update status = 1")
    int add(AdMaterialCategoryInfo categoryInfo);

}
