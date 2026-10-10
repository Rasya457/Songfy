from pydantic import BaseModel
from typing import Optional


class ArtistShort(BaseModel):
    id: Optional[str] = None
    name: str


class SongResult(BaseModel):
    video_id: str
    title: str
    artists: list[ArtistShort]
    album: Optional[str] = None
    duration: Optional[str] = None
    thumbnail_url: Optional[str] = None
    play_count: Optional[str] = None


class PlaylistSummary(BaseModel):
    browse_id: str
    title: str
    description: Optional[str] = None
    author: Optional[str] = None
    thumbnail_url: Optional[str] = None
    item_count: Optional[str] = None


class SearchResponse(BaseModel):
    songs: list[SongResult] = []
    artists: list[dict] = []
    albums: list[dict] = []
    playlists: list[PlaylistSummary] = []


class PlaylistDetailResponse(BaseModel):
    playlist_id: str
    title: str
    description: Optional[str] = None
    author: Optional[str] = None
    thumbnail_url: Optional[str] = None
    track_count: Optional[int] = None
    tracks: list[SongResult] = []


class StreamResponse(BaseModel):
    video_id: str
    audio_url: str
    expires_in: int
    bitrate: Optional[float] = None


class ArtistDetailResponse(BaseModel):
    artist_id: str
    name: str
    thumbnail_url: Optional[str] = None
    banner_url: Optional[str] = None
    description: Optional[str] = None
    subscribers: Optional[str] = None
    monthly_listeners: Optional[str] = None
    verified: bool = True
    top_songs: list[SongResult] = []
    all_songs: list[SongResult] = []
    albums: list[dict] = []
    singles: list[dict] = []
    related_artists: list[dict] = []


class LyricsResponse(BaseModel):
    video_id: str
    plain_lyrics: Optional[str] = None
    synced_lyrics: Optional[str] = None
    found: bool = False
    duration: Optional[int] = None


class PlaylistCreateRequest(BaseModel):
    name: str
    description: Optional[str] = None
    is_public: bool = False


class ErrorResponse(BaseModel):
    detail: str


class SessionStartRequest(BaseModel):
    device_name: str


class SessionStartResponse(BaseModel):
    session_id: str
    device_name: str
    user_id: str
    message: str = "Session started successfully"
