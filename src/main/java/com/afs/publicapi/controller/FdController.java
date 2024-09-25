package com.afs.publicapi.controller;

import com.afs.publicapi.controller.filter.NoAuth;
import com.afs.publicapi.dao.FdMapper;
import com.afs.publicapi.dao.model.Fd;
import com.afs.publicapi.utils.Page;
import com.afs.publicapi.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@CrossOrigin(origins = "*")
@Slf4j
@RestController
@RequestMapping("/api/feedback")
public class FdController {

    @Autowired
    FdMapper fdMapper;

    @Data
    public static class AddBody {
        @NotBlank
        String value;
        @NotNull
        Integer rate;
        @NotBlank
        String locale;
    }

    @NoAuth
    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestHeader(value = "Current-Site") String site, @RequestBody AddBody body) {
        log.info("add fd site={}", site);
        Fd info = toFd(body, site);
        int n = fdMapper.add(info);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.result("更新失败");
        }
    }

    private Fd toFd(AddBody body, String site) {
        Fd info = new Fd();
        info.setValue(body.getValue());
        info.setSite(site);
        info.setLocale(body.getLocale());
        info.setRate(body.getRate());
        return info;
    }
}
