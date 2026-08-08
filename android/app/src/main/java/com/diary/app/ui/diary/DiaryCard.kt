package com.diary.app.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diary.app.data.DiaryEntry
import com.diary.app.data.UiPrefsStore
import com.diary.app.ui.pressFeedbackModifier
import com.diary.app.ui.theme.HandFont
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shared entry card used by the browse list and the calendar day card:
 * big blue date + weekday on the left, time/weather/mood, title and
 * one-line text summary.
 */
@Composable
fun DiaryCard(
    entry: DiaryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = Instant.ofEpochMilli(entry.diaryDate).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = Instant.ofEpochMilli(entry.updatedAt).atZone(ZoneId.systemDefault())
        .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    // Shared card opacity from settings (unified with top bars etc).
    val context = LocalContext.current
    val uiPrefs = remember(context) { UiPrefsStore(context) }
    val surfaceAlpha by uiPrefs.surfaceAlpha.collectAsStateWithLifecycle()

    Card(
        modifier = modifier
            .pressFeedbackModifier(
                onClick = onClick,
                pressedScale = 0.97f,
            )
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
        ),
        // No elevation: Material3 draws a SQUARE shadow (clip=false) under
        // the rounded card, which shows as a mismatched box on translucent
        // cards over the background image.
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(64.dp),
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = weekdayOf(date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = HandFont),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Weather.fromKey(entry.weather)?.let {
                            Icon(
                                imageVector = it.icon,
                                contentDescription = it.label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Mood.fromKey(entry.mood)?.let {
                            Icon(
                                imageVector = it.icon,
                                contentDescription = it.label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Text(
                    text = entry.title?.takeIf { it.isNotBlank() }
                        ?: date.format(DateTimeFormatter.ofPattern("M月d日")),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = HandFont),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = strippedSummary(entry.body),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = HandFont),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
