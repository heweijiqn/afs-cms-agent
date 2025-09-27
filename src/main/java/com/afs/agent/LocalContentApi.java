package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.afs.utils.ResponseUtils;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
class LocalContentApi {
    private final HistoricalBusinessArchive archive;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    LocalContentApi(HistoricalBusinessArchive archive, JdbcTemplate jdbc, ObjectMapper json) {
        this.archive = archive;
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping({"/api/article/home/list", "/api/article/search/list", "/api/article/ccc/search/list", "/api/article/category/list"})
    Object articles(@RequestParam(defaultValue = "") String searchKeyword, @RequestParam(defaultValue = "") String category)
            throws SQLException {
        String term = searchKeyword.toLowerCase(java.util.Locale.ROOT);
        return ResponseUtils.result(archive.read().getOrDefault("articles", List.of()).stream()
                .filter(row -> String.valueOf(row.get("TITLE")).toLowerCase(java.util.Locale.ROOT).contains(term))
                .filter(row -> category.isBlank() || String.valueOf(row.get("CATEGORY")).startsWith(category))
                .toList());
    }

    @GetMapping("/api/article/detail")
    Object article(@RequestParam String articleId) throws SQLException {
        return ResponseUtils.result(archive.article(articleId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archive article missing")));
    }

    @GetMapping("/api/config/get")
    Object config(@RequestHeader("Current-Site") String site, @RequestParam String name) {
        return ResponseUtils.result(setting("config", site, name));
    }

    @GetMapping("/api/report/config/detail")
    Object report(@RequestHeader("Current-Site") String site, @RequestParam String channel) {
        return ResponseUtils.result(setting("report", site, channel));
    }

    private Object setting(String kind, String site, String name) {
        return jdbc.queryForList("""
                SELECT site AS "site", name AS "name", CAST(setting_value AS VARCHAR) AS "value"
                FROM local_api_setting WHERE kind = ? AND site = ? AND name = ?
                """, kind, site, name).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Local setting missing"));
    }

    @PostMapping(path = {"/api/feedback/add", "/api/keyword/report", "/api/ip/report"}, consumes = "application/json")
    Object record(@RequestHeader("Current-Site") String site, @RequestBody JsonNode payload,
                  javax.servlet.http.HttpServletRequest request) {
        String[] parts = request.getServletPath().split("/");
        String kind = parts[2];
        if (site.isBlank() || site.length() > 80 || !payload.isObject() || payload.toString().length() > 16000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid local API payload");
        }
        var row = json.createObjectNode();
        List<String> keys = switch (kind) {
            case "feedback" -> List.of("value", "rate", "locale");
            case "keyword" -> List.of("articleId", "keyword");
            case "ip" -> List.of("ip");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        for (String key : keys) {
            JsonNode value = payload.path(key);
            if (key.equals("rate") ? !value.isIntegralNumber() : !value.isTextual() || value.asText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid local API field: " + key);
            }
            row.set(key, value);
        }
        jdbc.update("INSERT INTO local_api_event(kind, site, payload) VALUES(?, ?, ?)", kind, site, row.toString());
        return ResponseUtils.ok();
    }

    @GetMapping("/api/ip/get")
    Object ip(@RequestHeader("Current-Site") String site, @RequestParam String ip) throws Exception {
        List<String> rows = jdbc.queryForList("""
                SELECT CAST(payload AS VARCHAR) FROM local_api_event
                WHERE kind = 'ip' AND site = ? AND created_at >= CURRENT_DATE
                """, String.class, site);
        for (String row : rows) {
            if (ip.equals(json.readTree(row).path("ip").asText())) return ResponseUtils.result(Map.of("todayReport", true));
        }
        return ResponseUtils.result(Map.of("todayReport", false));
    }

    @GetMapping("/cms/console/local-api")
    Object records() {
        return Map.of("settings", jdbc.queryForList("""
                SELECT kind AS "kind", site AS "site", name AS "name", CAST(setting_value AS VARCHAR) AS "value"
                FROM local_api_setting ORDER BY kind, site, name
                """), "events", jdbc.queryForList("""
                SELECT kind AS "kind", site AS "site", CAST(payload AS VARCHAR) AS "payload", created_at AS "createdAt"
                FROM local_api_event ORDER BY id DESC LIMIT 100
                """));
    }

    @PostMapping(path = "/cms/console/local-api", consumes = "application/json")
    Object save(@javax.validation.Valid @RequestBody Setting setting) {
        jdbc.update("""
                MERGE INTO local_api_setting(kind, site, name, setting_value)
                KEY(kind, site, name) VALUES(?, ?, ?, ?)
                """, setting.kind(), setting.site(), setting.name(), setting.value());
        return Map.of("saved", true);
    }

    record Setting(@javax.validation.constraints.NotBlank @javax.validation.constraints.Pattern(regexp = "config|report") String kind,
                   @javax.validation.constraints.NotBlank @javax.validation.constraints.Size(max = 80) String site,
                   @javax.validation.constraints.NotBlank @javax.validation.constraints.Size(max = 100) String name,
                   @javax.validation.constraints.NotBlank @javax.validation.constraints.Size(max = 16000) String value) {}
}

