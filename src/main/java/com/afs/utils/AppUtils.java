package com.afs.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class AppUtils {

    public static void logTiktokTotalNumber(Map dataMap, String otherMsg) {
        try {
            Map<String, Object> pageInfo = (Map<String, Object>) dataMap.get("page_info");
            Integer totalNumber = (Integer)pageInfo.get("total_number");
            Integer totalPage = (Integer)pageInfo.get("total_page");
            int avgPerPage = totalNumber / totalPage;
            if (avgPerPage > 1000) {
                log.error("logTiktokTotalNumber error. otherMsg={}, totalNumber={},totalPage={}", otherMsg, totalNumber, totalPage);
            }
        } catch (Exception e) {
            log.error("logTotalNumber error. otherMsg={},", otherMsg, e);
        }
    }

    public static String doubleToStr0(Double d) {
        return String.format("%.0f", d);
    }

    public static String doubleToStr2(Double d) {
        return String.format("%.2f", d);
    }

    public static Double doubleToDouble2(Double d) {
        BigDecimal bd = BigDecimal.valueOf(d);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static String doubleToStr4(Double d) {
        return String.format("%.4f", d);
    }

    public static String getPassword() {
        String pre = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        String sub = UUID.randomUUID().toString().replace("-", "").substring(4, 8);
        return "#&_" + pre + "$" + sub;
    }

    public static String getUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String getUUID_10() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public static String getSite(String urlString) throws Exception {
        URL url = new URL(urlString);
        String host = url.getHost();
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        int lastDotIndex = host.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return host.substring(0, lastDotIndex);
        }
        return host;
    }

    public static Boolean parseExpression(String expression, Map<String, Object> paramMap) {
        log.debug("parseExpression expression={}, paramMap={}", expression, paramMap);
        String exp = expression.replace("ROI", "#ROI").replace("PROFIT", "#PROFIT").replace("REVENUE", "#REVENUE").replace("AD_SPEND", "#AD_SPEND");

        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("ROI", paramMap.get("ROI"));
        context.setVariable("PROFIT", paramMap.get("PROFIT"));
        context.setVariable("REVENUE", paramMap.get("REVENUE"));
        context.setVariable("AD_SPEND", paramMap.get("AD_SPEND"));
        boolean result = parser.parseExpression(exp).getValue(context, Boolean.class);
        return result;
    }

    public static String getChannelIdByLandingPageUrl(String landingPageUrl) {
        log.debug("getChannelId landingPageUrl={}", landingPageUrl);
        String[] array = landingPageUrl.split("channel=");
        String channelId;
        if (array[1].contains("&")) {
            channelId = array[1].substring(0, array[1].indexOf("&"));
        } else {
            channelId = array[1];
        }
        if (StringUtils.isNotBlank(channelId)) {
            channelId = channelId.replace(" ", "");
        }
        return channelId;
    }

    public static void main(String[] args) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ROI", 100);
        paramMap.put("SPEND", 600);
        System.out.println(parseExpression("#ROI >= 110 && #SPEND > 500", paramMap));
    }
}
