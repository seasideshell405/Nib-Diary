package com.diary.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Custom background image persistence (compressed copy in filesDir). */
class AppearanceStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val dir: File get() = File(appContext.filesDir, "appearance").apply { mkdirs() }

    val backgroundFile: File get() = File(dir, "background.jpg")

    /** Bumped on every change so UI observing it re-decodes the image. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** User-edited block format JSON (null = defaults). */
    private val _blockStylesJson = MutableStateFlow(prefs.getString(KEY_BLOCK_STYLES, null))
    val blockStylesJson: StateFlow<String?> = _blockStylesJson.asStateFlow()

    fun setBlockStylesJson(json: String) {
        prefs.edit().putString(KEY_BLOCK_STYLES, json).apply()
        _blockStylesJson.value = json
    }

    fun resetBlockStyles() {
        prefs.edit().remove(KEY_BLOCK_STYLES).apply()
        _blockStylesJson.value = null
    }

    fun hasCustomBackground(): Boolean = prefs.getBoolean(KEY_BACKGROUND, false)

    suspend fun setBackground(uri: Uri) = withContext(Dispatchers.IO) {
        savePicked(uri, backgroundFile)
        prefs.edit().putBoolean(KEY_BACKGROUND, true).apply()
        _version.value++
    }

    fun clearBackground() {
        backgroundFile.delete()
        prefs.edit().putBoolean(KEY_BACKGROUND, false).apply()
        _version.value++
    }

    /** Copies a picked image into [output], compressed like diary photos. */
    private fun savePicked(uri: Uri, output: File) {
        val tmp = File(appContext.cacheDir, "picked_${System.currentTimeMillis()}.jpg")
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: throw IOException("无法读取所选图片")
            ImageCompressor.compress(tmp, output)
        } finally {
            tmp.delete()
        }
    }

    private companion object {
        const val KEY_BACKGROUND = "custom_background"
        const val KEY_BLOCK_STYLES = "block_styles"
    }
}
