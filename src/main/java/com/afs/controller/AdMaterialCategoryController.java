package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.dao.AdMaterialCategoryChildMapper;
import com.afs.dao.AdMaterialCategoryMapper;
import com.afs.dao.CategoryChildMapper;
import com.afs.dao.CategoryMapper;
import com.afs.dao.model.AdMaterialCategoryChildInfo;
import com.afs.dao.model.AdMaterialCategoryInfo;
import com.afs.dao.model.CategoryChildInfo;
import com.afs.dao.model.CategoryInfo;
import com.afs.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/cms/ad/material/category")
public class AdMaterialCategoryController {

    @Autowired
    AdMaterialCategoryMapper categoryMapper;
    @Autowired
    AdMaterialCategoryChildMapper categoryChildMapper;

    @Data
    public static class AddBody {
        @NotBlank
        String value;
        @NotBlank
        String label;
        @NotNull
        AddChildBody child;
    }

    @Data
    public static class AddChildBody {
        @NotBlank
        String value;
        @NotBlank
        String label;
    }

    @NoAuth
    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody @Valid AddBody body) {
        AdMaterialCategoryInfo info = toCategoryInfo(body);
        int n = categoryMapper.add(info);
        if (StringUtils.isNotEmpty(body.getChild().label) && StringUtils.isNotEmpty(body.getChild().value)) {
            AdMaterialCategoryChildInfo childInfo = toCategoryChildInfo(body);
            int m = categoryChildMapper.add(childInfo);
        }
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "更新失败");
        }
    }

    private AdMaterialCategoryChildInfo toCategoryChildInfo(AddBody body) {
        AdMaterialCategoryChildInfo info = new AdMaterialCategoryChildInfo();
        info.setValue(body.getValue());
        info.setLabelChild(body.getChild().getLabel());
        info.setValueChild(body.getChild().getValue());
        return info;
    }

    private AdMaterialCategoryInfo toCategoryInfo(AddBody body) {
        AdMaterialCategoryInfo info = new AdMaterialCategoryInfo();
        info.setValue(body.value);
        info.setLabel(body.label);
        return info;
    }

    @NoAuth
    @GetMapping("/list")
    public ResponseUtils.Result getList() {

        List<Map> mapList = new ArrayList<>();
        List<AdMaterialCategoryInfo> list = categoryMapper.get();
        for (AdMaterialCategoryInfo categoryInfo : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("value", categoryInfo.getValue());
            map.put("label", categoryInfo.getLabel());

            List<Map> childList = new ArrayList<>();

            List<AdMaterialCategoryChildInfo> categoryChildInfoList = categoryChildMapper.getChildByValue(categoryInfo.getValue());
            if (!CollectionUtils.isEmpty(categoryChildInfoList)) {
                for (AdMaterialCategoryChildInfo categoryChildInfo : categoryChildInfoList) {
                    Map<String, Object> childMap = new HashMap<>();
                    childMap.put("value", categoryChildInfo.getValueChild());
                    childMap.put("label", categoryChildInfo.getLabelChild());
                    childList.add(childMap);
                }
            }
            map.put("childList", childList);
            mapList.add(map);
        }
        return ResponseUtils.result(mapList);
    }
}
