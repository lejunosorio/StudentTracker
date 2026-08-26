package dev.soloistdev.studenttracker.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.core.graphics.scale

object ImageCompressor {

    /** Longest edge kept on disk. Photos are only ever drawn as small avatars or thumbnails. */
    private const val MAX_SIZE = 800

    /**
     * Downscales a picked image and stores it privately, returning its path.
     *
     * Suspending, and on the IO dispatcher: a modern phone camera produces a 50-megapixel JPEG,
     * and decoding plus re-encoding one is far too much work to do on the thread drawing the UI.
     */
    suspend fun compressAndSaveImage(context: Context, imageUri: Uri): String? =
        withContext(Dispatchers.IO) { compressAndSaveImageBlocking(context, imageUri) }

    private fun compressAndSaveImageBlocking(context: Context, imageUri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Read the dimensions first, without allocating the pixels. Decoding a full-size
            // camera image before downscaling it is what the downscale exists to avoid: at
            // 50MP that is 200MB of heap, and an OutOfMemoryError on a mid-range device.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 2. Decode at the smallest power-of-two scaling that still covers MAX_SIZE.
            var sample = 1
            var longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / 2 >= MAX_SIZE && sample < 32) {
                longest /= 2
                sample *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
            val originalBitmap = contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // 3. Trim the remainder, since inSampleSize only halves.
            val width = originalBitmap.width
            val height = originalBitmap.height
            val ratio = width.toFloat() / height.toFloat()
            val (targetWidth, targetHeight) = when {
                maxOf(width, height) <= MAX_SIZE -> width to height
                width > height -> MAX_SIZE to (MAX_SIZE / ratio).toInt().coerceAtLeast(1)
                else -> (MAX_SIZE * ratio).toInt().coerceAtLeast(1) to MAX_SIZE
            }

            val resizedBitmap = if (targetWidth == width && targetHeight == height) {
                originalBitmap
            } else {
                originalBitmap.scale(targetWidth, targetHeight)
            }

            // 4. Save the compressed file privately in internal filesDir/student_images/
            val imagesDir = File(context.filesDir, "student_images").apply { mkdirs() }
            val destFile = File(imagesDir, "img_${UUID.randomUUID()}.webp")

            // SAFE STREAM WRITE PIPELINE: `.use` block ensures the output stream is
            // closed securely even if a compression exception or write failure occurs [1].
            FileOutputStream(destFile).use { outputStream ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    resizedBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 70, outputStream)
                } else {
                    @Suppress("DEPRECATION")
                    resizedBitmap.compress(Bitmap.CompressFormat.WEBP, 70, outputStream)
                }
                outputStream.flush()
            }

            if (resizedBitmap !== originalBitmap) originalBitmap.recycle()
            resizedBitmap.recycle()

            destFile.absolutePath // Return private filesystem absolute path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
