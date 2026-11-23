"""Read-only concurrency / latency probe for hot console endpoints."""
from __future__ import annotations

import statistics
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient

c = CmsClient()
paths = [
    "/cms/agent/campaigns?limit=100",
    "/cms/agent/runs",
    "/cms/agent/telemetry",
    "/cms/agent/ops/funnel",
    "/cms/agent/ops/pacing",
    "/cms/ad/resource/list?pageIndex=1&pageSize=50",
]


def call(path):
    started = time.perf_counter()
    r = c.get(path) if "ad/resource/list" not in path else c.post(path, {})
    return path, r.status, r.code, (time.perf_counter() - started) * 1000


print("warmup")
for p in paths:
    call(p)

results = []
with ThreadPoolExecutor(max_workers=16) as pool:
    futures = [pool.submit(call, paths[i % len(paths)]) for i in range(240)]
    for f in futures:
        results.append(f.result())

by_path: dict[str, list] = {}
for path, status, code, ms in results:
    by_path.setdefault(path, []).append((status, code, ms))

all_ms = [ms for _, _, _, ms in results]
print(f"requests={len(results)} wall={sum(all_ms)/16/1000:.1f}s")
print(f"latency p50={statistics.median(all_ms):.0f}ms p95={sorted(all_ms)[int(len(all_ms)*.95)]:.0f}ms max={max(all_ms):.0f}ms")
for path, rows in by_path.items():
    ok = sum(1 for s, code, _ in rows if s == 200 and code == 0)
    times = sorted(ms for _, _, ms in rows)
    print(f"{path:42} ok={ok}/{len(rows)} p50={statistics.median(times):6.0f}ms p95={times[int(len(times)*.95)]:7.0f}ms max={times[-1]:7.0f}ms")
