package dev.soloistdev.studenttracker.security

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

object QrCodeGenerator {

    fun generateQrCode(text: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Generates a composite Bitmap containing the QR Code and the student's name label at the bottom [1]
    fun generateQrCodeWithLabel(studentName: String, qrPayload: String, size: Int = 512): Bitmap? {
        val qrBitmap = generateQrCode(qrPayload, size) ?: return null

        // Allocate 70px of extra height at the bottom for the name label
        val extraHeight = 70
        val totalHeight = size + extraHeight
        val compositeBitmap = createBitmap(size, totalHeight)

        val canvas = Canvas(compositeBitmap)
        canvas.drawColor(Color.WHITE) // Draw a solid white background
        canvas.drawBitmap(qrBitmap, 0f, 0f, null) // Stamp the QR Code matrix

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Render center-aligned student name at the bottom
        canvas.drawText(studentName, (size / 2).toFloat(), (size + 35).toFloat(), textPaint)

        return compositeBitmap
    }

    // Shares the composite labeled QR Code image securely using FileProvider [1]
    fun shareQrCode(context: Context, bitmap: Bitmap, studentName: String) {
        try {
            val cacheDir = File(context.cacheDir, "qr_exports").apply { mkdirs() }
            val qrFile = File(cacheDir, "QR_${studentName.replace(" ", "_")}.png")
            if (qrFile.exists()) qrFile.delete()

            FileOutputStream(qrFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            qrFile.deleteOnExit()

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                qrFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Profile QR Code"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Saves the composite labeled QR Code directly to the Gallery using MediaStore [1]
    fun saveQrToGallery(context: Context, bitmap: Bitmap, studentName: String): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "QR_${studentName.replace(" ", "_")}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Places the exported file cleanly under the sandboxed Pictures/StudentTracker directory
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StudentTracker")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false

        return try {
            resolver.openOutputStream(imageUri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.flush()
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            resolver.delete(imageUri, null, null)
            false
        }
    }
}