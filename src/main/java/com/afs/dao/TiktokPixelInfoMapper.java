package com.afs.dao;

import com.afs.dao.model.TiktokPixelInfo;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TiktokPixelInfoMapper {

    String Table = "`tiktok_pixel_info`";
    String Columns = "`pixel_id`, `pixel_code`";

    @Insert("insert into " + Table + " (`pixel_id`, `pixel_code`) values (#{pixelId}, #{pixelCode}) on duplicate key update pixelId = #{pixelId}")
    int add(TiktokPixelInfo tiktokPixelInfo);

    @Select(" select pixel_id from " + Table + " where `pixel_code` = #{pixelCode}")
    String getPixelIdByCode(@Param("pixelCode") String pixelCode);

    @Select("select " + Columns + " from " + Table + " order by pixel_code")
    List<TiktokPixelInfo> list();
}
