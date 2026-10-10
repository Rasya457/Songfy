import asyncio
import time
from collections import OrderedDict
from fastapi import APIRouter, Depends, HTTPException
from fastapi.concurrency import run_in_threadpool
from app.services.ytmusic_service import YTMusicService, get_ytmusic_service
from app.models.schemas import ArtistDetailResponse

router = APIRouter(prefix="/artist", tags=["artist"])

# Cache artist pages for 1 hour (data rarely changes). Cap at 200 entries.
_ARTIST_CACHE_TTL = 3600
_ARTIST_CACHE_MAX = 200
_artist_cache: OrderedDict = OrderedDict()
_artist_lock = asyncio.Lock()


@router.get("/{artist_id}", response_model=ArtistDetailResponse)
async def get_artist(
    artist_id: str,
    service: YTMusicService = Depends(get_ytmusic_service),
):
    """
    Fetch artist detail. Results are cached 1 hour to avoid 2-4s latency
    on repeated loads of the same artist page.
    """
    async with _artist_lock:
        entry = _artist_cache.get(artist_id)
        if entry and (time.monotonic() - entry[0]) < _ARTIST_CACHE_TTL:
            _artist_cache.move_to_end(artist_id)
            return entry[1]

    try:
        result = await run_in_threadpool(service.get_artist, artist_id)
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"Artist not found: {str(e)}")

    async with _artist_lock:
        _artist_cache[artist_id] = (time.monotonic(), result)
        _artist_cache.move_to_end(artist_id)
        while len(_artist_cache) > _ARTIST_CACHE_MAX:
            _artist_cache.popitem(last=False)

    return result
