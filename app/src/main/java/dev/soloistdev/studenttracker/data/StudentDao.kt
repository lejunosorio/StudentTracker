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


    // --- MAP ARCHIVES QUERIES DELETED COMPLETELY ---
    // [All queries referencing map_archives are removed from here]


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

    @Query("UPDATE attendance_logs SET status = :status WHERE recordId = :recordId AND dateMillis = :dateMillis AND studentId = :studentId")
    fun updateAttendanceStatus(recordId: Int, dateMillis: Long, studentId: Int, status: String): Int

    @Query("SELECT * FROM attendance_logs WHERE recordId = :recordId")
    fun getLogsForRecord(recordId: Int): List<AttendanceLogEntity>
}