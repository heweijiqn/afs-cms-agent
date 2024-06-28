package com.afs.controller;

import com.afs.dao.AdResourceMapper;
import com.afs.dao.UserMapper;
import com.afs.dao.model.User;
import com.afs.service.AdResourceService;
import com.afs.utils.Consts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-cms")
class ReportDefaultsController {
    private final JdbcTemplate jdbc;
    private final AdResourceMapper resources;
    private final AdResourceService resourceService;
    private final UserMapper users;
    private final ObjectMapper json;

    ReportDefaultsController(JdbcTemplate jdbc, AdResourceMapper resources, AdResourceService resourceService, UserMapper users, ObjectMapper json) {
        this.jdbc = jdbc;
        this.resources = resources;
        this.resourceService = resourceService;
        this.users = users;
        this.json = json;
    }

    @GetMapping("/cms/console/report-options")
    Object options(@RequestHeader(Consts.HeaderUserName) String userName) {
        User user = users.get(userName);
        boolean root = user.getRoot() == User.RootUser;
        String partner = root ? null : user.getPartner();
        var campaigns = resources.getListByQueryForGetChannelId(null, partner, null, null, null, null)
                .stream().filter(row -> root || (partner != null && partner.equals(row.getPartner())))
                .map(row -> {
                    String channel = resourceService.getCusChannelId(row.getAdResourceId(), row.getChannelId(), row.getLandPageUrl());
                    int separator = channel.indexOf(':');
                    String pubId = channel.startsWith("partner-") && separator > 8 ? channel.substring(8, separator) : "";
                    var option = new HashMap<String, Object>(Map.of("id", row.getAdResourceId(), "name", row.getCampaign(), "pubId", pubId,
                            "channelId", row.getChannelId(), "articleId", row.getArticleId(), "am", row.getAm(),
                            "country", row.getCountry(), "platform", row.getAdPlatform(), "partner", row.getPartner(),
                            "platformCampaignIds", platformCampaignIds(row.getAdPlatformInfo())));
                    String info = row.getAdPlatformInfo();
                    if (info != null && info.contains("\"localCampaignId\"")) option.put("localCampaignId", row.getAdResourceId());
                    return option;
                }).filter(row -> !row.get("pubId").equals("")).toList();
        List<String> publishers = campaigns.stream().map(row -> (String) row.get("pubId")).distinct().sorted().toList();
        var operators = root ? users.getUserByRoot(User.CommonUser).stream()
                .filter(userRow -> campaigns.stream().anyMatch(row -> row.get("partner").equals(userRow.getPartner())))
                .map(userRow -> Map.of("name", userRow.getName(), "partner", userRow.getPartner())).toList() : List.of();
        return Map.of("publishers", publishers, "campaigns", campaigns, "operators", operators);
    }

    private List<String> platformCampaignIds(String info) {
        if (info == null || info.isBlank()) return List.of();
        try {
            var node = json.readTree(info);
            var ids = new LinkedHashSet<String>();
            String createdId = node.path("platformCampaign").path("id").asText("");
            if (!createdId.isBlank()) ids.add(createdId);
            for (var legacyId : node.path("createResultInfo").path("campaignIdList")) {
                if (legacyId.asText("").matches("[0-9]+")) ids.add(legacyId.asText());
            }
            return List.copyOf(ids);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalidInfo) {
            return List.of();
        }
    }

    @GetMapping("/cms/console/report-defaults")
    Object defaults(@RequestParam(required = false) String pubId) {
        return jdbc.query("""
                SELECT c.pub_id, MAX(d.`date`) AS latest, MIN(d.`date`) AS earliest
                FROM adsense_channel c JOIN adsense_data d
                  ON d.custom_channel_id = CONCAT('partner-', c.pub_id, ':', c.channel_id)
                WHERE d.status = 1 AND (? IS NULL OR c.pub_id = ?)
                GROUP BY c.pub_id
                ORDER BY latest DESC, c.pub_id
                LIMIT 1
                """, (row, index) -> {
            LocalDate end = LocalDate.parse(row.getString("latest"));
            LocalDate earliest = LocalDate.parse(row.getString("earliest"));
            LocalDate start = end.minusDays(6);
            return Map.of("pubId", row.getString("pub_id"), "start", start.isBefore(earliest) ? earliest : start, "end", end);
        }, pubId, pubId).stream().findFirst().orElseGet(Map::of);
    }
}
