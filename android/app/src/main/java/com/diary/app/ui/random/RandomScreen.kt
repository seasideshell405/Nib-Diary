package com.diary.app.ui.random

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.EntryDao
import com.diary.app.data.ImageFileStore
import com.diary.app.ui.FullImageViewer
import com.diary.app.ui.detail.DetailBody
import com.diary.app.ui.detail.EntryExporter
import com.diary.app.ui.detail.EntryTitle
import com.diary.app.ui.detail.LocalBlockStyles
import com.diary.app.ui.theme.HandFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Ink-wash palette: rice-paper background, black ink, gray ink.
private val PaperWhite = Color(0xFFF5F5F5)
private val InkBlack = Color(0xFF1F1F1F)
private val InkGray = Color(0xFF6E6E6E)

/**
 * 回溯: revisit a past diary entry on a rice-paper background, handwritten
 * in ink. Top bar with back arrow, greeting card, the entry card (date/time
 * period + body), and stacked actions (write new / save to gallery).
 */
@Composable
fun RandomScreen(
    dao: EntryDao,
    onWriteNew: () -> Unit,
    onClose: () -> Unit,
    viewModel: RandomViewModel = viewModel(factory = RandomViewModel.factory(dao)),
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fileStore = remember(context) { ImageFileStore(context) }
    val blockStyles = LocalBlockStyles.current
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    var fullImageId by remember { mutableStateOf<String?>(null) }
    val nickname = remember(context) {
        context.getSharedPreferences("mine", Context.MODE_PRIVATE)
            .getString("nickname", "你") ?: "你"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PaperWhite,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            fullImageId?.let { id ->
                FullImageViewer(
                    imageId = id,
                    fileStore = fileStore,
                    onDismiss = { fullImageId = null },
                )
            }

            // Top bar: back arrow, centered "回溯" title.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = InkBlack,
                    )
                }
                Text(
                    text = "回溯",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = InkBlack,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            val current = entry
            if (current == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有日记\n写下第一篇后就能开始回溯",
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkGray,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Greeting card with an ink line on the left.
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White,
                        ),
                        // No shadow: a Material3 square shadow would box the
                        // translucent card.
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(IntrinsicSize.Min),
                        ) {
                            // Plain ink-colored block on the left.
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .fillMaxHeight()
                                    .background(InkBlack, RoundedCornerShape(3.dp)),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "$nickname，今天过得怎么样？",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = HandFont,
                                    color = InkBlack,
                                )
                                Text(
                                    text = "还记得吗，${agoText(current.diaryDate)} 你这样写道……",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = HandFont,
                                    color = InkGray,
                                )
                            }
                        }
                    }

                    // The entry card: meta row + handwritten body.
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White,
                        ),
                        // No shadow: a Material3 square shadow would box the
                        // translucent card.
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            val date = Instant.ofEpochMilli(current.diaryDate)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")) + "  " + weekdayOf(date),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = HandFont,
                                    color = InkBlack,
                                )
                                Text(
                                    text = periodOfDay(current.updatedAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = HandFont,
                                    color = InkBlack,
                                )
                            }
                            // Title like the reading view: handwriting
                            // font, marker-pen highlight, weather/mood at the
                            // row end; untitled entries show the date.
                            val title = current.title?.takeIf { it.isNotBlank() }
                                ?: date.format(DateTimeFormatter.ofPattern("M月d日"))
                            EntryTitle(
                                title = title,
                                weather = current.weather,
                                mood = current.mood,
                                textColor = InkBlack,
                            )
                            DetailBody(
                                body = current.body,
                                fileStore = fileStore,
                                imageCornerRadius = 12.dp,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = HandFont,
                                    color = InkBlack,
                                ),
                                onImageClick = { id -> fullImageId = id },
                            )
                        }
                    }
                }
            }

            // Stacked actions at the bottom.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onWriteNew,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkBlack,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                ) {
                    Text(
                        "写新日记",
                        fontWeight = FontWeight.Bold,
                        fontFamily = HandFont,
                    )
                }
            }
        }
    }
}

private fun weekdayOf(date: LocalDate): String =
    when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

private fun periodOfDay(millis: Long): String {
    val hour = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).hour
    return when (hour) {
        in 0..4 -> "深夜"
        in 5..7 -> "凌晨"
        in 8..10 -> "早晨"
        in 11..12 -> "上午"
        in 13..16 -> "下午"
        in 17..18 -> "傍晚"
        else -> "晚上"
    }
}

private fun agoText(diaryDateMillis: Long): String {
    val date = Instant.ofEpochMilli(diaryDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, LocalDate.now())
    return when {
        days <= 0 -> "今天"
        days < 30 -> "$days 天前"
        days < 365 -> "${days / 30} 个月${days % 30}天前"
        else -> "${days / 365} 年前"
    }
}
