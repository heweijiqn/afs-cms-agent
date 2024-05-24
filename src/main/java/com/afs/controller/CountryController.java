package com.afs.controller;

import com.afs.service.CountryService;
import com.afs.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@CrossOrigin(origins = "*")
@RequestMapping("/cms")
public class CountryController {

    @Autowired
    CountryService countryService;

    @Data
    public static class Region {
        public String regionCode;
        public String name;
    }

    @GetMapping("/region/list")
    public ResponseUtils.Result getRegionList() {
        Map<String, Object> resultMap = new HashMap<>();
        List<Region> list = new ArrayList<>();
        for (String key : countryService.getCodeCountryMap().keySet()) {
            Region r = new Region();
            r.setRegionCode(key);
            r.setName(countryService.getCodeCountryMap().get(key));
            list.add(r);
        }
        resultMap.put("list", list);
        return ResponseUtils.result(resultMap);
    }
}
