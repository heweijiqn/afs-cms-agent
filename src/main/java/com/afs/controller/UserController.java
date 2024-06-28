package com.afs.controller;

import com.afs.controller.filter.RootAuth;
import com.afs.dao.AdAccountInfoMapper;
import com.afs.dao.UserMapper;
import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.User;
import com.afs.utils.Consts;
import com.afs.utils.JsonUtils;
import com.afs.utils.ResponseUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/cms")
public class UserController {

    private static final Set<String> ALLOWED_AVATAR_COLORS = Set.of("teal", "indigo", "amber", "rose", "violet");

    @Autowired
    UserMapper userMapper;

    @Autowired(required = false)
    AdAccountInfoMapper adAccountInfoMapper;

    @Autowired(required = false)
    JdbcTemplate jdbcTemplate;

    @GetMapping("/user/login")
    public ResponseUtils.Result login(@RequestHeader(Consts.HeaderUserName) String userName) {
        try {
            userMapper.touchLastLogin(userName);
        } catch (Exception ignored) {
            // Keep login resilient across minimal test schemas.
        }
        User user = userMapper.get(userName);
        return ResponseUtils.result(buildProfileMap(user));
    }

    @GetMapping("/user/profile")
    public ResponseUtils.Result getProfile(@RequestHeader(Consts.HeaderUserName) String userName) {
        User user = userMapper.get(userName);
        if (user == null) {
            return ResponseUtils.error(404, "未找到当前登录账号");
        }
        return ResponseUtils.result(buildProfileMap(user));
    }

    @Data
    public static class ProfileUpdateBody {
        private String displayName;
        private String email;
        private String phone;
        private String department;
        private String jobTitle;
        private String timezone;
        private String avatarColor;
        private String desc;
    }

    @PostMapping("/user/profile")
    public ResponseUtils.Result updateProfile(@RequestHeader(Consts.HeaderUserName) String userName,
                                              @RequestBody ProfileUpdateBody body) {
        User current = userMapper.get(userName);
        if (current == null) {
            return ResponseUtils.error(404, "未找到当前登录账号");
        }
        current.setDisplayName(clean(body.getDisplayName(), current.getDisplayName(), 120));
        current.setEmail(clean(body.getEmail(), current.getEmail(), 200));
        current.setPhone(clean(body.getPhone(), current.getPhone(), 48));
        current.setDepartment(clean(body.getDepartment(), current.getDepartment(), 120));
        current.setJobTitle(clean(body.getJobTitle(), current.getJobTitle(), 120));
        current.setTimezone(clean(body.getTimezone(),
                StringUtils.defaultIfBlank(current.getTimezone(), "Asia/Shanghai"), 64));
        String color = clean(body.getAvatarColor(),
                StringUtils.defaultIfBlank(current.getAvatarColor(), "teal"), 24);
        current.setAvatarColor(ALLOWED_AVATAR_COLORS.contains(color) ? color : "teal");
        if (body.getDesc() != null) {
            current.setDesc(clean(body.getDesc(), "", 240));
        }
        userMapper.updateProfile(current);
        User updated = userMapper.get(userName);
        return ResponseUtils.result(buildProfileMap(updated));
    }

    @Data
    public static class PasswordUpdateBody {
        private String currentPassword;
        private String newPassword;
    }

    @PostMapping("/user/password")
    public ResponseUtils.Result updatePassword(@RequestHeader(Consts.HeaderUserName) String userName,
                                               @RequestBody PasswordUpdateBody body) {
        User current = userMapper.get(userName);
        if (current == null) {
            return ResponseUtils.error(404, "未找到当前登录账号");
        }
        String oldPwd = body.getCurrentPassword() == null ? "" : body.getCurrentPassword().strip();
        String newPwd = body.getNewPassword() == null ? "" : body.getNewPassword().strip();
        if (!StringUtils.equals(current.getPassword(), oldPwd)) {
            return ResponseUtils.error(400, "当前密码不正确");
        }
        if (newPwd.length() < 6 || newPwd.length() > 120) {
            return ResponseUtils.error(400, "新密码长度需在 6 到 120 位之间");
        }
        userMapper.updatePassword(userName, newPwd);
        return ResponseUtils.result(Map.of("updated", true));
    }

    @RootAuth
    @GetMapping("/user/list")
    public ResponseUtils.Result userList(@RequestHeader(Consts.HeaderUserName) String userName) {
        List<Map> list = new ArrayList<>();
        User loginUser = userMapper.get(userName);
        Map<String, Object> loginUserMap = new HashMap<>();
        loginUserMap.put("label", loginUser.getPartner());
        loginUserMap.put("value", loginUser.getName());
        list.add(loginUserMap);

        List<User> userList = userMapper.getUserByRoot(User.CommonUser);
        for (User user : userList) {
            Map<String, Object> map = new HashMap<>();
            map.put("label", user.getPartner());
            map.put("value", user.getName());
            list.add(map);
        }
        return ResponseUtils.result(list);
    }

