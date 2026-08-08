package com.diary.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders a local image file (thumbnail or full) as a Compose Image.
 * Falls back to nothing when the file is missing.
 */
@Composable
fun LocalImage(
    file: File?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    // produceState runs on the main thread by default; decode off-thread
    // so large files never stall animations.
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = file) {
        value = file?.takeIf { it.exists() }?.let {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(it.absolutePath)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        // empty placeholder keeps layout stable
        androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxWidth())
    }
}
