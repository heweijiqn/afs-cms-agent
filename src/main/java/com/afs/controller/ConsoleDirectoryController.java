package com.afs.controller;

import com.afs.controller.filter.RootAuth;
import com.afs.dao.AdAccountInfoMapper;
import com.afs.dao.TiktokPixelInfoMapper;
import com.afs.dao.UserMapper;
import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.User;
import com.afs.service.TiktokCountryIdService;
import com.afs.utils.JsonUtils;
import com.afs.utils.Consts;
import com.afs.utils.Page;
import com.afs.utils.ResponseUtils;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/cms/console")
public class ConsoleDirectoryController {
    private final UserMapper users;
    private final AdAccountInfoMapper accounts;
    private final TiktokPixelInfoMapper pixels;

    public ConsoleDirectoryController(UserMapper users, AdAccountInfoMapper accounts, TiktokPixelInfoMapper pixels) {
        this.users = users;
        this.accounts = accounts;
        this.pixels = pixels;
    }

    @RootAuth
    @GetMapping("/operators")
    public ResponseUtils.Result operators(@RequestParam int pageIndex, @RequestParam int pageSize) {
        Page<Operator> page = page(pageIndex, pageSize);
        page.list = users.getList(User.CommonUser, (pageIndex - 1) * pageSize, pageSize).stream()
                .map(this::toOperator)
                .toList();
        page.totalSize = users.countUserByRoot(User.CommonUser);
        page.maxIndex = (int) Math.ceil((double) page.totalSize / pageSize);
        return ResponseUtils.result(page);
    }

    @RootAuth
    @GetMapping("/operator")
    public ResponseUtils.Result operator(@RequestParam String name) {
        User user = users.get(name);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown operator");
        return ResponseUtils.result(toOperator(user));
    }

    private Operator toOperator(User user) {
        return new Operator(user.getName(), user.getDesc(), user.getPartner(),
                user.getSiteList() == null || user.getSiteList().isBlank() ? List.of()
                        : JsonUtils.toList(user.getSiteList(), String.class),
                user.getAdAccountList() == null || user.getAdAccountList().isBlank() ? List.of()
                        : JsonUtils.toList(user.getAdAccountList(), Long.class),
                user.getAutoArticle(), user.getAutoCampaign(), user.getShowMaterial(), user.getStatus());
    }

    @RootAuth
    @GetMapping("/ad-accounts")
    public ResponseUtils.Result adAccounts(@RequestParam int pageIndex, @RequestParam int pageSize) {
        Page<AdAccount> page = page(pageIndex, pageSize);
        page.list = accounts.getListByQuery((pageIndex - 1) * pageSize, pageSize).stream()
                .map(account -> new AdAccount(account.getId(), account.getName(), account.getAccountId(), account.getAdPlatform(),
                        account.getPartner(), account.getUserName(), account.getStatus() == AdAccountInfo.StatusEnabled))
                .toList();
        page.totalSize = accounts.countByQuery();
        page.maxIndex = (int) Math.ceil((double) page.totalSize / pageSize);
        return ResponseUtils.result(page);
    }

    @GetMapping("/campaign-choices")
    public ResponseUtils.Result campaignChoices(@RequestHeader(Consts.HeaderUserName) String userName) {
        User user = users.get(userName);
        boolean root = user.getRoot() == User.RootUser;
        List<Long> assigned = toOperator(user).adAccountList();
        List<AdAccountInfo> rows = root ? accounts.getListByQuery(0, Math.toIntExact(accounts.countByQuery()))
                : assigned.isEmpty() ? List.of() : accounts.getByIds(assigned);
        var options = rows.stream().filter(account -> account.getStatus() == AdAccountInfo.StatusEnabled)
                .map(account -> new AccountChoice(account.getId(), account.getName(), account.getAccountId(), account.getAdPlatform()))
                .toList();
        boolean hasTiktok = options.stream().anyMatch(account -> "TikTok".equalsIgnoreCase(account.platform()));
        return ResponseUtils.result(Map.of("accounts", options, "pixels", root || hasTiktok ? pixels.list() : List.of(),
                "tiktokCountries", TiktokCountryIdService.CODE_ID_MAP.keySet()));
    }

    private <T> Page<T> page(int pageIndex, int pageSize) {
        if (pageIndex < 1 || pageSize < 1 || pageSize > 100 || (long) (pageIndex - 1) * pageSize > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        Page<T> page = new Page<>();
        page.index = pageIndex;
        page.size = pageSize;
        return page;
    }

    record Operator(String name, String desc, String partner, List<String> sites, List<Long> adAccountList,
                    Boolean autoArticle, Boolean autoCampaign, Boolean showMaterial, int status) {}
    record AdAccount(Long id, String name, String accountId, String platform, String partner, String operator, boolean active) {}
    record AccountChoice(Long id, String name, String accountId, String platform) {}
}

