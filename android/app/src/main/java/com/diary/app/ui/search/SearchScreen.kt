package com.diary.app.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.DiaryEntry
import com.diary.app.data.EntryDao
import com.diary.app.data.UiPrefsStore
import com.diary.app.ui.diary.Mood
import com.diary.app.ui.theme.LocalTopBarTextColor
import com.diary.app.ui.diary.Weather
import com.diary.app.ui.theme.HandFont
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SearchScreen(
    dao: EntryDao,
    onOpenEntry: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(dao),
    )
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uiPrefs = remember(context) { UiPrefsStore(context) }
    val surfaceAlpha by uiPrefs.surfaceAlpha.collectAsStateWithLifecycle()

    // Open the page with the keyboard ready to type.
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // Back + large rounded search field. The back arrow picks its
        // color against the background brightness.
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
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("搜索日记…", style = MaterialTheme.typography.bodyLarge) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = surfaceAlpha),
                    unfocusedContainerColor = Color.White.copy(alpha = surfaceAlpha),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .focusRequester(focusRequester),
            )
        }

        // Animate only between the prompt / empty / results states; result
        // list updates while typing swap without re-animating.
        val state: SearchState = when {
            query.isBlank() -> SearchState.Prompt
            results.isEmpty() -> SearchState.Empty
            else -> SearchState.Results(results)
        }
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = {
                // Slide in/out (a third of the container height) with a
                // fade, so entering/leaving the results state reads clearly.
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 })
                    .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 3 })
            },
            label = "search-state",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            when (s) {
                is SearchState.Prompt -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "输入关键词\n搜索标题或正文",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalTopBarTextColor.current.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                }
                is SearchState.Empty -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "没有找到匹配的日记",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalTopBarTextColor.current.copy(alpha = 0.9f),
                    )
                }
                is SearchState.Results -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.entries, key = { it.id }) { entry ->
                        SearchResultCard(
                            entry = entry,
                            query = query,
                            onClick = { onOpenEntry(entry.id) },
                            modifier = Modifier.padding(horizontal = 0.dp),
                        )
                    }
                }
            }
        }
    }
}

private sealed interface SearchState {
    data object Prompt : SearchState
    data object Empty : SearchState
    data class Results(val entries: List<DiaryEntry>) : SearchState
}

/**
 * Result card, focused on the match: a compact meta line on top (date,
 * weekday, time, weather, mood), then the highlighted title with a hit
 * count badge, then a multi-line highlighted snippet around the match.
 * No big date column — the content is the point here.
 */
@Composable
private fun SearchResultCard(
    entry: DiaryEntry,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = Instant.ofEpochMilli(entry.diaryDate).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = Instant.ofEpochMilli(entry.updatedAt).atZone(ZoneId.systemDefault())
        .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    val title = entry.title?.takeIf { it.isNotBlank() }
        ?: date.format(DateTimeFormatter.ofPattern("M月d日"))
    val hits = countHits(searchVisibleText(entry), query)
    val context = LocalContext.current
    val uiPrefs = remember(context) { UiPrefsStore(context) }
    val surfaceAlpha by uiPrefs.surfaceAlpha.collectAsStateWithLifecycle()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha),
        ),
        // No elevation: the Material3 square shadow would show as a box
        // around the translucent rounded card.
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Meta line: full date, weekday, time, weather, mood.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) + " " +
                        weekdayOf(date) + " " + time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Weather.fromKey(entry.weather)?.let {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = it.label,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Mood.fromKey(entry.mood)?.let {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = it.label,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // Title (highlighted) with the hit-count badge on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = highlight(title, query),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = HandFont),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (hits > 0) {
                    Text(
                        text = "$hits 处",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            // Snippet around the first match, not the body head: the
            // match may sit deep in the text. Newlines are flattened by
            // snippetAroundMatch — the card never shows line breaks.
            Text(
                text = highlight(snippetAroundMatch(bodySearchText(entry.body), query), query),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = HandFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Counts every occurrence of [query] (case-insensitive) in [text]. */
private fun countHits(text: String, query: String): Int {
    if (query.isBlank()) return 0
    var count = 0
    var start = 0
    while (true) {
        val index = text.indexOf(query, startIndex = start, ignoreCase = true)
        if (index < 0) break
        count++
        start = index + query.length
    }
    return count
}

/** A short window around the first [query] match, with ellipses. */
internal fun snippetAroundMatch(text: String, query: String): String {
    if (query.isBlank()) return text
    val idx = text.indexOf(query, ignoreCase = true)
    if (idx < 0) return text.take(MAX_PREVIEW_LENGTH)
    val start = (idx - 12).coerceAtLeast(0)
    val end = (idx + query.length + 20).coerceAtMost(text.length)
    // Newlines inside the window are flattened to spaces: the card shows at
    // most maxLines lines, so a blank-line-heavy body would otherwise push
    // the highlighted match below the ellipsis and out of sight.
    val snippet = text.substring(start, end).replace(Regex("\r?\n+"), " ")
    return buildString {
        if (start > 0) append("…")
        append(snippet)
        if (end < text.length) append("…")
    }
}

/** Fallback preview length when the query matches the title, not the body. */
private const val MAX_PREVIEW_LENGTH = 60

/** Marks every occurrence of [query] (case-insensitive) with a highlight. */
@Composable
private fun highlight(text: String, query: String): AnnotatedString {
    if (query.isBlank() || query.length > text.length) return AnnotatedString(text)
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val highlightColor = MaterialTheme.colorScheme.onPrimaryContainer
    return buildAnnotatedString {
        var start = 0
        while (true) {
            val index = text.indexOf(query, startIndex = start, ignoreCase = true)
            if (index < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, index))
            withStyle(
                SpanStyle(
                    background = backgroundColor,
                    color = highlightColor,
                    fontWeight = FontWeight.Bold,
                )
            ) {
                append(text.substring(index, index + query.length))
            }
            start = index + query.length
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
