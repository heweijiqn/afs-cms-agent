package com.afs.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Commercial Overseas AdTech Operations Engine:
 * 1. Multilingual DCO copywriting & Cross-border Policy Compliance Radar
 * 2. Overseas Cohort Retention & D0-D30 Payback/LTV Analyzer
 * 3. Autonomous Unattended Smart Rules Engine (Stop-loss, Scale-up, Fatigue Rotation, Off-peak Saver)
 * 4. Dated user-supplied multi-currency exchange rates (USD, CNY, EUR, GBP, JPY)
 */
@Service
class AdOverseasOpsService {

    private static final Logger log = LoggerFactory.getLogger(AdOverseasOpsService.class);

    private final OverseasDataService observations;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final CmsCampaignDataService cmsData;
    private final AiModelGateway gateway;
    private final AgentTelemetryService telemetry;

    AdOverseasOpsService(JdbcTemplate db, ObjectMapper json,
                         CmsCampaignDataService cmsData,
                         AiModelGateway gateway,
                         AgentTelemetryService telemetry, OverseasDataService observations) {
        this.observations = observations;
        this.db = db;
        this.json = json;
        this.cmsData = cmsData;
        this.gateway = gateway;
        this.telemetry = telemetry;
    }

    // =========================================================================
    // 1. Currency Exchange Rate Utilities
    // =========================================================================

    Map<String,Object> getSupportedCurrencies() { return observations.currencies(); }

