package dev.soloistdev.studenttracker.security

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.StudentEntity
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGeneratorHelper {

    fun generateAndShareStudentPdf(context: Context, student: StudentEntity) {
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
            isFakeBoldText = true // Fixed: Native Android Paint Bold attribute
            isAntiAlias = true
        }

        canvas.drawText("STUDENT PROFILE REPORT", 40f, 60f, headerPaint)
        paint.color = Color.DKGRAY
        canvas.drawRect(40f, 80f, 555f, 82f, paint)

        var yPosition = 120f
        canvas.drawText("Name: ${student.lastName}, ${student.firstName}", 40f, yPosition, textPaint)
        yPosition += 25f

        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        val bdayFormatted = sdf.format(Date(student.birthday))
        val age = Calendar.getInstance().get(Calendar.YEAR) - Calendar.getInstance().apply { timeInMillis = student.birthday }.get(Calendar.YEAR)
        val genderFull = if (student.gender == "F") "Female" else "Male"

        canvas.drawText("Gender: $genderFull | Age: $age | Birthday: $bdayFormatted", 40f, yPosition, textPaint)
        yPosition += 25f
        canvas.drawText("Home Address: ${student.address}", 40f, yPosition, textPaint)
        yPosition += 40f

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
                        val displayValue = if (key == "Bautisado" && value == "Y") "Yes" else value
                        canvas.drawText("$label: $displayValue", 40f, yPosition, textPaint)
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

        // Render dynamic guardians list on PDF output
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

        pdfDocument.finishPage(page)

        val cacheDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val pdfFile = File(cacheDir, "report_${student.lastName}_${student.id}.pdf")

        try {
            val fos = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

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

            context.startActivity(Intent.createChooser(shareIntent, "Print/Share Student PDF"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating PDF report.", Toast.LENGTH_SHORT).show()
        }
    }
}