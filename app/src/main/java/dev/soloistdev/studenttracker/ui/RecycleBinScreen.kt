@file:Suppress("DEPRECATION")

package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = viewModel()
) {
    val deletedStudents by viewModel.deletedStudents.collectAsState()
    val deletedTemplates by viewModel.deletedTemplates.collectAsState()
    val deletedFilters by viewModel.deletedFilters.collectAsState()
    val deletedRecords by viewModel.deletedRecords.collectAsState()
    val context = LocalContext.current

    // Active Category Tab: 0 = Students, 1 = Custom Fields, 2 = Saved Filters, 3 = Attendance
    var activeCategoryTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Recycle Bin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Horizontal scrollable Material 3 category select tabs [1]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = activeCategoryTab == 0,
                    onClick = { activeCategoryTab = 0 },
                    label = { Text("Students (${deletedStudents.size})") }
                )
                FilterChip(
                    selected = activeCategoryTab == 1,
                    onClick = { activeCategoryTab = 1 },
                    label = { Text("Fields (${deletedTemplates.size})") }
                )
                FilterChip(
                    selected = activeCategoryTab == 2,
                    onClick = { activeCategoryTab = 2 },
                    label = { Text("Filters (${deletedFilters.size})") }
                )
                FilterChip(
                    selected = activeCategoryTab == 3,
                    onClick = { activeCategoryTab = 3 },
                    label = { Text("Attendance (${deletedRecords.size})") }
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = when (activeCategoryTab) {
                            1 -> "Deleted Custom Schema Fields"
                            2 -> "Deleted Saved Filters"
                            3 -> "Deleted Attendance Records"
                            else -> "Deleted Student Directory Profiles"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Render lists dynamically based on active categories [1]
                when (activeCategoryTab) {
                    0 -> {
                        // ================= TYPE 0: DELETED STUDENTS =================
                        if (deletedStudents.isEmpty()) {
                            item { EmptyStateLabel() }
                        } else {
                            items(deletedStudents) { student ->
                                RecycleBinRowItem(
                                    title = "${student.lastName}, ${student.firstName}",
                                    subtitle = "Gender: ${if (student.gender == "F") "Female" else "Male"}\nSaved to local storage sandbox",
                                    onRestore = {
                                        viewModel.restoreStudent(student.id)
                                        Toast.makeText(context, "${student.firstName} restored successfully.", Toast.LENGTH_SHORT).show()
                                    },
                                    onPurge = {
                                        viewModel.permanentDeleteStudent(student.id)
                                        Toast.makeText(context, "${student.firstName} permanently deleted.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                    1 -> {
                        // ================= TYPE 1: DELETED CUSTOM FIELDS =================
                        if (deletedTemplates.isEmpty()) {
                            item { EmptyStateLabel() }
                        } else {
                            items(deletedTemplates) { template ->
                                RecycleBinRowItem(
                                    title = template.fieldName.replace("_", " "),
                                    subtitle = "Type: ${template.fieldType} | Required: ${if (template.isRequired) "Yes" else "No"}",
                                    onRestore = {
                                        viewModel.restoreTemplate(template.id)
                                        Toast.makeText(context, "${template.fieldName} restored successfully.", Toast.LENGTH_SHORT).show()
                                    },
                                    onPurge = {
                                        viewModel.permanentDeleteTemplate(template.id)
                                        Toast.makeText(context, "${template.fieldName} permanently deleted.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                    2 -> {
                        // ================= TYPE 2: DELETED SAVED FILTERS =================
                        if (deletedFilters.isEmpty()) {
                            item { EmptyStateLabel() }
                        } else {
                            items(deletedFilters) { filter ->
                                RecycleBinRowItem(
                                    title = filter.filterName,
                                    subtitle = "Field: ${filter.fieldName.replace("_", " ")} | ${filter.comparison} ${filter.value1}",
                                    onRestore = {
                                        viewModel.restoreFilter(filter.id)
                                        Toast.makeText(context, "${filter.filterName} restored successfully.", Toast.LENGTH_SHORT).show()
                                    },
                                    onPurge = {
                                        viewModel.permanentDeleteSavedFilter(filter.id)
                                        Toast.makeText(context, "${filter.filterName} permanently deleted.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                    3 -> {
                        // ================= TYPE 3: DELETED ATTENDANCE RECORDS =================
                        if (deletedRecords.isEmpty()) {
                            item { EmptyStateLabel() }
                        } else {
                            items(deletedRecords) { record ->
                                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                                val dateStr = "${sdf.format(Date(record.startDate))} - ${sdf.format(Date(record.endDate))}"
                                RecycleBinRowItem(
                                    title = record.name,
                                    subtitle = "Active Range: $dateStr",
                                    onRestore = {
                                        viewModel.restoreAttendanceRecord(record.id)
                                        Toast.makeText(context, "${record.name} restored successfully.", Toast.LENGTH_SHORT).show()
                                    },
                                    onPurge = {
                                        viewModel.permanentDeleteAttendanceRecord(record.id)
                                        Toast.makeText(context, "${record.name} permanently deleted.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateLabel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Recycle bin is empty in this category.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
    }
}

@Composable
fun RecycleBinRowItem(
    title: String,
    subtitle: String,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Restore Action [1]
                IconButton(
                    onClick = onRestore,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(20.dp))
                }

                // Permanent Purge Action [1]
                IconButton(
                    onClick = onPurge,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Purge", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}