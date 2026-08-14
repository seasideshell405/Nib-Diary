package com.diary.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.diary.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 新版本信息，来自 GitHub 仓库的最新 Release。versionCode 为 null 时用版本名语义化比较。 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long?,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState()
    data object Latest : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/** check() 的一次性结果：UI 据此弹提示（底部 Snackbar）或打开更新弹窗。 */
sealed class CheckResult {
    data object None : CheckResult()
    data object Latest : CheckResult()
    data object Found : CheckResult()
    data class Failed(val message: String) : CheckResult()
}

/**
 * 检查、下载并安装新版本。版本来源为 GitHub Releases：
 * 客户端请求仓库的 latest release，tag 约定为 v{versionName}-{versionCode}
 * （如 v0.1.1-9），版本比较只用 versionCode；Release 必须附带一个 .apk 附件。
 */
class UpdateManager(private val context: Context) {

    companion object {
        const val REPO = "seasideshell405/Nib-Diary"
        private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases/latest"
        private const val APK_DIR = "apk-downloads"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 启动静默检查发现的新版本：DiaryApp 弹全局提示用，弹过后置回 null。 */
    private val _discovered = MutableStateFlow<UpdateInfo?>(null)
    val discovered: StateFlow<UpdateInfo?> = _discovered.asStateFlow()

    fun consumeDiscovered() {
        _discovered.value = null
    }

    /**
     * 检查是否有新版本。silent=true 时（启动静默检查）只在发现新版本时改变状态，
     * 已是最新或失败都静默回到原状态，不打扰用户。
     */
    suspend fun check(silent: Boolean = false): CheckResult {
        val prev = _state.value
        if (prev is UpdateState.Checking || prev is UpdateState.Downloading) return CheckResult.None
        // 已经发现过新版本：直接复用结果，不重复请求网络。
        if (prev is UpdateState.Available) return CheckResult.Found
        _state.value = UpdateState.Checking
        return try {
            val info = withContext(Dispatchers.IO) { fetchLatest() }
            if (info == null) {
                // 仓库还没有任何 Release：和"已是最新"同样对待。
                if (silent) _state.value = prev
                else _state.value = UpdateState.Latest
                CheckResult.Latest
            } else if (isNewer(info)) {
                _state.value = UpdateState.Available(info)
                if (silent) _discovered.value = info
                CheckResult.Found
            } else {
                if (silent) _state.value = prev
                else _state.value = UpdateState.Latest
                CheckResult.Latest
            }
        } catch (e: Exception) {
            _state.value = if (silent) prev else UpdateState.Failed("检查更新失败，请稍后重试")
            CheckResult.Failed("检查更新失败，请检查网络")
        }
    }

    private fun isNewer(info: UpdateInfo): Boolean {
        val code = info.versionCode
        return if (code != null) {
            code > BuildConfig.VERSION_CODE
        } else {
            compareVersions(info.versionName, BuildConfig.VERSION_NAME) > 0
        }
    }

    /** 语义化比较版本号："0.1.2" > "0.1.11" 时按数字段比较。 */
    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split('.').map { it.toIntOrNull() ?: 0 }
        val bParts = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(aParts.size, bParts.size)) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    /**
     * 启动前台服务下载已发现的版本。服务在通知栏显示进度，下载期间进程不会被
     * 系统回收；完成后进入 ReadyToInstall（UI 调用 install() 触发系统安装器）。
     */
    suspend fun download() {
        val current = _state.value as? UpdateState.Available ?: return
        _state.value = UpdateState.Downloading(current.info, 0f)
        context.startForegroundService(Intent(context, UpdateDownloadService::class.java))
    }

    /** 由前台服务调用：执行实际下载并更新状态与进度。 */
    suspend fun runDownload(onProgress: (Float) -> Unit) {
        val current = _state.value as? UpdateState.Downloading ?: return
        val info = current.info
        try {
            val file = withContext(Dispatchers.IO) {
                downloadApk(info) { progress ->
                    _state.value = UpdateState.Downloading(info, progress)
                    onProgress(progress)
                }
            }
            _state.value = UpdateState.ReadyToInstall(info, file)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("下载失败，请检查网络后重试")
        }
    }

    /** 调起系统安装器。未授予"安装未知应用"权限时返回 false。 */
    fun install(): Boolean {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return false
        if (!context.packageManager.canRequestPackageInstalls()) return false
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            ready.file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(intent)
            _state.value = UpdateState.Idle
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 拉取最新 Release。返回 null 表示仓库还没有 Release。
     * versionCode 为 null 表示 tag 是旧格式（无 -{code} 后缀），此时用版本名语义化比较。
     */
    private fun fetchLatest(): UpdateInfo? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NibDiary-Android")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val release = json.decodeFromString<GitHubRelease>(response.body?.string().orEmpty())
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: throw IOException("release has no apk asset")
            val (name, code) = parseTag(release.tagName)
            return UpdateInfo(
                versionName = name,
                versionCode = code,
                releaseNotes = release.body.orEmpty(),
                apkUrl = apk.browserDownloadUrl,
                apkSize = apk.size,
            )
        }
    }

    /** tag 约定 v{versionName}-{versionCode}（如 v0.1.1-9）；旧格式 v{versionName} 也能识别。 */
    private fun parseTag(tag: String): Pair<String, Long?> {
        val clean = tag.trim().removePrefix("v").removePrefix("V")
        val dash = clean.lastIndexOf('-')
        return if (dash > 0) {
            val code = clean.substring(dash + 1).toLongOrNull()
            clean.substring(0, dash) to code
        } else {
            clean to null
        }
    }

    private fun downloadApk(info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "NibDiary-Android")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val total = response.body?.contentLength() ?: info.apkSize
            val dir = File(context.cacheDir, APK_DIR).apply { mkdirs() }
            val file = File(dir, "nib-diary-${info.versionName}.apk")
            response.body!!.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            return file
        }
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String = "",
        val body: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        val size: Long = 0,
    )
}
