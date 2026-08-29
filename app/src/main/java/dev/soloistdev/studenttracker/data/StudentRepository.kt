package dev.soloistdev.studenttracker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StudentRepository(private val context: Context) {
    private val studentDao: StudentDao by lazy {
        AppDatabase.getDatabase(context).studentDao()
    }

    // Student Accessors
    suspend fun getAllActiveStudents(): List<StudentEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllActiveStudents()
    }

    /**
     * Writes a student, new or existing, and returns the id they live at.
     *
     * There is deliberately no raw insert on this surface. A plain REPLACE insert over an
     * existing student cascade-deletes their scores and behavior incidents, and that mistake was
     * easy to make when both spellings were available; [StudentDao.upsertStudent] decides.
     */
    suspend fun saveStudent(student: StudentEntity): Int = withContext(Dispatchers.IO) {
        studentDao.upsertStudent(student)
    }

    suspend fun softDeleteStudent(studentId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteStudent(studentId)
    }

    // Custom Fields / Template Accessors
    suspend fun getAllFormTemplates(): List<FormTemplateEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllFormTemplates()
    }

    suspend fun insertFormTemplate(template: FormTemplateEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertFormTemplate(template)
    }

    suspend fun softDeleteFormTemplate(templateId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteFormTemplate(templateId)
    }

    suspend fun getAllDeletedFormTemplates(): List<FormTemplateEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllDeletedFormTemplates()
    }

    suspend fun restoreFormTemplate(templateId: Int) = withContext(Dispatchers.IO) {
        studentDao.restoreFormTemplate(templateId)
    }

    suspend fun permanentDeleteFormTemplate(templateId: Int) = withContext(Dispatchers.IO) {
        studentDao.permanentDeleteFormTemplate(templateId)
    }

    // Recycle Bin & Data Purging
    suspend fun getAllDeletedStudents(): List<StudentEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllDeletedStudents()
    }

    suspend fun restoreStudent(studentId: Int) = withContext(Dispatchers.IO) {
        studentDao.restoreStudent(studentId)
    }

    suspend fun permanentDeleteStudent(studentId: Int) = withContext(Dispatchers.IO) {
        try {
            val deletedRoster = studentDao.getAllDeletedStudents()
            val targetStudent = deletedRoster.find { it.id == studentId }

            targetStudent?.let { student ->
                if (student.picturePath.isNotEmpty()) {
                    val imageFile = File(student.picturePath)
                    if (imageFile.exists()) {
                        imageFile.delete()
                    }
                }
            }
        } catch (_: Exception) {
            // Suppressed
        }
        studentDao.permanentDeleteStudent(studentId)
    }

    // Saved Filters Accessors
    suspend fun getAllSavedFilters(): List<SavedFilterEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllSavedFilters()
    }

    suspend fun insertSavedFilter(filter: SavedFilterEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertSavedFilter(filter)
    }

    suspend fun softDeleteSavedFilter(filterId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteSavedFilter(filterId)
    }

    suspend fun getAllDeletedSavedFilters(): List<SavedFilterEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllDeletedSavedFilters()
    }

    suspend fun restoreSavedFilter(filterId: Int) = withContext(Dispatchers.IO) {
        studentDao.restoreSavedFilter(filterId)
    }

    suspend fun permanentDeleteSavedFilter(filterId: Int) = withContext(Dispatchers.IO) {
        studentDao.permanentDeleteSavedFilter(filterId)
    }

    suspend fun updateAllSavedFilterOrders(ordersList: List<SavedFilterEntity>) = withContext(Dispatchers.IO) {
        studentDao.updateAllSavedFilterOrders(ordersList)
    }

    // Attendance System Accessors
    suspend fun getAllAttendanceRecords(): List<AttendanceRecordEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllAttendanceRecords()
    }

    suspend fun insertAttendanceRecord(record: AttendanceRecordEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertAttendanceRecord(record)
    }

    suspend fun softDeleteAttendanceRecord(recordId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteAttendanceRecord(recordId)
    }

    suspend fun getAllDeletedAttendanceRecords(): List<AttendanceRecordEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllDeletedAttendanceRecords()
    }

    suspend fun restoreAttendanceRecord(recordId: Int) = withContext(Dispatchers.IO) {
        studentDao.restoreAttendanceRecord(recordId)
    }

    suspend fun permanentDeleteAttendanceRecord(recordId: Int) = withContext(Dispatchers.IO) {
        studentDao.permanentDeleteAttendanceRecord(recordId)
    }

    suspend fun getLogsForDate(recordId: Int, dateMillis: Long): List<AttendanceLogEntity> = withContext(Dispatchers.IO) {
        studentDao.getLogsForDate(recordId, dateMillis)
    }

    suspend fun insertAttendanceLog(log: AttendanceLogEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertAttendanceLog(log)
    }

    /** Bulk sheet creation. One transaction rather than one per cell. */
    suspend fun insertAttendanceLogs(logs: List<AttendanceLogEntity>) = withContext(Dispatchers.IO) {
        studentDao.insertAttendanceLogs(logs)
    }

    /** Bulk status write for a single day, e.g. "mark all present" or a reset. */
    suspend fun applyAttendanceStatuses(
        recordId: Int,
        dateMillis: Long,
        statusByStudent: Map<Int, String>
    ) = withContext(Dispatchers.IO) {
        studentDao.applyAttendanceStatuses(recordId, dateMillis, statusByStudent)
    }

    suspend fun updateAttendanceStatus(recordId: Int, dateMillis: Long, studentId: Int, status: String) = withContext(Dispatchers.IO) {
        studentDao.updateAttendanceStatus(recordId, dateMillis, studentId, status, System.currentTimeMillis())
    }

    suspend fun getLogsForRecord(recordId: Int): List<AttendanceLogEntity> = withContext(Dispatchers.IO) {
        studentDao.getLogsForRecord(recordId)
    }

    suspend fun getAllAttendanceLogs(): List<AttendanceLogEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllAttendanceLogs()
    }
    // Behavior Incidents Accessors
    suspend fun getIncidentsForStudent(studentId: Int): List<BehaviorIncidentEntity> = withContext(Dispatchers.IO) {
        studentDao.getIncidentsForStudent(studentId)
    }

    suspend fun getAllIncidents(): List<BehaviorIncidentEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllIncidents()
    }

    suspend fun insertIncident(incident: BehaviorIncidentEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertIncident(incident)
    }

    suspend fun deleteIncident(incidentId: Int) = withContext(Dispatchers.IO) {
        studentDao.deleteIncident(incidentId)
    }

    suspend fun updateIncident(incident: BehaviorIncidentEntity) = withContext(Dispatchers.IO) {
        studentDao.updateIncident(incident)
    }

    // Contact log
    suspend fun getAllContactLog(): List<ContactLogEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllContactLog()
    }

    suspend fun getContactLogForStudent(studentId: Int): List<ContactLogEntity> = withContext(Dispatchers.IO) {
        studentDao.getContactLogForStudent(studentId)
    }

    suspend fun logContact(entry: ContactLogEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertContactLog(entry)
    }

    /** One transaction for a bulk send, which is how most messages go out. */
    suspend fun logContacts(entries: List<ContactLogEntity>) = withContext(Dispatchers.IO) {
        if (entries.isNotEmpty()) studentDao.insertContactLogEntries(entries)
    }

    suspend fun deleteContactLogEntry(entryId: Int) = withContext(Dispatchers.IO) {
        studentDao.deleteContactLogEntry(entryId)
    }

    // Message Templates Accessors
    suspend fun getAllMessageTemplates(): List<MessageTemplateEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllMessageTemplates()
    }

    suspend fun insertMessageTemplate(template: MessageTemplateEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertMessageTemplate(template)
    }

    suspend fun deleteMessageTemplate(templateId: Int) = withContext(Dispatchers.IO) {
        studentDao.deleteMessageTemplate(templateId)
    }

    // Gradebook / Assessment Accessors
    suspend fun getAllAssessmentColumns(): List<AssessmentColumnEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllAssessmentColumns()
    }

    suspend fun insertAssessmentColumn(column: AssessmentColumnEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertAssessmentColumn(column)
    }

    suspend fun updateAssessmentColumn(column: AssessmentColumnEntity) = withContext(Dispatchers.IO) {
        studentDao.updateAssessmentColumn(column)
    }

    suspend fun softDeleteAssessmentColumn(columnId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteAssessmentColumn(columnId)
    }

    suspend fun getScoresForColumn(columnId: Int): List<AssessmentScoreEntity> = withContext(Dispatchers.IO) {
        studentDao.getScoresForColumn(columnId)
    }

    suspend fun getAllAssessmentScores(): List<AssessmentScoreEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllAssessmentScores()
    }

    suspend fun insertAssessmentScore(score: AssessmentScoreEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertAssessmentScore(score)
    }

    // Classroom Accessors
    suspend fun getAllClassrooms(): List<ClassroomEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllClassrooms()
    }

    suspend fun insertClassroom(classroom: ClassroomEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertClassroom(classroom)
    }

    suspend fun softDeleteClassroom(classroomId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteClassroom(classroomId)
    }

    // Performs updates to seating chart coordinates mapped explicitly per classroom
    suspend fun updateStudentSeating(studentId: Int, className: String, x: Float, y: Float) = withContext(Dispatchers.IO) {
        studentDao.updateStudentSeatingForClass(studentId, className, x, y)
    }

    // Grading Terms
    suspend fun getAllGradingTerms(): List<GradingTermEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllGradingTerms()
    }

    suspend fun insertGradingTerm(term: GradingTermEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertGradingTerm(term)
    }

    suspend fun softDeleteGradingTerm(termId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteGradingTerm(termId)
    }

    suspend fun setActiveTerm(termId: Int) = withContext(Dispatchers.IO) {
        studentDao.setActiveTerm(termId)
    }

    // Assessment Categories
    suspend fun getAllAssessmentCategories(): List<AssessmentCategoryEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllAssessmentCategories()
    }

    suspend fun insertAssessmentCategory(category: AssessmentCategoryEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertAssessmentCategory(category)
    }

    suspend fun softDeleteAssessmentCategory(categoryId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteAssessmentCategory(categoryId)
    }

    suspend fun upsertAssessmentScore(columnId: Int, studentId: Int, score: String) = withContext(Dispatchers.IO) {
        studentDao.upsertAssessmentScore(columnId, studentId, score)
    }

    // Rubrics
    suspend fun getAllRubrics(): List<RubricEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllRubrics()
    }

    suspend fun insertRubric(rubric: RubricEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertRubric(rubric)
    }

    suspend fun softDeleteRubric(rubricId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteRubric(rubricId)
    }

    suspend fun getAllRubricLevels(): List<RubricLevelEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllRubricLevels()
    }

    suspend fun insertRubricLevel(level: RubricLevelEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertRubricLevel(level)
    }

    suspend fun deleteRubricLevel(levelId: Int) = withContext(Dispatchers.IO) {
        studentDao.deleteRubricLevel(levelId)
    }

    // Participation equity
    suspend fun getParticipationForClass(className: String): List<ParticipationCountEntity> = withContext(Dispatchers.IO) {
        studentDao.getParticipationForClass(className)
    }

    suspend fun recordParticipation(studentId: Int, className: String) = withContext(Dispatchers.IO) {
        studentDao.recordParticipation(studentId, className)
    }

    /** Sets a counter outright rather than incrementing it. Used when restoring a backup. */
    suspend fun setParticipation(
        studentId: Int,
        className: String,
        timesCalled: Int,
        lastCalledMillis: Long
    ) = withContext(Dispatchers.IO) {
        studentDao.setParticipation(studentId, className, timesCalled, lastCalledMillis)
    }

    suspend fun resetParticipationForClass(className: String) = withContext(Dispatchers.IO) {
        studentDao.resetParticipationForClass(className)
    }

    suspend fun updateAttendanceRecord(record: AttendanceRecordEntity) = withContext(Dispatchers.IO) {
        studentDao.updateAttendanceRecord(record)
    }

    suspend fun updateGradingTerm(term: GradingTermEntity) = withContext(Dispatchers.IO) {
        studentDao.updateGradingTerm(term)
    }

    suspend fun updateAssessmentCategory(category: AssessmentCategoryEntity) = withContext(Dispatchers.IO) {
        studentDao.updateAssessmentCategory(category)
    }

    suspend fun updateRubric(rubric: RubricEntity) = withContext(Dispatchers.IO) {
        studentDao.updateRubric(rubric)
    }

    suspend fun updateRubricLevel(level: RubricLevelEntity) = withContext(Dispatchers.IO) {
        studentDao.updateRubricLevel(level)
    }
}