package com.diary.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diary.app.DiaryApplication
import com.diary.app.ui.detail.BlockStyles

/**
 * Block format editor: JSON per block type (paragraph / heading / image /
 * timestamp). Opens pre-filled with the current config — the defaults when
 * nothing was customized — so every parameter is visible and editable.
 */
@Composable
fun BlockStylesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as DiaryApplication).container.appearanceStore
    // A stored config that is blank or equals the defaults (e.g. a saved
    // "{}" from an earlier build) still opens as the full default JSON.
    val stored = store.blockStylesJson.value
    var text by remember {
        mutableStateOf(
            if (stored != null && BlockStyles.parse(stored) != BlockStyles.defaults) stored
            else BlockStyles.toJson(BlockStyles.defaults)
        )
    }
    var message by remember { mutableStateOf<String?>(null) }

    // Immersive editing surface, same plain background as the entry editor.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Top bar: back arrow + title, same as the settings page.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "块格式",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "以 JSON 编辑每个块的排版参数（字号 sp、行高比例、上下边距 dp），保存后立即生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    message = null
                },
                minLines = 10,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                store.resetBlockStyles()
                text = BlockStyles.toJson(BlockStyles.defaults)
                message = "已恢复默认"
            }) {
                Text("恢复默认")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = {
                if (BlockStyles.parse(text) != null) {
                    store.setBlockStylesJson(text)
                    message = "已保存"
                } else {
                    message = "JSON 格式不正确，请检查后重试"
                }
            }) {
                Text("保存")
            }
        }
    }
    }
}
