/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * LyricsGoogleTranslator.kt — On-demand lyric translation via the lightweight Google Translate
 * public endpoint. No API key required.
 *
 * Cache strategy:
 *   - Memory: LRU, max 12 songs (songId → translated lines)
 *   - Disk: single JSON file, max 2 MB, evicts oldest entries when full
 *
 * Error policy: if the response line count mismatches the original, the entire result is discarded.
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object LyricsGoogleTranslator {

    private const val DISK_CACHE_MAX_BYTES = 2 * 1024 * 1024L // 2 MB
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val MAX_CHUNK_LINES = 25
    private const val MAX_CHUNK_CHARS = 1200
    private const val SUB_CHUNK_SIZE = 5
    private const val MAX_RETRIES = 2

    private val LRC_TAG_REGEX = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\]|<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
    private val META_TAG_REGEX = Regex("""^\[(?:ti|ar|al|by|offset|re|ve|length|bg):[^\]]*\]""", RegexOption.IGNORE_CASE)
    private val SPEAKER_PREFIX_REGEX = Regex("""^(?:v\d+|bg)\s*:\s*""", RegexOption.IGNORE_CASE)
    private val SPECIAL_SYMBOLS_REGEX = Regex("""[♪♫♬♩♭♮♯\u200B-\u200D\uFEFF\u0000-\u001F]""")
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")

    // Memory cache: songId -> list of translated lines (parallel to original lines)
    private val memCache = object : LruCache<String, List<String>>(16) {}

    /**
     * Cleans a single lyric line from LRC timestamps, metadata tags, musical symbols,
     * and control characters that could break translation parsing.
     */
    fun sanitizeLine(raw: String): String {
        if (raw.isBlank()) return ""
        val cleaned = raw
            .replace(LRC_TAG_REGEX, "")
            .replace(META_TAG_REGEX, "")
            .replace(SPEAKER_PREFIX_REGEX, "")
            .replace(SPECIAL_SYMBOLS_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        // Ignore lines that have no actual letters (e.g. only punctuation or dashes)
        return if (cleaned.any { it.isLetter() }) cleaned else ""
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Translates [lines] to Indonesian. Returns translated lines in the exact same indices
     * as [lines] (with empty strings for blank/instrumental lines), or null if all requests fail.
     *
     * Results are cached in memory (LRU 16) and on disk (2 MB).
     */
    suspend fun translate(
        context: Context,
        songId: String,
        lines: List<String>,
    ): List<String>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext null

        // 1. Memory cache hit
        memCache.get(songId)?.let { cached ->
            if (cached.size == lines.size) return@withContext cached
        }

        // 2. Disk cache hit
        readDiskCache(context, songId)?.let { cached ->
            if (cached.size == lines.size) {
                memCache.put(songId, cached)
                return@withContext cached
            }
        }

        // 3. Sanitize and index only meaningful non-blank lines
        val indexedValidLines = lines.mapIndexedNotNull { index, raw ->
            val clean = sanitizeLine(raw)
            if (clean.isNotEmpty()) index to clean else null
        }

        if (indexedValidLines.isEmpty()) return@withContext null

        // 4. Build safe chunks respecting both line count and character count limits
        val chunks = buildSafeChunks(indexedValidLines)
        val mappedResult = MutableList(lines.size) { "" }
        var anySuccess = false

        for (chunk in chunks) {
            val chunkTexts = chunk.map { it.second }
            val translatedChunk = translateChunkWithFallback(chunkTexts)
            for (i in chunk.indices) {
                val originalIndex = chunk[i].first
                val translatedText = translatedChunk.getOrNull(i)?.trim().orEmpty()
                if (translatedText.isNotEmpty()) {
                    mappedResult[originalIndex] = translatedText
                    anySuccess = true
                }
            }
        }

        if (!anySuccess) return@withContext null

        // 5. Write caches
        memCache.put(songId, mappedResult)
        writeDiskCache(context, songId, mappedResult)

        mappedResult
    }

    /**
     * Detects the language of [lines] using Google Translate's auto-detect.
     * Returns the BCP-47 language code (e.g. "id", "en", "ja") or null on failure.
     */
    suspend fun detectLanguage(lines: List<String>): String? = withContext(Dispatchers.IO) {
        try {
            val sample = lines
                .map { sanitizeLine(it) }
                .filter { it.isNotEmpty() }
                .take(5)
                .joinToString("\n")
            if (sample.isBlank()) return@withContext null

            val encoded = URLEncoder.encode(sample, StandardCharsets.UTF_8.name())
            val url = "$ENDPOINT?client=gtx&sl=auto&tl=id&dt=t&q=$encoded"
            val response = executeGetRequest(url, connectTimeoutMs = 5_000, readTimeoutMs = 8_000)
                ?: return@withContext null
            // Index [2] in Google's response is the detected source language code
            val lang = JSONArray(response).optString(2).lowercase().trim()
            // Treat Indonesian ("id" or legacy "in") consistently as "id"
            when (lang) {
                "id", "in" -> "id"
                "" -> null
                else -> lang
            }
        } catch (_: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chunking, Retry & Fallback
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildSafeChunks(indexedLines: List<Pair<Int, String>>): List<List<Pair<Int, String>>> {
        val result = mutableListOf<List<Pair<Int, String>>>()
        var currentChunk = mutableListOf<Pair<Int, String>>()
        var currentChars = 0

        for (item in indexedLines) {
            val len = item.second.length + 1
            if (currentChunk.isNotEmpty() &&
                (currentChunk.size >= MAX_CHUNK_LINES || currentChars + len > MAX_CHUNK_CHARS)
            ) {
                result.add(currentChunk)
                currentChunk = mutableListOf()
                currentChars = 0
            }
            currentChunk.add(item)
            currentChars += len
        }
        if (currentChunk.isNotEmpty()) {
            result.add(currentChunk)
        }
        return result
    }

    /**
     * Translates a chunk of sanitized non-empty lines with:
     * 1. Automatic retry on network/timeout failure
     * 2. Sub-chunk splitting (5 lines) if line-count alignment fails
     * 3. Per-line fallback if a sub-chunk still merges/drops a newline
     */
    private suspend fun translateChunkWithFallback(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()
        if (lines.size == 1) {
            return listOf(translateSingleLineWithRetry(lines[0]) ?: "")
        }

        // Stage 1: Try full chunk with retries
        for (attempt in 0 until MAX_RETRIES) {
            val batchResult = requestTranslateBatch(lines)
            if (batchResult != null && batchResult.size == lines.size) {
                return batchResult
            }
            if (attempt < MAX_RETRIES - 1) delay(250L * (attempt + 1))
        }

        // Stage 2: Split into smaller sub-chunks (or halves if already <= SUB_CHUNK_SIZE)
        val step = if (lines.size > SUB_CHUNK_SIZE) SUB_CHUNK_SIZE else (lines.size / 2).coerceAtLeast(1)
        val combined = mutableListOf<String>()

        for (subChunk in lines.chunked(step)) {
            if (subChunk.size == 1) {
                combined.add(translateSingleLineWithRetry(subChunk[0]) ?: "")
                continue
            }

            var subResult: List<String>? = null
            for (attempt in 0 until MAX_RETRIES) {
                val res = requestTranslateBatch(subChunk)
                if (res != null && res.size == subChunk.size) {
                    subResult = res
                    break
                }
                if (attempt < MAX_RETRIES - 1) delay(200L)
            }

            if (subResult != null) {
                combined.addAll(subResult)
            } else {
                // Stage 3: Per-line fallback so no line in the sub-chunk is lost or misaligned
                for (line in subChunk) {
                    combined.add(translateSingleLineWithRetry(line) ?: "")
                    delay(80L)
                }
            }
        }

        return combined
    }

    private suspend fun translateSingleLineWithRetry(line: String): String? {
        for (attempt in 0 until MAX_RETRIES) {
            val res = requestTranslateSingle(line)
            if (!res.isNullOrBlank()) return res
            if (attempt < MAX_RETRIES - 1) delay(200L)
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network
    // ─────────────────────────────────────────────────────────────────────────

    private fun requestTranslateBatch(lines: List<String>): List<String>? {
        return try {
            val joined = lines.joinToString("\n")
            val encoded = URLEncoder.encode(joined, StandardCharsets.UTF_8.name())
            val url = "$ENDPOINT?client=gtx&sl=auto&tl=id&dt=t&q=$encoded"
            val response = executeGetRequest(url, connectTimeoutMs = 8_000, readTimeoutMs = 12_000)
                ?: return null
            parseGoogleTranslateBatchResponse(response, lines.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun requestTranslateSingle(line: String): String? {
        return try {
            val encoded = URLEncoder.encode(line, StandardCharsets.UTF_8.name())
            val url = "$ENDPOINT?client=gtx&sl=auto&tl=id&dt=t&q=$encoded"
            val response = executeGetRequest(url, connectTimeoutMs = 6_000, readTimeoutMs = 10_000)
                ?: return null
            val outer = JSONArray(response)
            val sentences = outer.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val pair = sentences.optJSONArray(i) ?: continue
                sb.append(pair.optString(0))
            }
            sb.toString().replace("\n", " ").trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun executeGetRequest(
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Accept-Charset", "UTF-8")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parses Google Translate response and ensures exact [expectedCount] line alignment.
     */
    private fun parseGoogleTranslateBatchResponse(json: String, expectedCount: Int): List<String>? {
        return try {
            val outer = JSONArray(json)
            val sentences = outer.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val pair = sentences.optJSONArray(i) ?: continue
                sb.append(pair.optString(0))
            }
            val rawLines = sb.toString().split("\n").map { it.trim() }
            // Drop trailing empty element if Google appended a trailing newline
            val trimmedLines = if (rawLines.size == expectedCount + 1 && rawLines.last().isEmpty()) {
                rawLines.dropLast(1)
            } else {
                rawLines
            }

            if (trimmedLines.size == expectedCount && trimmedLines.all { it.isNotEmpty() }) {
                trimmedLines
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Disk cache (simple JSON, max 2 MB)
    // ─────────────────────────────────────────────────────────────────────────

    private fun cacheFile(context: Context) =
        File(context.cacheDir, "lyrics_translations.json")

    private fun readDiskCache(context: Context, songId: String): List<String>? {
        return try {
            val file = cacheFile(context)
            if (!file.exists()) return null
            val root = org.json.JSONObject(file.readText())
            val entry = root.optJSONArray(songId) ?: return null
            List(entry.length()) { entry.getString(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeDiskCache(context: Context, songId: String, lines: List<String>) {
        try {
            val file = cacheFile(context)
            val root = if (file.exists()) {
                try { org.json.JSONObject(file.readText()) } catch (_: Exception) { org.json.JSONObject() }
            } else {
                org.json.JSONObject()
            }

            // Evict oldest entries if file would exceed 2 MB
            val newEntry = JSONArray(lines)
            root.put(songId, newEntry)
            var serialized = root.toString()
            if (serialized.toByteArray().size > DISK_CACHE_MAX_BYTES) {
                // Remove keys until under limit (order not guaranteed, just remove first found)
                val keys = root.keys().asSequence().toMutableList()
                keys.remove(songId)
                for (key in keys) {
                    root.remove(key)
                    serialized = root.toString()
                    if (serialized.toByteArray().size <= DISK_CACHE_MAX_BYTES) break
                }
            }

            file.writeText(serialized)
        } catch (_: Exception) {
            // Non-critical — ignore disk write failures
        }
    }
}