    private Map<String, Object> buildProfileMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (user == null) return map;
        boolean isRoot = user.getRoot() == User.RootUser;
        String defaultDisplay = isRoot ? "林泽楷 · 投放总监" : user.getName();
        String defaultDept = isRoot ? "海外商业化增长中心" : "海外买量运营部";
        String defaultTitle = isRoot ? "系统超级管理员 · 投放总控" : "高级商业化投放优化师";
        String defaultEmail = user.getName() + "@afs-media.com";
        String defaultDesc = isRoot
                ? "负责全渠道（TikTok / Meta / Google / Taboola + AdSense）投放预算审批、规则风控与系统权限管理。"
                : "负责海外内容套利（AFS / RSOC）日常买量优化、素材迭代与国家 ROI 监控。";

        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("root", user.getRoot());
        map.put("partner", StringUtils.defaultIfBlank(user.getPartner(), isRoot ? "AFS Media HQ" : "AFS Media"));
        map.put("status", user.getStatus());
        map.put("displayName", StringUtils.defaultIfBlank(user.getDisplayName(), defaultDisplay));
        map.put("email", StringUtils.defaultIfBlank(user.getEmail(), defaultEmail));
        map.put("phone", StringUtils.defaultIfBlank(user.getPhone(), "+86 186-2109-8860"));
        map.put("department", StringUtils.defaultIfBlank(user.getDepartment(), defaultDept));
        map.put("jobTitle", StringUtils.defaultIfBlank(user.getJobTitle(), defaultTitle));
        map.put("timezone", StringUtils.defaultIfBlank(user.getTimezone(), "Asia/Shanghai"));
        map.put("avatarColor", StringUtils.defaultIfBlank(user.getAvatarColor(), "teal"));
        map.put("desc", StringUtils.defaultIfBlank(user.getDesc(), defaultDesc));
        map.put("autoArticle", isRoot || Boolean.TRUE.equals(user.getAutoArticle()));
        map.put("autoCampaign", isRoot || Boolean.TRUE.equals(user.getAutoCampaign()));
        map.put("showMaterial", isRoot || Boolean.TRUE.equals(user.getShowMaterial()));

        List<String> sites = parseStringList(user.getSiteList());
        if (isRoot && sites.isEmpty()) {
            sites = SiteController.sites();
        }
        map.put("siteList", sites);
        map.put("activeSiteCount", sites.size());

        List<Map<String, Object>> adAccounts = loadBoundAdAccounts(user, isRoot);
        map.put("boundAdAccounts", adAccounts);
        map.put("managedAccountCount", adAccounts.size());
        map.put("managedCampaignCount", queryCampaignCount(user, isRoot));
        map.put("recentOptimizationCount", queryOptimizationCount(user, isRoot));

        map.put("lastLoginAt", formatDate(user.getLastLoginAt() != null ? user.getLastLoginAt() : new Date()));
        map.put("ctime", formatDate(user.getCtime()));
        map.put("utime", formatDate(user.getUtime()));
        return map;
    }

    private List<String> parseStringList(String raw) {
        if (StringUtils.isBlank(raw)) return new ArrayList<>();
        try {
            List<String> parsed = JsonUtils.toList(raw, String.class);
            return parsed != null ? parsed : new ArrayList<>();
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> loadBoundAdAccounts(User user, boolean isRoot) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (jdbcTemplate == null) return list;
        try {
            if (isRoot) {
                return jdbcTemplate.query("""
                        SELECT id, name, account_id, ad_platform, partner, user_name
                        FROM ad_account_info
                        WHERE status = 1
                        ORDER BY id ASC
                        LIMIT 24
                        """, (rs, i) -> Map.of(
                        "id", rs.getLong("id"),
                        "name", StringUtils.defaultString(rs.getString("name")),
                        "accountId", StringUtils.defaultString(rs.getString("account_id")),
                        "platform", StringUtils.defaultString(rs.getString("ad_platform")),
                        "partner", StringUtils.defaultString(rs.getString("partner")),
                        "operator", StringUtils.defaultString(rs.getString("user_name"))
                ));
            }
            if (adAccountInfoMapper != null && StringUtils.isNotBlank(user.getAdAccountList())) {
                List<Long> ids = JsonUtils.toList(user.getAdAccountList(), Long.class);
                if (!CollectionUtils.isEmpty(ids)) {
                    for (AdAccountInfo info : adAccountInfoMapper.getByIds(ids)) {
                        list.add(Map.of(
                                "id", info.getId(),
                                "name", StringUtils.defaultString(info.getName()),
                                "accountId", StringUtils.defaultString(info.getAccountId()),
                                "platform", StringUtils.defaultString(info.getAdPlatform()),
                                "partner", StringUtils.defaultString(info.getPartner()),
                                "operator", StringUtils.defaultString(info.getUserName())
                        ));
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep resilient if ad_account_info is empty.
        }
        return list;
    }

    private int queryCampaignCount(User user, boolean isRoot) {
        if (jdbcTemplate == null) return 0;
        try {
            Integer count = isRoot
                    ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ad_resource WHERE status = 1", Integer.class)
                    : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ad_resource WHERE status = 1 AND am = ?",
                            Integer.class, user.getName());
            return count != null ? count : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int queryOptimizationCount(User user, boolean isRoot) {
        if (jdbcTemplate == null) return 0;
        try {
            Integer count = isRoot
                    ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM optimization_run", Integer.class)
                    : jdbcTemplate.queryForObject(
                            "SELECT COUNT(DISTINCT run_id) FROM optimization_audit WHERE actor = ?",
                            Integer.class, user.getName());
            return count != null ? count : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String clean(String value, String fallback, int maxLen) {
        if (value == null) return fallback == null ? "" : fallback;
        String trimmed = value.strip();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    private static String formatDate(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
}
