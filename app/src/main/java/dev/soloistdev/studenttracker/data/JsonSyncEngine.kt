package dev.soloistdev.studenttracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
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

object JsonSyncEngine {

    suspend fun exportSecureBackup(context: Context, students: List<StudentEntity>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        students.forEach { student ->
            val obj = JSONObject().apply {
                put("firstName", student.firstName)
                put("lastName", student.lastName)
                put("gender", student.gender)
                put("birthday", student.birthday)
                put("address", student.address)
                put("picturePath", student.picturePath) // Preserves images on export
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

    /**
     * Helper to robustly parse JSON arrays containing students.
     * Accommodates both stringified JSON data and standard nested JSON arrays/objects.
     */
    private suspend fun parseAndInsertJsonArray(array: JSONArray, repository: StudentRepository) {
        for (i in 0 until array.length()) {
            val jsonObj = array.getJSONObject(i)

            // Resolve guardiansJson: Handles both stringified arrays & raw nested JSON arrays
            val rawGuardians = jsonObj.opt("guardiansJson")
            val resolvedGuardiansJson = when (rawGuardians) {
                is JSONArray -> rawGuardians.toString()
                is String -> rawGuardians
                else -> "[]"
            }

            // Resolve customDataJson: Handles both stringified maps & raw nested JSON objects
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
                picturePath = jsonObj.optString("picturePath", ""), // Safely maps picture path to prevent sync loss
                guardiansJson = resolvedGuardiansJson,
                customDataJson = resolvedCustomDataJson,
                isDeleted = false
            )
            repository.insertStudent(student)
        }
    }

    /**
     * Standard secure backup import (Decrypted via Jetpack Security)
     */
    suspend fun importSecureBackup(context: Context, uri: Uri, repository: StudentRepository): Boolean = withContext(Dispatchers.IO) {
        try {
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

    /**
     * New function to import plain-text standard .json files (no decryption required)
     */
    suspend fun importUnencryptedBackup(context: Context, uri: Uri, repository: StudentRepository): Boolean = withContext(Dispatchers.IO) {
        try {
            // JVM Auto-Closeable implementation to securely prevent file descriptor leaks
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return@withContext false

            val jsonString = String(content, Charsets.UTF_8).trim()

            // Gracefully handles both a JSON array or a single JSON object wrapped as an array
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
}