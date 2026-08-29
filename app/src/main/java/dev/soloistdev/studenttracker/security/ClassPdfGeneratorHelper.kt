package dev.soloistdev.studenttracker.security

import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.OrganizationSettings
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.soloistdev.studenttracker.data.AppDatabase
import dev.soloistdev.studenttracker.data.BehaviorIncidentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("SpellCheckingInspection") // Suppresses harmless IDE spelling warnings (coords, fileprovider, etc.)
object ClassPdfGeneratorHelper {

    private class PdfPageTracker(
        val pdfDocument: PdfDocument,
        val pageWidth: Int = 595,
        val pageHeight: Int = 842,
        val headerText: String
    ) {
        private var pageCount = 0
        var currentPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var yPos = 90f

        private val paint = Paint()
        private val headerPaint = Paint().apply {
            color = Color.rgb(103, 80, 164) // Purple theme running header
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        private val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 9f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        fun startNewPage() {
            currentPage?.let {
                // Draw running page number footer on current page before closing
                canvas?.drawText("Page $pageCount", pageWidth / 2f, pageHeight - 30f, footerPaint)
                pdfDocument.finishPage(it)
            }

            pageCount++
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
            val page = pdfDocument.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas
            yPos = 90f

            // Draw running header bar on each page
            canvas?.drawText(headerText, 40f, 45f, headerPaint)
            paint.color = Color.rgb(218, 218, 222)
            paint.strokeWidth = 1f
            canvas?.drawLine(40f, 55f, pageWidth - 40f, 56f, paint)
        }

        fun ensureSpace(spaceNeeded: Float) {
            if (yPos + spaceNeeded > pageHeight - 60f) {
                startNewPage()
            }
        }

        fun finish() {
            currentPage?.let {
                canvas?.drawText("Page $pageCount", pageWidth / 2f, pageHeight - 30f, footerPaint)
                pdfDocument.finishPage(it)
            }
        }
    }

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

    suspend fun generateAndShareClassPdf(context: Context, className: String) = withContext(Dispatchers.IO) {
        clearPdfCache(context)

        val db = AppDatabase.getDatabase(context)
        val students = db.studentDao().getAllActiveStudents().filter { student ->
            student.getClassNamesList().contains(className)
        }

        if (students.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No active students enrolled in this classroom to generate a report.", Toast.LENGTH_LONG).show()
            }
            return@withContext
        }

        val pdfDocument = PdfDocument()
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
        val sectionTitlePaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val tableHeaderPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val initialsPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val dividerPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val paint = Paint()

        val tracker = PdfPageTracker(
            pdfDocument = pdfDocument,
            headerText = "CLASSROOM PROFILE & COHORT REPORT: ${className.uppercase()}"
        )

        // ==========================================
        // PAGE 1: CLASSROOM ROSTER & 2D SEATING CHART
        // ==========================================
        tracker.startNewPage()

        tracker.ensureSpace(120f)
        // Organisation name first: this report leaves the app, and one headed only
        // "CLASSROOM COHORT REPORT" gives no clue who produced it.
        val orgName = OrganizationSettings.organizationName(context)
        if (orgName.isNotBlank()) {
            tracker.canvas?.drawText(orgName.uppercase(Locale.getDefault()), 40f, tracker.yPos, headerPaint)
            tracker.yPos += 24f
        }
        val groupTerm = OrganizationSettings.group(context, context.getString(R.string.term_default_group))
        tracker.canvas?.drawText(
            groupTerm.uppercase(Locale.getDefault()) + " REPORT", 40f, tracker.yPos, headerPaint
        )
        tracker.yPos += 20f
        paint.color = Color.DKGRAY
        tracker.canvas?.drawRect(40f, tracker.yPos, 555f, tracker.yPos + 2f, paint)
        tracker.yPos += 25f

        tracker.canvas?.drawText("$groupTerm: $className", 40f, tracker.yPos, sectionTitlePaint)

        val classroomDetails = db.studentDao().getAllClassrooms().find { it.name == className }
        val startTime = classroomDetails?.startTime ?: "08:00 AM"
        val endTime = classroomDetails?.endTime ?: "04:00 PM"

        tracker.yPos += 20f
        tracker.canvas?.drawText("Daily Active Session Time: $startTime - $endTime", 40f, tracker.yPos, textPaint)
        tracker.yPos += 20f
        tracker.canvas?.drawText("Total Active Enrollment: ${students.size} Students", 40f, tracker.yPos, textPaint)
        tracker.yPos += 35f

        // Seating Chart section
        tracker.ensureSpace(320f)
        tracker.canvas?.drawText("CLASSROOM 2D SEATING CHART WORKSPACE", 40f, tracker.yPos, sectionTitlePaint)
        tracker.yPos += 10f
        tracker.canvas?.drawLine(40f, tracker.yPos, 350f, tracker.yPos, dividerPaint)
        tracker.yPos += 25f

        val mapLeft = 80f
        val mapTop = tracker.yPos
        val mapRight = 515f
        val mapBottom = tracker.yPos + 260f

        // Seating map background card container
        paint.color = Color.rgb(245, 245, 247)
        paint.style = Paint.Style.FILL
        tracker.canvas?.drawRoundRect(mapLeft, mapTop, mapRight, mapBottom, 12f, 12f, paint)

        paint.color = Color.rgb(218, 218, 222)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        tracker.canvas?.drawRoundRect(mapLeft, mapTop, mapRight, mapBottom, 12f, 12f, paint)

        // Classroom blackboard baseline representation bar
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(103, 80, 164) // Theme purple
        tracker.canvas?.drawRect(mapLeft + 120f, mapTop + 10f, mapRight - 120f, mapTop + 22f, paint)

        val whiteboardLabelPaint = Paint().apply {
            color = Color.WHITE
            textSize = 7f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        tracker.canvas?.drawText("WHITEBOARD / INSTRUCTOR DESK", (mapLeft + mapRight) / 2f, mapTop + 18f, whiteboardLabelPaint)

        // Plot placed student seats on the 2D canvas
        paint.style = Paint.Style.FILL
        students.forEach { student ->
            val coords = student.getSeatingCoordinates(className)
            if (coords != null && coords.first >= 0f && coords.second >= 0f) {
                val cx = mapLeft + 30f + (coords.first * (mapRight - mapLeft - 60f))
                val cy = mapTop + 45f + (coords.second * (mapBottom - mapTop - 70f))

                // Draw seat circle
                paint.color = Color.rgb(103, 80, 164)
                tracker.canvas?.drawCircle(cx, cy, 14f, paint)

                // Draw student initials
                val initials = "${student.lastName.take(1)}${student.firstName.take(1)}".uppercase()
                tracker.canvas?.drawText(initials, cx, cy + 3.5f, initialsPaint)
            }
        }

        tracker.yPos = mapBottom + 30f

        // ==========================================
        // PAGE 2: ACADEMIC EVALUATIONS & GRADEBOOK METRICS
        // ==========================================
        tracker.startNewPage()

        tracker.ensureSpace(50f)
        tracker.canvas?.drawText("CLASS ASSESSMENT TASKS AVERAGE RATE", 40f, tracker.yPos, sectionTitlePaint)
        tracker.yPos += 10f
        tracker.canvas?.drawLine(40f, tracker.yPos, 350f, tracker.yPos, dividerPaint)
        tracker.yPos += 25f

        val columns = db.studentDao().getAllAssessmentColumns()
        val scores = db.studentDao().getAllAssessmentScores()
        val studentIds = students.map { it.id }.toSet()

        if (columns.isEmpty()) {
            tracker.ensureSpace(30f)
            tracker.canvas?.drawText("No evaluation tasks or grading columns found.", 40f, tracker.yPos, textPaint)
            tracker.yPos += 40f
        } else {
            columns.forEach { col ->
                tracker.ensureSpace(24f)
                val colScores = scores.filter { it.columnId == col.id && it.studentId in studentIds }
                val numericScores = colScores.mapNotNull { it.score.toDoubleOrNull() }
                val average = if (numericScores.isNotEmpty()) numericScores.average() else 0.0

                tracker.canvas?.drawText(col.name, 50f, tracker.yPos, textPaint)
                tracker.canvas?.drawText(String.format(Locale.US, "Max: %.1f pts", col.maxPoints), 280f, tracker.yPos, textPaint)
                tracker.canvas?.drawText(String.format(Locale.US, "Avg: %.1f pts", average), 420f, tracker.yPos, tableHeaderPaint)

                tracker.canvas?.drawLine(40f, tracker.yPos + 6f, 555f, tracker.yPos + 7f, dividerPaint)
                tracker.yPos += 24f
            }
        }

        tracker.yPos += 20f
        tracker.ensureSpace(50f)
        tracker.canvas?.drawText("STUDENT CUMULATIVE ACADEMIC LEDGER", 40f, tracker.yPos, sectionTitlePaint)
        tracker.yPos += 10f
        tracker.canvas?.drawLine(40f, tracker.yPos, 350f, tracker.yPos, dividerPaint)
        tracker.yPos += 25f

        students.forEachIndexed { index, student ->
            tracker.ensureSpace(22f)
            if (index % 2 == 1) {
                paint.color = Color.rgb(248, 248, 250)
                paint.style = Paint.Style.FILL
                tracker.canvas?.drawRect(40f, tracker.yPos - 14f, 555f, tracker.yPos + 6f, paint)
            }

            val studentScores = scores.filter { it.studentId == student.id }
            val achieved = studentScores.mapNotNull { it.score.toDoubleOrNull() }.sum()
            val totalColIds = studentScores.map { it.columnId }.toSet()
            val possible = columns.filter { it.id in totalColIds }.sumOf { it.maxPoints }

            val rateStr = if (possible > 0) String.format(Locale.US, "%.1f%%", (achieved / possible) * 100f) else "N/A"

            tracker.canvas?.drawText("${student.lastName}, ${student.firstName}", 50f, tracker.yPos, textPaint)
            tracker.canvas?.drawText(String.format(Locale.US, "Achieved: %.1f / %.1f total pts", achieved, possible), 250f, tracker.yPos, textPaint)
            tracker.canvas?.drawText("Rate: $rateStr", 440f, tracker.yPos, tableHeaderPaint)

            tracker.canvas?.drawLine(40f, tracker.yPos + 6f, 555f, tracker.yPos + 7f, dividerPaint)
            tracker.yPos += 22f
        }

        // ==========================================
        // PAGE 3: BEHAVIOR BREAKDOWN & ATTENDANCE
        // ==========================================
        tracker.startNewPage()

        // Fetch classroom behavioral incidents
        val classIncidents = mutableListOf<BehaviorIncidentEntity>()
        students.forEach { s ->
            classIncidents.addAll(db.studentDao().getIncidentsForStudent(s.id))
        }

        val positiveCount = classIncidents.count { it.category == "Positive" }
        val negativeCount = classIncidents.count { it.category == "Negative" }
        val neutralCount = classIncidents.count { it.category == "Neutral" }
        val totalIncidents = classIncidents.size

        // Ensure vertical bounds contain the running behavior statistics block and chart
        tracker.ensureSpace(140f)
        tracker.canvas?.drawText("BEHAVIOR METRICS", 40f, tracker.yPos, sectionTitlePaint)
        tracker.yPos += 10f
        tracker.canvas?.drawLine(40f, tracker.yPos, 200f, tracker.yPos, dividerPaint)
        tracker.yPos += 25f

        tracker.canvas?.drawText("Total Recorded Behavioral Events: $totalIncidents", 40f, tracker.yPos, textPaint)
        tracker.yPos += 20f
        tracker.canvas?.drawText("Positive Achievements: $positiveCount", 40f, tracker.yPos, textPaint)
        tracker.yPos += 20f
        tracker.canvas?.drawText("Negative Incidents: $negativeCount", 40f, tracker.yPos, textPaint)
        tracker.yPos += 20f
        tracker.canvas?.drawText("Neutral Milestones: $neutralCount", 40f, tracker.yPos, textPaint)

        // Draw Behavior Pie Chart (Top Right - coordinates positioned relative to tracker.yPos)
        if (totalIncidents > 0) {
            val rectFBehavior = RectF(380f, tracker.yPos - 75f, 500f, tracker.yPos + 45f)
            val pIncSweep = (positiveCount.toFloat() / totalIncidents) * 360f
            val nIncSweep = (negativeCount.toFloat() / totalIncidents) * 360f
            val uIncSweep = 360f - pIncSweep - nIncSweep

            var behaviorStartAngle = -90f
            paint.style = Paint.Style.FILL
            if (pIncSweep > 0) {
                paint.color = Color.rgb(76, 175, 80) // Green
                tracker.canvas?.drawArc(rectFBehavior, behaviorStartAngle, pIncSweep, true, paint)
                behaviorStartAngle += pIncSweep
            }
            if (nIncSweep > 0) {
                paint.color = Color.rgb(229, 57, 53) // Red
                tracker.canvas?.drawArc(rectFBehavior, behaviorStartAngle, nIncSweep, true, paint)
                behaviorStartAngle += nIncSweep
            }
            if (uIncSweep > 0) {
                paint.color = Color.rgb(158, 158, 158) // Grey
                tracker.canvas?.drawArc(rectFBehavior, behaviorStartAngle, uIncSweep, true, paint)
            }
        }

        tracker.yPos += 30f

        // Classroom Attendance Rate
        val allLogs = db.studentDao().getAllAttendanceLogs().filter { it.studentId in studentIds }
        val totalLogsCount = allLogs.size
        val presentLogsCount = allLogs.count { it.status == "PRESENT" }
        val classAttendanceRate = if (totalLogsCount > 0) (presentLogsCount.toFloat() / totalLogsCount) * 100f else 0f

        tracker.ensureSpace(90f)
        tracker.canvas?.drawText("COHORT ATTENDANCE SUMMARY", 40f, tracker.yPos, sectionTitlePaint)
        tracker.yPos += 10f
        tracker.canvas?.drawLine(40f, tracker.yPos, 250f, tracker.yPos, dividerPaint)
        tracker.yPos += 25f

        tracker.canvas?.drawText(String.format(Locale.US, "Cumulative Classroom Attendance Rate: %.1f%%", classAttendanceRate), 40f, tracker.yPos, textPaint)
        tracker.yPos += 15f

        // Draw Class Attendance progress bar
        val barLeft = 40f
        val barTop = tracker.yPos
        val barRight = 280f
        val barBottom = tracker.yPos + 12f

        paint.color = Color.LTGRAY
        paint.style = Paint.Style.FILL
        tracker.canvas?.drawRoundRect(barLeft, barTop, barRight, barBottom, 4f, 4f, paint)

        if (classAttendanceRate > 0f) {
            paint.color = Color.rgb(103, 80, 164) // Purple theme
            val fillRight = barLeft + ((barRight - barLeft) * (classAttendanceRate / 100f))
            tracker.canvas?.drawRoundRect(barLeft, barTop, fillRight, barBottom, 4f, 4f, paint)
        }

        tracker.yPos = barBottom + 35f

        // Cohort Behavioral Notable Insights List
        tracker.ensureSpace(120f)
        tracker.canvas?.drawText("COHORT BEHAVIORAL NOTABLE EVENTS", 40f, tracker.yPos, sectionTitlePaint)
        tracker.yPos += 10f
        tracker.canvas?.drawLine(40f, tracker.yPos, 555f, tracker.yPos, dividerPaint)
        tracker.yPos += 25f

        val recentClassIncidents = classIncidents.take(4)
        if (recentClassIncidents.isEmpty()) {
            tracker.canvas?.drawText("No behavioral milestone entries reported in this cohort.", 40f, tracker.yPos, textPaint)
        } else {
            val listSdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            recentClassIncidents.forEach { incident ->
                tracker.ensureSpace(40f)
                val student = students.find { it.id == incident.studentId }
                val studentName = if (student != null) "${student.lastName}, ${student.firstName}" else "Unknown Student"
                val dateStr = listSdf.format(Date(incident.incidentDate))

                tracker.canvas?.drawText(
                    "[$dateStr] $studentName: ${incident.category.uppercase()} - ${incident.title}",
                    50f,
                    tracker.yPos,
                    Paint(textPaint).apply { isFakeBoldText = true }
                )
                if (incident.description.isNotBlank()) {
                    tracker.yPos += 16f
                    tracker.canvas?.drawText("Notes: ${incident.description}", 70f, tracker.yPos, textPaint)
                }
                tracker.yPos += 24f
            }
        }

        tracker.finish()

        // Write compiled classroom PDF file to cache sandbox
        val cacheDir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val pdfFile = File(cacheDir, "classroom_report_${className.replace(" ", "_")}.pdf")

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

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Share Classroom PDF Report"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error generating classroom PDF report.", Toast.LENGTH_SHORT).show()
            }
        } finally {
            pdfDocument.close()
        }
    }
}