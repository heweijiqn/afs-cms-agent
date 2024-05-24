package com.afs.controller;

import com.afs.dao.CategoryMapper;
import com.afs.dao.ConfigMapper;
import com.afs.dao.model.CategoryInfo;
import com.afs.dao.model.ConfigItem;
import com.afs.dao.model.LangInfo;
import com.afs.utils.Page;
import com.afs.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms/config")
public class ConfigController {

    @Autowired
    ConfigMapper configMapper;

    @Data
    public static class AddBody {
        @NotBlank
        String value;
        @NotBlank
        String name;
        @NotBlank
        String desc;
        @NotBlank
        String site;
    }

    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody @Valid AddBody body) {
        if (!SiteController.sites().contains(body.getSite())) return ResponseUtils.error(-1, "请选择有效站点");
        ConfigItem info = toConfigItem(body);
        int n = configMapper.add(info);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.result("更新失败");
        }
    }

    private ConfigItem toConfigItem(AddBody body) {
        ConfigItem info = new ConfigItem();
        info.setValue(body.getValue());
        info.setDesc(body.getDesc());
        info.setName(body.getName());
        info.setSite(body.getSite());
        return info;
    }

    @Data
    public static class EditBody {
        @NotBlank
        String value;
        @NotBlank
        String name;
        @NotBlank
        String desc;
        @NotBlank
        String site;
    }

    @PostMapping("/edit")
    public ResponseUtils.Result edit(@RequestBody @Valid AddBody body) {
        if (!SiteController.sites().contains(body.getSite())) return ResponseUtils.error(-1, "请选择有效站点");
        ConfigItem info = toConfigItem(body);
        int n = configMapper.update(info);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.result("更新失败");
        }
    }

    @Data
    public static class GetBody {
        @NotNull
        Integer pageIndex;
        @NotNull
        Integer pageSize;
        String site;
    }

    @PostMapping("/list")
    public ResponseUtils.Result getList(@RequestBody @Valid GetBody body) {
        Page page = new Page();
        page.size = Math.max(10, body.getPageSize());
        page.index = body.getPageIndex();
        int offset = Math.max(0, (body.getPageIndex() - 1)) * body.getPageSize();
        page.list = configMapper.getList(body.getSite(), SiteController.sites(), offset, body.getPageSize());
        page.totalSize = configMapper.count(body.getSite(), SiteController.sites());
        page.maxIndex = page.totalSize / body.getPageSize();
        return ResponseUtils.result(page);
    }
}
