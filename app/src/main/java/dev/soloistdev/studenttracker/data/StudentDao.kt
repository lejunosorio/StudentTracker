package dev.soloistdev.studenttracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface StudentDao {
    // --- STUDENT QUERIES ---
    @Query("SELECT * FROM students WHERE isDeleted = 0 ORDER BY lastName ASC")
    fun getAllActiveStudents(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE id = :studentId")
    fun getStudentById(studentId: Int): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStudent(student: StudentEntity): Long

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

    @Query("UPDATE attendance_logs SET status = :status, lastModified = :lastModified WHERE recordId = :recordId AND dateMillis = :dateMillis AND studentId = :studentId")
    fun updateAttendanceStatus(recordId: Int, dateMillis: Long, studentId: Int, status: String, lastModified: Long): Int

    @Query("SELECT * FROM attendance_logs WHERE recordId = :recordId")
    fun getLogsForRecord(recordId: Int): List<AttendanceLogEntity>
    @Query("SELECT * FROM attendance_logs")
    fun getAllAttendanceLogs(): List<AttendanceLogEntity>

    // --- BEHAVIOR INCIDENTS QUERIES ---
    @Query("SELECT * FROM behavior_incidents WHERE studentId = :studentId ORDER BY timestamp DESC")
    fun getIncidentsForStudent(studentId: Int): List<BehaviorIncidentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIncident(incident: BehaviorIncidentEntity): Long

    @Query("DELETE FROM behavior_incidents WHERE id = :incidentId")
    fun deleteIncident(incidentId: Int)

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
            val updatedStudent = student.withUpdatedSeating(className, x, y)
            insertStudent(updatedStudent)
        }
    }
}