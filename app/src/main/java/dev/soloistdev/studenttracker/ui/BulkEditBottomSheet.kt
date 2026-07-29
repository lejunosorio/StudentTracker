package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.FormTemplateEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkEditBottomSheet(
    selectedCount: Int,
    availableTemplates: List<FormTemplateEntity>,
    onApplyChanges: (fieldName: String, newValue: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedField by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val validationErrorMsg = stringResource(R.string.bulk_edit_validation_error)

    val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()) // Keyboard-safe layout scroll
                .imePadding(), // Keyboard-safe layout height constraints
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.bulk_edit_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 1. Custom Field Dropdown Selector [1]
            val currentLabel = if (selectedField.isEmpty()) "" else selectedField.replace("_", " ")
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.bulk_edit_select_field_label)) },
                    colors = m3TextFieldColors,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    availableTemplates.forEach { template ->
                        val userFriendlyLabel = template.fieldName.replace("_", " ")
                        DropdownMenuItem(
                            text = { Text(userFriendlyLabel) },
                            onClick = {
                                selectedField = template.fieldName
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // 2. New Value Input Text Field [1]
            OutlinedTextField(
                value = newValue,
                onValueChange = { newValue = it },
                label = { Text(stringResource(R.string.bulk_edit_value_label)) },
                colors = m3TextFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Submit Action Button [1]
            Button(
                onClick = {
                    if (selectedField.isBlank()) {
                        Toast.makeText(context, validationErrorMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        showConfirmDialog = true
                    }
                },
                enabled = selectedField.isNotBlank(),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(stringResource(R.string.bulk_edit_apply_button, selectedCount))
            }
        }
    }

    // Confirmation dialog ensuring double safety checks [1]
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.bulk_edit_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.bulk_edit_confirm_desc,
                        selectedField.replace("_", " "),
                        newValue.ifEmpty { "Empty" },
                        selectedCount
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onApplyChanges(selectedField, newValue)
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.action_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.action_no))
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}