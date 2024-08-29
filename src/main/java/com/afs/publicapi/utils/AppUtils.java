package com.afs.publicapi.utils;

import org.apache.commons.lang3.StringUtils;

public class AppUtils {
    public static String getArticleIdFixed(String articleId) {
        if (StringUtils.isNotBlank(articleId) && articleId.contains("%")) {
            articleId = articleId.split("%")[0];
        }
        if (StringUtils.isNotBlank(articleId) && articleId.contains("?")) {
            articleId = articleId.split("\\?")[0];
        }
        return articleId;
    }
}
