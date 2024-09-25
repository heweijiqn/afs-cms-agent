package com.afs.publicapi.controller;

import com.afs.publicapi.controller.filter.NoAuth;
import com.afs.publicapi.dao.ConfigMapper;
import com.afs.publicapi.dao.model.ConfigItem;
import com.afs.publicapi.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Autowired
    ConfigMapper configMapper;

    @NoAuth
    @GetMapping("/get")
    public ResponseUtils.Result get(@RequestHeader(value = "Current-Site") String site, @RequestParam String name) {
        ConfigItem configItem = configMapper.getConfig(site, name);
        if (configItem == null) return ResponseUtils.error(-1, "配置不存在");
        Map<String, Object> map = new HashMap<>();
        map.put("value", configItem.getValue());
        return ResponseUtils.result(map);
    }
}
