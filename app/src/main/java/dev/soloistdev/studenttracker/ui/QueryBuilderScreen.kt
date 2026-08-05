package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryBuilderScreen(
    onBack: () -> Unit,
    onShowResults: () -> Unit,
    viewModel: QueryViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    var templates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.loadData()
        templates = repository.getAllFormTemplates()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_query_builder), fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Query Conditions", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = viewModel.matchOperator == "AND",
                                onClick = { viewModel.matchOperator = "AND" },
                                label = { Text(stringResource(R.string.query_builder_match_all)) }
                            )
                            FilterChip(
                                selected = viewModel.matchOperator == "OR",
                                onClick = { viewModel.matchOperator = "OR" },
                                label = { Text(stringResource(R.string.query_builder_match_any)) }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        viewModel.queryRules.forEachIndexed { index, rule ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Condition ${index + 1}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(
                                            onClick = { viewModel.queryRules.remove(rule) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Rule",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    val coreFields = listOf("First Name", "Last Name", "Gender", "Birthday", "Address", "Age", "Classroom")
                                    val fieldsList = remember {
                                        val list = coreFields.toMutableList()
                                        templates.forEach { list.add(it.fieldName) }
                                        list
                                    }

                                    // Row 2: Field Selection Dropdown
                                    var fExpanded by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = rule.field.replace("_", " "),
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Select Field") },
                                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { fExpanded = true }) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DropdownMenu(
                                            expanded = fExpanded,
                                            onDismissRequest = { fExpanded = false },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            fieldsList.forEach { option ->
                                                DropdownMenuItem(
                                                    text = { Text(option.replace("_", " ")) },
                                                    onClick = {
                                                        rule.field = option
                                                        fExpanded = false
                                                        val isClass = option == "Classroom"
                                                        rule.comparison = if (isClass) "equal" else "contains"
                                                        rule.value1 = ""
                                                        rule.value2 = ""
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Row 3: Comparison Operator Dropdown
                                    var cExpanded by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = rule.comparison,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Comparison") },
                                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { cExpanded = true }) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DropdownMenu(
                                            expanded = cExpanded,
                                            onDismissRequest = { cExpanded = false }
                                        ) {
                                            val ops = when (rule.field) {
                                                "Classroom" -> {
                                                    listOf("equal", "not equal", "empty", "not empty")
                                                }
                                                "Age" -> {
                                                    listOf("equal", "greater than", "less than", "In between")
                                                }
                                                else -> {
                                                    listOf("contains", "does not contain", "equal", "not equal")
                                                }
                                            }
                                            ops.forEach { option ->
                                                DropdownMenuItem(
                                                    text = { Text(option) },
                                                    onClick = {
                                                        rule.comparison = option
                                                        cExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Row 4: Value Input Fields (Hides elements dynamically)
                                    val isValueRequired = rule.comparison != "empty" && rule.comparison != "not empty"
                                    if (isValueRequired) {
                                        if (rule.comparison == "In between") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = rule.value1,
                                                    onValueChange = { rule.value1 = it },
                                                    label = { Text("Min Value *") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = rule.value2,
                                                    onValueChange = { rule.value2 = it },
                                                    label = { Text("Max Value *") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        } else {
                                            val isNumeric = rule.field == "Age" || rule.field == "Birthday"
                                            OutlinedTextField(
                                                value = rule.value1,
                                                onValueChange = { rule.value1 = it },
                                                label = { Text("Value *") },
                                                keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.queryRules.add(QueryRule()) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.query_builder_add_rule), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onShowResults,
                enabled = viewModel.queryRules.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.query_builder_action_show_results),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}