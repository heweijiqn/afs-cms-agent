package com.afs.controller;

import com.afs.controller.filter.RootAuth;
import com.afs.dao.AdResourceMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Profile("local-cms")
@RestController
@RequestMapping("/cms/console/maintenance")
class LocalMaintenanceController {
    private static final String ADSENSE = """
            FROM adsense_data WHERE status = 1 AND product_code = 'AFS'
              AND (custom_channel_id LIKE 'partner-pub-%:%' OR custom_channel_id LIKE 'pub-%:%')
            """;
    private final JdbcTemplate db;
    private final AdResourceMapper resources;

    LocalMaintenanceController(JdbcTemplate db, AdResourceMapper resources) {
        this.db = db;
        this.resources = resources;
    }

    @RootAuth
    @GetMapping("/options")
    Map<String, Object> options() {
        var channels = db.query("""
                SELECT custom_channel_id, country_code, MIN(`date`) AS earliest, MAX(`date`) AS latest
                """ + ADSENSE + """
                GROUP BY custom_channel_id, country_code
                ORDER BY latest DESC, custom_channel_id, country_code
                """, (row, index) -> new ChannelOption(row.getString(1), row.getString(2), row.getString(3), row.getString(4)));
        var dates = db.queryForList("SELECT DISTINCT `date` " + ADSENSE + " ORDER BY `date` DESC", String.class);
        return Map.of("channels", channels, "dates", dates);
    }

    @RootAuth
    @GetMapping("/adsense")
    List<Map<String, Object>> adsense(@RequestParam String reportChannel, @RequestParam String dateStart,
                                    @RequestParam String dateEnd, @RequestParam(required = false) String countryCode) {
        if (day(dateStart).isAfter(day(dateEnd))) throw invalid("结束日期不能早于开始日期");
        if (reportChannel.isBlank()) throw invalid("请选择渠道");
        return db.queryForList("""
                SELECT `date` AS "date", country_code AS "countryCode", estimated_earnings AS "revenue",
                       individual_ad_impressions AS "impressions", clicks AS "clicks",
                       funnel_impressions AS "funnelImpressions", funnel_clicks AS "funnelClicks"
                """ + ADSENSE + """
                  AND custom_channel_id = ? AND `date` BETWEEN ? AND ?
                  AND (? IS NULL OR country_code = ?)
                ORDER BY `date` DESC, country_code
                """, reportChannel, dateStart, dateEnd, countryCode, countryCode);
    }

    @RootAuth
    @GetMapping("/daily")
    Map<String, Object> daily(@RequestParam(required = false) String date) {
        String selected = date;
        if (selected == null || selected.isBlank()) {
            selected = db.queryForObject("SELECT MAX(`date`) " + ADSENSE, String.class);
        }
        return selected == null ? Map.of("date", "", "list", List.of())
                : Map.of("date", selected, "list", activityMetrics(day(selected)));
    }

    @RootAuth
    @PostMapping("/recalculate")
    @Transactional
    public Map<String, Object> recalculate(@RequestBody ReportDate input) {
        var date = day(input.date());
        var activities = new LinkedHashMap<String, ActivityMetrics>();
        for (var row : activityMetrics(date)) {
            if (row.canUpdate()) activities.merge(row.activityId(), row, ActivityMetrics::combine);
        }
        var rows = List.copyOf(activities.values());
        for (var row : rows) {
            if (resources.updateLastData(row.activityId(), row.spend(), row.revenue(), row.profit(), row.roas()) != 1) {
                throw new IllegalStateException("活动数据发生变化，请重试");
            }
        }
        return Map.of("date", date, "updated", rows.size(), "list", rows);
    }

    private List<ActivityMetrics> activityMetrics(LocalDate date) {
        return db.query("""
                WITH revenue AS (
                    SELECT custom_channel_id,
                           CASE WHEN COUNT(DISTINCT NULLIF(ad_resource_id, '')) = 1
                                THEN MAX(NULLIF(ad_resource_id, '')) END AS activity_id,
                           COUNT(DISTINCT NULLIF(ad_resource_id, '')) AS activity_matches,
                           SUM(estimated_earnings) AS earnings,
                           SUM(tt_spend) AS tt_spend, SUM(tb_spend) AS tb_spend,
                           SUM(gg_spend) AS gg_spend, SUM(fb_spend) AS fb_spend
                    """ + ADSENSE + """
                      AND `date` = ?
                    GROUP BY custom_channel_id
                ), active_binding AS (
                    SELECT channel_id, MIN(ad_resource_id) AS activity_id FROM ad_resource
                    WHERE state = 'PASS' AND status = 1 AND channel_id <> ''
                    GROUP BY channel_id HAVING COUNT(*) = 1
                ), platform_spend AS (
                    SELECT channel_id, SUM(tt) AS tt, SUM(tb) AS tb, SUM(gg) AS gg, SUM(fb) AS fb FROM (
                        SELECT channel_id, spend AS tt, NULL AS tb, NULL AS gg, NULL AS fb FROM tiktok_data WHERE `date` = ?
                        UNION ALL SELECT channel_id, NULL, spend, NULL, NULL FROM taboola_data WHERE `date` = ?
                        UNION ALL SELECT channel_id, NULL, NULL, NULL, spend FROM meta_data WHERE `date` = ?
                        UNION ALL SELECT channel_id, NULL, NULL, spend, NULL FROM google_data WHERE `date` = ?
                    ) spend_rows GROUP BY channel_id
                )
                SELECT a.ad_resource_id, a.campaign, r.custom_channel_id,
                       COALESCE(p.tt, r.tt_spend, 0) + COALESCE(p.tb, r.tb_spend, 0)
                           + COALESCE(p.gg, r.gg_spend, 0) + COALESCE(p.fb, r.fb_spend, 0),
                       COALESCE(r.earnings, 0), (a.state = 'PASS' AND a.status = 1)
                FROM revenue r
                LEFT JOIN active_binding b ON r.activity_matches = 0
                    AND b.channel_id = SUBSTRING(r.custom_channel_id, LOCATE(':', r.custom_channel_id) + 1)
                LEFT JOIN ad_resource a ON a.ad_resource_id = COALESCE(r.activity_id, b.activity_id)
                LEFT JOIN platform_spend p ON p.channel_id = SUBSTRING(r.custom_channel_id, LOCATE(':', r.custom_channel_id) + 1)
                ORDER BY r.custom_channel_id
                """, (row, index) -> {
            double spend = row.getDouble(4);
            double earnings = row.getDouble(5);
            return new ActivityMetrics(row.getString(1), row.getString(2), row.getString(3), spend, earnings,
                    earnings - spend, spend != 0 ? earnings / spend : 0, row.getBoolean(6));
        }, date.toString(), date.toString(), date.toString(), date.toString(), date.toString());
    }

    private static LocalDate day(String value) {
        try {
            return LocalDate.parse(value == null ? "" : value);
        } catch (DateTimeParseException invalid) {
            throw invalid("请选择有效日期");
        }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    record ChannelOption(String id, String countryCode, String start, String end) {}
    record ReportDate(String date) {}
    record ActivityMetrics(String activityId, String campaign, String channel, double spend, double revenue,
                           double profit, double roas, boolean canUpdate) {
        ActivityMetrics combine(ActivityMetrics other) {
            double totalSpend = spend + other.spend;
            double totalRevenue = revenue + other.revenue;
            return new ActivityMetrics(activityId, campaign, channel + " / " + other.channel, totalSpend, totalRevenue,
                    totalRevenue - totalSpend, totalSpend != 0 ? totalRevenue / totalSpend : 0, true);
        }
    }
}
