package dev.soloistdev.studenttracker.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageCompressor {
    fun compressAndSaveImage(context: Context, imageUri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // 1. Downscale the bitmap to max 800x800 to prevent OOM and storage bloat
            val maxSize = 800
            val width = originalBitmap.width
            val height = originalBitmap.height
            val ratio = width.toFloat() / height.toFloat()

            val (targetWidth, targetHeight) = if (width > height) {
                Pair(maxSize, (maxSize / ratio).toInt())
            } else {
                Pair((maxSize * ratio).toInt(), maxSize)
            }

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)

            // 2. Save the compressed file privately in internal filesDir/student_images/
            val imagesDir = File(context.filesDir, "student_images").apply { mkdirs() }
            val destFile = File(imagesDir, "img_${UUID.randomUUID()}.webp")

            val outputStream = FileOutputStream(destFile)
            // Compress to WebP Lossy at 70% quality (reduces file size to ~150KB)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                resizedBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 70, outputStream)
            } else {
                @Suppress("DEPRECATION")
                resizedBitmap.compress(Bitmap.CompressFormat.WEBP, 70, outputStream)
            }
            outputStream.flush()
            outputStream.close()

            destFile.absolutePath // Return private filesystem absolute path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}