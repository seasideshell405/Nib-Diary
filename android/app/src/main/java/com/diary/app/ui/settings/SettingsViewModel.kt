package com.diary.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.DiaryApplication
import com.diary.app.data.AppearanceStore
import com.diary.app.data.ConfigStore
import com.diary.app.data.LockStore
import com.diary.app.data.SyncEngine
import com.diary.app.data.SyncStatus
import com.diary.app.data.UiPrefsStore
import com.diary.app.data.CheckResult
import com.diary.app.data.UpdateManager
import com.diary.app.data.UpdateState
import com.diary.app.syncEngine
import com.diary.app.ui.applock.BiometricAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val configStore: ConfigStore,
    private val engine: SyncEngine,
    val lockStore: LockStore,
    private val appearanceStore: AppearanceStore,
    private val uiPrefs: UiPrefsStore,
    val biometricAvailable: Boolean,
    private val updateManager: UpdateManager,
) : ViewModel() {

    private val _serverUrl = MutableStateFlow(configStore.getServerUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _token = MutableStateFlow(configStore.getToken())
    val token: StateFlow<String> = _token.asStateFlow()

    private val _configured = MutableStateFlow(configStore.isConfigured())
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = engine.status

    private val _lockEnabled = MutableStateFlow(lockStore.isEnabled())
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _lockOnBackground = MutableStateFlow(lockStore.lockOnBackground())
    val lockOnBackground: StateFlow<Boolean> = _lockOnBackground.asStateFlow()

    private val _fingerprintEnabled = MutableStateFlow(lockStore.isFingerprintEnabled())
    val fingerprintEnabled: StateFlow<Boolean> = _fingerprintEnabled.asStateFlow()

    private val _hasCustomBackground = MutableStateFlow(appearanceStore.hasCustomBackground())
    val hasCustomBackground: StateFlow<Boolean> = _hasCustomBackground.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(uiPrefs.hapticEnabled)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    val backgroundMask: StateFlow<Boolean> = uiPrefs.backgroundMask
    val maskStrength: StateFlow<Float> = uiPrefs.maskStrength
    val themeFromBackground: StateFlow<Boolean> = uiPrefs.themeFromBackground
    val surfaceAlpha: StateFlow<Float> = uiPrefs.surfaceAlpha

    val updateState: StateFlow<UpdateState> = updateManager.state

    val startupUpdateCheck: StateFlow<Boolean> = uiPrefs.startupUpdateCheck

    fun setStartupUpdateCheck(enabled: Boolean) {
        uiPrefs.startupUpdateCheckEnabled = enabled
    }

    fun setHapticEnabled(enabled: Boolean) {
        uiPrefs.hapticEnabled = enabled
        _hapticEnabled.value = enabled
    }

    fun setBackgroundMask(enabled: Boolean) {
        uiPrefs.backgroundMaskEnabled = enabled
    }

    fun setMaskStrength(value: Float) {
        uiPrefs.maskStrengthValue = value
    }

    fun setThemeFromBackground(enabled: Boolean) {
        uiPrefs.themeFromBackgroundEnabled = enabled
    }

    fun setSurfaceAlpha(value: Float) {
        uiPrefs.surfaceAlphaValue = value
    }

    /** 返回检查结果，由 UI 决定弹提示还是开更新弹窗。 */
    suspend fun checkForUpdate(): CheckResult = updateManager.check()

    fun downloadUpdate() {
        viewModelScope.launch { updateManager.download() }
    }

    /** 调起系统安装器；未授予安装未知应用权限时返回 false。 */
    fun installUpdate(): Boolean = updateManager.install()

    fun onServerUrlChange(value: String) {
        _serverUrl.value = value
    }

    fun onTokenChange(value: String) {
        _token.value = value
    }

    fun save() {
        val url = _serverUrl.value.trim()
        val token = _token.value.trim()
        if (url.isBlank() || token.isBlank()) {
            _message.value = "请填写服务器地址和 Token"
            return
        }
        configStore.save(url, token)
        _configured.value = true
        _message.value = "已保存，正在同步…"
        viewModelScope.launch {
            engine.sync()
            _message.value = "配置已保存"
        }
    }

    fun syncNow() {
        viewModelScope.launch { engine.sync() }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun enableLock(pin: String) {
        lockStore.setPin(pin)
        lockStore.setEnabled(true)
        _lockEnabled.value = true
        _message.value = "锁屏已开启"
    }

    fun setLockOnBackground(enabled: Boolean) {
        lockStore.setLockOnBackground(enabled)
        _lockOnBackground.value = enabled
    }

    fun disableLock() {
        lockStore.setEnabled(false)
        _lockEnabled.value = false
        _message.value = "锁屏已关闭"
    }

    fun changePin(newPin: String) {
        lockStore.setPin(newPin)
        _message.value = "PIN 已更新"
    }

    fun setFingerprintEnabled(enabled: Boolean) {
        lockStore.setFingerprintEnabled(enabled)
        _fingerprintEnabled.value = enabled
    }

    fun saveBackground(uri: Uri) {
        viewModelScope.launch {
            try {
                appearanceStore.setBackground(uri)
                _hasCustomBackground.value = true
                _message.value = "背景图已更新"
            } catch (e: Exception) {
                _message.value = "背景图处理失败，请换一张图片"
            }
        }
    }

    fun resetBackground() {
        appearanceStore.clearBackground()
        _hasCustomBackground.value = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiaryApplication
                SettingsViewModel(
                    configStore = app.container.configStore,
                    engine = app.syncEngine,
                    lockStore = app.container.lockStore,
                    appearanceStore = app.container.appearanceStore,
                    uiPrefs = app.container.uiPrefs,
                    biometricAvailable = BiometricAuth.isAvailable(app),
                    updateManager = app.container.updateManager,
                )
            }
        }
    }
}
