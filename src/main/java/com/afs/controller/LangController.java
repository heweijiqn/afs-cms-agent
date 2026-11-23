package com.afs.controller;

import com.afs.dao.LangMapper;
import com.afs.dao.model.LangInfo;
import com.afs.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/cms/lang")
public class LangController {
    
    @Autowired
    LangMapper langMapper;

    @Data
    public static class AddBody {
        @NotBlank
        String value;
        @NotBlank
        String label;
    }

    @PostMapping("/add")
    public ResponseUtils.Result addLang(@RequestBody AddBody body) {
        LangInfo langInfo = toLangInfo(body);
        int n = langMapper.add(langInfo);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    private LangInfo toLangInfo(AddBody body) {
        LangInfo langInfo = new LangInfo();
        langInfo.setValue(body.value);
        langInfo.setLabel(body.label);
        langInfo.setSite("");
        return langInfo;
    }

    @Data
    public static class EditBody {
        @NotBlank
        Long id;
        @NotBlank
        String value;
        @NotBlank
        String label;
    }

    @PostMapping("/edit")
    public ResponseUtils.Result editLang(@RequestBody EditBody body) {
        int n = langMapper.updateLabelAndValue(body.value, body.label, body.id);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error( -1, "更新失败");
        }
    }


    @GetMapping("/list")
    public ResponseUtils.Result getList() {
        List<LangInfo> list = langMapper.get();
        return ResponseUtils.result(list);
    }
}
