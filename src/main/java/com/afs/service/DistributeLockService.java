package com.afs.service;

import com.afs.dao.DistributedLockMapper;
import com.afs.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DistributeLockService {

    @Autowired
    DistributedLockMapper distributedLockMapper;

    public boolean tryLock(String lockKey, String lockVal, long expiryTime) {
        distributedLockMapper.insertIgnore(lockKey);
        String date = DateUtils.formatDate(System.currentTimeMillis()+expiryTime);
        return distributedLockMapper.tryLock(lockKey, lockVal, date)>0;
    }

    public boolean tryUnLock(String lockKey, String lockVal) {
        try {
            return distributedLockMapper.releaseLock(lockKey, lockVal)>0;
        } catch (Exception e) {
            log.error("Failed to unlock redis lock, lockKey={}, lockVal={}", lockKey, lockVal, e);
            return false;
        }
    }

    public boolean delLock(String lockKey, String lockVal) {
        try {
            return distributedLockMapper.deleteLock(lockKey, lockVal)>0;
        } catch (Exception e) {
            log.error("Failed to unlock redis lock, lockKey={}, lockVal={}", lockKey, lockVal, e);
            return false;
        }
    }

}
