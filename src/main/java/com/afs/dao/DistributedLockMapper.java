package com.afs.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface DistributedLockMapper {

    @Insert(" insert ignore into distributed_lock(lock_key) values(#{lockKey})")
    void insertIgnore(@Param("lockKey") String lockKey);

    @Update("update distributed_lock set status = 1, expire_time = #{expireAt}, lock_value=#{lockValue} where lock_key = #{lockKey} and (status = 0 or expire_time < now())")
    int tryLock(@Param("lockKey") String lockKey, @Param("lockValue") String lockValue, @Param("expireAt") String expireAt);

    @Update("update distributed_lock set status = 0, expire_time = now(), lock_value = null where lock_key = #{lockKey} and lock_value=#{lockValue}")
    int releaseLock(@Param("lockKey") String lockKey, @Param("lockValue") String lockValue);

    @Delete(" delete from distributed_lock where lock_key = #{lockKey} and lock_value=#{lockValue}")
    int deleteLock(@Param("lockKey") String lockKey, @Param("lockValue") String lockValue);

}
