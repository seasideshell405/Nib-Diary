package com.diary.app.ui.media

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.ImageFileStore
import com.diary.app.ui.ImageDecoder
import com.diary.app.ui.theme.LocalTopBarTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MediaLibraryScreen(
    onOpenEntry: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MediaLibraryViewModel = viewModel(factory = MediaLibraryViewModel.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fileStore = remember(context) { ImageFileStore(context) }
    val scope = rememberCoroutineScope()

    // Full-screen preview over the whole library, paging through every
    // image in order.
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // Top bar: back + title, colored against the background brightness
        // (the page itself has no backing surface).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = LocalTopBarTextColor.current,
                )
            }
            Text(
                text = "媒体库",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LocalTopBarTextColor.current,
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                text = "共 ${items.size} 张",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalTopBarTextColor.current.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "还没有图片",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Waterfall: two columns, each card sized by its image's aspect
            // ratio (bounds-only decode, no pixels).
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                items(
                    count = items.size,
                    key = { items[it].id },
                ) { index ->
                    val item = items[index]
                    MediaCard(
                        item = item,
                        fileStore = fileStore,
                        onClick = { previewIndex = index },
                    )
                }
            }
        }
    }

    previewIndex?.let { start ->
        MediaPreview(
            items = items,
            initialIndex = start,
            fileStore = fileStore,
            onOpenEntry = { entryId ->
                previewIndex = null
                onOpenEntry(entryId)
            },
            onExport = { id ->
                scope.launch(Dispatchers.IO) {
                    val ok = exportWebP(context, id, fileStore)
                    withContext(Dispatchers.Main) {
                        exportMessage = if (ok) "已保存到相册" else "导出失败"
                    }
                }
            },
            onDismiss = { previewIndex = null },
        )
    }

    if (exportMessage != null) {
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("导出") },
            text = { Text(exportMessage!!) },
            confirmButton = { TextButton(onClick = { exportMessage = null }) { Text("好") } },
        )
    }
}

/** Waterfall card: thumbnail with its entry's title/date and file size. */
@Composable
private fun MediaCard(
    item: MediaItem,
    fileStore: ImageFileStore,
    onClick: () -> Unit,
) {
    // Full image decoded per column: the 256px thumbnails used by list
    // cards are too small for the wide waterfall cells and come out blurry.
    val full = fileStore.existingFull(item.id)
    val date = Instant.ofEpochMilli(item.diaryDate).atZone(ZoneId.systemDefault()).toLocalDate()
    val caption = item.title?.takeIf { it.isNotBlank() }
        ?: date.format(DateTimeFormatter.ofPattern("M月d日"))

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        MediaThumb(file = full, modifier = Modifier.fillMaxWidth())
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatSize(item.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renders a local image file scaled to the column width. The aspect ratio
 * comes from a bounds-only decode first, so the card keeps its final size
 * while the real bitmap loads.
 */
@Composable
private fun MediaThumb(
    file: File?,
    modifier: Modifier = Modifier,
) {
    val ratio by produceState(1f, file) {
        value = withContext(Dispatchers.IO) {
            val f = file ?: return@withContext 1f
            if (!f.exists()) return@withContext 1f
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                bounds.outWidth.toFloat() / bounds.outHeight
            } else 1f
        }
    }
    val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            // Decode to roughly twice the column width so Crop has real
            // pixels to work with; bounds-only keeps tall images cheap.
            file?.takeIf { it.exists() }?.let { ImageDecoder.decodeByWidth(it, 800) }
        }
    }
    Box(
        modifier = modifier.aspectRatio(ratio).background(MaterialTheme.colorScheme.surfaceVariant),
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

/**
 * Full-screen pager over the whole library: swipe between images, counter
 * on top, "查看日记" and "导出" actions at the bottom. Tap the image to
 * dismiss.
 */
@Composable
private fun MediaPreview(
    items: List<MediaItem>,
    initialIndex: Int,
    fileStore: ImageFileStore,
    onOpenEntry: (String) -> Unit,
    onExport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, items.size - 1)) {
        items.size
    }
    val metrics = LocalContext.current.resources.displayMetrics
    val maxDim = maxOf(metrics.widthPixels, metrics.heightPixels)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val id = items[page].id
                var bitmap by remember(id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(id) {
                    bitmap = withContext(Dispatchers.IO) {
                        fileStore.existingFull(id)?.let { ImageDecoder.decodeSampled(it, maxDim) }
                    }
                }
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onDismiss),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            // Counter.
            Text(
                text = "${pagerState.currentPage + 1} / ${items.size}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(vertical = 12.dp),
            )

            // Bottom action bar: the current image's entry + export.
            val current = items[pagerState.currentPage]
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { onOpenEntry(current.entryId) }) {
                    Text(
                        "查看日记",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
                TextButton(onClick = { onExport(current.id) }) {
                    Icon(
                        Icons.Filled.IosShare,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("导出", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Copies the stored WebP to the system gallery as-is (no re-encode). */
private suspend fun exportWebP(context: Context, id: String, fileStore: ImageFileStore): Boolean {
    val file = fileStore.existingFull(id) ?: return false
    return try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "diary_${id.take(8)}.webp")
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Diary")
            }
        }
        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
        }
        uri != null
    } catch (e: Exception) {
        false
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1 shl 20 -> "%.1fMB".format(bytes.toDouble() / (1 shl 20))
    bytes >= 1 shl 10 -> "%.0fKB".format(bytes.toDouble() / (1 shl 10))
    else -> "${bytes}B"
}
