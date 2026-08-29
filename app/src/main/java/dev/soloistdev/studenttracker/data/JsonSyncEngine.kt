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

    /** One place that knows how a device-bound file is sealed, so write and read cannot drift. */
    private fun encryptedFileFor(context: Context, file: File): EncryptedFile =
        EncryptedFile.Builder(
            file,
            context,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

    /**
     * The same document as [writeBackupTo], sealed under the hardware-backed key.
     *
     * Used for the rolling snapshots, which sit on disk between sessions. A snapshot holds the
     * whole roster in the clear otherwise - names, birthdays, addresses and guardian numbers of
     * minors - which would undo the point of encrypting the database it was copied out of.
     */
    suspend fun writeEncryptedBackupTo(
        context: Context,
        repository: StudentRepository,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = buildBackupPayload(repository).toString().toByteArray(Charsets.UTF_8)
            targetFile.parentFile?.mkdirs()
            // EncryptedFile refuses to write over an existing file
            if (targetFile.exists()) targetFile.delete()
            encryptedFileFor(context, targetFile).openFileOutput().use { out ->
                out.write(payload)
                out.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (targetFile.exists()) targetFile.delete()
            false
        }
    }

    /** Seals already-built JSON text into [targetFile]. Used to retro-seal legacy snapshots. */
    suspend fun sealTextTo(context: Context, text: String, targetFile: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                targetFile.parentFile?.mkdirs()
                if (targetFile.exists()) targetFile.delete()
                encryptedFileFor(context, targetFile).openFileOutput().use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                if (targetFile.exists()) targetFile.delete()
                false
            }
        }

    /**
     * Reads a snapshot from local storage, sealed or not, and returns its JSON text.
     *
     * Snapshots written before backups were encrypted are still plain JSON on disk, and a
     * teacher's disaster recovery should not depend on which version of the app wrote the file.
     */
    suspend fun readLocalBackup(context: Context, file: File): String = withContext(Dispatchers.IO) {
        if (file.length() > MAX_IMPORT_SIZE_BYTES) {
            throw IOException("Safety threshold exceeded. File size exceeds 10MB limit.")
        }
        if (file.name.endsWith(ENCRYPTED_SUFFIX)) {
            encryptedFileFor(context, file).openFileInput().use { it.readBytes() }
                .toString(Charsets.UTF_8)
        } else {
            file.readText(Charsets.UTF_8)
        }
    }

    /** Merges a snapshot held in the app's own storage. Handles both the sealed and legacy forms. */
    suspend fun importLocalBackup(
        context: Context,
        file: File,
        repository: StudentRepository
    ): ImportResult? = withContext(Dispatchers.IO) {
        try {
            parseAndInsertBackupPayload(asPayloadObject(readLocalBackup(context, file)), repository)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Suffix marking a snapshot sealed under the device key. */
    const val ENCRYPTED_SUFFIX = ".enc"

    /**
     * Accepts either the current object form or the original bare students array, so files
     * written by any past version still import.
     */
    internal fun asPayloadObject(raw: String): JSONObject {
        // The byte order mark is stripped before anything is decided. It is not whitespace, so
        // trim leaves it in place, and a single invisible character at the front is enough to make
        // the check below miss the '{' - at which point a perfectly good backup is read as a
        // legacy array, fails to parse, and is reported as corrupt. Any file that has been through
        // a Windows editor can carry one, and the moment a teacher restores a backup is the worst
        // possible moment to reject it.
        val trimmed = raw.trim().removePrefix("﻿").trim()
        return if (trimmed.startsWith("{")) {
            JSONObject(trimmed)
        } else {
            JSONObject().apply { put("students", JSONArray(trimmed)) }
        }
    }

    /** The canonical backup document. One builder, so every export path stays in step. */
    suspend fun buildBackupPayload(repository: StudentRepository): JSONObject = withContext(Dispatchers.IO) {
        val activeRoster = repository.getAllActiveStudents()
        val classrooms = repository.getAllClassrooms()
        val columns = repository.getAllAssessmentColumns()

        val sdfDate = SimpleDateFormat("MM-dd-yyyy", Locale.US)

        JSONObject().apply {
            val classroomsArr = JSONArray()
            classrooms.forEach { c ->
                classroomsArr.put(JSONObject().apply {
                    put("name", c.name)
                    put("start", c.startTime)
                    put("end", c.endTime)
                    put("meetingDays", c.meetingDays)
                })
            }
            put("classrooms", classroomsArr)

            val contactLogByStudent = repository.getAllContactLog().groupBy { it.studentId }

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
                        put("actionTaken", b.actionTaken)
                        put("resolvedAt", b.resolvedAt)
                    })
                }

                val contactArr = JSONArray()
                contactLogByStudent[s.id].orEmpty().forEach { entry ->
                    contactArr.put(JSONObject().apply {
                        put("guardianName", entry.guardianName)
                        put("phone", entry.phone)
                        put("channel", entry.channel)
                        put("templateName", entry.templateName)
                        put("body", entry.body)
                        put("sentAt", entry.sentAt)
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
                    put("classRoom", s.getClassNamesList().firstOrNull() ?: "")
                    put("classNamesJson", JSONArray(s.classNamesJson))
                    put("seatingJson", JSONObject(s.seatingJson))
                    put("guardiansJson", JSONArray(s.guardiansJson))
                    put("customDataJson", JSONObject(s.customDataJson))
                    put("behaviorIncidents", behaviorArr)
                    put("contactLog", contactArr)
                    // picturePath is absent on purpose: photos live on the device that took them.
                })
            }
            put("students", studentsArr)

            // Everything in this block used to be absent from the document, so a restore quietly
            // dropped the teacher's custom fields, saved filters, grading periods, category
            // weights, rubrics and message templates. Cross-references are carried by name, not
            // by row id, because ids are local to whichever device wrote the file.
            val formTemplatesArr = JSONArray()
            repository.getAllFormTemplates().forEach { t ->
                formTemplatesArr.put(JSONObject().apply {
                    put("fieldName", t.fieldName)
                    put("fieldType", t.fieldType)
                    put("isRequired", t.isRequired)
                    put("options", JSONArray(t.optionsJson))
                })
            }
            put("formTemplates", formTemplatesArr)

            val savedFiltersArr = JSONArray()
            repository.getAllSavedFilters().forEach { f ->
                savedFiltersArr.put(JSONObject().apply {
                    put("name", f.filterName)
                    put("field", f.fieldName)
                    put("comparison", f.comparison)
                    put("value1", f.value1)
                    put("value2", f.value2)
                    put("displayOrder", f.displayOrder)
                })
            }
            put("savedFilters", savedFiltersArr)

            val terms = repository.getAllGradingTerms()
            val termsArr = JSONArray()
            terms.forEach { t ->
                termsArr.put(JSONObject().apply {
                    put("name", t.name)
                    put("startDate", sdfDate.format(Date(t.startDate)))
                    put("endDate", sdfDate.format(Date(t.endDate)))
                    put("isActive", t.isActive)
                })
            }
            put("gradingTerms", termsArr)

            val termNameById = terms.associate { it.id to it.name }
            val categories = repository.getAllAssessmentCategories()
            val categoriesArr = JSONArray()
            categories.forEach { c ->
                categoriesArr.put(JSONObject().apply {
                    put("name", c.name)
                    put("weight", c.weight)
                    put("term", termNameById[c.termId] ?: "")
                })
            }
            put("assessmentCategories", categoriesArr)

            val rubrics = repository.getAllRubrics()
            val allLevels = repository.getAllRubricLevels()
            val rubricsArr = JSONArray()
            rubrics.forEach { r ->
                val levelsArr = JSONArray()
                allLevels.filter { it.rubricId == r.id }.forEach { level ->
                    levelsArr.put(JSONObject().apply {
                        put("label", level.label)
                        put("points", level.points)
                        put("descriptor", level.descriptor)
                        put("order", level.displayOrder)
                    })
                }
                rubricsArr.put(JSONObject().apply {
                    put("name", r.name)
                    put("levels", levelsArr)
                })
            }
            put("rubrics", rubricsArr)

            val messageTemplatesArr = JSONArray()
            repository.getAllMessageTemplates().forEach { t ->
                messageTemplatesArr.put(JSONObject().apply {
                    put("name", t.name)
                    put("text", t.text)
                })
            }
            put("messageTemplates", messageTemplatesArr)

            val participationArr = JSONArray()
            classrooms.forEach { classroom ->
                repository.getParticipationForClass(classroom.name).forEach { entry ->
                    val student = activeRoster.find { it.id == entry.studentId } ?: return@forEach
                    participationArr.put(JSONObject().apply {
                        put("studentIdentifier", participantKey(student))
                        put("className", entry.className)
                        put("timesCalled", entry.timesCalled)
                        put("lastCalled", entry.lastCalledMillis)
                    })
                }
            }
            put("participation", participationArr)

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
                    put("dueDate", if (col.dueDate > 0L) sdfDate.format(Date(col.dueDate)) else "")
                    // By name: which grading period this assessment belongs to, which weighted
                    // bucket it counts toward, and which marking scale it is graded against.
                    put("term", termNameById[col.termId] ?: "")
                    put("category", categories.find { it.id == col.categoryId }?.name ?: "")
                    put("rubric", rubrics.find { it.id == col.rubricId }?.name ?: "")

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
    }


    /** Marks a payload as a scoped substitute handoff rather than a full backup. */
    const val SCOPE_SUBSTITUTE = "substitute"

    /**
     * Builds a minimal packet for handing one class to a substitute teacher.
     *
     * Deliberately omits home addresses, guardian names and phone numbers, student contact
     * numbers, behaviour notes and grades. A substitute needs to know who is in the room and
     * where they sit; nothing else is theirs to hold, and a device that leaves your control
     * should carry as little as the job allows.
     */
    suspend fun buildSubstitutePacket(
        repository: StudentRepository,
        className: String,
        record: AttendanceRecordEntity,
        dateMillis: Long
    ): JSONObject = withContext(Dispatchers.IO) {
        val sdfDate = SimpleDateFormat("MM-dd-yyyy", Locale.US)
        val roster = repository.getAllActiveStudents()
            .filter { it.getClassNamesList().contains(className) }
        val rosterIds = roster.map { it.id }.toSet()
        val logs = repository.getLogsForDate(record.id, dateMillis).filter { it.studentId in rosterIds }

        JSONObject().apply {
            put("scope", SCOPE_SUBSTITUTE)

            val classroomsArr = JSONArray()
            repository.getAllClassrooms().filter { it.name == className }.forEach { c ->
                classroomsArr.put(JSONObject().apply {
                    put("name", c.name)
                    put("start", c.startTime)
                    put("end", c.endTime)
                })
            }
            put("classrooms", classroomsArr)

            val studentsArr = JSONArray()
            roster.forEach { s ->
                studentsArr.put(JSONObject().apply {
                    put("id", s.id)
                    put("firstName", s.firstName)
                    put("lastName", s.lastName)
                    put("gender", s.gender)
                    put("birthday", sdfDate.format(Date(s.birthday)))
                    put("lastModified", s.lastModified)
                    put("classRoom", className)
                    put("classNamesJson", JSONArray(s.classNamesJson))
                    put("seatingJson", JSONObject(s.seatingJson))
                    // address, contactNumber, guardiansJson and customDataJson intentionally absent
                })
            }
            put("students", studentsArr)

            put("savedFilters", JSONArray())
            put("gradeBook", JSONArray())

            val attendanceArr = JSONArray()
            attendanceArr.put(JSONObject().apply {
                put("name", record.name)
                put("startDate", sdfDate.format(Date(record.startDate)))
                put("endDate", sdfDate.format(Date(record.endDate)))

                val participantsArr = JSONArray()
                roster.forEach { student ->
                    val attendanceObj = JSONObject()
                    logs.filter { it.studentId == student.id }.forEach { log ->
                        attendanceObj.put(sdfDate.format(Date(log.dateMillis)), log.status)
                    }
                    participantsArr.put(JSONObject().apply {
                        put("studentIdentifier", "${student.lastName}_${student.firstName}_$className")
                        put("attendance", attendanceObj)
                    })
                }
                put("participants", participantsArr)
            })
            put("attendanceRecord", attendanceArr)
        }
    }

    /**
     * Writes the backup as an AES-GCM encrypted .enc file and offers it for sharing.
     *
     * The counterpart to importSecureBackup, which already existed. Without this the encrypted
     * format was documented in the help text and produced by nothing, so the only way to obtain
     * a .enc file was to already have one.
     *
     * The key lives in the hardware-backed keystore, so a .enc file is readable only on the
     * device that wrote it. That is the point for a stolen-phone threat model, and exactly why
     * it is the wrong choice for moving a roster between devices - use the plain JSON for that.
     */
    suspend fun exportEncryptedBackup(
        context: Context,
        repository: StudentRepository,
        customFileName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = buildBackupPayload(repository).toString().toByteArray(Charsets.UTF_8)

            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val baseName = if (customFileName.isNullOrBlank()) "student_tracker_backup" else customFileName.trim()
            val target = File(cacheDir, "$baseName.enc")
            // EncryptedFile refuses to write over an existing file
            if (target.exists()) target.delete()

            encryptedFileFor(context, target).openFileOutput().use { out ->
                out.write(payload)
                out.flush()
            }
            target.deleteOnExit()

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Share $baseName.enc"))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    /**
     * Shares the backup as plain JSON, for moving a roster to another device.
     *
     * Built by [buildBackupPayload] like every other export path. This used to assemble the whole
     * document a second time by hand, so anything added to the backup was silently missing here
     * until someone noticed.
     */
    suspend fun exportBackupJson(context: Context, repository: StudentRepository, customFileName: String? = null) = withContext(Dispatchers.IO) {
        try {
            val payloadObj = buildBackupPayload(repository)

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

            val content = encryptedFileFor(context, tempEncFile).openFileInput().use { decryptedInputStream ->
                decryptedInputStream.readBytes()
            }
            tempEncFile.delete()

            parseAndInsertBackupPayload(asPayloadObject(String(content, Charsets.UTF_8)), repository)
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

            parseAndInsertBackupPayload(asPayloadObject(String(content, Charsets.UTF_8)), repository)
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

        // A substitute packet carries only enough of each student to identify them - no address,
        // no guardians, no contact number. Letting it update an existing record would therefore
        // blank exactly the fields it deliberately omitted, so scoped packets may create students
        // but never modify ones already on this device.
        val isScopedHandoff = payloadObj.optString("scope", "") == SCOPE_SUBSTITUTE

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
                        endTime = cObj.optString("end", "04:00 PM"),
                        meetingDays = cObj.optString("meetingDays", "")
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
                // Deliberately not read from the payload. A picturePath is a file path on the
                // device that wrote it, so importing one elsewhere produces a student whose photo
                // is a broken link. Photos are device-local; the app says so where it matters.
                picturePath = "",
                guardiansJson = resolvedGuardiansJson,
                customDataJson = resolvedCustomDataJson,
                isDeleted = false,
                classNamesJson = resolvedClassNamesJson,
                seatingJson = resolvedSeatingJson
            )

            val existing = studentsByIdentity[identityKey(first, last, bdayMillis)]
            val resolvedStudentId: Int
            if (existing == null) {
                resolvedStudentId = repository.saveStudent(student)
                studentsLoaded++
            } else if (!isScopedHandoff && incomingLastModified > existing.lastModified) {
                // Carry the peer's timestamp across rather than stamping the merge time. Stamping
                // now would make this device look newer than every peer, so the next sync in the
                // other direction would decline to bring anything back.
                repository.saveStudent(
                    student.copy(
                        id = existing.id,
                        lastModified = incomingLastModified,
                        // The photo stays whatever this device already had. It is not in the
                        // payload, so carrying the blank across would erase a picture the teacher
                        // took, every time they imported a backup.
                        picturePath = existing.picturePath
                    )
                )
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
                            incidentDate = bDateMillis,
                            actionTaken = bObj.optString("actionTaken", ""),
                            resolvedAt = bObj.optLong("resolvedAt", 0L)
                        )
                    )
                }
            }

            // Contact history, de-duplicated on who and when so a re-import does not double it up
            val contactArr = sObj.optJSONArray("contactLog")
            if (contactArr != null) {
                val seenContacts = repository.getContactLogForStudent(resolvedStudentId)
                    .map { "${it.phone}|${it.sentAt}" }
                    .toMutableSet()

                for (c in 0 until contactArr.length()) {
                    val cLog = contactArr.getJSONObject(c)
                    val phone = cLog.optString("phone", "")
                    val sentAt = cLog.optLong("sentAt", 0L)
                    if (!seenContacts.add("$phone|$sentAt")) continue

                    repository.logContact(
                        ContactLogEntity(
                            studentId = resolvedStudentId,
                            guardianName = cLog.optString("guardianName", ""),
                            phone = phone,
                            channel = cLog.optString("channel", ContactLogEntity.CHANNEL_SMS),
                            templateName = cLog.optString("templateName", ""),
                            body = cLog.optString("body", ""),
                            sentAt = if (sentAt > 0L) sentAt else System.currentTimeMillis()
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

        // Form templates. Carrying these explicitly preserves the field TYPE and any dropdown
        // options. The discovery pass above has already created a plain TEXT field for every
        // custom key it saw, so this resolves by name and corrects the type in place.
        val templatesArr = payloadObj.optJSONArray("formTemplates")
        if (templatesArr != null) {
            val templatesByName = repository.getAllFormTemplates().associateBy { it.fieldName.trim().lowercase() }
            for (i in 0 until templatesArr.length()) {
                val tObj = templatesArr.getJSONObject(i)
                val fieldName = tObj.optString("fieldName", "").trim()
                if (fieldName.isEmpty()) continue

                repository.insertFormTemplate(
                    FormTemplateEntity(
                        id = templatesByName[fieldName.lowercase()]?.id ?: 0,
                        fieldName = fieldName,
                        fieldType = tObj.optString("fieldType", "TEXT"),
                        isRequired = tObj.optBoolean("isRequired", false),
                        optionsJson = (tObj.optJSONArray("options") ?: JSONArray()).toString()
                    )
                )
            }
        }

        // Grading periods, keyed by name. Resolved before the gradebook so assessments can be
        // attached to the period they belong to.
        val termIdByName = mutableMapOf<String, Int>()
        repository.getAllGradingTerms().forEach { termIdByName[it.name.trim().lowercase()] = it.id }
        val termsArr = payloadObj.optJSONArray("gradingTerms")
        if (termsArr != null) {
            for (i in 0 until termsArr.length()) {
                val tObj = termsArr.getJSONObject(i)
                val name = tObj.optString("name", "").trim()
                if (name.isEmpty()) continue

                val newId = repository.insertGradingTerm(
                    GradingTermEntity(
                        id = termIdByName[name.lowercase()] ?: 0,
                        name = name,
                        startDate = parseDateOrZero(sdfBday, tObj.optString("startDate", "")),
                        endDate = parseDateOrZero(sdfBday, tObj.optString("endDate", "")),
                        isActive = tObj.optBoolean("isActive", false)
                    )
                ).toInt()
                termIdByName[name.lowercase()] = newId
            }
            // Exactly one period may be active, and setActiveTerm enforces that in one transaction.
            repository.getAllGradingTerms().firstOrNull { it.isActive }?.let {
                repository.setActiveTerm(it.id)
            }
        }

        // Weighted categories, keyed by name
        val categoryIdByName = mutableMapOf<String, Int>()
        repository.getAllAssessmentCategories().forEach { categoryIdByName[it.name.trim().lowercase()] = it.id }
        val categoriesArr = payloadObj.optJSONArray("assessmentCategories")
        if (categoriesArr != null) {
            for (i in 0 until categoriesArr.length()) {
                val cObj = categoriesArr.getJSONObject(i)
                val name = cObj.optString("name", "").trim()
                if (name.isEmpty()) continue

                val newId = repository.insertAssessmentCategory(
                    AssessmentCategoryEntity(
                        id = categoryIdByName[name.lowercase()] ?: 0,
                        name = name,
                        weight = cObj.optDouble("weight", 0.0),
                        termId = termIdByName[cObj.optString("term", "").trim().lowercase()] ?: 0
                    )
                ).toInt()
                categoryIdByName[name.lowercase()] = newId
            }
        }

        // Rubrics and their levels, keyed by rubric name. Levels are replaced wholesale rather
        // than merged: they are an ordered scale, and half-updating one produces a scale that
        // no longer adds up.
        val rubricIdByName = mutableMapOf<String, Int>()
        repository.getAllRubrics().forEach { rubricIdByName[it.name.trim().lowercase()] = it.id }
        val rubricsArr = payloadObj.optJSONArray("rubrics")
        if (rubricsArr != null) {
            val existingLevels = repository.getAllRubricLevels()
            for (i in 0 until rubricsArr.length()) {
                val rObj = rubricsArr.getJSONObject(i)
                val name = rObj.optString("name", "").trim()
                if (name.isEmpty()) continue

                // Update in place for a rubric that already exists. A REPLACE insert would delete
                // the row first, and rubric_levels cascades on delete - so the scale would be
                // destroyed before the replacement levels below were written, and a failure part
                // way through would leave a rubric that grades nothing.
                val existingId = rubricIdByName[name.lowercase()]
                val rubricId = if (existingId != null) {
                    repository.updateRubric(RubricEntity(id = existingId, name = name))
                    existingId
                } else {
                    repository.insertRubric(RubricEntity(name = name)).toInt()
                }
                rubricIdByName[name.lowercase()] = rubricId

                existingLevels.filter { it.rubricId == rubricId }.forEach {
                    repository.deleteRubricLevel(it.id)
                }

                val levelsArr = rObj.optJSONArray("levels") ?: JSONArray()
                for (l in 0 until levelsArr.length()) {
                    val lObj = levelsArr.getJSONObject(l)
                    repository.insertRubricLevel(
                        RubricLevelEntity(
                            rubricId = rubricId,
                            label = lObj.optString("label", ""),
                            points = lObj.optDouble("points", 0.0),
                            descriptor = lObj.optString("descriptor", ""),
                            displayOrder = lObj.optInt("order", l)
                        )
                    )
                }
            }
        }

        // Message templates, keyed by name
        val messageTemplatesArr = payloadObj.optJSONArray("messageTemplates")
        if (messageTemplatesArr != null) {
            val existingMessages = repository.getAllMessageTemplates().associateBy { it.name.trim().lowercase() }
            for (i in 0 until messageTemplatesArr.length()) {
                val mObj = messageTemplatesArr.getJSONObject(i)
                val name = mObj.optString("name", "").trim()
                if (name.isEmpty()) continue

                repository.insertMessageTemplate(
                    MessageTemplateEntity(
                        id = existingMessages[name.lowercase()]?.id ?: 0,
                        name = name,
                        text = mObj.optString("text", "")
                    )
                )
            }
        }

        // Cold-call counters, so an imported roster arrives with its participation history intact
        val participationArr = payloadObj.optJSONArray("participation")
        if (participationArr != null) {
            for (i in 0 until participationArr.length()) {
                val pObj = participationArr.getJSONObject(i)
                val studentId = studentIdentifierToIdMap[pObj.optString("studentIdentifier", "")] ?: continue
                val className = pObj.optString("className", "").trim()
                if (className.isEmpty()) continue

                repository.setParticipation(
                    studentId = studentId,
                    className = className,
                    timesCalled = pObj.optInt("timesCalled", 0),
                    lastCalledMillis = pObj.optLong("lastCalled", 0L)
                )
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

                // Blank or unreadable means nothing is outstanding, not "due at the epoch".
                val dueMillis = parseDateOrZero(sdfBday, gObj.optString("dueDate", ""))

                val termId = termIdByName[gObj.optString("term", "").trim().lowercase()] ?: 0
                val categoryId = categoryIdByName[gObj.optString("category", "").trim().lowercase()] ?: 0
                val rubricId = rubricIdByName[gObj.optString("rubric", "").trim().lowercase()] ?: 0

                val existingColumn = existingColumns.find { it.name == gName && it.examDate == examMillis }
                val columnId = if (existingColumn == null) {
                    repository.insertAssessmentColumn(
                        AssessmentColumnEntity(
                            name = gName,
                            maxPoints = gObj.optDouble("maxPoints", 100.0),
                            examDate = examMillis,
                            checkDate = checkMillis,
                            savedFilterId = 0,
                            dueDate = dueMillis,
                            termId = termId,
                            categoryId = categoryId,
                            rubricId = rubricId
                        )
                    ).toInt()
                } else {
                    // In place, so re-importing refreshes an assessment's weighting and period
                    // instead of leaving the first import's links frozen. Not a REPLACE insert:
                    // that would cascade every score on the sheet away.
                    repository.updateAssessmentColumn(
                        existingColumn.copy(
                            maxPoints = gObj.optDouble("maxPoints", existingColumn.maxPoints),
                            checkDate = checkMillis,
                            dueDate = dueMillis,
                            termId = termId,
                            categoryId = categoryId,
                            rubricId = rubricId
                        )
                    )
                    existingColumn.id
                }
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

    /** Parses an "MM-dd-yyyy" field, treating anything unreadable as unset rather than throwing. */
    private fun parseDateOrZero(format: SimpleDateFormat, raw: String): Long =
        try {
            format.parse(raw)?.time ?: 0L
        } catch (_: Exception) {
            0L
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