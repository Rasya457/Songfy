package com.lagu.songfy.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.BuildConfig
import com.lagu.songfy.data.remote.api.SongfyApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Intelligent Interceptor that retries on candidate backend hosts if the primary host fails.
 * Supports:
 * - Direct Wi-Fi IP (192.168.1.12:8000)
 * - USB adb reverse loopback (127.0.0.1:8000)
 * - Android Emulator loopback (10.0.2.2:8000)
 */
class HostFallbackInterceptor(private val context: Context? = null) : Interceptor {
    private val candidateHosts = listOf(
        "192.168.8.133:8000",
        "192.168.1.5:8000",
        "192.168.1.12:8000",
        "127.0.0.1:8000",
        "10.0.2.2:8000"
    )

    companion object {
        @Volatile
        private var activeHost: String? = null
        private var isInitialized = false
    }

    private fun getPersistedHost(): String? {
        if (context == null) return null
        return try {
            val value = context.getSharedPreferences("songfy_net_prefs", Context.MODE_PRIVATE)
                .getString("active_host", null)
            // Validasi format "host:port" — tolak string kosong atau tidak valid
            if (value.isNullOrBlank() || !value.contains(":")) null else value
        } catch (_: Exception) {
            null
        }
    }

    private fun persistHost(host: String) {
        if (context == null) return
        try {
            val prefs = context.getSharedPreferences("songfy_net_prefs", Context.MODE_PRIVATE)
            if (host.isBlank()) {
                // Hapus key sepenuhnya, bukan simpan string kosong
                // (string kosong menyebabkan .host("") → IllegalArgumentException di OkHttp)
                prefs.edit().remove("active_host").apply()
            } else {
                prefs.edit().putString("active_host", host).apply()
            }
        } catch (_: Exception) {}
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isInitialized) {
            activeHost = getPersistedHost()
            isInitialized = true
        }

        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        val currentWorking = activeHost?.takeIf { it.contains(":") }
        val requestToTry = if (currentWorking != null &&
            "${originalUrl.host}:${originalUrl.port}" != currentWorking) {
            try {
                val parts = currentWorking.split(":")
                val host = parts[0]
                val port = parts[1].toInt()
                val scheme = if (host.startsWith("192.") || host.startsWith("127.") || host.startsWith("10.") || port == 8000) "http" else originalUrl.scheme
                val newUrl = originalUrl.newBuilder()
                    .scheme(scheme)
                    .host(host)
                    .port(port)
                    .build()
                originalRequest.newBuilder().url(newUrl).build()
            } catch (_: Exception) {
                // Host format tidak valid — reset dan pakai request original
                activeHost = null
                persistHost("")
                originalRequest
            }
        } else {
            originalRequest
        }

