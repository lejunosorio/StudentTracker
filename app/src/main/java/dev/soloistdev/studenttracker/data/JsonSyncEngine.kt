package dev.soloistdev.studenttracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns // Resolved: Explicit OpenableColumns import
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

    private const val MAX_IMPORT_SIZE_BYTES = 10 * 1024 * 1024 // Safety threshold boundary: 10MB limit

    // Parses a backup in memory without inserting it to the database [1]
    suspend fun parseBackup(context: Context, uri: Uri): Pair<List<StudentEntity>, List<String>> = withContext(Dispatchers.IO) {
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
        val array = if (jsonString.startsWith("[")) {
            JSONArray(jsonString)
        } else {
            JSONArray().apply { put(JSONObject(jsonString)) }
        }

        val studentList = mutableListOf<StudentEntity>()
        val customKeys = mutableSetOf<String>()

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
                is JSONObject -> {
                    val obj = rawCustomData
                    obj.keys().forEach { customKeys.add(it) }
                    obj.toString()
                }
                is String -> {
                    try {
                        val obj = JSONObject(rawCustomData)
                        obj.keys().forEach { customKeys.add(it) }
                    } catch (_: Exception) {}
                    rawCustomData
                }
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
                isDeleted = false
            )
            studentList.add(student)
        }

        Pair(studentList, customKeys.toList())
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

    // Resolved: Restored missing file name parser utility [1]
    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment // Directly returns the file name segment [1]
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

    // Resolved: Restored missing parsing and insertion loops [1]
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
                isDeleted = false
            )
            repository.insertStudent(student)
        }
    }
}