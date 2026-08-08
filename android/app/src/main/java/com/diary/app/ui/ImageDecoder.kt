package com.diary.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Bitmap decoding helpers. All decode work here is meant to run on a
 * background dispatcher: decoding a full 2000px JPEG on the UI thread is
 * a guaranteed frame drop.
 */
object ImageDecoder {

    /**
     * Decodes [file] with power-of-two downsampling so the longest side
     * stays around [maxDimension] pixels. Fits display sizes without
     * allocating full-resolution bitmaps (a 2000px JPEG decodes to
     * ~12MB in memory, a 1000px one to ~4MB and roughly 4x faster).
     */
    fun decodeSampled(file: File, maxDimension: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /**
     * Decodes [file] sampling by WIDTH: the decoded width stays around
     * [targetWidth] while the height follows the image's aspect ratio.
     * Meant for tall images whose height would otherwise dominate the
     * longest-side sampling in [decodeSampled].
     */
    fun decodeByWidth(file: File, targetWidth: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > targetWidth) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }
}

/**
 * Unified on-page image display spec for reading / editing / random pages:
 * max width 85% of the page width (or the container when narrower), height
 * unrestricted, never upscaled, centered.
 */
object DiaryImageSpec {

    fun maxWidthPx(pageWidthPx: Int): Int = pageWidthPx * 85 / 100

    /**
     * Fits [bitmapW]x[bitmapH] keeping aspect: the width is FIXED at 85%
     * of the page width (small images are scaled up to it), the height
     * follows freely.
     */
    fun fittedSize(bitmapW: Int, bitmapH: Int, pageWidthPx: Int): Pair<Int, Int> {
        val targetW = maxWidthPx(pageWidthPx)
        val scale = targetW.toFloat() / bitmapW
        return (bitmapW * scale).toInt().coerceAtLeast(1) to (bitmapH * scale).toInt().coerceAtLeast(1)
    }
}
