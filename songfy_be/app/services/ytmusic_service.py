# pyrefly: ignore [missing-import]
import re
import time
import threading
from collections import OrderedDict
from ytmusicapi import YTMusic
from functools import lru_cache
from app.models.schemas import SongResult, ArtistShort, SearchResponse, ArtistDetailResponse, PlaylistSummary, PlaylistDetailResponse


class YTMusicService:
    def __init__(self):
        # Anonymous/unauthenticated mode - cukup buat search & metadata publik
        self._yt = YTMusic()
        self._search_cache = OrderedDict()
        self._artist_cache = OrderedDict()
        self._artist_id_map = OrderedDict()
        self._lock = threading.Lock()
        self._search_ttl = 600  # 10 minutes
        self._artist_ttl = 3600  # 1 hour
        self._cache_max = 300

    def search(self, query: str, filter_type: str | None = None) -> SearchResponse:
        clean_q = query.strip().lower()
        actual_filter = filter_type if filter_type in ("artists", "albums", "playlists") else "songs"
        cache_key = (clean_q, actual_filter)
        with self._lock:
            entry = self._search_cache.get(cache_key)
            if entry and (time.monotonic() - entry[0]) < self._search_ttl:
                self._search_cache.move_to_end(cache_key)
                return entry[1]

        try:
            results = self._yt.search(query, filter=actual_filter, limit=20)
        except Exception:
            try:
                results = self._yt.search(query, filter="songs", limit=20)
            except Exception:
                results = []

        songs, artists, albums, playlists = [], [], [], []
        seen_artists = set()

        for item in results:
            category = item.get("resultType")

            if category in ("song", "video", None) and (actual_filter == "songs" or category in ("song", "video")):
                item_artists = [
                    ArtistShort(id=a.get("id"), name=a.get("name", ""))
                    for a in item.get("artists", [])
                ]
                thumb = self._best_thumbnail(item.get("thumbnails"))
                songs.append(
                    SongResult(
                        video_id=item.get("videoId", ""),
                        title=item.get("title", ""),
                        artists=item_artists,
                        album=item.get("album", {}).get("name") if item.get("album") else None,
                        duration=item.get("duration"),
                        thumbnail_url=thumb,
                    )
                )
                for a in item_artists:
                    if a.name and a.name.lower() not in seen_artists:
                        seen_artists.add(a.name.lower())
                        artists.append(
                            {
                                "artist_id": a.id,
                                "name": a.name,
                                "thumbnail_url": thumb,
                            }
                        )
            elif category == "artist":
                artists.append(
                    {
                        "artist_id": item.get("browseId"),
                        "name": item.get("artist"),
                        "thumbnail_url": self._best_thumbnail(item.get("thumbnails")),
                    }
                )
            elif category == "album":
                albums.append(
                    {
                        "browse_id": item.get("browseId"),
                        "title": item.get("title"),
                        "artist": item.get("artists", [{}])[0].get("name") if item.get("artists") else None,
                        "thumbnail_url": self._best_thumbnail(item.get("thumbnails")),
                    }
                )
            elif category == "playlist":
                browse_id = item.get("browseId") or ""
                title = item.get("title") or "Playlist"
                author = item.get("author")
                description = item.get("description") or (f"By {author}" if author else None)
                thumb = self._best_thumbnail(item.get("thumbnails"))
                playlists.append(
                    PlaylistSummary(
                        browse_id=browse_id,
                        title=title,
                        description=description,
                        author=author,
                        thumbnail_url=thumb,
                        item_count=str(item.get("itemCount", "")) if item.get("itemCount") else None,
                    )
                )

        response = SearchResponse(songs=songs, artists=artists, albums=albums, playlists=playlists)

        with self._lock:
            self._search_cache[cache_key] = (time.monotonic(), response)
            self._search_cache.move_to_end(cache_key)
            while len(self._search_cache) > self._cache_max:
                self._search_cache.popitem(last=False)

        return response

    def get_artist(self, artist_id: str) -> ArtistDetailResponse:
        cache_key = artist_id.strip().lower()
        with self._lock:
            entry = self._artist_cache.get(cache_key)
            if entry and (time.monotonic() - entry[0]) < self._artist_ttl:
                self._artist_cache.move_to_end(cache_key)
                return entry[1]

        # Resolve artist channelId if name or browseId provided
        data = None
        target_id = artist_id

        if not (target_id.startswith("UC") or target_id.startswith("FE")):
            # Check cached mapped ID first
            with self._lock:
                mapped_id = self._artist_id_map.get(cache_key)
            if mapped_id:
                target_id = mapped_id
            else:
                try:
                    search_artists = self._yt.search(target_id, filter="artists", limit=1)
                    if search_artists and search_artists[0].get("browseId"):
                        target_id = search_artists[0]["browseId"]
                        with self._lock:
                            self._artist_id_map[cache_key] = target_id
                except Exception:
                    pass

        try:
            data = self._yt.get_artist(target_id)
        except Exception:
            # Fallback search if direct get_artist failed
            try:
                search_artists = self._yt.search(artist_id, filter="artists", limit=1)
                if search_artists and search_artists[0].get("browseId"):
                    target_id = search_artists[0]["browseId"]
                    data = self._yt.get_artist(target_id)
                    with self._lock:
                        self._artist_id_map[cache_key] = target_id
            except Exception as e:
                raise ValueError(f"Could not find artist {artist_id}: {e}")

        if not data:
            raise ValueError(f"Artist data not found for {artist_id}")

        artist_name = data.get("name", artist_id)
        views_str = data.get("views")
        subscribers_str = data.get("subscribers")

        # Extract numeric total views for play count and monthly listeners estimation
        total_views = 45000000
        if views_str:
            nums = re.findall(r'\d+', views_str.replace(',', '').replace('.', ''))
            if nums:
                total_views = int(nums[0])

        monthly_listeners = self._format_monthly_listeners(views_str, subscribers_str, total_views)

        # Extract thumbnails: wide banner and high-res square avatar
        thumbnails = data.get("thumbnails", [])
        banner_url = thumbnails[-1].get("url") if thumbnails else None

        avatar_url = None
        try:
            art_search = self._yt.search(artist_name, filter="artists", limit=1)
            if art_search and art_search[0].get("thumbnails"):
                raw_av = art_search[0]["thumbnails"][-1].get("url", "")
                if raw_av:
                    avatar_url = re.sub(r'=w\d+-h\d+', '=w500-h500', raw_av)
                    if '=w' not in avatar_url and '=s' not in avatar_url:
                        avatar_url += '=w500-h500-p-l90-rj'
        except Exception:
            pass

        if not avatar_url:
            avatar_url = thumbnails[0].get("url") if thumbnails else banner_url
        if not banner_url:
            banner_url = avatar_url

        # Top popular songs with formatted stream count
        top_songs: list[SongResult] = []
        raw_top_songs = data.get("songs", {}).get("results", [])
        for idx, s in enumerate(raw_top_songs[:10]):
            song_artists = [
                ArtistShort(id=a.get("id"), name=a.get("name", artist_name))
                for a in s.get("artists", [])
            ] if s.get("artists") else [ArtistShort(id=target_id, name=artist_name)]

            play_count = self._format_stream_count(s.get("views"), total_views, idx)
            top_songs.append(
                SongResult(
                    video_id=s.get("videoId", ""),
                    title=s.get("title", ""),
                    artists=song_artists,
                    album=s.get("album", {}).get("name") if isinstance(s.get("album"), dict) else None,
                    duration=s.get("duration"),
                    thumbnail_url=self._best_thumbnail(s.get("thumbnails")),
                    play_count=play_count,
                )
            )

        # Full songs from artist playlist for "See All"
        all_songs: list[SongResult] = []
        playlist_browse_id = data.get("songs", {}).get("browseId")
        if playlist_browse_id:
            try:
                playlist_data = self._yt.get_playlist(playlist_browse_id, limit=15)
                for idx, t in enumerate(playlist_data.get("tracks", [])):
                    track_artists = [
                        ArtistShort(id=a.get("id"), name=a.get("name", artist_name))
                        for a in t.get("artists", [])
                    ] if t.get("artists") else [ArtistShort(id=target_id, name=artist_name)]

                    play_count = self._format_stream_count(t.get("views"), total_views, idx)
                    all_songs.append(
                        SongResult(
                            video_id=t.get("videoId", ""),
                            title=t.get("title", ""),
                            artists=track_artists,
                            album=t.get("album", {}).get("name") if isinstance(t.get("album"), dict) else None,
                            duration=t.get("duration"),
                            thumbnail_url=self._best_thumbnail(t.get("thumbnails")),
                            play_count=play_count,
                        )
                    )
            except Exception:
                all_songs = top_songs
        else:
            all_songs = top_songs

        albums = data.get("albums", {}).get("results", []) if "albums" in data else []
        singles = data.get("singles", {}).get("results", []) if "singles" in data else []
        related = data.get("related", {}).get("results", []) if "related" in data else []

        result = ArtistDetailResponse(
            artist_id=target_id,
            name=artist_name,
            thumbnail_url=avatar_url,
            banner_url=banner_url,
            description=data.get("description"),
            subscribers=subscribers_str,
            monthly_listeners=monthly_listeners,
            verified=True,
            top_songs=top_songs,
            all_songs=all_songs,
            albums=albums,
            singles=singles,
            related_artists=related,
        )

        with self._lock:
            self._artist_cache[cache_key] = (time.monotonic(), result)
            self._artist_cache[target_id.strip().lower()] = (time.monotonic(), result)
            self._artist_cache.move_to_end(cache_key)
            while len(self._artist_cache) > self._cache_max:
                self._artist_cache.popitem(last=False)

        return result

    def get_top_playlists(self) -> list[PlaylistSummary]:
        cache_key = "top_playlists_hits"
        with self._lock:
            entry = self._search_cache.get(cache_key)
            if entry and (time.monotonic() - entry[0]) < 1800:
                self._search_cache.move_to_end(cache_key)
                return entry[1]

        playlists: list[PlaylistSummary] = []
        try:
            results = self._yt.search("Top Hits Indonesia 2026", filter="playlists", limit=15)
            if not results:
                results = self._yt.search("Top Hits", filter="playlists", limit=15)
            for item in results:
                browse_id = item.get("browseId") or ""
                title = item.get("title") or "Top Hits"
                author = item.get("author")
                description = item.get("description") or (f"By {author}" if author else "Top YouTube Music Hits")
                thumb = self._best_thumbnail(item.get("thumbnails"))
                playlists.append(
                    PlaylistSummary(
                        browse_id=browse_id,
                        title=title,
                        description=description,
                        author=author,
                        thumbnail_url=thumb,
                        item_count=str(item.get("itemCount", "")) if item.get("itemCount") else None,
                    )
                )
        except Exception as e:
            print(f"Error fetching top playlists: {e}")

        with self._lock:
            self._search_cache[cache_key] = (time.monotonic(), playlists)
            self._search_cache.move_to_end(cache_key)

        return playlists

    def get_playlist_details(self, playlist_id: str) -> PlaylistDetailResponse:
        cache_key = f"pl_{playlist_id.strip()}"
        with self._lock:
            entry = self._search_cache.get(cache_key)
            if entry and (time.monotonic() - entry[0]) < 1800:
                self._search_cache.move_to_end(cache_key)
                return entry[1]

        data = self._yt.get_playlist(playlist_id, limit=50)
        title = data.get("title") or "Playlist"
        description = data.get("description")
        author = data.get("author", {}).get("name") if isinstance(data.get("author"), dict) else data.get("author")
        thumb = self._best_thumbnail(data.get("thumbnails"))
        track_count = data.get("trackCount")

        tracks: list[SongResult] = []
        for t in data.get("tracks", []):
            vid = t.get("videoId")
            if not vid:
                continue
            track_artists = [
                ArtistShort(id=a.get("id"), name=a.get("name", "Unknown Artist"))
                for a in t.get("artists", [])
            ] if t.get("artists") else [ArtistShort(name="Unknown Artist")]

            tracks.append(
                SongResult(
                    video_id=vid,
                    title=t.get("title", "Unknown Title"),
                    artists=track_artists,
                    album=t.get("album", {}).get("name") if isinstance(t.get("album"), dict) else None,
                    duration=t.get("duration"),
                    thumbnail_url=self._best_thumbnail(t.get("thumbnails")) or f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg",
                )
            )

        resp = PlaylistDetailResponse(
            playlist_id=playlist_id,
            title=title,
            description=description,
            author=author,
            thumbnail_url=thumb,
            track_count=track_count,
            tracks=tracks,
        )

        with self._lock:
            self._search_cache[cache_key] = (time.monotonic(), resp)
            self._search_cache.move_to_end(cache_key)

        return resp

    def get_song_metadata(self, video_id: str) -> dict:
        return self._yt.get_song(video_id)

    @staticmethod
    def _best_thumbnail(thumbnails: list[dict] | None) -> str | None:
        if not thumbnails:
            return None
        raw_url = thumbnails[-1].get("url")
        if not raw_url:
            return None
        # Upgrade Google User Content / YTMusic CDN to full HD 1200x1200
        if "googleusercontent.com" in raw_url or "ggpht.com" in raw_url:
            return re.sub(r"=w\d+-h\d+.*|=s\d+.*", "=w1200-h1200-l90-rj", raw_url)
        # For YouTube video thumbnails: use sddefault (640x480) as safe guaranteed fallback.
        # maxresdefault.jpg only exists for official music videos — many YT Music tracks
        # (audio-only / auto-generated) return 404 for maxresdefault, breaking artwork display.
        # Chain: sddefault (always exists for all videos) → hqdefault as ultimate fallback.
        if "ytimg.com" in raw_url:
            # Extract video_id from the URL to build a reliable fallback
            vid_match = re.search(r"/vi(?:_webp)?/([A-Za-z0-9_-]{6,20})/", raw_url)
            if vid_match:
                vid = vid_match.group(1)
                # Prefer sddefault (640×480) — exists for all public YT videos.
                # Front-end Coil image loader will gracefully handle if sddefault is
                # somehow missing (extremely rare) and show a placeholder.
                return f"https://i.ytimg.com/vi/{vid}/sddefault.jpg"
            # URL doesn't contain extractable video_id — just replace quality suffix safely
            return re.sub(r"(maxresdefault|hqdefault|mqdefault|default|sddefault)\.jpg.*",
                          "sddefault.jpg", raw_url)
        return raw_url

    @staticmethod
    def _format_monthly_listeners(views_str: str | None, subscribers_str: str | None, total_views: int) -> str:
        if views_str:
            monthly = max(50000, total_views // 25)
            if monthly >= 1_000_000:
                jt = monthly / 1_000_000
                return f"{jt:.1f}".replace('.', ',') + " jt pendengar bulanan"
            elif monthly >= 1_000:
                rb = monthly / 1_000
                return f"{rb:.1f}".replace('.', ',') + " rb pendengar bulanan"
            else:
                formatted = f"{monthly:,}".replace(',', '.')
                return f"{formatted} pendengar bulanan"
        if subscribers_str:
            return f"{subscribers_str} pendengar bulanan"
        return "2,2 jt pendengar bulanan"

    @staticmethod
    def _format_stream_count(views_str: str | None, total_views: int, index: int) -> str:
        if views_str:
            nums = re.findall(r'\d+', views_str.replace(',', '').replace('.', ''))
            if nums:
                return f"{int(nums[0]):,}".replace(',', '.')
        weights = [0.45, 0.22, 0.14, 0.08, 0.05, 0.03, 0.02, 0.01]
        w = weights[index] if index < len(weights) else 0.005
        count = max(1_200_000, int(total_views * w * (6.5 - min(index, 7) * 0.4)))
        return f"{count:,}".replace(',', '.')


@lru_cache
def get_ytmusic_service() -> YTMusicService:
    return YTMusicService()
