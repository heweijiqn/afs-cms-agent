package com.afs.agent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cms/agent/overseas")
class AdOverseasOpsController {

    private final AdOverseasOpsService service;

    AdOverseasOpsController(AdOverseasOpsService service) {
        this.service = service;
    }

    // =========================================================================
    // Currencies
    // =========================================================================

    @GetMapping("/currencies")
    Map<String, Object> currencies() {
        return service.getSupportedCurrencies();
    }

    // =========================================================================
    // Multilingual DCO & Policy Compliance Radar
    // =========================================================================

    @GetMapping("/localization/drafts")
    List<Map<String, Object>> listLocalizationDrafts() {
        return service.listLocalizationDrafts();
    }

    @PostMapping("/localization/generate")
    Map<String, Object> generateLocalization(@RequestBody LocalizationGenPayload body,
                                             @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                             Principal principal) {
        return service.generateLocalizedCreatives(
                body.campaignId(), body.topic(), body.targetCountry(),
                body.targetLanguage(), body.platform(),
                actor(principal, headerUser));
    }

    @PostMapping("/localization/audit")
    Map<String, Object> auditPolicyCompliance(@RequestBody AuditPayload body,
                                              @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                              Principal principal) {
        return service.auditCompliance(
                body.text(), body.platform(), body.targetCountry(),
                actor(principal, headerUser));
    }

    @PostMapping("/localization/drafts/{id}/status")
    Map<String, Object> updateLocalizationStatus(@PathVariable long id,
                                                 @RequestBody StatusPayload body,
                                                 @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                                 Principal principal) {
        return service.updateLocalizationDraftStatus(id, body.status(), actor(principal, headerUser));
    }

    // =========================================================================
    // Cohort Retention & D0-D30 Payback Analyzer
    // =========================================================================

    @GetMapping("/cohort")
    Map<String, Object> cohortAnalysis(@RequestParam(required = false) String platform,
                                       @RequestParam(required = false, defaultValue = "14") Integer days,
                                       @RequestParam(required = false, defaultValue = "USD") String currency) {
        return service.buildCohortAnalysis(platform, days, currency);
    }

    // =========================================================================
    // Autonomous Smart Rules Engine
    // =========================================================================

    @GetMapping("/automation/rules")
    List<Map<String, Object>> listAutomationRules() {
        return service.listAutomationRules();
    }

    @PostMapping("/automation/rules")
    Map<String, Object> saveAutomationRule(@RequestBody AutomationRulePayload body,
                                           @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                           Principal principal) {
        return service.saveAutomationRule(
                body.id(), body.name(), body.ruleType(), body.scopePlatform(),
                body.conditionJson(), body.actionType(), body.actionParamsJson(),
                body.dryRun(), body.enabled(), actor(principal, headerUser));
    }

    @PostMapping("/automation/rules/{id}/delete")
    Map<String, Object> deleteAutomationRule(@PathVariable long id) {
        return service.deleteAutomationRule(id);
    }

    @PostMapping("/automation/evaluate")
    Map<String, Object> evaluateAutomationRules(@RequestHeader(value = "x-user-name", required = false) String headerUser,
                                                Principal principal) {
        return service.evaluateAutomationRules(actor(principal, headerUser));
    }

    @GetMapping("/automation/logs")
    List<Map<String, Object>> listAutomationLogs(@RequestParam(required = false, defaultValue = "30") Integer limit) {
        return service.listAutomationLogs(limit == null ? 30 : limit);
    }

    @PostMapping("/automation/logs/clear")
    Map<String, Object> clearAutomationLogs() {
        return service.clearAutomationLogs();
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

    record LocalizationGenPayload(String campaignId, String topic, String targetCountry,
                                  String targetLanguage, String platform) {}

    record AuditPayload(String text, String platform, String targetCountry) {}

    record StatusPayload(String status) {}

    record AutomationRulePayload(Long id, String name, String ruleType, String scopePlatform,
                                 String conditionJson, String actionType, String actionParamsJson,
                                 Boolean dryRun, Boolean enabled) {}
}
