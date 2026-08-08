package com.diary.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.diary.app.data.UiPrefsStore
import kotlin.math.hypot

/**
 * Clickable modifier with press feedback: the content scales down while
 * pressed. The vibration fires on a CONFIRMED tap (finger up in place),
 * not on press-down — swiping across a card must not vibrate. The press
 * look is also dropped as soon as the finger drags past the touch slop
 * (scrolling), so a swipe across a card never shows a pressed state.
 * The vibration respects the in-app "震动反馈" switch and uses the Vibrator
 * directly (system touch-feedback settings do not apply).
 */
fun Modifier.pressFeedbackModifier(
    onClick: () -> Unit,
    pressedScale: Float = 0.95f,
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uiPrefs = remember { UiPrefsStore(context) }

    this
        .pointerInput(onClick) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                var dragged = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        // Cancelled (the parent scroll consumed the
                        // gesture) — drop the press feedback.
                        pressed = false
                        break
                    }
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (!change.pressed) {
                        // Finger up: a tap when it never dragged.
                        pressed = false
                        if (!dragged && hypot(dx, dy) < 100f) {
                            if (uiPrefs.hapticEnabled) vibrateTick(context)
                            onClick()
                        }
                        break
                    }
                    if (!dragged && hypot(dx, dy) > touchSlop) {
                        // Scrolling started: cancel the pressed look.
                        dragged = true
                        pressed = false
                    }
                }
            }
        }
        .graphicsLayer {
            scaleX = if (pressed) pressedScale else 1f
            scaleY = if (pressed) pressedScale else 1f
        }
}
