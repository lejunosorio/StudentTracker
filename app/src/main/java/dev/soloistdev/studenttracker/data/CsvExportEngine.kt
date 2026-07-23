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
    suspend fun exportRosterToCsv(context: Context, students: List<StudentEntity>) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val templates = db.studentDao().getAllFormTemplates() // Safely fetch custom fields

        // 1. Build Header: Core attributes + custom templates
        val coreHeader = "Last Name,First Name,Gender,Birthday,Address,Guardian Name,Guardian Contact"
        val dynamicHeader = if (templates.isNotEmpty()) {
            "," + templates.joinToString(",") { it.fieldName.replace("_", " ") }
        } else ""

        val csvHeader = "$coreHeader$dynamicHeader\n"
        val csvContent = StringBuilder(csvHeader)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // 2. Build rows dynamically
        students.forEach { student ->
            val birthdayStr = sdf.format(Date(student.birthday))
            val customJson = try { JSONObject(student.customDataJson) } catch (_: Exception) { JSONObject() }

            val guardians = Guardian.listFromJsonString(student.guardiansJson)
            val primaryName = if (guardians.isNotEmpty()) guardians[0].name else "N/A"
            val primaryContact = if (guardians.isNotEmpty()) guardians[0].phones.firstOrNull() ?: "N/A" else "N/A"

            val cleanAddress = student.address.replace(",", " ")
            val cleanGuardian = primaryName.replace(",", " ")

            val coreRow = "${student.lastName},${student.firstName},${student.gender},$birthdayStr,$cleanAddress,$cleanGuardian,$primaryContact"

            // Extract and format all custom attributes generically
            val dynamicRow = if (templates.isNotEmpty()) {
                "," + templates.joinToString(",") { template ->
                    val rawValue = customJson.optString(template.fieldName, "")
                    rawValue.replace(",", " ") // Prevent CSV delimiter breakage
                }
            } else ""

            csvContent.append("$coreRow$dynamicRow\n")
        }

        val cacheDir = File(context.cacheDir, "csv_exports").apply { mkdirs() }
        val csvFile = File(cacheDir, "choir_roster_export.csv")
        if (csvFile.exists()) csvFile.delete()

        val fos = FileOutputStream(csvFile)
        fos.write(csvContent.toString().toByteArray(Charsets.UTF_8))
        fos.close()

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

        context.startActivity(Intent.createChooser(shareIntent, "Export Spreadsheet (CSV)"))
    }
}