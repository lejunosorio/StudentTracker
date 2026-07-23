package dev.soloistdev.studenttracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface StudentDao {
    // Core Student Queries
    @Query("SELECT * FROM students WHERE isDeleted = 0 ORDER BY lastName ASC")
    fun getAllActiveStudents(): List<StudentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStudent(student: StudentEntity): Long

    @Query("UPDATE students SET isDeleted = 1 WHERE id = :studentId")
    fun softDeleteStudent(studentId: Int)

    // Dynamic Form Template Queries
    @Query("SELECT * FROM form_templates ORDER BY fieldName ASC")
    fun getAllFormTemplates(): List<FormTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFormTemplate(template: FormTemplateEntity): Long

    @Query("DELETE FROM form_templates WHERE id = :templateId")
    fun deleteFormTemplate(templateId: Int)

    // SPRINT 9 ADDITIONS: Recycle Bin & Data Purging Queries
    @Query("SELECT * FROM students WHERE isDeleted = 1 ORDER BY lastName ASC")
    fun getAllDeletedStudents(): List<StudentEntity>

    @Query("UPDATE students SET isDeleted = 0 WHERE id = :studentId")
    fun restoreStudent(studentId: Int)

    @Query("DELETE FROM students WHERE id = :studentId")
    fun permanentDeleteStudent(studentId: Int)

    // --- SAVED FILTERS DATA ACCESS APIS ---
    @Query("SELECT * FROM saved_filters ORDER BY displayOrder ASC")
    fun getAllSavedFilters(): List<SavedFilterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSavedFilter(filter: SavedFilterEntity): Long

    @Query("DELETE FROM saved_filters WHERE id = :filterId")
    fun deleteSavedFilter(filterId: Int)

    @Query("UPDATE saved_filters SET displayOrder = :order WHERE id = :filterId")
    fun updateSavedFilterOrder(filterId: Int, order: Int)

    @Transaction
    fun updateAllSavedFilterOrders(ordersList: List<SavedFilterEntity>) {
        ordersList.forEach { filter ->
            updateSavedFilterOrder(filter.id, filter.displayOrder)
        }
    }
}