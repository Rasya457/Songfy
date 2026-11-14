package com.metrolist.music.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * CoverBlurHelper — Pre-computes and caches background artwork blur on a worker thread.
 *
 * Implements a fast 3-pass moving-average box blur on a downscaled 128px bitmap.
 * Produces an ultra-soft, wide Gaussian-like blur stretched full-screen with 0 GPU RenderEffect
 * overhead during playback and UI animations.
 */
object CoverBlurHelper {
    private val blurCache = LruCache<String, Bitmap>(8)

    fun getCached(url: String): Bitmap? = blurCache.get(url)

    suspend fun getOrComputeBlur(
        context: Context,
        url: String,
        targetSize: Int = 128,
        radius: Int = 9,
        passes: Int = 3,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cached = blurCache.get(url)
        if (cached != null) return@withContext cached

        try {
            val req = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            val result = context.imageLoader.execute(req)
            val bitmap = result.image?.toBitmap() ?: return@withContext null
            val blurred = process(bitmap, targetSize, radius, passes)
            blurCache.put(url, blurred)
            blurred
        } catch (_: Exception) {
            null
        }
    }

    fun process(src: Bitmap, targetSize: Int = 128, radius: Int = 9, passes: Int = 3): Bitmap {
        val w = if (src.width >= src.height) targetSize else (targetSize * src.width / max(1, src.height))
        val h = if (src.height >= src.width) targetSize else (targetSize * src.height / max(1, src.width))
        val scaled = Bitmap.createScaledBitmap(src, max(16, w), max(16, h), true)
        val sw = scaled.width
        val sh = scaled.height

        val pix = IntArray(sw * sh)
        scaled.getPixels(pix, 0, sw, 0, 0, sw, sh)

        val out = IntArray(sw * sh)
        var current = pix
        var buffer = out

        repeat(passes) {
            boxBlurH(current, buffer, sw, sh, radius)
            boxBlurV(buffer, current, sw, sh, radius)
        }

        val result = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        result.setPixels(current, 0, sw, 0, 0, sw, sh)
        return result
    }

    private fun boxBlurH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val rowOffset = y * w
            var aSum = 0
            var rSum = 0
            var gSum = 0
            var bSum = 0

            for (i in -r..r) {
                val clampedX = i.coerceIn(0, w - 1)
                val c = src[rowOffset + clampedX]
                aSum += (c ushr 24) and 0xFF
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }

            for (x in 0 until w) {
                dst[rowOffset + x] = ((aSum / div) shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)

                val xRemove = (x - r).coerceIn(0, w - 1)
                val xAdd = (x + r + 1).coerceIn(0, w - 1)
                val cRemove = src[rowOffset + xRemove]
                val cAdd = src[rowOffset + xAdd]

                aSum += ((cAdd ushr 24) and 0xFF) - ((cRemove ushr 24) and 0xFF)
                rSum += ((cAdd ushr 16) and 0xFF) - ((cRemove ushr 16) and 0xFF)
                gSum += ((cAdd ushr 8) and 0xFF) - ((cRemove ushr 8) and 0xFF)
                bSum += (cAdd and 0xFF) - (cRemove and 0xFF)
            }
        }
    }

    private fun boxBlurV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var aSum = 0
            var rSum = 0
            var gSum = 0
            var bSum = 0

            for (i in -r..r) {
                val clampedY = i.coerceIn(0, h - 1)
                val c = src[clampedY * w + x]
                aSum += (c ushr 24) and 0xFF
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }

            for (y in 0 until h) {
                dst[y * w + x] = ((aSum / div) shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)

                val yRemove = (y - r).coerceIn(0, h - 1)
                val yAdd = (y + r + 1).coerceIn(0, h - 1)
                val cRemove = src[yRemove * w + x]
                val cAdd = src[yAdd * w + x]

                aSum += ((cAdd ushr 24) and 0xFF) - ((cRemove ushr 24) and 0xFF)
                rSum += ((cAdd ushr 16) and 0xFF) - ((cRemove ushr 16) and 0xFF)
                gSum += ((cAdd ushr 8) and 0xFF) - ((cRemove ushr 8) and 0xFF)
                bSum += (cAdd and 0xFF) - (cRemove and 0xFF)
            }
        }
    }
}

