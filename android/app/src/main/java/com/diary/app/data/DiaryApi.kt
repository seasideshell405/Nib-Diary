package com.diary.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class WireEntry(
    val id: String,
    val title: String = "",
    val body: String,
    val mood: String = "",
    val weather: String = "",
    val diaryDate: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deletedAt: Long = 0,
)

@Serializable
data class SyncRequest(
    val entries: List<WireEntry>,
    val sinceSeq: Long = 0,
)

@Serializable
data class SyncResponse(
    val changes: List<WireEntry> = emptyList(),
    val serverSeq: Long = 0,
    val serverTime: Long = 0,
)

fun DiaryEntry.toWire() = WireEntry(
    id = id,
    title = title.orEmpty(),
    body = body,
    mood = mood.orEmpty(),
    weather = weather.orEmpty(),
    diaryDate = diaryDate,
    updatedAt = updatedAt,
    deleted = deleted,
    deletedAt = deletedAt ?: 0,
)

fun WireEntry.toLocal() = DiaryEntry(
    id = id,
    title = title.takeIf { it.isNotEmpty() },
    body = body,
    mood = mood.takeIf { it.isNotEmpty() },
    weather = weather.takeIf { it.isNotEmpty() },
    diaryDate = diaryDate,
    updatedAt = updatedAt,
    deleted = deleted,
    deletedAt = deletedAt.takeIf { it != 0L },
)

@Serializable
data class WireProfile(
    val nickname: String = "",
    val signature: String = "",
    val avatarUrl: String = "",
    val updatedAt: Long = 0,
)

sealed class SyncFailure : Exception() {
    data object NotConfigured : SyncFailure()
    data object Unauthorized : SyncFailure()
    data object Network : SyncFailure()
}

interface DiaryApiContract {
    suspend fun sync(entries: List<WireEntry>, sinceSeq: Long): Result<SyncResponse>
    suspend fun getProfile(): Result<WireProfile>
    suspend fun putProfile(profile: WireProfile): Result<WireProfile>
}

class DiaryApi(
    private val client: OkHttpClient,
    private val config: ConfigStore,
) : DiaryApiContract {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun sync(entries: List<WireEntry>, sinceSeq: Long): Result<SyncResponse> {
        val serverUrl = config.getServerUrl()
        val token = config.getToken()
        if (serverUrl.isBlank() || token.isBlank()) {
            return Result.failure(SyncFailure.NotConfigured)
        }

        val body = json.encodeToString(
            SyncRequest.serializer(),
            SyncRequest(entries = entries, sinceSeq = sinceSeq),
        )
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/sync")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    Result.failure(SyncFailure.Unauthorized)
                } else if (!response.isSuccessful) {
                    Result.failure(SyncFailure.Network)
                } else {
                    val text = response.body?.string().orEmpty()
                    Result.success(json.decodeFromString(SyncResponse.serializer(), text))
                }
            }
        } catch (e: IOException) {
            Result.failure(SyncFailure.Network)
        }
    }

    override suspend fun getProfile(): Result<WireProfile> {
        val serverUrl = config.getServerUrl()
        val token = config.getToken()
        if (serverUrl.isBlank() || token.isBlank()) {
            return Result.failure(SyncFailure.NotConfigured)
        }
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/profile")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    Result.failure(SyncFailure.Unauthorized)
                } else if (!response.isSuccessful) {
                    Result.failure(SyncFailure.Network)
                } else {
                    val text = response.body?.string().orEmpty()
                    Result.success(json.decodeFromString(WireProfile.serializer(), text))
                }
            }
        } catch (e: IOException) {
            Result.failure(SyncFailure.Network)
        }
    }

    override suspend fun putProfile(profile: WireProfile): Result<WireProfile> {
        val serverUrl = config.getServerUrl()
        val token = config.getToken()
        if (serverUrl.isBlank() || token.isBlank()) {
            return Result.failure(SyncFailure.NotConfigured)
        }
        val body = json.encodeToString(WireProfile.serializer(), profile)
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/profile")
            .header("Authorization", "Bearer $token")
            .put(body.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    Result.failure(SyncFailure.Unauthorized)
                } else if (!response.isSuccessful) {
                    Result.failure(SyncFailure.Network)
                } else {
                    val text = response.body?.string().orEmpty()
                    Result.success(json.decodeFromString(WireProfile.serializer(), text))
                }
            }
        } catch (e: IOException) {
            Result.failure(SyncFailure.Network)
        }
    }

    companion object {
        fun create(config: ConfigStore): DiaryApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return DiaryApi(client, config)
        }
    }
}
