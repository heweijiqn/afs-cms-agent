package com.afs.controller.filter;

import com.afs.dao.UserMapper;
import com.afs.dao.model.User;
import com.afs.utils.*;
import com.afs.controller.filter.CmsJsonBodyFilter.CachedBodyRequest;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final String CONSOLE_HEADER = "admin-console";
    private static final String BODY_HASH_HEADER = "admin-body-md5";
    private static final Set<String> LOCAL_PUBLIC_ROUTES = Set.of("/", "/health", "/cms/console/bootstrap", "/error");
    private static final Set<String> LOCAL_MAINTENANCE_ROUTES = Set.of("/open/endless/ad/resource/archive",
            "/open/adsense/data/list", "/open/ad/resource/do/last-day", "/healthX");
    private static final Set<String> CONSOLE_ADMIN_ACTIONS = Set.of(
            "/cms/ad/material/del", "/cms/ad/material/category/add", "/cms/ad/text/del",
            "/cms/ad/resource/rule/add", "/cms/ad/resource/rule/edit", "/cms/ad/resource/rule/del",
            "/cms/article/cache/clear", "/cms/ad/account/info/add",
            "/cms/adsense/account/add", "/cms/adsense/account/edit", "/cms/adsense/channel/add",
            "/cms/adsense/channel/delete", "/cms/lang/add", "/cms/lang/edit", "/cms/category/add",
            "/cms/ad/resource/rule/bind", "/cms/ad/resource/rule/unbind");
    /** Signed requests are accepted for twelve minutes; each nonce can be used once. */
    private static final long SIGNATURE_WINDOW_MS = 12 * 60 * 1000L;
    private static final long NONCE_RETENTION_MS = SIGNATURE_WINDOW_MS + 60_000L;
    private static final ConcurrentHashMap<String, Long> USED_NONCES = new ConcurrentHashMap<>();

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
        if (!console && LOCAL_PUBLIC_ROUTES.contains(path)) {
            return true;
        }
        if (method.isAnnotationPresent(NoAuth.class) && !console && (!localWorkspace || LOCAL_PUBLIC_ROUTES.contains(path))) {
            return true;
        }
        User user = userMapper.get(httpServletRequest.getHeader(Consts.HeaderUserName));
        if (user != null && (!localWorkspace || user.status == User.StatusValid)
                && verifyAdminAuth(httpServletRequest, user.password)) {
            boolean rootRequired = method.isAnnotationPresent(RootAuth.class)
                    || (localWorkspace && LOCAL_MAINTENANCE_ROUTES.contains(path))
                    || CONSOLE_ADMIN_ACTIONS.contains(path);
            if (rootRequired && user.root != User.RootUser) {
                onFailureLogin(httpServletResponse, "权限不够", console ? 403 : 403);
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
        String timestamp = request.getHeader(Consts.HeaderTimestamp);
        String nonce = request.getHeader(Consts.HeaderNonce);
        String sigClient = request.getHeader(Consts.HeaderSignature);

        if (StringUtils.isBlank(sigClient) || StringUtils.isBlank(nonce)) {
            return false;
        }
        long _t = NumberUtils.toLong(timestamp);
        long now = System.currentTimeMillis();
        if (Math.abs(now - _t) > SIGNATURE_WINDOW_MS) {
            return false;
        }
        if (!verifyBodyHash(request)) {
            return false;
        }

        TreeMap<String, String> headerMap = new TreeMap<>();
        Enumeration<String> it = request.getHeaderNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement();
            String value = request.getHeader(name);
            if (StringUtils.isNotBlank(value) && name.startsWith(Consts.HeaderPrefix)
                    && !name.equals(Consts.HeaderSignature)) {
                headerMap.put(name, value);
            }
        }
        String signatureSource = signatureSource(request, headerMap, salt);
        String sigServer = Coder.from(signatureSource).md5().asHexLower();
        // 本地 CMS 的密钥文件是唯一凭据来源；兼容已有 H2 库中残留的旧密码。
        if (!sigServer.equals(sigClient) && localWorkspace && "local-admin".equals(request.getHeader(Consts.HeaderUserName))) {
            try {
                String localKey = Files.readString(Path.of("data", "local-admin.key")).strip();
                sigServer = Coder.from(signatureSource.replace(salt + "&", localKey + "&")).md5().asHexLower();
            } catch (Exception ignored) {
                // 继续按正常签名失败处理
            }
        }
        if (!sigServer.equals(sigClient)) {
            log.warn("Invalid CMS request signature for {}", request.getRequestURI());
            return false;
        }
        return acceptNonce(nonce, now);
    }

    private String signatureSource(HttpServletRequest request, TreeMap<String, String> headers, String salt) {
        String query = request.getQueryString();
        StringBuilder sb = new StringBuilder(request.getRequestURI());
        if (StringUtils.isNotBlank(query)) {
            sb.append('?').append(query);
        }
        sb.append('&').append(request.getMethod()).append('&');
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('&');
        }
        return sb.append(salt).append('&').toString();
    }

    /** JSON mutations must carry the MD5 of the exact request body; GET/multipart bodies are exempt. */
    private boolean verifyBodyHash(HttpServletRequest request) {
        String contentType = request.getContentType();
        boolean json = contentType != null && contentType.toLowerCase().startsWith("application/json");
        if (!json) return true;
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) {
            return true;
        }
        String claimed = request.getHeader(BODY_HASH_HEADER);
        if (StringUtils.isBlank(claimed)) {
            log.warn("Missing {} for {} {}", BODY_HASH_HEADER, method, request.getRequestURI());
            return false;
        }
        if (!(request instanceof CachedBodyRequest cached)) {
            log.warn("JSON body was not cached for {}", request.getRequestURI());
            return false;
        }
        String actual = Coder.from(cached.getBody()).md5().asHexLower();
        return actual.equalsIgnoreCase(claimed);
    }

    private boolean acceptNonce(String nonce, long now) {
        long cutoff = now - NONCE_RETENTION_MS;
        if (USED_NONCES.size() > 4096) {
            USED_NONCES.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
        Long previous = USED_NONCES.putIfAbsent(nonce, now);
        if (previous != null && previous >= cutoff) {
            log.warn("Rejected replayed CMS request nonce");
            return false;
        }
        if (previous != null) {
            USED_NONCES.put(nonce, now);
        }
        return true;
    }
}
