package com.diary.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageCompressor {

    const val JPEG_QUALITY = 85
    const val THUMB_DIMENSION = 256

    /**
     * Converts [input] to a WebP file at [output]: format only, the
     * original resolution is kept (no downsampling). EXIF rotation is
     * baked in. The converted version is the ONLY version kept.
     */
    fun compress(input: File, output: File) {
        val rotation = readRotation(input)
        var bitmap = BitmapFactory.decodeFile(input.absolutePath)
            ?: throw IOException("decode failed: ${input.name}")

        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
                bitmap = rotated
            }
        }

        bitmap.compress(Bitmap.CompressFormat.WEBP, JPEG_QUALITY, FileOutputStream(output))
        bitmap.recycle()
    }

    /** Creates a small WebP thumbnail for list display. */
    fun thumbnail(input: File, output: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("unreadable image: ${input.name}")
        }

        var sample = 1
        while (bounds.outWidth / sample > THUMB_DIMENSION * 2 || bounds.outHeight / sample > THUMB_DIMENSION * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(input.absolutePath, options)
            ?: throw IOException("decode failed: ${input.name}")

        val scale = THUMB_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) {
            val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            if (scaled != bitmap) bitmap.recycle()
            scaled.compress(Bitmap.CompressFormat.WEBP, 80, FileOutputStream(output))
            scaled.recycle()
        } else {
            bitmap.compress(Bitmap.CompressFormat.WEBP, 80, FileOutputStream(output))
            bitmap.recycle()
        }
    }

    private fun readRotation(file: File): Int = try {
        val exif = ExifInterface(file)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
}

/** Manages local image files under filesDir/images/. New files are WebP;
 *  legacy .jpg files from older builds still resolve via [existingFull]
 *  and [existingThumb]. */
class ImageFileStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    fun fullFile(id: String): File = File(dir, "$id.webp")

    fun thumbFile(id: String): File = File(dir, "${id}_thumb.webp")

    /** The image file that actually exists: WebP preferred, legacy JPEG fallback. */
    fun existingFull(id: String): File? =
        fullFile(id).takeIf { it.exists() }
            ?: File(dir, "$id.jpg").takeIf { it.exists() }

    /** The thumbnail file that actually exists: WebP preferred, legacy JPEG fallback. */
    fun existingThumb(id: String): File? =
        thumbFile(id).takeIf { it.exists() }
            ?: File(dir, "${id}_thumb.jpg").takeIf { it.exists() }

    fun deleteAll(id: String) {
        fullFile(id).delete()
        thumbFile(id).delete()
        File(dir, "$id.jpg").delete()
        File(dir, "${id}_thumb.jpg").delete()
    }
}
