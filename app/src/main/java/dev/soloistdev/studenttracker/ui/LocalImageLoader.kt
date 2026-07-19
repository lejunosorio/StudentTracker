package dev.soloistdev.studenttracker.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LocalImageLoader(
    imagePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    var bitmap by remember(imagePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoaded by remember(imagePath) { mutableStateOf(false) }

    LaunchedEffect(imagePath) {
        if (imagePath.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(imagePath)
                    if (file.exists()) {
                        bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isLoaded = true
            }
        } else {
            isLoaded = true
        }
    }

    if (isLoaded) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        } else {
            fallback()
        }
    } else {
        Box(modifier = modifier) {
            fallback()
        }
    }
}