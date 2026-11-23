"""Stage 3b: re-verify chat message persistence with the correct SessionDetail shape."""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from agent_common import c, record, save  # noqa: E402

created: dict = {}
TT_TREADMILL = "1850127310500047514"


def messages_of(res) -> list:
    data = res.data or {}
    return data.get("messages") or []


sess = c.post("/cms/agent/chat/sessions",
              {"title": "QA-多轮记忆校验 · FoldableTreadmill", "campaignId": TT_TREADMILL})
sid = ((sess.data or {}).get("session") or {}).get("id")
created["chatSession"] = sid
record("create chat session for memory check", sess.status == 201 and sid,
       "POST /cms/agent/chat/sessions", "201 + id", f"status={sess.status} id={sid}", "high")
empty = c.get(f"/cms/agent/chat/sessions/{sid}")
record("new session starts with 0 messages", messages_of(empty) == [],
       f"GET /cms/agent/chat/sessions/{sid}", "messages=[]", str(len(messages_of(empty))), "high")

m1 = c.post(f"/cms/agent/chat/sessions/{sid}/messages",
            {"content": "TT_US_FoldableTreadmill_Conv_006 近 14 天 ROAS 和利润是多少？只给结论，不要展开。"},
            timeout=180)
c1 = (m1.data or {}).get("content") or ""
record("chat turn 1 answered", m1.code == 0 and len(c1) > 20,
       "POST .../messages (ROAS question)", "200 + answer", f"len={len(c1)}", "high")
s1 = c.get(f"/cms/agent/chat/sessions/{sid}")
m1rows = messages_of(s1)
record("turn 1 persisted as user+assistant messages",
       [m.get("role") for m in m1rows] == ["user", "assistant"],
       f"GET /cms/agent/chat/sessions/{sid}", "2 messages",
       f"roles={[m.get('role') for m in m1rows]}", "high")
record("assistant message stores graph meta",
       bool((m1rows[-1].get("meta") or {}).get("model")) if m1rows else False,
       f"GET /cms/agent/chat/sessions/{sid}", "assistant meta.model present",
       json.dumps((m1rows[-1].get("meta") or {}) if m1rows else {}, ensure_ascii=False)[:200], "medium")
record("assistant answer states the real 14-day ROAS (1.68-1.79)",
       any(x in c1 for x in ("1.68", "1.675", "1.79", "1.786")),
       "POST .../messages answer text", "answer cites ~1.68-1.79 ROAS", c1[:200], "medium")

m2 = c.post(f"/cms/agent/chat/sessions/{sid}/messages",
            {"content": "基于你上面的结论，给出一个未来 7 天的预算调整建议，一句话即可。"}, timeout=180)
c2 = (m2.data or {}).get("content") or ""
record("multi-turn follow-up answered", m2.code == 0 and len(c2) > 10,
       "POST .../messages (follow-up referencing prior turn)", "200 + answer", f"len={len(c2)}", "high")
s2 = c.get(f"/cms/agent/chat/sessions/{sid}")
m2rows = messages_of(s2)
record("session persists 4 messages for 2 turns",
       [m.get("role") for m in m2rows] == ["user", "assistant", "user", "assistant"],
       f"GET /cms/agent/chat/sessions/{sid}", "4 messages",
       f"roles={[m.get('role') for m in m2rows]}", "high")
record("follow-up context preserved (history endpoint consistent)",
       len(m2rows) == 4 and m2rows[0].get("content", "").startswith("TT_US_FoldableTreadmill"),
       f"GET /cms/agent/chat/sessions/{sid}", "first user message retained",
       (m2rows[0].get("content") or "")[:80], "medium")
records = [{"role": m.get("role"), "chars": len(m.get("content") or ""),
            "metaModel": (m.get("meta") or {}).get("model")} for m in m2rows]
(Path(__file__).resolve().parent / "out" / "agent-core-chat.json").write_text(
    json.dumps({"sessionId": sid, "records": records, "answer1": c1[:600], "answer2": c2[:400]},
               ensure_ascii=False, indent=2), encoding="utf-8")

deleted = c.delete(f"/cms/agent/chat/sessions/{sid}")
record("DELETE chat session after memory check", deleted.code == 0 and (deleted.data or {}).get("deleted") is True,
       f"DELETE /cms/agent/chat/sessions/{sid}", "deleted=true", f"status={deleted.status}", "high")
gone = c.get(f"/cms/agent/chat/sessions/{sid}")
record("deleted session 404", gone.status == 404, f"GET /cms/agent/chat/sessions/{sid}", "404",
       f"status={gone.status}", "high")

save("stage3b")
print("stage3b done", json.dumps(created, ensure_ascii=False))
