package dev.soloistdev.studenttracker.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object CsvExportEngine {

    // Programmatically clear all old temporary .csv files under cacheDir/csv_exports to mitigate local state leaks
    fun clearCsvCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "csv_exports")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".csv", ignoreCase = true)) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun exportRosterToCsv(context: Context, students: List<StudentEntity>, customFileName: String? = null) = withContext(Dispatchers.IO) {
        // Safe-purge prior spreadsheets to eliminate stale plain-text PII
        clearCsvCache(context)

        val db = AppDatabase.getDatabase(context)
        val templates = db.studentDao().getAllFormTemplates()

        // 1. Build Header: Core attributes + custom templates
        val headerFields = listOf(
            "Last Name", "First Name", "Gender", "Birthday", "Address",
            "Student Contact", "Classrooms", "Guardian Name", "Guardian Contact"
        ) + templates.map { it.fieldName.replace("_", " ") }

        val csvHeader = CsvWriter.row(headerFields)
        val csvContent = StringBuilder(csvHeader)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // 2. Build rows dynamically
        students.forEach { student ->
            val birthdayStr = sdf.format(Date(student.birthday))
            val customJson = try { JSONObject(student.customDataJson) } catch (_: Exception) { JSONObject() }

            val guardians = Guardian.listFromJsonString(student.guardiansJson)
            val primaryName = if (guardians.isNotEmpty()) guardians[0].name else "N/A"
            val primaryContact = if (guardians.isNotEmpty()) guardians[0].phones.firstOrNull() ?: "N/A" else "N/A"

            // Several classes in one cell, separated by semicolons so the comma stays available
            // as the field separator.
            val classStr = student.getClassNamesList().joinToString("; ")

            val fields = listOf(
                student.lastName,
                student.firstName,
                student.gender,
                birthdayStr,
                student.address,
                student.contactNumber,
                classStr,
                primaryName,
                primaryContact
            ) + templates.map { customJson.optString(it.fieldName, "") }

            csvContent.append(CsvWriter.row(fields))
        }

        val cacheDir = File(context.cacheDir, "csv_exports").apply { mkdirs() }
        val baseName = if (customFileName.isNullOrBlank()) "choir_roster_export" else customFileName.trim()
        val csvFile = File(cacheDir, "$baseName.csv")

        FileOutputStream(csvFile).use { fos ->
            fos.write(csvContent.toString().toByteArray(Charsets.UTF_8))
            fos.flush()
        }

        // Mark file for cleanup on VM exit
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

        context.startActivity(Intent.createChooser(shareIntent, "Export Spreadsheet ($baseName)"))
    }
}