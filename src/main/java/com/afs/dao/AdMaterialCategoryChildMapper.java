package com.afs.dao;

import com.afs.dao.model.AdMaterialCategoryChildInfo;
import com.afs.dao.model.AdMaterialCategoryInfo;
import com.afs.dao.model.CategoryChildInfo;
import com.afs.dao.model.CategoryInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


public interface AdMaterialCategoryChildMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ad_material_category_child`";
    String Columns = "`id`, `value`, `value_child`, `label_child`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table + " where value = #{value}")
    List<AdMaterialCategoryChildInfo> getChildByValue(@Param("value") String value);

    @Insert(" insert into " + Table + " (`value`, `value_child`, `label_child`) values (#{value}, #{valueChild}, #{labelChild}) on duplicate key update status = 1")
    int add(AdMaterialCategoryChildInfo categoryChildInfo);

}
