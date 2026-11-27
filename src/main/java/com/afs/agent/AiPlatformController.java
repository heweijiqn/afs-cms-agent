package com.afs.agent;

import com.afs.controller.filter.RootAuth;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console/API surface for the AI platform layer: retrieval, prompt versions, eval runs,
 * semantic cache and the async queue.
 */
@RestController
@RequestMapping("/cms/ai")
@Profile("local-cms")
class AiPlatformController {
    private final AiPlatformBootstrap bootstrap;
    private final RagService rag;
    private final SemanticCache cache;
    private final AiJobQueue jobs;
    private final AiEvalService eval;
    private final PromptRegistry prompts;

    AiPlatformController(AiPlatformBootstrap bootstrap, RagService rag, SemanticCache cache,
                         AiJobQueue jobs, AiEvalService eval, PromptRegistry prompts) {
        this.bootstrap = bootstrap;
        this.rag = rag;
        this.cache = cache;
        this.jobs = jobs;
        this.eval = eval;
        this.prompts = prompts;
    }

    @GetMapping("/platform/overview")
    Map<String, Object> overview(@RequestParam(defaultValue = "5") int runs) {
        Map<String, Object> out = bootstrap.overview();
        out.put("cache", cache.stats());
        out.put("evalRuns", eval.runs(runs));
        return out;
    }

    @RootAuth
    @PostMapping("/rag/rebuild")
    Map<String, Object> rebuild(@RequestBody(required = false) Map<String, Object> body) {
        boolean inline = body != null && Boolean.TRUE.equals(body.get("inline"));
        if (inline) {
            return Map.of("mode", "inline", "stats", rag.rebuildIndex());
        }
        return Map.of("mode", "queued", "job", jobs.enqueue("rag.rebuild", Map.of("reason", "api"), null));
    }

    @PostMapping("/rag/search")
    Map<String, Object> search(@RequestBody RetrievalQuery query) {
        int topK = query.topK() == null ? 5 : Math.max(1, Math.min(20, query.topK()));
        var hits = rag.search(query.query(), query.site(), topK);
        return Map.of("query", query.query(), "site", query.site() == null ? "" : query.site(),
                "topK", topK, "hits", hits);
    }

    @PostMapping("/rag/answer")
    Map<String, Object> answer(@RequestBody RetrievalQuery query) {
        int topK = query.topK() == null ? 5 : Math.max(1, Math.min(12, query.topK()));
        var response = rag.answer(query.query(), query.site(), topK);
        return Map.of("question", query.query(), "answer", response.answer(),
                "citations", response.citations(), "latencyMs", response.latencyMs(),
                "cacheHit", response.cacheHit(), "model", response.model(),
                "promptTokens", response.promptTokens(), "completionTokens", response.completionTokens(),
                "promptVersion", response.promptVersion());
    }

    @RootAuth
    @PostMapping("/cache/purge")
    Map<String, Object> purgeCache() {
        cache.clear();
        return cache.stats();
    }

    @RootAuth
    @PostMapping("/jobs/run")
    Map<String, Object> runJobs(@RequestBody(required = false) Map<String, Object> body) {
        int times = body == null || body.get("max") == null ? 1
                : Math.max(1, Math.min(5, ((Number) body.get("max")).intValue()));
        List<String> executed = new java.util.ArrayList<>();
        for (int i = 0; i < times; i++) {
            String type = jobs.runOne();
            if (type == null) break;
            executed.add(type);
        }
        return Map.of("executed", executed, "stats", jobs.stats());
    }

    @RootAuth
    @PostMapping("/eval/run")
    Map<String, Object> runEval(@RequestBody(required = false) Map<String, Object> body) {
        String suite = body == null || body.get("suite") == null
                ? AiEvalService.DEFAULT_SUITE : String.valueOf(body.get("suite"));
        return eval.run(suite);
    }

    @GetMapping("/eval/runs")
    List<Map<String, Object>> evalRuns(@RequestParam(defaultValue = "10") int limit) {
        return eval.runs(limit);
    }

    @GetMapping("/prompts")
    List<Map<String, Object>> promptList() {
        return prompts.list();
    }

    @RootAuth
    @PostMapping("/prompts/activate")
    Map<String, Object> activatePrompt(@RequestBody Map<String, Object> body) {
        String key = String.valueOf(body.get("key"));
        int version = ((Number) body.get("version")).intValue();
        return prompts.activate(key, version);
    }

    record RetrievalQuery(String query, String site, Integer topK) {}
}
