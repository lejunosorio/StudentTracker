package dev.soloistdev.studenttracker.security

import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.OrganizationSettings
import java.util.Locale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun generateAndShareStudentPdf(context: Context, student: StudentEntity) = withContext(Dispatchers.IO) {
        clearPdfCache(context)

        // Enforce database scope resolution across all pages to ensure proper compilation
        val db = AppDatabase.getDatabase(context)

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
        val dividerPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        // ==========================================
        // PAGE 1: STUDENT PROFILE & CONTACT SCHEMA
        // ==========================================
        val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1: Canvas = page1.canvas
        val paint = Paint()

        // Page Header
        //
        // The organisation name goes above the report title, because this is the page that
        // leaves the app: it is handed to a parent or filed by an office, and a report headed
        // only "STUDENT PROFILE REPORT" gives no clue who produced it.
        val orgName = OrganizationSettings.organizationName(context)
        val learnerTerm = OrganizationSettings.learner(context, context.getString(R.string.term_default_learner))
        var headerY = 60f
        if (orgName.isNotBlank()) {
            canvas1.drawText(orgName.uppercase(Locale.getDefault()), 40f, headerY, headerPaint)
            headerY += 24f
        }
        canvas1.drawText(
            context.getString(R.string.term_profile_report, learnerTerm.uppercase(Locale.getDefault())),
            40f, headerY, headerPaint
        )
        paint.color = Color.DKGRAY
        canvas1.drawRect(40f, headerY + 20f, 555f, headerY + 22f, paint)

        // Dynamic deep-link optical QR payload setup
        val encodedFirst = Uri.encode(student.firstName)
        val encodedLast = Uri.encode(student.lastName)
        val encodedAddress = Uri.encode(student.address)
        val encodedContact = Uri.encode(student.contactNumber)
        val encodedGuardians = Uri.encode(student.guardiansJson)
        val encodedCustom = Uri.encode(student.customDataJson)
        val encodedClass = Uri.encode(student.classNamesJson)

        // Resolves primary coordinates mapped inside our deep link payload
        val primaryClass = student.getClassNamesList().firstOrNull() ?: ""
        val primaryCoords = student.getSeatingCoordinates(primaryClass) ?: Pair(-1f, -1f)

        val qrPayload = "studenttracker://student?id=${student.id}" +
                "&first=$encodedFirst" +
                "&last=$encodedLast" +
                "&gender=${student.gender}" +
                "&birthday=${student.birthday}" +
                "&address=$encodedAddress" +
                "&contact=$encodedContact" +
                "&guardians=$encodedGuardians" +
                "&custom=$encodedCustom" +
                "&class=$encodedClass" +
                "&seatingX=${primaryCoords.first}" +
                "&seatingY=${primaryCoords.second}"

        val qrBitmap = QrCodeGenerator.generateQrCode(qrPayload, size = 80)
        if (qrBitmap != null) {
            canvas1.drawBitmap(qrBitmap, 475f, 15f, null)
        }

        var yPosition = 120f
        canvas1.drawText("Name: ${student.lastName}, ${student.firstName}", 40f, yPosition, textPaint)
        yPosition += 25f

        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        val birthdayFormatted = sdf.format(Date(student.birthday))
        val age = dev.soloistdev.studenttracker.data.AgeCalculator.ageInYears(student.birthday)
        val genderFull = if (student.gender == "F") "Female" else "Male"

        canvas1.drawText("Gender: $genderFull | Age: $age | Birthday: $birthdayFormatted", 40f, yPosition, textPaint)
        yPosition += 25f
        canvas1.drawText("Home Address: ${student.address}", 40f, yPosition, textPaint)
        yPosition += 25f

        if (student.contactNumber.isNotEmpty()) {
            canvas1.drawText("Student Contact: ${student.contactNumber}", 40f, yPosition, textPaint)
            yPosition += 25f
        }
        yPosition += 15f

        // Classroom & Seating Section
        canvas1.drawText("CLASSROOM & SEATING MAP", 40f, yPosition, sectionTitlePaint)
        yPosition += 10f
        canvas1.drawLine(40f, yPosition, 250f, yPosition, dividerPaint)
        yPosition += 25f

        // Displays comma-separated list representing all assigned classroom cohorts
        val classStringList = student.getClassNamesList().joinToString(", ")
        canvas1.drawText("Assigned Cohorts: ${classStringList.ifEmpty { "Floating Student (No assigned classroom)" }}", 40f, yPosition, textPaint)

        val coords = student.getSeatingCoordinates(primaryClass)
        if (primaryClass.isNotEmpty() && coords != null && coords.first >= 0f && coords.second >= 0f) {
            yPosition += 20f
            val gridRow = ((coords.second * 10).toInt()) + 1
            val gridCol = ((coords.first * 10).toInt()) + 1
            canvas1.drawText("Seating position in $primaryClass: Row $gridRow, Col $gridCol", 40f, yPosition, textPaint)

            // Render a mini visual seating chart layout of the classroom map parallel to the text block
            val gridLeft = 380f
            val gridTop = yPosition - 35f
            val gridRight = 530f
            val gridBottom = yPosition + 45f

            // Seating map background card container
            paint.color = Color.rgb(245, 245, 247)
            paint.style = Paint.Style.FILL
            canvas1.drawRoundRect(gridLeft, gridTop, gridRight, gridBottom, 8f, 8f, paint)

            paint.color = Color.rgb(218, 218, 222)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            canvas1.drawRoundRect(gridLeft, gridTop, gridRight, gridBottom, 8f, 8f, paint)

            // Classroom blackboard baseline representation bar
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(103, 80, 164) // Thematic deep violet
            canvas1.drawRect(gridLeft + 35f, gridTop + 4f, gridRight - 35f, gridTop + 9f, paint)

            val mapLabelPaint = Paint().apply {
                color = Color.WHITE
                textSize = 4.5f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas1.drawText("FRONT / BOARD", (gridLeft + gridRight) / 2f, gridTop + 8f, mapLabelPaint)

            // Plot existing classmates in the same cohort as smaller neutral dots
            val classmates = db.studentDao().getAllActiveStudents()
                .filter { classmate -> classmate.getClassNamesList().contains(primaryClass) && !classmate.isDeleted && classmate.id != student.id }

            paint.color = Color.rgb(180, 180, 185)
            paint.style = Paint.Style.FILL
            classmates.forEach { classmate ->
                val classmateCoords = classmate.getSeatingCoordinates(primaryClass)
                if (classmateCoords != null && classmateCoords.first >= 0f && classmateCoords.second >= 0f) {
                    val cx = gridLeft + 10f + (classmateCoords.first * (gridRight - gridLeft - 20f))
                    val cy = gridTop + 15f + (classmateCoords.second * (gridBottom - gridTop - 25f))
                    canvas1.drawCircle(cx, cy, 3.5f, paint)
                }
            }

            // Highlights the primary student with a bold M3-thematic indicator
            val studentCx = gridLeft + 10f + (coords.first * (gridRight - gridLeft - 20f))
            val studentCy = gridTop + 15f + (coords.second * (gridBottom - gridTop - 25f))

            paint.color = Color.rgb(103, 80, 164) // Highlight color
            canvas1.drawCircle(studentCx, studentCy, 6f, paint)

            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            canvas1.drawCircle(studentCx, studentCy, 6f, paint)

            paint.style = Paint.Style.FILL
        } else {
            yPosition += 20f
            canvas1.drawText("Seating Status: Floating (Seating coordinate unassigned in $primaryClass)", 40f, yPosition, textPaint)
        }

        yPosition += 35f

        // Custom Fields Section
        canvas1.drawText("CUSTOM METADATA", 40f, yPosition, sectionTitlePaint)
        yPosition += 10f
        canvas1.drawLine(40f, yPosition, 250f, yPosition, dividerPaint)
        yPosition += 25f

        try {
            val json = JSONObject(student.customDataJson)
            val keys = json.keys()
            if (!keys.hasNext()) {
                canvas1.drawText("No custom metadata recorded.", 40f, yPosition, textPaint)
                yPosition += 25f
            } else {
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.optString(key, "")
                    if (value.isNotEmpty() && key != "Gender") {
                        val label = key.replace("_", " ")
                        canvas1.drawText("$label: $value", 40f, yPosition, textPaint)
                        yPosition += 25f
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        yPosition += 15f
        canvas1.drawText("EMERGENCY CONTACTS", 40f, yPosition, sectionTitlePaint)
        yPosition += 10f
        canvas1.drawLine(40f, yPosition, 250f, yPosition, dividerPaint)
        yPosition += 25f

        val guardians = Guardian.listFromJsonString(student.guardiansJson)
        if (guardians.isEmpty()) {
            canvas1.drawText("No emergency contact recorded.", 40f, yPosition, textPaint)
        } else {
            guardians.forEach { guardian ->
                canvas1.drawText("Guardian: ${guardian.name} (${guardian.relationship})", 40f, yPosition, textPaint)
                yPosition += 20f
                guardian.phones.forEachIndexed { i, phone ->
                    canvas1.drawText("Phone ${i+1}: $phone", 60f, yPosition, textPaint)
                    yPosition += 20f
                }
                yPosition += 10f
            }
        }

        pdfDocument.finishPage(page1)

        // ==========================================
        // PAGE 2: VISUAL ANALYTICS & INSIGHTS
        // ==========================================
        val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2: Canvas = page2.canvas

        // Page 2 Title
        canvas2.drawText("VISUAL ANALYTICS & INSIGHTS", 40f, 60f, headerPaint)
        canvas2.drawRect(40f, 80f, 555f, 82f, paint)

        // Read relational analytics from database
        val studentLogs = db.studentDao().getAllAttendanceLogs().filter { log -> log.studentId == student.id }
        val behaviorIncidents = db.studentDao().getIncidentsForStudent(student.id)

        // 1. Attendance Section (Top Half)
        var yPos2 = 120f
        canvas2.drawText("ATTENDANCE METRICS", 40f, yPos2, sectionTitlePaint)
        yPos2 += 10f
        canvas2.drawLine(40f, yPos2, 200f, yPos2, dividerPaint)
        yPos2 += 25f

        val totalLogs = studentLogs.size
        if (totalLogs == 0) {
            canvas2.drawText("No attendance logs recorded for this student.", 40f, yPos2, textPaint)
            yPos2 += 65f
        } else {
            val presentCount = studentLogs.count { log -> log.status == "PRESENT" }
            val absentCount = studentLogs.count { log -> log.status == "ABSENT" }
            val excusedCount = studentLogs.count { log -> log.status == "EXCUSED" }
            val presentPct = (presentCount.toFloat() / totalLogs.toFloat()) * 100f

            canvas2.drawText("Total Attendance Sessions: $totalLogs", 40f, yPos2, textPaint)
            yPos2 += 20f
            canvas2.drawText("Present: $presentCount (${presentPct.toInt()}%)", 40f, yPos2, textPaint)
            yPos2 += 20f
            canvas2.drawText("Absent: $absentCount | Excused: $excusedCount", 40f, yPos2, textPaint)
            yPos2 += 25f

            // Attendance Progress Bar
            canvas2.drawText("Attendance Rate:", 40f, yPos2, textPaint)
            yPos2 += 8f
            val barLeft = 40f
            val barTop = yPos2
            val barRight = 240f
            val barBottom = yPos2 + 12f

            paint.color = Color.LTGRAY
            canvas2.drawRoundRect(barLeft, barTop, barRight, barBottom, 4f, 4f, paint)

            if (presentCount > 0) {
                paint.color = Color.rgb(76, 175, 80)
                val fillRight = barLeft + ((barRight - barLeft) * (presentPct / 100f))
                canvas2.drawRoundRect(barLeft, barTop, fillRight, barBottom, 4f, 4f, paint)
            }
            yPos2 += 35f

            // Attendance Pie Chart (Right)
            val rectFAttendance = RectF(380f, 110f, 500f, 230f)
            val pSweep = (presentCount.toFloat() / totalLogs.toFloat()) * 360f
            val aSweep = (absentCount.toFloat() / totalLogs.toFloat()) * 360f
            val eSweep = (excusedCount.toFloat() / totalLogs.toFloat()) * 360f
            val uSweep = 360f - pSweep - aSweep - eSweep

            var startAngle = -90f
            if (pSweep > 0) {
                paint.color = Color.rgb(76, 175, 80)
                canvas2.drawArc(rectFAttendance, startAngle, pSweep, true, paint)
                startAngle += pSweep
            }
            if (aSweep > 0) {
                paint.color = Color.rgb(229, 57, 53)
                canvas2.drawArc(rectFAttendance, startAngle, aSweep, true, paint)
                startAngle += aSweep
            }
            if (eSweep > 0) {
                paint.color = Color.rgb(251, 140, 0)
                canvas2.drawArc(rectFAttendance, startAngle, eSweep, true, paint)
                startAngle += eSweep
            }
            if (uSweep > 0) {
                paint.color = Color.rgb(251, 192, 45)
                canvas2.drawArc(rectFAttendance, startAngle, uSweep, true, paint)
            }
        }

        // Divider
        canvas2.drawLine(40f, 300f, 555f, 301f, dividerPaint)

        // 2. Behavior Incident Section (Bottom Half)
        yPos2 = 330f
        canvas2.drawText("BEHAVIOR LOGS & ANALYTICS", 40f, yPos2, sectionTitlePaint)
        yPos2 += 10f
        canvas2.drawLine(40f, yPos2, 250f, yPos2, dividerPaint)
        yPos2 += 25f

        val totalIncidents = behaviorIncidents.size
        if (totalIncidents == 0) {
            canvas2.drawText("No behavioral incidents or milestones recorded.", 40f, yPos2, textPaint)
            paint.color = Color.rgb(224, 224, 224)
            canvas2.drawCircle(440f, 380f, 60f, paint)
            yPos2 += 100f
        } else {
            val positiveCount = behaviorIncidents.count { incident -> incident.category == "Positive" }
            val negativeCount = behaviorIncidents.count { incident -> incident.category == "Negative" }
            val neutralCount = behaviorIncidents.count { incident -> incident.category == "Neutral" }

            val positivePct = (positiveCount.toFloat() / totalIncidents.toFloat()) * 100f
            val negativePct = (negativeCount.toFloat() / totalIncidents.toFloat()) * 100f
            val neutralPct = (neutralCount.toFloat() / totalIncidents.toFloat()) * 100f

            canvas2.drawText("Total Logged Events: $totalIncidents", 40f, yPos2, textPaint)
            yPos2 += 20f
            canvas2.drawText("Positive Achievements: $positiveCount (${positivePct.toInt()}%)", 40f, yPos2, textPaint)
            yPos2 += 20f
            canvas2.drawText("Negative Incidents: $negativeCount (${negativePct.toInt()}%)", 40f, yPos2, textPaint)
            yPos2 += 20f
            canvas2.drawText("Neutral Milestones: $neutralCount (${neutralPct.toInt()}%)", 40f, yPos2, textPaint)
            yPos2 += 35f

            // Dynamic Behavior Breakdown Pie Chart
            val rectFBehavior = RectF(380f, 330f, 500f, 450f)
            val pIncSweep = (positiveCount.toFloat() / totalIncidents.toFloat()) * 360f
            val nIncSweep = (negativeCount.toFloat() / totalIncidents.toFloat()) * 360f
            val uIncSweep = (neutralCount.toFloat() / totalIncidents.toFloat()) * 360f

            var behaviorStartAngle = -90f
            if (pIncSweep > 0) {
                paint.color = Color.rgb(76, 175, 80)
                canvas2.drawArc(rectFBehavior, behaviorStartAngle, pIncSweep, true, paint)
                behaviorStartAngle += pIncSweep
            }
            if (nIncSweep > 0) {
                paint.color = Color.rgb(229, 57, 53)
                canvas2.drawArc(rectFBehavior, behaviorStartAngle, nIncSweep, true, paint)
                behaviorStartAngle += nIncSweep
            }
            if (uIncSweep > 0) {
                paint.color = Color.rgb(158, 158, 158)
                canvas2.drawArc(rectFBehavior, behaviorStartAngle, uIncSweep, true, paint)
            }
        }

        // Draw Historical Behavior List (Bottom)
        canvas2.drawText("RECENT INCIDENT ENTRIES", 40f, 520f, Paint(sectionTitlePaint).apply { textSize = 11f })
        canvas2.drawLine(40f, 530f, 555f, 531f, dividerPaint)

        var logY = 550f
        val listSdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)

        if (behaviorIncidents.isEmpty()) {
            canvas2.drawText("No historical event entries.", 40f, logY, textPaint)
        } else {
            behaviorIncidents.take(3).forEach { incident ->
                val dateStr = listSdf.format(Date(incident.incidentDate))
                canvas2.drawText(
                    "[${dateStr}] ${incident.category.uppercase()} - ${incident.title}",
                    40f,
                    logY,
                    Paint(textPaint).apply { isFakeBoldText = true }
                )
                if (incident.description.isNotBlank()) {
                    logY += 16f
                    canvas2.drawText("Notes: ${incident.description}", 60f, logY, textPaint)
                }
                logY += 24f
            }
        }

        pdfDocument.finishPage(page2)

        // ==========================================
        // PAGE 3: ACADEMIC PERFORMANCE & GRADE REPORT
        // ==========================================
        val pageInfo3 = PdfDocument.PageInfo.Builder(595, 842, 3).create()
        val page3 = pdfDocument.startPage(pageInfo3)
        val canvas3: Canvas = page3.canvas

        // Page Header
        canvas3.drawText("ACADEMIC PERFORMANCE & GRADE REPORT", 40f, 60f, headerPaint)
        paint.color = Color.DKGRAY
        canvas3.drawRect(40f, 80f, 555f, 82f, paint)

        // Read relational Gradebook data from database
        val gradingColumns = db.studentDao().getAllAssessmentColumns()
        val studentScores = db.studentDao().getAllAssessmentScores().filter { score -> score.studentId == student.id }

        if (gradingColumns.isEmpty()) {
            canvas3.drawText("No academic evaluations or tasks recorded.", 40f, 120f, textPaint)
        } else {
            // Draw Table Header Background block
            paint.color = Color.rgb(240, 240, 240)
            canvas3.drawRect(40f, 110f, 555f, 135f, paint)

            // Draw Table Header Column Labels
            val tableHeaderPaint = Paint(textPaint).apply { isFakeBoldText = true }
            canvas3.drawText("Assessment Task / Column", 50f, 127f, tableHeaderPaint)
            canvas3.drawText("Max Points", 300f, 127f, tableHeaderPaint)
            canvas3.drawText("Achieved Grade / Score", 420f, 127f, tableHeaderPaint)

            var rowY = 160f
            var totalPointsPossible = 0.0
            var totalPointsAchieved = 0.0
            var numericCount = 0

            gradingColumns.forEachIndexed { index, col ->
                // Alternating zebra striping
                if (index % 2 == 1) {
                    paint.color = Color.rgb(250, 249, 250)
                    canvas3.drawRect(40f, rowY - 17f, 555f, rowY + 8f, paint)
                }

                // Match score
                val matchingScore = studentScores.find { score -> score.columnId == col.id }
                val scoreStr = matchingScore?.score ?: "N/A"

                // Accumulate statistics for summary calculation if score is numeric
                val scoreNum = scoreStr.toDoubleOrNull()
                if (scoreNum != null) {
                    totalPointsPossible += col.maxPoints
                    totalPointsAchieved += scoreNum
                    numericCount++
                }

                // Draw Row Text data
                canvas3.drawText(col.name, 50f, rowY, textPaint)
                canvas3.drawText(String.format(Locale.US, "%.1f", col.maxPoints), 300f, rowY, textPaint)
                canvas3.drawText(scoreStr, 420f, rowY, Paint(textPaint).apply { isFakeBoldText = true })

                // Draw Row divider line
                paint.color = Color.rgb(230, 230, 230)
                canvas3.drawLine(40f, rowY + 8f, 555f, rowY + 9f, paint)

                rowY += 25f
            }

            // Draw Cumulative Academic Summary card if numeric performance metrics exist
            if (numericCount > 0 && totalPointsPossible > 0) {
                val summaryY = rowY + 30f
                paint.color = Color.rgb(232, 245, 233) // Light green background card
                canvas3.drawRoundRect(40f, summaryY, 555f, summaryY + 80f, 8f, 8f, paint)

                val summaryTitlePaint = Paint(textPaint).apply {
                    color = Color.rgb(46, 125, 50) // Green text color
                    isFakeBoldText = true
                    textSize = 12f
                }
                canvas3.drawText("OVERALL ACADEMIC PERFORMANCE SUMMARY", 60f, summaryY + 25f, summaryTitlePaint)

                val averagePct = (totalPointsAchieved / totalPointsPossible) * 100f
                canvas3.drawText(
                    String.format(Locale.US, "Overall Cumulative Average Rate: %.1f%%", averagePct),
                    60f,
                    summaryY + 48f,
                    textPaint
                )
                canvas3.drawText(
                    String.format(Locale.US, "Total Points: %.1f / %.1f possible", totalPointsAchieved, totalPointsPossible),
                    60f,
                    summaryY + 65f,
                    textPaint
                )
            }
        }

        pdfDocument.finishPage(page3)

        // Write complete compiled payload to cache sandbox
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