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

    // COMPLETE UNENCRYPTED JSON BACKUP EXPORTER (RE-ARCHITECTED) [1]
    suspend fun exportBackupJson(context: Context, repository: StudentRepository) = withContext(Dispatchers.IO) {
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
                        put("firstName", s.firstName)
                        put("lastName", s.lastName)
                        put("gender", s.gender)
                        put("birthday", sdfDate.format(Date(s.birthday)))
                        put("address", s.address)
                        put("contactNumber", s.contactNumber)
                        put("classRoom", s.className)
                        put("guardiansJson", JSONArray(s.guardiansJson))
                        put("customDataJson", JSONObject(s.customDataJson))
                        put("behaviorIncidents", behaviorArr)
                    })
                }
                put("students", studentsArr)

                // 3. Saved Filters
                val filtersArr = JSONArray()
                filters.forEach { f ->
                    filtersArr.put(JSONObject().apply {
                        put("name", f.filterName)
                        put("field", f.fieldName)
                        put("comparison", f.comparison)
                        put("value", f.value1)
                        put("value2", f.value2)
                        put("displayOrder", f.displayOrder)
                    })
                }
                put("savedFilters", filtersArr)

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
                                put("studentIdentifier", "${student.lastName}_${student.firstName}_${student.className}")
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
                                put("studentIdentifier", "${student.lastName}_${student.firstName}_${student.className}")
                                put("score", matchedScore?.score ?: "")
                            })
                        }
                        put("grades", gradesArr)
                    })
                }
                put("gradeBook", gradebookArr)
            }

            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val backupFile = File(cacheDir, "student_tracker_backup.json")
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
                context.startActivity(Intent.createChooser(shareIntent, "Share JSON Backup"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

        // Reconciles incoming payload classes to prevent data-loss on clean merges
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

            // Boundary validation check: Nullify className if the class profile is unregistered
            val rawClass = sObj.optString("Classroom", sObj.optString("classRoom", sObj.optString("class", ""))).trim()
            val validatedClass = if (validClassrooms.contains(rawClass)) rawClass else ""

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
                className = validatedClass
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

    private suspend fun parseAndInsertBackupPayload(payloadObj: JSONObject, repository: StudentRepository): ImportResult {
        val sdfBday = SimpleDateFormat("MM-dd-yyyy", Locale.US)

        var classroomsLoaded = 0
        var studentsLoaded = 0
        var filtersLoaded = 0
        var attendanceLoaded = 0
        var gradebookLoaded = 0

        val validClassroomNames = mutableSetOf<String>()

        // 1. Classrooms
        val classroomsArr = payloadObj.optJSONArray("classrooms")
        if (classroomsArr != null) {
            for (i in 0 until classroomsArr.length()) {
                val cObj = classroomsArr.getJSONObject(i)
                val name = cObj.optString("name", "").trim()
                val start = cObj.optString("start", "08:00 AM")
                val end = cObj.optString("end", "04:00 PM")

                if (name.isNotEmpty()) {
                    validClassroomNames.add(name)
                    val classroom = ClassroomEntity(
                        name = name,
                        startTime = start,
                        endTime = end
                    )
                    repository.insertClassroom(classroom)
                    classroomsLoaded++
                }
            }
        }

        repository.getAllClassrooms().forEach {
            validClassroomNames.add(it.name.trim())
        }

        val existingTemplateNames = repository.getAllFormTemplates().map { it.fieldName }.toSet()
        val discoveredTemplates = mutableSetOf<String>()

        // 2. Students & Relational Behavior Logs
        val studentsArr = payloadObj.optJSONArray("students") ?: JSONArray()
        val studentIdentifierToIdMap = mutableMapOf<String, Int>()

        for (i in 0 until studentsArr.length()) {
            val sObj = studentsArr.getJSONObject(i)
            val first = sObj.optString("firstName", "")
            val last = sObj.optString("lastName", "")
            val bdayStr = sObj.optString("birthday", "")
            val bdayMillis = try { sdfBday.parse(bdayStr)?.time ?: 0L } catch (_: Exception) { 0L }

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

            // Relational classroom bound validations
            val rawClass = sObj.optString("Classroom", sObj.optString("classRoom", sObj.optString("class", ""))).trim()
            val validatedClass = if (validClassroomNames.contains(rawClass)) rawClass else ""

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
                className = validatedClass
            )
            val newStudentId = repository.insertStudent(student).toInt()
            studentsLoaded++

            val identifier = "${last}_${first}_${student.className}"
            studentIdentifierToIdMap[identifier] = newStudentId

            val behaviorArr = sObj.optJSONArray("behaviorIncidents")
            if (behaviorArr != null) {
                for (b in 0 until behaviorArr.length()) {
                    val bObj = behaviorArr.getJSONObject(b)
                    val title = bObj.optString("title", "")
                    val cat = bObj.optString("category", "Neutral")
                    val desc = bObj.optString("description", "")
                    val bDateStr = bObj.optString("date", "")
                    val bDateMillis = try { sdfBday.parse(bDateStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }

                    val incident = BehaviorIncidentEntity(
                        studentId = newStudentId,
                        title = title,
                        category = cat,
                        description = desc,
                        incidentDate = bDateMillis
                    )
                    repository.insertIncident(incident)
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

        // 3. Saved Filters
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

                val filter = SavedFilterEntity(
                    filterName = name,
                    fieldName = field,
                    comparison = comp,
                    value1 = fObj.optString("value1", fObj.optString("value", "")),
                    value2 = fObj.optString("value2", ""),
                    displayOrder = fObj.optInt("displayOrder", i)
                )
                repository.insertSavedFilter(filter)
                filtersLoaded++
            }
        }

        // 4. Attendance Sheets & Log matrix
        val attendanceArr = payloadObj.optJSONArray("attendanceRecord")
        if (attendanceArr != null) {
            for (i in 0 until attendanceArr.length()) {
                val rObj = attendanceArr.getJSONObject(i)
                val rName = rObj.optString("name", "")
                val startStr = rObj.optString("startDate", "")
                val endStr = rObj.optString("endDate", "")

                val startMillis = try { sdfBday.parse(startStr)?.time ?: 0L } catch (_: Exception) { 0L }
                val endMillis = try { sdfBday.parse(endStr)?.time ?: 0L } catch (_: Exception) { 0L }

                val record = AttendanceRecordEntity(
                    name = rName,
                    savedFilterId = 0,
                    startDate = startMillis,
                    endDate = endMillis
                )
                val recordId = repository.insertAttendanceRecord(record).toInt()
                attendanceLoaded++

                val participantsArr = rObj.optJSONArray("participants")
                if (participantsArr != null) {
                    for (p in 0 until participantsArr.length()) {
                        val pObj = participantsArr.getJSONObject(p)
                        val identifier = pObj.optString("studentIdentifier", "")
                        val localStudentId = studentIdentifierToIdMap[identifier] ?: -1

                        if (localStudentId != -1) {
                            val attendanceLogsObj = pObj.optJSONObject("attendance")
                            if (attendanceLogsObj != null) {
                                val logDates = attendanceLogsObj.keys()
                                while (logDates.hasNext()) {
                                    val dateStr = logDates.next()
                                    val logStatus = attendanceLogsObj.optString(dateStr, "NOT_SET")
                                    val logDateMillis = try { sdfBday.parse(dateStr)?.time ?: 0L } catch (_: Exception) { 0L }

                                    val log = AttendanceLogEntity(
                                        recordId = recordId,
                                        dateMillis = logDateMillis,
                                        studentId = localStudentId,
                                        status = logStatus.uppercase()
                                    )
                                    repository.insertAttendanceLog(log)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Gradebook Sheets & Score matrix
        val gradebookArr = payloadObj.optJSONArray("gradeBook")
        if (gradebookArr != null) {
            for (i in 0 until gradebookArr.length()) {
                val gObj = gradebookArr.getJSONObject(i)
                val gName = gObj.optString("name", "")
                val maxPoints = gObj.optDouble("maxPoints", 100.0)
                val examStr = gObj.optString("examDate", "")
                val checkStr = gObj.optString("checkDate", "")

                val examMillis = try { sdfBday.parse(examStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                val checkMillis = try { sdfBday.parse(checkStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }

                val column = AssessmentColumnEntity(
                    name = gName,
                    maxPoints = maxPoints,
                    examDate = examMillis,
                    checkDate = checkMillis,
                    savedFilterId = 0
                )
                val columnId = repository.insertAssessmentColumn(column).toInt()
                gradebookLoaded++

                val gradesArr = gObj.optJSONArray("grades")
                if (gradesArr != null) {
                    for (g in 0 until gradesArr.length()) {
                        val scoreObj = gradesArr.getJSONObject(g)
                        val identifier = scoreObj.optString("studentIdentifier", "")
                        val localStudentId = studentIdentifierToIdMap[identifier] ?: -1

                        if (localStudentId != -1) {
                            val scoreStr = scoreObj.optString("score", "")
                            val scoreEntity = AssessmentScoreEntity(
                                columnId = columnId,
                                studentId = localStudentId,
                                score = scoreStr
                            )
                            repository.insertAssessmentScore(scoreEntity)
                        }
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