    // =========================================================================
    // 2. Multilingual DCO & Policy Compliance Radar
    // =========================================================================

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> generateLocalizedCreatives(String campaignId, String topic, String targetCountry,
                                                  String targetLanguage, String platform, String actor) {
        String country = (targetCountry == null || targetCountry.isBlank()) ? "US" : targetCountry.strip().toUpperCase(Locale.ROOT);
        String lang = (targetLanguage == null || targetLanguage.isBlank()) ? "en-US" : targetLanguage.strip();
        String plat = (platform == null || platform.isBlank()) ? "Google" : platform.strip();
        String safeTopic = (topic == null || topic.isBlank()) ? (campaignId == null ? "出海核心业务" : campaignId) : topic.strip();

        List<Map<String, String>> variants = generateLocalizedWithLlm(safeTopic, country, lang, plat);
        boolean aiPowered = !variants.isEmpty();
        if (!aiPowered) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 未生成可识别的本地化文案，请检查 AI 配置和模型响应");
        }

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, String> v : variants) {
            String headline = v.get("headline");
            String hook = v.get("hook");
            String body = v.get("body");
            String cta = v.get("cta");

            // Automatic policy compliance radar check on each generated draft
            Map<String, Object> compliance = auditComplianceInternal(headline + " " + hook + " " + body + " " + cta, plat, country);
            String compStatus = String.valueOf(compliance.getOrDefault("riskLevel", "COMPLIANT"));
            int compScore = ((Number) compliance.getOrDefault("riskScore", 95)).intValue();
            String riskTagsJson = jsonString(compliance.get("flaggedTokens"));
            String advice = String.valueOf(compliance.getOrDefault("aiRevisionSuggestion", ""));

            long id = insertLocalizationDraft(campaignId, safeTopic, country, lang, plat,
                    headline, hook, body, cta, compStatus, compScore, riskTagsJson, advice, actor);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("campaignId", campaignId);
            item.put("campaignName", safeTopic);
            item.put("targetCountry", country);
            item.put("targetLanguage", lang);
            item.put("platform", plat);
            item.put("headline", headline);
            item.put("hookText", hook);
            item.put("bodyText", body);
            item.put("ctaText", cta);
            item.put("complianceStatus", compStatus);
            item.put("complianceScore", compScore);
            item.put("riskTags", compliance.get("flaggedTokens"));
            item.put("complianceAdvice", advice);
            item.put("status", "DRAFT");
            created.add(item);
        }

        return Map.of(
                "aiPowered", aiPowered,
                "country", country,
                "language", lang,
                "platform", plat,
                "count", created.size(),
                "drafts", created
        );
    }

    private synchronized long insertLocalizationDraft(String campaignId, String topic, String country, String lang, String platform,
                                         String headline, String hook, String body, String cta,
                                         String compStatus, int compScore, String riskTags, String advice, String actor) {
        try {
            db.update("""
                    INSERT INTO ad_localization_draft(
                        campaign_id, campaign_name, target_country, target_language, platform,
                        headline, hook_text, body_text, cta_text, compliance_status, compliance_score,
                        risk_tags, compliance_advice, status, created_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)
                    """, campaignId == null ? "" : campaignId, topic, country, lang, platform,
                    headline, hook, body, cta, compStatus, compScore, riskTags, advice, actor);
            Long id = db.queryForObject("SELECT id FROM ad_localization_draft WHERE created_by=? AND headline=? ORDER BY id DESC LIMIT 1", Long.class, actor, headline);
            return id != null ? id : 1L;
        } catch (Exception ex) {
            log.error("Failed to persist ad_localization_draft: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "本地化草稿保存失败");
        }
    }

    private List<Map<String, String>> generateLocalizedWithLlm(String topic, String country, String lang, String platform) {
        String langGuide = switch (country) {
            case "DE" -> "德语（de-DE）。德国用户注重专业认证（TÜV/CE）、技术参数严谨度、质保条款与合规隐私（DSGVO）。风格严谨诚实，避免夸大。";
            case "JP" -> "日语（ja-JP）。日本用户注重敬语礼貌（です/ます调）、真实用户口碑与细腻参数。CTA 需委婉而清晰。";
            case "ES", "MX" -> "西班牙语（es-ES/es-MX）。西语市场注重亲和力、生活痛点共鸣与优惠直击，语气热情有感染力。";
            case "FR" -> "法语（fr-FR）。法国用户看重品质、设计审美与真实保障，语言自然典雅。";
            case "SA", "AE" -> "阿拉伯语（ar-SA）。中东海湾国家注重高端尊贵品质、正品行货信誉与尊享服务。";
            case "BR" -> "葡萄牙语（pt-BR）。巴西拉美用户偏好高性价比、分期付款便利与热烈社交认同。";
            case "GB" -> "英式英语（en-GB）。注重英式含蓄幽默、清晰性价比与可靠售后支持。";
            default -> "地道美式英语（en-US）。直击消费者最关心的痛点，前两秒钩子强烈，突出限时福利、透明费用与极速在线办理。";
        };

        String formatGuide = platform.toLowerCase(Locale.ROOT).contains("tiktok")
            ? "TikTok 15秒短视频分镜脚本（用目标语言编写，body 包含 0-3s Hook画面、3-10s 卖点演示、10-15s 行动号召）"
            : platform.toLowerCase(Locale.ROOT).contains("facebook") || platform.toLowerCase(Locale.ROOT).contains("meta")
            ? "Meta/Facebook 信息流广告（吸睛主标题、高点击首句Hook、痛点+卖点正文、行动号召CTA按钮）"
            : "Google 搜索/展示广告（高意图主标题、卖点副标题Hook、高转化正文、行动号召CTA）";

        String prompt = """
                你是顶尖的出海广告创意总监与跨国本地化营销专家。
                请为商品/服务主题「%s」量身打造 3 组针对「%s（%s 语言）」的高转化出海广告创意，发布渠道为「%s」。
                
                目标市场本土化指南：%s
                渠道格式指南：%s
                实际输出语言必须以指定的语言代码为准：%s。国家文化指南不能覆盖所选语言。
                
                要求：
                1. 必须使用目标国家的母语原生书写，地道生动，符合当地消费心理，严禁生硬机翻！
                2. 遵守海外广告合规政策（严禁虚假承诺100%%效果、严禁侵权或冒充官方、严禁绝对化最高级宣称）。
                3. 必须输出严格的 JSON 数组（不要包含任何 Markdown 标记或多余说明），每个元素包含 4 个字段：
                [{"headline":"目标母语主标题","hook":"目标母语钩子或卖点","body":"目标母语正文或脚本","cta":"目标母语行动按钮"}]
                """.formatted(topic, country, lang, formatGuide, langGuide, formatGuide, lang);

        String raw = callLlm("overseas_localization_agent", prompt);
        if (raw == null || raw.isBlank()) return List.of();
        try {
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start < 0 || end <= start) return List.of();
            JsonNode arr = json.readTree(raw.substring(start, end + 1));
            List<Map<String, String>> out = new ArrayList<>();
            for (JsonNode n : arr) {
                String h = n.path("headline").asText("").strip();
                String hk = n.path("hook").asText("").strip();
                String b = n.path("body").asText("").strip();
                String c = n.path("cta").asText("").strip();
                if (h.isEmpty() || b.isEmpty()) continue;
                out.add(Map.of("headline", h, "hook", hk.isEmpty() ? "Native localized hook" : hk, "body", b, "cta", c.isEmpty() ? "Learn More" : c));
                if (out.size() >= 3) break;
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    // =========================================================================
    // Policy Compliance Radar Audit (Meta / Google / TikTok / FTC)
    // =========================================================================

    Map<String, Object> auditCompliance(String text, String platform, String country, String actor) {
        return auditComplianceInternal(text, platform, country);
    }

    private Map<String, Object> auditComplianceInternal(String text, String platform, String country) {
        if (text == null || text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入待检查文案");

        // Try LLM contextual review first
        Map<String, Object> llmAudit = gateway.isConfigured() ? auditWithLlm(text, platform, country) : null;
        if (llmAudit != null && !llmAudit.isEmpty()) {
            return llmAudit;
        }

        // Deterministic Regex & Rule Engine Fallback
        return auditWithRules(text, platform);
    }

    private Map<String, Object> auditWithLlm(String text, String platform, String country) {
        String prompt = """
                你是顶尖的海外数字广告政策合规官（精通 Meta Ads、Google Ads、TikTok For Business 及美国 FTC 广告真实性法规）。
                请严谨审核以下出海广告文案在「%s」平台（目标国家「%s」）的合规风险：
                
                待审核文案：
                \"\"\"%s\"\"\"
                
                审核重点：
                1. FTC 误导性宣称（如虚假的 100%% 成功率承诺、虚假限时倒计时、隐藏扣费条款）。
                2. Meta 政策红线（针对个人身体特征/生理缺陷/财务困境的歧视性或侵犯隐私质问，如 Are you broke? Are you fat?）。
                3. Google 政策红线（未经验证的最高级字眼如 #1 in the world、Best ever、侵犯第三方商标、全部大写叹号咆哮体）。
                4. TikTok 政策红线（过度夸大、猎奇低俗、缺乏免责声明）。
                
                请严格输出 JSON 对象（严禁包含 Markdown 格式标签与多余废话）：
                {
                  "riskScore": 85,
                  "riskLevel": "COMPLIANT",
                  "flaggedTokens": ["风险词1", "风险词2"],
                  "policyViolations": ["RULE_CODE_1"],
                  "aiRevisionSuggestion": "修改后的安全且保留高转化力的建议文案"
                }
                （其中 riskScore 取 0-100 整数；riskLevel 只能是 COMPLIANT [85-100]、WARNING [60-84] 或 VIOLATION [<60]）
                """.formatted(platform, country, text);

        String raw = callLlm("compliance_radar_agent", prompt);
        if (raw == null || raw.isBlank()) return null;
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) return null;
            JsonNode n = json.readTree(raw.substring(start, end + 1));
            if(!n.isObject() || !n.path("riskScore").isIntegralNumber() || !n.path("flaggedTokens").isArray()
                || !n.path("policyViolations").isArray() || n.path("aiRevisionSuggestion").asText("").isBlank())return null;
            int score=n.path("riskScore").asInt();
            if(score<0||score>100)return null;
            String level = score >= 85 ? "COMPLIANT" : (score >= 60 ? "WARNING" : "VIOLATION");
            List<String> tokens = new ArrayList<>();
            for (JsonNode t : n.path("flaggedTokens")) {
                if (!t.asText("").isBlank()) tokens.add(t.asText().strip());
            }
            List<String> rules = new ArrayList<>();
            for (JsonNode r : n.path("policyViolations")) {
                if (!r.asText("").isBlank()) rules.add(r.asText().strip());
            }
            String sugg = n.path("aiRevisionSuggestion").asText("").strip();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("source", "AI_ASSISTED_REVIEW");
            out.put("notice", "风险提示仅供人工复核，平台审核结果以平台实际返回为准");
            out.put("riskScore", score);
            out.put("riskLevel", level);
            out.put("flaggedTokens", tokens);
            out.put("policyViolations", rules);
            out.put("aiRevisionSuggestion", sugg.isEmpty() ? "建议微调绝对化修饰词，增加真实保障条款" : sugg);
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> auditWithRules(String text, String platform) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> flagged = new ArrayList<>();
        List<String> rules = new ArrayList<>();
        int score = 95;

        // Pattern 1: Absolute guarantees & deceptive claims
        if (lower.contains("100% guaranteed") || lower.contains("100% cure") || lower.contains("get rich quick")
                || lower.contains("guaranteed profit") || lower.contains("100% free forever") || lower.contains("包赚")
                || lower.contains("绝不亏损") || lower.contains("绝对第一")) {
            flagged.add("100% Guaranteed/Absolute Promise");
            rules.add("FTC_DECEPTIVE_GUARANTEE");
            score -= 35;
        }

        // Pattern 2: Meta personal attribute interrogations
        if (lower.contains("are you broke") || lower.contains("are you fat") || lower.contains("in debt")
                || lower.contains("feeling lonely") || lower.contains("struggling financially")) {
            flagged.add("Personal Attribute Inquiry (Meta)");
            rules.add("META_PERSONAL_ATTRIBUTES");
            score -= 30;
        }

        // Pattern 3: Unverified superlatives
        if (lower.contains("#1 in the world") || lower.contains("the best on earth") || lower.contains("cheapest anywhere")
                || lower.contains("全球最便宜") || lower.contains("史上最好")) {
            flagged.add("Unsubstantiated Superlative Claim");
            rules.add("GOOGLE_UNVERIFIED_SUPERLATIVE");
            score -= 15;
        }

        // Pattern 4: Excessive exclamation marks or shouting
        if (Pattern.compile("!{3,}").matcher(text).find() || Pattern.compile("[A-Z\\s]{25,}").matcher(text).find()) {
            flagged.add("Excessive Capitalization / Exclamation");
            rules.add("PLATFORM_EDITORIAL_VIOLATION");
            score -= 10;
        }

        score = Math.max(20, Math.min(100, score));
        String level = score >= 85 ? "COMPLIANT" : (score >= 60 ? "WARNING" : "VIOLATION");
        String suggestion = flagged.isEmpty()
                ? "关键词筛查未命中已配置规则；事实、资质、授权和当地政策仍需人工复核。"
                : "检测到敏感或高危修饰词 [" + String.join(", ", flagged) + "]，建议替换为「经评测高评分」、「透明报价」、「合规保障」，避免因绝对化承诺导致广告被拒登或封户。";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "KEYWORD_RULES");
        out.put("notice", "仅完成关键词筛查，不能判断侵权、事实真实性或保证平台过审");
        out.put("riskScore", score);
        out.put("riskLevel", level);
        out.put("flaggedTokens", flagged);
        out.put("policyViolations", rules);
        out.put("aiRevisionSuggestion", suggestion);
        return out;
    }

    List<Map<String, Object>> listLocalizationDrafts() {
        try {
            return db.query("""
                    SELECT id, campaign_id, campaign_name, target_country, target_language, platform,
                           headline, hook_text, body_text, cta_text, compliance_status, compliance_score,
                           risk_tags, compliance_advice, status, created_by, created_at
                    FROM ad_localization_draft
                    ORDER BY id DESC LIMIT 50
                    """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("campaignId", rs.getString("campaign_id"));
                m.put("campaignName", rs.getString("campaign_name"));
                m.put("targetCountry", rs.getString("target_country"));
                m.put("targetLanguage", rs.getString("target_language"));
                m.put("platform", rs.getString("platform"));
                m.put("headline", rs.getString("headline"));
                m.put("hookText", rs.getString("hook_text"));
                m.put("bodyText", rs.getString("body_text"));
                m.put("ctaText", rs.getString("cta_text"));
                m.put("complianceStatus", rs.getString("compliance_status"));
                m.put("complianceScore", rs.getInt("compliance_score"));
                m.put("complianceAdvice", rs.getString("compliance_advice"));
                m.put("status", rs.getString("status"));
                m.put("createdBy", rs.getString("created_by"));
                m.put("createdAt", String.valueOf(rs.getObject("created_at")));
                return m;
            });
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取本地化草稿失败");
        }
    }

    Map<String, Object> updateLocalizationDraftStatus(long id, String status, String actor) {
        String safe = List.of("DRAFT", "PUBLISHED", "AB_TESTING", "ARCHIVED").contains(status) ? status : "PUBLISHED";
        int updated = db.update("UPDATE ad_localization_draft SET status = ? WHERE id = ?", safe, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "目标本地化草稿不存在");
        }
        if ("PUBLISHED".equals(safe) || "AB_TESTING".equals(safe)) {
            syncLocalizationToAdText();
        }
        return Map.of("id", id, "status", safe, "actor", actor);
    }

    private void syncLocalizationToAdText() {
        if (!cmsData.isLocalWorkspace()) return;
        try {
            List<Map<String, Object>> rows = db.queryForList("""
                    SELECT headline, target_country, target_language, hook_text, body_text, cta_text
                    FROM ad_localization_draft WHERE status IN ('PUBLISHED', 'AB_TESTING')
                    """);
            for (Map<String, Object> row : rows) {
                String headline = String.valueOf(row.getOrDefault("HEADLINE", "")).strip();
                if (headline.isEmpty()) continue;
                Integer exists = db.queryForObject("SELECT COUNT(*) FROM ad_text WHERE name = ?", Integer.class, headline);
                if (exists != null && exists > 0) continue;
                List<String> lines = new ArrayList<>();
                lines.add(headline + " [" + row.get("TARGET_COUNTRY") + "/" + row.get("TARGET_LANGUAGE") + "]");
                String hook = String.valueOf(row.getOrDefault("HOOK_TEXT", "")).strip();
                if (!hook.isEmpty()) lines.add("Hook: " + hook);
                String body = String.valueOf(row.getOrDefault("BODY_TEXT", "")).strip();
                if (!body.isEmpty()) lines.add(body);
                String cta = String.valueOf(row.getOrDefault("CTA_TEXT", "")).strip();
                if (!cta.isEmpty()) lines.add("CTA: " + cta);
                db.update("INSERT INTO ad_text(name, ad_text) VALUES (?, ?)", headline, json.writeValueAsString(lines));
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 3. Overseas Cohort Retention & D0-D30 Payback/LTV Analyzer
    // =========================================================================

    Map<String,Object> buildCohortAnalysis(String platform,Integer days,String currency) {
        return observations.cohort(platform,days,currency);
    }

    List<Map<String, Object>> listAutomationRules() {
        seedStandardRulesIfEmpty();
        try {
            return db.query("""
                    SELECT id, name, rule_type, scope_platform, condition_json, action_type,
                           action_params_json, dry_run, enabled, trigger_count, last_triggered_at,
                           updated_by, updated_at
                    FROM ad_smart_automation_rule
                    ORDER BY id ASC
                    """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("name", rs.getString("name"));
                m.put("ruleType", rs.getString("rule_type"));
                m.put("scopePlatform", rs.getString("scope_platform"));
                m.put("conditionJson", rs.getString("condition_json"));
                m.put("actionType", rs.getString("action_type"));
                m.put("actionParamsJson", rs.getString("action_params_json"));
                m.put("dryRun", rs.getBoolean("dry_run"));
                m.put("enabled", rs.getBoolean("enabled"));
                m.put("triggerCount", rs.getInt("trigger_count"));
                m.put("lastTriggeredAt", rs.getObject("last_triggered_at") == null ? null : String.valueOf(rs.getObject("last_triggered_at")));
                m.put("updatedBy", rs.getString("updated_by"));
                m.put("updatedAt", String.valueOf(rs.getObject("updated_at")));
                return m;
            });
        } catch (Exception ex) {
            log.error("listAutomationRules error: {}", ex.getMessage());
            return List.of();
        }
    }

    private synchronized void seedStandardRulesIfEmpty() {
        try {
            Integer seeded=db.queryForObject("SELECT COUNT(*) FROM local_api_setting WHERE kind='automation' AND site='local' AND name='rules_initialized'",Integer.class);
            if(seeded!=null&&seeded>0)return;
            Integer count = db.queryForObject("SELECT COUNT(*) FROM ad_smart_automation_rule", Integer.class);
            if (count != null && count > 0) {
                db.update("MERGE INTO local_api_setting(kind,site,name,setting_value) KEY(kind,site,name) VALUES('automation','local','rules_initialized','true')");return;
            }

            db.update("""
                    INSERT INTO ad_smart_automation_rule(name, rule_type, scope_platform, condition_json, action_type, action_params_json, dry_run, enabled, updated_by)
                    VALUES
                    ('海外全渠道-空耗止损熔断', 'STOP_LOSS', 'ALL',
                     '{"minSpend":50.0,"maxConversions":0,"lookbackHours":24}',
                     'PAUSE_CAMPAIGN', '{"pauseReason":"当日消耗超标且无转化"}', TRUE, TRUE, 'system'),
                    
                    ('优质爆品-梯级放量追投', 'SCALE_UP', 'ALL',
                     '{"minRoas":1.30,"minPacingPct":80.0,"budgetDeltaPct":15}',
                     'ADJUST_BUDGET', '{"budgetDeltaPct":15,"maxDailyBudget":600.0}', TRUE, TRUE, 'system'),
                    
                    ('素材点击率衰退提醒', 'FATIGUE_ROTATE', 'ALL',
                     '{"minImpressions":1000,"ctrDeclinePct":25.0}',
                     'SWAP_CREATIVE', '{"action":"NOTIFY_AND_SWAP_BACKUP"}', TRUE, TRUE, 'system'),
                    
                    ('欧美时差-非高峰节能巡航', 'OFF_PEAK_SAVER', 'ALL',
                     '{"offPeakHours":"01:00-06:00","bidDiscountPct":30}',
                     'ADJUST_BUDGET', '{"bidDiscountPct":30}', TRUE, TRUE, 'system')
                    """);
            db.update("MERGE INTO local_api_setting(kind,site,name,setting_value) KEY(kind,site,name) VALUES('automation','local','rules_initialized','true')");
        } catch (Exception ex) {
            log.warn("seedStandardRulesIfEmpty failed: {}", ex.getMessage());
        }
    }

    Map<String, Object> saveAutomationRule(Long id, String name, String ruleType, String scopePlatform,
                                          String conditionJson, String actionType, String actionParamsJson,
                                          Boolean dryRun, Boolean enabled, String actor) {
        String safeName = (name == null || name.isBlank()) ? "自定义自动化规则" : name.strip();
        String safeType = (ruleType == null || ruleType.isBlank()) ? "STOP_LOSS" : ruleType.strip().toUpperCase(Locale.ROOT);
        String safePlat = (scopePlatform == null || scopePlatform.isBlank()) ? "ALL" : scopePlatform.strip();
        String safeCond = (conditionJson == null || conditionJson.isBlank()) ? "{}" : conditionJson.strip();
        String safeAction = (actionType == null || actionType.isBlank()) ? "PAUSE_CAMPAIGN" : actionType.strip().toUpperCase(Locale.ROOT);
        String safeParams = (actionParamsJson == null || actionParamsJson.isBlank()) ? "{}" : actionParamsJson.strip();
        boolean safeDryRun = dryRun == null || dryRun;
        boolean safeEnabled = enabled == null || enabled;

        if(!List.of("STOP_LOSS","SCALE_UP","FATIGUE_ROTATE","OFF_PEAK_SAVER").contains(safeType)
            || !List.of("PAUSE_CAMPAIGN","ADJUST_BUDGET","SWAP_CREATIVE").contains(safeAction))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不支持的规则或动作");
        String expected=safeType.equals("STOP_LOSS")?"PAUSE_CAMPAIGN":safeType.equals("FATIGUE_ROTATE")?"SWAP_CREATIVE":"ADJUST_BUDGET";
        if(!safeAction.equals(expected))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"动作与规则类型不匹配");
        JsonNode conditions=parseObject(safeCond), parameters=parseObject(safeParams);
        try {java.time.ZoneId.of(conditions.path("timezone").asText("UTC"));
            if(safeType.equals("OFF_PEAK_SAVER")){String[] hours=conditions.path("offPeakHours").asText("01:00-06:00").split("-");if(hours.length!=2||hours[0].equals(hours[1]))throw new IllegalArgumentException();java.time.LocalTime.parse(hours[0]);java.time.LocalTime.parse(hours[1]);}
        }catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"时区或低峰时段无效");}
        for(String key:List.of("minSpend","maxConversions","minRoas","minPacingPct","ctrDeclinePct","minImpressions")) {
            if(conditions.has(key)&&(!conditions.get(key).isNumber()||conditions.get(key).asDouble()<0||!Double.isFinite(conditions.get(key).asDouble())))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"规则阈值须为非负数字");
        }
        double delta=parameters.path("budgetDeltaPct").asDouble(15),discount=parameters.path("bidDiscountPct").asDouble(30);
        if(!Double.isFinite(delta)||delta < 0||delta > 50||!Double.isFinite(discount)||discount<0||discount>50||parameters.path("maxDailyBudget").asDouble(600)<=0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"预算调整幅度须在 ±50% 以内，预算上限须大于 0");
        if (id != null && id > 0) {
            db.update("""
                    UPDATE ad_smart_automation_rule
                    SET name=?, rule_type=?, scope_platform=?, condition_json=?, action_type=?,
                        action_params_json=?, dry_run=?, enabled=?, updated_by=?, updated_at=CURRENT_TIMESTAMP
                    WHERE id=?
                    """, safeName, safeType, safePlat, safeCond, safeAction, safeParams, safeDryRun, safeEnabled, actor, id);
        } else {
            db.update("""
                    INSERT INTO ad_smart_automation_rule(name, rule_type, scope_platform, condition_json, action_type, action_params_json, dry_run, enabled, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, safeName, safeType, safePlat, safeCond, safeAction, safeParams, safeDryRun, safeEnabled, actor);
        }

        return Map.of("saved", true, "name", safeName, "rules", listAutomationRules());
    }

    Map<String, Object> deleteAutomationRule(long id) {
        int deleted = db.update("DELETE FROM ad_smart_automation_rule WHERE id = ?", id);
        return Map.of("deleted", deleted > 0, "id", id);
    }

    private final java.util.concurrent.atomic.AtomicBoolean evaluating = new java.util.concurrent.atomic.AtomicBoolean();

    Map<String,Object> evaluateAutomationRules(String actor) {
        if(!evaluating.compareAndSet(false,true)) throw new ResponseStatusException(HttpStatus.CONFLICT,"规则评估正在运行");
        try {
            return new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.getDataSource())).execute(transaction -> {
            List<Map<String,Object>> events=new ArrayList<>();
            List<Map<String,Object>> rules=listAutomationRules().stream().filter(r->Boolean.TRUE.equals(r.get("enabled"))).toList();
            int simulated=0,executed=0,skipped=0;
            for(var rule:rules) {
                JsonNode cond=parseObject(String.valueOf(rule.get("conditionJson")));
                JsonNode params=parseObject(String.valueOf(rule.get("actionParamsJson")));
                String platform=String.valueOf(rule.get("scopePlatform")), type=String.valueOf(rule.get("ruleType")), action=String.valueOf(rule.get("actionType"));
                var zone=java.time.ZoneId.of(cond.path("timezone").asText("UTC"));
                var today=LocalDate.now(zone);
                for(var c:cmsData.listCampaigns("","ALL".equalsIgnoreCase(platform)?"":platform,250)) {
                    if(!cmsData.hasPlatformObservation(c,today)){skipped++;continue;}
                    // Day evidence is calendar-day data in the configured report timezone; no invented hourly precision.
                    var evidence=cmsData.loadCampaignEvidence(c.campaignId(),c.site(),c.channel(),today.minusDays(7),today);
                    if(evidence==null) {skipped++;continue;}
                    var current=evidence.daily().stream().filter(d->d.date().equals(today)).findFirst().orElse(null);
                    if(current==null) {skipped++;continue;}
                    double spend=current.spend().doubleValue(),revenue=current.revenue().doubleValue();
                    double roas=spend>0?revenue/spend:0;boolean matched=false; String reason="";
                    var budgetRows=cmsData.isLocalWorkspace() && "TikTok".equalsIgnoreCase(c.platform())?db.queryForList("SELECT SUM(budget) AS budget FROM tiktok_ad_group_info WHERE ad_resource_id=? AND status=1",c.campaignId()):List.<Map<String,Object>>of();
                    double budget=budgetRows.isEmpty()?0:OverseasDataService.num(budgetRows.get(0),"budget");
                    switch(type) {
                        case "STOP_LOSS" -> { matched=spend>=cond.path("minSpend").asDouble(50)&&current.conversions()<=cond.path("maxConversions").asLong(0);reason="当日花费 "+spend+"，转化 "+current.conversions(); }
                        case "SCALE_UP" -> {matched=budget>0&&roas>=cond.path("minRoas").asDouble(1.3)&&spend/budget*100>=cond.path("minPacingPct").asDouble(85);reason="当日 ROAS "+roas+"，预算利用率 "+(budget>0?spend/budget*100:0)+"%";}
                        case "FATIGUE_ROTATE" -> {
                            long pastImps=evidence.daily().stream().filter(d->d.date().isBefore(today)).mapToLong(CmsReportSource.Day::impressions).sum();
                            long pastClicks=evidence.daily().stream().filter(d->d.date().isBefore(today)).mapToLong(CmsReportSource.Day::clicks).sum();
                            double baseline=pastImps>0?(double)pastClicks/pastImps:0,ctr=current.impressions()>0?(double)current.clicks()/current.impressions():0;
                            matched=baseline>0&&current.impressions()>=cond.path("minImpressions").asInt(1000)&&(baseline-ctr)/baseline*100>=cond.path("ctrDeclinePct").asDouble(25);
                            reason="当日 CTR "+ctr+"，前 7 日基准 CTR "+baseline+"；缺少独立触达人数，未判断频次";
                        }
                        case "OFF_PEAK_SAVER" -> {
                            String[] times=cond.path("offPeakHours").asText("01:00-06:00").split("-");
                            var start=java.time.LocalTime.parse(times[0]);var end=java.time.LocalTime.parse(times[1]);var now=java.time.LocalTime.now(zone);
                            matched=start.isBefore(end)?!now.isBefore(start)&&now.isBefore(end):!now.isBefore(start)||now.isBefore(end);
                            matched=matched&&budget>0;reason="时区 "+zone+"，低峰时段 "+cond.path("offPeakHours").asText();
                        }
                    }
                    if(!matched)continue;
                    boolean dryRun=Boolean.TRUE.equals(rule.get("dryRun"));
                    String claim=rule.get("id")+"|"+c.campaignId()+"|"+today+"|"+(dryRun?"DRY":"LOCAL");
                    try {db.update("INSERT INTO ad_automation_claim(claim_key) VALUES(?)",claim);}catch(org.springframework.dao.DuplicateKeyException duplicate){continue;}
                    String status="SIMULATED",detail="条件命中，拟执行 "+action;
                    if(dryRun)simulated++;
                    else if(!cmsData.isLocalWorkspace()) {status="BLOCKED";detail="仅支持本地工作区执行，未调用广告平台";}
                    else if(!"TikTok".equalsIgnoreCase(c.platform()) || "SWAP_CREATIVE".equals(action)) {
                        status="PENDING_MANUAL";detail="请人工复核并在广告平台执行；本地无可更新的对应资产";
                    } else {
                        try {
                            int affected;
                            if("PAUSE_CAMPAIGN".equals(action)) {
                                affected=db.update("UPDATE tiktok_ad_group_info SET ad_group_status='DISABLE' WHERE ad_resource_id=? AND status=1 AND ad_group_status<>'DISABLE'",c.campaignId());
                            } else {
                                double delta=type.equals("OFF_PEAK_SAVER")?-params.path("bidDiscountPct").asDouble(30):params.path("budgetDeltaPct").asDouble(15);
                                double cap=delta>0?params.path("maxDailyBudget").asDouble(600):Double.MAX_VALUE;
                                affected=db.update("UPDATE tiktok_ad_group_info SET budget=LEAST(budget*?,?) WHERE ad_resource_id=? AND status=1 AND (?<=0 OR budget<?)",1+delta/100,cap,c.campaignId(),delta,cap);
                            }
                            if(affected>0){status="LOCAL_UPDATED";detail="已更新 "+affected+" 条本地广告组记录，未向广告平台发送请求";executed++;}
                            else {status="NO_CHANGE";detail="无可更新的本地广告组";}
                        }catch(Exception ex){status="FAILED";detail="本地更新失败："+ex.getClass().getSimpleName();}
                    }
                    db.update("INSERT INTO ad_automation_execution_log(rule_id,rule_name,campaign_id,campaign_name,platform,trigger_reason,action_taken,is_simulated,execution_status) VALUES(?,?,?,?,?,?,?,?,?)",
                        rule.get("id"),rule.get("name"),c.campaignId(),c.name(),c.platform(),reason,detail,dryRun,status);
                    db.update("UPDATE ad_smart_automation_rule SET trigger_count=trigger_count+1,last_triggered_at=CURRENT_TIMESTAMP WHERE id=?",rule.get("id"));
                    events.add(Map.of("campaignId",c.campaignId(),"triggerReason",reason,"actionTaken",detail,"status",status));
                }
            }
            return Map.of("evaluatedRules",rules.size(),"triggeredCount",events.size(),"simulatedCount",simulated,"executedCount",executed,"skippedMissingData",skipped,"events",events,"logs",listAutomationLogs(30));
            });
        } finally {evaluating.set(false);}
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=60000,initialDelay=60000)
    public void scheduledEvaluation() {
        var enabled=db.queryForList("SELECT CAST(setting_value AS VARCHAR) AS setting_value FROM local_api_setting WHERE kind='automation' AND site='local' AND name='enabled'");
        if(enabled.isEmpty()||!"true".equalsIgnoreCase(String.valueOf(enabled.get(0).get("setting_value"))))return;
        try {evaluateAutomationRules("scheduler");}catch(Exception ex){log.warn("Scheduled rule evaluation failed: {}",ex.getClass().getSimpleName());}
    }

    Map<String,Object> schedule(Boolean enabled) {
        if(enabled!=null) db.update("MERGE INTO local_api_setting(kind,site,name,setting_value) KEY(kind,site,name) VALUES('automation','local','enabled',?)",enabled.toString());
        var rows=db.queryForList("SELECT CAST(setting_value AS VARCHAR) AS enabled FROM local_api_setting WHERE kind='automation' AND site='local' AND name='enabled'");
        return Map.of("enabled",!rows.isEmpty()&&"true".equalsIgnoreCase(String.valueOf(rows.get(0).get("enabled"))),"intervalSeconds",60);
    }

    private JsonNode parseObject(String value) {
        try {var node=json.readTree(value);if(node==null||!node.isObject())throw new IllegalArgumentException();return node;}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"规则条件或动作参数格式无效");}
    }

    List<Map<String, Object>> listAutomationLogs(int limit) {
        int safeLimit = Math.max(5, Math.min(limit, 100));
        try {
            return db.query("""
                    SELECT id, rule_id, rule_name, campaign_id, campaign_name, platform,
                           trigger_reason, action_taken, is_simulated, execution_status, created_at
                    FROM ad_automation_execution_log
                    ORDER BY id DESC LIMIT ?
                    """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("ruleId", rs.getLong("rule_id"));
                m.put("ruleName", rs.getString("rule_name"));
                m.put("campaignId", rs.getString("campaign_id"));
                m.put("campaignName", rs.getString("campaign_name"));
                m.put("platform", rs.getString("platform"));
                m.put("triggerReason", rs.getString("trigger_reason"));
                m.put("actionTaken", rs.getString("action_taken"));
                m.put("isSimulated", rs.getBoolean("is_simulated"));
                m.put("executionStatus", rs.getString("execution_status"));
                m.put("createdAt", String.valueOf(rs.getObject("created_at")));
                return m;
            }, safeLimit);
        } catch (Exception ex) {
            log.warn("listAutomationLogs error: {}", ex.getMessage());
            return List.of();
        }
    }

    Map<String, Object> clearAutomationLogs() {
        int deleted = db.update("DELETE FROM ad_automation_execution_log");
        return Map.of("cleared", true, "deleted", deleted);
    }

    // =========================================================================
    // Internal Helper Utilities
    // =========================================================================

    private String callLlm(String nodeName, String prompt) {
        if (!gateway.isConfigured()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先在渠道密钥页面配置 AI 聊天模型");
        long start = System.currentTimeMillis();
        AiModelGateway.ResolvedModel resolved=null;
        try {
            resolved = gateway.resolve();
            if (resolved == null) return null;
            var call = gateway.chat(resolved, List.of(UserMessage.from(prompt)), List.of());
            long dur = System.currentTimeMillis() - start;
            String text = (call.message() == null || call.message().text() == null) ? "" : call.message().text().strip();
            telemetry.recordNode(null, null, "afs_overseas_ops", nodeName, "OK",
                    resolved.chatModel(), call.inputTokens(), call.outputTokens(), 0, dur,
                    text.length() > 160 ? text.substring(0, 160) + "..." : text);
            return AiModelGateway.redact(resolved,text);
        } catch (Exception ex) {
            long dur = System.currentTimeMillis() - start;
            String detail=AiModelGateway.redact(resolved,AiModelGateway.describe(ex));
            telemetry.recordNode(null, null, "afs_overseas_ops", nodeName, "FAILED",
                    resolved==null?"":resolved.chatModel(), 0, 0, 0, dur, detail);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 调用失败：" + detail);
        }
    }

    private String jsonString(Object obj) {
        if (obj == null) return "[]";
        try {
            return json.writeValueAsString(obj);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private static double round(double val, int scale) {
        return BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
