"""Shared helpers for the agent-core QA sweep."""
from __future__ import annotations

import json
import sys
import threading
import time
from pathlib import Path
from urllib import request as urlrequest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import CmsClient, qs  # noqa: E402

OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)
c = CmsClient()

CHECKS: list[dict] = []


def record(name: str, ok: bool, request: str, expected: str, actual: str,
           severity: str = "none") -> None:
    CHECKS.append({"name": name, "ok": bool(ok), "request": request,
                   "expected": expected, "actual": actual, "severity": severity})
    print(("PASS " if ok else "FAIL ") + name + " :: " + str(actual)[:240])


def save(stage: str) -> None:
    (OUT / f"agent-core-{stage}.json").write_text(
        json.dumps(CHECKS, ensure_ascii=False, indent=2), encoding="utf-8")


def abort(msg: str) -> None:
    print("ABORT:", msg)
    save("aborted")
    raise SystemExit(1)


def wait_ready(run_id: str, timeout: float = 190.0) -> dict:
    deadline = time.time() + timeout
    last: dict = {}
    while time.time() < deadline:
        r = c.get(f"/cms/agent/runs/{run_id}")
        if r.code == 0 and isinstance(r.data, dict):
            last = r.data
            source = (((last.get("plan") or {}).get("explanation") or {}).get("source") or "")
            if source and source != "PENDING":
                return last
        time.sleep(2)
    return last


def sse(run_id: str, collected: list, timeout: float = 150.0) -> None:
    path = f"/cms/agent/runs/{run_id}/stream"
    headers = {"Accept": "text/event-stream", **c.auth_headers("GET", path)}
    req = urlrequest.Request(c.base + path, headers=headers, method="GET")
    try:
        with urlrequest.urlopen(req, timeout=timeout) as resp:
            event = "message"
            data_lines: list[str] = []
            started = time.time()
            for raw in resp:
                if time.time() - started > timeout:
                    break
                line = raw.decode("utf-8", "replace").rstrip("\r\n")
                if line == "":
                    if data_lines:
                        payload = "\n".join(data_lines)
                        try:
                            parsed = json.loads(payload)
                        except Exception:
                            parsed = payload
                        collected.append((event, parsed, round(time.time() - started, 2)))
                    event = "message"
                    data_lines = []
                elif line.startswith("event:"):
                    event = line[6:].strip()
                elif line.startswith("data:"):
                    data_lines.append(line[5:].lstrip())
    except Exception as exc:  # stream closes at completion
        collected.append(("__closed__", repr(exc), 0))


def start_sse(run_id: str, timeout: float = 150.0) -> tuple[list, threading.Thread]:
    collected: list = []
    t = threading.Thread(target=sse, args=(run_id, collected, timeout), daemon=True)
    t.start()
    return collected, t


def rows(res) -> list:
    """The ResponseAdviser wraps list payloads as {code, data:{list:[...]}}."""
    data = res.data
    if isinstance(data, dict) and isinstance(data.get("list"), list):
        return data["list"]
    if isinstance(data, list):
        return data
    return []


def day_spend(day: dict) -> float:
    return float(day.get("ad_spend") or day.get("spend") or 0)


def day_revenue(day: dict) -> float:
    return float(day.get("gross_revenue") or day.get("revenue") or 0)


def brief(res, keys: str = "") -> str:
    body = res.body
    if isinstance(body, dict):
        picked = {k: body[k] for k in keys.split(",") if k in body} if keys else body
        return f"status={res.status} code={res.code} " + json.dumps(picked, ensure_ascii=False)[:400]
    return f"status={res.status} code={res.code} " + str(body)[:300]
