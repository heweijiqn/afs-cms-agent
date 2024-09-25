package com.afs.publicapi.controller;

import com.afs.publicapi.controller.filter.NoAuth;
import com.afs.publicapi.service.FeishuReportService;
import com.afs.publicapi.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/service")
@Slf4j
public class HealthController {

    public long mErrorLength = 0;

    List<String> mExList = new ArrayList<String>() {
        {
        }
    };

    @Value("${env}")
    String env;
    @Value("${spring.log.dir}")
    String logDir;

    @Autowired
    FeishuReportService feishuReportService;

    @NoAuth
    @RequestMapping("/")
    ResponseUtils.Result healthRootPath() {
        Map<String, String> map = new HashMap<>();
        map.put("root path", "ok");
        return ResponseUtils.result(map);
    }

    @NoAuth
    @RequestMapping("/health")
    ResponseUtils.Result health() {
        Map<String, String> map = new HashMap<>();
        map.put("api", "ok");
        return ResponseUtils.result(map);
    }

    @NoAuth
    @PostMapping("/healthX")
    ResponseUtils.Result healthX(@RequestBody Map<String, Object> mapRequest) {
        log.info("healthX mapRequest={}", mapRequest);
        Map<String, String> map = new HashMap<>();
        map.put("api", "ok");
        return ResponseUtils.result(map);
    }

    @Async
    @Scheduled(fixedRate = 33 * 60 * 1000, initialDelay = 60 * 1000)
    private void checkErrorLog() {
        if (!"prod".equals(env)) {
            return;
        }
        String logDirLocal = logDir + "/app.log";
        log.info("checkErrorLog logDir={}", logDirLocal);
        try {
            RandomAccessFile randomFile = new RandomAccessFile(logDirLocal, "r");
            long fileLength = randomFile.length();
            randomFile.seek(mErrorLength != 0 ? mErrorLength : (fileLength > 5 * 1024 * 1024 ? fileLength - 5 * 1024 * 1024 : 0));
            mErrorLength = fileLength;

            CircularFifoQueue<String> queue = new CircularFifoQueue<>(10);
            String tmp;
            while ((tmp = randomFile.readLine()) != null) {
                if (tmp.startsWith("[ERROR") && !isExList(tmp)) {
                    queue.add(tmp + "\n");
                }
            }
            if (!CollectionUtils.isEmpty(queue)) {
                log.debug("health report msg");
                feishuReportService.reportUserAlert(env + "\n" + "afs-api" + "\n" + queue.toString(), FeishuReportService.mApiErrorAlertUrl);
            }
        } catch (Throwable e) {
            log.error("checkErrorLog error.", e);
        }
    }

    private boolean isExList(String errorStr) {
        for (String t : mExList) {
            if (errorStr.contains(t)) {
                return true;
            }
        }
        return false;
    }
}
