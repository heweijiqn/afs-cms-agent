package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.controller.filter.RootAuth;
import com.afs.dao.AdResourceMapper;
import com.afs.dao.AdsenseChannelMapper;
import com.afs.dao.AdsenseDataMapper;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.AdsenseChannel;
import com.afs.task.AdGroupInfoAndLastDayAdResourceTask;
import com.afs.utils.JsonUtils;
import com.afs.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/open")
public class OpenController {
    @org.springframework.beans.factory.annotation.Value("${afs.local-workspace:false}")
    private boolean localWorkspace;
    private static final String archiveToken = System.getenv().getOrDefault("AFS_OPEN_ARCHIVE_TOKEN", "");
    private static final String reportToken = System.getenv().getOrDefault("AFS_OPEN_REPORT_TOKEN", "");
    private static final String publisherPrefix = System.getenv().getOrDefault("AFS_OPEN_PUBLISHER_PREFIX", "");

    static List<String> partnerList = Arrays.asList("endless-possibilities" + "endless-1", "endless-2", "endless-3", "endless-4");

    @Autowired
    AdsenseDataMapper adsenseDataMapper;
    @Autowired
    AdResourceMapper adResourceMapper;
    @Autowired
    AdsenseChannelMapper adsenseChannelMapper;

    @Data
    public static class ArchiveBody {
        @NotEmpty
        public List<String> list;
    }

    @NoAuth
    @PostMapping("/endless/ad/resource/archive")
    public ResponseUtils.Result archive(@RequestBody @Valid ArchiveBody body,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!localWorkspace && (archiveToken.isBlank() || !archiveToken.equals(authorization))) {
            return ResponseUtils.error(-1, "authorization error");
        }
        List<AdResource> adResourceList = adResourceMapper.getByAdResourceIdList(body.list);
        for (AdResource adResource : adResourceList) {
            if (!localWorkspace && !partnerList.contains(adResource.getPartner())) {
                return ResponseUtils.error(-1, "cannot archive, no permission");
            }
        }
        if (adResourceList.size() != new java.util.HashSet<>(body.list).size()) {
            return ResponseUtils.error(-1, "部分活动不存在");
        }

        log.info("archive list={}", body.getList());
        for (AdResource adResource : adResourceList) {
            String adResourceId = adResource.getAdResourceId();
            log.info("archive adResource={}", adResource);
            if (AdResource.ARCHIVE.equals(adResource.getState())) {
                continue;
            }
            int n = adResourceMapper.archive(AdResource.ARCHIVE, adResourceId);
            if (n > 0) {
                if (!hasOtherAdResourceUseChannelIdNew(adResource.getAdResourceId(), adResource.getChannelId())) {
                    log.info("archive adResource updateByChannelId={}", adResource.getChannelId());
                    adsenseChannelMapper.updateByChannelId(adResource.getChannelId(), AdsenseChannel.ARCHIVE);
                }
            }
        }
        return ResponseUtils.ok();
    }

    private boolean hasOtherAdResourceUseChannelIdNew(String adResourceId, String channelId) {
        int n = adResourceMapper.countOtherAdResourceIdByChannelIdByStateList(adResourceId, channelId, Arrays.asList(AdResource.PASS, AdResource.AUDITING));
        log.info("hasOtherAdResourceUseChannelIdNew channelId={}, n={}", channelId, n);
        if (n > 0) {
            return true;
        } else {
            return false;
        }
    }

    @NoAuth
    @GetMapping("/adsense/data/list")
    public ResponseUtils.Result getList(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestParam String dateStart, @RequestParam String dateEnd,
                                        @RequestParam String channelId, @RequestParam(required = false) String countryCode,
                                        @RequestParam(required = false) String pubId) {
        if (!localWorkspace && (reportToken.isBlank() || !reportToken.equals(authorization))) {
            return ResponseUtils.error(-1, "authorization error");
        }
        if (noValid(dateStart, dateEnd)) {
            return ResponseUtils.error(-1, "parameter error");
        }
        String publisher = localWorkspace ? pubId : publisherPrefix;
        if (publisher == null || publisher.isBlank()) return ResponseUtils.error(-1, "请填写发布商 ID");
        List<Map<String, Object>> list = adsenseDataMapper.getList(dateStart, dateEnd, "AFS", countryCode, publisher + ":" + channelId);
        List<Map<String, Object>> resultList = list.stream().map(e -> JsonUtils.toMap((String) e.get("adsense_data"))).collect(Collectors.toList());
        return ResponseUtils.result(resultList);
    }

    @Autowired
    AdGroupInfoAndLastDayAdResourceTask adGroupInfoAndLastDayAdResourceTask;

    @RootAuth
    @GetMapping("/ad/resource/do/last-day")
    public ResponseUtils.Result doLastDayResource() {
        adGroupInfoAndLastDayAdResourceTask.updateAdResourceLastDayData();
//        adGroupInfoAndLastDayAdResourceTask.updateAdGroupLastDayData();
        return ResponseUtils.ok();
    }

    private boolean noValid(String dateStart, String dateEnd) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            dateFormat.parse(dateStart);
            dateFormat.parse(dateEnd);
            return false;
        } catch (Exception exception) {
            return true;
        }
    }

    private boolean toLong(String dateStart, String dateEnd) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date date1 = dateFormat.parse(dateStart);
            Date date2 = dateFormat.parse(dateEnd);
            long gap = date2.getTime() - date1.getTime();
            return gap > 10 * 24 * 60 * 60 * 1000;
        } catch (Exception exception) {
            return true;
        }
    }


}
