package com.afs.controller;


import com.afs.controller.filter.NoAuth;
import com.afs.controller.filter.RootAuth;
import com.afs.dao.AdMaterialMapper;
import com.afs.dao.UserMapper;
import com.afs.dao.model.Material;
import com.afs.utils.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms/ad/material")
public class AdMaterialController {

    @Autowired
    AdMaterialMapper materialsMapper;
    @Autowired
    UserMapper userMapper;

    @Data
    public static class ListRequest {
        public Long startTime;
        public Long endTime;
        public String keyword;
        public List<String> ratio;
        public List<String> type;
        public List<String> countryList;
        public List<String> lang;
        public List<Integer> duration;
        public List<String> category;
        @NotNull
        public Integer pageSize;
        @NotNull
        public Integer pageIndex;
    }

    @Data
    private static class MaterialsVO {
        private Long id;
        private String adMaterialId;
        private String name;
        private List<String> countryList;
        private String lang;
        private String type;
        private String dimension;
        private String ratio;
        private List<String> category;
        private String template;
        private String cover;
        private String url;
        private String userName;
        private Integer size;
        private Integer duration;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        private Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        private Date utime;
    }

    private MaterialsVO toMaterialsVO(Material material) {
        MaterialsVO materialsVO = new MaterialsVO();
        materialsVO.setId(material.getId());
        materialsVO.setAdMaterialId(material.getAdMaterialId());
        materialsVO.setName(material.getName());
        materialsVO.setCountryList(Arrays.asList(material.getCountry().split(",")));
        materialsVO.setLang(material.getLang());
        materialsVO.setType(material.getType());
        materialsVO.setDimension(material.getDimension());
        materialsVO.setRatio(material.getRatio());
        materialsVO.setCategory(Arrays.asList(material.getCategory().split("-")));
        materialsVO.setTemplate(material.getTemplate());
        materialsVO.setSize(material.getSize());
        materialsVO.setDuration(material.getDuration());
        materialsVO.setCover(material.getCover());
        materialsVO.setUrl(material.getUrl());
        materialsVO.setUserName(material.getUserName());
        materialsVO.setCtime(material.getCtime());
        materialsVO.setUtime(material.getUtime());
        return materialsVO;
    }

    @NoAuth
    @PostMapping("/list")
    public ResponseUtils.Result getList(@RequestBody @Valid ListRequest request) {
        Page page = new Page();
        page.size = Math.max(10, request.getPageSize());
        page.index = request.getPageIndex();
        int offset = Math.max(0, (request.getPageIndex() - 1)) * request.getPageSize();

        Integer startDur = null;
        Integer endDur = null;
        if (!CollectionUtils.isEmpty(request.getDuration())) {
            if (request.getDuration().size() >= 1 && request.getDuration().get(0) != null) {
                startDur = request.getDuration().get(0);
            }
            if (request.getDuration().size() == 2 && request.getDuration().get(1) != null) {
                endDur = request.getDuration().get(1);
            }
        }
        String cate = CollectionUtils.isEmpty(request.getCategory()) ? "" : String.join("-", request.getCategory());
        List<Material> materialsList = materialsMapper.getListByQuery(cate, request.getCountryList(), startDur, endDur, request.getStartTime() != null ? DateUtils.formatDate(request.getStartTime()) : null,
                request.getStartTime() != null ? DateUtils.formatDate(request.getEndTime()) : null, request.getRatio(), request.getLang(), request.getType(),
                request.getKeyword(), offset, request.getPageSize());
        page.list = materialsList.stream().map(e -> toMaterialsVO(e)).collect(Collectors.toList());
        page.totalSize = materialsMapper.countByQuery(cate, request.getCountryList(), startDur, endDur, request.getStartTime() != null ? DateUtils.formatDate(request.getStartTime()) : null,
                request.getStartTime() != null ? DateUtils.formatDate(request.getEndTime()) : null,  request.getRatio(), request.getLang(), request.getType(), request.getKeyword());
        page.maxIndex = page.totalSize / request.getPageSize();
        return ResponseUtils.result(page);
    }

    @Data
    public static class AddBody {
        @NotBlank
        private String name;
        @NotEmpty
        public List<String> countryList;
        @NotBlank
        private String lang;
        @NotBlank
        private String type;
        @NotEmpty
        private List<String> category;
        private String dimension;
        private String ratio;
        @NotBlank
        private String template;
        private String cover;
        @NotBlank
        private String url;
        @NotNull
        private Integer size;
        private Integer duration;
    }

    private Material toMaterial(AddBody body, String userName) {
        Material material = new Material();
        String adMaterialId = AppUtils.getUUID().substring(0, 20);
        material.setAdMaterialId(adMaterialId);
        material.setName(body.getName());
        material.setUserName(userName);
        material.setCountry(String.join(",", body.getCountryList()));
        material.setLang(body.getLang());
        material.setType(body.getType());
        material.setDimension(body.getDimension());
        material.setRatio(body.getRatio());
        material.setCategory(String.join("-", body.getCategory()));
        material.setTemplate(body.getTemplate());
        material.setCover(body.getCover());
        material.setUrl(body.getUrl());
        material.setSize(body.getSize());
        material.setDuration(body.getDuration());
        return material;
    }

    @NoAuth
    @RootAuth
    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody @Valid List<AddBody> bodyList, @RequestHeader(Consts.HeaderUserName) String userName) {
        for (AddBody body : bodyList) {
            Material newMaterial = toMaterial(body, userName);
            materialsMapper.add(newMaterial);
            Material materialDb = materialsMapper.getByAdMaterialId(newMaterial.getAdMaterialId());
            materialsMapper.updateName(materialDb.getId() + "_" + materialDb.getName(), materialDb.getAdMaterialId());
        }
        return ResponseUtils.ok();
    }

    @Data
    public static class EditBody {
        @NotNull
        private Long id;
        public List<String> countryList;
        private String lang;
        private String name;
        private String type;
        private List<String> category;
        private String dimension;
        private String ratio;
        private String template;
        private String cover;
        private String url;
        private Integer size;
        private Integer duration;
    }

    @NoAuth
    @RootAuth
    @PostMapping("/edit")
    public ResponseUtils.Result edit(@RequestBody @Valid EditBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        Material newMaterial = toMaterial(body);
        int n = materialsMapper.update(newMaterial);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "添加失败");
        }
    }

    private Material toMaterial(EditBody body) {
        Material material = new Material();
        material.setId(body.getId());
        material.setName(body.getName());
        material.setCountry(CollectionUtils.isEmpty(body.getCountryList()) ? null : String.join(",", body.getCountryList()));
        material.setLang(body.getLang());
        material.setCategory(String.join("-", body.getCategory()));
        material.setTemplate(body.getTemplate());
        material.setSize(body.getSize());
        material.setDuration(body.getDuration());
        return material;
    }

    @Data
    public static class DelRequest {
        @NotEmpty
        private List<Long> idList;
    }

    @NoAuth
    @PostMapping("/del")
    public ResponseUtils.Result del(@RequestBody DelRequest request, @RequestHeader(Consts.HeaderUserName) String userName) {
        int n = materialsMapper.deleteByIdList(request.getIdList());
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "删除失败");
        }
    }
}
