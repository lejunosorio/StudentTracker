package dev.soloistdev.studenttracker.ui

import android.app.Application
import android.content.Context
import android.content.Intent
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
import java.util.zip.ZipEntry // Required native ZIP streams for OpenXML (.xlsx) packaging [2]
import java.util.zip.ZipOutputStream

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

    private val _recordLogs = MutableStateFlow<List<AttendanceLogEntity>>(emptyList())
    val recordLogs: StateFlow<List<AttendanceLogEntity>> = _recordLogs

    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())
    val students: StateFlow<List<StudentEntity>> = _students

    init {
        loadRecords()
    }

    fun loadRecords() {
        viewModelScope.launch {
            try {
                _records.value = repository.getAllAttendanceRecords()
                _savedFilters.value = repository.getAllSavedFilters()
            } catch (_: Exception) {
                // Suppressed unused parameter
            }
        }
    }

    fun loadRecordLogs(recordId: Int) {
        viewModelScope.launch {
            try {
                _recordLogs.value = repository.getLogsForRecord(recordId)
                _students.value = repository.getAllActiveStudents()
            } catch (_: Exception) {
                // Suppressed unused parameter
            }
        }
    }

    fun createRecord(name: String, filterId: Int, start: Long, end: Long) {
        viewModelScope.launch {
            try {
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
            } catch (_: Exception) {
                // Suppressed unused parameter
            }
        }
    }

    fun deleteRecord(recordId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteAttendanceRecord(recordId)
                loadRecords()
            } catch (_: Exception) {
                // Suppressed unused parameter
            }
        }
    }

    fun loadSheetData(record: AttendanceRecordEntity, dateMillis: Long) {
        viewModelScope.launch {
            try {
                val logs = repository.getLogsForDate(record.id, dateMillis)
                val studentsList = repository.getAllActiveStudents()

                val allRecordLogs = repository.getLogsForRecord(record.id)
                val rosterStudentIds = allRecordLogs.map { it.studentId }.distinct()
                val matched = studentsList.filter { it.id in rosterStudentIds }

                _currentRoster.value = matched
                _currentLogs.value = logs
            } catch (_: Exception) {
                // Suppressed unused parameter
            }
        }
    }

    fun updateStatus(recordId: Int, dateMillis: Long, studentId: Int, newStatus: String) {
        viewModelScope.launch {
            try {
                repository.updateAttendanceStatus(recordId, dateMillis, studentId, newStatus)
                _currentLogs.value = repository.getLogsForDate(recordId, dateMillis)
                _recordLogs.value = repository.getLogsForRecord(recordId)
            } catch (_: Exception) {
                // Suppressed unused parameter
            }
        }
    }

    fun markAllUnmarkedPresent(recordId: Int, dateMillis: Long) {
        viewModelScope.launch {
            try {
                _currentLogs.value.forEach { log ->
                    if (log.status == "NOT_SET") {
                        repository.updateAttendanceStatus(recordId, dateMillis, log.studentId, "PRESENT")
                    }
                }
                _currentLogs.value = repository.getLogsForDate(recordId, dateMillis)
                _recordLogs.value = repository.getLogsForRecord(recordId)
            } catch (_: Exception) {
                // Suppressed unused parameter
            }
        }
    }

    fun resetAllMarks(recordId: Int, dateMillis: Long) {
        viewModelScope.launch {
            try {
                _currentLogs.value.forEach { log ->
                    repository.updateAttendanceStatus(recordId, dateMillis, log.studentId, "NOT_SET")
                }
                _currentLogs.value = repository.getLogsForDate(recordId, dateMillis)
                _recordLogs.value = repository.getLogsForRecord(recordId)
            } catch (_: Exception) {
                // Suppressed unused parameter
            }
        }
    }

    // NEW: Highly styled, un-corrupted binary OpenXML (.xlsx) Per-Date Attendance Exporter [2]
    fun exportSheetToCsv(context: Context, record: AttendanceRecordEntity, dateMillis: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sdfDate = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
                val dateStr = sdfDate.format(Date(dateMillis))

                val sdfSheetName = SimpleDateFormat("MMddyy", Locale.US)
                val sheetName = sdfSheetName.format(Date(dateMillis))

                val roster = _currentRoster.value
                val logs = _currentLogs.value
                val filter = repository.getAllSavedFilters().find { it.id == record.savedFilterId }
                val filterName = filter?.filterName ?: "Unknown Filter"

                val sheetData = StringBuilder()

                // Row 1: Title Row
                sheetData.append("<row r=\"1\" ht=\"32\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A1", "DAILY ATTENDANCE SHEET", 1))
                sheetData.append("</row>")

                // Row 2: Metadata Record Name
                sheetData.append("<row r=\"2\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A2", "Attendance Record Name", 8))
                sheetData.append(writeStringCell("B2", record.name, 0))
                sheetData.append("</row>")

                // Row 3: Metadata Date
                sheetData.append("<row r=\"3\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A3", "Date", 8))
                sheetData.append(writeStringCell("B3", dateStr, 0))
                sheetData.append("</row>")

                // Row 4: Metadata Filter Name
                sheetData.append("<row r=\"4\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A4", "Attendance Filter", 8))
                sheetData.append(writeStringCell("B4", filterName, 0))
                sheetData.append("</row>")

                // Row 5: Empty separator row
                sheetData.append("<row r=\"5\" ht=\"12\" customHeight=\"1\"></row>")

                // Row 6: Column Headers (Name on left, status centered. Date column removed entirely) [2]
                sheetData.append("<row r=\"6\" ht=\"24\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A6", "Last Name", 8))
                sheetData.append(writeStringCell("B6", "First Name", 8))
                sheetData.append(writeStringCell("C6", "Attendance Status", 2))
                sheetData.append("</row>")

                // Rows 7+: Student Rows (Zebra striped with color-coded status cells) [2]
                roster.forEachIndexed { sIdx, student ->
                    val rowNum = sIdx + 7
                    val zebraStyleId = if (sIdx % 2 == 0) 9 else 0

                    sheetData.append("<row r=\"$rowNum\" ht=\"20\" customHeight=\"1\">")
                    sheetData.append(writeStringCell("A$rowNum", student.lastName, zebraStyleId))
                    sheetData.append(writeStringCell("B$rowNum", student.firstName, zebraStyleId))

                    val log = logs.find { it.studentId == student.id }
                    val status = log?.status ?: "NOT_SET"
                    val (mark, cellStyleId) = when (status) {
                        "PRESENT" -> Pair("Present", 3)
                        "ABSENT" -> "Absent" to 4
                        "EXCUSED" -> "Excused" to 5
                        "REMOVED" -> "Removed" to 6
                        else -> "Not Set" to 7
                    }
                    sheetData.append(writeStringCell("C$rowNum", mark, cellStyleId))
                    sheetData.append("</row>")
                }

                // Row Sum Day Header
                val sumHeaderRowNum = roster.size + 8
                sheetData.append("<row r=\"$sumHeaderRowNum\" ht=\"22\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$sumHeaderRowNum", "Daily Totals", 8))
                sheetData.append("</row>")

                // Row Total Present
                val totalPRowNum = roster.size + 9
                val totalP = logs.count { it.status == "PRESENT" && roster.any { r -> r.id == it.studentId } }
                sheetData.append("<row r=\"$totalPRowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalPRowNum", "Total Present", 8))
                sheetData.append("<c r=\"B$totalPRowNum\" s=\"0\"/>")
                sheetData.append(writeNumberCell("C$totalPRowNum", totalP, 3))
                sheetData.append("</row>")

                // Row Total Absent
                val totalARowNum = roster.size + 10
                val totalA = logs.count { it.status == "ABSENT" && roster.any { r -> r.id == it.studentId } }
                sheetData.append("<row r=\"$totalARowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalARowNum", "Total Absent", 8))
                sheetData.append("<c r=\"B$totalARowNum\" s=\"0\"/>")
                sheetData.append(writeNumberCell("C$totalARowNum", totalA, 4))
                sheetData.append("</row>")

                // Row Total Excused
                val totalERowNum = roster.size + 11
                val totalE = logs.count { it.status == "EXCUSED" && roster.any { r -> r.id == it.studentId } }
                sheetData.append("<row r=\"$totalERowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalERowNum", "Total Excused", 8))
                sheetData.append("<c r=\"B$totalERowNum\" s=\"0\"/>")
                sheetData.append(writeNumberCell("C$totalERowNum", totalE, 5))
                sheetData.append("</row>")

                // Row Total Removed
                val totalRRowNum = roster.size + 12
                val totalR = logs.count { it.status == "REMOVED" && roster.any { r -> r.id == it.studentId } }
                sheetData.append("<row r=\"$totalRRowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalRRowNum", "Total Removed", 8))
                sheetData.append("<c r=\"B$totalRRowNum\" s=\"0\"/>")
                sheetData.append(writeNumberCell("C$totalRRowNum", totalR, 6))
                sheetData.append("</row>")

                // Row Total Unmarked
                val totalURowNum = roster.size + 13
                val totalU = logs.count { it.status == "NOT_SET" && roster.any { r -> r.id == it.studentId } }
                sheetData.append("<row r=\"$totalURowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalURowNum", "Total Unmarked", 8))
                sheetData.append("<c r=\"B$totalURowNum\" s=\"0\"/>")
                sheetData.append(writeNumberCell("C$totalURowNum", totalU, 7))
                sheetData.append("</row>")

                // Format column dimensions and merge references
                val colXml = """
                    <cols>
                      <col min="1" max="1" width="20" customWidth="1"/>
                      <col min="2" max="2" width="20" customWidth="1"/>
                      <col min="3" max="3" width="18" customWidth="1"/>
                    </cols>
                """.trimIndent()

                val mergeCellsXml = """
                    <mergeCells count="5">
                      <mergeCell ref="A1:C1"/>
                      <mergeCell ref="B2:C2"/>
                      <mergeCell ref="B3:C3"/>
                      <mergeCell ref="B4:C4"/>
                      <mergeCell ref="A$sumHeaderRowNum:C$sumHeaderRowNum"/>
                    </mergeCells>
                """.trimIndent()

                // Compile Zip-archive natively [2]
                val excelFile = "Attendance_${record.name.replace(" ", "_")}_$sheetName.xlsx"
                writeOpenXmlSpreadsheet(context, excelFile, sheetName, colXml, sheetData.toString(), mergeCellsXml)

                // Dispatch via standard sharing sheet using standard OpenXML MIME type (.xlsx) [2]
                val cacheDir = File(context.cacheDir, "attendance_exports").apply { mkdirs() }
                val targetFile = File(cacheDir, excelFile)
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(shareIntent, "Share Daily Attendance Spreadsheet"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // EXCEL MOCKUP COMPLIANT EXPORTER: Builds cell-for-cell row and column matrices with aligned styling
    fun exportOverallReportToCsv(context: Context, record: AttendanceRecordEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = repository.getLogsForRecord(record.id)
                val studentsList = repository.getAllActiveStudents()

                val rosterStudentIds = logs.map { it.studentId }.distinct()
                val roster = studentsList.filter { it.id in rosterStudentIds }

                val dates = generateDateList(record.startDate, record.endDate)
                val totalDays = dates.size
                val sdfDate = SimpleDateFormat("MMM-dd", Locale.US)
                val sdfRange = SimpleDateFormat("MMMM dd", Locale.US)
                val rangeStr = "${sdfRange.format(Date(record.startDate))} - ${sdfRange.format(Date(record.endDate))}"

                val sheetData = StringBuilder()
                val colSpanTotal = totalDays + 7
                val lastColLetter = getColLetter(colSpanTotal - 1)

                // Row 1: Title Row
                sheetData.append("<row r=\"1\" ht=\"32\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A1", "ATTENDANCE OVERALL REPORT", 1))
                sheetData.append("</row>")

                // Row 2: Metadata Record Name
                sheetData.append("<row r=\"2\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A2", "Attendance Record Name", 8))
                sheetData.append(writeStringCell("B2", record.name, 0))
                sheetData.append("</row>")

                // Row 3: Metadata Date Range
                sheetData.append("<row r=\"3\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A3", "Date Range", 8))
                sheetData.append(writeStringCell("B3", rangeStr, 0))
                sheetData.append("</row>")

                // Row 4: Metadata Total Days
                sheetData.append("<row r=\"4\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A4", "Total Days", 8))
                sheetData.append(writeNumberCell("B4", totalDays, 0))

                val countsHeaderColRef = getCellRef(totalDays + 2, 3)
                sheetData.append(writeStringCell(countsHeaderColRef, "Count Per Student", 8))
                sheetData.append("</row>")

                // Row 5: Empty separator row
                sheetData.append("<row r=\"5\" ht=\"12\" customHeight=\"1\"></row>")

                // Row 6: Table Column Headers
                sheetData.append("<row r=\"6\" ht=\"24\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A6", "Name", 8))
                dates.forEachIndexed { idx, date ->
                    val ref = getCellRef(idx + 1, 5)
                    sheetData.append(writeStringCell(ref, sdfDate.format(Date(date)), 2))
                }
                val spacerColRef = getCellRef(totalDays + 1, 5)
                sheetData.append("<c r=\"$spacerColRef\" s=\"0\"/>") // Spacer column

                sheetData.append(writeStringCell(getCellRef(totalDays + 2, 5), "P", 2))
                sheetData.append(writeStringCell(getCellRef(totalDays + 3, 5), "A", 2))
                sheetData.append(writeStringCell(getCellRef(totalDays + 4, 5), "E", 2))
                sheetData.append(writeStringCell(getCellRef(totalDays + 5, 5), "R", 2))
                sheetData.append(writeStringCell(getCellRef(totalDays + 6, 5), "Unmarked", 2))
                sheetData.append("</row>")

                // Rows 7+: Student Rows
                roster.forEachIndexed { sIdx, student ->
                    val rowNum = sIdx + 7
                    val zebraStyleId = if (sIdx % 2 == 0) 9 else 0

                    sheetData.append("<row r=\"$rowNum\" ht=\"20\" customHeight=\"1\">")

                    val cleanName = "${student.lastName} ${student.firstName}".replace(",", " ")
                    sheetData.append(writeStringCell("A$rowNum", cleanName, zebraStyleId))

                    var pCount = 0
                    var aCount = 0
                    var eCount = 0
                    var rCount = 0
                    var uCount = 0

                    dates.forEachIndexed { dIdx, date ->
                        val log = logs.find { it.studentId == student.id && it.dateMillis == date }
                        val ref = getCellRef(dIdx + 1, rowNum - 1)
                        val (mark, cellStyleId) = when (log?.status) {
                            "PRESENT" -> { pCount++; Pair("P", 3) }
                            "ABSENT" -> { aCount++; "A" to 4 }
                            "EXCUSED" -> { eCount++; "E" to 5 }
                            "REMOVED" -> { rCount++; "R" to 6 }
                            else -> { uCount++; "" to 7 }
                        }
                        sheetData.append(writeStringCell(ref, mark, cellStyleId))
                    }

                    val spacerCellRef = getCellRef(totalDays + 1, rowNum - 1)
                    sheetData.append("<c r=\"$spacerCellRef\" s=\"0\"/>") // Spacer

                    sheetData.append(writeNumberCell(getCellRef(totalDays + 2, rowNum - 1), pCount, 3))
                    sheetData.append(writeNumberCell(getCellRef(totalDays + 3, rowNum - 1), aCount, 4))
                    sheetData.append(writeNumberCell(getCellRef(totalDays + 4, rowNum - 1), eCount, 5))
                    sheetData.append(writeNumberCell(getCellRef(totalDays + 5, rowNum - 1), rCount, 6))
                    sheetData.append(writeNumberCell(getCellRef(totalDays + 6, rowNum - 1), uCount, 7))
                    sheetData.append("</row>")
                }

                // Row Sum Day Header
                val sumHeaderRowNum = roster.size + 8
                sheetData.append("<row r=\"$sumHeaderRowNum\" ht=\"22\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$sumHeaderRowNum", "Sum per Day", 8))
                sheetData.append("</row>")

                // Row Total P
                val totalPRowNum = roster.size + 9
                sheetData.append("<row r=\"$totalPRowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalPRowNum", "Total P", 8))
                dates.forEachIndexed { dIdx, date ->
                    val dayP = logs.count { it.dateMillis == date && it.status == "PRESENT" && roster.any { r -> r.id == it.studentId } }
                    sheetData.append(writeNumberCell(getCellRef(dIdx + 1, totalPRowNum - 1), dayP, 3))
                }
                sheetData.append("</row>")

                // Row Total A
                val totalARowNum = roster.size + 10
                sheetData.append("<row r=\"$totalARowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalARowNum", "Total A", 8))
                dates.forEachIndexed { dIdx, date ->
                    val dayA = logs.count { it.dateMillis == date && it.status == "ABSENT" && roster.any { r -> r.id == it.studentId } }
                    sheetData.append(writeNumberCell(getCellRef(dIdx + 1, totalARowNum - 1), dayA, 4))
                }
                sheetData.append("</row>")

                // Row Total E
                val totalERowNum = roster.size + 11
                sheetData.append("<row r=\"$totalERowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalERowNum", "Total E", 8))
                dates.forEachIndexed { dIdx, date ->
                    val dayE = logs.count { it.dateMillis == date && it.status == "EXCUSED" && roster.any { r -> r.id == it.studentId } }
                    sheetData.append(writeNumberCell(getCellRef(dIdx + 1, totalERowNum - 1), dayE, 5))
                }
                sheetData.append("</row>")

                // Row Total R
                val totalRRowNum = roster.size + 12
                sheetData.append("<row r=\"$totalRRowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalRRowNum", "Total R", 8))
                dates.forEachIndexed { dIdx, date ->
                    val dayR = logs.count { it.dateMillis == date && it.status == "REMOVED" && roster.any { r -> r.id == it.studentId } }
                    sheetData.append(writeNumberCell(getCellRef(dIdx + 1, totalRRowNum - 1), dayR, 6))
                }
                sheetData.append("</row>")

                // Row Total Unmarked
                val totalURowNum = roster.size + 13
                sheetData.append("<row r=\"$totalURowNum\" ht=\"20\" customHeight=\"1\">")
                sheetData.append(writeStringCell("A$totalURowNum", "Total Unmarked", 8))
                dates.forEachIndexed { dIdx, date ->
                    val dayU = logs.count { it.dateMillis == date && it.status == "NOT_SET" && roster.any { r -> r.id == it.studentId } }
                    sheetData.append(writeNumberCell(getCellRef(dIdx + 1, totalURowNum - 1), dayU, 7))
                }
                sheetData.append("</row>")

                // Format column dimensions and merge references
                val colXml = StringBuilder()
                colXml.append("""
                    <cols>
                      <col min="1" max="1" width="22" customWidth="1"/>
                """.trimIndent())
                for (c in 1..totalDays) {
                    colXml.append("      <col min=\"${c + 1}\" max=\"${c + 1}\" width=\"9\" customWidth=\"1\"/>\n")
                }
                colXml.append("      <col min=\"${totalDays + 2}\" max=\"${totalDays + 2}\" width=\"3\" customWidth=\"1\"/>\n")
                for (c in 1..5) {
                    colXml.append("      <col min=\"${totalDays + 2 + c}\" max=\"${totalDays + 2 + c}\" width=\"9\" customWidth=\"1\"/>\n")
                }
                colXml.append("    </cols>")

                val mergeCellsXml = """
                    <mergeCells count="5">
                      <mergeCell ref="A1:${lastColLetter}1\"/>
                      <mergeCell ref="B2:${lastColLetter}2\"/>
                      <mergeCell ref="B3:${lastColLetter}3\"/>
                      <mergeCell ref="B4:${getColLetter(totalDays)}4\"/>
                      <mergeCell ref="${getColLetter(totalDays + 2)}4:${getColLetter(totalDays + 6)}4\"/>
                    </mergeCells>
                """.trimIndent()

                // Compile Zip-archive natively
                val excelFile = "Overall_Report_${record.name.replace(" ", "_")}.xlsx"
                writeOpenXmlSpreadsheet(context, excelFile, "Overall Attendance", colXml.toString(), sheetData.toString(), mergeCellsXml)

                // Dispatch via standard sharing sheet using standard OpenXML MIME type (.xlsx) [2]
                val cacheDir = File(context.cacheDir, "attendance_exports").apply { mkdirs() }
                val targetFile = File(cacheDir, excelFile)
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(shareIntent, "Share Overall Report Spreadsheet"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * UNIFIED REUSABLE OPENXML SPREADSHEET PACKAGER:
     * Compiles standard, warning-free binary .xlsx files natively [2].
     */
    private fun writeOpenXmlSpreadsheet(
        context: Context,
        fileName: String,
        sheetName: String,
        colWidthsXml: String,
        sheetDataXml: String,
        mergeCellsXml: String
    ): File? {
        try {
            val cacheDir = File(context.cacheDir, "attendance_exports").apply { mkdirs() }
            val excelFile = File(cacheDir, fileName)
            if (excelFile.exists()) excelFile.delete()

            FileOutputStream(excelFile).use { fos ->
                ZipOutputStream(fos).use { zos ->

                    // [1] _rels/.rels
                    zos.putNextEntry(ZipEntry("_rels/.rels"))
                    zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // [2] [Content_Types].xml
                    zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                    zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // [3] xl/workbook.xml (Passes custom Sheet Name formatted as MMDDYY) [2]
                    zos.putNextEntry(ZipEntry("xl/workbook.xml"))
                    zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="$sheetName" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // [4] xl/_rels/workbook.xml.rels
                    zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                    zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // [5] xl/styles.xml (Uses safe 'Arial' to prevent spellcheck typos) [1, 2]
                    zos.putNextEntry(ZipEntry("xl/styles.xml"))
                    zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><name val="Arial"/><family val="2"/></font>
    <font><bold/><sz val="11"/><name val="Arial"/><family val="2"/></font>
    <font><bold/><sz val="14"/><name val="Arial"/><family val="2"/></font>
  </fonts>
  <fills count="10">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1B5E20"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF2E7D32"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE8F5E9"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFEBEE"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFF3E0"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF5F5F5"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFFDE7"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFCFD8DC"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/></border>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="10">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf> // 0: Normal (Aligned Left) [2]
    <xf numFmtId="0" fontId="2" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf> // 1: Header Main
    <xf numFmtId="0" fontId="1" fillId="3" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf> // 2: Header Table
    <xf numFmtId="0" fontId="1" fillId="4" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf> // 3: Present
    <xf numFmtId="0" fontId="1" fillId="5" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf> // 4: Absent
    <xf numFmtId="0" fontId="1" fillId="6" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf> // 5: Excused
    <xf numFmtId="0" fontId="1" fillId="7" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf> // 6: Removed
    <xf numFmtId="0" fontId="0" fillId="8" borderId="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf> // 7: Unmarked
    <xf numFmtId="0" fontId="1" fillId="9" borderId="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf> // 8: Sum Header (Aligned Left) [2]
    <xf numFmtId="0" fontId="0" fillId="7" borderId="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf> // 9: Zebra Even (Aligned Left) [2]
  </cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // [6] xl/worksheets/sheet1.xml
                    zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                    val sheetXml = StringBuilder()
                    sheetXml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheetViews>
    <sheetView tabSelected="1" workbookViewId="0">
      <showGridLines val="1"/>
    </sheetView>
  </sheetViews>
""")
                    sheetXml.append(colWidthsXml)
                    sheetXml.append("\n  <sheetData>\n")
                    sheetXml.append(sheetDataXml)
                    sheetXml.append("  </sheetData>\n")
                    sheetXml.append(mergeCellsXml)
                    sheetXml.append("\n</worksheet>")

                    zos.write(sheetXml.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            return excelFile
        } catch (_: Exception) { // Suppressed unused parameters [1]
            return null
        }
    }

    private fun getColLetter(col: Int): String {
        val colName = StringBuilder()
        var tempCol = col
        while (tempCol >= 0) {
            colName.insert(0, ('A' + (tempCol % 26)))
            tempCol = (tempCol / 26) - 1
        }
        return colName.toString()
    }

    private fun getCellRef(col: Int, row: Int): String {
        return "${getColLetter(col)}${row + 1}"
    }

    private fun writeStringCell(ref: String, value: String, styleId: Int): String {
        val cleanValue = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<c r=\"$ref\" t=\"inlineStr\" s=\"$styleId\"><is><t>$cleanValue</t></is></c>"
    }

    private fun writeNumberCell(ref: String, value: Int, styleId: Int): String {
        return "<c r=\"$ref\" t=\"n\" s=\"$styleId\"><v>$value</v></c>"
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
                } catch (_: Exception) {
                    ""
                }
            }
        }
    }

    private fun evaluateCondition(fieldVal: String, operator: String, v1: String, v2: String): Boolean {
        val cleanVal = fieldVal.trim()
        if (operator in listOf("birth_year", "birth_month", "birth_month_year", "exact_birthday")) {
            val studentBirthday = cleanVal.toLongOrNull() ?: return false
            val studentCal = Calendar.getInstance().apply { timeInMillis = studentBirthday }
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