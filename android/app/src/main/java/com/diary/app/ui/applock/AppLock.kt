package com.diary.app.ui.applock

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.diary.app.data.LockStore
import com.diary.app.ui.pressFeedbackModifier

/**
 * Fingerprint / biometric auth via androidx.biometric.
 *
 * Uses BIOMETRIC_STRONG | BIOMETRIC_WEAK: some vendors (Xiaomi/MIUI and
 * others) classify their under-display sensors as weak, so checking only
 * STRONG hides the feature or fails at runtime.
 */
object BiometricAuth {

    /** True when the device has a usable biometric enrolled (strong or weak). */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                or BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(context: Context, onSuccess: () -> Unit, onFailure: () -> Unit) {
        val activity = context as? FragmentActivity
            ?: return onFailure()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onFailure()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    onFailure()
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("指纹解锁日记")
            .setSubtitle("验证指纹以解锁")
            .setNegativeButtonText("取消")
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        }
        prompt.authenticate(builder.build())
    }
}

/** Full-screen unlock overlay: PIN pad, optional fingerprint. */
@Composable
fun UnlockScreen(
    lockStore: LockStore,
    biometricAvailable: Boolean,
    onUnlocked: () -> Unit,
) {
    val storedLength = lockStore.pinLength().coerceIn(4, 6)
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Auto-prompt biometrics once when the lock screen appears.
    LaunchedEffect(Unit) {
        if (lockStore.isFingerprintEnabled() && biometricAvailable) {
            BiometricAuth.authenticate(context, onUnlocked) { errorText = "指纹验证失败，请输入 PIN" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("日记已锁定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        PinDots(count = input.length, total = storedLength)
        Spacer(Modifier.height(8.dp))
        Text(
            text = errorText ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(24.dp))
        PinKeypad(
            onDigit = { digit ->
                if (input.length < storedLength) {
                    input += digit
                    if (input.length == storedLength) {
                        if (lockStore.verifyPin(input)) {
                            onUnlocked()
                        } else {
                            errorText = "PIN 错误，请重试"
                            input = ""
                        }
                    }
                }
            },
            onDelete = { input = input.dropLast(1) },
        )
        if (lockStore.isFingerprintEnabled() && biometricAvailable) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    BiometricAuth.authenticate(context, onUnlocked) {
                        errorText = "指纹验证失败，请输入 PIN"
                    }
                },
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("指纹解锁")
            }
        }
    }
}

/** PIN input shared by the unlock screen and the settings dialogs. */
@Composable
fun PinPad(
    maxLength: Int,
    minLength: Int = 4,
    autoSubmitAtLength: Int? = null,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }

    fun submit(value: String) {
        input = ""
        onComplete(value)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PinDots(count = input.length, total = maxLength)
        Spacer(Modifier.height(24.dp))
        PinKeypad(
            onDigit = { digit ->
                if (input.length < maxLength) {
                    input += digit
                    if (autoSubmitAtLength != null && input.length >= autoSubmitAtLength) {
                        submit(input)
                    }
                }
            },
            onDelete = { input = input.dropLast(1) },
        )
        if (autoSubmitAtLength == null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { submit(input) },
                enabled = input.length in minLength..maxLength,
            ) {
                Text("确认")
            }
        }
    }
}

@Composable
private fun PinDots(count: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (i < count) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun PinKeypad(onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    PinKeyButton(digit = digit, onClick = { onDigit(digit) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.size(72.dp))
            PinKeyButton(digit = '0', onClick = { onDigit('0') })
            Box(
                modifier = Modifier.pressFeedbackModifier(
                    onClick = onDelete,
                    pressedScale = 0.85f,
                )
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Backspace,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PinKeyButton(digit: Char, onClick: (Char) -> Unit) {
    Box(
        modifier = Modifier.pressFeedbackModifier(
            onClick = { onClick(digit) },
            pressedScale = 0.85f,
        )
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
