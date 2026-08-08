package com.diary.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeSeedTest {

    private fun solidBitmap(color: Int): Bitmap =
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }

    private fun hueOf(color: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv[0]
    }

    @Test
    fun solidBlue_derivesBlueHue() {
        val seed = extractThemeSeed(solidBitmap(android.graphics.Color.rgb(74, 144, 217))) // BrandBlue

        assertNotNull(seed)
        assertEquals(212f, hueOf(seed!!), 2f)
    }

    @Test
    fun solidGreen_derivesGreenHue() {
        val seed = extractThemeSeed(solidBitmap(android.graphics.Color.rgb(80, 180, 90)))

        assertNotNull(seed)
        assertEquals(125f, hueOf(seed!!), 3f)
    }

    @Test
    fun grayImage_returnsNull() {
        val seed = extractThemeSeed(solidBitmap(android.graphics.Color.rgb(128, 128, 128)))

        assertNull(seed)
    }

    @Test
    fun nearWhiteImage_returnsNull() {
        val seed = extractThemeSeed(solidBitmap(android.graphics.Color.rgb(250, 250, 250)))

        assertNull(seed)
    }

    @Test
    fun mixedImage_ignoresGrayPixels() {
        // A mostly-gray image with a saturated orange patch still derives
        // the orange hue from the colored pixels.
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(128, 128, 128))
        }
        for (y in 4 until 12) {
            for (x in 4 until 12) {
                bmp.setPixel(x, y, android.graphics.Color.rgb(255, 140, 20))
            }
        }

        val seed = extractThemeSeed(bmp)

        assertNotNull(seed)
        assertEquals(31f, hueOf(seed!!), 4f)
    }

    @Test
    fun brightness_blackIsZero_whiteIsOne() {
        val black = solidBitmap(android.graphics.Color.rgb(0, 0, 0))
        val white = solidBitmap(android.graphics.Color.rgb(255, 255, 255))

        assertEquals(0f, extractBrightness(black), 0.01f)
        assertEquals(1f, extractBrightness(white), 0.01f)
    }
}

