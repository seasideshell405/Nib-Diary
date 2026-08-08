package com.diary.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Direct Vibrator tick. Unlike Compose's performHapticFeedback, this is NOT
 * filtered by the system "touch feedback" setting, so it fires on devices
 * (e.g. many Xiaomi/MIUI builds) that ignore the framework haptics.
 */
fun vibrateTick(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= 26) {
        vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(15)
    }
}
