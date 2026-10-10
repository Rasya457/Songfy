---
title: Songfy BE
emoji: 🎵
colorFrom: gray
colorTo: black
sdk: docker
app_port: 7860
pinned: false
---

# Songfy BE

Backend FastAPI untuk aplikasi Songfy (search/artist/stream/lyrics via YouTube Music + yt-dlp).

## Cek hidup

- `GET /health` → `{"status": "ok"}`
- `GET /api/v1/search?q=ran&filter=songs`

Request pertama setelah idle bisa 30–60 detik (cold start free tier) — itu wajar.

## Env vars (isi di Space Settings → Secrets, JANGAN commit)

| Key | Contoh |
|---|---|
| `ENVIRONMENT` | `production` |
| `ALLOWED_ORIGINS` | `["*"]` |
| `FIREBASE_PROJECT_ID` | id project Firebase asli |
| `RATE_LIMIT_PER_MINUTE` | `60` |

Opsional (kosongkan dulu, isi hanya bila kena 429 YouTube): `YTDLP_COOKIES_FILE`,
`GOOGLE_APPLICATION_CREDENTIALS` (Secret File).

## Jangan upload ke Space

`.env`, `*serviceAccount*.json`, `cookies.txt`, folder `venv/`.
