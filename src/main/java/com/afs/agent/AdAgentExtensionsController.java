package com.afs.agent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cms/agent/ext")
class AdAgentExtensionsController {

    private final AdAgentExtensionsService service;

    AdAgentExtensionsController(AdAgentExtensionsService service) {
        this.service = service;
    }

    @PostMapping("/alerts/rules/{id}/test")
    Map<String, Object> testAlertWebhook(@PathVariable long id) {
        return service.testAlertRuleWebhook(id);
    }

    @GetMapping("/patrol")
    Map<String, Object> patrolConfig() {
        return service.getPatrolConfig();
    }

    @PostMapping("/patrol")
    Map<String, Object> savePatrol(@RequestBody PatrolPayload body,
                                   @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                   Principal principal) {
        return service.savePatrolConfig(
                Boolean.TRUE.equals(body.enabled()),
                body.intervalMinutes() == null ? 60 : body.intervalMinutes(),
                body.dailyBriefHour() == null ? 9 : body.dailyBriefHour(),
                body.channel(),
                body.webhookUrl(),
                Boolean.TRUE.equals(body.autoPropose()),
                actor(principal, headerUser));
    }

    @GetMapping("/briefs")
    List<Map<String, Object>> listBriefs() {
        return service.listDailyBriefs();
    }

    @PostMapping("/brief/generate")
    Map<String, Object> generateBrief(@RequestBody(required = false) BriefPayload body,
                                      @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                      Principal principal) {
        boolean push = body != null && Boolean.TRUE.equals(body.pushWebhook());
        return service.generateDailyBrief(push, actor(principal, headerUser));
    }

    @PostMapping("/csv-analyze")
    Map<String, Object> analyzeCsv(@RequestBody CsvPayload body) {
        return service.analyzeCsvReport(body.csvText(), body.platform());
    }

    @GetMapping("/creatives/fatigue")
    List<Map<String, Object>> fatiguedCampaigns() {
        return service.detectFatiguedCampaigns();
    }

    @GetMapping("/creatives/drafts")
    List<Map<String, Object>> creativeDrafts() {
        return service.listCreativeDrafts();
    }

    @GetMapping("/creatives/ab-tests")
    List<Map<String, Object>> creativeAbTests() {
        return service.listCreativeAbTests();
    }

    @PostMapping("/creatives/generate")
    Map<String, Object> generateCreatives(@RequestBody CreativeGenPayload body,
                                          @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                          Principal principal) {
        return service.generateCreativeDrafts(
                body.campaignId(), body.campaignName(), body.platform(), body.style(),
                actor(principal, headerUser));
    }

    @PostMapping("/creatives/drafts/{id}/status")
    Map<String, Object> updateCreativeStatus(@PathVariable long id,
                                             @RequestBody StatusPayload body,
                                             @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                             Principal principal) {
        return service.updateCreativeDraftStatus(id, body.status(), actor(principal, headerUser));
    }

    @PostMapping("/five-agent/propose")
    Map<String, Object> proposeFromFiveAgent(@RequestBody FiveAgentProposePayload body,
                                             @RequestHeader(value = "x-user-name", required = false) String headerUser,
                                             Principal principal) {
        return service.createProposalFromFiveAgent(
                body.campaignId(),
                body.action(),
                body.changePercent() == null ? 0 : body.changePercent(),
                body.targetRoas() == null ? 1.25 : body.targetRoas(),
                body.rationale(),
                actor(principal, headerUser));
    }

    @GetMapping("/attribution")
    Map<String, Object> attribution() {
        return service.buildAttributionReport();
    }

    @PostMapping("/scan-interpret")
    Map<String, Object> scanInterpret(@RequestBody ScanInterpretPayload body) {
        return service.interpretAnomalyScan(body == null ? "" : body.contextText());
    }

    private static String actor(Principal principal, String headerUser) {
        return com.afs.utils.Actors.name(principal);
    }

    record PatrolPayload(Boolean enabled, Integer intervalMinutes, Integer dailyBriefHour,
                         String channel, String webhookUrl, Boolean autoPropose) {}

    record BriefPayload(Boolean pushWebhook) {}

    record CsvPayload(String csvText, String platform) {}

    record CreativeGenPayload(String campaignId, String campaignName, String platform, String style) {}

    record StatusPayload(String status) {}

    record FiveAgentProposePayload(String campaignId, String action, Integer changePercent,
                                   Double targetRoas, String rationale) {}

    record ScanInterpretPayload(String contextText) {}
}
