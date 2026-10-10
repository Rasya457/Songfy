package com.lagu.songfy.data.remote.api

import com.lagu.songfy.data.remote.dto.ArtistDetailDto
import com.lagu.songfy.data.remote.dto.LyricsResponseDto
import com.lagu.songfy.data.remote.dto.SearchResponseDto
import com.lagu.songfy.data.remote.dto.StreamResponseDto
import com.lagu.songfy.data.remote.dto.PlaylistDetailResponseDto
import com.lagu.songfy.data.remote.dto.PlaylistSummaryDto
import com.lagu.songfy.data.remote.dto.SessionStartRequestDto
import com.lagu.songfy.data.remote.dto.SessionStartResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface SongfyApiService {

    @GET("api/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("filter") filter: String? = null
    ): SearchResponseDto

    @GET("api/v1/playlist/top")
    suspend fun getTopPlaylists(): List<PlaylistSummaryDto>

    @GET("api/v1/playlist/{playlistId}")
    suspend fun getPlaylist(
        @Path("playlistId") playlistId: String
    ): PlaylistDetailResponseDto

    @GET("api/v1/artist/{artistId}")
    suspend fun getArtist(
        @Path("artistId") artistId: String
    ): ArtistDetailDto

    @GET("api/v1/stream/{videoId}")
    suspend fun getStreamUrl(
        @Path("videoId") videoId: String
    ): StreamResponseDto

    @GET("api/v1/lyrics")
    suspend fun getLyrics(
        @Query("video_id") videoId: String,
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("duration") duration: Int? = null
    ): LyricsResponseDto

    /**
     * Server-side proxy download.
     * Backend resolves YouTube URL and proxies audio bytes to us.
     * This avoids IP-binding 403 errors when Android downloads directly from CDN.
     *
     * @Streaming prevents Retrofit from buffering the entire response body in memory.
     */
    @Streaming
    @GET("api/v1/download/{videoId}")
    suspend fun downloadAudio(
        @Path("videoId") videoId: String,
        @Query("title") title: String? = null
    ): Response<ResponseBody>

    @POST("api/v1/session/start")
    suspend fun startSession(
        @Header("Authorization") authHeader: String? = null,
        @Body request: SessionStartRequestDto
    ): SessionStartResponseDto
}