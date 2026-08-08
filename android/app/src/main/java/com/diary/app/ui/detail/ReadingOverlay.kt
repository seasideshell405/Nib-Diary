package com.diary.app.ui.detail

import androidx.activity.compose.BackHandler
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.BodyImages
import com.diary.app.data.ContentBlock
import com.diary.app.data.ImageFileStore
import com.diary.app.ui.DiaryImageSpec
import com.diary.app.ui.FullImageViewer
import com.diary.app.ui.ImageDecoder
import com.diary.app.ui.detail.LocalBlockStyles
import com.diary.app.ui.diary.Mood
import com.diary.app.ui.diary.Weather
import com.diary.app.ui.theme.HandFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reading screen rendered as an in-app overlay (scrim + centered sheet),
 * sliding in from the right.
 */
@Composable
fun ReadingOverlay(
    entryId: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DetailViewModel = viewModel(key = entryId, factory = DetailViewModel.factory(entryId)),
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileStore = remember(context) { ImageFileStore(context) }
    val blockStyles = LocalBlockStyles.current
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var fullImageId by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这篇日记？") },
            text = { Text("删除后无法恢复，并会同步到服务器。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
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

    // The overlay is not a system Dialog, so intercept back explicitly.
    BackHandler(onBack = onDismiss)

    val current = entry
    if (current == null) {
        // First open of the sheet: the entry is still loading from the DB.
        // Show a loading state instead of dismissing, so the sheet never
        // flashes open and closes on the first tap.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // The sheet, centered. The scrim is rendered by the caller.
    Box(modifier = Modifier.fillMaxSize()) {

        fullImageId?.let { id ->
            FullImageViewer(
                imageId = id,
                fileStore = fileStore,
                onDismiss = { fullImageId = null },
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            // No shadow: a Material3 square shadow would box the translucent sheet.
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                // Blue header, centered: year/month + weekday, big day
                // number, time, mood/weather.
                val date = Instant.ofEpochMilli(current.diaryDate)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        )
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = weekdayOf(date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            )
                            Text(
                                text = Instant.ofEpochMilli(current.updatedAt)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalTime()
                                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            )
                        }
                    }
                }

                // Content: title + body on the white part. Horizontal
                // padding is slim (12dp) so images at the fixed 85% page
                // width fit without being clipped by the scroll container.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                ) {
                    // Title in the handwriting font, highlighted like a
                    // marker pen stroke; weather and mood sit at the end of
                    // the row, like the diary cards. Untitled entries fall
                    // back to the default date title, like the cards.
                    val title = current.title?.takeIf { it.isNotBlank() }
                        ?: date.format(DateTimeFormatter.ofPattern("M月d日"))
                    EntryTitle(
                        title = title,
                        weather = current.weather,
                        mood = current.mood,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    DetailBody(
                        body = current.body,
                        fileStore = fileStore,
                        onImageClick = { id -> fullImageId = id },
                    )
                }

                // Blue bottom bar: delete on the left, edit/analyze right.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "删除",
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Row {
                        TextButton(onClick = onEdit) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "编辑",
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun weekdayOf(date: java.time.LocalDate): String =
    when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

/**
 * The entry title row, shared by the reading overlay and the random
 * (回溯) page: handwriting-font title with a marker-pen highlight block,
 * weather and mood at the end of the row. Text color is parameterized for
 * the ink-wash palette of the random page.
 */
@Composable
internal fun EntryTitle(
    title: String,
    weather: String?,
    mood: String?,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = HandFont),
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Weather.fromKey(weather)?.let {
                Icon(
                    imageVector = it.icon,
                    contentDescription = it.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Mood.fromKey(mood)?.let {
                Icon(
                    imageVector = it.icon,
                    contentDescription = it.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// The handwriting font's latin glyphs run wide: latin letters and digits
// tighten by this fixed amount; the CJK spacing comes from the block
// format config. Applied per run of the same kind.
private val LatinLetterSpacing = (-0.4f).sp

/** True for latin letters and digits; CJK, punctuation, spaces stay wide. */
internal fun isLatinOrDigit(ch: Char): Boolean =
    (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9')

/** Splits [text] into runs of latin/digits vs everything else. */
internal fun splitTypeRuns(text: String): List<Pair<String, Boolean>> {
    if (text.isEmpty()) return emptyList()
    val runs = mutableListOf<Pair<String, Boolean>>()
    var start = 0
    var current = isLatinOrDigit(text[0])
    for (i in 1 until text.length) {
        val latin = isLatinOrDigit(text[i])
        if (latin != current) {
            runs.add(text.substring(start, i) to current)
            start = i
            current = latin
        }
    }
    runs.add(text.substring(start) to current)
    return runs
}

/** [text] with per-kind letter spacing: latin tighter, CJK [cjkSpacing]. */
internal fun letterSpaced(text: String, cjkSpacing: TextUnit = 0.sp): AnnotatedString =
    buildAnnotatedString {
        for ((segment, latin) in splitTypeRuns(text)) {
            withStyle(
                SpanStyle(
                    letterSpacing = if (latin) LatinLetterSpacing else cjkSpacing
                )
            ) {
                append(segment)
            }
        }
    }

@Composable
internal fun DetailBody(
    body: String,
    fileStore: ImageFileStore,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = HandFont,
    ),
    imageCornerRadius: Dp = 12.dp,
    onImageClick: (String) -> Unit = {},
) {
    // Block model: paragraphs, headings, images and timestamps, each with
    // its own configurable margin; fixed line height inside paragraphs.
    val blocks = remember(body) { BodyImages.parseBlocks(body) }
    val styles = LocalBlockStyles.current
    // Fixed width spec: images are always 85% of the page width, centered.
    val pageMetrics = LocalContext.current.resources.displayMetrics
    val pageWidthPx = pageMetrics.widthPixels
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val contentWidthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        // Full-width column: a narrower-than-container image must still
        // center horizontally, not hug the left edge.
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Paragraph -> {
                        val style = styles.paragraph.toStyle()
                        val size = (style.fontSize ?: 16f).sp
                        Text(
                            text = letterSpaced(block.text, (style.letterSpacing ?: 0f).sp),
                            modifier = Modifier.padding(
                                top = style.marginTop,
                                bottom = style.marginBottom,
                            ),
                            style = textStyle.copy(
                                fontSize = size,
                                lineHeight = size * style.lineHeightFactor,
                            ),
                        )
                    }
                    is ContentBlock.Heading -> {
                        val style = styles.heading.toStyle()
                        val size = (style.fontSize ?: 20f).sp
                        // A short theme-colored bar on the left marks this
                        // line as a subheading; height follows the font size.
                        val barHeight = with(LocalDensity.current) { (size * 0.9f).toDp() }
                        Row(
                            modifier = Modifier.padding(
                                top = style.marginTop,
                                bottom = style.marginBottom,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(barHeight)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = letterSpaced(block.text, (style.letterSpacing ?: 0f).sp),
                                style = textStyle.copy(
                                    fontSize = size,
                                    lineHeight = size * style.lineHeightFactor,
                                ),
                            )
                        }
                    }
                    is ContentBlock.Timestamp -> {
                        // Small gray chip: clock icon + time, timestamps read
                        // as meta, not as part of the flowing text.
                        val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val style = styles.timestamp.toStyle()
                        Row(
                            modifier = Modifier
                                .padding(
                                    top = style.marginTop,
                                    bottom = style.marginBottom,
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = metaColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = (style.fontSize ?: 13f).sp,
                                    color = metaColor,
                                ),
                            )
                        }
                    }
                    is ContentBlock.Image -> {
                        val style = styles.image.toStyle()
                        val full = fileStore.existingFull(block.id)
                        if (full != null) {
                            // Decode off the UI thread: a 2000px JPEG on the main
                            // thread stalls the sheet's open/close animation.
                            var bitmap by remember(block.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                            LaunchedEffect(block.id) {
                                bitmap = withContext(Dispatchers.IO) {
                                    ImageDecoder.decodeByWidth(full, DiaryImageSpec.maxWidthPx(pageWidthPx))
                                }
                            }
                            val bmp = bitmap
                            if (bmp != null) {
                                // Fixed 85% page width, height follows,
                                // centered.
                                val (w, h) = DiaryImageSpec.fittedSize(
                                    bmp.width, bmp.height, pageWidthPx,
                                )
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(
                                            top = style.marginTop,
                                            bottom = style.marginBottom,
                                        )
                                        .clip(RoundedCornerShape(imageCornerRadius))
                                        .width(with(density) { w.toDp() })
                                        .height(with(density) { h.toDp() })
                                        .clickable { onImageClick(block.id) },
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                // Placeholder sized from the image's real pixel
                                // dimensions (bounds-only decode) so the layout
                                // does not jump when the image arrives.
                                val (phW, phH) = remember(block.id) {
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    if (full != null) BitmapFactory.decodeFile(full.absolutePath, bounds)
                                    val srcW = if (bounds.outWidth > 0) bounds.outWidth else 4
                                    val srcH = if (bounds.outHeight > 0) bounds.outHeight else 3
                                    DiaryImageSpec.fittedSize(srcW, srcH, pageWidthPx)
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(
                                            top = style.marginTop,
                                            bottom = style.marginBottom,
                                        )
                                        .width(with(density) { phW.toDp() })
                                        .height(with(density) { phH.toDp() })
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(12.dp),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
