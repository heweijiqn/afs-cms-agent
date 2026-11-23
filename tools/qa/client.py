"""Signed HTTP client for the local AFS CMS test harness.

Signature contract (see AuthInterceptor):
    md5( requestURI + "&" + METHOD + "&"
         + join(sorted("admin-*" headers except admin-signature) as "k=v&")
         + secret + "&" )
"""
from __future__ import annotations

import hashlib
import json
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any
from urllib import error, parse, request

ROOT = Path(__file__).resolve().parents[2]


def read_local_key() -> str:
    return (ROOT / "data" / "local-admin.key").read_text(encoding="utf-8").strip()


@dataclass
class ApiResult:
    status: int
    body: Any
    text: str
    elapsed_ms: float
    headers: dict[str, str] = field(default_factory=dict)

    @property
    def ok(self) -> bool:
        return 200 <= self.status < 300

    @property
    def code(self) -> int | None:
        if isinstance(self.body, dict) and isinstance(self.body.get("code"), int):
            return self.body["code"]
        return None

    @property
    def data(self) -> Any:
        if isinstance(self.body, dict) and "data" in self.body:
            return self.body["data"]
        return self.body

    def __repr__(self) -> str:
        return f"<ApiResult {self.status} code={self.code} {self.elapsed_ms:.0f}ms>"


class CmsClient:
    def __init__(self, base: str = "http://127.0.0.1:9901",
                 name: str = "local-admin", secret: str | None = None,
                 console: bool = True):
        self.base = base.rstrip("/")
        self.name = name
        self.secret = secret if secret is not None else read_local_key()
        self.console = console
        self.last_headers: dict[str, str] = {}

    # ------------------------------------------------------------------ auth
    def auth_headers(self, method: str, path: str, body_text: str | None = None) -> dict[str, str]:
        headers = {
            "admin-name": self.name,
            "admin-nonce": str(uuid.uuid4()),
            "admin-timestamp": str(int(time.time() * 1000)),
        }
        if self.console:
            headers["admin-console"] = "1"
        if body_text is not None:
            headers["admin-body-md5"] = hashlib.md5(body_text.encode("utf-8")).hexdigest()
        ordered = sorted(
            (k, v) for k, v in headers.items()
            if k.startswith("admin-") and k != "admin-signature"
        )
        parameter_text = "".join(f"{k}={v}&" for k, v in ordered)
        endpoint = path.split("#")[0]
        plain = f"{endpoint}&{method.upper()}&{parameter_text}{self.secret}&"
        headers["admin-signature"] = hashlib.md5(plain.encode("utf-8")).hexdigest()
        return headers

    # --------------------------------------------------------------- request
    def call(self, method: str, path: str, body: Any = None,
             headers: dict[str, str] | None = None,
             timeout: float = 120.0) -> ApiResult:
        body_text = None
        payload = None
        if body is not None:
            body_text = json.dumps(body, ensure_ascii=False)
            payload = body_text.encode("utf-8")
        request_headers = {
            "Accept": "application/json",
            **self.auth_headers(method, path, body_text),
        }
        if payload is not None:
            request_headers["Content-Type"] = "application/json"
        if headers:
            request_headers.update(headers)
        self.last_headers = request_headers
        req = request.Request(self.base + path, data=payload, method=method.upper(),
                              headers=request_headers)
        started = time.perf_counter()
        try:
            with request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read()
                status = resp.status
                resp_headers = dict(resp.headers)
        except error.HTTPError as exc:
            raw = exc.read()
            status = exc.code
            resp_headers = dict(exc.headers)
        except Exception as exc:  # network-level
            return ApiResult(-1, None, repr(exc), (time.perf_counter() - started) * 1000)
        elapsed = (time.perf_counter() - started) * 1000
        text = raw.decode("utf-8", "replace")
        try:
            parsed = json.loads(text) if text else None
        except json.JSONDecodeError:
            parsed = None
        return ApiResult(status, parsed, text, elapsed, resp_headers)

    def get(self, path: str, **kw) -> ApiResult:
        return self.call("GET", path, **kw)

    def post(self, path: str, body: Any = None, **kw) -> ApiResult:
        return self.call("POST", path, body if body is not None else {}, **kw)

    def delete(self, path: str, **kw) -> ApiResult:
        return self.call("DELETE", path, **kw)

    # ---------------------------------------------------------------- helper
    def login(self) -> ApiResult:
        return self.get("/cms/user/login")

    def paged(self, path: str, body: dict | None = None) -> ApiResult:
        payload = {"page": 1, "size": 50}
        if body:
            payload.update(body)
        return self.post(path, payload)


def qs(path: str, **params) -> str:
    clean = {k: v for k, v in params.items() if v is not None}
    return path + ("?" + parse.urlencode(clean) if clean else "")
