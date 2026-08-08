package com.diary.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diary.app.data.LockStore
import com.diary.app.ui.applock.PinPad

/**
 * Multi-stage PIN dialog used by settings:
 * 1. (optional) verify the current PIN,
 * 2. enter a new PIN,
 * 3. re-enter it to confirm.
 * For the disable-lock flow ([requiresNewPin] = false) it ends after verify.
 */
@Composable
fun PinFlowDialog(
    lockStore: LockStore,
    title: String,
    verifyCurrent: Boolean,
    requiresNewPin: Boolean = true,
    onDone: (newPin: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var stage by remember {
        mutableStateOf(if (verifyCurrent) Stage.VERIFY else Stage.ENTER)
    }
    var newPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val currentLength = lockStore.pinLength().coerceIn(4, 6)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = when (stage) {
                        Stage.VERIFY -> "输入当前 PIN"
                        Stage.ENTER -> "设置新 PIN（4-6 位数字）"
                        Stage.CONFIRM -> "再次输入新 PIN"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = errorText ?: " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
                PinPad(
                    maxLength = when (stage) {
                        Stage.VERIFY -> currentLength
                        Stage.CONFIRM -> newPin.length
                        else -> 6
                    },
                    minLength = 4,
                    autoSubmitAtLength = when (stage) {
                        Stage.VERIFY -> currentLength
                        Stage.CONFIRM -> newPin.length
                        else -> null
                    },
                    onComplete = { value ->
                        when (stage) {
                            Stage.VERIFY -> {
                                if (lockStore.verifyPin(value)) {
                                    if (requiresNewPin) {
                                        stage = Stage.ENTER
                                    } else {
                                        onDone(null)
                                    }
                                } else {
                                    errorText = "PIN 错误"
                                }
                            }
                            Stage.ENTER -> {
                                newPin = value
                                stage = Stage.CONFIRM
                            }
                            Stage.CONFIRM -> {
                                if (value == newPin) {
                                    onDone(newPin)
                                } else {
                                    errorText = "两次输入不一致，请重新设置"
                                    newPin = ""
                                    stage = Stage.ENTER
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private enum class Stage { VERIFY, ENTER, CONFIRM }
