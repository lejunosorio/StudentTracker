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

    // Map Archive Accessors (Sprint 7)
    suspend fun getAllMapArchives(): List<MapArchiveEntity> = withContext(Dispatchers.IO) {
        studentDao.getAllMapArchives()
    }

    suspend fun insertMapArchive(archive: MapArchiveEntity) = withContext(Dispatchers.IO) {
        studentDao.insertMapArchive(archive)
    }

    suspend fun setActiveMapArchive(activeId: Int) = withContext(Dispatchers.IO) {
        studentDao.setActiveMapArchive(activeId)
    }

    suspend fun deleteMapArchive(archiveId: Int) = withContext(Dispatchers.IO) {
        studentDao.deleteMapArchive(archiveId)
    }
}