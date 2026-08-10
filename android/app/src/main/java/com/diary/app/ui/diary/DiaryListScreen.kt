package com.diary.app.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.DiaryEntry
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun DiaryListScreen(
    onOpenEntry: (String) -> Unit,
    onSearch: () -> Unit,
    onRandom: () -> Unit,
    viewModel: DiaryListViewModel = viewModel(factory = DiaryListViewModel.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.hiddenIds.collectAsStateWithLifecycle()
    // Cards deleted just now are hidden immediately (hide), even if the
    // flow emission lags behind the DB write.
    val visibleEntries = remember(entries, hiddenIds) {
        if (hiddenIds.isEmpty()) entries
        else entries.filterNot { it.id in hiddenIds }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (visibleEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("还没有日记，点右下角写第一篇", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // Group entries by month, newest first: 2026年8月 -> 2026年7月 -> ...
                val grouped = remember(visibleEntries) {
                    visibleEntries.groupBy { entry ->
                        val date = Instant.ofEpochMilli(entry.diaryDate)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        YearMonth.of(date.year, date.monthValue)
                    }.toSortedMap(compareByDescending { it })
                }
                val months = remember(grouped) {
                    grouped.map { (yearMonth, list) ->
                        MonthGroup(yearMonth = yearMonth, entries = list)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = viewModel.listState,
                ) {
                    months.forEachIndexed { index, month ->
                        item(key = "month_${month.yearMonth}") {
                            MonthTitleRow(
                                yearMonth = month.yearMonth,
                                actions = if (index == 0) {
                                    {
                                        IconButton(
                                            onClick = onSearch,
                                            modifier = Modifier.size(52.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Search,
                                                contentDescription = "搜索",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = onRandom,
                                            modifier = Modifier.size(52.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Casino,
                                                contentDescription = "回溯",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                        items(month.entries, key = { it.id }) { entry ->
                            DiaryCard(
                                entry = entry,
                                onClick = { onOpenEntry(entry.id) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class MonthGroup(
    val yearMonth: YearMonth,
    val entries: List<DiaryEntry>,
)

@Composable
private fun MonthTitleRow(
    yearMonth: YearMonth,
    actions: (@Composable RowScope.() -> Unit)?,
) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Month on top, year below — like the calendar header.
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = chineseMonth(yearMonth.monthValue),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "${yearMonth.year}年",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            actions?.invoke(this)
        }
}

fun chineseMonth(month: Int): String =
    listOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")[month - 1]

