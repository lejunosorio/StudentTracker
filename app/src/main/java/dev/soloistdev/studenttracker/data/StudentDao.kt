package dev.soloistdev.studenttracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface StudentDao {
    // --- STUDENT QUERIES ---
    @Query("SELECT * FROM students WHERE isDeleted = 0 ORDER BY lastName ASC")
    fun getAllActiveStudents(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE id = :studentId")
    fun getStudentById(studentId: Int): StudentEntity?

    // Only ever for a row that does not exist yet. Everything else goes through upsertStudent:
    // see the note there for why writing an existing student this way destroys their data.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNewStudent(student: StudentEntity): Long

    // In-place update. Avoids the INSERT OR REPLACE delete+insert, which would cascade
    // away the behavior incidents and assessment scores that reference this student.
    @Update
    fun updateStudent(student: StudentEntity)

    /**
     * The single supported way to write a student, insert or edit.
     *
     * @Insert(REPLACE) resolves a primary-key conflict by DELETING the existing row and
     * inserting a new one. Foreign keys are on, and both assessment_scores and
     * behavior_incidents cascade on delete, so re-inserting a student over themselves silently
     * erases their entire gradebook and incident history. Correcting a spelling in a name is
     * not supposed to do that, so the choice is made here rather than at each call site.
     *
     * @return the row id the student now lives at.
     */
    @Transaction
    fun upsertStudent(student: StudentEntity): Int {
        if (student.id != 0 && getStudentById(student.id) != null) {
            updateStudent(student)
            return student.id
        }
        return insertNewStudent(student).toInt()
    }

    @Query("UPDATE students SET isDeleted = 1 WHERE id = :studentId")
    fun softDeleteStudent(studentId: Int)

    @Query("SELECT * FROM students WHERE isDeleted = 1 ORDER BY lastName ASC")
    fun getAllDeletedStudents(): List<StudentEntity>

    @Query("UPDATE students SET isDeleted = 0 WHERE id = :studentId")
    fun restoreStudent(studentId: Int)

    @Query("DELETE FROM students WHERE id = :studentId")
    fun permanentDeleteStudent(studentId: Int)


    // --- CUSTOM FIELDS / TEMPLATES QUERIES ---
    @Query("SELECT * FROM form_templates WHERE isDeleted = 0 ORDER BY fieldName ASC")
    fun getAllFormTemplates(): List<FormTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFormTemplate(template: FormTemplateEntity): Long

    @Query("UPDATE form_templates SET isDeleted = 1 WHERE id = :templateId")
    fun softDeleteFormTemplate(templateId: Int)

    @Query("SELECT * FROM form_templates WHERE isDeleted = 1 ORDER BY fieldName ASC")
    fun getAllDeletedFormTemplates(): List<FormTemplateEntity>

    @Query("UPDATE form_templates SET isDeleted = 0 WHERE id = :templateId")
    fun restoreFormTemplate(templateId: Int)

    @Query("DELETE FROM form_templates WHERE id = :templateId")
    fun permanentDeleteFormTemplate(templateId: Int)


    // --- SAVED FILTERS QUERIES ---
    @Query("SELECT * FROM saved_filters WHERE isDeleted = 0 ORDER BY displayOrder ASC")
    fun getAllSavedFilters(): List<SavedFilterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedFilter(filter: SavedFilterEntity): Long

    @Query("UPDATE saved_filters SET isDeleted = 1 WHERE id = :filterId")
    fun softDeleteSavedFilter(filterId: Int)

    @Query("SELECT * FROM saved_filters WHERE isDeleted = 1 ORDER BY displayOrder ASC")
    fun getAllDeletedSavedFilters(): List<SavedFilterEntity>

    @Query("UPDATE saved_filters SET isDeleted = 0 WHERE id = :filterId")
    fun restoreSavedFilter(filterId: Int)

    @Query("DELETE FROM saved_filters WHERE id = :filterId")
    fun permanentDeleteSavedFilter(filterId: Int)

    @Query("UPDATE saved_filters SET displayOrder = :order WHERE id = :filterId")
    fun updateSavedFilterOrder(filterId: Int, order: Int)

    @Transaction
    fun updateAllSavedFilterOrders(ordersList: List<SavedFilterEntity>) {
        ordersList.forEach { filter ->
            updateSavedFilterOrder(filter.id, filter.displayOrder)
        }
    }

    // --- ATTENDANCE SYSTEM QUERIES ---
    @Query("SELECT * FROM attendance_records WHERE isDeleted = 0 ORDER BY id DESC")
    fun getAllAttendanceRecords(): List<AttendanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttendanceRecord(record: AttendanceRecordEntity): Long

    @Query("UPDATE attendance_records SET isDeleted = 1 WHERE id = :recordId")
    fun softDeleteAttendanceRecord(recordId: Int)

    @Query("SELECT * FROM attendance_records WHERE isDeleted = 1 ORDER BY id DESC")
    fun getAllDeletedAttendanceRecords(): List<AttendanceRecordEntity>

    @Query("UPDATE attendance_records SET isDeleted = 0 WHERE id = :recordId")
    fun restoreAttendanceRecord(recordId: Int)

    @Query("DELETE FROM attendance_records WHERE id = :recordId")
    fun permanentDeleteAttendanceRecord(recordId: Int)

    @Query("SELECT * FROM attendance_logs WHERE recordId = :recordId AND dateMillis = :dateMillis")
    fun getLogsForDate(recordId: Int, dateMillis: Long): List<AttendanceLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttendanceLog(log: AttendanceLogEntity): Long

    // Creating a sheet writes one row per student per day - a term across a few classes is tens
    // of thousands. One statement per row, each in its own implicit transaction, made that take
    // long enough to look frozen; this commits the lot once.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttendanceLogs(logs: List<AttendanceLogEntity>)

    @Query("UPDATE attendance_logs SET status = :status, lastModified = :lastModified WHERE recordId = :recordId AND dateMillis = :dateMillis AND studentId = :studentId")
    fun updateAttendanceStatus(recordId: Int, dateMillis: Long, studentId: Int, status: String, lastModified: Long): Int

    /** Sets one status across many students on a single day, in one transaction. */
    @Query("UPDATE attendance_logs SET status = :status, lastModified = :lastModified WHERE recordId = :recordId AND dateMillis = :dateMillis AND studentId IN (:studentIds)")
    fun updateAttendanceStatusForStudents(
        recordId: Int,
        dateMillis: Long,
        studentIds: List<Int>,
        status: String,
        lastModified: Long
    ): Int

    /** Applies a per-student status map for one day in a single transaction. */
    @Transaction
    fun applyAttendanceStatuses(recordId: Int, dateMillis: Long, statusByStudent: Map<Int, String>) {
        val now = System.currentTimeMillis()
        statusByStudent.entries
            .groupBy({ it.value }, { it.key })
            .forEach { (status, studentIds) ->
                updateAttendanceStatusForStudents(recordId, dateMillis, studentIds, status, now)
            }
    }

    @Query("SELECT * FROM attendance_logs WHERE recordId = :recordId")
    fun getLogsForRecord(recordId: Int): List<AttendanceLogEntity>
    @Query("SELECT * FROM attendance_logs")
    fun getAllAttendanceLogs(): List<AttendanceLogEntity>

    // --- BEHAVIOR INCIDENTS QUERIES ---
    @Query("SELECT * FROM behavior_incidents WHERE studentId = :studentId ORDER BY timestamp DESC")
    fun getIncidentsForStudent(studentId: Int): List<BehaviorIncidentEntity>

    // Bulk read for analytics, which needs every incident at once rather than N per-student queries
    @Query("SELECT * FROM behavior_incidents")
    fun getAllIncidents(): List<BehaviorIncidentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIncident(incident: BehaviorIncidentEntity): Long

    @Query("DELETE FROM behavior_incidents WHERE id = :incidentId")
    fun deleteIncident(incidentId: Int)

    // In-place: an incident carries a follow-up now, and a REPLACE insert would churn its id.
    @Update
    fun updateIncident(incident: BehaviorIncidentEntity)

    // --- CONTACT LOG ---
    @Query("SELECT * FROM contact_log ORDER BY sentAt DESC")
    fun getAllContactLog(): List<ContactLogEntity>

    @Query("SELECT * FROM contact_log WHERE studentId = :studentId ORDER BY sentAt DESC")
    fun getContactLogForStudent(studentId: Int): List<ContactLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertContactLog(entry: ContactLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertContactLogEntries(entries: List<ContactLogEntity>)

    @Query("DELETE FROM contact_log WHERE id = :entryId")
    fun deleteContactLogEntry(entryId: Int)

    // --- MESSAGE TEMPLATES QUERIES ---
    @Query("SELECT * FROM message_templates ORDER BY name ASC")
    fun getAllMessageTemplates(): List<MessageTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessageTemplate(template: MessageTemplateEntity): Long

    @Query("DELETE FROM message_templates WHERE id = :templateId")
    fun deleteMessageTemplate(templateId: Int)

    // --- ASSESSMENT/GRADEBOOK QUERIES ---
    @Query("SELECT * FROM assessment_columns WHERE isDeleted = 0 ORDER BY id ASC")
    fun getAllAssessmentColumns(): List<AssessmentColumnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAssessmentColumn(column: AssessmentColumnEntity): Long

    // In-place edit. Insert-with-REPLACE would delete the column row first, and the CASCADE
    // from assessment_scores would take every score on that sheet with it.
    @Update
    fun updateAssessmentColumn(column: AssessmentColumnEntity)

    @Query("UPDATE assessment_columns SET isDeleted = 1 WHERE id = :columnId")
    fun softDeleteAssessmentColumn(columnId: Int)

    @Query("SELECT * FROM assessment_scores WHERE columnId = :columnId")
    fun getScoresForColumn(columnId: Int): List<AssessmentScoreEntity>

    @Query("SELECT * FROM assessment_scores")
    fun getAllAssessmentScores(): List<AssessmentScoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAssessmentScore(score: AssessmentScoreEntity): Long

    // --- CLASSROOMS QUERIES ---
    @Query("SELECT * FROM classrooms WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllClassrooms(): List<ClassroomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertClassroom(classroom: ClassroomEntity): Long

    @Query("UPDATE classrooms SET isDeleted = 1 WHERE id = :classroomId")
    fun softDeleteClassroom(classroomId: Int)

    // Performs classroom-specific seating writes via an transaction
    @Transaction
    fun updateStudentSeatingForClass(studentId: Int, className: String, x: Float, y: Float) {
        val student = getStudentById(studentId)
        if (student != null) {
            // In-place: dragging a seat must not cascade the student's scores away.
            updateStudent(student.withUpdatedSeating(className, x, y))
        }
    }

    // --- GRADING TERMS ---
    @Query("SELECT * FROM grading_terms WHERE isDeleted = 0 ORDER BY startDate ASC")
    fun getAllGradingTerms(): List<GradingTermEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGradingTerm(term: GradingTermEntity): Long

    @Query("UPDATE grading_terms SET isDeleted = 1 WHERE id = :termId")
    fun softDeleteGradingTerm(termId: Int)

    @Query("UPDATE grading_terms SET isActive = 0")
    fun clearActiveTerms()

    @Query("UPDATE grading_terms SET isActive = 1 WHERE id = :termId")
    fun markTermActive(termId: Int)

    @Transaction
    fun setActiveTerm(termId: Int) {
        clearActiveTerms()
        markTermActive(termId)
    }

    // --- ASSESSMENT CATEGORIES ---
    @Query("SELECT * FROM assessment_categories WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllAssessmentCategories(): List<AssessmentCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAssessmentCategory(category: AssessmentCategoryEntity): Long

    @Query("UPDATE assessment_categories SET isDeleted = 1 WHERE id = :categoryId")
    fun softDeleteAssessmentCategory(categoryId: Int)

    // Resolves the existing row so a re-save updates in place rather than appending a duplicate
    @Query("SELECT * FROM assessment_scores WHERE columnId = :columnId AND studentId = :studentId LIMIT 1")
    fun getScoreFor(columnId: Int, studentId: Int): AssessmentScoreEntity?

    @Transaction
    fun upsertAssessmentScore(columnId: Int, studentId: Int, score: String) {
        val existing = getScoreFor(columnId, studentId)
        insertAssessmentScore(
            AssessmentScoreEntity(
                id = existing?.id ?: 0,
                columnId = columnId,
                studentId = studentId,
                score = score
            )
        )
    }

    // --- RUBRICS ---
    @Query("SELECT * FROM rubrics WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllRubrics(): List<RubricEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRubric(rubric: RubricEntity): Long

    @Query("UPDATE rubrics SET isDeleted = 1 WHERE id = :rubricId")
    fun softDeleteRubric(rubricId: Int)

    @Query("SELECT * FROM rubric_levels ORDER BY rubricId ASC, displayOrder ASC")
    fun getAllRubricLevels(): List<RubricLevelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRubricLevel(level: RubricLevelEntity): Long

    @Query("DELETE FROM rubric_levels WHERE id = :levelId")
    fun deleteRubricLevel(levelId: Int)

    // --- PARTICIPATION EQUITY ---
    @Query("SELECT * FROM participation_counts WHERE className = :className")
    fun getParticipationForClass(className: String): List<ParticipationCountEntity>

    @Query("SELECT * FROM participation_counts WHERE studentId = :studentId AND className = :className LIMIT 1")
    fun getParticipationFor(studentId: Int, className: String): ParticipationCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertParticipation(entry: ParticipationCountEntity): Long

    @Query("DELETE FROM participation_counts WHERE className = :className")
    fun resetParticipationForClass(className: String)

    /** Restores a counter to a known value, as opposed to counting one more call. */
    @Transaction
    fun setParticipation(studentId: Int, className: String, timesCalled: Int, lastCalledMillis: Long) {
        val existing = getParticipationFor(studentId, className)
        insertParticipation(
            ParticipationCountEntity(
                id = existing?.id ?: 0,
                studentId = studentId,
                className = className,
                timesCalled = timesCalled,
                lastCalledMillis = lastCalledMillis
            )
        )
    }

    @Transaction
    fun recordParticipation(studentId: Int, className: String) {
        val existing = getParticipationFor(studentId, className)
        insertParticipation(
            ParticipationCountEntity(
                id = existing?.id ?: 0,
                studentId = studentId,
                className = className,
                timesCalled = (existing?.timesCalled ?: 0) + 1,
                lastCalledMillis = System.currentTimeMillis()
            )
        )
    }

    // --- IN-PLACE UPDATES ---
    // All of these exist because @Insert(REPLACE) deletes the row before re-inserting it, which
    // fires ON DELETE CASCADE on anything that references it. Renaming a rubric that way would
    // destroy its levels; editing an attendance sheet would destroy its logs.
    @Update
    fun updateAttendanceRecord(record: AttendanceRecordEntity)

    @Update
    fun updateGradingTerm(term: GradingTermEntity)

    @Update
    fun updateAssessmentCategory(category: AssessmentCategoryEntity)

    @Update
    fun updateRubric(rubric: RubricEntity)

    @Update
    fun updateRubricLevel(level: RubricLevelEntity)
}