package com.diary.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.SyncFailure
import com.diary.app.data.SyncStatus
import com.diary.app.ui.theme.LocalTopBarTextColor

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onOpenBlockStyles: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()
    val configured by viewModel.configured.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val lockEnabled by viewModel.lockEnabled.collectAsStateWithLifecycle()
    val lockOnBackground by viewModel.lockOnBackground.collectAsStateWithLifecycle()
    val fingerprintEnabled by viewModel.fingerprintEnabled.collectAsStateWithLifecycle()
    val hasCustomBackground by viewModel.hasCustomBackground.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    val backgroundMask by viewModel.backgroundMask.collectAsStateWithLifecycle()
    val maskStrength by viewModel.maskStrength.collectAsStateWithLifecycle()
    val themeFromBackground by viewModel.themeFromBackground.collectAsStateWithLifecycle()
    val surfaceAlpha by viewModel.surfaceAlpha.collectAsStateWithLifecycle()

    var showSetPin by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var showDisablePin by remember { mutableStateOf(false) }

    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::saveBackground) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val topBarColor = LocalTopBarTextColor.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = topBarColor,
                )
            }
            Text(
                "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = topBarColor,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
        ),
        // No elevation: a Material3 square shadow would box the translucent card.
    ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("服务器", style = MaterialTheme.typography.titleMedium)
                Text(
                    "填写你的服务器地址和 API Token。首次连接会从服务器全量拉取日记完成恢复（换机时在新手机上执行一次即可）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = viewModel::onServerUrlChange,
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://diary.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = viewModel::onTokenChange,
                    label = { Text("API Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (configured) "保存并同步" else "连接服务器")
                }
                Text("同步状态", style = MaterialTheme.typography.titleMedium)
                when (val status = syncStatus) {
                    is SyncStatus.Idle -> Text("空闲")
                    is SyncStatus.Syncing -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                        Text("同步中…")
                    }
                    is SyncStatus.Success -> Text("最近同步成功：推送 ${status.pushed} 条，拉取 ${status.pulled} 条")
                    is SyncStatus.Failed -> Text(
                        "同步失败：${describeFailure(status.reason)}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = viewModel::syncNow, enabled = configured) {
                    Text("立即同步")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
        ),
        // No elevation: a Material3 square shadow would box the translucent card.
    ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("外观", style = MaterialTheme.typography.titleMedium)
                SettingSwitchRow(
                    title = "震动反馈",
                    subtitle = "点击按键、日记卡片和新建按钮时震动",
                    checked = hapticEnabled,
                    onCheckedChange = viewModel::setHapticEnabled,
                )
                SettingSwitchRow(
                    title = "背景遮罩",
                    subtitle = "在背景图上覆盖半透明层，让文字内容更清晰",
                    checked = backgroundMask,
                    onCheckedChange = viewModel::setBackgroundMask,
                )
                if (backgroundMask) {
                    PercentSliderSetting(
                        title = "遮罩强度",
                        value = maskStrength,
                        min = 0.1f,
                        max = 0.8f,
                        onValueChange = viewModel::setMaskStrength,
                    )
                }
                SettingSwitchRow(
                    title = "主题色跟随背景",
                    subtitle = "开启后根据背景图自动调整主题色",
                    checked = themeFromBackground,
                    onCheckedChange = viewModel::setThemeFromBackground,
                )
                PercentSliderSetting(
                    title = "卡片与栏透明度",
                    value = surfaceAlpha,
                    min = 0.3f,
                    max = 1f,
                    onValueChange = viewModel::setSurfaceAlpha,
                    subtitle = "统一控制顶栏、搜索框和日记/统计/媒体库卡片的透明度",
                )
                SettingActionRow(
                    title = "背景图",
                    subtitle = if (hasCustomBackground) "使用自定义图片" else "使用默认图片",
                    onPick = {
                        pickBackground.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onReset = if (hasCustomBackground) viewModel::resetBackground else null,
                )
                SettingActionRow(
                    title = "块格式",
                    subtitle = "段落、小标题、图片的排版参数",
                    onPick = onOpenBlockStyles,
                    onReset = null,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
        ),
        // No elevation: a Material3 square shadow would box the translucent card.
    ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("锁屏保护", style = MaterialTheme.typography.titleMedium)
                SettingSwitchRow(
                    title = "锁屏保护",
                    subtitle = "开启后，打开应用或从后台返回时需要输入 PIN 或指纹解锁",
                    checked = lockEnabled,
                    onCheckedChange = { on ->
                        if (on) showSetPin = true else showDisablePin = true
                    },
                )
                if (lockEnabled) {
                    SettingSwitchRow(
                        title = "进入后台立即上锁",
                        subtitle = "开启后切到后台就锁定；关闭后仅重新启动软件时锁定",
                        checked = lockOnBackground,
                        onCheckedChange = viewModel::setLockOnBackground,
                    )
                    if (viewModel.biometricAvailable) {
                        SettingSwitchRow(
                            title = "指纹解锁",
                            subtitle = "使用系统指纹快速解锁（需已录入指纹）",
                            checked = fingerprintEnabled,
                            onCheckedChange = viewModel::setFingerprintEnabled,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showChangePin = true }) {
                            Text("修改 PIN")
                        }
                    }
                }
            }
        }
    }

    if (showSetPin) {
        PinFlowDialog(
            lockStore = viewModel.lockStore,
            title = "开启锁屏",
            verifyCurrent = false,
            onDone = { pin ->
                if (pin != null) viewModel.enableLock(pin)
                showSetPin = false
            },
            onDismiss = { showSetPin = false },
        )
    }
    if (showChangePin) {
        PinFlowDialog(
            lockStore = viewModel.lockStore,
            title = "修改 PIN",
            verifyCurrent = true,
            onDone = { pin ->
                if (pin != null) viewModel.changePin(pin)
                showChangePin = false
            },
            onDismiss = { showChangePin = false },
        )
    }
    if (showDisablePin) {
        PinFlowDialog(
            lockStore = viewModel.lockStore,
            title = "关闭锁屏",
            verifyCurrent = true,
            requiresNewPin = false,
            onDone = {
                viewModel.disableLock()
                showDisablePin = false
            },
            onDismiss = { showDisablePin = false },
        )
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { Text("好") }
            },
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    onPick: () -> Unit,
    onReset: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onReset != null) {
            TextButton(onClick = onReset) { Text("恢复默认") }
        }
        TextButton(onClick = onPick) { Text("更换") }
    }
}

/**
 * A labeled percent slider (value 0..1) whose percent readout opens a
 * numeric input dialog when tapped.
 */
@Composable
private fun PercentSliderSetting(
    title: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
    subtitle: String? = null,
) {
    var editing by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { editing = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = min..max)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (editing) {
        var input by remember { mutableStateOf((value * 100).toInt().toString()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("百分比（${(min * 100).toInt()}–${(max * 100).toInt()}）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = input.toIntOrNull() ?: 0
                    onValueChange((v / 100f).coerceIn(min, max))
                    editing = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("取消") }
            },
        )
    }
}

private fun describeFailure(failure: SyncFailure): String = when (failure) {
    is SyncFailure.NotConfigured -> "未配置服务器"
    is SyncFailure.Unauthorized -> "Token 无效"
    is SyncFailure.Network -> "网络错误"
}

