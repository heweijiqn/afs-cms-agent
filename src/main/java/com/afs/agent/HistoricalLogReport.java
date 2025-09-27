package com.afs.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class HistoricalLogReport {
    private final ObjectMapper json;
    private final Path path;

    HistoricalLogReport(ObjectMapper json,
                        @Value("${cms.console.log-report-path:./data/imported-log-report.json}") String path) {
        this.json = json;
        this.path = Path.of(path);
    }

    boolean available() {
        return Files.isRegularFile(path);
    }

    Report read() throws IOException {
        if (!available()) {
            return new Report(0, List.of());
        }
        try (var input = Files.newInputStream(path)) {
            return json.readValue(input, Report.class);
        }
    }

    record Report(int files, List<Day> days) {}
    record Day(LocalDate date, long trace, long debug, long info, long warn, long error) {}
}

