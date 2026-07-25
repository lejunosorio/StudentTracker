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

    suspend fun insertStudent(student: StudentEntity) = withContext(Dispatchers.IO) {
        studentDao.insertStudent(student)
    }

    suspend fun softDeleteStudent(studentId: Int) = withContext(Dispatchers.IO) {
        studentDao.softDeleteStudent(studentId)
    }

    // Custom Fields / Template Accessors
    suspend fun getAllFormTemplates(): List<FormTemplateEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllFormTemplates()
    }

    suspend fun insertFormTemplate(template: FormTemplateEntity) = withContext(Dispatchers.IO) {
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

    suspend fun insertSavedFilter(filter: SavedFilterEntity) = withContext(Dispatchers.IO) {
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