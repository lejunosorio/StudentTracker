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
import java.io.InputStream

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

        // FIXED: Changed from openFileOutputStream() to openFileOutput()
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
            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val tempEncFile = File(cacheDir, "temp_import.enc")
            if (tempEncFile.exists()) tempEncFile.delete()

            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val fos = FileOutputStream(tempEncFile)
            inputStream?.copyTo(fos)
            fos.close()
            inputStream?.close()

            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val encryptedFile = EncryptedFile.Builder(
                tempEncFile,
                context,
                masterKeyAlias,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            val decryptedInputStream = encryptedFile.openFileInput()
            val content = decryptedInputStream.readBytes()
            decryptedInputStream.close()
            tempEncFile.delete()

            val decryptedString = String(content, Charsets.UTF_8).trim()
            val array = JSONArray(decryptedString)
            for (i in 0 until array.length()) {
                val jsonObj = array.getJSONObject(i)
                val student = StudentEntity(
                    firstName = jsonObj.getString("firstName"),
                    lastName = jsonObj.getString("lastName"),
                    gender = jsonObj.getString("gender"),
                    birthday = jsonObj.getLong("birthday"),
                    address = jsonObj.getString("address"),
                    guardiansJson = jsonObj.optString("guardiansJson", "[]"),
                    customDataJson = jsonObj.getString("customDataJson")
                )
                repository.insertStudent(student)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}