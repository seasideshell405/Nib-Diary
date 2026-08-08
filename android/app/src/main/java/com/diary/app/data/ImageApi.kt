package com.diary.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

interface ImageApiContract {
    suspend fun upload(id: String, entryId: String, file: File): Boolean
    suspend fun download(id: String, target: File): Boolean
    suspend fun deleteRemote(id: String): Boolean
}

class ImageApi(
    private val client: OkHttpClient,
    private val config: ConfigStore,
) : ImageApiContract {

    override suspend fun upload(id: String, entryId: String, file: File): Boolean {
        val serverUrl = config.getServerUrl()
        val token = config.getToken()
        if (serverUrl.isBlank() || token.isBlank()) return false

        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/images/$id?entryId=$entryId")
            .header("Authorization", "Bearer $token")
            .put(file.asRequestBody("image/webp".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun download(id: String, target: File): Boolean {
        val serverUrl = config.getServerUrl()
        val token = config.getToken()
        if (serverUrl.isBlank() || token.isBlank()) return false

        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/images/$id")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                target.outputStream().use { out ->
                    response.body?.byteStream()?.copyTo(out)
                }
                true
            }
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun deleteRemote(id: String): Boolean {
        val serverUrl = config.getServerUrl()
        val token = config.getToken()
        if (serverUrl.isBlank() || token.isBlank()) return false

        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/images/$id")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()

        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }

    companion object {
        fun create(config: ConfigStore): ImageApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
            return ImageApi(client, config)
        }
    }
}
