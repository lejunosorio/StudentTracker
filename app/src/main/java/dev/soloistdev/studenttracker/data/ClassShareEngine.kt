package dev.soloistdev.studenttracker.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shares a chosen set of classes, with the teacher deciding what travels with them.
 *
 * The output is the same document shape as a full backup, so anything produced here imports
 * through the normal restore path on the receiving device with no special casing.
 *
 * Content is opt-out rather than all-or-nothing because sharing a roster with a colleague, a
 * substitute, or a department head are three different disclosures. Guardian phone numbers and
 * home addresses are the parts most worth withholding, so they are separately switchable.
 */
object ClassShareEngine {

    data class ShareOptions(
        val includeContactDetails: Boolean = false,
        val includeGuardians: Boolean = false,
        val includeAttendance: Boolean = true,
        val includeGrades: Boolean = true,
        val includeBehaviour: Boolean = false,
        val includeSeating: Boolean = true
    )

    /** What a given selection would actually export, for showing before anything leaves. */
    data class SharePreview(
        val students: Int,
        val attendanceSheets: Int,
        val assessments: Int,
        val behaviourNotes: Int
    )

    suspend fun preview(
        repository: StudentRepository,
        classNames: Set<String>,
        options: ShareOptions
    ): SharePreview = withContext(Dispatchers.IO) {
        val roster = rosterFor(repository, classNames)
        val ids = roster.map { it.id }.toSet()

        val sheets = if (!options.includeAttendance) 0 else {
            val logs = repository.getAllAttendanceLogs().filter { it.studentId in ids }
            logs.map { it.recordId }.distinct().size
        }
        val assessments = if (!options.includeGrades) 0 else {
            repository.getAllAssessmentScores()
                .filter { it.studentId in ids }
                .map { it.columnId }
                .distinct()
                .size
        }
        val notes = if (!options.includeBehaviour) 0 else {
            repository.getAllIncidents().count { it.studentId in ids }
        }

        SharePreview(roster.size, sheets, assessments, notes)
    }

    private suspend fun rosterFor(repository: StudentRepository, classNames: Set<String>): List<StudentEntity> =
        repository.getAllActiveStudents().filter { student ->
            student.getClassNamesList().any { it in classNames }
        }

    suspend fun shareClasses(
        context: Context,
        repository: StudentRepository,
        classNames: Set<String>,
        options: ShareOptions
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (classNames.isEmpty()) return@withContext false

            val payload = buildPayload(repository, classNames, options)

            val cacheDir = File(context.cacheDir, "shares").apply { mkdirs() }
            val label = classNames.sorted().joinToString("_").replace(Regex("[^A-Za-z0-9_]+"), "_").take(60)
            val file = File(cacheDir, "classes_$label.json")
            if (file.exists()) file.delete()

            FileOutputStream(file).use { fos ->
                fos.write(payload.toString().toByteArray(Charsets.UTF_8))
                fos.flush()
            }
            file.deleteOnExit()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Share ${classNames.size} class(es)"))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Builds the export document.
     *
     * Emits the standard backup schema so the receiving device restores it through the same
     * merge path as any other import - matching students on name and birthday rather than
     * duplicating them.
     */
    private suspend fun buildPayload(
        repository: StudentRepository,
        classNames: Set<String>,
        options: ShareOptions
    ): JSONObject = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("MM-dd-yyyy", Locale.US)
        val roster = rosterFor(repository, classNames)
        val ids = roster.map { it.id }.toSet()

        JSONObject().apply {
            val classroomsArr = JSONArray()
            repository.getAllClassrooms()
                .filter { it.name in classNames }
                .forEach { c ->
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
                    put("birthday", sdf.format(Date(s.birthday)))
                    put("lastModified", s.lastModified)

                    // Only the selected classes travel. A student in Class 1 and Class 8 who is
                    // shared as Class 1 alone should not disclose the other membership.
                    val shared = s.getClassNamesList().filter { it in classNames }
                    put("classRoom", shared.firstOrNull() ?: "")
                    put("classNamesJson", JSONArray().apply { shared.forEach { put(it) } })

                    put("seatingJson", if (options.includeSeating) JSONObject(s.seatingJson) else JSONObject())
                    put("address", if (options.includeContactDetails) s.address else "")
                    put("contactNumber", if (options.includeContactDetails) s.contactNumber else "")
                    put("guardiansJson", if (options.includeGuardians) JSONArray(s.guardiansJson) else JSONArray())
                    put("customDataJson", JSONObject(s.customDataJson))

                    val behaviourArr = JSONArray()
                    if (options.includeBehaviour) {
                        repository.getIncidentsForStudent(s.id).forEach { b ->
                            behaviourArr.put(JSONObject().apply {
                                put("title", b.title)
                                put("category", b.category)
                                put("description", b.description)
                                put("date", sdf.format(Date(b.incidentDate)))
                            })
                        }
                    }
                    put("behaviorIncidents", behaviourArr)
                })
            }
            put("students", studentsArr)
            put("savedFilters", JSONArray())

