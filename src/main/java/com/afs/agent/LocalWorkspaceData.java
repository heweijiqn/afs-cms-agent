package com.afs.agent;

import com.afs.utils.ResponseUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-cms")
@Order(0)
class LocalWorkspaceData implements ApplicationRunner {
    private final JdbcTemplate database;
    private final TransactionTemplate transactions;
    private final HistoricalBusinessArchive archive;
    private final HistoricalLogReport history;

    LocalWorkspaceData(JdbcTemplate database, TransactionTemplate transactions,
                       HistoricalBusinessArchive archive, HistoricalLogReport history) {
        this.database = database;
        this.transactions = transactions;
        this.archive = archive;
        this.history = history;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException, SQLException {
        Path key = Path.of("data", "local-admin.key");
        Files.createDirectories(key.getParent());
        if (!Files.exists(key)) Files.writeString(key, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
        String localAdminKey = Files.readString(key).strip();
        if (database.queryForObject("SELECT COUNT(*) FROM `user` WHERE name = 'local-admin'", Integer.class) == 0) {
            database.update("""
                    INSERT INTO `user` (name, password, root, partner, status, auto_article, auto_campaign, show_material,
                        display_name, email, phone, department, job_title, timezone, avatar_color, `desc`)
                    VALUES ('local-admin', ?, 1, 'AFS Media HQ', 1, TRUE, TRUE, TRUE,
                        '林泽楷 (Admin)', 'admin@afs-media.com', '+86 186-2109-8860',
                        '海外商业化增长中心', '系统超级管理员 · 投放总监', 'Asia/Shanghai', 'teal',
                        '负责全渠道（TikTok / Meta / Google / Taboola + AdSense）投放预算审批、规则风控与系统权限管理。')
                    """, localAdminKey);
        } else {
            // 仅当账号密码为空时补充默认密码，保留用户自行修改后的登录密码。
            database.update("""
                    UPDATE `user`
                    SET password = CASE WHEN COALESCE(password, '') = '' THEN ? ELSE password END,
                        root = 1, status = 1,
                        display_name = CASE WHEN COALESCE(display_name, '') = '' THEN '林泽楷 (Admin)' ELSE display_name END,
                        email = CASE WHEN COALESCE(email, '') = '' THEN 'admin@afs-media.com' ELSE email END,
                        phone = CASE WHEN COALESCE(phone, '') = '' THEN '+86 186-2109-8860' ELSE phone END,
                        department = CASE WHEN COALESCE(department, '') = '' THEN '海外商业化增长中心' ELSE department END,
                        job_title = CASE WHEN COALESCE(job_title, '') = '' THEN '系统超级管理员 · 投放总监' ELSE job_title END,
                        `desc` = CASE WHEN COALESCE(`desc`, '') = '' THEN '负责全渠道（TikTok / Meta / Google / Taboola + AdSense）投放预算审批、规则风控与系统权限管理。' ELSE `desc` END
                    WHERE name = 'local-admin'
                    """, localAdminKey);
        }
        seedAdminAlias(localAdminKey);
        seedOperatorProfiles(localAdminKey);
        transactions.executeWithoutResult(status -> {
            if (migrated("iso-639-languages-v1")) return;
            var languages = Arrays.stream(Locale.getISOLanguages()).map(Locale::forLanguageTag).distinct()
                    .map(language -> new Object[]{language.getLanguage(), language.getDisplayLanguage(Locale.SIMPLIFIED_CHINESE),
                            language.getLanguage()}).toList();
            database.batchUpdate("""
                    INSERT INTO lang(`value`, label, site)
                    SELECT ?, ?, '' WHERE NOT EXISTS (SELECT 1 FROM lang WHERE `value` = ?)
                    """, languages);
            database.update("INSERT INTO local_migration(name) VALUES ('iso-639-languages-v1')");
        });
        if (!archive.available() || (migrated("imported-business-v1") && migrated("archived-operators-v1"))) return;
        try (var connection = DriverManager.getConnection(archive.jdbcUrl(), "sa", "")) {
            var source = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            transactions.executeWithoutResult(status -> {
                if (!migrated("imported-business-v1")) importArchive(source);
                if (!migrated("archived-operators-v1")) {
                    copy(source, """
                            SELECT name, MIN(created_at), MAX(updated_at)
                            FROM archive_user WHERE name IS NOT NULL AND TRIM(name) <> '' GROUP BY name
                            """, """
                            INSERT INTO `user`(name, ctime, utime, password, root, status)
                            SELECT ?, ?, ?, '', 0, 0
                            WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE name = ?)
                            """, true);
                    database.update("INSERT INTO local_migration(name) VALUES ('archived-operators-v1')");
                }
            });
        }
    }

    private boolean migrated(String name) {
        return database.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, name) > 0;
    }

    private void seedAdminAlias(String localAdminKey) {
        try {
            Integer exists = database.queryForObject("SELECT COUNT(*) FROM `user` WHERE name = 'admin'", Integer.class);
            if (exists == null || exists == 0) {
                database.update("""
                        INSERT INTO `user` (name, password, root, partner, status, auto_article, auto_campaign, show_material,
                            display_name, email, phone, department, job_title, timezone, avatar_color, `desc`)
                        VALUES ('admin', ?, 1, 'AFS Media HQ', 1, TRUE, TRUE, TRUE,
                            '林泽楷 (Admin)', 'admin@afs-media.com', '+86 186-2109-8860',
                            '海外商业化增长中心', '系统超级管理员 · 投放总监', 'Asia/Shanghai', 'teal',
                            '负责全渠道（TikTok / Meta / Google / Taboola + AdSense）投放预算审批、规则风控与系统权限管理。')
                        """, localAdminKey);
            }
        } catch (Exception ignored) {
            // Keep resilient across minimal test schemas.
        }
    }

    private void seedOperatorProfiles(String localAdminKey) {
        String[][] profiles = {
                {"alex.chen", "陈亚历 (Alex Chen)", "alex.chen@afs-media.com", "+86 138-1024-5501", "北美 TikTok 增长组", "北美 TikTok 买量负责人", "teal"},
                {"sarah.lin", "林莎拉 (Sarah Lin)", "sarah.lin@afs-media.com", "+86 139-2088-6102", "Meta 信息流增长组", "Meta 信息流增长经理", "indigo"},
                {"david.wu", "吴大卫 (David Wu)", "david.wu@afs-media.com", "+86 137-6120-7303", "Google 搜索与展示组", "Google 搜索投放经理", "amber"},
                {"emily.zhao", "赵艾米 (Emily Zhao)", "emily.zhao@afs-media.com", "+86 150-2190-8404", "Taboola 原生内容组", "Taboola 原生内容优化师", "rose"},
                {"kevin.zhou", "周凯文 (Kevin Zhou)", "kevin.zhou@afs-media.com", "+86 185-1620-9505", "北美高客单垂直组", "北美高客单垂直投放师", "violet"},
                {"claire.huang", "黄克莱 (Claire Huang)", "claire.huang@afs-media.com", "+86 136-7188-1606", "欧洲小语种投放组", "欧洲德法市场投放专员", "teal"},
                {"ryan.li", "李瑞安 (Ryan Li)", "ryan.li@afs-media.com", "+86 158-0192-2707", "智能风控与商业化组", "自动化规则与风控运营", "indigo"},
                {"sophia.wang", "王苏菲 (Sophia Wang)", "sophia.wang@afs-media.com", "+86 186-2155-3808", "创意素材与转化组", "创意素材与落地页专家", "rose"}
        };
        for (String[] p : profiles) {
            try {
                database.update("""
                        UPDATE `user`
                        SET password = CASE WHEN COALESCE(password, '') = '' OR password LIKE 'ops-%' THEN ? ELSE password END,
                            status = 1,
                            display_name = CASE WHEN COALESCE(display_name, '') = '' THEN ? ELSE display_name END,
                            email = CASE WHEN COALESCE(email, '') = '' THEN ? ELSE email END,
                            phone = CASE WHEN COALESCE(phone, '') = '' THEN ? ELSE phone END,
                            department = CASE WHEN COALESCE(department, '') = '' THEN ? ELSE department END,
                            job_title = CASE WHEN COALESCE(job_title, '') = '' THEN ? ELSE job_title END,
                            avatar_color = CASE WHEN COALESCE(avatar_color, '') = '' OR avatar_color = 'teal' THEN ? ELSE avatar_color END
                        WHERE name = ?
                        """, localAdminKey, p[1], p[2], p[3], p[4], p[5], p[6], p[0]);
            } catch (Exception ignored) {
                // Schema columns may not exist in minimal unit tests.
            }
        }
    }

    private void importArchive(JdbcTemplate source) {
        copy(source, """
                SELECT a.id, a.title, CAST(a.terms AS VARCHAR), a.category, a.lang,
                       CAST(a.first_part AS VARCHAR), CAST(a.body AS VARCHAR), a.online,
                       COALESCE((SELECT MIN(c.site) FROM archive_category c
                                 WHERE c.code = a.category OR a.category LIKE CONCAT(c.code, '/%')
                                    OR a.category LIKE CONCAT(c.code, ':%')), 'local')
                FROM archive_article a
                """, """
                INSERT INTO article_info(article_id, title, terms, category, lang, first_part, text_list, online, site,
                                         inside_show, state, status, other_info, partner)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'PASS', 1, '{}', 'local'
                WHERE NOT EXISTS (SELECT 1 FROM article_info WHERE article_id = ?)
                """, true);
        copy(source, "SELECT site, code, label FROM archive_category",
                "MERGE INTO category(site, `value`, label) KEY(site, `value`) VALUES (?, ?, ?)", false);
        copy(source, "SELECT site, parent_code, code, label FROM archive_category_child",
                "MERGE INTO category_child(site, `value`, value_child, label_child) KEY(site, `value`, value_child) VALUES (?, ?, ?, ?)", false);
        copy(source, """
                SELECT app_id, MAX(name), MAX(os), MAX(ratio), MAX(status)
                FROM (SELECT app_id, name, os, ratio, status FROM archive_api_app
                      UNION ALL SELECT app_id, name, os, ratio, status FROM archive_app) a
                WHERE app_id IS NOT NULL GROUP BY app_id
                """, "MERGE INTO app_info(app_id, name, os, ratio, status) KEY(app_id) VALUES (?, ?, ?, ?, ?)", false);
        copy(source, "SELECT ad_id, app_id, name, ratio, ad_type, on_off, status FROM archive_ad",
                "MERGE INTO ad_info(ad_id, app_id, name, ratio, type, on_off, status) KEY(app_id, ad_id) VALUES (?, ?, ?, ?, ?, ?, ?)", false);
        copy(source, "SELECT id, app_id, ad_id, day_time, country, pv, total, ecpm, status FROM archive_ad_metric",
                "MERGE INTO ad_data(id, app_id, ad_id, day_time, country, pv, total, ecpm, status) KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", false);
        copy(source, "SELECT user_name, app_id, access_level, created_at, updated_at FROM archive_user_access",
                "MERGE INTO user_access(user_name, app_id, access, ctime, utime) KEY(user_name, app_id) VALUES (?, ?, ?, ?, ?)", false);
        database.update("INSERT INTO local_migration(name) VALUES ('imported-business-v1')");
    }

    private void copy(JdbcTemplate source, String select, String insert, boolean repeatFirstColumn) {
        var rows = source.query(select, (result, index) -> {
            int columns = result.getMetaData().getColumnCount();
            Object[] row = new Object[columns + (repeatFirstColumn ? 1 : 0)];
            for (int column = 1; column <= columns; column++) row[column - 1] = result.getObject(column);
            if (repeatFirstColumn) row[columns] = row[0];
            return row;
        });
        database.batchUpdate(insert, rows);
    }

    @GetMapping("/cms/console/archive-data")
    Object archive() throws SQLException {
        return ResponseUtils.result(archive.read());
    }

    @GetMapping("/cms/console/log-report")
    HistoricalLogReport.Report logReport() throws IOException {
        return history.read();
    }
}
