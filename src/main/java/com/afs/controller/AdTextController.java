package com.afs.controller;


import com.afs.controller.filter.NoAuth;
import com.afs.controller.filter.RootAuth;
import com.afs.dao.AdTextMapper;
import com.afs.dao.model.AdText;
import com.afs.utils.Consts;
import com.afs.utils.JsonUtils;
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
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms/ad/text")
public class AdTextController {

    @Autowired
    AdTextMapper adTextMapper;

    @Data
    public static class ListRequest {
        public String name;
        @NotNull
        public Integer pageSize;
        @NotNull
        public Integer pageIndex;
    }

    @Data
    private static class AdTextVO {
        private Long id;
        private List<String> textList;
        private String name;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        private Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        private Date utime;
    }

    private AdTextVO toAdTextVO(AdText adText) {
        AdTextVO materialsVO = new AdTextVO();
        materialsVO.setId(adText.getId());
        materialsVO.setName(adText.getName());
        materialsVO.setTextList(JsonUtils.toList(adText.getAdText(), String.class));
        materialsVO.setCtime(adText.getCtime());
        materialsVO.setUtime(adText.getUtime());
        return materialsVO;
    }

    @NoAuth
    @PostMapping("/list")
    public ResponseUtils.Result getList(@RequestBody @Valid ListRequest request) {
        Page page = new Page();
        page.size = Math.max(10, request.getPageSize());
        page.index = request.getPageIndex();
        int offset = Math.max(0, (request.getPageIndex() - 1)) * request.getPageSize();

        List<AdText> adTextList = adTextMapper.getList(request.getName(), offset, request.getPageSize());
        page.list = adTextList.stream().map(e -> toAdTextVO(e)).collect(Collectors.toList());
        page.totalSize = adTextMapper.count( request.getName());
        page.maxIndex = page.totalSize / request.getPageSize();
        return ResponseUtils.result(page);
    }

    @Data
    public static class AddBody {
        @NotBlank
        private String name;
        @NotEmpty
        public List<String> textList;
    }

    private AdText toAdText(AddBody body) {
        AdText adText = new AdText();
        adText.setName(body.getName());
        adText.setAdText(JsonUtils.toJsonString(body.getTextList()));
        return adText;
    }

    @NoAuth
    @RootAuth
    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody @Valid AddBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        AdText adText = toAdText(body);
        adTextMapper.add(adText);
        return ResponseUtils.ok();
    }

    @Data
    public static class EditBody {
        @NotNull
        private Long id;
        public List<String> textList;
        private String name;
    }

    @NoAuth
    @RootAuth
    @PostMapping("/edit")
    public ResponseUtils.Result edit(@RequestBody @Valid EditBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        AdText adText = toAdText(body);
        int n = adTextMapper.update(adText);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "添加失败");
        }
    }

    private AdText toAdText(EditBody body) {
        AdText adText = new AdText();
        adText.setId(body.getId());
        adText.setName(body.getName());
        adText.setAdText(JsonUtils.toJsonString(body.getTextList()));
        return adText;
    }

    @RootAuth
    @PostMapping("/console/add")
    public ResponseUtils.Result addFromConsole(@RequestBody @Valid AddBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        return add(body, userName);
    }

    @RootAuth
    @PostMapping("/console/edit")
    public ResponseUtils.Result editFromConsole(@RequestBody @Valid EditBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        return edit(body, userName);
    }

    @Data
    public static class DelRequest {
        @NotNull
        private Long id;
    }

    @NoAuth
    @PostMapping("/del")
    public ResponseUtils.Result del(@RequestBody DelRequest request, @RequestHeader(Consts.HeaderUserName) String userName) {
        int n = adTextMapper.del(request.getId());
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "删除失败");
        }
    }
}
