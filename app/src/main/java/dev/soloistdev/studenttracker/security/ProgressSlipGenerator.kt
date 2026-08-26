package dev.soloistdev.studenttracker.security

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bulk progress slips: one page per student, ready to print and hand out.
 *
 * Parent-teacher conference day is the single most manual afternoon in a teacher year - the data
 * is already in the app, it just cannot get onto paper. Everything here is derived from the same
 * engines the screens use, so a slip can never disagree with what the gradebook shows.
 */
object ProgressSlipGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    suspend fun generateAndShare(
        context: Context,
        students: List<StudentEntity>,
        title: String,
        termId: Int = 0
    ) = withContext(Dispatchers.IO) {
        if (students.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No students to report on.", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val repository = StudentRepository(context)
        val logs = repository.getAllAttendanceLogs()
        val columns = repository.getAllAssessmentColumns()
        val scores = repository.getAllAssessmentScores()
        val categories = repository.getAllAssessmentCategories()
        val incidents = repository.getAllIncidents()

        val insights = StudentInsights.compute(students, logs, columns, scores, categories, incidents)
        val scopedColumns = if (termId == 0) columns else columns.filter { it.termId == termId }

        val pdfDocument = PdfDocument()
        val generatedOn = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())

        try {
            students.forEachIndexed { index, student ->
                val page = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                )
                drawSlip(
                    canvas = page.canvas,
                    student = student,
                    title = title,
                    generatedOn = generatedOn,
                    insight = insights[student.id],
                    columns = scopedColumns,
                    scores = scores.filter { it.studentId == student.id },
                    incidents = incidents.filter { it.studentId == student.id }
                )
                pdfDocument.finishPage(page)
            }

            val cacheDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val safeName = title.replace(Regex("[^A-Za-z0-9]+"), "_")
            val pdfFile = File(cacheDir, "progress_slips_$safeName.pdf")
            if (pdfFile.exists()) pdfFile.delete()

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

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Share progress slips"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error generating progress slips.", Toast.LENGTH_SHORT).show()
            }
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawSlip(
        canvas: Canvas,
        student: StudentEntity,
        title: String,
        generatedOn: String,
        insight: StudentInsights.Insight?,
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>,
        incidents: List<BehaviorIncidentEntity>
    ) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headingPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(55, 55, 55)
            textSize = 11f
            isAntiAlias = true
        }
        val mutedPaint = Paint().apply {
            color = Color.rgb(120, 120, 120)
            textSize = 9f
            isAntiAlias = true
        }
        val rulePaint = Paint().apply {
            color = Color.rgb(200, 200, 200)
            strokeWidth = 1f
        }

        var y = MARGIN + 10f

        canvas.drawText(title, MARGIN, y, titlePaint)
        y += 16f
        canvas.drawText("Progress slip generated $generatedOn", MARGIN, y, mutedPaint)
        y += 18f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rulePaint)
        y += 26f

        canvas.drawText("${student.lastName}, ${student.firstName}", MARGIN, y, titlePaint)
        y += 15f
        val classes = student.getClassNamesList().joinToString(", ")
        if (classes.isNotBlank()) {
            canvas.drawText(classes, MARGIN, y, mutedPaint)
            y += 14f
        }
        y += 12f

        // Attendance
        canvas.drawText("Attendance", MARGIN, y, headingPaint)
        y += 16f
        val attendance = insight?.attendance
        if (attendance == null || attendance.total == 0) {
            canvas.drawText("No attendance recorded.", MARGIN, y, bodyPaint)
            y += 16f
        } else {
            val rate = attendance.attendanceRate?.let { String.format(Locale.US, "%.0f%%", it) } ?: "n/a"
            canvas.drawText(
                "Present ${attendance.present}    Absent ${attendance.absent}    Excused ${attendance.excused}    Rate $rate",
                MARGIN, y, bodyPaint
            )
            y += 15f
            if (attendance.unmarked > 0) {
                canvas.drawText(
                    "${attendance.unmarked} day(s) were never marked and are excluded from the rate.",
                    MARGIN, y, mutedPaint
                )
                y += 14f
            }
        }
        y += 14f

        // Grades
        canvas.drawText("Assessments", MARGIN, y, headingPaint)
        y += 16f

        val scored = columns.mapNotNull { column ->
            val raw = scores.firstOrNull { it.columnId == column.id }?.score?.trim()
            if (raw.isNullOrEmpty()) null else column to raw
        }

        if (scored.isEmpty()) {
            canvas.drawText("No graded work in this period.", MARGIN, y, bodyPaint)
            y += 16f
        } else {
            scored.forEach { (column, raw) ->
                // Stop cleanly rather than overrunning the page; the running grade below is the
                // number that actually matters on a slip.
                if (y > PAGE_HEIGHT - 200f) {
                    canvas.drawText("... and ${scored.size - scored.indexOf(column to raw)} more", MARGIN, y, mutedPaint)
                    y += 14f
                    return@forEach
                }
                canvas.drawText(column.name, MARGIN, y, bodyPaint)
                val outOf = "$raw / ${String.format(Locale.US, "%.0f", column.maxPoints)}"
                canvas.drawText(outOf, PAGE_WIDTH - MARGIN - bodyPaint.measureText(outOf), y, bodyPaint)
                y += 14f
            }
        }
        y += 10f

        val gradeText = insight?.gradePercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--"
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rulePaint)
        y += 18f
        canvas.drawText("Overall grade", MARGIN, y, headingPaint)
        canvas.drawText(gradeText, PAGE_WIDTH - MARGIN - titlePaint.measureText(gradeText), y + 2f, titlePaint)
        y += 30f

        // Behaviour
        canvas.drawText("Behaviour notes", MARGIN, y, headingPaint)
        y += 16f
        if (incidents.isEmpty()) {
            canvas.drawText("None recorded.", MARGIN, y, bodyPaint)
            y += 16f
        } else {
            incidents.sortedByDescending { it.incidentDate }.take(5).forEach { incident ->
                canvas.drawText("[${incident.category}] ${incident.title}", MARGIN, y, bodyPaint)
                y += 14f
            }
            if (incidents.size > 5) {
                canvas.drawText("and ${incidents.size - 5} more on file", MARGIN, y, mutedPaint)
                y += 14f
            }
        }

        // Signature block sits at a fixed offset from the bottom so every slip matches
        val signatureY = PAGE_HEIGHT - MARGIN - 40f
        canvas.drawLine(MARGIN, signatureY, MARGIN + 200f, signatureY, rulePaint)
        canvas.drawText("Teacher signature", MARGIN, signatureY + 14f, mutedPaint)
        canvas.drawLine(PAGE_WIDTH - MARGIN - 200f, signatureY, PAGE_WIDTH - MARGIN, signatureY, rulePaint)
        canvas.drawText("Parent signature", PAGE_WIDTH - MARGIN - 200f, signatureY + 14f, mutedPaint)
    }
}
