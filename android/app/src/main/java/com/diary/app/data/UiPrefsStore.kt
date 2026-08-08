package com.diary.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lightweight UI preference flags (haptics, ...). */
class UiPrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    /** Master switch for press vibration feedback. */
    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    /**
     * Dims the background image with a translucent overlay so foreground
     * content reads better. Exposed as a flow so the app root and the
     * settings screen share one live value.
     */
    private val _backgroundMask = MutableStateFlow(prefs.getBoolean(KEY_BG_MASK, false))
    val backgroundMask: StateFlow<Boolean> = _backgroundMask.asStateFlow()

    var backgroundMaskEnabled: Boolean
        get() = _backgroundMask.value
        set(value) {
            prefs.edit().putBoolean(KEY_BG_MASK, value).apply()
            _backgroundMask.value = value
        }

    /** Mask opacity, 0f..1f. Default keeps the background visible. */
    private val _maskStrength = MutableStateFlow(prefs.getFloat(KEY_BG_MASK_STRENGTH, 0.35f))
    val maskStrength: StateFlow<Float> = _maskStrength.asStateFlow()

    var maskStrengthValue: Float
        get() = _maskStrength.value
        set(value) {
            val v = value.coerceIn(0f, 1f)
            prefs.edit().putFloat(KEY_BG_MASK_STRENGTH, v).apply()
            _maskStrength.value = v
        }

    /**
     * Derive the theme primary color from the background image instead of
     * the fixed brand blue.
     */
    private val _themeFromBackground = MutableStateFlow(prefs.getBoolean(KEY_THEME_FROM_BG, false))
    val themeFromBackground: StateFlow<Boolean> = _themeFromBackground.asStateFlow()

    var themeFromBackgroundEnabled: Boolean
        get() = _themeFromBackground.value
        set(value) {
            prefs.edit().putBoolean(KEY_THEME_FROM_BG, value).apply()
            _themeFromBackground.value = value
        }

    /**
     * Shared opacity for top bars, the search field and cards over the
     * background image. 1f = fully opaque.
     */
    private val _surfaceAlpha = MutableStateFlow(prefs.getFloat(KEY_SURFACE_ALPHA, 0.92f))
    val surfaceAlpha: StateFlow<Float> = _surfaceAlpha.asStateFlow()

    var surfaceAlphaValue: Float
        get() = _surfaceAlpha.value
        set(value) {
            val v = value.coerceIn(0.3f, 1f)
            prefs.edit().putFloat(KEY_SURFACE_ALPHA, v).apply()
            _surfaceAlpha.value = v
        }

    private companion object {
        const val KEY_HAPTIC = "haptic_enabled"
        const val KEY_BG_MASK = "bg_mask"
        const val KEY_BG_MASK_STRENGTH = "bg_mask_strength"
        const val KEY_THEME_FROM_BG = "theme_from_bg"
        const val KEY_SURFACE_ALPHA = "surface_alpha"
    }
}
