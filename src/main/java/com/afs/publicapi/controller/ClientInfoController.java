package com.afs.publicapi.controller;

import com.afs.publicapi.controller.filter.NoAuth;
import com.afs.publicapi.dao.FdMapper;
import com.afs.publicapi.dao.IpInfoMapper;
import com.afs.publicapi.dao.model.Fd;
import com.afs.publicapi.dao.model.IpInfo;
import com.afs.publicapi.dao.model.ReportData;
import com.afs.publicapi.utils.DateUtils;
import com.afs.publicapi.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequestMapping("/api")
public class ClientInfoController {

    @Autowired
    IpInfoMapper ipInfoMapper;
    @Autowired
    com.afs.service.LocalApiEventStore localApiEventStore;

    @Data
    public static class AddBody {
        @NotBlank
        String ip;
    }

    @NoAuth
    @PostMapping("/ip/report")
    public ResponseUtils.Result add(@RequestHeader(value = "Current-Site") String site, @RequestBody @Valid AddBody body) {
        log.info("add fd site={}", site);
        IpInfo info = toClientInfo(body, site);
        int n = ipInfoMapper.add(info);
        if (n > 0) {
            localApiEventStore.record("ip", site, java.util.Map.of("ip", body.getIp()));
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.result("更新失败");
        }
    }

    private IpInfo toClientInfo(AddBody body, String site) {
        IpInfo info = new IpInfo();
        info.setSite(site);
        info.setIp(body.getIp());
        return info;
    }

    @NoAuth
    @GetMapping("/ip/get")
    public ResponseUtils.Result getList(@RequestHeader(value = "Current-Site") String site, @RequestParam String ip) {
        IpInfo ipInfo = ipInfoMapper.getInfoByIpAndSite(site, ip);
        Map<String, Boolean> map = new HashMap<>();
        if (ipInfo == null || ipInfo.getUtime().getTime() < DateUtils.getTodayStartTimestamp()) {
            map.put("todayReport", false);
            if (ipInfo != null) {
                ipInfoMapper.delete(site, ip);
            }
        } else {
            map.put("todayReport", true);
        }
        return ResponseUtils.result(map);
    }
}
