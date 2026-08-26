package dev.soloistdev.studenttracker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Decoded student photos, shared across every screen.
 *
 * Photos are stored at up to 800x800 and were previously decoded at full size, once per
 * composition - roughly 2.5MB of heap for an avatar drawn at 48dp, re-read from disk on every
 * scroll. The cache is bounded by a fraction of the heap and keyed by the size actually asked
 * for, so the roster list and the profile header can hold different scalings of one photo
 * without fighting over the same entry.
 */
private object StudentPhotoCache {
    private val cache: LruCache<String, Bitmap> =
        object : LruCache<String, Bitmap>(
            ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceAtLeast(4 * 1024)
        ) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /** Drops every scaling of one photo. Call after the file behind [path] changes or is removed. */
    fun invalidate(path: String) {
        cache.snapshot().keys
            .filter { it.startsWith("$path|") }
            .forEach { cache.remove(it) }
    }
}

/** Lets a screen evict a photo it has just replaced or deleted. */
fun invalidateStudentPhoto(path: String) = StudentPhotoCache.invalidate(path)

/**
 * Decodes no larger than needed. [targetPx] is the longest edge the image will be drawn at;
 * inSampleSize only ever halves, so the result is the smallest power-of-two scaling that still
 * covers it.
 */
private fun decodeScaled(file: File, targetPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    var longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / 2 >= targetPx && sample < 32) {
        longest /= 2
        sample *= 2
    }

    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
    )
}

@Composable
fun LocalImageLoader(
    imagePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    displaySize: Dp = 96.dp,
    fallback: @Composable () -> Unit
) {
    val targetPx = with(LocalDensity.current) { displaySize.roundToPx() }.coerceAtLeast(1)

    // Synchronous cache hit avoids a placeholder flash on every scroll back into view.
    val cacheKey = remember(imagePath, targetPx) { "$imagePath|$targetPx" }
    val cached = remember(cacheKey) { StudentPhotoCache.get(cacheKey) }

    var bitmap by remember(cacheKey) { mutableStateOf(cached) }
    var isLoaded by remember(cacheKey) { mutableStateOf(cached != null || imagePath.isEmpty()) }

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

    LaunchedEffect(cacheKey) {
        if (imagePath.isEmpty() || bitmap != null) {
            isLoaded = true
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            try {
                File(imagePath).takeIf { it.exists() }?.let { decodeScaled(it, targetPx) }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (decoded != null) StudentPhotoCache.put(cacheKey, decoded)
        bitmap = decoded
        isLoaded = true
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
