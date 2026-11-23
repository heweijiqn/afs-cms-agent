package com.afs.controller;

import com.afs.controller.filter.RootAuth;
import com.afs.utils.ResponseUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * Local-cms console view over the local API setting/event tables. The agent-sim
 * profile uses {@code LocalContentApi}, which also serves the public {@code /api/*}
 * routes; local-cms keeps those routes on the original public controllers.
 */
@RestController
@RequestMapping("/cms/console/local-api")
class LocalApiConsoleController {
    private final JdbcTemplate db;

    LocalApiConsoleController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    Object records() {
        return ResponseUtils.result(Map.of(
                "settings", db.queryForList("""
                        SELECT kind AS "kind", site AS "site", name AS "name", CAST(setting_value AS VARCHAR) AS "value"
                        FROM local_api_setting ORDER BY kind, site, name
                        """),
                "events", db.queryForList("""
                        SELECT kind AS "kind", site AS "site", CAST(payload AS VARCHAR) AS "payload", created_at AS "createdAt"
                        FROM local_api_event ORDER BY id DESC LIMIT 100
                        """)));
    }

    @RootAuth
    @PostMapping(consumes = "application/json")
    Object save(@Valid @RequestBody Setting setting) {
        db.update("""
                MERGE INTO local_api_setting(kind, site, name, setting_value)
                KEY(kind, site, name) VALUES(?, ?, ?, ?)
                """, setting.kind(), setting.site(), setting.name(), setting.value());
        return ResponseUtils.result(Map.of("saved", true));
    }

    record Setting(@NotBlank @Pattern(regexp = "config|report") String kind,
                   @NotBlank @Size(max = 80) String site,
                   @NotBlank @Size(max = 100) String name,
                   @NotBlank @Size(max = 16000) String value) {}
}
