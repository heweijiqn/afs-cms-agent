package com.afs.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class DiagnosticAgents {
    List<Finding> inspect(OptimizationWorkflow.Input input, CmsReportSource.Campaign report) {
        return List.of(monitor(input, report), audience(report), creative(report));
    }

    private Finding monitor(OptimizationWorkflow.Input input, CmsReportSource.Campaign report) {
        if (report == null) {
            return new Finding("MONITOR", "AGGREGATE_ONLY", "只有汇总数据，无法检查日度波动和日期覆盖", null);
        }
        long activeDays = report.daily().stream().filter(day -> day.spend().signum() > 0).count();
        if (activeDays < 3 || input.conversions() < 5) {
            return new Finding("MONITOR", "INSUFFICIENT", "放量前至少需要 3 个有花费的日度样本和 5 次转化", BigDecimal.valueOf(activeDays));
        }
        return new Finding("MONITOR", "READY", "已有至少 3 个有花费的日度样本和 5 次转化", BigDecimal.valueOf(activeDays));
    }

    private Finding audience(CmsReportSource.Campaign report) {
        if (report != null) {
            var best = report.segments().stream().filter(segment -> segment.conversions() >= 5 && segment.clicks() >= 30)
                    .min(Comparator.comparing(segment -> ratio(segment.spend(), segment.conversions())));
            if (best.isPresent()) {
                var segment = best.get();
                return new Finding("AUDIENCE", "CANDIDATE", "受众实验候选：" + segment.name() + "，对照组保持原定向", ratio(segment.spend(), segment.conversions()));
            }
        }
        return new Finding("AUDIENCE", "INSUFFICIENT", "受众分组转化样本不足，先收集分组数据", null);
    }

    private Finding creative(CmsReportSource.Campaign report) {
        if (report != null) {
            var variants = report.creative().stream().filter(creative -> creative.impressions() >= 1000).toList();
            if (variants.size() >= 2) {
                var best = variants.stream().max(Comparator.comparing(creative ->
                        ratio(BigDecimal.valueOf(creative.clicks()), creative.impressions()))).orElseThrow();
                return new Finding("CREATIVE", "CANDIDATE", "创意 A/B 实验候选：" + best.name() + "，用 CPA 和 ROAS 验证点击表现", ratio(BigDecimal.valueOf(best.clicks()), best.impressions()));
            }
        }
        return new Finding("CREATIVE", "INSUFFICIENT", "缺少足量的创意对照样本，先建立单变量实验", null);
    }

    private BigDecimal ratio(BigDecimal amount, long count) {
        return amount.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
    }

    record Finding(String stage, String status, String evidence, BigDecimal value) {}
}

