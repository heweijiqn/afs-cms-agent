package com.afs.controller.filter;

import com.afs.dao.UserMapper;
import com.afs.dao.model.User;
import com.afs.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final String CONSOLE_HEADER = "admin-console";
    private static final Set<String> LOCAL_PUBLIC_ROUTES = Set.of("/", "/health", "/cms/console/bootstrap");
    private static final Set<String> LOCAL_MAINTENANCE_ROUTES = Set.of("/open/endless/ad/resource/archive",
            "/open/adsense/data/list", "/open/ad/resource/do/last-day", "/healthX");
    private static final Set<String> CONSOLE_ADMIN_ACTIONS = Set.of(
            "/cms/ad/material/del", "/cms/ad/material/category/add", "/cms/ad/text/del",
            "/cms/ad/resource/rule/add", "/cms/ad/resource/rule/edit", "/cms/ad/resource/rule/del",
            "/cms/article/cache/clear", "/cms/ad/account/info/add",
            "/cms/adsense/account/add", "/cms/adsense/account/edit", "/cms/adsense/channel/add",
            "/cms/adsense/channel/delete", "/cms/lang/add", "/cms/lang/edit", "/cms/category/add",
            "/cms/ad/resource/rule/bind", "/cms/ad/resource/rule/unbind");

    @Autowired
    UserMapper userMapper;

    @Value("${afs.local-workspace:false}")
    private boolean localWorkspace;

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object object) throws Exception {
        if (!(object instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) object;
        Method method = handlerMethod.getMethod();
        boolean console = "1".equals(httpServletRequest.getHeader(CONSOLE_HEADER));
        String path = httpServletRequest.getRequestURI().substring(httpServletRequest.getContextPath().length());
        if (method.isAnnotationPresent(NoAuth.class) && !console && (!localWorkspace || LOCAL_PUBLIC_ROUTES.contains(path))) {
            return true;
        }
        User user = userMapper.get(httpServletRequest.getHeader(Consts.HeaderUserName));
        if (user != null && (!localWorkspace || user.status == User.StatusValid)
                && verifyAdminAuth(httpServletRequest, user.password)) {
            boolean rootRequired = method.isAnnotationPresent(RootAuth.class)
                    || (localWorkspace && LOCAL_MAINTENANCE_ROUTES.contains(path))
                    || (console && CONSOLE_ADMIN_ACTIONS.contains(path));
            if (rootRequired && user.root != User.RootUser) {
                onFailureLogin(httpServletResponse, "权限不够", console ? 403 : 401);
                return false;
            }
            KiwiContext.Context context = new KiwiContext.Context();
            context.setUser(user);
            KiwiContext.setContext(context);
            return true;
        } else {
            onFailureLogin(httpServletResponse, "账号或密码不正确", 401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        KiwiContext.clear();
    }

    private void onFailureLogin(HttpServletResponse response, String msg, int status) throws Exception {
        String result = JsonUtils.toJsonString(ResponseUtils.error(status, msg));
        response.setStatus(status);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(result);
    }

    public boolean verifyAdminAuth(HttpServletRequest request, String salt) {
        TreeMap<String, String> treeMap = new TreeMap<>();
        String timestamp = request.getHeader(Consts.HeaderTimestamp);
        String nonce = request.getHeader(Consts.HeaderNonce);
        String sigClient = request.getHeader(Consts.HeaderSignature);

        if (StringUtils.isBlank(sigClient)) {
            log.error("error to verifyAdminAuth, header:{} is empty:{}", Consts.HeaderTimestamp, treeMap);
            return false;
        }
        if (StringUtils.isBlank(nonce)) { // TODO check _n
            log.error("error to verifyAdminAuth, header:{} is empty:{}", Consts.HeaderTimestamp, treeMap);
            return false;
        }
        long _t = NumberUtils.toLong(timestamp);
        long now = System.currentTimeMillis();
        if (Math.abs(now - _t) > 12 * 60 * 60000) {
            log.error("error to verifyAdminAuth, header:{} is timeout:{}, now:{}", Consts.HeaderTimestamp, treeMap, now);
            return false;
        }
        StringBuilder sb = new StringBuilder(request.getRequestURI()).append("&")
                .append(request.getMethod()).append("&");

        TreeMap<String, String> headerMap = new TreeMap<>();
        Enumeration<String> it = request.getHeaderNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement();
            String value = request.getHeader(name);
            if (StringUtils.isNotBlank(value)) {
                if (name.startsWith(Consts.HeaderPrefix) && !name.equals(Consts.HeaderSignature)) {
                    headerMap.put(name, value);
                }
            }
        }
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        // TODO add body
        sb.append(salt).append("&");
        String plainText = sb.toString();
        String sigServer = Coder.from(plainText).md5().asHexLower();
        // 本地 CMS 的密钥文件是唯一凭据来源；兼容已有 H2 库中残留的旧密码。
        if (!sigServer.equals(sigClient) && localWorkspace && "local-admin".equals(request.getHeader(Consts.HeaderUserName))) {
            try {
                String localKey = Files.readString(Path.of("data", "local-admin.key")).strip();
                sigServer = Coder.from(sb.toString().replace(salt + "&", localKey + "&")).md5().asHexLower();
            } catch (Exception ignored) {
                // 继续按正常签名失败处理
            }
        }
        if (!sigServer.equals(sigClient)) {
            log.warn("Invalid CMS request signature for {}", request.getRequestURI());
            return false;
        }
        return true;
    }
}
