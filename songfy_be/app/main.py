from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded

from app.core.config import get_settings
from app.core.security import initialize_firebase
from app.api.deps import limiter
from app.api.routes import search, artist, stream, lyrics, playlist, download, session

settings = get_settings()

# Firebase Admin SDK - initialize BEFORE any route that calls verify_id_token
initialize_firebase()

# Configure default limit on the shared limiter
limiter.default_limits = [f"{settings.RATE_LIMIT_PER_MINUTE}/minute"]

app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    description="Songfy Music Streaming API",
    docs_url="/docs" if settings.ENVIRONMENT == "development" else None,
    redoc_url=None,
)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def add_cache_control_headers(request: Request, call_next):
    response = await call_next(request)
    if request.method == "GET" and 200 <= response.status_code < 300:
        path = request.url.path
        if "/stream/" in path:
            response.headers["Cache-Control"] = "public, max-age=14400"
        elif "/lyrics" in path:
            response.headers["Cache-Control"] = "public, max-age=86400"
        elif "/artist/" in path:
            response.headers["Cache-Control"] = "public, max-age=3600"
        elif "/search" in path:
            response.headers["Cache-Control"] = "public, max-age=600"
        elif "/playlist" in path:
            response.headers["Cache-Control"] = "public, max-age=1800"
        elif "/download/" in path:
            response.headers["Cache-Control"] = "no-cache, no-store"
    return response


app.include_router(search.router, prefix=settings.API_V1_PREFIX)
app.include_router(artist.router, prefix=settings.API_V1_PREFIX)
app.include_router(stream.router, prefix=settings.API_V1_PREFIX)
app.include_router(lyrics.router, prefix=settings.API_V1_PREFIX)
app.include_router(playlist.router, prefix=settings.API_V1_PREFIX)
app.include_router(download.router, prefix=settings.API_V1_PREFIX)
app.include_router(session.router, prefix=settings.API_V1_PREFIX)


@app.get("/health", tags=["health"])
def health_check():
    return {"status": "ok", "version": "1.0.0"}
