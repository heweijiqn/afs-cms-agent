package com.afs.publicapi.service;

import com.afs.publicapi.dao.ConfigMapper;
import com.afs.publicapi.dao.model.ConfigItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ConfigItemService {

    @Autowired
    ConfigMapper configMapper;
    AtomicBoolean init = new AtomicBoolean(false);
    Map<String, String> configItemMap = new HashMap<>();

    @Async
    @Scheduled(fixedDelay = 2 * 60 * 1000)
    public void reload() {
        synchronized (init) {
            try {
                List<ConfigItem> list = configMapper.getConfigList();
                for (ConfigItem configItem : list) {
                    configItemMap.put(configItem.getName(), configItem.getValue());
                }
            } catch (Exception e) {
                log.error("failed to load configItemMap:{}", configItemMap, e);
            }
        }
    }

    public String getValue(String name) {
        return configItemMap.get(name);
    }
}
