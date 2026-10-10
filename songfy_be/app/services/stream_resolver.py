import time
import threading
from collections import OrderedDict
import yt_dlp
from functools import lru_cache
from fastapi import HTTPException
from app.core.config import get_settings


class StreamResolver:
    def __init__(self):
        settings = get_settings()
        self._ydl_opts = {
            "format": "141/251/bestaudio[acodec=opus]/bestaudio[ext=webm]/bestaudio[ext=m4a]/bestaudio/best",
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "extract_flat": False,
            "skip_download": True,
            "youtube_include_dash_manifest": False,
            "youtube_include_hls_manifest": False,
            "extractor_retries": 1,
            "socket_timeout": 10,
        }
        if settings.YTDLP_COOKIES_FILE:
            self._ydl_opts["cookiefile"] = settings.YTDLP_COOKIES_FILE

        self._cache = OrderedDict()
        self._lock = threading.Lock()
        self._cache_ttl = 5 * 3600  # 5 hours (YouTube stream URLs expire after ~6h)
        self._cache_max = 500

    def resolve(self, video_id: str) -> dict:
        # Check in-memory cache first for instant sub-millisecond response
        with self._lock:
            entry = self._cache.get(video_id)
            if entry and (time.monotonic() - entry[0]) < self._cache_ttl:
                self._cache.move_to_end(video_id)
                return entry[1]

        url = f"https://music.youtube.com/watch?v={video_id}"

        try:
            with yt_dlp.YoutubeDL(self._ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
        except yt_dlp.utils.DownloadError as e:
            raise HTTPException(status_code=404, detail=f"Could not resolve stream: {str(e)}")

        audio_url = info.get("url")
        if not audio_url:
            # fallback: cari format audio-only terbaik dari list formats
            formats = info.get("formats", [])
            audio_formats = [f for f in formats if f.get("acodec") != "none" and f.get("vcodec") == "none"]
            if not audio_formats:
                raise HTTPException(status_code=404, detail="No audio stream found")
            best = max(audio_formats, key=lambda f: f.get("abr") or 0)
            audio_url = best["url"]
            bitrate = best.get("abr")
        else:
            bitrate = info.get("abr")

        result = {
            "video_id": video_id,
            "audio_url": audio_url,
            "expires_in": 21600,  # link YouTube biasanya valid ~6 jam
            "bitrate": bitrate,
        }

        with self._lock:
            self._cache[video_id] = (time.monotonic(), result)
            self._cache.move_to_end(video_id)
            while len(self._cache) > self._cache_max:
                self._cache.popitem(last=False)

        return result


@lru_cache
def get_stream_resolver() -> StreamResolver:
    return StreamResolver()
