package dev.soloistdev.studenttracker.security

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.soloistdev.studenttracker.data.AppDatabase
import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.StudentEntity
import kotlinx.coroutines.Dispatchers // Resolved: Dispatchers import [1]
import kotlinx.coroutines.withContext // Resolved: withContext import [1]
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGeneratorHelper {

    fun clearPdfCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "pdf_reports")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".pdf", ignoreCase = true)) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Resolved: Convert to suspend function to offload data and file operations off the UI thread [1]
    suspend fun generateAndShareStudentPdf(context: Context, student: StudentEntity) = withContext(Dispatchers.IO) {
        clearPdfCache(context)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas: Canvas = page.canvas
        val paint = Paint()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        canvas.drawText("STUDENT PROFILE REPORT", 40f, 60f, headerPaint)
        paint.color = Color.DKGRAY
        canvas.drawRect(40f, 80f, 555f, 82f, paint)

        val encodedFirst = Uri.encode(student.firstName)
        val encodedLast = Uri.encode(student.lastName)
        val encodedAddress = Uri.encode(student.address)
        val encodedContact = Uri.encode(student.contactNumber)
        val encodedGuardians = Uri.encode(student.guardiansJson)
        val encodedCustom = Uri.encode(student.customDataJson)

        val qrPayload = "studenttracker://student?id=${student.id}" +
                "&first=$encodedFirst" +
                "&last=$encodedLast" +
                "&gender=${student.gender}" +
                "&birthday=${student.birthday}" +
                "&address=$encodedAddress" +
                "&contact=$encodedContact" +
                "&guardians=$encodedGuardians" +
                "&custom=$encodedCustom"

        val qrBitmap = QrCodeGenerator.generateQrCode(qrPayload, size = 80)
        if (qrBitmap != null) {
            canvas.drawBitmap(qrBitmap, 475f, 15f, null)
        }

        var yPosition = 120f
        canvas.drawText("Name: ${student.lastName}, ${student.firstName}", 40f, yPosition, textPaint)
        yPosition += 25f

        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        val birthdayFormatted = sdf.format(Date(student.birthday))
        val age = Calendar.getInstance().get(Calendar.YEAR) - Calendar.getInstance().apply { timeInMillis = student.birthday }.get(Calendar.YEAR)
        val genderFull = if (student.gender == "F") "Female" else "Male"

        canvas.drawText("Gender: $genderFull | Age: $age | Birthday: $birthdayFormatted", 40f, yPosition, textPaint)
        yPosition += 25f
        canvas.drawText("Home Address: ${student.address}", 40f, yPosition, textPaint)
        yPosition += 25f

        if (student.contactNumber.isNotEmpty()) {
            canvas.drawText("Student Contact: ${student.contactNumber}", 40f, yPosition, textPaint)
            yPosition += 25f
        }
        yPosition += 15f

        canvas.drawText("CUSTOM METADATA", 40f, yPosition, Paint(textPaint).apply { isFakeBoldText = true })
        yPosition += 10f
        canvas.drawRect(40f, yPosition, 200f, yPosition + 1f, paint)
        yPosition += 25f

        try {
            val json = JSONObject(student.customDataJson)
            val keys = json.keys()
            if (!keys.hasNext()) {
                canvas.drawText("No custom metadata recorded.", 40f, yPosition, textPaint)
                yPosition += 25f
            } else {
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.optString(key, "")
                    if (value.isNotEmpty() && key != "Gender") {
                        val label = key.replace("_", " ")
                        canvas.drawText("$label: $value", 40f, yPosition, textPaint)
                        yPosition += 25f
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        yPosition += 15f
        canvas.drawText("EMERGENCY CONTACTS", 40f, yPosition, Paint(textPaint).apply { isFakeBoldText = true })
        yPosition += 10f
        canvas.drawRect(40f, yPosition, 200f, yPosition + 1f, paint)
        yPosition += 25f

        val guardians = Guardian.listFromJsonString(student.guardiansJson)
        if (guardians.isEmpty()) {
            canvas.drawText("No emergency contact recorded.", 40f, yPosition, textPaint)
        } else {
            guardians.forEach { guardian ->
                canvas.drawText("Guardian: ${guardian.name} (${guardian.relationship})", 40f, yPosition, textPaint)
                yPosition += 20f
                guardian.phones.forEachIndexed { i, phone ->
                    canvas.drawText("Phone ${i+1}: $phone", 60f, yPosition, textPaint)
                    yPosition += 20f
                }
                yPosition += 10f
            }
        }

        // ================= VISUAL ATTENDANCE ANALYTICS SECTION =================
        yPosition += 15f
        canvas.drawText("ATTENDANCE METRICS & VISUAL ANALYTICS", 40f, yPosition, Paint(textPaint).apply { isFakeBoldText = true })
        yPosition += 10f
        canvas.drawRect(40f, yPosition, 200f, yPosition + 1f, paint)
        yPosition += 25f

        val db = AppDatabase.getDatabase(context)
        val studentLogs = db.studentDao().getAllAttendanceLogs().filter { it.studentId == student.id }
        val totalLogs = studentLogs.size

        if (totalLogs == 0) {
            canvas.drawText("No attendance logs recorded for this student.", 40f, yPosition, textPaint)
            yPosition += 25f
        } else {
            val presentCount = studentLogs.count { it.status == "PRESENT" }
            val absentCount = studentLogs.count { it.status == "ABSENT" }
            val excusedCount = studentLogs.count { it.status == "EXCUSED" }
            val unmarkedCount = studentLogs.count { it.status == "NOT_SET" }

            val presentPct = (presentCount.toFloat() / totalLogs.toFloat()) * 100f

            canvas.drawText("Total Attendance Sessions: $totalLogs", 40f, yPosition, textPaint)
            yPosition += 20f
            canvas.drawText("Present: $presentCount (${presentPct.toInt()}%)", 40f, yPosition, textPaint)
            yPosition += 20f
            canvas.drawText("Absent: $absentCount", 40f, yPosition, textPaint)
            yPosition += 20f
            canvas.drawText("Excused: $excusedCount", 40f, yPosition, textPaint)
            yPosition += 30f

            canvas.drawText("Overall Attendance Rate:", 40f, yPosition, textPaint)
            yPosition += 10f

            val barLeft = 40f
            val barTop = yPosition
            val barRight = 240f
            val barBottom = yPosition + 15f

            paint.color = Color.LTGRAY
            canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, 4f, 4f, paint)

            if (presentCount > 0) {
                paint.color = Color.rgb(76, 175, 80)
                val fillRight = barLeft + ((barRight - barLeft) * (presentPct / 100f))
                canvas.drawRoundRect(barLeft, barTop, fillRight, barBottom, 4f, 4f, paint)
            }
            yPosition += 35f

            val pieLeft = 380f
            val pieTop = yPosition - 110f
            val pieRight = 500f
            val pieBottom = yPosition + 10f
            val rectF = RectF(pieLeft, pieTop, pieRight, pieBottom)

            val pSweep = (presentCount.toFloat() / totalLogs.toFloat()) * 360f
            val aSweep = (absentCount.toFloat() / totalLogs.toFloat()) * 360f
            val eSweep = (excusedCount.toFloat() / totalLogs.toFloat()) * 360f
            val uSweep = 360f - pSweep - aSweep - eSweep

            var startAngle = -90f

            if (pSweep > 0) {
                paint.color = Color.rgb(76, 175, 80)
                canvas.drawArc(rectF, startAngle, pSweep, true, paint)
                startAngle += pSweep
            }

            if (aSweep > 0) {
                paint.color = Color.rgb(229, 57, 53)
                canvas.drawArc(rectF, startAngle, aSweep, true, paint)
                startAngle += aSweep
            }

            if (eSweep > 0) {
                paint.color = Color.rgb(251, 140, 0)
                canvas.drawArc(rectF, startAngle, eSweep, true, paint)
                startAngle += eSweep
            }

            if (uSweep > 0) {
                paint.color = Color.rgb(251, 192, 45)
                canvas.drawArc(rectF, startAngle, uSweep, true, paint)
            }
        }

        pdfDocument.finishPage(page)

        val cacheDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val pdfFile = File(cacheDir, "report_${student.lastName}_${student.id}.pdf")

        try {
            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
                fos.flush()
            }

            pdfFile.deleteOnExit()

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Resolved: Dispatches UI intent launching safely on the main thread to prevent OEM-specific crashes [1]
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Print/Share Student PDF"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error generating PDF report.", Toast.LENGTH_SHORT).show()
            }
        } finally {
            pdfDocument.close()
        }
    }
}