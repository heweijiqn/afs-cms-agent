package com.afs.dao;

import com.afs.dao.model.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface UserMapper {
	String Table = "`user`";
	String Columns = "`name`, `password`, `partner`, `root`, `status`, `desc`, `auto_article`, `auto_campaign`, `show_material`, `site_list`, `ad_account_list`, " +
			"`display_name` AS displayName, `email`, `phone`, `department`, `job_title` AS jobTitle, `timezone`, `avatar_color` AS avatarColor, `last_login_at` AS lastLoginAt";
	String FullColumns = "`id`," + Columns + ", `ctime`, `utime`";

	@Select("SELECT " + FullColumns + " FROM " + Table + " WHERE `name`=#{name}")
	User get(@Param("name") String name);

	@Select("select count(*) from " + Table)
	int countUser();

	@Select("select count(*) from " + Table)
	int countUserByStatus();

	@Select(" select " + FullColumns + " from " + Table + " where `root` = #{root}")
	List<User> getUserByRoot(@Param("root") int root);

	@Select(" select " + FullColumns + " from " + Table + "  where `root` = #{root} order by utime desc limit #{offset}, #{size}")
	List<User> getList(@Param("root") int root, @Param("offset") int offset, @Param("size") int size);

	@Select(" select count(*) from " + Table + "  where `root` = #{root}")
	int countUserByRoot(@Param("root") int root);

	@Select(" select " + Columns + " from " + Table + " where name like #{name} limit #{size}")
	List<User> search(@Param("name") String name, @Param("status") int status, @Param("size") int size);

	@Insert("insert into " + Table + " (`name`, `password`, `root`, `status`, `desc`, `auto_article`, `auto_campaign`, `show_material`, `site_list`, `ad_account_list`, `partner`)" +
			" values(#{name}, #{password}, #{root}, #{status}, #{desc}, #{autoArticle}, #{autoCampaign}, #{showMaterial}, #{siteList}, #{adAccountList}, #{partner})")
	int add(User user);

	@Update("<script>" +
			" update " + Table + " " +
			" <set>" +
			"   <if test=\"desc != null\">" +
			"     `desc` = #{desc}," +
			"   </if>" +
			"   <if test=\"autoArticle != null\">" +
			"     `auto_article` = #{autoArticle}," +
			"   </if>" +
			"   <if test=\"autoCampaign != null\">" +
			"     `auto_campaign` = #{autoCampaign}," +
			"   </if>" +
			"   <if test=\"showMaterial != null\">" +
			"     `show_material` = #{showMaterial}," +
			"   </if>" +
			"   <if test=\"siteList != null\">" +
			"     `site_list` = #{siteList}," +
			"   </if>" +
			"   <if test=\"adAccountList != null\">" +
			"     `ad_account_list` = #{adAccountList}," +
			"   </if>" +
			" </set>" +
			" where name = #{name}" +
			"</script>")
	int updateUser(User user);

	@Update("UPDATE " + Table + " SET `display_name` = #{displayName}, `email` = #{email}, `phone` = #{phone}, " +
			"`department` = #{department}, `job_title` = #{jobTitle}, `timezone` = #{timezone}, " +
			"`avatar_color` = #{avatarColor}, `desc` = #{desc}, `utime` = CURRENT_TIMESTAMP WHERE `name` = #{name}")
	int updateProfile(User user);

	@Update("UPDATE " + Table + " SET `last_login_at` = CURRENT_TIMESTAMP WHERE `name` = #{name}")
	int touchLastLogin(@Param("name") String name);

	@Update("UPDATE " + Table + " SET `password` = #{password}, `utime` = CURRENT_TIMESTAMP WHERE `name` = #{name}")
	int updatePassword(@Param("name") String name, @Param("password") String password);

	@Insert("update " + Table + " set `status` = #{status} where `name` = #{name}")
	int updateStatus(@Param("name") String name, @Param("status") int status);

	@Delete("delete from " + Table + " where `name` = #{name}")
	int delete(@Param("name") String name);
}
