package com.afs.controller;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-cms")
class PublicAdCaseController {
    private final JdbcTemplate database;

    PublicAdCaseController(JdbcTemplate database) {
        this.database = database;
    }

    @GetMapping("/cms/console/public-ad-cases")
    List<Metric> list() {
        return database.query("""
                SELECT case_id, platform, advertiser, measurement_scope, metric_name,
                       metric_value, metric_unit, source_url
                FROM public_ad_case_metric
                ORDER BY case_id, metric_code
                """, (row, index) -> new Metric(
                row.getString("case_id"), row.getString("platform"), row.getString("advertiser"),
                row.getString("measurement_scope"), row.getString("metric_name"), row.getBigDecimal("metric_value"),
                row.getString("metric_unit"), row.getString("source_url")));
    }

    record Metric(String caseId, String platform, String advertiser, String scope,
                  String name, BigDecimal value, String unit, String source) {}
}
