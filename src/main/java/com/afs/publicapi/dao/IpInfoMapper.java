package com.afs.publicapi.dao;

import com.afs.publicapi.dao.model.Fd;
import com.afs.publicapi.dao.model.IpInfo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface IpInfoMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ip_info`";
    String Columns = "`id`, `ip`, `site`, `ctime`, `utime`";

    @Select("select " + Columns + " from " + Table + "where ip = #{ip} and site = #{site}")
    IpInfo getInfoByIpAndSite(@Param("site") String site, @Param("ip") String ip);

    @Insert(" insert into " + Table + " (`ip`, `site`) values (#{ip}, #{site}) on duplicate key update status = 1")
    int add(IpInfo ipInfo);

    @Select("select " + Columns + " from " + Table + " where site = #{site} order by id desc limit #{offset}, #{size}")
    List<Fd> getList(@Param("site") String site, @Param("offset") int offset, @Param("size") int size);

    @Select("select count(*) from " + Table + " where site = #{site}")
    int count(@Param("site") String site);

    @Delete("delete from " + Table + " where ip = #{ip} and site = #{site}")
    int delete(@Param("site") String site, @Param("ip") String ip);
}