package com.afs.controller;

import com.afs.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/cms/strategy")
public class StrategyController {

    @GetMapping("/list")
    public ResponseUtils.Result getList() {
        List<Map> list = new ArrayList<>();
        Map<String, String> map1 = new HashMap<>();
        map1.put("label", "复制");
        map1.put("value", "Copy");
        list.add(map1);

        Map<String, String> map2 = new HashMap<>();
        map2.put("label", "洗稿");
        map2.put("value", "Write");
        list.add(map2);
        return ResponseUtils.result(list);
    }
}
