package com.afs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges public report endpoints into the local API event log so the
 * console "API 数据管理" view can show what the public site sent.
 */
@Component
public class LocalApiEventStore {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public LocalApiEventStore(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    public void record(String kind, String site, Map<String, Object> payload) {
        try {
            Map<String, Object> row = new LinkedHashMap<>(payload);
            db.update("INSERT INTO local_api_event(kind, site, payload) VALUES(?, ?, ?)",
                    kind, site, json.writeValueAsString(row));
        } catch (Exception ignored) {
            // Event mirroring must never fail the public endpoint.
        }
    }
}
