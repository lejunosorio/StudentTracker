package dev.soloistdev.studenttracker.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    // Infinite transition to generate a smooth, memory-safe pulsing placeholder [1]
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

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
        // Renders a pulsing placeholder to prevent layout pop stutters during disk-seek latency [1]
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f))
        )
    }
}