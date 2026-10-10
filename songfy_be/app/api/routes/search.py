import asyncio
import hashlib
import time
from collections import OrderedDict
from fastapi import APIRouter, Query, Depends, Request
from fastapi.concurrency import run_in_threadpool
from app.api.deps import limiter
from app.services.ytmusic_service import YTMusicService, get_ytmusic_service
from app.models.schemas import SearchResponse

router = APIRouter(prefix="/search", tags=["search"])

# LRU-bounded search cache: {cache_key: (timestamp, SearchResponse)}
_search_cache: OrderedDict = OrderedDict()
_search_lock = asyncio.Lock()
_CACHE_TTL_SECONDS = 300  # 5 minutes
_CACHE_MAX_SIZE = 300


def _cache_key(q: str, filter_type: str | None) -> str:
    raw = f"{q.lower().strip()}|{filter_type or ''}"
    return hashlib.md5(raw.encode()).hexdigest()


@router.get("", response_model=SearchResponse)
@limiter.limit("60/minute")
async def search(
    request: Request,
    q: str = Query(..., min_length=1, max_length=200, description="Search query"),
    filter: str | None = Query(None, description="songs, artists, albums, videos, playlists"),
    service: YTMusicService = Depends(get_ytmusic_service),
) -> SearchResponse:
    key = _cache_key(q, filter)

    async with _search_lock:
        entry = _search_cache.get(key)
        if entry and (time.monotonic() - entry[0]) < _CACHE_TTL_SECONDS:
            _search_cache.move_to_end(key)
            return entry[1]

    # Non-blocking search in threadpool
    result = await run_in_threadpool(service.search, q, filter_type=filter)

    async with _search_lock:
        _search_cache[key] = (time.monotonic(), result)
        _search_cache.move_to_end(key)
        while len(_search_cache) > _CACHE_MAX_SIZE:
            _search_cache.popitem(last=False)

    return result
