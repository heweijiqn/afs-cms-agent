package com.afs.controller;

import com.afs.dao.ConfigMapper;
import com.afs.dao.ReportDataMapper;
import com.afs.dao.model.ConfigItem;
import com.afs.dao.model.ReportData;
import com.afs.utils.Page;
import com.afs.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms/report/config")
public class ReportDataConfigController {

    @Autowired
    ReportDataMapper reportDataMapper;

    @Data
    public static class AddBody {
        @NotBlank
        String value;
        @NotBlank
        String channel;
        @NotBlank
        String desc;
        @NotBlank
        String site;
    }

    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody AddBody body) {
        if (!SiteController.sites().contains(body.getSite())) return ResponseUtils.error(-1, "请选择有效站点");
        ReportData info = toReportData(body);
        int n = reportDataMapper.add(info);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.result("更新失败");
        }
    }

    private ReportData toReportData(AddBody body) {
        ReportData info = new ReportData();
        info.setValue(body.getValue());
        info.setDesc(body.getDesc());
        info.setChannel(body.getChannel());
        info.setSite(body.getSite());
        return info;
    }

    @Data
    public static class EditBody {
        @NotBlank
        String value;
        @NotBlank
        String channel;
        @NotBlank
        String desc;
        @NotBlank
        String site;
    }

    @PostMapping("/edit")
    public ResponseUtils.Result edit(@RequestBody AddBody body) {
        if (!SiteController.sites().contains(body.getSite())) return ResponseUtils.error(-1, "请选择有效站点");
        ReportData info = toReportData(body);
        int n = reportDataMapper.update(info);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.result("更新失败");
        }
    }

    @Data
    public static class GetBody {
        String channel;
        @NotBlank
        Integer pageIndex;
        @NotBlank
        Integer pageSize;
        String site;
    }

    @PostMapping("/list")
    public ResponseUtils.Result getList(@RequestBody GetBody body) {
        Page page = new Page();
        page.size = Math.max(10, body.getPageSize());
        page.index = body.getPageIndex();
        int offset = Math.max(0, (body.getPageIndex() - 1)) * body.getPageSize();
        page.list = reportDataMapper.getList(body.getSite(), body.channel, SiteController.sites(), offset, body.getPageSize());
        page.totalSize = reportDataMapper.count(body.getSite(), body.channel, SiteController.sites());
        page.maxIndex = page.totalSize / body.getPageSize();
        return ResponseUtils.result(page);
    }
}
