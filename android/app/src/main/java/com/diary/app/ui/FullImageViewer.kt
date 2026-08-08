package com.diary.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.diary.app.data.ImageFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen image viewer: dims everything, shows the original photo
 * fitted to the screen. Tap anywhere to dismiss.
 */
@Composable
fun FullImageViewer(
    imageId: String,
    fileStore: ImageFileStore,
    onDismiss: () -> Unit,
) {
    // Decode off the UI thread so the dialog's open animation does not
    // stall on a full-size JPEG decode.
    val metrics = LocalContext.current.resources.displayMetrics
    val maxDim = maxOf(metrics.widthPixels, metrics.heightPixels)
    var bitmap by remember(imageId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(imageId) {
        val file = fileStore.existingFull(imageId)
        val decoded = if (file != null) {
            withContext(Dispatchers.IO) { ImageDecoder.decodeSampled(file, maxDim) }
        } else {
            null
        }
        bitmap = decoded
        if (decoded == null) onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
