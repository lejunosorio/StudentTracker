package dev.soloistdev.studenttracker.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.CsvImportEngine
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch

/**
 * Maps spreadsheet columns onto roster fields.
 *
 * A sample value is shown beside every column, because the fastest way to map a column called
 * SURNAME_1 correctly is to see what is actually in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    var parsed by remember { mutableStateOf<CsvImportEngine.ParsedCsv?>(null) }
    var mapping by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var templates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }
    var outcome by remember { mutableStateOf<CsvImportEngine.ImportOutcome?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        templates = repository.getAllFormTemplates()
    }

    val targetOptions = remember(templates) {
        CsvImportEngine.Target.CORE + templates.map { it.fieldName }
    }
    val customFieldNames = remember(templates) { templates.map { it.fieldName }.toSet() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        isWorking = true
        parseError = null
        scope.launch {
            val result = CsvImportEngine.readCsv(context, uri)
            isWorking = false
            if (result == null || result.headers.isEmpty()) {
                parseError = "Could not read that file as CSV."
            } else {
                parsed = result
                // Pre-map any header that already matches a known field name exactly
                mapping = result.headers.mapIndexedNotNull { index, header ->
                    targetOptions.firstOrNull { it.equals(header.trim(), ignoreCase = true) }
                        ?.let { index to it }
                }.toMap()
            }
        }
    }

    val mappedTargets = mapping.values.filter { it != CsvImportEngine.Target.IGNORE }
    val hasName = mappedTargets.contains(CsvImportEngine.Target.FIRST_NAME) ||
            mappedTargets.contains(CsvImportEngine.Target.LAST_NAME)
    val duplicateTarget = mappedTargets.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Import Spreadsheet", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val table = parsed
            if (table == null) {
                CsvPickPrompt(
                    isWorking = isWorking,
                    parseError = parseError,
                    onPick = { picker.launch("*/*") }
                )
            } else {
                CsvMappingBody(
                    table = table,
                    mapping = mapping,
                    targetOptions = targetOptions,
                    hasName = hasName,
                    duplicateTarget = duplicateTarget,
                    isWorking = isWorking,
                    onMap = { index, choice ->
                        mapping = mapping.toMutableMap().apply {
                            if (choice == CsvImportEngine.Target.IGNORE) remove(index) else put(index, choice)
                        }
                    },
                    onImport = {
                        isWorking = true
                        scope.launch {
                            outcome = CsvImportEngine.import(repository, table, mapping, customFieldNames)
                            isWorking = false
                        }
                    },
                    onReset = {
                        parsed = null
                        mapping = emptyMap()
                        outcome = null
                    }
                )
            }
        }

        outcome?.let { result ->
            CsvOutcomeDialog(result = result, onDone = {
                outcome = null
                onBack()
            })
        }
    }
}

@Composable
private fun CsvPickPrompt(isWorking: Boolean, parseError: String?, onPick: () -> Unit) {
    Text(
        text = "Pick a .csv exported from your school system or a spreadsheet. The first row must be the column headers.",
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = onPick,
        enabled = !isWorking,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (isWorking) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.s_choose_csv_file))
        }
    }
    parseError?.let {
        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ColumnScope.CsvMappingBody(
    table: CsvImportEngine.ParsedCsv,
    mapping: Map<Int, String>,
    targetOptions: List<String>,
    hasName: Boolean,
    duplicateTarget: String?,
    isWorking: Boolean,
    onMap: (Int, String) -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit
) {
    Text(
        text = "${table.rows.size} rows found. Tell the app what each column means.",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    table.headers.forEachIndexed { index, header ->
        ColumnMappingRow(
            header = header.ifBlank { "Column ${index + 1}" },
            sample = table.rows.firstOrNull()?.getOrNull(index).orEmpty(),
            selected = mapping[index] ?: CsvImportEngine.Target.IGNORE,
            options = targetOptions,
            onSelect = { choice -> onMap(index, choice) }
        )
    }

    if (!hasName) {
        Text(
            text = "Map at least a first or last name column before importing.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (duplicateTarget != null) {
        Text(
            text = "Two columns are mapped to '$duplicateTarget'. Only the first will be used.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error
        )
    }

    Text(
        text = "Students already on the roster are matched on name and birthday, then refreshed. Nothing is duplicated.",
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )

    Button(
        onClick = onImport,
        enabled = hasName && !isWorking,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (isWorking) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Text("Import ${table.rows.size} rows")
        }
    }

    OutlinedButton(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(stringResource(R.string.s_choose_a_different_file))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMappingRow(
    header: String,
    sample: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isMapped = selected != CsvImportEngine.Target.IGNORE

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isMapped) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(header, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (sample.isNotBlank()) {
                Text(
                    text = "e.g. $sample",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selected,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Maps to", fontSize = 11.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onSelect(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CsvOutcomeDialog(
    result: CsvImportEngine.ImportOutcome,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Import Finished", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Created: ${result.created}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Updated: ${result.updated}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Skipped: ${result.skipped}", fontSize = 14.sp, fontWeight = FontWeight.Medium)

                if (result.errors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Problems",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    result.errors.take(20).forEach {
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                    if (result.errors.size > 20) {
                        Text(
                            text = "and ${result.errors.size - 20} more",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text(stringResource(R.string.s_done)) } },
        shape = RoundedCornerShape(28.dp)
    )
}
