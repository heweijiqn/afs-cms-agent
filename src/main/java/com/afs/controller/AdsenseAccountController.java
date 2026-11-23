package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.dao.AdsenseAccountMapper;
import com.afs.dao.model.AdsenseAccount;
import com.afs.service.AdsenseReportService;
import com.afs.utils.Page;
import com.afs.utils.ResponseUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cms/adsense/account")
public class AdsenseAccountController {

    @Autowired
    AdsenseAccountMapper adsenseAccountMapper;
    @Autowired
    AdsenseReportService adsenseReportService;

    @Data
    public static class ListRequest {
        @NotNull
        public Integer pageSize;
        @NotNull
        public Integer pageIndex;
    }

    @Data
    private static class AdsenseAccountVO {
        public String pubId;
        public List<String> siteList;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date utime;
    }

    private AdsenseAccountVO toAdsenseAccountVO(AdsenseAccount e) {
        AdsenseAccountVO vo = new AdsenseAccountVO();
        vo.setPubId(e.getPubId());
        vo.setSiteList(Arrays.asList(e.getSite().split(",")));
        vo.setCtime(e.getCtime());
        vo.setUtime(e.getUtime());
        return vo;
    }

//    @NoAuth
    @PostMapping("/list")
    public ResponseUtils.Result getAdsenseAccountList(@RequestBody @Valid ListRequest request) {
        Page page = new Page();
        page.size = Math.min(100, Math.max(1, request.getPageSize()));
        page.index = request.getPageIndex();
        int offset = Math.max(0, (request.getPageIndex() - 1)) * page.size;
        List<AdsenseAccount> adsenseAccountList = adsenseAccountMapper.getListByQuery(offset, page.size);
        page.list = adsenseAccountList.stream().map(e -> toAdsenseAccountVO(e)).collect(Collectors.toList());
        page.totalSize = adsenseAccountMapper.countByQuery();
        page.maxIndex = (int) Math.ceil((double) page.totalSize / page.size);
        return ResponseUtils.result(page);
    }

    @GetMapping("/pubId/list")
    public ResponseUtils.Result getPubIdList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (String pubId : adsenseAccountMapper.listDistinctPubIds()) {
            if (pubId == null || pubId.isBlank()) continue;
            Map<String, String> item = new LinkedHashMap<>();
            item.put("value", pubId);
            item.put("label", pubId);
            list.add(item);
        }
        if (list.isEmpty()) {
            Map<String, String> fallback = new LinkedHashMap<>();
            fallback.put("value", "partner-");
            fallback.put("label", "未配置 publisher ID");
            list.add(fallback);
        }
        return ResponseUtils.result(list);
    }

    @Data
    public static class AddBody {
        @NotEmpty
        List<String> siteList;
        @NotBlank
        String pubId;
    }

//    @NoAuth
    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody @Valid AddBody body) {
        if (!body.getPubId().matches("(partner-)?pub-[0-9]+")) {
            return ResponseUtils.error(-1, "pubId 格式不正确，应为 pub-数字 或 partner-pub-数字");
        }
        for (String site : body.getSiteList()) {
            if (!SiteController.sites().contains(site)) return ResponseUtils.error(-1, "未知站点: " + site);
        }
        if (adsenseAccountMapper.countByPubId(body.getPubId()) > 0) {
            return ResponseUtils.error(-1, "该pubId已存在");
        }
        for (String site : body.getSiteList()) {
            if (adsenseAccountMapper.getBySite(site) != null) {//site已经对应pubId
                return ResponseUtils.error(-1, site + "已经对应pubId");
            }
        }
        AdsenseAccount adsenseAccount = toAdsenseAccount(body);
        int n = adsenseAccountMapper.add(adsenseAccount);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "添加失败");
        }
    }

    private AdsenseAccount toAdsenseAccount(AddBody body) {
        AdsenseAccount adsenseAccount = new AdsenseAccount();
        adsenseAccount.setPubId(body.getPubId());
        adsenseAccount.setSite(String.join(",", body.getSiteList()));
        return adsenseAccount;
    }

    @Data
    public static class EditBody {
        @NotEmpty
        List<String> siteList;
        @NotBlank
        String pubId;
    }

    @PostMapping("/edit")
    public ResponseUtils.Result edit(@RequestBody @Valid EditBody body) {
        for (String site : body.getSiteList()) {
            AdsenseAccount adsenseAccount = adsenseAccountMapper.getBySite(site);
            if (adsenseAccountMapper.getBySite(site) != null && !adsenseAccount.getPubId().equals(body.getPubId())) {//site已经对应pubId
                return ResponseUtils.error(-1, site + "已经对应pubId");
            }
        }
        String site = String.join(",", body.getSiteList());
        int n = adsenseAccountMapper.updateSite(body.getPubId(), site);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败,id不存在");
        }
    }
}
