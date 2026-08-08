package com.diary.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.DiaryEntry
import com.diary.app.data.UiPrefsStore
import com.diary.app.ui.diary.DiaryCard
import com.diary.app.ui.diary.chineseMonth
import com.diary.app.ui.theme.LocalTopBarTextColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(
    onOpenEntry: (String) -> Unit,
    viewModel: CalendarViewModel,
) {
    val dates by viewModel.dates.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectedEntry by viewModel.selectedEntry.collectAsStateWithLifecycle()
    val markedDates = remember(dates) {
        dates.map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }.toSet()
    }
    val context = LocalContext.current
    val uiPrefs = remember(context) { UiPrefsStore(context) }
    val surfaceAlpha by uiPrefs.surfaceAlpha.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Month switcher.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.onMonthChange(month.minusMonths(1)) }) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "上个月",
                    tint = Color.White,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = chineseMonth(month.monthValue),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "${month.year}年",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            IconButton(onClick = { viewModel.onMonthChange(month.plusMonths(1)) }) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "下个月",
                    tint = Color.White,
                )
            }
        }

        // Calendar on a white rounded card: the grid reads cleanly off
        // the sky background.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
            // No shadow: a Material3 square shadow would box the translucent card.
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                MonthGrid(
                    month = month,
                    markedDates = markedDates,
                    selectedDate = selectedDate,
                    onDaySelected = viewModel::onDateSelect,
                )
            }
        }

        // Selected day's diary card, hugging the month grid.
        DayCard(
            date = selectedDate,
            entry = selectedEntry,
            onOpenEntry = { id -> onOpenEntry(id) },
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    markedDates: Set<LocalDate>,
    selectedDate: LocalDate,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstDay = month.atDay(1)
    val offset = (firstDay.dayOfWeek.value + 6) % 7
    val daysInMonth = month.lengthOfMonth()
    val cells: List<Int?> = buildList {
        repeat(offset) { add(null) }
        for (d in 1..daysInMonth) add(d)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = modifier,
    ) {
        items(cells) { day ->
            if (day != null) {
                val date = month.atDay(day)
                val marked = date in markedDates
                val selected = date == selectedDate
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable { onDaySelected(date) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (marked || selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (marked) {
                        // Dot pinned to the bottom of the circle, out of the
                        // text flow so the number stays perfectly centered.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 5.dp)
                                .size(4.dp)
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.aspectRatio(1f))
            }
        }
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    entry: DiaryEntry?,
    onOpenEntry: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 20.dp, bottom = 12.dp),
    ) {
        if (entry == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "这一天还没有日记\n点右下角的 + 记录一下吧",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalTopBarTextColor.current,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            DiaryCard(
                entry = entry,
                onClick = { onOpenEntry(entry.id) },
            )
        }
    }
}
