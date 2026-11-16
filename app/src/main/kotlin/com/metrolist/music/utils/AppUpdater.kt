package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.metrolist.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class AppReleaseInfo(
    val versionName: String,
    val title: String,
    val changelog: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
)

object AppUpdater {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Rasya457/Songfy/releases/latest"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").substringBefore("+").substringBefore("-")

    fun isNewerVersion(remote: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        val rParts = normalizeVersion(remote).split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = normalizeVersion(current).split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun fetchLatestRelease(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Songfy/${BuildConfig.VERSION_NAME}")
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body.string()
            }

            val obj = json.parseToJsonElement(body).jsonObject
            val tagName = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val version = normalizeVersion(tagName)
            val title = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Songfy $version"
            val changelog = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()

            val assets = obj["assets"]?.jsonArray.orEmpty()
            val apkAsset = assets.firstOrNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull.equals("Songfy.apk", ignoreCase = true)
            } ?: assets.firstOrNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true
            } ?: return@runCatching null

            val apkUrl = apkAsset.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull
                ?: return@runCatching null
            val apkSize = apkAsset.jsonObject["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

            AppReleaseInfo(
                versionName = version,
                title = title,
                changelog = changelog,
                apkUrl = apkUrl,
                apkSizeBytes = apkSize,
            )
        }.getOrNull()
    }

    suspend fun downloadApk(
        url: String,
        destFile: File,
        expectedSizeBytes: Long,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Songfy/${BuildConfig.VERSION_NAME}")
            .build()

        val call = httpClient.newCall(request)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                error("Gagal mengunduh update (HTTP ${response.code})")
            }
            val body = response.body
            val total = body.contentLength().takeIf { it > 0L } ?: expectedSizeBytes
            val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
            if (destFile.exists()) destFile.delete()
            tempFile.renameTo(destFile)
            onProgress(1f)
            destFile
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(permIntent)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
        return true
    }
}
