package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource // Resolved: Explicit resource accessor import [1]
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R // Resolved: Explicit R file import [1]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricsPrivacyScreen(
    onBack: () -> Unit,
    viewModel: SecurityViewModel = viewModel()
) {
    val context = LocalContext.current
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isSecurityGateEnabled by viewModel.isSecurityGateEnabled.collectAsState()

    var showResetPinDialog by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }

    // Pre-read localized Toast messages safely inside Composable scope to prevent context resolution stutters [1]
    val gateEnabledMsg = stringResource(R.string.toast_security_gate_enabled)
    val gateDisabledMsg = stringResource(R.string.toast_security_gate_disabled)
    val bioEnabledMsg = stringResource(R.string.toast_biometrics_enabled)
    val bioDisabledMsg = stringResource(R.string.toast_biometrics_disabled)
    val fieldsRequiredMsg = stringResource(R.string.error_all_fields_required)
    val pinMismatchMsg = stringResource(R.string.error_pins_do_not_match)
    val pinUpdatedMsg = stringResource(R.string.toast_pin_updated)
    val incorrectPinMsg = stringResource(R.string.error_incorrect_pin)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_biometrics_privacy), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
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
            Text(stringResource(R.string.lock_settings_category), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.lock_security_gate_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.lock_security_gate_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = isSecurityGateEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setSecurityGateEnabled(enabled)
                                Toast.makeText(context, if (enabled) gateEnabledMsg else gateDisabledMsg, Toast.LENGTH_LONG).show()
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.lock_biometric_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.lock_biometric_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            enabled = isSecurityGateEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setBiometricEnabled(enabled)
                                Toast.makeText(context, if (enabled) bioEnabledMsg else bioDisabledMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showResetPinDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.lock_reset_pin_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.lock_reset_pin_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.LockReset, contentDescription = stringResource(R.string.lock_reset_pin_title), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (showResetPinDialog) {
            val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AlertDialog(
                onDismissRequest = { showResetPinDialog = false },
                title = { Text(stringResource(R.string.lock_reset_pin_dialog_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = oldPin,
                            onValueChange = { oldPin = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text(stringResource(R.string.lock_current_pin_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text(stringResource(R.string.lock_new_pin_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmNewPin,
                            onValueChange = { confirmNewPin = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text(stringResource(R.string.lock_confirm_new_pin_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (oldPin.isBlank() || newPin.isBlank() || confirmNewPin.isBlank()) {
                                Toast.makeText(context, fieldsRequiredMsg, Toast.LENGTH_SHORT).show()
                            } else if (newPin != confirmNewPin) {
                                Toast.makeText(context, pinMismatchMsg, Toast.LENGTH_SHORT).show()
                            } else {
                                val success = viewModel.resetPin(oldPin, newPin)
                                if (success) {
                                    Toast.makeText(context, pinUpdatedMsg, Toast.LENGTH_SHORT).show()
                                    oldPin = ""
                                    newPin = ""
                                    confirmNewPin = ""
                                    showResetPinDialog = false
                                } else {
                                    Toast.makeText(context, incorrectPinMsg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.lock_reset_pin_title))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        oldPin = ""
                        newPin = ""
                        confirmNewPin = ""
                        showResetPinDialog = false
                    }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}