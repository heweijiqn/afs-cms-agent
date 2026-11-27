"""End-to-end check for the AI platform layer (RAG / prompt registry / cache / queue / eval).

Runs against a live local CMS on 127.0.0.1:9901 and prints a compact report. Every step is a
real HTTP call, so the script doubles as the demo and as the regression gate for prompt or
index changes.
"""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient

OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)

c = CmsClient()
report: dict[str, object] = {}
failures: list[str] = []


def check(name: str, ok: bool, detail: object = None) -> None:
    report[name] = {"ok": ok, "detail": detail}
    if not ok:
        failures.append(name)
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  {detail}" if detail is not None else ""))


def main() -> int:
    started = time.time()

    overview = c.get("/cms/ai/platform/overview")
    check("overview", overview.code == 0, overview.code)
    data = overview.data or {}
    prompts = data.get("prompts") or []
    check("prompt registry", len(prompts) >= 5, f"{len(prompts)} prompts")
    check("async queue reachable", "queue" in data, (data.get("queue") or {}).get("total"))

    # rebuild the index synchronously so the check is deterministic
    rebuild = c.post("/cms/ai/rag/rebuild", {"inline": True})
    stats = (rebuild.data or {}).get("stats") or {}
    check("rag index", (stats.get("chunks") or 0) > 100, f"{stats.get('chunks')} chunks / {stats.get('rebuildMs')} ms")

    # retrieval quality on a known topic
    search = c.post("/cms/ai/rag/search", {"query": "foldable treadmill apartment noise", "site": "cccooperate", "topK": 5})
    hits = (search.data or {}).get("hits") or []
    top_score = hits[0]["score"] if hits else 0
    check("hybrid retrieval", bool(hits) and top_score > 0.2, f"top score {top_score}")

    # clear the semantic cache so the first answer is a real model call
    purge = c.post("/cms/ai/cache/purge", {})
    check("cache cleared", (purge.data or {}).get("entries") == 0, purge.data)

    # cited answer (real model call, cache bypassed on the first hit)
    first = c.post("/cms/ai/rag/answer", {"query": "种植牙的费用包含哪些项目", "site": "", "topK": 5})
    first_data = first.data or {}
    check("answer with citations", bool(first_data.get("citations")),
          f"{len(first_data.get('citations') or [])} citations, {first_data.get('latencyMs')} ms, model={first_data.get('model')}")

    # same question again: semantic cache should answer orders of magnitude faster
    time.sleep(0.3)
    second = c.post("/cms/ai/rag/answer", {"query": "种植牙的费用包含哪些项目", "site": "", "topK": 5})
    second_data = second.data or {}
    check("semantic cache hit", bool(second_data.get("cacheHit")),
          f"{second_data.get('latencyMs')} ms vs {first_data.get('latencyMs')} ms")
    check("cache avoids model call", second_data.get("model") != first_data.get("model")
          or second_data.get("completionTokens") == 0)

    # refusal behaviour: unanswerable question must say so instead of inventing
    refusal = c.post("/cms/ai/rag/answer", {"query": "给我一份 2026 年第三季度员工薪资明细", "site": "", "topK": 3})
    refusal_text = (refusal.data or {}).get("answer") or ""
    lowered = refusal_text.lower()
    check("refuses without evidence",
          any(token in lowered for token in ("缺少", "无法", "没有", "不足", "insufficient")),
          refusal_text[:80].replace("\n", " "))

    # async queue round-trip through the real worker path
    enqueued = c.post("/cms/ai/rag/rebuild", {})
    job = (enqueued.data or {}).get("job") or {}
    check("job enqueued", job.get("id") is not None, job)
    executed = c.post("/cms/ai/jobs/run", {"max": 1})
    ran = (executed.data or {}).get("executed") or []
    check("worker executed job", "rag.rebuild" in ran, ran)

    # eval suite
    evaluation = c.post("/cms/ai/eval/run", {})
    metrics = evaluation.data or {}
    pass_rate = metrics.get("passRate")
    check("eval harness", (metrics.get("cases") or 0) >= 5, f"{metrics.get('passed')}/{metrics.get('cases')} passed")
    check("eval pass rate", (pass_rate or 0) >= 0.6, pass_rate)
    check("eval recall", (metrics.get("avgKeywordRecall") or 0) >= 0.6, metrics.get("avgKeywordRecall"))

    runs = c.get("/cms/ai/eval/runs?limit=5").data or []
    check("eval history stored", len(runs) > 0, f"{len(runs)} runs")

    elapsed = round(time.time() - started, 1)
    report["elapsedSeconds"] = elapsed
    report["failures"] = failures
    (OUT / "ai-platform.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n{'ALL PASSED' if not failures else 'FAILURES: ' + ', '.join(failures)}  ({elapsed}s) -> {OUT / 'ai-platform.json'}")
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
