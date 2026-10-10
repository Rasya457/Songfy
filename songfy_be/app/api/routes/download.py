import asyncio
import re
import httpx
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import StreamingResponse
from app.api.deps import limiter
from app.services.stream_resolver import StreamResolver, get_stream_resolver

router = APIRouter(prefix="/download", tags=["download"])

_VIDEO_ID_RE = re.compile(r'^[A-Za-z0-9_-]{6,20}$')

# Shared httpx async client for proxying — persistent connection pool,
# 10-minute timeout for large audio files on slow connections.
_http_client = httpx.AsyncClient(
    timeout=httpx.Timeout(connect=10.0, read=600.0, write=600.0, pool=10.0),
    follow_redirects=True,
    headers={
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/125.0.0.0 Safari/537.36"
        ),
        "Accept-Language": "en-US,en;q=0.9",
        "Accept": "*/*",
        "Referer": "https://music.youtube.com/",
        "Origin": "https://music.youtube.com",
    },
    http2=True,
)


@router.get("/{video_id}")
@limiter.limit("20/minute")
async def download_audio(
    request: Request,
    video_id: str,
    title: str = "audio",
    resolver: StreamResolver = Depends(get_stream_resolver),
):
    """
    Server-side proxy download endpoint.

    Why server-side proxy?
    ----------------------
    YouTube audio stream URLs contain 'sig', 'n', and 'ip' parameters that are
    bound to the requesting IP address. When an Android client tries to download
    directly from the CDN URL:
      1. The URL is resolved by the server (server's IP in the URL signature).
      2. Android then tries to download from that URL — different IP → 403 Forbidden.
    
    By proxying through the server, the same IP that resolved the URL also
    downloads it, so YouTube CDN accepts the request.

    The audio is streamed chunk-by-chunk to the client so memory usage stays flat
    regardless of file size.
    """
    if not _VIDEO_ID_RE.match(video_id):
        raise HTTPException(
            status_code=400,
            detail="Invalid video ID format.",
        )

    # Resolve stream URL (uses same cache as /stream endpoint)
    try:
        stream_result = await asyncio.wait_for(
            run_in_threadpool(resolver.resolve, video_id),
            timeout=15.0,
        )
    except asyncio.TimeoutError:
        raise HTTPException(
            status_code=504,
            detail=f"Stream resolution timed out for {video_id}.",
        )

    audio_url = stream_result.get("audio_url", "") if isinstance(stream_result, dict) else getattr(stream_result, "audio_url", "")
    if not audio_url:
        raise HTTPException(
            status_code=404,
            detail=f"No audio URL found for {video_id}.",
        )

    # Sanitize filename
    safe_title = re.sub(r'[\\/:*?"<>|]', '_', title)[:80] or video_id
    filename = f"{safe_title}.m4a"

    async def audio_stream():
        """Stream audio bytes from YouTube CDN to client."""
        async with _http_client.stream("GET", audio_url) as response:
            if response.status_code not in (200, 206):
                raise HTTPException(
                    status_code=502,
                    detail=f"YouTube CDN returned {response.status_code}.",
                )
            async for chunk in response.aiter_bytes(chunk_size=64 * 1024):  # 64KB chunks
                yield chunk

    return StreamingResponse(
        audio_stream(),
        media_type="audio/mp4",
        headers={
            "Content-Disposition": f'attachment; filename="{filename}"',
            "Cache-Control": "no-cache",
            "X-Video-Id": video_id,
        },
    )
