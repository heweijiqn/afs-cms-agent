package com.afs.agent;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cms/agent/ops")
class AdOpsInsightsController {

    private final AdOpsInsightsService service;
    private final OverseasDataService observations;

    AdOpsInsightsController(AdOpsInsightsService service, OverseasDataService observations) {
        this.service = service;
        this.observations = observations;
    }

    @GetMapping("/funnel")
    Map<String, Object> funnel(@RequestParam(required = false) String platform,@RequestParam(defaultValue="USD") String currency) {
        return observations.convertDisplay(service.buildFunnelAndMatrix(platform),currency);
    }

    @GetMapping("/pacing")
    Map<String, Object> pacing(@RequestParam(required = false) String platform,@RequestParam(defaultValue="USD") String currency) {
        return observations.convertDisplay(service.buildBudgetPacing(platform),currency);
    }

    @GetMapping("/alerts/rules")
    List<Map<String, Object>> alertRules() {
        return service.listAlertRules();
    }

    @PostMapping("/alerts/rules")
    Map<String, Object> saveAlertRule(@RequestBody AlertRulePayload body,
                                      @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                      Principal principal) {
        return service.saveAlertRule(
                body.id(), body.name(), body.metric(), body.operator(),
                body.threshold(), body.severity(), body.channel(),
                body.webhookUrl(), body.enabled() == null || body.enabled(),
                actor(principal, headerUser));
    }

    @PostMapping("/alerts/rules/{id}/delete")
    Map<String, Object> deleteAlertRule(@PathVariable long id) {
        service.deleteAlertRule(id);
        return Map.of("deleted", true, "id", id);
    }

    @PostMapping("/alerts/scan")
    Map<String, Object> scanAlerts(@RequestHeader(value = "x-user-name", required = false) String headerUser,
                                   Principal principal) {
        return service.scanAndTriggerAlerts(actor(principal, headerUser));
    }

    @GetMapping("/alerts/events")
    List<Map<String, Object>> alertEvents(@RequestParam(required = false, defaultValue = "ALL") String status) {
        return service.listAlertEvents(status);
    }

    @PostMapping("/alerts/events/{id}/ack")
    Map<String, Object> ackEvent(@PathVariable long id,
                                 @RequestBody(required = false) AckPayload body,
                                 @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                 Principal principal) {
        String status = body != null && body.status() != null ? body.status() : "RESOLVED";
        return service.acknowledgeAlertEvent(id, status, actor(principal, headerUser));
    }

    @GetMapping("/dayparting")
    List<Map<String, Object>> daypartingSchedules() {
        return service.listDaypartingSchedules();
    }

    @PostMapping("/dayparting")
    Map<String, Object> saveDayparting(@RequestBody DaypartingPayload body,
                                       @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                       Principal principal) {
        return service.saveDaypartingSchedule(
                body.scopeKey(), body.label(), body.platform(),
                body.timezone(), body.matrixBits(), body.presetName(),
                actor(principal, headerUser));
    }

    @PostMapping("/campaigns/batch")
    Map<String, Object> batchCampaigns(@RequestBody BatchCampaignPayload body,
                                       @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                       Principal principal) {
        return service.batchOperateCampaigns(
                body.adResourceIds(), body.action(), body.ruleId(), body.state(),
                actor(principal, headerUser));
    }

    @PostMapping("/campaigns/{adResourceId}/clone")
    Map<String, Object> cloneCampaign(@PathVariable String adResourceId,
                                      @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                      Principal principal) {
        return service.cloneCampaign(adResourceId, actor(principal, headerUser));
    }

    private static String actor(Principal principal, String headerUser) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        if (headerUser != null && !headerUser.isBlank()) {
            return headerUser.strip();
        }
        return "operator";
    }

    record AlertRulePayload(Long id, String name, String metric, String operator,
                            BigDecimal threshold, String severity, String channel,
                            String webhookUrl, Boolean enabled) {}

    record AckPayload(String status) {}

    record DaypartingPayload(String scopeKey, String label, String platform,
                             String timezone, String matrixBits, String presetName) {}

    record BatchCampaignPayload(List<String> adResourceIds, String action, Integer ruleId, String state) {}
}
