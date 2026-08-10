package com.diary.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diary.app.DiaryApplication
import com.diary.app.R
import com.diary.app.data.AppearanceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Fixed brand blue, matching the reference design (light #4A90D9).
private val BrandBlue = Color(0xFF4A90D9)
private val BrandBlueDark = Color(0xFF63A3E6)
private val BrandBlueContainer = Color(0xFFD9E8F7)
private val BrandBlueOnContainer = Color(0xFF0D3C66)
private val BrandBlueContainerDark = Color(0xFF2A4A6B)
private val BrandBlueOnContainerDark = Color(0xFFD9E8F7)

/** Primary color family derived from a seed color. */
private data class SeedPalette(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

/** Background brightness + optional theme seed, computed off the UI thread. */
private data class BackgroundInfo(
    val seed: Color?,
    val brightness: Float,
)

/**
 * Text color for page top bars (back arrow + title): near-black on light
 * backgrounds, white on dark ones — chosen against the background image.
 */
val LocalTopBarTextColor = staticCompositionLocalOf { Color(0xFF1F1F1F) }

/** Background image mean brightness, 0f (black) .. 1f (white). */
val LocalBackgroundBrightness = staticCompositionLocalOf { 1f }

@Composable
fun DiaryTheme(
    // Dark mode is not supported yet: always use the light scheme even
    // when the system is in dark mode.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as DiaryApplication).container
    val appearanceStore = container.appearanceStore
    val uiPrefs = container.uiPrefs

    // Decode a small sample of the background image once per background
    // change: derive the theme seed (when the toggle is on) and the top
    // bar text color (always) from it.
    val backgroundVersion by appearanceStore.version.collectAsStateWithLifecycle()
    val themeFromBackground by uiPrefs.themeFromBackground.collectAsStateWithLifecycle()
    val backgroundInfo by produceState<BackgroundInfo?>(null, backgroundVersion) {
        value = withContext(Dispatchers.IO) { decodeBackgroundInfo(context, appearanceStore) }
    }
    val effectiveSeed = if (themeFromBackground) backgroundInfo?.seed else null
    val topBarTextColor = if ((backgroundInfo?.brightness ?: 1f) > 0.55f) {
        Color(0xFF1F1F1F)
    } else {
        Color.White
    }

    val colorScheme = if (effectiveSeed == null) {
        if (darkTheme) {
            darkColorScheme(
                primary = BrandBlueDark,
                onPrimary = Color.White,
                primaryContainer = BrandBlueContainerDark,
                onPrimaryContainer = BrandBlueOnContainerDark,
            )
        } else {
            lightColorScheme(
                primary = BrandBlue,
                onPrimary = Color.White,
                primaryContainer = BrandBlueContainer,
                onPrimaryContainer = BrandBlueOnContainer,
            )
        }
    } else {
        val palette = paletteFromSeed(effectiveSeed, darkTheme)
        if (darkTheme) {
            darkColorScheme(
                primary = palette.primary,
                onPrimary = Color.White,
                primaryContainer = palette.primaryContainer,
                onPrimaryContainer = palette.onPrimaryContainer,
            )
        } else {
            lightColorScheme(
                primary = palette.primary,
                onPrimary = Color.White,
                primaryContainer = palette.primaryContainer,
                onPrimaryContainer = palette.onPrimaryContainer,
            )
        }
    }

    CompositionLocalProvider(
        LocalTopBarTextColor provides topBarTextColor,
        LocalBackgroundBrightness provides (backgroundInfo?.brightness ?: 1f),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = DiaryShapes,
            content = content,
        )
    }
}

/** Compose color from HSV components. */
private fun hsv(hue: Float, sat: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

/**
 * Extracts a theme seed color from the background image: the mean hue of
 * saturated, mid-value pixels (ignoring gray/near-white/near-black areas
 * that would pull the average toward mud). Returns null when the image is
 * too muted to derive anything.
 */
internal fun extractThemeSeed(bitmap: Bitmap): Color? {
    val h = bitmap.height
    val w = bitmap.width
    var sinSum = 0.0
    var cosSum = 0.0
    var satSum = 0.0
    var count = 0
    val hsvArr = FloatArray(3)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val c = bitmap.getPixel(x, y)
            android.graphics.Color.RGBToHSV(
                (c shr 16) and 0xFF,
                (c shr 8) and 0xFF,
                c and 0xFF,
                hsvArr,
            )
            if (hsvArr[1] < 0.10f) continue // gray / white (near-white has low saturation)
            if (hsvArr[2] < 0.15f) continue // too dark
            val rad = hsvArr[0] * PI / 180.0
            sinSum += sin(rad)
            cosSum += cos(rad)
            satSum += hsvArr[1]
            count++
        }
    }
    if (count == 0) return null
    val hue = (atan2(sinSum, cosSum) * 180.0 / PI + 360.0) % 360.0
    return hsv(hue.toFloat(), (satSum / count).toFloat(), 1f)
}

/** Reads the current background (custom file or default drawable) sampled. */
private fun decodeSample(context: Context, appearanceStore: AppearanceStore): Bitmap? {
    val file = appearanceStore.backgroundFile
    return if (appearanceStore.hasCustomBackground() && file.exists()) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } else {
        context.resources.openRawResource(R.drawable.diary_bg)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }
}

private fun decodeBackgroundInfo(context: Context, appearanceStore: AppearanceStore): BackgroundInfo? =
    runCatching {
        val bmp = decodeSample(context, appearanceStore) ?: return@runCatching null
        try {
            BackgroundInfo(
                seed = extractThemeSeed(bmp),
                brightness = extractBrightness(bmp),
            )
        } finally {
            bmp.recycle()
        }
    }.getOrNull()

/** Mean luma of the bitmap, 0f (black) .. 1f (white). */
internal fun extractBrightness(bitmap: Bitmap): Float {
    var sum = 0.0
    var count = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val c = bitmap.getPixel(x, y)
            sum += 0.299f * ((c shr 16) and 0xFF) +
                0.587f * ((c shr 8) and 0xFF) +
                0.114f * (c and 0xFF)
            count++
            x += 2
        }
        y += 2
    }
    return if (count == 0) 1f else (sum / count / 255f).toFloat()
}

/** Builds the primary palette around a seed color. */
private fun paletteFromSeed(seed: Color, darkTheme: Boolean): SeedPalette {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val hue = hsv[0]
    val sat = hsv[1].coerceIn(0.25f, 0.9f)
    return if (darkTheme) {
        SeedPalette(
            primary = hsv(hue, (sat * 0.7f + 0.15f).coerceAtMost(1f), 0.86f),
            primaryContainer = hsv(hue, sat * 0.45f, 0.30f),
            onPrimaryContainer = hsv(hue, sat * 0.4f, 0.92f),
        )
    } else {
        SeedPalette(
            primary = hsv(hue, sat, 0.72f),
            primaryContainer = hsv(hue, sat * 0.35f, 0.94f),
            onPrimaryContainer = hsv(hue, sat * 0.85f, 0.28f),
        )
    }
}
