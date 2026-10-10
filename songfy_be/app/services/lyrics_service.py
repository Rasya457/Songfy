"""
lyrics_service.py
=================
Multi-source lyrics fetcher with smart fallback chain:

  1. LRCLIB /get  — exact match with duration (synced preferred)
  2. LRCLIB /search — fuzzy search, pick best duration match
  3. syncedlyrics — tries Musixmatch, NetEase, Deezer, Lrclib, Megalobiz
     (in order, stops at first synced result)
  4. syncedlyrics plain — falls back to plain text from Genius if synced not found

Duration tolerance: ±15 seconds. Without a close duration match, LRCLIB
often returns a radio edit / live version with completely wrong timing.
"""

import re
import time
import asyncio
import logging
from collections import OrderedDict
from functools import lru_cache
from typing import Optional

import httpx
from app.core.config import get_settings
from app.models.schemas import LyricsResponse

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Title cleaning helpers
# ---------------------------------------------------------------------------
_NOISE_PARENS = re.compile(
    r"""[\(\[]  # open bracket
        (?:official|music|video|audio|lyric|lyrics|visualizer|live|
           remastered|hd|4k|feat\.?|ft\.?|prod\.?|extended|version|
           remix|cover|acoustic|unplugged|vevo|topic)
        [^\)\]]*   # rest of parenthetical
    [\)\]]""",
    re.IGNORECASE | re.VERBOSE,
)
_NOISE_SUFFIX = re.compile(
    r"""\s*[-–—|]\s*
        (?:official|music|video|audio|lyric|lyrics|visualizer|
           remastered|hd|4k|vevo|topic)
        .*$""",
    re.IGNORECASE | re.VERBOSE,
)
_MULTI_SPACE = re.compile(r"[\s\-_]+")


def clean_title(title: str) -> str:
    """Remove YouTube noise from track title to improve lyrics lookup accuracy."""
    cleaned = _NOISE_PARENS.sub("", title)
    cleaned = _NOISE_SUFFIX.sub("", cleaned)
    cleaned = _MULTI_SPACE.sub(" ", cleaned).strip()
    return cleaned if cleaned else title


def _first_artist(artist_name: str) -> str:
    """Take only the primary artist (before first comma / 'feat.' / '&')."""
    for sep in [",", " feat.", " ft.", " &", "/"]:
        if sep.lower() in artist_name.lower():
            artist_name = artist_name[: artist_name.lower().index(sep.lower())]
    return artist_name.strip()


# ---------------------------------------------------------------------------
# LRCLIB helpers
# ---------------------------------------------------------------------------
_DURATION_TOLERANCE_S = 3    # accept synced lyrics if |found_duration - query_duration| <= 3s
_DURATION_TOLERANCE_PLAIN_S = 15  # looser tolerance for plain lyrics (timing doesn't matter)


def _lrclib_best_item(items: list, wanted_duration_s: Optional[int]) -> Optional[dict]:
    """
    From a list of LRCLIB search results, pick the best match:
    - Prefer synced lyrics (syncedLyrics present)
    - If wanted_duration_s is provided:
        1. Prefer item within ±3 seconds tolerance
        2. If none within tolerance, pick item with closest duration in pool
    """
    if not items:
        return None

    synced = [i for i in items if i.get("syncedLyrics")]
    plain  = [i for i in items if not i.get("syncedLyrics") and i.get("plainLyrics")]

    for pool in (synced, plain):
        if not pool:
            continue
        if wanted_duration_s is not None:
            close = sorted(
                pool,
                key=lambda i: abs((i.get("duration") or 0) - wanted_duration_s),
            )
            tolerance = _DURATION_TOLERANCE_S if pool is synced else _DURATION_TOLERANCE_PLAIN_S
            best = close[0]
            # Match within tolerance (±3s for synced, ±15s for plain)
            if abs((best.get("duration") or 0) - wanted_duration_s) <= tolerance:
                return best
            # If none within tolerance, return the closest available in the pool
            return close[0]
        return pool[0]

    return None


# ---------------------------------------------------------------------------
# syncedlyrics async wrapper
# ---------------------------------------------------------------------------
async def _syncedlyrics_search(
    search_term: str,
    synced_only: bool = False,
) -> Optional[str]:
    """
    Run the blocking syncedlyrics.search() in a thread pool so it does not
    block the FastAPI event loop.  Returns LRC string or None.
    """
    try:
        import syncedlyrics  # lazy import — not needed at startup

        # Providers ordered by Indonesian coverage quality:
        # Musixmatch > NetEase (Chinese/Asian music) > Deezer > Lrclib > Megalobiz
        providers = ["Musixmatch", "NetEase", "Deezer", "Lrclib", "Megalobiz"]

        lrc = await asyncio.get_event_loop().run_in_executor(
            None,
            lambda: syncedlyrics.search(
                search_term,
                synced_only=synced_only,
                providers=providers,
            ),
        )
        return lrc
    except Exception as exc:
        log.debug("syncedlyrics search failed: %s", exc)
        return None


async def _syncedlyrics_plain(search_term: str) -> Optional[str]:
    """Try Genius (via syncedlyrics plain mode) for plain lyrics."""
    try:
        import syncedlyrics

        lrc = await asyncio.get_event_loop().run_in_executor(
            None,
            lambda: syncedlyrics.search(
                search_term,
                plain_only=True,
                providers=["Genius"],
            ),
        )
        return lrc
    except Exception as exc:
        log.debug("syncedlyrics plain search failed: %s", exc)
        return None


