import asyncio
import re
import time
from collections import OrderedDict
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from app.api.deps import limiter
from app.services.stream_resolver import StreamResolver, get_stream_resolver
from app.models.schemas import StreamResponse

router = APIRouter(prefix="/stream", tags=["stream"])

# YouTube video IDs: 11 chars, base64url alphabet [A-Za-z0-9_-]
_VIDEO_ID_RE = re.compile(r'^[A-Za-z0-9_-]{6,20}$')

# LRU-bounded stream cache: video_id -> (timestamp, StreamResponse)
# yt-dlp URLs valid ~6h; cache 5h. Cap 500 to prevent memory growth.
_STREAM_CACHE_TTL = 5 * 3600
_STREAM_CACHE_MAX = 500
_stream_cache: OrderedDict = OrderedDict()
_stream_lock = asyncio.Lock()


def _get_cached(video_id: str):
    entry = _stream_cache.get(video_id)
    if entry and (time.monotonic() - entry[0]) < _STREAM_CACHE_TTL:
        _stream_cache.move_to_end(video_id)
        return entry[1]
    return None


def _set_cached(video_id: str, result) -> None:
    _stream_cache[video_id] = (time.monotonic(), result)
    _stream_cache.move_to_end(video_id)
    while len(_stream_cache) > _STREAM_CACHE_MAX:
        _stream_cache.popitem(last=False)


@router.get("/{video_id}", response_model=StreamResponse)
@limiter.limit("60/minute")
async def get_stream_url(
    request: Request,
    video_id: str,
    resolver: StreamResolver = Depends(get_stream_resolver),
):
    """
    Resolve a YouTube Music stream URL.
    yt-dlp is CPU-heavy (2-5s); run it in threadpool so event loop
    stays responsive for all other concurrent requests.
    """
    # Validate video_id format to prevent injection into yt-dlp
    if not _VIDEO_ID_RE.match(video_id):
        raise HTTPException(
            status_code=400,
            detail="Invalid video ID format. Expected YouTube video ID (6-20 alphanumeric/underscore/dash chars)."
        )

    async with _stream_lock:
        cached = _get_cached(video_id)
        if cached:
            return cached

    # Run blocking yt-dlp outside event loop with a hard 15s total timeout.
    # socket_timeout=10 in yt-dlp options covers individual socket ops, but yt-dlp
    # can still hang longer during YouTube throttling / DASH manifest parsing.
    # asyncio.wait_for ensures the event loop stays responsive for all other requests.
    try:
        result = await asyncio.wait_for(
            run_in_threadpool(resolver.resolve, video_id),
            timeout=15.0,
        )
    except asyncio.TimeoutError:
        raise HTTPException(
            status_code=504,
            detail=f"Stream resolution timed out for {video_id}. YouTube may be throttling requests."
        )

    async with _stream_lock:
        _set_cached(video_id, result)

    return result

