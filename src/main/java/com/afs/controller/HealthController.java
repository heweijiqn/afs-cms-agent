package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.dao.AdResourceMapper;
import com.afs.dao.TaboolaDataMapper;
import com.afs.dao.TiktokDataMapper;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.TaboolaData;
import com.afs.dao.model.TiktokData;
import com.afs.service.DistributeLockService;
import com.afs.service.FeishuReportService;
import com.afs.service.OpDailyReportService;
import com.afs.utils.DateUtils;
import com.afs.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.io.RandomAccessFile;
import java.util.*;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping
public class HealthController {

    public long mErrorLength = 0;

    List<String> mExList = new ArrayList<String>() {
        {
            add("7450330129370480656");
            add("7450330115269181441");
            add("7499009145488572434");
        }
    };

    @Value("${env}")
    String env;
    @Value("${spring.log.dir}")
    String logDir;
    @Autowired
    FeishuReportService feishuReportService;
    @Autowired
    DistributeLockService distributeLockService;
    @Autowired
    TiktokDataMapper tiktokDataMapper;
    @Autowired
    TaboolaDataMapper taboolaDataMapper;
    @Autowired
    OpDailyReportService opDailyReportService;
    @Autowired(required = false)
    LocalMaintenanceController localMaintenance;
    @Autowired
    AdResourceMapper adResourceMapper;

    @NoAuth
    @RequestMapping("/")
    ResponseUtils.Result rootPath() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put(env + "-cms", "ok");
        return ResponseUtils.result(map);
    }

    @NoAuth
    @RequestMapping("/health")
    ResponseUtils.Result health() {
        Map<String, String> map = new HashMap<>();
        map.put(env + "-cms", "ok");
        return ResponseUtils.result(map);
    }

    @NoAuth
    @PostMapping("/healthX")
    ResponseUtils.Result healthX(@RequestBody Map<String, Object> mapRequest) throws Exception {
        log.info("healthX mapRequest={}", mapRequest);
        Map<String, String> map = new HashMap<>();
        map.put(env + "cms", "ok");
        return ResponseUtils.result(map);
    }

    @com.afs.controller.filter.RootAuth
    @GetMapping("/daily/report")
    ResponseUtils.Result exeDailyReport(@RequestParam(required = false) String date) {
        if (localMaintenance != null) return ResponseUtils.result(localMaintenance.daily(date));
        log.info("exeDailyReport start");
        opDailyReportService.dailyReportData();
        Map<String, String> map = new HashMap<>();
        map.put(env + "cms", "ok");
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
                feishuReportService.reportUserAlert(env + "\n" + "afs-cms" + "\n" + queue.toString(), FeishuReportService.mErrorAlertUrl);
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

    @Async
    @Scheduled(cron = "0 0 20 * * ?")
    public void dailyCheck() {
        if (!env.equals("prod")) {
            return;
        }
        boolean lock = distributeLockService.tryLock("dailyCheck", "dailyCheck_value", 10 * 60 * 60 * 1000L);
        if (!lock) {
            return;
        }
        try {
            String dateStr = DateUtils.getFormatDayFromToday(-1);
            log.debug("dailyCheck, dateStr={}", dateStr);
            List<TiktokData> tiktokDataList = tiktokDataMapper.getNoChannelIdByDate(dateStr);
            log.debug("dailyCheck, tiktokDataList={}", tiktokDataList);
            StringBuffer sb = new StringBuffer();
            for (TiktokData tiktokData : tiktokDataList) {
                sb.append("Tiktok. Campaign cannot find channelId" + " : " + tiktokData.getDate() + ", " +
                        tiktokData.getCampaignId() + ", " + tiktokData.getCampaignName()).append("\n");
            }

            List<TaboolaData> tbDataList = taboolaDataMapper.getNoChannelIdByDate(dateStr);
            log.debug("dailyCheck, tbDataList={}", tbDataList);

            for (TaboolaData taboolaData : tbDataList) {
                sb.append("Taboola. Campaign cannot find channelId" + " : " + taboolaData.getDate() + ", " +
                        taboolaData.getCampaignId() + ", " + taboolaData.getCampaignName()).append("\n");
            }

            List<Map<String, Object>> invalidAdResourceList = adResourceMapper.getInvalidAdResource(Arrays.asList(AdResource.PASS));//todo:opt
            log.info("dailyCheck, invalidAdResourceList={}", invalidAdResourceList);
            for (Map<String, Object> map : invalidAdResourceList) {
                sb.append("AdResource. Invalid AdResource" + " : " + map.get("channel_id") + ", " +
                        map.get("state") + ", " + map.get("num")).append("\n");
            }
            feishuReportService.reportUserAlert("数据检测日报:\n" + sb.toString(), FeishuReportService.mErrorAlertUrl);
            log.info("dailyCheck end, sb={}", sb);
        } catch (Exception e) {
            log.error("dailyCheck error.", e);
        }
    }
}
