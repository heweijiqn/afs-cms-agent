package com.afs.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class CmsReportSource {
    private final List<Campaign> campaigns;

    CmsReportSource(ObjectMapper json) throws IOException {
        try (var source = new ClassPathResource("agent/simulation/campaigns.json").getInputStream()) {
            campaigns = List.copyOf(json.readValue(source, new TypeReference<List<Campaign>>() {}));
        }
    }

    List<Scenario> scenarios() {
        return campaigns.stream().map(campaign ->
                new Scenario(campaign.id(), campaign.campaignId(), campaign.currency(), campaign.currentBudget())).toList();
    }

    List<Campaign> campaigns() {
        return campaigns;
    }

    Campaign get(String id) {
        return campaigns.stream().filter(campaign -> campaign.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown simulation scenario"));
    }

    record Scenario(String id, String campaignId, String currency, BigDecimal currentBudget) {}
    record Day(LocalDate date, @JsonProperty("ad_spend") BigDecimal spend,
               @JsonProperty("gross_revenue") BigDecimal revenue,
               @JsonProperty("ad_impressions") long impressions, @JsonProperty("ad_clicks") long clicks,
               @JsonProperty("ad_conversion") long conversions) {}

    record Segment(String name, long impressions, long clicks, long conversions, BigDecimal spend) {}
    record Creative(String name, long impressions, long clicks) {}

    record Campaign(String id, String campaignId, String site, String channel, String currency,
                    BigDecimal currentBudget, List<Day> daily, List<Segment> segments, List<Creative> creative) {
        OptimizationWorkflow.Input toInput() {
            List<Day> sorted = daily.stream().sorted(Comparator.comparing(Day::date)).toList();
            return new OptimizationWorkflow.Input(site, channel, campaignId, currency,
                    sorted.get(0).date(), sorted.get(sorted.size() - 1).date(),
                    sorted.stream().map(Day::spend).reduce(BigDecimal.ZERO, BigDecimal::add),
                    sorted.stream().map(Day::revenue).reduce(BigDecimal.ZERO, BigDecimal::add),
                    sorted.stream().mapToLong(Day::impressions).sum(),
                    sorted.stream().mapToLong(Day::clicks).sum(),
                    sorted.stream().mapToLong(Day::conversions).sum(),
                    new BigDecimal("1.30"), new BigDecimal("50"), 100, 10);
        }
    }
}

