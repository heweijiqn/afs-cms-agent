package com.afs.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

@Component
class HistoricalBusinessArchive {
    private final Path database;
    private final String jdbcUrl;

    HistoricalBusinessArchive(@Value("${cms.console.archive-database:./data/imported-business}") String path) {
        if (path.contains(";")) throw new IllegalArgumentException("The archive path cannot contain JDBC options");
        Path base = Path.of(path).toAbsolutePath().normalize();
        database = Path.of(base + ".mv.db");
        jdbcUrl = "jdbc:h2:file:" + base.toString().replace('\\', '/')
                + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r;TRACE_LEVEL_FILE=0";
    }

    boolean available() {
        return Files.isRegularFile(database);
    }

    String jdbcUrl() {
        return jdbcUrl;
    }

    Map<String, List<Map<String, Object>>> read() throws SQLException {
        if (!available()) return Map.of();
        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            return Map.of(
                    "metrics", jdbc.queryForList("""
                            SELECT id, ad_id AS "adId", app_id AS "appId", CAST(report_date AS VARCHAR) AS "date",
                                   country, pv, total, ecpm, status
                            FROM archive_ad_metric ORDER BY report_date, id
                            """),
                    "applications", jdbc.queryForList("""
                            SELECT id, app_id AS "appId", name, os, ratio, status, 'afs-cms' AS "source" FROM archive_app
                            UNION ALL
                            SELECT id, app_id AS "appId", name, os, ratio, status, 'afs-api' AS "source" FROM archive_api_app
                            """),
                    "adUnits", jdbc.queryForList("""
                            SELECT id, ad_id AS "adId", app_id AS "appId", name, ratio,
                                   ad_type AS "adType", on_off AS "onOff", status FROM archive_ad ORDER BY id
                            """),
                    "users", jdbc.queryForList("""
                            SELECT id, name, root, status, CAST(created_at AS VARCHAR) AS "createdAt",
                                   CAST(updated_at AS VARCHAR) AS "updatedAt" FROM archive_user ORDER BY id
                            """),
                    "access", jdbc.queryForList("""
                            SELECT id, user_name AS "userName", app_id AS "appId", access_level AS "accessLevel",
                                   CAST(created_at AS VARCHAR) AS "createdAt", CAST(updated_at AS VARCHAR) AS "updatedAt"
                            FROM archive_user_access ORDER BY id
                            """),
                    "categories", jdbc.queryForList("""
                            SELECT site, code, label FROM archive_category ORDER BY site, code
                            """),
                    "childCategories", jdbc.queryForList("""
                            SELECT site, parent_code AS "parentCode", code, label
                            FROM archive_category_child ORDER BY site, parent_code, code
                            """),
                    "articles", jdbc.queryForList("""
                            SELECT id AS "articleId", title, category, lang, online FROM archive_article ORDER BY title, id
                            """));
        }
    }

    List<Map<String, Object>> article(String id) throws SQLException {
        if (!available()) return List.of();
        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            return jdbc.queryForList("""
                    SELECT id AS "articleId", title, category, lang, CAST(terms AS VARCHAR) AS "terms",
                           CAST(first_part AS VARCHAR) AS "firstP", CAST(body AS VARCHAR) AS "textList", online
                    FROM archive_article WHERE id = ?
                    """, id);
        }
    }
}

