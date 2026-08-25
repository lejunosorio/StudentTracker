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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data holder class returning database restoration results
data class ImportResult(
    val classroomsCount: Int,
    val studentsCount: Int,
    val filtersCount: Int,
    val attendanceCount: Int,
    val gradebookCount: Int
)

object JsonSyncEngine {

    private const val MAX_IMPORT_SIZE_BYTES = 10 * 1024 * 1024 // 10MB limit

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

    // COMPLETE UNENCRYPTED JSON BACKUP EXPORTER (Allows customizable sharing filenames)
    suspend fun exportBackupJson(context: Context, repository: StudentRepository, customFileName: String? = null) = withContext(Dispatchers.IO) {
        try {
            val activeRoster = repository.getAllActiveStudents()
            val activeLogs = repository.getAllAttendanceLogs()
            val classrooms = repository.getAllClassrooms()
            val filters = repository.getAllSavedFilters()
            val columns = repository.getAllAssessmentColumns()
            val scores = repository.getAllAssessmentScores()

            val sdfDate = SimpleDateFormat("MM-dd-yyyy", Locale.US)

            val payloadObj = JSONObject().apply {
                // 1. Classrooms
                val classroomsArr = JSONArray()
                classrooms.forEach { c ->
                    classroomsArr.put(JSONObject().apply {
                        put("name", c.name)
                        put("start", c.startTime)
                        put("end", c.endTime)
                    })
                }
                put("classrooms", classroomsArr)

                // 2. Students & Relational Behavior Logs
                val studentsArr = JSONArray()
                activeRoster.forEach { s ->
                    val behaviorIncidents = repository.getIncidentsForStudent(s.id)
                    val behaviorArr = JSONArray()
                    behaviorIncidents.forEach { b ->
                        behaviorArr.put(JSONObject().apply {
                            put("title", b.title)
                            put("category", b.category)
                            put("description", b.description)
                            put("date", sdfDate.format(Date(b.incidentDate)))
                        })
                    }

                    studentsArr.put(JSONObject().apply {
                        put("id", s.id)
                        put("firstName", s.firstName)
                        put("lastName", s.lastName)
                        put("gender", s.gender)
                        put("birthday", sdfDate.format(Date(s.birthday)))
                        put("address", s.address)
                        put("contactNumber", s.contactNumber)
                        put("lastModified", s.lastModified)
                        // Backward compatibility: legacy single class string
                        put("classRoom", s.getClassNamesList().firstOrNull() ?: "")
                        put("classNamesJson", JSONArray(s.classNamesJson))
                        put("seatingJson", JSONObject(s.seatingJson))
                        put("guardiansJson", JSONArray(s.guardiansJson))
                        put("customDataJson", JSONObject(s.customDataJson))
                        put("behaviorIncidents", behaviorArr)
                    })
                }
                put("students", studentsArr)

                // 3. Saved Filters (can be empty or legacy)
                put("savedFilters", JSONArray())

                // 4. Attendance Sheets
                val attendanceRecords = repository.getAllAttendanceRecords()
                val attendanceArr = JSONArray()
                attendanceRecords.forEach { r ->
                    val recordLogs = repository.getLogsForRecord(r.id)
                    val rosterStudentIds = recordLogs.map { it.studentId }.distinct()
                    val roster = activeRoster.filter { it.id in rosterStudentIds }

                    attendanceArr.put(JSONObject().apply {
                        put("name", r.name)
                        put("startDate", sdfDate.format(Date(r.startDate)))
                        put("endDate", sdfDate.format(Date(r.endDate)))

                        val participantsArr = JSONArray()
                        roster.forEach { student ->
                            val studentLogs = recordLogs.filter { it.studentId == student.id }
                            val attendanceObj = JSONObject()
                            studentLogs.forEach { log ->
                                attendanceObj.put(sdfDate.format(Date(log.dateMillis)), log.status)
                            }

                            participantsArr.put(JSONObject().apply {
                                put("studentIdentifier", "${student.lastName}_${student.firstName}_${student.getClassNamesList().firstOrNull() ?: ""}")
                                put("attendance", attendanceObj)
                            })
                        }
                        put("participants", participantsArr)
                    })
                }
                put("attendanceRecord", attendanceArr)

                // 5. Gradebook Sheets
                val gradebookArr = JSONArray()
                columns.forEach { col ->
                    val colScores = repository.getScoresForColumn(col.id)
                    val rosterIds = colScores.map { it.studentId }.distinct()
                    val roster = activeRoster.filter { it.id in rosterIds }

                    gradebookArr.put(JSONObject().apply {
                        put("name", col.name)
                        put("maxPoints", col.maxPoints)
                        put("examDate", sdfDate.format(Date(col.examDate)))
                        put("checkDate", sdfDate.format(Date(col.checkDate)))

                        val gradesArr = JSONArray()
                        roster.forEach { student ->
                            val matchedScore = colScores.find { it.studentId == student.id }
                            gradesArr.put(JSONObject().apply {
                                put("studentIdentifier", "${student.lastName}_${student.firstName}_${student.getClassNamesList().firstOrNull() ?: ""}")
                                put("score", matchedScore?.score ?: "")
                            })
                        }
                        put("grades", gradesArr)
                    })
                }
                put("gradeBook", gradebookArr)
            }

            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val baseName = if (customFileName.isNullOrBlank()) "student_tracker_backup" else customFileName.trim()
            val backupFile = File(cacheDir, "$baseName.json")
            if (backupFile.exists()) backupFile.delete()

            FileOutputStream(backupFile).use { fos ->
                fos.write(payloadObj.toString().toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            backupFile.deleteOnExit()

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Share $baseName"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // COHORT-SPECIFIC BACKUP EXPORTER: Filters database schemas relative strictly to the selected classroom cohort
    suspend fun generateClassroomBackupJson(context: Context, repository: StudentRepository, className: String): File? = withContext(Dispatchers.IO) {
        try {
            val activeRoster = repository.getAllActiveStudents().filter { s -> s.getClassNamesList().contains(className) }
            val classStudentIds = activeRoster.map { it.id }.toSet()

            val activeLogs = repository.getAllAttendanceLogs().filter { l -> l.studentId in classStudentIds }
            val activeScores = repository.getAllAssessmentScores().filter { sc -> sc.studentId in classStudentIds }

            val columns = repository.getAllAssessmentColumns().filter { col ->
                activeScores.any { sc -> sc.columnId == col.id }
            }

            val attendanceRecords = repository.getAllAttendanceRecords().filter { rec ->
                activeLogs.any { l -> l.recordId == rec.id }
            }

            val classrooms = repository.getAllClassrooms().filter { c -> c.name == className }

            val sdfDate = SimpleDateFormat("MM-dd-yyyy", Locale.US)

            val payloadObj = JSONObject().apply {
                // Classrooms
                val classroomsArr = JSONArray()
                classrooms.forEach { c ->
                    classroomsArr.put(JSONObject().apply {
                        put("name", c.name)
                        put("start", c.startTime)
                        put("end", c.endTime)
                    })
                }
                put("classrooms", classroomsArr)

                // Students & Behavior Logs
                val studentsArr = JSONArray()
                activeRoster.forEach { s ->
                    val behaviorIncidents = repository.getIncidentsForStudent(s.id)
                    val behaviorArr = JSONArray()
                    behaviorIncidents.forEach { b ->
                        behaviorArr.put(JSONObject().apply {
                            put("title", b.title)
                            put("category", b.category)
                            put("description", b.description)
                            put("date", sdfDate.format(Date(b.incidentDate)))
                        })
                    }

                    studentsArr.put(JSONObject().apply {
                        put("id", s.id)
                        put("firstName", s.firstName)
                        put("lastName", s.lastName)
                        put("gender", s.gender)
                        put("birthday", sdfDate.format(Date(s.birthday)))
                        put("address", s.address)
                        put("contactNumber", s.contactNumber)
                        put("lastModified", s.lastModified)
                        put("picturePath", s.picturePath)
                        put("guardiansJson", s.guardiansJson)
                        put("customDataJson", s.customDataJson)
                        put("classNamesJson", s.classNamesJson)
                        put("seatingJson", s.seatingJson)
                        put("behaviorIncidents", behaviorArr)
                    })
                }
                put("students", studentsArr)

                // Saved Filters (can be empty or legacy)
                put("savedFilters", JSONArray())

                // Attendance Sheets
                val attendanceArr = JSONArray()
                attendanceRecords.forEach { r ->
                    val recordLogs = activeLogs.filter { it.recordId == r.id }
                    val roster = activeRoster.filter { it.id in recordLogs.map { l -> l.studentId } }

                    attendanceArr.put(JSONObject().apply {
                        put("name", r.name)
                        put("startDate", sdfDate.format(Date(r.startDate)))
                        put("endDate", sdfDate.format(Date(r.endDate)))

                        val participantsArr = JSONArray()
                        roster.forEach { student ->
                            val studentLogs = recordLogs.filter { it.studentId == student.id }
                            val attendanceObj = JSONObject()
                            studentLogs.forEach { log ->
                                attendanceObj.put(sdfDate.format(Date(log.dateMillis)), log.status)
                            }

                            participantsArr.put(JSONObject().apply {
                                put("studentIdentifier", "${student.lastName}_${student.firstName}_${student.getClassNamesList().firstOrNull() ?: ""}")
                                put("attendance", attendanceObj)
                            })
                        }
                        put("participants", participantsArr)
                    })
                }
                put("attendanceRecord", attendanceArr)

                // Gradebook Sheets
                val gradebookArr = JSONArray()
                columns.forEach { col ->
                    val colScores = activeScores.filter { it.columnId == col.id }
                    val roster = activeRoster.filter { it.id in colScores.map { cs -> cs.studentId } }

                    gradebookArr.put(JSONObject().apply {
                        put("name", col.name)
                        put("maxPoints", col.maxPoints)
                        put("examDate", sdfDate.format(Date(col.examDate)))
                        put("checkDate", sdfDate.format(Date(col.checkDate)))

                        val gradesArr = JSONArray()
                        roster.forEach { student ->
                            val matchedScore = colScores.find { it.studentId == student.id }
                            gradesArr.put(JSONObject().apply {
                                put("studentIdentifier", "${student.lastName}_${student.firstName}_${student.getClassNamesList().firstOrNull() ?: ""}")
                                put("score", matchedScore?.score ?: "")
                            })
                        }
                        put("grades", gradesArr)
                    })
                }
                put("gradeBook", gradebookArr)
            }

            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val backupFile = File(cacheDir, "classroom_share_${className.replace(" ", "_")}.json")
            if (backupFile.exists()) backupFile.delete()

            FileOutputStream(backupFile).use { fos ->
                fos.write(payloadObj.toString().toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            backupFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

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

        val bdaySdf = SimpleDateFormat("MM-dd-yyyy", Locale.US)

        val incomingClassrooms = mutableSetOf<String>()
        val classroomsArr = payloadObj?.optJSONArray("classrooms")
        if (classroomsArr != null) {
            for (c in 0 until classroomsArr.length()) {
                incomingClassrooms.add(classroomsArr.getJSONObject(c).optString("name", "").trim())
            }
        }
        val dbClassrooms = repository.getAllClassrooms().map { it.name.trim() }.toSet()
        val validClassrooms = dbClassrooms + incomingClassrooms

        for (i in 0 until incomingStudentsArr.length()) {
            val sObj = incomingStudentsArr.getJSONObject(i)
            val incomingId = sObj.optInt("id", -1)
            val first = sObj.optString("firstName", "")
            val last = sObj.optString("lastName", "")

            val bdayStr = sObj.optString("birthday", "")
            val bday = try { bdaySdf.parse(bdayStr)?.time ?: sObj.optLong("birthday", 0L) } catch (_: Exception) { sObj.optLong("birthday", 0L) }

            val lastMod = sObj.optLong("lastModified", 0L)

            val identity = "${first.lowercase()}_${last.lowercase()}_$bday"
            if (incomingId != -1) {
                incomingIdToIdentityMap[incomingId] = identity
            }

            // Fallback Parsing for Multi-Class list
            val classNamesJsonRaw = sObj.opt("classNamesJson")
            val resolvedClassNamesJson = when (classNamesJsonRaw) {
                is JSONArray -> classNamesJsonRaw.toString()
                is String -> classNamesJsonRaw
                else -> {
                    val rawClass = sObj.optString("Classroom", sObj.optString("classRoom", sObj.optString("class", ""))).trim()
                    val validatedClass = if (validClassrooms.contains(rawClass)) rawClass else ""
                    if (validatedClass.isNotEmpty()) JSONArray().put(validatedClass).toString() else "[]"
                }
            }

            // Fallback Parsing for Multi-Class Seating coordinates
            val seatingJsonRaw = sObj.opt("seatingJson")
            val resolvedSeatingJson = when (seatingJsonRaw) {
                is JSONObject -> seatingJsonRaw.toString()
                is String -> seatingJsonRaw
                else -> {
                    val rawClass = sObj.optString("Classroom", sObj.optString("classRoom", sObj.optString("class", ""))).trim()
                    val oldX = sObj.optDouble("seatingX", -1.0).toFloat()
                    val oldY = sObj.optDouble("seatingY", -1.0).toFloat()
                    if (rawClass.isNotEmpty() && oldX >= 0f && oldY >= 0f) {
                        JSONObject().put(rawClass, JSONObject().put("x", oldX.toDouble()).put("y", oldY.toDouble())).toString()
                    } else "{}"
                }
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
                classNamesJson = resolvedClassNamesJson,
                seatingJson = resolvedSeatingJson
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

    suspend fun importSecureBackup(context: Context, uri: Uri, repository: StudentRepository): ImportResult? = withContext(Dispatchers.IO) {
        try {
            verifyFileSizeLimit(context, uri)

            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val tempEncFile = File(cacheDir, "student_tracker_backup.enc")
            if (tempEncFile.exists()) tempEncFile.delete()

            context.contentResolver.openInputStream(uri)?.use { stream ->
                FileOutputStream(tempEncFile).use { fos ->
                    stream.copyTo(fos)
                }
            } ?: return@withContext null

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

            val payloadObj = if (decryptedString.startsWith("{")) {
                JSONObject(decryptedString)
            } else {
                JSONObject().apply { put("students", JSONArray(decryptedString)) }
            }

            parseAndInsertBackupPayload(payloadObj, repository)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun importUnencryptedBackup(context: Context, uri: Uri, repository: StudentRepository): ImportResult? = withContext(Dispatchers.IO) {
        try {
            verifyFileSizeLimit(context, uri)

            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return@withContext null

            val jsonString = String(content, Charsets.UTF_8).trim()

            val payloadObj = if (jsonString.startsWith("{")) {
                JSONObject(jsonString)
            } else {
                JSONObject().apply { put("students", JSONArray(jsonString)) }
            }

            parseAndInsertBackupPayload(payloadObj, repository)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Applies a backup payload as a MERGE rather than a blind insert.
     *
     * Every entity is reconciled against what the database already holds, so importing the same
     * file twice converges on the same roster instead of duplicating it. Students are keyed by
     * first name + last name + birthday; an existing student is refreshed only when the incoming
     * record carries a newer lastModified, so a restore never silently discards newer local
     * edits. Backups written before lastModified was exported resolve to 0 and therefore count
     * as older, which leaves the local copy untouched.
     */
    private suspend fun parseAndInsertBackupPayload(payloadObj: JSONObject, repository: StudentRepository): ImportResult {
        val sdfBday = SimpleDateFormat("MM-dd-yyyy", Locale.US)

        var classroomsLoaded = 0
        var studentsLoaded = 0
        var filtersLoaded = 0
        var attendanceLoaded = 0
        var gradebookLoaded = 0

        val validClassroomNames = mutableSetOf<String>()

        // Classrooms, keyed by name so a re-import refreshes the timetable in place
        val existingClassrooms = repository.getAllClassrooms().associateBy { it.name.trim().lowercase() }
        val classroomsArr = payloadObj.optJSONArray("classrooms")
        if (classroomsArr != null) {
            for (i in 0 until classroomsArr.length()) {
                val cObj = classroomsArr.getJSONObject(i)
                val name = cObj.optString("name", "").trim()
                if (name.isEmpty()) continue

                validClassroomNames.add(name)
                repository.insertClassroom(
                    ClassroomEntity(
                        id = existingClassrooms[name.lowercase()]?.id ?: 0,
                        name = name,
                        startTime = cObj.optString("start", "08:00 AM"),
                        endTime = cObj.optString("end", "04:00 PM")
                    )
                )
                classroomsLoaded++
            }
        }

        repository.getAllClassrooms().forEach {
            validClassroomNames.add(it.name.trim())
        }

        val existingTemplateNames = repository.getAllFormTemplates().map { it.fieldName }.toSet()
        val discoveredTemplates = mutableSetOf<String>()

        // Soft-deleted students are included so a re-import revives them instead of cloning them
        val localStudents = repository.getAllActiveStudents() + repository.getAllDeletedStudents()
        val studentsByIdentity = localStudents.associateBy { identityKey(it.firstName, it.lastName, it.birthday) }

        // Seeded from the current roster so participant rows can resolve students that the
        // payload does not itself carry.
        val studentIdentifierToIdMap = mutableMapOf<String, Int>()
        localStudents.forEach { studentIdentifierToIdMap[participantKey(it)] = it.id }

        // Students and their behavior logs
        val studentsArr = payloadObj.optJSONArray("students") ?: JSONArray()

        for (i in 0 until studentsArr.length()) {
            val sObj = studentsArr.getJSONObject(i)
            val first = sObj.optString("firstName", "")
            val last = sObj.optString("lastName", "")
            val bdayStr = sObj.optString("birthday", "")
            val bdayMillis = try {
                sdfBday.parse(bdayStr)?.time ?: sObj.optLong("birthday", 0L)
            } catch (_: Exception) {
                sObj.optLong("birthday", 0L)
            }
            val incomingLastModified = sObj.optLong("lastModified", 0L)

            val rawGuardians = sObj.opt("guardiansJson")
            val resolvedGuardiansJson = when (rawGuardians) {
                is JSONArray -> rawGuardians.toString()
                is String -> rawGuardians
                else -> "[]"
            }

            val rawCustomData = sObj.opt("customDataJson")
            val resolvedCustomDataJson = when (rawCustomData) {
                is JSONObject -> rawCustomData.toString()
                is String -> rawCustomData
                else -> "{}"
            }

            try {
                val customJson = JSONObject(resolvedCustomDataJson)
                val keys = customJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.isNotBlank() && !existingTemplateNames.contains(key) && !discoveredTemplates.contains(key)) {
                        discoveredTemplates.add(key)
                    }
                }
            } catch (_: Exception) {}

            // Fallback Parsing for Multi-Class list during direct restore operations
            val classNamesJsonRaw = sObj.opt("classNamesJson")
            val resolvedClassNamesJson = when (classNamesJsonRaw) {
                is JSONArray -> classNamesJsonRaw.toString()
                is String -> classNamesJsonRaw
                else -> {
                    val rawClass = sObj.optString("Classroom", sObj.optString("classRoom", sObj.optString("class", ""))).trim()
                    val validatedClass = if (validClassroomNames.contains(rawClass)) rawClass else ""
                    if (validatedClass.isNotEmpty()) JSONArray().put(validatedClass).toString() else "[]"
                }
            }

            // Fallback Parsing for Multi-Class Seating during direct restore operations
            val seatingJsonRaw = sObj.opt("seatingJson")
            val resolvedSeatingJson = when (seatingJsonRaw) {
                is JSONObject -> seatingJsonRaw.toString()
                is String -> seatingJsonRaw
                else -> {
                    val rawClass = sObj.optString("Classroom", sObj.optString("classRoom", sObj.optString("class", ""))).trim()
                    val oldX = sObj.optDouble("seatingX", -1.0).toFloat()
                    val oldY = sObj.optDouble("seatingY", -1.0).toFloat()
                    if (rawClass.isNotEmpty() && oldX >= 0f && oldY >= 0f) {
                        JSONObject().put(rawClass, JSONObject().put("x", oldX.toDouble()).put("y", oldY.toDouble())).toString()
                    } else "{}"
                }
            }

            val student = StudentEntity(
                firstName = first,
                lastName = last,
                gender = sObj.optString("gender", ""),
                birthday = bdayMillis,
                address = sObj.optString("address", ""),
                contactNumber = sObj.optString("contactNumber", ""),
                picturePath = sObj.optString("picturePath", ""),
                guardiansJson = resolvedGuardiansJson,
                customDataJson = resolvedCustomDataJson,
                isDeleted = false,
                classNamesJson = resolvedClassNamesJson,
                seatingJson = resolvedSeatingJson
            )

            val existing = studentsByIdentity[identityKey(first, last, bdayMillis)]
            val resolvedStudentId: Int
            if (existing == null) {
                resolvedStudentId = repository.insertStudent(student).toInt()
                studentsLoaded++
            } else if (incomingLastModified > existing.lastModified) {
                // updateStudent, not insert-with-id: an INSERT OR REPLACE here would delete the
                // row first and cascade away the incidents and scores that point at it.
                repository.updateStudent(student.copy(id = existing.id))
                resolvedStudentId = existing.id
                studentsLoaded++
            } else {
                // Local record is the same age or newer, so it wins. Still resolve the link.
                resolvedStudentId = existing.id
            }

            val firstClass = student.getClassNamesList().firstOrNull() ?: ""
            studentIdentifierToIdMap[participantKey(last, first, firstClass)] = resolvedStudentId

            // Behavior incidents, de-duplicated on title plus date within this student
            val behaviorArr = sObj.optJSONArray("behaviorIncidents")
            if (behaviorArr != null) {
                val seenIncidents = repository.getIncidentsForStudent(resolvedStudentId)
                    .map { "${it.title}|${it.incidentDate}" }
                    .toMutableSet()

                for (b in 0 until behaviorArr.length()) {
                    val bObj = behaviorArr.getJSONObject(b)
                    val title = bObj.optString("title", "")
                    val bDateStr = bObj.optString("date", "")
                    val bDateMillis = try {
                        sdfBday.parse(bDateStr)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }

                    if (!seenIncidents.add("$title|$bDateMillis")) continue

                    repository.insertIncident(
                        BehaviorIncidentEntity(
                            studentId = resolvedStudentId,
                            title = title,
                            category = bObj.optString("category", "Neutral"),
                            description = bObj.optString("description", ""),
                            incidentDate = bDateMillis
                        )
                    )
                }
            }
        }

        // Silent creation of discovered fields
        discoveredTemplates.forEach { field ->
            repository.insertFormTemplate(
                FormTemplateEntity(
                    fieldName = field,
                    fieldType = "TEXT",
                    isRequired = false
                )
            )
        }

        // Saved Filters, keyed by name
        val existingFilters = repository.getAllSavedFilters().associateBy { it.filterName.trim().lowercase() }
        val filtersArr = payloadObj.optJSONArray("savedFilters")
        if (filtersArr != null) {
            for (i in 0 until filtersArr.length()) {
                val fObj = filtersArr.getJSONObject(i)
                val name = fObj.optString("name", "")
                val rawField = fObj.optString("field", fObj.optString("fieldName", ""))
                val field = if (rawField == "classRoom" || rawField == "Classroom") "Classroom" else rawField

                var comp = fObj.optString("comparison", "equal")
                val bdayFilterType = fObj.optString("birthdayFilterType", "")
                if (bdayFilterType.isNotEmpty()) {
                    comp = when (bdayFilterType) {
                        "BirthMonth" -> "birth_month"
                        "BirthYear" -> "birth_year"
                        else -> "exact_birthday"
                    }
                }

                repository.insertSavedFilter(
                    SavedFilterEntity(
                        id = existingFilters[name.trim().lowercase()]?.id ?: 0,
                        filterName = name,
                        fieldName = field,
                        comparison = comp,
                        value1 = fObj.optString("value1", fObj.optString("value", "")),
                        value2 = fObj.optString("value2", ""),
                        displayOrder = fObj.optInt("displayOrder", i)
                    )
                )
                filtersLoaded++
            }
        }

        // Attendance sheets, keyed by name plus date range; logs upserted per student and day
        val existingRecords = repository.getAllAttendanceRecords()
        val attendanceArr = payloadObj.optJSONArray("attendanceRecord")
        if (attendanceArr != null) {
            for (i in 0 until attendanceArr.length()) {
                val rObj = attendanceArr.getJSONObject(i)
                val rName = rObj.optString("name", "")
                val startMillis = try { sdfBday.parse(rObj.optString("startDate", ""))?.time ?: 0L } catch (_: Exception) { 0L }
                val endMillis = try { sdfBday.parse(rObj.optString("endDate", ""))?.time ?: 0L } catch (_: Exception) { 0L }

                val existingRecord = existingRecords.find {
                    it.name == rName && it.startDate == startMillis && it.endDate == endMillis
                }
                val recordId = existingRecord?.id ?: repository.insertAttendanceRecord(
                    AttendanceRecordEntity(
                        name = rName,
                        savedFilterId = 0,
                        startDate = startMillis,
                        endDate = endMillis
                    )
                ).toInt()
                attendanceLoaded++

                val existingLogs = if (existingRecord != null) repository.getLogsForRecord(recordId) else emptyList()

                val participantsArr = rObj.optJSONArray("participants")
                if (participantsArr != null) {
                    for (p in 0 until participantsArr.length()) {
                        val pObj = participantsArr.getJSONObject(p)
                        val localStudentId = studentIdentifierToIdMap[pObj.optString("studentIdentifier", "")] ?: -1
                        if (localStudentId == -1) continue

                        val attendanceLogsObj = pObj.optJSONObject("attendance") ?: continue
                        val logDates = attendanceLogsObj.keys()
                        while (logDates.hasNext()) {
                            val dateStr = logDates.next()
                            val logDateMillis = try { sdfBday.parse(dateStr)?.time ?: 0L } catch (_: Exception) { 0L }
                            val priorLog = existingLogs.find {
                                it.dateMillis == logDateMillis && it.studentId == localStudentId
                            }

                            repository.insertAttendanceLog(
                                AttendanceLogEntity(
                                    id = priorLog?.id ?: 0,
                                    recordId = recordId,
                                    dateMillis = logDateMillis,
                                    studentId = localStudentId,
                                    status = attendanceLogsObj.optString(dateStr, "NOT_SET").uppercase()
                                )
                            )
                        }
                    }
                }
            }
        }

        // Gradebook sheets, keyed by name plus exam date; scores upserted per student
        val existingColumns = repository.getAllAssessmentColumns()
        val gradebookArr = payloadObj.optJSONArray("gradeBook")
        if (gradebookArr != null) {
            for (i in 0 until gradebookArr.length()) {
                val gObj = gradebookArr.getJSONObject(i)
                val gName = gObj.optString("name", "")
                val examMillis = try {
                    sdfBday.parse(gObj.optString("examDate", ""))?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
                val checkMillis = try {
                    sdfBday.parse(gObj.optString("checkDate", ""))?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }

                val existingColumn = existingColumns.find { it.name == gName && it.examDate == examMillis }
                val columnId = existingColumn?.id ?: repository.insertAssessmentColumn(
                    AssessmentColumnEntity(
                        name = gName,
                        maxPoints = gObj.optDouble("maxPoints", 100.0),
                        examDate = examMillis,
                        checkDate = checkMillis,
                        savedFilterId = 0
                    )
                ).toInt()
                gradebookLoaded++

                val existingScores = if (existingColumn != null) repository.getScoresForColumn(columnId) else emptyList()

                val gradesArr = gObj.optJSONArray("grades")
                if (gradesArr != null) {
                    for (g in 0 until gradesArr.length()) {
                        val scoreObj = gradesArr.getJSONObject(g)
                        val localStudentId = studentIdentifierToIdMap[scoreObj.optString("studentIdentifier", "")] ?: -1
                        if (localStudentId == -1) continue

                        val priorScore = existingScores.find { it.studentId == localStudentId }
                        repository.insertAssessmentScore(
                            AssessmentScoreEntity(
                                id = priorScore?.id ?: 0,
                                columnId = columnId,
                                studentId = localStudentId,
                                score = scoreObj.optString("score", "")
                            )
                        )
                    }
                }
            }
        }

        return ImportResult(
            classroomsCount = classroomsLoaded,
            studentsCount = studentsLoaded,
            filtersCount = filtersLoaded,
            attendanceCount = attendanceLoaded,
            gradebookCount = gradebookLoaded
        )
    }

    // Match key for a student across devices: names are case-folded, birthday is exact.
    private fun identityKey(firstName: String, lastName: String, birthday: Long): String =
        "${firstName.trim().lowercase()}_${lastName.trim().lowercase()}_$birthday"

    // Match key used by attendance and gradebook rows, mirroring the exporter format.
    private fun participantKey(lastName: String, firstName: String, className: String): String =
        "${lastName}_${firstName}_${className}"

    private fun participantKey(student: StudentEntity): String =
        participantKey(student.lastName, student.firstName, student.getClassNamesList().firstOrNull() ?: "")

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
}