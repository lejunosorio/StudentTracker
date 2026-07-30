package dev.soloistdev.studenttracker.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object GradebookExportEngine {

    suspend fun exportGradebookToCsv(
        context: Context,
        students: List<StudentEntity>,
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>
    ) = withContext(Dispatchers.IO) {

        val csvContent = StringBuilder()

        // 1. Build Header: Name + Assessment Task Columns
        val header = StringBuilder("Last Name,First Name")
        columns.forEach { col ->
            header.append(",${col.name} (Max: ${col.maxPoints})")
        }
        csvContent.append(header.toString()).append("\n")

        // 2. Build Rows: Map scores sequentially per student
        students.forEach { student ->
            val row = StringBuilder("${student.lastName},${student.firstName}")
            columns.forEach { col ->
                val matchedScore = scores.find { it.studentId == student.id && it.columnId == col.id }
                val scoreVal = matchedScore?.score?.replace(",", " ") ?: ""
                row.append(",$scoreVal")
            }
            csvContent.append(row.toString()).append("\n")
        }

        val cacheDir = File(context.cacheDir, "csv_exports").apply { mkdirs() }
        val csvFile = File(cacheDir, "gradebook_export.csv")
        if (csvFile.exists()) csvFile.delete()

        FileOutputStream(csvFile).use { fos ->
            fos.write(csvContent.toString().toByteArray(Charsets.UTF_8))
            fos.flush()
        }

        csvFile.deleteOnExit()

        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(shareIntent, "Export Gradebook Matrix (CSV)"))
        }
    }
}