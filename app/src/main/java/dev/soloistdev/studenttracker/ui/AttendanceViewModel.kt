package dev.soloistdev.studenttracker.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    private val _records = MutableStateFlow<List<AttendanceRecordEntity>>(emptyList())
    val records: StateFlow<List<AttendanceRecordEntity>> = _records

    private val _savedFilters = MutableStateFlow<List<SavedFilterEntity>>(emptyList())
    val savedFilters: StateFlow<List<SavedFilterEntity>> = _savedFilters

    private val _currentRoster = MutableStateFlow<List<StudentEntity>>(emptyList())
    val currentRoster: StateFlow<List<StudentEntity>> = _currentRoster

    private val _currentLogs = MutableStateFlow<List<AttendanceLogEntity>>(emptyList())
    val currentLogs: StateFlow<List<AttendanceLogEntity>> = _currentLogs

    // New: Observable state flow holding all logs for the loaded record
    private val _recordLogs = MutableStateFlow<List<AttendanceLogEntity>>(emptyList())
    val recordLogs: StateFlow<List<AttendanceLogEntity>> = _recordLogs

    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())
    val students: StateFlow<List<StudentEntity>> = _students

    private val _templates = MutableStateFlow<List<FormTemplateEntity>>(emptyList())
    val templates: StateFlow<List<FormTemplateEntity>> = _templates

    init {
        loadRecords()
    }

    fun loadRecords() {
        viewModelScope.launch {
            _records.value = repository.getAllAttendanceRecords()
            _savedFilters.value = repository.getAllSavedFilters()
        }
    }

    // New load method triggered on record card tap
    fun loadRecordLogs(recordId: Int) {
        viewModelScope.launch {
            _recordLogs.value = repository.getLogsForRecord(recordId)
            _students.value = repository.getAllActiveStudents()
        }
    }

    fun createRecord(name: String, filterId: Int, start: Long, end: Long) {
        viewModelScope.launch {
            val recordId = repository.insertAttendanceRecord(
                AttendanceRecordEntity(
                    name = name.trim(),
                    savedFilterId = filterId,
                    startDate = start,
                    endDate = end
                )
            ).toInt()

            val studentsList = repository.getAllActiveStudents()
            val filter = _savedFilters.value.find { it.id == filterId }

            if (filter != null) {
                val matchedStudents = studentsList.filter { student ->
                    val value = getFieldValue(student, filter.fieldName)
                    evaluateCondition(value, filter.comparison, filter.value1, filter.value2)
                }

                val daysList = generateDateList(start, end)
                daysList.forEach { date ->
                    matchedStudents.forEach { student ->
                        repository.insertAttendanceLog(
                            AttendanceLogEntity(
                                recordId = recordId,
                                dateMillis = date,
                                studentId = student.id,
                                status = "NOT_SET"
                            )
                        )
                    }
                }
            }
            loadRecords()
        }
    }

    fun deleteRecord(recordId: Int) {
        viewModelScope.launch {
            repository.deleteAttendanceRecord(recordId)
            loadRecords()
        }
    }

    fun loadSheetData(record: AttendanceRecordEntity, dateMillis: Long) {
        viewModelScope.launch {
            val logs = repository.getLogsForDate(record.id, dateMillis)
            val studentsList = repository.getAllActiveStudents()

            val filter = _savedFilters.value.find { it.id == record.savedFilterId }
            if (filter != null) {
                val matched = studentsList.filter { student ->
                    val value = getFieldValue(student, filter.fieldName)
                    evaluateCondition(value, filter.comparison, filter.value1, filter.value2)
                }
                _currentRoster.value = matched
            } else {
                _currentRoster.value = emptyList()
            }
            _currentLogs.value = logs
        }
    }

    fun updateStatus(recordId: Int, dateMillis: Long, studentId: Int, newStatus: String) {
        viewModelScope.launch {
            repository.updateAttendanceStatus(recordId, dateMillis, studentId, newStatus)
            _currentLogs.value = repository.getLogsForDate(recordId, dateMillis)
            _recordLogs.value = repository.getLogsForRecord(recordId) // Updates date list count dynamically [1]
        }
    }

    fun markAllUnmarkedPresent(recordId: Int, dateMillis: Long) {
        viewModelScope.launch {
            _currentLogs.value.forEach { log ->
                if (log.status == "NOT_SET") {
                    repository.updateAttendanceStatus(recordId, dateMillis, log.studentId, "PRESENT")
                }
            }
            _currentLogs.value = repository.getLogsForDate(recordId, dateMillis)
            _recordLogs.value = repository.getLogsForRecord(recordId) // Updates date list count dynamically [1]
        }
    }

    fun resetAllMarks(recordId: Int, dateMillis: Long) {
        viewModelScope.launch {
            _currentLogs.value.forEach { log ->
                repository.updateAttendanceStatus(recordId, dateMillis, log.studentId, "NOT_SET")
            }
            _currentLogs.value = repository.getLogsForDate(recordId, dateMillis)
            _recordLogs.value = repository.getLogsForRecord(recordId) // Updates date list count dynamically [1]
        }
    }

    fun exportSheetToCsv(context: Context, record: AttendanceRecordEntity, dateMillis: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dateStr = sdf.format(Date(dateMillis))

            val csvHeader = "Last Name,First Name,Attendance Status,Date\n"
            val csvContent = StringBuilder(csvHeader)

            val roster = _currentRoster.value
            val logs = _currentLogs.value

            roster.forEach { student ->
                val log = logs.find { it.studentId == student.id }
                val status = log?.status ?: "NOT_SET"
                csvContent.append("${student.lastName},${student.firstName},$status,$dateStr\n")
            }

            val cacheDir = File(context.cacheDir, "attendance_exports").apply { mkdirs() }
            val csvFile = File(cacheDir, "Attendance_${record.name.replace(" ", "_")}_$dateStr.csv")
            if (csvFile.exists()) csvFile.delete()

            FileOutputStream(csvFile).use { fos ->
                fos.write(csvContent.toString().toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Export Attendance CSV"))
            }
        }
    }

    fun generateDateList(startDate: Long, endDate: Long): List<Long> {
        val dates = mutableListOf<Long>()
        val startCal = Calendar.getInstance().apply {
            timeInMillis = startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = endDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (!startCal.after(endCal)) {
            dates.add(startCal.timeInMillis)
            startCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dates
    }

    private fun getFieldValue(student: StudentEntity, field: String): String {
        return when (field) {
            "First Name" -> student.firstName
            "Last Name" -> student.lastName
            "Gender" -> if (student.gender == "F") "Female" else "Male"
            "Address" -> student.address
            "Age" -> {
                val age = Calendar.getInstance().get(Calendar.YEAR) - Calendar.getInstance().apply { timeInMillis = student.birthday }.get(Calendar.YEAR)
                age.toString()
            }
            "Birthday" -> student.birthday.toString()
            else -> {
                try {
                    JSONObject(student.customDataJson).optString(field, "")
                } catch (e: Exception) {
                    ""
                }
            }
        }
    }

    private fun evaluateCondition(fieldVal: String, operator: String, v1: String, v2: String): Boolean {
        val cleanVal = fieldVal.trim()
        if (operator in listOf("birth_year", "birth_month", "birth_month_year", "exact_birthday")) {
            val studentBday = cleanVal.toLongOrNull() ?: return false
            val studentCal = Calendar.getInstance().apply { timeInMillis = studentBday }
            return when (operator) {
                "birth_year" -> {
                    val yearVal = v1.toIntOrNull() ?: return false
                    studentCal.get(Calendar.YEAR) == yearVal
                }
                "birth_month" -> {
                    val monthVal = v1.toIntOrNull() ?: return false
                    (studentCal.get(Calendar.MONTH) + 1) == monthVal
                }
                "birth_month_year" -> {
                    val monthVal = v1.toIntOrNull() ?: return false
                    val yearVal = v2.toIntOrNull() ?: return false
                    (studentCal.get(Calendar.MONTH) + 1) == monthVal && studentCal.get(Calendar.YEAR) == yearVal
                }
                "exact_birthday" -> {
                    val targetBday = v1.toLongOrNull() ?: return false
                    val calFilter = Calendar.getInstance().apply { timeInMillis = targetBday }
                    studentCal.get(Calendar.YEAR) == calFilter.get(Calendar.YEAR) &&
                            studentCal.get(Calendar.DAY_OF_YEAR) == calFilter.get(Calendar.DAY_OF_YEAR)
                }
                else -> false
            }
        }
        return when (operator) {
            "contains" -> cleanVal.contains(v1, ignoreCase = true)
            "does not contain" -> !cleanVal.contains(v1, ignoreCase = true)
            "equal" -> cleanVal.equals(v1, ignoreCase = true)
            "not equal" -> !cleanVal.equals(v1, ignoreCase = true)
            "greater than" -> (cleanVal.toDoubleOrNull() ?: 0.0) > (v1.toDoubleOrNull() ?: 0.0)
            "less than" -> (cleanVal.toDoubleOrNull() ?: 0.0) < (v1.toDoubleOrNull() ?: 0.0)
            "In between" -> {
                val num = cleanVal.toDoubleOrNull() ?: 0.0
                val min = v1.toDoubleOrNull() ?: 0.0
                val max = v2.toDoubleOrNull() ?: 0.0
                num in min..max
            }
            else -> true
        }
    }
}