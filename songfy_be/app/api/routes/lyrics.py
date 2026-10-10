from fastapi import APIRouter, Query, Depends, Request
from app.api.deps import limiter
from app.services.lyrics_service import LyricsService, get_lyrics_service
from app.models.schemas import LyricsResponse

router = APIRouter(prefix="/lyrics", tags=["lyrics"])


@router.get("", response_model=LyricsResponse)
@limiter.limit("60/minute")
async def get_lyrics(
    request: Request,
    video_id: str = Query(...),
    track_name: str = Query(...),
    artist_name: str = Query(...),
    duration: int | None = Query(None),
    service: LyricsService = Depends(get_lyrics_service),
) -> LyricsResponse:
    """
    Fetch synced/plain lyrics for a track.
    Falls back through: LRCLIB → syncedlyrics (Musixmatch/NetEase/Deezer) → Genius.
    Results are cached in-memory for 24 h inside LyricsService.
    """
    return await service.get_lyrics(track_name, artist_name, video_id, duration)
