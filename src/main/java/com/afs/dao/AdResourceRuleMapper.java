package com.afs.dao;

import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.AdResourceRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface AdResourceRuleMapper {

    int VALID = 1;
    int INVALID = 0;

    String Table = "`ad_resource_rule`";
    String Columns = "`id`, `name`, `type`, `action_cycle`, `action_time`, `creator`, `rule`, `budget_change`, `status`, `ctime`, `utime`";

    @Insert("insert into " + Table + " (`name`, `type`, `action_cycle`, `action_time`, `creator`, `rule`, `budget_change`) values (#{name}, #{type}, #{actionCycle}, #{actionTime}, #{creator}, #{rule}, #{budgetChange})")
    int add(AdResourceRule adResourceRule);

    @Update("<script>" +
            " update " + Table + " " +
            " <set>" +
            "   <if test=\"name != null\">" +
            "     name = #{name}," +
            "   </if>" +
            "   <if test=\"type != null\">" +
            "     type = #{type}," +
            "   </if>" +
            "   <if test=\"actionCycle != null\">" +
            "     action_cycle = #{actionCycle}," +
            "   </if>" +
            "   <if test=\"actionTime != null\">" +
            "     action_time = #{actionTime}," +
            "   </if>" +
            "   <if test=\"rule != null\">" +
            "     rule = #{rule}," +
            "   </if>" +
            "   <if test=\"budgetChange != null\">" +
            "     budget_change = #{budgetChange}," +
            "   </if>" +
            " </set>" +
            " where id = #{id}" +
            "</script>")
    int edit(AdResourceRule adResourceRule);

    @Select("select count(*) from " + Table)
    long count();

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"name != null and name != ''\">" +
            "     and name like concat('%', #{name}, '%')" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<AdResourceRule> getListByQuery(@Param("name") String name, @Param("offset") int offset, @Param("size") int size);

    @Select("select " + Columns + " from " + Table + " where name = #{name}")
    AdResourceRule getByName(@Param("name") String name);

    @Select("select " + Columns + " from " + Table + " where id = #{id}")
    AdResourceRule getById(@Param("id") Long id);

    @Select("<script>" +
            "select count(1) from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"name != null and name != ''\">" +
            "     and name like concat('%', #{name}, '%')" +
            "   </if>" +
            "</where>" +
            "</script>")
    int countByQuery(@Param("name") String name);

    @Delete("delete from " + Table + " where `id` = #{id}")
    int delById(@Param("id") Long id);

}