# ---------------------------------------------------------------------------
# LyricsService
# ---------------------------------------------------------------------------
class LyricsService:
    def __init__(self):
        self._base_url = get_settings().LRCLIB_BASE_URL
        self._client = httpx.AsyncClient(timeout=8.0)
        self._cache: OrderedDict = OrderedDict()
        self._cache_ttl = 86400   # 24 h — lyrics are static
        self._cache_max = 500

    def _put_cache(self, key, value: LyricsResponse):
        self._cache[key] = (time.monotonic(), value)
        self._cache.move_to_end(key)
        while len(self._cache) > self._cache_max:
            self._cache.popitem(last=False)

    async def get_lyrics(
        self,
        track_name: str,
        artist_name: str,
        video_id: str,
        duration: Optional[int] = None,
    ) -> LyricsResponse:
        cache_key = (video_id, track_name.strip().lower(), artist_name.strip().lower())
        entry = self._cache.get(cache_key)
        if entry and (time.monotonic() - entry[0]) < self._cache_ttl:
            self._cache.move_to_end(cache_key)
            return entry[1]

        cleaned_track  = clean_title(track_name)
        primary_artist = _first_artist(artist_name)
        search_term    = f"{cleaned_track} {primary_artist}"

        # ----------------------------------------------------------------
        # 1. LRCLIB /get — exact match (with duration when available)
        # ----------------------------------------------------------------
        result = await self._lrclib_get(
            cleaned_track, primary_artist, video_id, duration
        )
        if result and result.found:
            self._put_cache(cache_key, result)
            return result

        # ----------------------------------------------------------------
        # 2. LRCLIB /search — fuzzy, pick best duration match
        # ----------------------------------------------------------------
        result = await self._lrclib_search(cleaned_track, primary_artist, video_id, duration)
        if result and result.found:
            self._put_cache(cache_key, result)
            return result

        # If primary artist failed, try without artist (track-only LRCLIB search)
        if primary_artist:
            result = await self._lrclib_search(cleaned_track, "", video_id, duration)
            if result and result.found:
                self._put_cache(cache_key, result)
                return result

        # ----------------------------------------------------------------
        # 3. syncedlyrics — Musixmatch / NetEase / Deezer / Lrclib / Megalobiz
        #    Try synced first; this is the main fallback for Indonesian songs.
        # ----------------------------------------------------------------
        lrc_str = await _syncedlyrics_search(search_term, synced_only=True)
        if lrc_str:
            result = LyricsResponse(
                video_id=video_id,
                synced_lyrics=lrc_str,
                plain_lyrics=None,
                found=True,
            )
            self._put_cache(cache_key, result)
            return result

        # ----------------------------------------------------------------
        # 4. syncedlyrics — allow plain text results from all providers
        # ----------------------------------------------------------------
        lrc_str = await _syncedlyrics_search(search_term, synced_only=False)
        if lrc_str:
            # Determine if the result is actually synced (LRC timestamps present)
            is_synced = bool(re.search(r"\[\d{1,2}:\d{2}", lrc_str))
            result = LyricsResponse(
                video_id=video_id,
                synced_lyrics=lrc_str if is_synced else None,
                plain_lyrics=lrc_str if not is_synced else None,
                found=True,
            )
            self._put_cache(cache_key, result)
            return result

        # ----------------------------------------------------------------
        # 5. Genius plain text via syncedlyrics (last resort)
        # ----------------------------------------------------------------
        plain = await _syncedlyrics_plain(search_term)
        if plain:
            result = LyricsResponse(
                video_id=video_id,
                synced_lyrics=None,
                plain_lyrics=plain,
                found=True,
            )
            self._put_cache(cache_key, result)
            return result

        # ----------------------------------------------------------------
        # Nothing found — cache empty to avoid hammering all sources again
        # ----------------------------------------------------------------
        empty = LyricsResponse(video_id=video_id, found=False)
        self._put_cache(cache_key, empty)
        return empty

    # ------------------------------------------------------------------
    # Private LRCLIB helpers
    # ------------------------------------------------------------------
    async def _lrclib_get(
        self,
        track: str,
        artist: str,
        video_id: str,
        duration: Optional[int],
    ) -> Optional[LyricsResponse]:
        params: dict = {"track_name": track, "artist_name": artist}
        if duration:
            params["duration"] = duration
        try:
            resp = await self._client.get(f"{self._base_url}/get", params=params)
            if resp.status_code == 200:
                data = resp.json()
                synced = data.get("syncedLyrics")
                plain  = data.get("plainLyrics")
                if synced or plain:
                    return LyricsResponse(
                        video_id=video_id,
                        plain_lyrics=plain,
                        synced_lyrics=synced,
                        found=True,
                        duration=data.get("duration"),
                    )
        except httpx.RequestError as exc:
            log.debug("LRCLIB /get error: %s", exc)
        return None

    async def _lrclib_search(
        self,
        track: str,
        artist: str,
        video_id: str,
        duration: Optional[int],
    ) -> Optional[LyricsResponse]:
        q = f"{track} {artist}".strip()
        if not q:
            return None
        try:
            resp = await self._client.get(f"{self._base_url}/search", params={"q": q})
            if resp.status_code == 200:
                items = resp.json()
                if isinstance(items, list) and items:
                    best = _lrclib_best_item(items, duration)
                    if best:
                        return LyricsResponse(
                            video_id=video_id,
                            plain_lyrics=best.get("plainLyrics"),
                            synced_lyrics=best.get("syncedLyrics"),
                            found=True,
                            duration=best.get("duration"),
                        )
        except httpx.RequestError as exc:
            log.debug("LRCLIB /search error: %s", exc)
        return None


@lru_cache
def get_lyrics_service() -> LyricsService:
    return LyricsService()
