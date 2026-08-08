package com.diary.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ImageImporter(
    private val context: Context,
    private val fileStore: ImageFileStore,
) {
    /**
     * Imports an image picked by the user: compresses it (the only kept
     * version, per ADR-0003), generates a thumbnail, returns the marker id.
     */
    suspend fun import(source: File): String = withContext(Dispatchers.IO) {
        val id = java.util.UUID.randomUUID().toString()
        val full = fileStore.fullFile(id)
        val thumb = fileStore.thumbFile(id)
        try {
            ImageCompressor.compress(source, full)
        } catch (e: IOException) {
            throw e
        }
        try {
            ImageCompressor.thumbnail(full, thumb)
        } catch (_: Exception) {
        }
        id
    }
}
