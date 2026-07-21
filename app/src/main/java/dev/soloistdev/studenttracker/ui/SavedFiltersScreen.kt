package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.SavedFilterEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

// VIEW MODEL: Unchanged, coordinating database states
class SavedFiltersViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    private val _filters = MutableStateFlow<List<SavedFilterEntity>>(emptyList())
    val filters: StateFlow<List<SavedFilterEntity>> = _filters

    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())
    val students: StateFlow<List<StudentEntity>> = _students

    private val _templates = MutableStateFlow<List<FormTemplateEntity>>(emptyList())
    val templates: StateFlow<List<FormTemplateEntity>> = _templates

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _filters.value = repository.getAllSavedFilters()
            _students.value = repository.getAllActiveStudents()
            _templates.value = repository.getAllFormTemplates()
        }
    }

    fun saveFilter(entity: SavedFilterEntity) {
        viewModelScope.launch {
            val maxOrder = _filters.value.maxOfOrNull { it.displayOrder } ?: 0
            val filterToInsert = entity.copy(displayOrder = maxOrder + 1)
            repository.insertSavedFilter(filterToInsert)
            loadData()
        }
    }

    fun deleteFilter(filterId: Int) {
        viewModelScope.launch {
            repository.deleteSavedFilter(filterId)
            loadData()
        }
    }

    fun moveFilter(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _filters.value.indices || toIndex !in _filters.value.indices) return
        viewModelScope.launch {
            val currentList = _filters.value.toMutableList()
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)

            val updatedList = currentList.mapIndexed { index, filter ->
                filter.copy(displayOrder = index)
            }
            _filters.value = updatedList
            repository.updateAllSavedFilterOrders(updatedList)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFiltersScreen(
    onBack: () -> Unit,
    onStudentClick: (Int) -> Unit,
    viewModel: SavedFiltersViewModel = viewModel()
) {
    val filters by viewModel.filters.collectAsState()
    val students by viewModel.students.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val context = LocalContext.current

    var activeFilterForListing by remember { mutableStateOf<SavedFilterEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingFilter by remember { mutableStateOf<SavedFilterEntity?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (activeFilterForListing != null) {
                            "${activeFilterForListing!!.filterName} List"
                        } else {
                            "Saved Filters"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeFilterForListing != null) {
                                activeFilterForListing = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeFilterForListing == null) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Filter")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val activeFilter = activeFilterForListing
            if (activeFilter != null) {
                // ================= STATE B: FILTERED STUDENTS DIRECTORY =================
                val filteredList = remember(students, activeFilter) {
                    students.filter { student ->
                        val valueToCompare = getFieldValue(student, activeFilter.fieldName)
                        evaluateCondition(valueToCompare, activeFilter.comparison, activeFilter.value1, activeFilter.value2)
                    }
                }

                if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No students match this filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            Text(
                                text = "Filtered Members (${filteredList.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        items(filteredList) { student ->
                            StudentCard(
                                student = student,
                                isSelected = false,
                                onClick = { onStudentClick(student.id) },
                                onLongClick = {}
                            )
                        }
                    }
                }
            } else {
                // ================= STATE A: FILTER WORKBENCH CARDS (Default) =================
                if (filters.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved filters.\nTap '+' to create a workbench group.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val lazyListState = rememberLazyListState()
                    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }

                    // Tracks accumulated Y offset value of active card during drag [1]
                    var draggedOffsetY by remember { mutableStateOf(0f) }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(filters) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val layoutInfo = lazyListState.layoutInfo
                                        val item = layoutInfo.visibleItemsInfo.firstOrNull { visibleItem ->
                                            offset.y.toInt() in visibleItem.offset..(visibleItem.offset + visibleItem.size)
                                        }
                                        item?.let {
                                            if (it.index > 0) {
                                                draggedItemIndex = it.index - 1
                                                draggedOffsetY = 0f // Reset offset on drag start [1]
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedItemIndex = null
                                        draggedOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggedItemIndex = null
                                        draggedOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val currentIndex = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                                        draggedOffsetY += dragAmount.y // Accumulate continuous Y displacement [1]

                                        val layoutInfo = lazyListState.layoutInfo
                                        val itemBelow = layoutInfo.visibleItemsInfo.firstOrNull { visibleItem ->
                                            change.position.y.toInt() in visibleItem.offset..(visibleItem.offset + visibleItem.size)
                                        }
                                        itemBelow?.let { visibleItem ->
                                            val targetIndex = visibleItem.index - 1
                                            if (targetIndex in filters.indices && targetIndex != currentIndex) {
                                                viewModel.moveFilter(currentIndex, targetIndex)
                                                // Reset displacement during list-index swap to prevent visual jumping [1]
                                                draggedOffsetY = 0f
                                                draggedItemIndex = targetIndex
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        item {
                            Text(
                                text = "Filter Groups (Tap to View • Drag to Reorder)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        itemsIndexed(
                            items = filters,
                            key = { _, item -> item.id }
                        ) { index, filter ->
                            val isDragging = draggedItemIndex == index
                            val matchingCount = remember(students, filter) {
                                students.count { student ->
                                    val valueToCompare = getFieldValue(student, filter.fieldName)
                                    evaluateCondition(valueToCompare, filter.comparison, filter.value1, filter.value2)
                                }
                            }

                            // Dynamic physical float settings [1]
                            val cardElevation = if (isDragging) 16.dp else 1.dp
                            val cardScale = if (isDragging) 1.05f else 1.0f
                            val cardAlpha = if (isDragging) 0.92f else 1.0f
                            val cardTranslationY = if (isDragging) draggedOffsetY else 0f
                            val cardBorder = if (isDragging) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else null

                            var showDeleteDialog by remember { mutableStateOf(false) }

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            editingFilter = filter
                                            false
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            showDeleteDialog = true
                                            false
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier
                                    .animateItem()
                                    .zIndex(if (isDragging) 10f else 0f),
                                enableDismissFromStartToEnd = !isDragging, // Disable swipes during drag interactions
                                enableDismissFromEndToStart = !isDragging,
                                backgroundContent = {
                                    val color = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        else -> Color.Transparent
                                    }
                                    val alignment = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        else -> Alignment.Center
                                    }
                                    val icon = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
                                        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                                        else -> Icons.Default.Delete
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(color),
                                        contentAlignment = alignment
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                },
                                content = {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .graphicsLayer {
                                                scaleX = cardScale
                                                scaleY = cardScale
                                                alpha = cardAlpha
                                                translationY = cardTranslationY // Physically glides card along Y-axis [1]
                                                shadowElevation = if (isDragging) 24f else 0f // Suspends real shadow depth [1]
                                            }
                                            .clickable { activeFilterForListing = filter },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDragging) {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ),
                                        elevation = CardDefaults.cardElevation(
                                            defaultElevation = cardElevation
                                        ),
                                        border = cardBorder
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "${filter.filterName} ($matchingCount)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                                Text(
                                                    text = "Field: ${filter.fieldName.replace("_", " ")} | ${filter.comparison} ${filter.value1}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Drag to reorder",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            )

                            if (showDeleteDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteDialog = false },
                                    title = { Text("Delete Filter?") },
                                    text = { Text("Are you sure you want to delete the '${filter.filterName}' group?") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                showDeleteDialog = false
                                                viewModel.deleteFilter(filter.id)
                                                Toast.makeText(context, "Filter deleted.", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) { Text("Delete") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                                    },
                                    shape = RoundedCornerShape(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ADD FILTER DIALOG OVERLAY
        if (showAddDialog) {
            FilterDialogForm(
                templates = templates,
                onDismiss = { showAddDialog = false },
                onSave = { newFilter ->
                    viewModel.saveFilter(newFilter)
                    showAddDialog = false
                    Toast.makeText(context, "Filter saved!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // EDIT FILTER DIALOG OVERLAY
        editingFilter?.let { filter ->
            FilterDialogForm(
                templates = templates,
                existingFilter = filter,
                onDismiss = { editingFilter = null },
                onSave = { updatedFilter ->
                    viewModel.saveFilter(updatedFilter)
                    editingFilter = null
                    Toast.makeText(context, "Filter updated!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}


@Composable
fun FilterDialogForm(
    templates: List<FormTemplateEntity>,
    existingFilter: SavedFilterEntity? = null,
    onDismiss: () -> Unit,
    onSave: (SavedFilterEntity) -> Unit
) {
    var name by remember { mutableStateOf(existingFilter?.filterName ?: "") }
    var field by remember { mutableStateOf(existingFilter?.fieldName ?: "Age") }
    var comparison by remember { mutableStateOf(existingFilter?.comparison ?: "contains") }
    var val1 by remember { mutableStateOf(existingFilter?.value1 ?: "") }
    var val2 by remember { mutableStateOf(existingFilter?.value2 ?: "") }

    val coreFields = listOf("First Name", "Last Name", "Gender", "Birthday", "Address", "Age")
    val fieldsList = remember {
        val list = coreFields.toMutableList()
        templates.forEach { list.add(it.fieldName) }
        list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingFilter == null) "New Saved Filter" else "Edit Saved Filter", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Filter Group Name *") },
                    modifier = Modifier.fillMaxWidth()
                )

                var fieldExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = field.replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Field") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { fieldExpanded = true }) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = fieldExpanded, onDismissRequest = { fieldExpanded = false }) {
                        fieldsList.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.replace("_", " ")) },
                                onClick = {
                                    field = option
                                    fieldExpanded = false
                                }
                            )
                        }
                    }
                }

                var compExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = comparison,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Comparison") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { compExpanded = true }) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = compExpanded, onDismissRequest = { compExpanded = false }) {
                        listOf("contains", "equal", "not equal", "empty", "greater than", "less than", "in range").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    comparison = option
                                    compExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = val1,
                    onValueChange = { val1 = it },
                    label = { Text("Value 1 *") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (comparison == "in range") {
                    OutlinedTextField(
                        value = val2,
                        onValueChange = { val2 = it },
                        label = { Text("Value 2 *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && val1.isNotBlank()) {
                        onSave(
                            SavedFilterEntity(
                                id = existingFilter?.id ?: 0,
                                filterName = name.trim(),
                                fieldName = field,
                                comparison = comparison,
                                value1 = val1.trim(),
                                value2 = val2.trim(),
                                displayOrder = existingFilter?.displayOrder ?: 0
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

// Extraction utility mapping dynamic attributes
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

// Generic comparator engine
private fun evaluateCondition(fieldVal: String, operator: String, v1: String, v2: String): Boolean {
    val cleanVal = fieldVal.trim()
    return when (operator) {
        "contains" -> cleanVal.contains(v1, ignoreCase = true)
        "equal" -> cleanVal.equals(v1, ignoreCase = true)
        "not equal" -> !cleanVal.equals(v1, ignoreCase = true)
        "empty" -> cleanVal.isEmpty()
        "greater than" -> (cleanVal.toDoubleOrNull() ?: 0.0) > (v1.toDoubleOrNull() ?: 0.0)
        "less than" -> (cleanVal.toDoubleOrNull() ?: 0.0) < (v1.toDoubleOrNull() ?: 0.0)
        "in range" -> {
            val num = cleanVal.toDoubleOrNull() ?: 0.0
            val min = v1.toDoubleOrNull() ?: 0.0
            val max = v2.toDoubleOrNull() ?: 0.0
            num in min..max
        }
        else -> true
    }
}