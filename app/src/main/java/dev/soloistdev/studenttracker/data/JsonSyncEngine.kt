package dev.soloistdev.studenttracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object JsonSyncEngine {

    private const val MAX_IMPORT_SIZE_BYTES = 10 * 1024 * 1024

    data class MergeSummary(
        val newStudentsCount: Int,
        val updatedStudentsCount: Int,
        val updatedLogsCount: Int,
        val skippedCount: Int,
        val studentsToInsert: List<StudentEntity>,
        val studentsToUpdate: List<StudentEntity>,
        val logsToMerge: List<AttendanceLogEntity>,
        val incomingIdToIdentityMap: Map<Int, String>
    )

    suspend fun evaluateMerge(
        context: Context,
        uri: Uri,
        repository: StudentRepository
    ): MergeSummary = withContext(Dispatchers.IO) {
        verifyFileSizeLimit(context, uri)
        val fileName = getFileName(context, uri)
        val mimeType = context.contentResolver.getType(uri)
        val isJsonFile = fileName?.endsWith(".json", ignoreCase = true) == true || mimeType == "application/json"

        val decryptedContent = if (isJsonFile) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Failed to read stream")
        } else {
            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val tempEncFile = File(cacheDir, "temp_backup_parse.enc")
            if (tempEncFile.exists()) tempEncFile.delete()

            context.contentResolver.openInputStream(uri)?.use { stream ->
                FileOutputStream(tempEncFile).use { fos -> stream.copyTo(fos) }
            } ?: throw IOException("Failed to cache stream")

            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val encryptedFile = EncryptedFile.Builder(
                tempEncFile,
                context,
                masterKeyAlias,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            val content = encryptedFile.openFileInput().use { it.readBytes() }
            tempEncFile.delete()
            content
        }

        val jsonString = String(decryptedContent, Charsets.UTF_8).trim()
        val payloadObj = try { JSONObject(jsonString) } catch (_: Exception) { null }

        val incomingStudentsArr = payloadObj?.optJSONArray("students") ?: JSONArray(jsonString)
        val incomingLogsArr = payloadObj?.optJSONArray("attendanceLogs") ?: JSONArray()

        val localStudents = repository.getAllActiveStudents()
        val localLogs = repository.getAllAttendanceLogs()

        val studentsToInsert = mutableListOf<StudentEntity>()
        val studentsToUpdate = mutableListOf<StudentEntity>()
        val logsToMerge = mutableListOf<AttendanceLogEntity>()

        var newStudents = 0
        var updatedStudents = 0
        var updatedLogs = 0
        var skipped = 0

        val incomingIdToIdentityMap = mutableMapOf<Int, String>()
        val identityToLocalIdMap = mutableMapOf<String, Int>()
        localStudents.forEach { s ->
            val identity = "${s.firstName.lowercase()}_${s.lastName.lowercase()}_${s.birthday}"
            identityToLocalIdMap[identity] = s.id
        }

        for (i in 0 until incomingStudentsArr.length()) {
            val sObj = incomingStudentsArr.getJSONObject(i)
            val incomingId = sObj.optInt("id", -1)
            val first = sObj.optString("firstName", "")
            val last = sObj.optString("lastName", "")
            val bday = sObj.optLong("birthday", 0L)
            val lastMod = sObj.optLong("lastModified", 0L)

            val identity = "${first.lowercase()}_${last.lowercase()}_$bday"
            if (incomingId != -1) {
                incomingIdToIdentityMap[incomingId] = identity
            }

            val student = StudentEntity(
                firstName = first,
                lastName = last,
                gender = sObj.optString("gender", ""),
                birthday = bday,
                address = sObj.optString("address", ""),
                contactNumber = sObj.optString("contactNumber", ""),
                picturePath = sObj.optString("picturePath", ""),
                guardiansJson = sObj.optString("guardiansJson", "[]"),
                customDataJson = sObj.optString("customDataJson", "{}"),
                lastModified = lastMod,
                className = sObj.optString("class", "") // UPDATED: Maps class key inside synchronizations
            )

            val localMatch = localStudents.find {
                it.firstName.equals(first, ignoreCase = true) &&
                        it.lastName.equals(last, ignoreCase = true) &&
                        it.birthday == bday
            }

            if (localMatch == null) {
                newStudents++
                studentsToInsert.add(student)
            } else {
                if (lastMod > localMatch.lastModified) {
                    updatedStudents++
                    studentsToUpdate.add(student.copy(id = localMatch.id))
                } else {
                    skipped++
                }
            }
        }

        for (i in 0 until incomingLogsArr.length()) {
            val lObj = incomingLogsArr.getJSONObject(i)
            val recordId = lObj.optInt("recordId", -1)
            val dateMillis = lObj.optLong("dateMillis", 0L)
            val incomingStudentId = lObj.optInt("studentId", -1)
            val status = lObj.optString("status", "NOT_SET")
            val lastMod = lObj.optLong("lastModified", 0L)

            if (recordId == -1 || incomingStudentId == -1) continue

            val identity = incomingIdToIdentityMap[incomingStudentId]
            val localStudentId = identity?.let { identityToLocalIdMap[it] } ?: -1

            val log = AttendanceLogEntity(
                recordId = recordId,
                dateMillis = dateMillis,
                studentId = localStudentId,
                status = status,
                lastModified = lastMod
            )

            val localMatch = localLogs.find {
                it.recordId == recordId &&
                        it.dateMillis == dateMillis &&
                        it.studentId == localStudentId
            }

            if (localMatch == null) {
                logsToMerge.add(log)
            } else {
                if (lastMod > localMatch.lastModified) {
                    updatedLogs++
                    logsToMerge.add(log.copy(id = localMatch.id))
                } else {
                    skipped++
                }
            }
        }

        MergeSummary(
            newStudentsCount = newStudents,
            updatedStudentsCount = updatedStudents,
            updatedLogsCount = updatedLogs,
            skippedCount = skipped,
            studentsToInsert = studentsToInsert,
            studentsToUpdate = studentsToUpdate,
            logsToMerge = logsToMerge,
            incomingIdToIdentityMap = incomingIdToIdentityMap
        )
    }

    suspend fun executeMerge(
        repository: StudentRepository,
        summary: MergeSummary
    ) = withContext(Dispatchers.IO) {
        val newlyCreatedIdsMap = mutableMapOf<String, Int>()

        summary.studentsToInsert.forEach { s ->
            val newId = repository.insertStudent(s).toInt()
            val identity = "${s.firstName.lowercase()}_${s.lastName.lowercase()}_${s.birthday}"
            newlyCreatedIdsMap[identity] = newId
        }

        summary.studentsToUpdate.forEach { s ->
            repository.insertStudent(s)
        }

        summary.logsToMerge.forEach { log ->
            var studentIdToUse = log.studentId
            if (studentIdToUse == -1) {
                val key = summary.incomingIdToIdentityMap[log.studentId]
                studentIdToUse = key?.let { newlyCreatedIdsMap[it] } ?: -1
            }

            if (studentIdToUse != -1) {
                repository.insertAttendanceLog(log.copy(studentId = studentIdToUse))
            }
        }
    }

    suspend fun exportSecureBackup(context: Context, students: List<StudentEntity>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        students.forEach { student ->
            val obj = JSONObject().apply {
                put("firstName", student.firstName)
                put("lastName", student.lastName)
                put("gender", student.gender)
                put("birthday", student.birthday)
                put("address", student.address)
                put("contactNumber", student.contactNumber)
                put("picturePath", student.picturePath)
                put("guardiansJson", student.guardiansJson)
                put("customDataJson", student.customDataJson)
                put("class", student.className) // UPDATED: Exports className under key "class"
            }
            array.put(obj)
        }

        val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val tempPlainFile = File(cacheDir, "temp_backup.json")
        FileOutputStream(tempPlainFile).use { it.write(array.toString().toByteArray()) }

        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val finalFile = File(cacheDir, "student_tracker_backup.enc")
        if (finalFile.exists()) finalFile.delete()

        val encryptedFile = EncryptedFile.Builder(
            finalFile,
            context,
            masterKeyAlias,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        val encryptedOutputStream = encryptedFile.openFileOutput()
        val fileInputStream = FileInputStream(tempPlainFile)
        try {
            fileInputStream.copyTo(encryptedOutputStream)
        } finally {
            fileInputStream.close()
            encryptedOutputStream.close()
        }
        tempPlainFile.delete()

        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            finalFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Encrypted Backup"))
    }

    suspend fun importSecureBackup(context: Context, uri: Uri, repository: StudentRepository): Boolean = withContext(Dispatchers.IO) {
        try {
            verifyFileSizeLimit(context, uri)

            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val tempEncFile = File(cacheDir, "student_tracker_backup.enc")
            if (tempEncFile.exists()) tempEncFile.delete()

            context.contentResolver.openInputStream(uri)?.use { stream ->
                FileOutputStream(tempEncFile).use { fos ->
                    stream.copyTo(fos)
                }
            } ?: return@withContext false

            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val encryptedFile = EncryptedFile.Builder(
                tempEncFile,
                context,
                masterKeyAlias,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            val content = encryptedFile.openFileInput().use { decryptedInputStream ->
                decryptedInputStream.readBytes()
            }
            tempEncFile.delete()

            val decryptedString = String(content, Charsets.UTF_8).trim()
            val array = JSONArray(decryptedString)

            parseAndInsertJsonArray(array, repository)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importUnencryptedBackup(context: Context, uri: Uri, repository: StudentRepository): Boolean = withContext(Dispatchers.IO) {
        try {
            verifyFileSizeLimit(context, uri)

            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return@withContext false

            val jsonString = String(content, Charsets.UTF_8).trim()

            val array = if (jsonString.startsWith("[")) {
                JSONArray(jsonString)
            } else {
                JSONArray().apply { put(JSONObject(jsonString)) }
            }

            parseAndInsertJsonArray(array, repository)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun verifyFileSizeLimit(context: Context, uri: Uri) {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    val fileSize = it.getLong(sizeIndex)
                    if (fileSize > MAX_IMPORT_SIZE_BYTES) {
                        throw IOException("Safety threshold exceeded. File size exceeds 10MB limit.")
                    }
                }
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }

    private suspend fun parseAndInsertJsonArray(array: JSONArray, repository: StudentRepository) {
        for (i in 0 until array.length()) {
            val jsonObj = array.getJSONObject(i)

            val rawGuardians = jsonObj.opt("guardiansJson")
            val resolvedGuardiansJson = when (rawGuardians) {
                is JSONArray -> rawGuardians.toString()
                is String -> rawGuardians
                else -> "[]"
            }

            val rawCustomData = jsonObj.opt("customDataJson")
            val resolvedCustomDataJson = when (rawCustomData) {
                is JSONObject -> rawCustomData.toString()
                is String -> rawCustomData
                else -> "{}"
            }

            val student = StudentEntity(
                firstName = jsonObj.optString("firstName", ""),
                lastName = jsonObj.optString("lastName", ""),
                gender = jsonObj.optString("gender", ""),
                birthday = jsonObj.optLong("birthday", 0L),
                address = jsonObj.optString("address", ""),
                contactNumber = jsonObj.optString("contactNumber", ""),
                picturePath = jsonObj.optString("picturePath", ""),
                guardiansJson = resolvedGuardiansJson,
                customDataJson = resolvedCustomDataJson,
                isDeleted = false,
                className = jsonObj.optString("class", "") // UPDATED: Maps JSON "class" -> Room className
            )
            repository.insertStudent(student)
        }
    }
}