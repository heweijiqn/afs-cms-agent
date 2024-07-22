package com.afs.publicapi.dao;

import com.afs.publicapi.dao.model.ConfigItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ConfigMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`config_item`";
    String Columns = "`id`, `name`, `value`, `desc`, `site`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table + " where name = #{name} and site =#{site}")
    ConfigItem getConfig(@Param("site") String site, @Param("name") String name);

    @Select("select " + Columns + " from " + Table)
    List<ConfigItem> getConfigList();
}