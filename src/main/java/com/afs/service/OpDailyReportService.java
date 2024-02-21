package com.afs.service;

import com.afs.dao.*;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.TaboolaData;
import com.afs.dao.model.TiktokData;
import com.afs.dao.model.User;
import com.afs.utils.AppUtils;
import com.afs.utils.DateUtils;
import com.afs.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OpDailyReportService {

    @Value("${env}")
    String env;
    @Autowired
    AdsenseDataMapper adsenseDataMapper;
    @Autowired
    UserMapper userMapper;
    @Autowired
    AdResourceMapper adResourceMapper;
    @Autowired
    FeishuReportService feishuReportService;
    @Autowired
    DistributeLockService distributeLockService;
    @Autowired
    AdResourceService adResourceService;

    @Async
    @Scheduled(cron = "0 0 9 * * ?")
    public void dailyReportData() {
        if (!env.equals("prod")) {
            return;
        }
        log.info("dailyReportData start");
        boolean lock = distributeLockService.tryLock("dailyReportData", "dailyReportData_value", 60 * 60 * 1000L);
        log.info("dailyReportData lock={}", lock);
        if (!lock) {
            return;
        }
        List<User> userList = userMapper.getUserByRoot(User.CommonUser);

        String dateStr = DateUtils.getFormatDayFromToday(-1);
        TreeMap<Double, Object> resultMap = new TreeMap<>();
        for (User user : userList) {
            List<String> channelIdList = getChannelId(user.getPartner());
            log.debug("dailyReportData, dateStr={}, partner={}, channelIdList={}", dateStr, user.getPartner(), channelIdList);
            List<Map<String, Object>> dataMapList = adsenseDataMapper.getListByDimenDate(channelIdList, null, dateStr, dateStr);
            log.debug("dailyReportData, dataMapList={}", dataMapList);
            if (!CollectionUtils.isEmpty(dataMapList)) {
                Map<String, Object> map = dataMapList.get(0);
                Double tt_spend = map.get("tt_spend") != null ? (Double) map.get("tt_spend") : 0.0;
                Double tb_spend = map.get("tb_spend") != null ? (Double) map.get("tb_spend") : 0.0;
                Double gg_spend = map.get("gg_spend") != null ? (Double) map.get("gg_spend") : 0.0;
                Double fb_spend = map.get("fb_spend") != null ? (Double) map.get("fb_spend") : 0.0;

                tt_spend += tb_spend + gg_spend + fb_spend;

                double roi = 0.0;
                if (tt_spend != 0) {
                    roi = ((Double)map.get("gross_revenue"))/tt_spend;
                }
                String roiStr = "roi : " + AppUtils.doubleToStr0(roi*100) + "%";
                double profit = 0.0;
                if (tt_spend != 0) {
                    profit = ((Double)map.get("gross_revenue")) - tt_spend;
                }
                String profitStr = "profit : " + AppUtils.doubleToStr0(profit);
                String spendStr = "spend : " + AppUtils.doubleToStr0(tt_spend);
                String grossRevenue = "grossRevenue : " + AppUtils.doubleToStr0((Double)map.get("gross_revenue"));
                String resultStr = roiStr + " , " + profitStr + " , " + spendStr + " , " + grossRevenue;
                resultMap.put(profit, user.name + " ｜ " + resultStr + "\n\n");
            }
        }

        if (!CollectionUtils.isEmpty(resultMap)) {
            StringBuffer sb = new StringBuffer();
            sb.append(dateStr + " 投放日报: \n\n");
            resultMap.descendingMap().forEach((k, v) -> {
                sb.append(v);
            });

            int maxRetry = 10;
            int retryCount = 0;
            while (retryCount < maxRetry) {
                try {
                    String response = feishuReportService.reportUserAlertWaitResponse(sb.toString(), FeishuReportService.mDailyOpReportUrl);
                    Map<String, Object> responseMap = JsonUtils.toMap(response);
                    Integer responseCode = -1;
                    String responseMsg = "";
                    if (responseMap.get("code") != null) {
                        responseCode = (Integer) responseMap.get("code");
                    }
                    if (responseMap.get("msg") != null) {
                        responseMsg = (String) responseMap.get("msg");
                    }
                    if (responseCode == 0 && "success".equals(responseMsg)) {
                        log.info("发送飞书日报成功");
                        break;
                    } else {
                        retryCount++;
                        Thread.sleep(5000);
                        continue;
                    }
                } catch (Exception e) {
                    log.error("dailyReportData error.", e);
                }
            }
        }
    }

    private List<String> getChannelId(String partner) {
        List<AdResource> adResourceList = adResourceMapper.getByPartner(partner);
        List<String> channelIdList = adResourceList.stream()
                .map(e -> adResourceService.getCusChannelId(e.getAdResourceId(), e.getChannelId(), e.getLandPageUrl()))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(channelIdList)) {
            return Arrays.asList("placeholder");
        }
        return new ArrayList<>(new HashSet<>(channelIdList));
    }
}
