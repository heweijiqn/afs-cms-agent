"""Summarise today's ERROR/WARN log signatures for the running service."""
from __future__ import annotations

import collections
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
files = [root / "log" / "app.err.log", root / "log" / "app.log", root / "log" / "app.warn.log"]
today = "2026-11-23"
err_sig = collections.Counter()
warn_sig = collections.Counter()
err_examples = {}
for path in files:
    if not path.exists():
        continue
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        m = re.match(r"\[(ERROR|WARN)\s+(\d{4}-\d{2}-\d{2})[^\]]*\]\[.*?\]\s*(.*)", line)
        if not m or m.group(2) != today:
            continue
        level, msg = m.group(1), m.group(3).strip()
        msg = re.sub(r"\b[0-9a-f]{8}-[0-9a-f-]{27,}\b", "<uuid>", msg)
        msg = re.sub(r"\b\d{6,}\b", "<num>", msg)
        msg = re.sub(r"run=[^\s]+", "run=<id>", msg)
        msg = msg[:160]
        target = err_sig if level == "ERROR" else warn_sig
        target[msg] += 1
        err_examples.setdefault(msg, line[:300])

print("== top ERROR signatures today")
for msg, count in err_sig.most_common(12):
    print(f"{count:5}  {msg}")
    print(f"       e.g. {err_examples[msg][:220]}")
print("\n== top WARN signatures today")
for msg, count in warn_sig.most_common(10):
    print(f"{count:5}  {msg}")
print(f"\nERROR total={sum(err_sig.values())} distinct={len(err_sig)}; WARN total={sum(warn_sig.values())} distinct={len(warn_sig)}")
