package com.afs.controller;

import com.afs.dao.UserMapper;
import com.afs.dao.model.User;
import com.afs.utils.Consts;
import com.afs.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/cms/dir")
public class DirController {

    static Map<String, String> superUserMap = new LinkedHashMap<String, String>() {
        {
            put("文章管理", "article");
            put("语言管理", "lang");
            put("分类管理", "category");
            put("配置管理", "config");
            put("埋点管理", "track");
            put("活动管理", "campaign");
            put("活动规则", "campaignRule");
            put("素材管理", "material");
            put("素材管理分类", "materialCategory");
            put("文案管理", "adText");
            put("反馈管理", "feedback");
            put("数据管理", "statistic");
            put("投放账号管理", "adAccount");
            put("AFS账户管理", "afsAccount");
            put("AFS变现账户管理", "adsenseAccount");
            put("Channel管理", "adsenseChannel");
        }
    };

    static Map<String, String> commonUserMap = new LinkedHashMap<String, String>() {
        {
            put("文章管理", "article");
            put("活动管理", "campaign");
            put("数据管理", "statistic");
        }
    };

    static Map<String, String> showMaterialMap = new LinkedHashMap<String, String>() {
        {
            put("素材管理", "material");
            put("活动规则", "campaignRule");
            put("文案管理", "adText");
        }
    };

    @Autowired
    UserMapper userMapper;

    @GetMapping("/list")
    public ResponseUtils.Result getList(@RequestHeader(Consts.HeaderUserName) String userName) {
        User user = userMapper.get(userName);
        log.info("getList0 user={}", user);
        List<Map> list = new ArrayList<>();
        Map<String, String> tMap = new HashMap<>();
        if (user.root == User.RootUser) {
            tMap.putAll(superUserMap);
        }
        log.info("getList1 tMap={}", tMap);
        if (user.root == User.CommonUser) {
            tMap.putAll(commonUserMap);
            log.info("getList1 tMap={}", tMap);
            if (user.showMaterial) {
                log.info("getList3 tMap={}", tMap);
                tMap.putAll(showMaterialMap);
            }
        }
        log.info("getList4 tMap={}", tMap);
        for (String key : tMap.keySet()) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("label", key);
            map.put("value", tMap.get(key));
            list.add(map);
        }
        return ResponseUtils.result(list);
    }
}
