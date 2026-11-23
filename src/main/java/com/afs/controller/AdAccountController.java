package com.afs.controller;

import com.afs.controller.filter.NoAuth;
import com.afs.dao.AdAccountInfoMapper;
import com.afs.dao.UserMapper;
import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.AdResource;
import com.afs.dao.model.User;
import com.afs.utils.Consts;
import com.afs.utils.JsonUtils;
import com.afs.utils.Page;
import com.afs.utils.ResponseUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.api.client.json.Json;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cms/ad/account/info")
public class AdAccountController {

    @Autowired
    AdAccountInfoMapper adAccountInfoMapper;
    @Autowired
    UserMapper userMapper;

    @Data
    public static class AdInfoRequest {
        @NotNull
        public Integer pageSize;
        @NotNull
        public Integer pageIndex;
    }

//    @NoAuth
    @PostMapping("/list")
    public ResponseUtils.Result getList(@RequestBody @Valid AdInfoRequest request) {
        Page page = new Page();
        page.size = Math.min(100, Math.max(1, request.getPageSize()));
        page.index = request.getPageIndex();
        int offset = Math.max(0, (request.getPageIndex() - 1)) * page.size;

        List<AdAccountInfo> list = adAccountInfoMapper.getListByQuery(offset, page.size);
        page.list = list.stream().map(e -> toAdAccountInfoVO(e)).collect(Collectors.toList());

        page.totalSize = adAccountInfoMapper.countByQuery();
        page.maxIndex = (int) Math.ceil((double) page.totalSize / page.size);
        return ResponseUtils.result(page);
    }

    @Data
    public static class AdAccountInfoVO {
        public Long id;
        public String name;
        public String partner;
        public String userName;
        public String accountId;
        public String adPlatform;
        public String clientId;
        public String clientSecret;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date ctime;
        @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
        public Date utime;
    }

    private AdAccountInfoVO toAdAccountInfoVO(AdAccountInfo e) {
        AdAccountInfoVO adResourceVO = new AdAccountInfoVO();
        adResourceVO.setId(e.getId());
        adResourceVO.setAccountId(e.getAccountId());
        adResourceVO.setName(e.getName());
        adResourceVO.setUserName(e.getUserName());
        adResourceVO.setPartner(e.getPartner());
        adResourceVO.setAdPlatform(e.getAdPlatform());
        if (e.getAdPlatform().equals("Taboola")) {
            adResourceVO.setClientId(JsonUtils.getInfoFromJson("clientId", e.getInfo()));
            adResourceVO.setClientSecret(JsonUtils.getInfoFromJson("clientSecret", e.getInfo()));
        }
        adResourceVO.setCtime(e.getCtime());
        adResourceVO.setUtime(e.getUtime());
        return adResourceVO;
    }

    @Data
    public static class AddBody {
        public String name;
        public String accountId;
        @NotBlank
        public String adPlatform;
        public String clientId;
        public String clientSecret;
    }

//    @NoAuth
    @PostMapping("/add")
    public ResponseUtils.Result add(@RequestBody @Valid AddBody body, @RequestHeader(Consts.HeaderUserName) String userName) {
        if (body.adPlatform.equalsIgnoreCase("Tiktok") || body.adPlatform.equalsIgnoreCase("Facebook")) {
            if (StringUtils.isBlank(body.getAccountId()) || StringUtils.isBlank(body.getName())) {
                return ResponseUtils.error( -1, "name or accountId body invalid");
            }
            long x = adAccountInfoMapper.countByAdAccountId(body.accountId);
            if (x > 0) {
                return ResponseUtils.error( -1, "account is exist");
            }
        }
        if (body.adPlatform.equalsIgnoreCase("Taboola")) {
            if (StringUtils.isBlank(body.name) || StringUtils.isBlank(body.clientId) || StringUtils.isBlank(body.clientSecret)) {
                return ResponseUtils.error( -1, "taboola body invalid");
            }
            long x = adAccountInfoMapper.countByName(body.name);
            if (x > 0) {
                return ResponseUtils.error( -1, "account is exist");
            }
        }
        if (body.adPlatform.equalsIgnoreCase("Google")) {
            if (StringUtils.isBlank(body.getAccountId()) || StringUtils.isBlank(body.getName())) {
                return ResponseUtils.error( -1, "Google body invalid");
            }
            long x = adAccountInfoMapper.countByName(body.name);
            if (x > 0) {
                return ResponseUtils.error( -1, "account is exist");
            }
        }

        AdAccountInfo adAccountInfo = new AdAccountInfo();
        Map<String, Object> map = new HashMap<>();
        if (body.adPlatform.equalsIgnoreCase("Taboola")) {
            map.put("clientId", body.getClientId());
            map.put("clientSecret", body.getClientSecret());
            adAccountInfo.setName(body.getName().trim());
            adAccountInfo.setAccountId("");
            adAccountInfo.setInfo(JsonUtils.toJsonString(map));
        }
        if (body.adPlatform.equalsIgnoreCase("Tiktok") || body.adPlatform.equalsIgnoreCase("Facebook")) {
            adAccountInfo.setName(body.getName().trim());
            adAccountInfo.setAccountId(body.accountId.trim());
            adAccountInfo.setInfo(JsonUtils.toJsonString(map));
        }
        if (body.adPlatform.equalsIgnoreCase("Google")) {
            adAccountInfo.setName(body.getName().trim());
            adAccountInfo.setAccountId(body.accountId.trim());
            adAccountInfo.setInfo(JsonUtils.toJsonString(map));
        }

        adAccountInfo.setAdPlatform(body.getAdPlatform());
        User user = userMapper.get(userName);
        adAccountInfo.setUserName(user.getName());
        adAccountInfo.setPartner(user.getPartner());
        adAccountInfo.setStatus(AdResource.StatusEnabled);
        int n = adAccountInfoMapper.add(adAccountInfo);
        if (n > 0) {
            return ResponseUtils.ok();
        } else {
            return ResponseUtils.error( -1, "更新失败");
        }
    }
}
