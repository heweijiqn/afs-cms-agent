package com.afs.publicapi.dao;

import com.afs.publicapi.dao.model.Fd;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FdMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`feedback`";
    String Columns = "`id`, `site`, `rate`, `locale`, `value`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table)
    List<Fd> getAll();

    @Insert(" insert into " + Table + " (`site`, `rate`, `locale`, `value`) values (#{site}, #{rate}, #{locale}, #{value})")
    int add(Fd fd);

    @Select("select " + Columns + " from " + Table + " where site = #{site} order by id desc limit #{offset}, #{size}")
    List<Fd> getList(@Param("site") String site, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(*) from " + Table + " where site = #{site}")
    int count(@Param("site") String site);
}