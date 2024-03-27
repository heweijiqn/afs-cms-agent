//package com.afs.task;
//
//import com.afs.dao.GoogleBigqueryKeywordMapper;
//import com.afs.dao.model.GoogleBigqueryData;
//import com.afs.service.DistributeLockService;
//import com.afs.utils.DateUtils;
//import com.google.auth.oauth2.GoogleCredentials;
//import com.google.auth.oauth2.ServiceAccountCredentials;
//import com.google.cloud.bigquery.*;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.io.InputStream;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@Service
//@Slf4j
//public class GoogleBigqueryDataTask {
//
//    private static final String projectId = "kiwi-ad";
//
//    static List<String> mTableList = new ArrayList<String>() {
//        {
//            add("kiwi-ad.analytics_417271568");
//            add("kiwi-ad.analytics_426117057");
//            add("kiwi-ad.analytics_462268685");
//        }
//    };
//
//
//    @Value("${env}")
//    String env;
//    @Autowired
//    DistributeLockService distributeLockService;
//    @Autowired
//    GoogleBigqueryKeywordMapper googleBigqueryKeywordMapper;
//
////    @Async
////    @Scheduled(fixedDelay = 56 * 60 * 1000, initialDelay = 50 * 60 * 1000)
//    void handleGoogleBigqueryDataTask() {
//        log.info("handleGoogleBigqueryDataTask start");
//        if (!env.equals("prod")) {
//            return;
//        }
//        boolean lock = distributeLockService.tryLock("handleGoogleBigqueryDataTask", "handleGoogleBigqueryDataTask_value", 50 * 60 * 1000L);
//        log.info("handleGoogleBigqueryDataTask lock={}", lock);
//        if (!lock) {
//            return;
//        }
//        try {
//            InputStream serviceAccountStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("google/service/kiwi-ad-new.json");
//            GoogleCredentials credentials = ServiceAccountCredentials.fromStream(serviceAccountStream);
//            BigQuery bigquery = BigQueryOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build().getService();
//
//            for (String table : mTableList) {
//                QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(getQuerySql(table, DateUtils.getFormatDayFromToday(0))).build();
//                TableResult tableResults = bigquery.query(queryConfig);
//                Iterable<FieldValueList> values = tableResults.getValues();
//                log.info("query values={}", values);
//                for (FieldValueList fieldValueList : values) {
//                    if (fieldValueList.get(0).isNull() || fieldValueList.get(1).isNull()
//                            || fieldValueList.get(2).isNull() || fieldValueList.get(3).isNull()) {
//                        continue;
//                    }
//                    log.debug("fieldValueList={}", fieldValueList);
//                    String pd = fieldValueList.get(0).getStringValue();
//                    String articleId = fieldValueList.get(1).getStringValue();
//                    String keyword = fieldValueList.get(2).getStringValue();
//                    Integer count = fieldValueList.get(3).getNumericValue().intValue();
//
//                    GoogleBigqueryData data = new GoogleBigqueryData();
//                    data.setPd(pd);
//                    data.setArticleId("article_" + articleId);
//                    data.setKeyWord(keyword);
//                    data.setCount(count);
//                    googleBigqueryKeywordMapper.add(data);
//                }
//            }
//        } catch (Exception ex) {
//            log.error("handleGoogleBigqueryDataTask error", ex);
//        }
//        log.info("handleGoogleBigqueryDataTask end");
//    }
//
//    private String getQuerySql(String table, String date) {
//        return "SELECT\n" +
//                "    PARSE_DATE('%Y%m%d', events_all.event_date) AS pd,\n" +
//                "    (\n" +
//                "    SELECT\n" +
//                "      value.string_value\n" +
//                "    FROM\n" +
//                "      events_all.event_params\n" +
//                "    WHERE\n" +
//                "      KEY = 'articleId' and value.string_value IS NOT NULL) AS article_id,\n" +
//                "    (\n" +
//                "    SELECT\n" +
//                "      value.string_value\n" +
//                "    FROM\n" +
//                "      events_all.event_params\n" +
//                "    WHERE\n" +
//                "      KEY = 'query' and value.string_value IS NOT NULL) AS query,\n" +
//                "    count(*) as countQuery,\n" +
//                "  FROM\n" +
//                "    `" + table + ".events_intraday_*` AS events_all\n" +
//                "  WHERE\n" +
//                "  events_all.event_name='show_serp' and PARSE_DATE('%Y%m%d', events_all.event_date) >= '" + date + "' group by pd, article_id, query order by PARSE_DATE('%Y%m%d', events_all.event_date) desc;";
//    }
//
//}
