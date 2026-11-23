package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.dao.AdResourceRuleMapper;
import com.afs.dao.model.AdResourceRule;
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
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cms/ad/resource/rule")
public class AdResourceRuleController {

    @Autowired
    AdResourceRuleMapper adResourceRuleMapper;

    @Data
    public static class Request {
        public String name;
    }

    @NoAuth
    @PostMapping("/list")
    public ResponseUtils.Result getList(@RequestHeader(Consts.HeaderUserName) String userName, @RequestBody @Valid Request request,
                                        @RequestParam int pageIndex, @RequestParam int pageSize) {
        Page page = new Page();
        page.size = Math.min(100, Math.max(1, pageSize));
        page.index = pageIndex;
        int offset = Math.max(0, (pageIndex - 1)) * page.size;

        List<AdResourceRule> adResourceRuleList = adResourceRuleMapper.getListByQuery(request.getName(), offset, page.size);
        page.list = adResourceRuleList.stream().map(e -> toAdResourceRuleVO(e)).collect(Collectors.toList());
        page.totalSize = adResourceRuleMapper.countByQuery(request.getName());

        page.maxIndex = (int) Math.ceil((double) page.totalSize / page.size);
        return ResponseUtils.result(page);
    }

    @Data
    public static class AdResourceRuleVO {
        public Long id;
        public String name;
        public String type;
        public String actionCycle;
        public Integer actionTime;
        public Object rule;
        public String creator;
        public Double budgetChange;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date utime;
    }

    private AdResourceRuleVO toAdResourceRuleVO(AdResourceRule e) {
        AdResourceRuleVO adResourceRuleVO = new AdResourceRuleVO();
        adResourceRuleVO.setId(e.getId());
        adResourceRuleVO.setName(e.getName());
        adResourceRuleVO.setType(e.getType());
        adResourceRuleVO.setActionCycle(e.getActionCycle());
        adResourceRuleVO.setActionTime(e.getActionTime());
        adResourceRuleVO.setRule(JsonUtils.toList(e.getRule(), Map.class));
        adResourceRuleVO.setCreator(e.getCreator());
        adResourceRuleVO.setBudgetChange(e.getBudgetChange());
        adResourceRuleVO.setCtime(e.getCtime());
        adResourceRuleVO.setUtime(e.getUtime());
        return adResourceRuleVO;
    }

    @Data
    public static class AddBody {
        @NotBlank
        public String name;
        @NotBlank
        public String type;
        @NotBlank
        public String actionCycle;
        @NotNull
        public Integer actionTime;
        @NotEmpty
        public List<Object> rule;
        public Double budgetChange;
    }

    @NoAuth
    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody @Valid AddBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        AdResourceRule adResourceRule = new AdResourceRule();
        adResourceRule.setName(body.getName());
        adResourceRule.setType(body.getType());
        adResourceRule.setActionTime(body.actionTime);
        adResourceRule.setActionCycle(body.actionCycle);
        adResourceRule.setCreator(userName);
        adResourceRule.setBudgetChange(body.getBudgetChange() != null ? body.getBudgetChange() : 0);
        adResourceRule.setRule(JsonUtils.toJsonString(body.getRule()));
        adResourceRule.setStatus(AdResourceRule.StatusEnabled);
        int n = adResourceRuleMapper.add(adResourceRule);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "添加失败");
        }
    }

    @Data
    public static class EditBody {
        @NotNull
        public Long id;
        @NotBlank
        public String name;
        public String type;
        public String actionCycle;
        public Integer actionTime;
        public List<Object> rule;
        public Double budgetChange;
    }

    @NoAuth
    @PostMapping("/edit")
    public ResponseUtils.Result edit(@RequestBody @Valid EditBody body) {
        AdResourceRule adResourceRule = new AdResourceRule();
        adResourceRule.setId(body.getId());
        adResourceRule.setName(body.getName());
        adResourceRule.setType(body.getType());
        adResourceRule.setActionTime(body.getActionTime());
        adResourceRule.setActionCycle(body.getActionCycle());
        adResourceRule.setBudgetChange(body.getBudgetChange());
        adResourceRule.setRule(JsonUtils.toJsonString(body.getRule()));
        adResourceRule.setStatus(AdResourceRule.StatusEnabled);
        int n = adResourceRuleMapper.edit(adResourceRule);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "编辑失败");
        }
    }


    @Data
    public static class DelBody {
        @NotNull
        public Long id;
    }

    @NoAuth
    @PostMapping("/del")
    public ResponseUtils.Result del(@RequestBody @Valid DelBody body) {
        AdResourceRule adResourceRule = adResourceRuleMapper.getById(body.getId());
        log.info("del adResourceRule={}", adResourceRule);
        int n = adResourceRuleMapper.delById(body.getId());
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }
}
