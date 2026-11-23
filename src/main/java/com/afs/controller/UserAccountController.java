package com.afs.controller;

import com.afs.controller.filter.RootAuth;
import com.afs.dao.AdAccountInfoMapper;
import com.afs.dao.UserMapper;
import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.User;
import com.afs.utils.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cms")
public class UserAccountController {

    @Autowired
    UserMapper userMapper;
    @Autowired
    AdAccountInfoMapper adAccountInfoMapper;

    @GetMapping("/user/account/get")
    public ResponseUtils.Result getUserAccount(@RequestHeader(Consts.HeaderUserName) String loginUserName, @RequestParam String name) {
        User user = userMapper.get(name);
        return ResponseUtils.result(toUserAccountVO(user, false));
    }

    @RootAuth
    @PostMapping("/user/account/list")
    public ResponseUtils.Result userAccountList(@RequestHeader(Consts.HeaderUserName) String userName,
                                                @RequestParam int pageIndex, @RequestParam int pageSize) {
        Page page = new Page();
        page.size = Math.min(100, Math.max(1, pageSize));
        page.index = pageIndex;
        int offset = Math.max(0, (pageIndex - 1)) * page.size;
        List<User> userList = userMapper.getList(User.CommonUser, offset, page.size);
        page.list = userList.stream().map(e -> toUserAccountVO(e, true)).collect(Collectors.toList());
        page.totalSize = userMapper.countUserByRoot(User.CommonUser);
        page.maxIndex = (int) Math.ceil((double) page.totalSize / page.size);
        return ResponseUtils.result(page);
    }

    @Data
    public static class UserVO {
        String name;
        String desc;
        String password;
        List<String> siteList;
        List<AdAccountInfo> adAccountList;
        Boolean autoArticle;
        Boolean autoCampaign;
        Boolean showMaterial;
    }

    private UserVO toUserAccountVO(User e, boolean showPassword) {
        UserVO vo = new UserVO();
        vo.setName(e.getName());
        vo.setDesc(e.getDesc());
        vo.setPassword(showPassword ? e.getPassword() : "");
        vo.setAutoArticle(e.getAutoArticle());
        vo.setAutoCampaign(e.getAutoCampaign());
        vo.setShowMaterial(e.getShowMaterial());
        vo.setSiteList(StringUtils.isNotBlank(e.getSiteList()) ? JsonUtils.toList(e.getSiteList(), String.class) : new ArrayList<>());

        List<Long> adAccountIdList = StringUtils.isNotBlank(e.getAdAccountList()) ? JsonUtils.toList(e.getAdAccountList(), Long.class) : new ArrayList<>();

        if (CollectionUtils.isEmpty(adAccountIdList)) {
            vo.setAdAccountList(new ArrayList<>());
        } else {
            List<AdAccountInfo> adAccountInfoList = adAccountInfoMapper.getByIds(adAccountIdList);
            vo.setAdAccountList(adAccountInfoList);
        }
        return vo;
    }

    @Data
    public static class AddBody {
        @NotBlank
        String name;
        @NotBlank
        String desc;
        List<String> siteList;
        List<Long> adAccountList;
        @NotNull
        Boolean autoArticle;
        @NotNull
        Boolean autoCampaign;
        Boolean showMaterial;
    }

    @RootAuth
    @PostMapping("/user/account/add")
    public ResponseUtils.Result addUserAccount(@RequestBody AddBody body) {
        return addUserAccount(body, false);
    }

    @RootAuth
    @PostMapping("/user/account/console/add")
    public ResponseEntity<ResponseUtils.Result> addUserAccountFromConsole(@RequestBody @Valid AddBody body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(addUserAccount(body, true));
    }

    private ResponseUtils.Result addUserAccount(AddBody body, boolean includeInitialCredential) {
        if (userMapper.get(body.getName()) != null) {
            return ResponseUtils.error( -1, "user name is exist");
        }
        if (body.autoArticle && CollectionUtils.isEmpty(body.getSiteList())) {
            return ResponseUtils.error( -1, "site list is empty");
        }

        User user = toUser(body);
        int n = userMapper.add(user);
        if (n > 0) {
            if (includeInitialCredential) {
                return ResponseUtils.result(java.util.Map.of("name", user.getName(), "initialCredential", user.getPassword()));
            }
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error(-1, "添加失败");
        }
    }

    private User toUser(AddBody body) {
        User user = new User();
        user.setName(body.getName());
        user.setDesc(body.getDesc());
        if (CollectionUtils.isEmpty(body.getSiteList())) {
            user.setSiteList("[]");
        } else {
            user.setSiteList(JsonUtils.toJsonString(body.getSiteList()));
        }
        if (CollectionUtils.isEmpty(body.getAdAccountList())) {
            user.setAdAccountList("[]");
        } else {
            user.setAdAccountList(JsonUtils.toJsonString(body.getAdAccountList()));
        }
        user.setPassword(AppUtils.getPassword());
        user.setAutoArticle(body.getAutoArticle());
        user.setAutoCampaign(body.getAutoCampaign());
        user.setShowMaterial(body.getShowMaterial() != null? body.getShowMaterial() : false);
        user.setRoot(User.CommonUser);
        user.setPartner(body.getName());
        user.setStatus(User.StatusValid);
        return user;
    }

    @Data
    public static class EditBody {
        @NotBlank
        String name;
        String desc;
        List<String> siteList;
        List<Long> adAccountList;
        Boolean autoArticle;
        Boolean autoCampaign;
        Boolean showMaterial;
    }

    @RootAuth
    @PostMapping("/user/account/edit")
    public ResponseUtils.Result editUserAccount(@RequestBody EditBody body) {
        User user = toUser(body);
        int n = userMapper.updateUser(user);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error( -1, "更新失败");
        }
    }

    private User toUser(EditBody body) {
        User user = new User();
        user.setName(body.getName());
        user.setDesc(body.getDesc());
        if (CollectionUtils.isEmpty(body.getSiteList())) {
            user.setSiteList("[]");
        } else {
            user.setSiteList(JsonUtils.toJsonString(body.getSiteList()));
        }
        if (CollectionUtils.isEmpty(body.getAdAccountList())) {
            user.setAdAccountList("[]");
        } else {
            user.setAdAccountList(JsonUtils.toJsonString(body.getAdAccountList()));
        }
        user.setAutoArticle(body.getAutoArticle());
        user.setAutoCampaign(body.getAutoCampaign());
        if (body.getShowMaterial() != null) {
            user.setShowMaterial(body.getShowMaterial());
        }
        return user;
    }
}
