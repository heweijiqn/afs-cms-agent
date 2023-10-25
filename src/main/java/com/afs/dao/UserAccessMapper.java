package com.afs.dao;

import com.afs.dao.model.UserAccess;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserAccessMapper {
	String Table = "`user_access`";
	String Columns = "`user_name`, `app_id`, `access`";
	String FullColumns = "`id`," + Columns + ", `ctime`, `utime`";

	@Select(" select " + FullColumns + " from " + Table + " order by id asc limit #{offset}, #{size}")
	List<UserAccess> getList(@Param("offset") int offset, @Param("size") int size);

	@Select("select count(*) from " + Table)
	int countUser();

	@Select(" select " + FullColumns + " from " + Table + " where `user_name` = #{userName} ")
	List<UserAccess> getListByUserName(@Param("userName") String userName);

	@Insert("insert into " + Table + " (`user_name`, `app_id`, `access`) values(#{userName}, #{appId}, #{access})" +
			" on duplicate key update access = #{access}")
	int add(@Param("userName") String userName, @Param("appId") String appId, @Param("access") int access);

	@Delete("delete from " + Table + " where `user_name` = #{userName} and `app_id` = #{appId}")
	int delete(@Param("userName") String userName, @Param("appId") String appId);

}