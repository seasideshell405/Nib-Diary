package com.diary.app.ui.diary

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diary.app.data.BodyImages
import com.diary.app.data.ImageFileStore
import com.diary.app.ui.GalleryViewer
import com.diary.app.ui.LocalImage
import com.diary.app.ui.detail.LocalBlockStyles
import java.io.File
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    entryId: String?,
    initialDate: String?,
    onClose: () -> Unit,
) {
    val date = initialDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    val viewModel: EditorViewModel = viewModel(
        key = entryId ?: "new_$initialDate",
        factory = EditorViewModel.factory(entryId, date),
    )
    val title by viewModel.title.collectAsState()
    val body by viewModel.body.collectAsState()
    val mood by viewModel.mood.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val diaryDate by viewModel.diaryDate.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val dateError by viewModel.dateError.collectAsState()
    val context = LocalContext.current
    val fileStore = remember(context) { ImageFileStore(context) }
    val dirty by viewModel.dirty.collectAsState()
    var showMoodDialog by remember { mutableStateOf(false) }
    var showWeatherDialog by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var showSubheadingDialog by remember { mutableStateOf(false) }
    var editorView by remember { mutableStateOf<android.widget.EditText?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var gallery by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    // Hide the keyboard whenever the editor closes, no matter the path
    // (cancel, save, system back). The AndroidView EditText keeps focus
    // during the nav exit animation, so hide directly via the IME.
    val keyboard = LocalSoftwareKeyboardController.current
    val close = {
        keyboard?.hide()
        editorView?.let { view ->
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
        onClose()
    }
    // Unsaved changes warn before leaving; confirm first.
    val requestClose = {
        if (dirty) showDiscardDialog = true else close()
    }
    BackHandler(onBack = requestClose)

    // Insert position for the picked image: captured when the picker is
    // launched. Reading the caret AFTER the system picker returns is
    // unreliable (the EditText caret can be reset to 0 / select-all while
    // the activity is covered), which would insert the image before the
    // text.
    var pendingImageInsertAt by remember { mutableStateOf<Int?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val copy = File(context.cacheDir, "picked_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(it)?.use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.importImage(copy, pendingImageInsertAt)
        }
        pendingImageInsertAt = null
    }

    LaunchedEffect(saved) {
        if (saved) close()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .imePadding(),
        ) {
            // Top bar: cancel | title field | blue pill save. Slim side
            // margins so the two pills sit close to the screen edges.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cancel styled like the save pill: both are blue capsules,
                // slimmed down vertically.
                Button(
                    onClick = requestClose,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .padding(vertical = 4.dp),
                ) {
                    Text("取消", fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = viewModel::onTitleChange,
                    placeholder = {
                        Text(
                            "标题",
                            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                        )
                    },
                    textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Button(
                    onClick = viewModel::save,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .padding(vertical = 4.dp),
                ) {
                    Text("保存", fontWeight = FontWeight.SemiBold)
                }
            }

            // Body: full-bleed editor, no card treatment. Images are NOT
            // rendered inline: the markers show as text and a thumbnail
            // strip below lists what the entry contains.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                RichBodyEditor(
                    body = body,
                    onBodyChange = viewModel::onBodyChange,
                    onEditText = { editorView = it },
                    fontSizeSp = LocalBlockStyles.current.paragraph.fontSize?.toFloat() ?: 16f,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Thumbnail strip: images in the body, in order. Tapping one
            // opens the gallery (swipe + counter).
            val imageIds = remember(body) { BodyImages.extractIds(body) }
            if (imageIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    imageIds.forEachIndexed { index, id ->
                        val thumb = fileStore.existingThumb(id) ?: fileStore.existingFull(id)
                        if (thumb != null) {
                            LocalImage(
                                file = thumb,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { gallery = imageIds to index },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }

            // Bottom toolbar: five tools.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ToolbarIcon(Icons.Filled.AddPhotoAlternate, "图片", enabled = !importing) {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                // Insert markers at the caret, not at the end of the body.
                ToolbarIcon(Icons.Filled.Schedule, "时间戳", true) {
                    val marker = timestampMarkerText()
                    editorView?.insertBlockMarker(marker) ?: viewModel.insertTimestamp()
                }
                ToolbarIcon(Icons.Filled.Title, "小标题", true) { showSubheadingDialog = true }
                ToolbarIcon(Icons.Filled.SentimentSatisfied, "心情", true) { showMoodDialog = true }
                ToolbarIcon(Icons.Filled.WbSunny, "天气", true) { showWeatherDialog = true }
                ToolbarIcon(Icons.Filled.DateRange, "日期", true) { showDateDialog = true }
            }
        }
    }

    if (showSubheadingDialog) {
        var subheading by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubheadingDialog = false },
            title = { Text("小标题") },
            text = {
                OutlinedTextField(
                    value = subheading,
                    onValueChange = { subheading = it },
                    placeholder = { Text("如：今天的工作") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val marker = subheadingMarkerText(subheading)
                        if (marker != null) {
                            editorView?.insertBlockMarker(marker)
                                ?: viewModel.insertSubheading(subheading)
                        }
                        showSubheadingDialog = false
                    },
                ) {
                    Text("插入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubheadingDialog = false }) { Text("取消") }
            },
        )
    }

    if (showMoodDialog) {
        AlertDialog(
            onDismissRequest = { showMoodDialog = false },
            title = { Text("心情") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 320.dp),
                ) {
                    IconPickerRow(
                        options = Mood.entries,
                        selected = mood,
                        onSelect = { viewModel.onMoodChange(it) },
                        iconOf = { it.icon },
                        labelOf = { it.label },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showMoodDialog = false }) { Text("完成") } },
            dismissButton = {
                TextButton(onClick = { viewModel.onMoodChange(null); showMoodDialog = false }) {
                    Text("清除")
                }
            },
        )
    }

    if (showWeatherDialog) {
        AlertDialog(
            onDismissRequest = { showWeatherDialog = false },
            title = { Text("天气") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 320.dp),
                ) {
                    IconPickerRow(
                        options = Weather.entries,
                        selected = weather,
                        onSelect = { viewModel.onWeatherChange(it) },
                        iconOf = { it.icon },
                        labelOf = { it.label },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showWeatherDialog = false }) { Text("完成") } },
            dismissButton = {
                TextButton(onClick = { viewModel.onWeatherChange(null); showWeatherDialog = false }) {
                    Text("清除")
                }
            },
        )
    }

    if (showDateDialog) {
        // DatePicker millis are UTC-based; a local-midnight timestamp
        // shifts the picked date by one day on non-UTC devices.
        val millis = diaryDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showDateDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selected ->
                            viewModel.onDateChange(
                                Instant.ofEpochMilli(selected)
                                    .atZone(java.time.ZoneOffset.UTC)
                                    .toLocalDate()
                            )
                        }
                        showDateDialog = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) { Text("取消") }
            },
        ) {
            // Calendar table only: no text-input mode toggle.
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }

    dateError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDateError() },
            title = { Text("无法保存") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDateError() }) { Text("好") }
            },
        )
    }

    // Unsaved changes: warn before leaving, offer to save first.
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("尚未保存") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("当前内容还没有保存。")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = {
                            showDiscardDialog = false
                            close()
                        }) {
                            Text("放弃更改", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { showDiscardDialog = false }) {
                            Text("继续编辑")
                        }
                        TextButton(onClick = {
                            showDiscardDialog = false
                            editorView?.let { viewModel.onBodyChange(it.text.toString()) }
                            viewModel.save()
                        }) {
                            Text("保存")
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    gallery?.let { (ids, index) ->
        GalleryViewer(
            imageIds = ids,
            initialIndex = index,
            fileStore = fileStore,
            onDismiss = { gallery = null },
        )
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}
