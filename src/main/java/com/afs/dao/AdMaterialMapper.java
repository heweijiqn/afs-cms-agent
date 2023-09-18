package com.afs.dao;

import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.Material;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

public interface AdMaterialMapper {

    String Table = "`ad_material`";
    String Columns = "`id`, `ad_material_id`, `name`, `user_name`, `country`, `lang`, `type`, `dimension`, `ratio`, `category`, `template`, `cover`, `url`, `size`, `duration`, `status`, `ctime`, `utime`";

    @Select("<script>" +
            "select " + Columns + " from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"keyword != null and keyword != ''\">" +
            "     and name like concat('%', #{keyword}, '%')" +
            "   </if>" +
            "   <if test=\"category != null and category != ''\">" +
            "     and category = #{category}" +
            "   </if>" +
            "   <if test=\"ratioList != null and ratioList.size > 0\">" +
            "     and ratio in (<foreach collection='ratioList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"langList != null and langList.size > 0\">" +
            "     and lang in (<foreach collection='langList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"typeList != null and typeList.size > 0\">" +
            "     and type in (<foreach collection='typeList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"countryList != null and countryList.size > 0\">" +
            "     and (<foreach collection='countryList' item='e' separator=' or '>country like concat('%', #{e}, '%')</foreach>)" +
            "   </if>" +
            "   <if test=\"timeStart != null\">" +
            "     and `ctime` &gt;= #{timeStart}" +
            "   </if>" +
            "   <if test=\"timeEnd != null\">" +
            "     and `ctime` &lt;= #{timeEnd}" +
            "   </if>" +
            "   <if test=\"startDur != null\">" +
            "     and `duration` &gt;= #{startDur}" +
            "   </if>" +
            "   <if test=\"endDur != null\">" +
            "     and `duration` &lt;= #{endDur}" +
            "   </if>" +
            "</where>" +
            " order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<Material> getListByQuery(@Param("category") String category, @Param("countryList") List<String> countryList,
                                  @Param("startDur") Integer startDur, @Param("endDur") Integer endDur,
                                  @Param("timeStart") String timeStart, @Param("timeEnd") String timeEnd,
                                  @Param("ratioList") List<String> ratioList,
                                  @Param("langList") List<String> langList, @Param("typeList") List<String> typeList,
                                  @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(*) from " + Table +
            "<where>" +
            " status = 1" +
            "   <if test=\"keyword != null and keyword != ''\">" +
            "     and name like concat('%', #{keyword}, '%')" +
            "   </if>" +
            "   <if test=\"category != null and category != ''\">" +
            "     and category = #{category}" +
            "   </if>" +
            "   <if test=\"ratioList != null and ratioList.size > 0\">" +
            "     and ratio in (<foreach collection='ratioList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"langList != null and langList.size > 0\">" +
            "     and lang in (<foreach collection='langList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"typeList != null and typeList.size > 0\">" +
            "     and type in (<foreach collection='typeList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "   <if test=\"countryList != null and countryList.size > 0\">" +
            "     and (<foreach collection='countryList' item='e' separator=' or '>country like concat('%', #{e}, '%')</foreach>)" +
            "   </if>" +
            "   <if test=\"timeStart != null\">" +
            "     and `ctime` &gt;= #{timeStart}" +
            "   </if>" +
            "   <if test=\"timeEnd != null\">" +
            "     and `ctime` &lt;= #{timeEnd}" +
            "   </if>" +
            "   <if test=\"startDur != null\">" +
            "     and `duration` &gt;= #{startDur}" +
            "   </if>" +
            "   <if test=\"endDur != null\">" +
            "     and `duration` &lt;= #{endDur}" +
            "   </if>" +
            "</where>" +
            "</script>")
    int countByQuery(@Param("category") String category, @Param("countryList") List<String> countryList,
                     @Param("startDur") Integer startDur, @Param("endDur") Integer endDur,
                     @Param("timeStart") String timeStart, @Param("timeEnd") String timeEnd,
                     @Param("ratioList") List<String> ratioList,
                     @Param("langList") List<String> langList, @Param("typeList") List<String> typeList,
                     @Param("keyword") String keyword);


    @Insert("insert into " + Table + " (`ad_material_id`, `name`, `user_name`, `country`, `lang`, `type`, `dimension`, `ratio`, `category`, `template`, `cover`, `url`, `size`, `duration`)" +
            " values (#{adMaterialId}, #{name}, #{userName}, #{country}, #{lang}, #{type}, #{dimension}, #{ratio}, #{category}, #{template}, #{cover}, #{url}, #{size}, #{duration})")
    int add(Material material);

    @Select(" select " + Columns + " from " + Table + " where `ad_material_id` = #{adMaterialId}")
    Material getByAdMaterialId(@Param("adMaterialId") String adMaterialId);

    @Update("update " + Table + " set `name` = #{name} where `ad_material_id` = #{adMaterialId}")
    int updateName(@Param("name") String name, @Param("adMaterialId") String adMaterialId);

    @Update("<script>" +
            " update " + Table + " " +
            " <set>" +
            "   <if test=\"name != null\">" +
            "     name = #{name}," +
            "   </if>" +
            "   <if test=\"country != null\">" +
            "     country = #{country}," +
            "   </if>" +
            "   <if test=\"lang != null\">" +
            "     lang = #{lang}," +
            "   </if>" +
            "   <if test=\"type != null\">" +
            "     type = #{type}," +
            "   </if>" +
            "   <if test=\"category != null\">" +
            "     category = #{category}," +
            "   </if>" +
            "   <if test=\"dimension != null\">" +
            "     dimension = #{dimension}," +
            "   </if>" +
            "   <if test=\"ratio != null\">" +
            "     ratio = #{ratio}," +
            "   </if>" +
            "   <if test=\"template != null\">" +
            "     template = #{template}," +
            "   </if>" +
            "   <if test=\"cover != null\">" +
            "     cover = #{cover}," +
            "   </if>" +
            "   <if test=\"url != null\">" +
            "     url = #{url}," +
            "   </if>" +
            "   <if test=\"size != null\">" +
            "     size = #{size}," +
            "   </if>" +
            "   <if test=\"duration != null\">" +
            "     duration = #{duration}," +
            "   </if>" +
            " </set>" +
            " where id = #{id}" +
            "</script>")
    int update(Material material);

    @Update("<script>" +
            "update " + Table + " set status = 0 " +
            "<where>" +
            " status = 1" +
            "   <if test=\"idList != null and idList.size > 0\">" +
            "     and id in (<foreach collection='idList' item='e' separator=','>#{e}</foreach>)" +
            "   </if>" +
            "</where>" +
            "</script>")
    int deleteByIdList(@Param("idList") List<Long> idList);
}
