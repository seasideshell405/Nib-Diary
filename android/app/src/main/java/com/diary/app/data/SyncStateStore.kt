package com.diary.app.data

import android.content.Context

class SyncStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)

    /** Server-side change watermark (seq). Pull uses this; never misses entries. */
    fun getServerSeq(): Long = prefs.getLong("server_seq", 0L)

    fun setServerSeq(value: Long) {
        prefs.edit().putLong("server_seq", value).apply()
    }

    /** Local push watermark: newest entry timestamp already pushed. */
    fun getPushWatermark(): Long = prefs.getLong("push_watermark", 0L)

    fun setPushWatermark(value: Long) {
        prefs.edit().putLong("push_watermark", value).apply()
    }
}

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun getServerUrl(): String = prefs.getString("server_url", "").orEmpty()

    fun getToken(): String = prefs.getString("token", "").orEmpty()

    fun save(serverUrl: String, token: String) {
        prefs.edit().putString("server_url", serverUrl).putString("token", token).apply()
    }

    fun isConfigured(): Boolean = getServerUrl().isNotBlank() && getToken().isNotBlank()
}
