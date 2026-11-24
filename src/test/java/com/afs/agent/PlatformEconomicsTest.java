package com.afs.agent;

import com.afs.utils.AppUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformEconomicsTest {

    @Test
    void generatedMetricsSatisfyRealPlatformPhysicsAndSchemaLimits() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 7);
        Set<String> campaignNames = new HashSet<>();
        BigDecimal portfolioSpend = BigDecimal.ZERO;
        BigDecimal portfolioRevenue = BigDecimal.ZERO;
        int winners = 0, core = 0, underperforming = 0;

        for (int site = 0; site < PlatformEconomics.SITE_COUNT; site++) {
            var vertical = PlatformEconomics.vertical(site);
            assertThat(vertical.categorySlug().length()).isLessThanOrEqualTo(24);
            String expectedSite = PlatformEconomics.siteCode(site);

            for (int slot = 0; slot < PlatformEconomics.SLOTS_PER_SITE; slot++) {
                String name = PlatformEconomics.campaignName(site, slot);
                assertThat(name.length()).isLessThanOrEqualTo(80);
                assertThat(campaignNames.add(name)).isTrue();

                String channel = Long.toString(4_630_219_184L + (site * 20L + slot) * 73L);
                String landingUrl = PlatformEconomics.landingPageUrl(site, slot, channel);
                assertThat(landingUrl.length()).isLessThanOrEqualTo(120);
                assertThat(AppUtils.getSite(landingUrl)).isEqualTo(expectedSite);

                var sub = PlatformEconomics.subtopic(site, slot);
                assertThat(sub.keyword().length()).isLessThanOrEqualTo(64);

                var creative = PlatformEconomics.creativeForCampaign(site, slot);
                if ("TikTok".equals(vertical.platform())) {
                    assertThat(creative.type()).isEqualTo("VIDEO");
                    assertThat(creative.dimension()).isEqualTo("1080x1920");
                    assertThat(creative.ratio()).isEqualTo("9:16");
                    assertThat(creative.duration()).isBetween(15, 35);
                } else if ("Taboola".equals(vertical.platform())) {
                    assertThat(creative.type()).isEqualTo("IMAGE");
                    assertThat(creative.dimension()).isEqualTo("1200x628");
                }

                if (!PlatformEconomics.isRunningActive(site, slot)) continue;

                List<PlatformEconomics.DailyRow> rows = PlatformEconomics.generateDailySeries(site, slot, start, 14);
                assertThat(rows).hasSize(14);
                for (var day : rows) {
                    // Monotonic two-click AFS funnel invariants
                    assertThat(day.adImpressions()).isGreaterThan(day.adClicks());
                    assertThat(day.adClicks()).isGreaterThanOrEqualTo(day.funnelImpressions());
                    assertThat(day.funnelImpressions()).isGreaterThanOrEqualTo(day.funnelClicks());
                    assertThat(day.funnelClicks()).isGreaterThanOrEqualTo(day.serpImpressions());
                    assertThat(day.serpImpressions()).isGreaterThanOrEqualTo(day.afsClicks());
                    assertThat(day.afsClicks()).isGreaterThanOrEqualTo(1L);

                    // Pixel S2S attribution health: riskValue ~ 0.90 - 1.15
                    double riskValue = (double) day.afsClicks() / day.adConversions();
                    assertThat(riskValue).isBetween(0.85, 1.18);

                    // Realistic daily ROAS (no fantasy 9x spikes)
                    double dayRoas = day.grossRevenue().doubleValue() / day.adSpend().doubleValue();
                    assertThat(dayRoas).isBetween(0.50, 1.92);
                }

                var totals = PlatformEconomics.summarize(rows);
                assertThat(totals.profit())
                        .isEqualByComparingTo(totals.grossRevenue().subtract(totals.adSpend()));
                double expectedRoas = totals.grossRevenue().divide(totals.adSpend(), 4, RoundingMode.HALF_UP).doubleValue();
                assertThat(totals.roas()).isEqualTo(expectedRoas);
                assertThat(totals.roas()).isBetween(0.58, 1.80);

                portfolioSpend = portfolioSpend.add(totals.adSpend());
                portfolioRevenue = portfolioRevenue.add(totals.grossRevenue());
                if (totals.roas() >= 1.32) winners++;
                else if (totals.roas() >= 1.00) core++;
                else underperforming++;
            }
        }

        for (var rule : PlatformEconomics.RULES) {
            assertThat(rule.name().length()).isLessThanOrEqualTo(56);
            assertThat(rule.condition().length()).isLessThanOrEqualTo(56);
            assertThat(rule.creator().length()).isLessThanOrEqualTo(24);
        }

        double blendedRoas = portfolioRevenue.doubleValue() / portfolioSpend.doubleValue();
        assertThat(blendedRoas).isBetween(1.05, 1.38);
        assertThat(winners).isGreaterThan(20);
        assertThat(core).isGreaterThan(40);
        assertThat(underperforming).isGreaterThan(25);
    }
}
