package com.afs.publicapi.controller;

import com.afs.publicapi.controller.filter.NoAuth;
import com.afs.publicapi.dao.CategoryMapper;
import com.afs.publicapi.dao.ReportDataMapper;
import com.afs.publicapi.dao.model.ReportData;
import com.afs.publicapi.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequestMapping("/api/report/config")
public class ReportDataController {

    @Autowired
    ReportDataMapper reportDataMapper;

    @NoAuth
    @GetMapping("/detail")
    public ResponseUtils.Result getList(@RequestHeader(value = "Current-Site") String site, @RequestParam String channel) {
        ReportData reportData = reportDataMapper.getByChannel(site, channel);
        return ResponseUtils.result(reportData);
    }
}
