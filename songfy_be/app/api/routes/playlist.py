from fastapi import APIRouter, Depends, HTTPException
from fastapi.concurrency import run_in_threadpool
from app.models.schemas import PlaylistCreateRequest, PlaylistSummary, PlaylistDetailResponse
from app.api.deps import get_current_user_id
from app.services.ytmusic_service import YTMusicService, get_ytmusic_service

router = APIRouter(prefix="/playlist", tags=["playlist"])


@router.get("/top", response_model=list[PlaylistSummary])
async def get_top_playlists(
    service: YTMusicService = Depends(get_ytmusic_service),
) -> list[PlaylistSummary]:
    return await run_in_threadpool(service.get_top_playlists)


@router.get("/{playlist_id}", response_model=PlaylistDetailResponse)
async def get_playlist(
    playlist_id: str,
    service: YTMusicService = Depends(get_ytmusic_service),
) -> PlaylistDetailResponse:
    try:
        return await run_in_threadpool(service.get_playlist_details, playlist_id)
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"Playlist not found: {e}")


@router.post("")
def create_playlist(
    payload: PlaylistCreateRequest,
    user_id: str = Depends(get_current_user_id),
):
    return {"message": "Handled via Firestore directly from client", "user_id": user_id}
