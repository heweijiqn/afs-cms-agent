package com.afs.controller;

import com.afs.dao.FdMapper;
import com.afs.dao.model.Fd;
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
@RequestMapping("/cms/feedback")
public class FdController {

    @Autowired
    FdMapper fdMapper;

    @Data
    public static class GetBody {
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
        page.list = fdMapper.getList(body.getSite(), SiteController.sites(), offset, body.getPageSize());
        page.totalSize = fdMapper.count(body.getSite(), SiteController.sites());
        page.maxIndex = page.totalSize / body.getPageSize();
        return ResponseUtils.result(page);
    }
}
