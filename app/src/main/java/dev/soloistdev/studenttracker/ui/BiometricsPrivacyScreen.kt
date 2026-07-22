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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricsPrivacyScreen(
    onBack: () -> Unit,
    viewModel: SecurityViewModel = viewModel()
) {
    val context = LocalContext.current
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()

    var showResetPinDialog by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Biometrics & Privacy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Security, contentDescription = "Security Status")
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
            Text("App Lock Settings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Biometric Authentication switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Lockout", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Fingerprint or Face unlock on startup", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setBiometricEnabled(enabled)
                                Toast.makeText(context, if (enabled) "Biometrics Enabled" else "Biometrics Disabled", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Change PIN action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showResetPinDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reset Master PIN", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Update your 4 to 6 digit master passcode", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.LockReset, contentDescription = "Reset PIN", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Secure PIN setup dialog
        if (showResetPinDialog) {
            val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AlertDialog(
                onDismissRequest = { showResetPinDialog = false },
                title = { Text("Reset Recovery PIN", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = oldPin,
                            onValueChange = { oldPin = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("Current Master PIN *") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("New Master PIN *") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmNewPin,
                            onValueChange = { confirmNewPin = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("Confirm New PIN *") },
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
                                Toast.makeText(context, "All fields are required.", Toast.LENGTH_SHORT).show()
                            } else if (newPin != confirmNewPin) {
                                Toast.makeText(context, "New PIN fields do not match.", Toast.LENGTH_SHORT).show()
                            } else {
                                val success = viewModel.resetPin(oldPin, newPin)
                                if (success) {
                                    Toast.makeText(context, "PIN successfully updated!", Toast.LENGTH_SHORT).show()
                                    oldPin = ""
                                    newPin = ""
                                    confirmNewPin = ""
                                    showResetPinDialog = false
                                } else {
                                    Toast.makeText(context, "Incorrect current PIN or invalid format.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        oldPin = ""
                        newPin = ""
                        confirmNewPin = ""
                        showResetPinDialog = false
                    }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}