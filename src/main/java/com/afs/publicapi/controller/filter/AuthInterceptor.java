package com.afs.publicapi.controller.filter;

import com.afs.publicapi.dao.AppInfoMapper;
import com.afs.publicapi.dao.model.AppInfo;
import com.afs.publicapi.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    static String SIGNATURE = "signature";
    static String APP_ID = "appId";

    @Autowired
    AppInfoMapper appInfoMapper;

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object object) throws Exception {
        if (!(object instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) object;
        Method method = handlerMethod.getMethod();
        log.debug("preHandle path={}, origin={}", httpServletRequest.getRequestURI(), httpServletRequest.getHeader("Origin"));
        if (method.isAnnotationPresent(NoAuth.class)) {
            return true;
        }
        if (verifyAuth(httpServletRequest)) {
            return true;
        } else {
            onFailureLogin(httpServletResponse);
            return false;
        }
    }

    private void onFailureLogin(HttpServletResponse response) throws Exception {
        String result = JsonUtils.toJsonString(ResponseUtils.error(401, "权限验证失败"));
        response.setStatus(401);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(result);
    }

    public boolean verifyAuth(HttpServletRequest request) {
        String originalSign = request.getParameter(SIGNATURE);
        if (StringUtils.isBlank(originalSign)) {
            return false;
        }

        String appId = request.getParameter(APP_ID);
        if (StringUtils.isBlank(appId)) {
            return false;
        }

        AppInfo appInfo = appInfoMapper.getByAppId(appId);
        if (appInfo == null) {
            return false;
        }

        String[] queryString = request.getQueryString().split("&");
        Map<String, String> treeMap = new TreeMap<>();

        String[] kvs;
        for (int i = 0; i < queryString.length; i++) {
            kvs = queryString[i].split("=", 2);

            if (kvs.length == 1 && "".equals(kvs[0])) {
                continue;
            }
            if (kvs.length < 2) {
                treeMap.put(kvs[0], "");
            } else {
                treeMap.put(kvs[0], kvs[1]);
            }
        }

        // 签名串 name=value&name=value&key=apiSecretKey
        StringBuilder sortedParams = new StringBuilder();
        for (Map.Entry<String, String> param : treeMap.entrySet()) {
            if (SIGNATURE.equals(param.getKey())) {
                continue;
            }
            sortedParams.append(param.getKey()).append("=").append(param.getValue()).append("&");
        }

        sortedParams.append("secretKey=").append(appInfo.getAppSecret());
        String unconfirmedSign = Coder.from(sortedParams.toString()).md5().asHexLower();
        boolean result = StringUtils.equals(unconfirmedSign, originalSign);
        return result;
    }
}
