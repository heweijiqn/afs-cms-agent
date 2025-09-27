package com.afs.agent;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Replaces fixture account identifiers with platform-shaped local identifiers. */
@Order(550)
class WorkspacePlatformIdentifiers implements ApplicationRunner {
    private static final String MIGRATION = "local-platform-account-ids-v1";
    private final JdbcTemplate db;

    WorkspacePlatformIdentifiers(JdbcTemplate db) { this.db = db; }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (db.queryForObject("SELECT COUNT(*) FROM local_migration WHERE name = ?", Integer.class, MIGRATION) > 0) return;
        List<Account> accounts = db.query("SELECT id, account_id, ad_platform FROM ad_account_info ORDER BY id",
                (rs, n) -> new Account(rs.getLong("id"), rs.getString("account_id"), rs.getString("ad_platform")));
        for (Account account : accounts) {
            String id = platformId(account.platform(), account.id());
            if (id.equals(account.accountId())) continue;
            db.update("UPDATE ad_account_info SET account_id = ? WHERE id = ?", id, account.id());
            db.update("UPDATE ad_resource SET ad_account = ? WHERE ad_account = ?", id, account.accountId());
            db.update("UPDATE tiktok_ad_group_info SET advertiser = ? WHERE advertiser = ?", id, account.accountId());
            db.update("UPDATE tiktok_data SET advertiser = ? WHERE advertiser = ?", id, account.accountId());
            db.update("UPDATE meta_data SET advertiser = ? WHERE advertiser = ?", id, account.accountId());
            db.update("UPDATE google_data SET account_name = ? WHERE account_name = ?", id, account.accountId());
            db.update("UPDATE taboola_data SET account_name = ? WHERE account_name = ?", id, account.accountId());
        }
        db.update("INSERT INTO local_migration(name) VALUES (?)", MIGRATION);
    }

    private String platformId(String platform, long sequence) {
        String p = platform == null ? "" : platform.toLowerCase();
        if (p.contains("tiktok")) return Long.toString(7100000000000000000L + sequence);
        if (p.contains("google")) return Long.toString(9876500000L + sequence);
        if (p.contains("taboola")) return Long.toString(7000000000L + sequence);
        return Long.toString(1208497000000000L + sequence); // Meta/Facebook numeric ad account id
    }

    private record Account(long id, String accountId, String platform) {}
}
