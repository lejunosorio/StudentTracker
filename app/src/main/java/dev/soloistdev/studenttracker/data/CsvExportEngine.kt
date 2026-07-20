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
        val csvHeader = "Last Name,First Name,Gender,Birthday,Address,Guardian Name,Guardian Contact,Purok,Status,Bautisado\n"
        val csvContent = StringBuilder(csvHeader)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        students.forEach { student ->
            val bdayStr = sdf.format(Date(student.birthday))
            val json = try { JSONObject(student.customDataJson) } catch (e: Exception) { JSONObject() }
            val purok = json.optString("Purok", "")
            val status = json.optString("Status", "")
            val bated = json.optString("Bautisado", "")

            // Parse guardians list dynamically to extract primary guardian details cleanly
            val guardians = Guardian.listFromJsonString(student.guardiansJson)
            val primaryName = if (guardians.isNotEmpty()) guardians[0].name else "N/A"
            val primaryContact = if (guardians.isNotEmpty()) guardians[0].phones.firstOrNull() ?: "N/A" else "N/A"

            val cleanAddress = student.address.replace(",", " ")
            val cleanGuardian = primaryName.replace(",", " ")

            val row = "${student.lastName},${student.firstName},${student.gender},$bdayStr,$cleanAddress,$cleanGuardian,$primaryContact,$purok,$status,$bated\n"
            csvContent.append(row)
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