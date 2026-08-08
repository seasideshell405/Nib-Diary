package com.diary.app.ui.mine

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.ImageFileStore
import com.diary.app.data.ImageWithEntry
import com.diary.app.data.UiPrefsStore
import com.diary.app.ui.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MineScreen(
    onOpenMediaLibrary: () -> Unit,
    viewModel: MineViewModel = viewModel(factory = MineViewModel.Factory),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val recentImages by viewModel.recentImages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uiPrefs = remember(context) { UiPrefsStore(context) }
    val surfaceAlpha by uiPrefs.surfaceAlpha.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var draftNickname by remember { mutableStateOf("") }
    var draftSignature by remember { mutableStateOf("") }
    var draftAvatarUrl by remember { mutableStateOf("") }

    // Avatar bitmap is cached in the ViewModel (activity scope): switching
    // tabs does not re-download or re-decode it.
    val avatarBitmap by viewModel.avatarBitmap.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Full-width profile banner (kept as-is).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = avatarBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "头像",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(text = "📓", fontSize = 30.sp)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = profile.nickname,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = profile.signature,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
            }
            Button(
                onClick = {
                    draftNickname = profile.nickname
                    draftSignature = profile.signature
                    draftAvatarUrl = profile.avatarUrl
                    showEditDialog = true
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "修改",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // White rounded container, matching the calendar card structure.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
                // No shadow: a Material3 square shadow would box the translucent card.
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatCell(value = stats.totalEntries.toString(), label = "篇", modifier = Modifier.weight(1f))
                    DividerDot()
                    StatCell(value = stats.totalWords.toString(), label = "字", modifier = Modifier.weight(1f))
                    DividerDot()
                    StatCell(value = stats.totalImages.toString(), label = "图", modifier = Modifier.weight(1f))
                }
            }

            // Media library entry card: recent images preview, opens the
            // library page.
            val appContext = LocalContext.current.applicationContext
            val fileStore = remember(appContext) { ImageFileStore(appContext) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
                // No shadow: a Material3 square shadow would box the translucent card.
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenMediaLibrary)
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "媒体库",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${stats.totalImages} 张",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    MediaPreviewStrip(
                        images = recentImages,
                        fileStore = fileStore,
                        surfaceAlpha = surfaceAlpha,
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改资料") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draftAvatarUrl,
                        onValueChange = { draftAvatarUrl = it },
                        label = { Text("头像图片 URL（可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftNickname,
                        onValueChange = { draftNickname = it },
                        label = { Text("昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftSignature,
                        onValueChange = { draftSignature = it },
                        label = { Text("签名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveProfile(
                        nickname = draftNickname.ifBlank { "日记本" },
                        signature = draftSignature.ifBlank { "记录每一天" },
                        avatarUrl = draftAvatarUrl.trim(),
                    )
                    showEditDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DividerDot() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
    )
}

/**
 * Thumbnail strip on the media library entry card: up to 4 images in a
 * row, each cropped square. Falls back to a placeholder row when empty.
 * Placeholder backgrounds use the shared surface alpha so no opaque box
 * stands out inside the translucent card.
 */
@Composable
private fun MediaPreviewStrip(
    images: List<ImageWithEntry>,
    fileStore: ImageFileStore,
    surfaceAlpha: Float,
) {
    if (images.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "还没有图片",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEach { image ->
            // Full image decoded by width: the 256px thumbnails look blurry
            // at card size, and sampling by longest side starves tall
            // images. Fixed size keeps one image from growing huge (weight
            // + widthIn would not: weight's min-width wins over the max).
            val full = fileStore.existingFull(image.id)
            var bitmap by remember(image.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
            androidx.compose.runtime.LaunchedEffect(image.id) {
                bitmap = withContext(Dispatchers.IO) {
                    full?.takeIf { it.exists() }?.let { ImageDecoder.decodeByWidth(it, 400) }
                }
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)),
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
