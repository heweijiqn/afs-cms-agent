package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ConsoleSimulationController {
    private final CmsReportSource reports;
    private final LegacyContext context;
    private final HistoricalLogReport history;
    private final HistoricalBusinessArchive archive;
    private final boolean requireStaffLogin;

    ConsoleSimulationController(CmsReportSource reports, LegacyContext context, HistoricalLogReport history,
                                HistoricalBusinessArchive archive,
                                @Value("${cms.agent.require-staff-login:false}") boolean requireStaffLogin) {
        this.reports = reports;
        this.context = context;
        this.history = history;
        this.archive = archive;
        this.requireStaffLogin = requireStaffLogin;
    }

    @GetMapping("/cms/console/bootstrap")
    Bootstrap bootstrap() {
        return new Bootstrap("simulation", requireStaffLogin, reports.campaigns(),
                context.exampleRules(), context.exampleChannel(), history.available(), archive.available());
    }

    @GetMapping("/cms/console/log-report")
    HistoricalLogReport.Report logReport() throws IOException {
        return history.read();
    }

    @GetMapping("/cms/console/archive-data")
    Map<String, List<Map<String, Object>>> archiveData() throws SQLException {
        return archive.read();
    }

    record Bootstrap(String mode, boolean requireStaffLogin, List<CmsReportSource.Campaign> campaigns,
                     JsonNode rules, JsonNode channel, boolean hasLogReport, boolean hasArchiveData) {}
}

