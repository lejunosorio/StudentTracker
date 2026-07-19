package dev.soloistdev.studenttracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    // Map Archive Queries
    @Query("SELECT * FROM map_archives ORDER BY fileName ASC")
    fun getAllMapArchives(): List<MapArchiveEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMapArchive(archive: MapArchiveEntity): Long

    @Query("UPDATE map_archives SET isActive = (id = :activeId)")
    fun setActiveMapArchive(activeId: Int)

    @Query("DELETE FROM map_archives WHERE id = :archiveId")
    fun deleteMapArchive(archiveId: Int)

    // SPRINT 9 ADDITIONS: Recycle Bin & Data Purging Queries
    @Query("SELECT * FROM students WHERE isDeleted = 1 ORDER BY lastName ASC")
    fun getAllDeletedStudents(): List<StudentEntity>

    @Query("UPDATE students SET isDeleted = 0 WHERE id = :studentId")
    fun restoreStudent(studentId: Int)

    @Query("DELETE FROM students WHERE id = :studentId")
    fun permanentDeleteStudent(studentId: Int)
}