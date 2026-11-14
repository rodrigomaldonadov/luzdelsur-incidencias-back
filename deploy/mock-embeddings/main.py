#!/usr/bin/env python3
"""
Mock temporal del microservicio de embeddings.

IMPORTANTE: este servicio existe solo para que Persona 1 pueda integrar y
probar la API sin esperar al modelo real. Persona 5 lo reemplaza con FastAPI +
sentence-transformers. Calcula una similitud lexica (Jaccard de tokens), no
semantica, y cumple el mismo contrato HTTP:

  POST /api/v1/similarity        {"text_a": "...", "text_b": "..."} -> {"similarity": 0-1}
  POST /api/v1/similarity/batch  {"text": "...", "candidates": ["..."]} -> {"similarities": [...]}
  GET  /health
"""
import json
import re
import unicodedata
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def normalize(text: str) -> list[str]:
    decomposed = unicodedata.normalize("NFD", text or "")
    without_accents = "".join(c for c in decomposed if unicodedata.category(c) != "Mn")
    return re.findall(r"[a-z0-9]+", without_accents.lower())


def similarity(text_a: str, text_b: str) -> float:
    tokens_a = set(normalize(text_a))
    tokens_b = set(normalize(text_b))
    if not tokens_a or not tokens_b:
        return 0.0
    return round(len(tokens_a & tokens_b) / len(tokens_a | tokens_b), 4)


class Handler(BaseHTTPRequestHandler):
    server_version = "mock-embeddings/1.0"

    def _send(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self) -> dict | None:
        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length) if length else b"{}"
            return json.loads(raw or b"{}")
        except (ValueError, json.JSONDecodeError):
            return None

    def do_GET(self) -> None:
        if self.path in ("/health", "/healthz"):
            self._send(200, {"status": "ok", "model": "mock-jaccard"})
        else:
            self._send(404, {"detail": "not found"})

    def do_POST(self) -> None:
        payload = self._read_json()
        if payload is None:
            self._send(400, {"detail": "JSON invalido"})
            return

        if self.path == "/api/v1/similarity":
            self._send(200, {
                "similarity": similarity(str(payload.get("text_a", "")), str(payload.get("text_b", "")))
            })
            return

        if self.path == "/api/v1/similarity/batch":
            candidates = payload.get("candidates")
            if not isinstance(candidates, list):
                self._send(422, {"detail": "candidates debe ser una lista"})
                return
            text = str(payload.get("text", ""))
            self._send(200, {"similarities": [similarity(text, str(c)) for c in candidates]})
            return

        self._send(404, {"detail": "not found"})

    def log_message(self, fmt: str, *args) -> None:
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)


if __name__ == "__main__":
    print("Mock embeddings escuchando en 0.0.0.0:8000", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