            val attendanceArr = JSONArray()
            if (options.includeAttendance) {
                val scopedLogs = repository.getAllAttendanceLogs().filter { it.studentId in ids }
                val recordIds = scopedLogs.map { it.recordId }.toSet()
                repository.getAllAttendanceRecords()
                    .filter { it.id in recordIds }
                    .forEach { record ->
                        val recordLogs = scopedLogs.filter { it.recordId == record.id }
                        attendanceArr.put(JSONObject().apply {
                            put("name", record.name)
                            put("startDate", sdf.format(Date(record.startDate)))
                            put("endDate", sdf.format(Date(record.endDate)))

                            val participants = JSONArray()
                            roster.forEach { student ->
                                val mine = recordLogs.filter { it.studentId == student.id }
                                if (mine.isEmpty()) return@forEach
                                val marks = JSONObject()
                                mine.forEach { log -> marks.put(sdf.format(Date(log.dateMillis)), log.status) }
                                participants.put(JSONObject().apply {
                                    put("studentIdentifier", participantKey(student, classNames))
                                    put("attendance", marks)
                                })
                            }
                            put("participants", participants)
                        })
                    }
            }
            put("attendanceRecord", attendanceArr)

            val gradebookArr = JSONArray()
            if (options.includeGrades) {
                val scopedScores = repository.getAllAssessmentScores().filter { it.studentId in ids }
                val columnIds = scopedScores.map { it.columnId }.toSet()
                repository.getAllAssessmentColumns()
                    .filter { it.id in columnIds }
                    .forEach { column ->
                        val colScores = scopedScores.filter { it.columnId == column.id }
                        gradebookArr.put(JSONObject().apply {
                            put("name", column.name)
                            put("maxPoints", column.maxPoints)
                            put("examDate", sdf.format(Date(column.examDate)))
                            put("checkDate", sdf.format(Date(column.checkDate)))

                            val grades = JSONArray()
                            roster.forEach { student ->
                                val match = colScores.firstOrNull { it.studentId == student.id } ?: return@forEach
                                grades.put(JSONObject().apply {
                                    put("studentIdentifier", participantKey(student, classNames))
                                    put("score", match.score)
                                })
                            }
                            put("grades", grades)
                        })
                    }
            }
            put("gradeBook", gradebookArr)
        }
    }

    /**
     * Must match the identifier the importer rebuilds, which uses the first class name on the
     * student. Since only the shared classes are exported, the key has to be built from that
     * filtered list rather than the full membership.
     */
    private fun participantKey(student: StudentEntity, classNames: Set<String>): String {
        val firstShared = student.getClassNamesList().firstOrNull { it in classNames } ?: ""
        return "${student.lastName}_${student.firstName}_$firstShared"
    }
}
