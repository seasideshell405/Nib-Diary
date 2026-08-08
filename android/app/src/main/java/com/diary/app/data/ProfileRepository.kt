package com.diary.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local profile persistence (SharedPreferences). */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("mine", Context.MODE_PRIVATE)

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, "日记本") ?: "日记本"
        set(value) = prefs.edit().putString(KEY_NICKNAME, value).apply()

    var signature: String
        get() = prefs.getString(KEY_SIGNATURE, "记录每一天") ?: "记录每一天"
        set(value) = prefs.edit().putString(KEY_SIGNATURE, value).apply()

    var avatarUrl: String
        get() = prefs.getString(KEY_AVATAR_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AVATAR_URL, value).apply()

    var updatedAt: Long
        get() = prefs.getLong(KEY_UPDATED_AT, 0)
        set(value) = prefs.edit().putLong(KEY_UPDATED_AT, value).apply()

    private companion object {
        const val KEY_NICKNAME = "nickname"
        const val KEY_SIGNATURE = "signature"
        const val KEY_AVATAR_URL = "avatarUrl"
        const val KEY_UPDATED_AT = "updatedAt"
    }
}

/** Profile access combining local storage with the server copy. */
class ProfileRepository(
    private val local: ProfileStore,
    private val api: DiaryApi,
) {

    fun load(): WireProfile = WireProfile(
        nickname = local.nickname,
        signature = local.signature,
        avatarUrl = local.avatarUrl,
        updatedAt = local.updatedAt,
    )

    /** Saves locally and pushes to the server (best effort). */
    suspend fun save(profile: WireProfile) = withContext(Dispatchers.IO) {
        val stamped = profile.copy(updatedAt = System.currentTimeMillis())
        local.nickname = stamped.nickname
        local.signature = stamped.signature
        local.avatarUrl = stamped.avatarUrl
        local.updatedAt = stamped.updatedAt
        api.putProfile(stamped)
    }

    /**
     * Pulls the server profile and applies it locally. Used on app open and
     * after restoring on a new device. The server copy wins.
     */
    suspend fun pullFromServer() = withContext(Dispatchers.IO) {
        val remote = api.getProfile().getOrNull() ?: return@withContext
        if (remote.updatedAt == 0L && local.updatedAt > 0L) {
            // Empty remote (fresh server): push the local copy once.
            api.putProfile(load())
            return@withContext
        }
        local.nickname = remote.nickname
        local.signature = remote.signature
        local.avatarUrl = remote.avatarUrl
        local.updatedAt = remote.updatedAt
    }
}
