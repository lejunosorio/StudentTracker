package dev.soloistdev.studenttracker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File // Required for disk-level file operations [1]

class StudentRepository(private val context: Context) {
    private val studentDao: StudentDao by lazy {
        AppDatabase.getDatabase(context).studentDao()
    }

    // Student Accessors
    suspend fun getAllActiveStudents(): List<StudentEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllActiveStudents()
    }

    suspend fun insertStudent(student: StudentEntity) = withContext(Dispatchers.IO) {
        studentDao.insertStudent(student)
    }

    suspend fun softDeleteStudent(studentId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteStudent(studentId)
    }

    // Template Accessors
    suspend fun getAllFormTemplates(): List<FormTemplateEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllFormTemplates()
    }

    suspend fun insertFormTemplate(template: FormTemplateEntity) = withContext(Dispatchers.IO) {
        studentDao.insertFormTemplate(template)
    }

    suspend fun deleteFormTemplate(templateId: Int) = withContext(Dispatchers.IO) {
        studentDao.deleteFormTemplate(templateId)
    }

    // Recycle Bin & Data Purging (With file cleanup) [1]
    suspend fun getAllDeletedStudents(): List<StudentEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllDeletedStudents()
    }

    suspend fun restoreStudent(studentId: Int) = withContext(Dispatchers.IO) {
        studentDao.restoreStudent(studentId)
    }

    suspend fun permanentDeleteStudent(studentId: Int) = withContext(Dispatchers.IO) {
        // Query the soft-deleted list to find and purge their profile image file [1]
        try {
            val deletedRoster = studentDao.getAllDeletedStudents()
            val targetStudent = deletedRoster.find { it.id == studentId }

            targetStudent?.let { student ->
                if (student.picturePath.isNotEmpty()) {
                    val imageFile = File(student.picturePath)
                    if (imageFile.exists()) {
                        imageFile.delete() // Permanently delete the file to prevent storage bloat [1]
                    }
                }
            }
        } catch (_: Exception) {
            // Suppressed
        }
        studentDao.permanentDeleteStudent(studentId)
    }

    // --- SAVED FILTERS DATA ACCESS ACCESSORS ---
    suspend fun getAllSavedFilters(): List<SavedFilterEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllSavedFilters()
    }

    suspend fun insertSavedFilter(filter: SavedFilterEntity) = withContext(Dispatchers.IO) {
        studentDao.insertSavedFilter(filter)
    }

    suspend fun deleteSavedFilter(filterId: Int) = withContext(Dispatchers.IO) {
        studentDao.deleteSavedFilter(filterId)
    }

    suspend fun updateAllSavedFilterOrders(ordersList: List<SavedFilterEntity>) = withContext(Dispatchers.IO) {
        studentDao.updateAllSavedFilterOrders(ordersList)
    }

    suspend fun getAllAttendanceRecords(): List<AttendanceRecordEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllAttendanceRecords()
    }

    suspend fun insertAttendanceRecord(record: AttendanceRecordEntity): Long = withContext(Dispatchers.IO) {
        studentDao.insertAttendanceRecord(record)
    }

    suspend fun deleteAttendanceRecord(recordId: Int) = withContext(Dispatchers.IO) {
        studentDao.deleteAttendanceRecord(recordId)
    }

    suspend fun getLogsForDate(recordId: Int, dateMillis: Long): List<AttendanceLogEntity> = withContext(Dispatchers.IO) {
        studentDao.getLogsForDate(recordId, dateMillis)
    }

    suspend fun insertAttendanceLog(log: AttendanceLogEntity) = withContext(Dispatchers.IO) {
        studentDao.insertAttendanceLog(log)
    }

    suspend fun updateAttendanceStatus(recordId: Int, dateMillis: Long, studentId: Int, status: String) = withContext(Dispatchers.IO) {
        studentDao.updateAttendanceStatus(recordId, dateMillis, studentId, status)
    }

    suspend fun getLogsForRecord(recordId: Int): List<AttendanceLogEntity> = withContext(Dispatchers.IO) {
        studentDao.getLogsForRecord(recordId)
    }
}