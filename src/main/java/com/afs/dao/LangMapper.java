package com.afs.dao;

import com.afs.dao.model.LangInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface LangMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`lang`";
    String Columns = "`id`, `value`, `label`, `site`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table)
    List<LangInfo> getAll();

    @Select("select " + Columns + " from " + Table + " where site = #{site}")
    List<LangInfo> getBySite(@Param("site") String site);

    @Select("select " + Columns + " from " + Table)
    List<LangInfo> get();

    @Insert(" insert into " + Table + " (`value`, `label`, `site`) values (#{value}, #{label}, #{site})")
    int add(LangInfo langInfo);

    @Update("update " + Table + " set `value` = #{value}, `label` = #{label} where `id` = #{id}")
    int updateLabelAndValue(@Param("value") String value, @Param("label") String label, @Param("id") Long id);
}