        try {
            val response = chain.proceed(requestToTry)
            if (response.isSuccessful) {
                val hostPort = "${requestToTry.url.host}:${requestToTry.url.port}"
                activeHost = hostPort
                persistHost(hostPort)
            }
            return response
        } catch (e: IOException) {
            // Host yang tersimpan tidak bisa dihubungi — hapus cache agar tidak stuck ke IP lama
            if (currentWorking != null) {
                activeHost = null
                persistHost("") // akan memanggil remove(), BUKAN simpan string kosong
            }

            val fastChain = try {
                chain.withConnectTimeout(3000, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                chain
            }

            for (candidate in candidateHosts) {
                val parts = candidate.split(":")
                if (parts.size != 2) continue
                val host = parts[0]
                val port = parts[1].toIntOrNull() ?: continue
                if (host == requestToTry.url.host && port == requestToTry.url.port) continue

                try {
                    val scheme = if (host.startsWith("192.") || host.startsWith("127.") || host.startsWith("10.") || port == 8000) "http" else originalUrl.scheme
                    val retryUrl = originalUrl.newBuilder()
                        .scheme(scheme)
                        .host(host)
                        .port(port)
                        .build()
                    val retryRequest = originalRequest.newBuilder().url(retryUrl).build()
                    val response = fastChain.proceed(retryRequest)
                    if (response.isSuccessful) {
                        activeHost = candidate
                        persistHost(candidate)
                        return response
                    }
                } catch (_: Exception) {
                    // Try next candidate
                }
            }
            throw e
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BASIC
            else
                HttpLoggingInterceptor.Level.NONE
        }

    @Provides
    @Singleton
    fun provideAuthInterceptor(firebaseAuth: FirebaseAuth): Interceptor =
        object : Interceptor {
            @Volatile private var cachedToken: String? = null
            @Volatile private var tokenExpiryEpochMs: Long = 0L

            override fun intercept(chain: Interceptor.Chain): Response {
                val user = firebaseAuth.currentUser
                if (user == null) {
                    cachedToken = null
                    return chain.proceed(chain.request())
                }

                val now = System.currentTimeMillis()
                // Cache token in memory; re-request only if null or expiring within 5 minutes
                val token = if (cachedToken != null && now < (tokenExpiryEpochMs - 300_000L)) {
                    cachedToken
                } else {
                    try {
                        val tokenTask = user.getIdToken(false)
                        val res = com.google.android.gms.tasks.Tasks.await(tokenTask, 3, TimeUnit.SECONDS)
                        val tok = res?.token
                        if (tok != null) {
                            cachedToken = tok
                            tokenExpiryEpochMs = if (res.expirationTimestamp > 0) {
                                res.expirationTimestamp * 1000L
                            } else {
                                now + 3300_000L
                            }
                            tok
                        } else cachedToken
                    } catch (_: Exception) {
                        cachedToken
                    }
                }

                val request = if (token != null) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                return chain.proceed(request)
            }
        }

    @Provides
    @Singleton
    fun provideOkHttpCache(@ApplicationContext context: Context): Cache {
        val cacheDir = File(context.cacheDir, "http_cache")
        return Cache(cacheDir, 10L * 1024 * 1024)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: Interceptor,
        cache: Cache
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cache(cache)
        // HostFallbackInterceptor (IP LAN 192.168/127.0.0.1/10.0.2.2) hanya untuk
        // debug. Di release, baseUrl adalah cloud — fallback ke IP LAN hanya
        // menambah latency + perilaku acak (bisa nyangkut ke laptop yang kebetulan
        // nyala). Release langsung pakai URL cloud apa adanya.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HostFallbackInterceptor(context))
        }
        return builder
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            // 60s: request pertama setelah free host tidur (cold start) bisa 30-60 dtk.
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        val cleanedUrl = try {
            BuildConfig.BASE_URL
                .replace("[", "")
                .replace("]", "")
                .trim()
                .let { url ->
                    val withScheme = if (!url.startsWith("http://") && !url.startsWith("https://")) "http://$url" else url
                    if (!withScheme.endsWith("/")) "$withScheme/" else withScheme
                }
        } catch (_: Exception) {
            "http://127.0.0.1:8000/"
        }
        return Retrofit.Builder()
            .baseUrl(cleanedUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideSongfyApiService(retrofit: Retrofit): SongfyApiService =
        retrofit.create(SongfyApiService::class.java)

    /**
     * Dedicated OkHttpClient for large-file downloads.
     * Timeout 5 menit agar download audio 30MB+ di koneksi lambat tidak terputus.
     * Pakai auth interceptor yang sama agar token Firebase tetap disertakan.
     * Tidak pakai cache (download audio langsung disimpan ke disk).
     */
    @Provides
    @Singleton
    @Named("download")
    fun provideDownloadOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: Interceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HostFallbackInterceptor(context))
        }
        return builder
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)   // 5 min for large audio files
            .writeTimeout(5, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("download")
    fun provideDownloadRetrofit(@Named("download") okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    @Named("download")
    fun provideDownloadApiService(@Named("download") retrofit: Retrofit): SongfyApiService =
        retrofit.create(SongfyApiService::class.java)
}
