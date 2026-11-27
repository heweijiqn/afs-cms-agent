package com.afs.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boots the AI platform layer once the schema exists: prompt versions, eval suite, and a first
 * index build queued through the async worker rather than blocking startup.
 */
@Order(700)
@Profile("local-cms")
class AiPlatformBootstrap implements ApplicationRunner {
    private final PromptRegistry prompts;
    private final AiEvalService eval;
    private final RagService rag;
    private final AiJobQueue jobs;
    private final JdbcTemplate db;

    AiPlatformBootstrap(PromptRegistry prompts, AiEvalService eval, RagService rag, AiJobQueue jobs,
                        JdbcTemplate db) {
        this.prompts = prompts;
        this.eval = eval;
        this.rag = rag;
        this.jobs = jobs;
        this.db = db;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            prompts.seedDefaults();
            eval.seedDefaults();
            if (rag.isEmpty()) {
                jobs.enqueue("rag.rebuild", Map.of("reason", "startup"), "rag-rebuild-startup");
            }
        } catch (Exception failure) {
            // The console must still boot if the AI layer cannot initialise (e.g. minimal schema).
            org.slf4j.LoggerFactory.getLogger(AiPlatformBootstrap.class)
                    .warn("AI platform bootstrap skipped: {}", failure.getMessage());
        }
    }

    Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rag", rag.stats());
        out.put("queue", jobs.stats());
        out.put("prompts", prompts.list());
        out.put("evalSuite", Map.of("name", AiEvalService.DEFAULT_SUITE, "cases", eval.caseCount(AiEvalService.DEFAULT_SUITE)));
        out.put("evalRuns", eval.runs(5));
        return out;
    }
}
