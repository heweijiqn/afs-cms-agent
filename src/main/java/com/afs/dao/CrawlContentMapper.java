package com.afs.dao;

import com.afs.dao.model.CrawlContent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface CrawlContentMapper {
    String table = "crawl_content";
    String columns = "fid, content, source, online, status";
    String allColumns = "id, " + columns + ", ctime, utime";

    @Insert(" insert into " + table + " (`fid`, `content`, `source`, `online`) " +
            " values (#{fid}, #{content}, #{source}, #{online}) on duplicate key update `status`=1")
    int add(CrawlContent content);

    @Select(" select " + allColumns + " from " + table +
            " where `category` = #{category} and `country` = #{country} order by id desc " +
            " limit #{offset}, #{size}")
    List<CrawlContent> getListPage(@Param("offset") int offset, @Param("size") int size);

    @Select(" select " + allColumns + " from " + table + " where `fid` = #{fid}")
    CrawlContent getByFid(@Param("fid") String fid);

    @Select(" select fid from " + table + " where `status` = #{status}")
    List<String> getFidList(@Param("status") Integer status);

    @Select(" select fid from " + table + " where `status` = #{status} and source = #{source}")
    List<String> getFidListByStatusAndSource(@Param("status") Integer status, @Param("source") String source);

    @Select(" select fid from " + table + " where `status` = #{status} and source = #{source} limit #{size}")
    List<String> getFidListByPage(@Param("status") Integer status, @Param("source") String source, @Param("size") Integer size);

    @Select(" select fid from " + table + " where `category` = #{category} and `status` = #{status} and source = #{source} order by id desc limit #{size}")
    List<String> getFidListByCategory(@Param("category") String category, @Param("status") Integer status, @Param("source") String source, @Param("size") Integer size);

    @Select(" select fid from " + table + " where source = #{source} limit #{size}")
    List<String> getFidListBySourceAndSize(@Param("source") String source, @Param("size") Integer size);

    @Select(" select count(*) from " + table)
    int count();

    @Update("update " + table + " set status = #{status} where `fid` = #{fid}")
    int updateStatus(@Param("fid") String fid, @Param("status") Integer status);

    @Update("update " + table + " set category = #{category} where `fid` = #{fid}")
    int updateCategory(@Param("fid") String fid, @Param("category") String category);

    @Update("update " + table + " set content = #{content} where `fid` = #{fid}")
    int updateContentByFid(@Param("fid") String fid, @Param("content") String content);
}


