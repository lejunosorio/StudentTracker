package dev.soloistdev.studenttracker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    // Recycle Bin & Data Purging
    suspend fun getAllDeletedStudents(): List<StudentEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllDeletedStudents()
    }

    suspend fun restoreStudent(studentId: Int) = withContext(Dispatchers.IO) {
        studentDao.restoreStudent(studentId)
    }

    suspend fun permanentDeleteStudent(studentId: Int) = withContext(Dispatchers.IO) {
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
}