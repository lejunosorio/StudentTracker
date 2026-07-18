package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityGateScreen(onUnlockSuccess: () -> Unit, viewModel: SecurityViewModel = viewModel()) {
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isAlreadyConfigured = viewModel.isConfigured

    // Dual-PIN Setup State Managers
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    // Detect if device supports biometrics
    val biometricManager = remember { BiometricManager.from(context) }
    val isBiometricsAvailable = remember {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val launchBiometricPrompt = {
        val activity = context as? FragmentActivity
        if (activity != null && isBiometricsAvailable) {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onUnlockSuccess()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Unlock")
                .setSubtitle("Authenticate using your fingerprint or face")
                .setNegativeButtonText("Use PIN Instead")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    // Auto-launch biometrics on start if configured
    LaunchedEffect(isAlreadyConfigured) {
        if (isAlreadyConfigured && isBiometricsAvailable) {
            launchBiometricPrompt()
        }
    }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            onUnlockSuccess()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isAlreadyConfigured) "App Locked" else "Secure Setup",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isAlreadyConfigured) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = if (isAlreadyConfigured) "Enter PIN to Unlock" else "Configure Recovery PIN",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D192B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isAlreadyConfigured) {
                    "Enter your master PIN or use biometrics to access the student directory."
                } else {
                    "Create a secure database PIN. Ensure both fields match exactly before saving."
                },
                fontSize = 14.sp,
                color = Color(0xFF49454F),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1. PRIMARY PIN INPUT FIELD
            OutlinedTextField(
                value = pin,
                onValueChange = { input ->
                    val sanitized = input.filter { it.isDigit() }
                    if (sanitized.length <= 6) {
                        pin = sanitized
                    }
                },
                label = { Text(if (isAlreadyConfigured) "Enter PIN" else "Create Master PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    // If onboarding, pressing enter moves down. If login, pressing enter submits.
                    imeAction = if (isAlreadyConfigured) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }, // Auto-focus transition
                    onDone = {
                        if (isAlreadyConfigured) {
                            val success = viewModel.verifyPin(pin)
                            if (!success) {
                                Toast.makeText(context, "Incorrect PIN. Access Denied.", Toast.LENGTH_SHORT).show()
                                pin = ""
                            }
                        }
                    }
                ),
                singleLine = true,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            // 2. CONFIRM PIN INPUT FIELD (Only visible during first-launch onboarding setup)
            if (!isAlreadyConfigured) {
                Spacer(modifier = Modifier.height(16.dp))

                val pinMismatch = confirmPin.isNotEmpty() && pin != confirmPin

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { input ->
                        val sanitized = input.filter { it.isDigit() }
                        if (sanitized.length <= 6) {
                            confirmPin = sanitized
                        }
                    },
                    label = { Text("Confirm Master PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pin == confirmPin) {
                                val success = viewModel.saveRecoveryPin(pin)
                                if (success) {
                                    Toast.makeText(context, "Recovery PIN Saved Successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "PIN must be 4 to 6 digits.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "PINs do not match. Please verify.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ),
                    singleLine = true,
                    maxLines = 1,
                    isError = pinMismatch,
                    // Render custom error borders if inputs do not match
                    colors = OutlinedTextFieldDefaults.colors(
                        errorBorderColor = Color(0xFFB3261E),
                        errorLabelColor = Color(0xFFB3261E)
                    ),
                    supportingText = {
                        if (pinMismatch) {
                            Text("PINs do not match", color = Color(0xFFB3261E))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // BIOMETRIC SHORTCUT FINGERPRINT BUTTON
            if (isAlreadyConfigured && isBiometricsAvailable) {
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(
                    onClick = { launchBiometricPrompt() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Authenticate with Fingerprint",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. MAIN SUBMIT ACTION BUTTON
            Button(
                onClick = {
                    if (isAlreadyConfigured) {
                        val success = viewModel.verifyPin(pin)
                        if (!success) {
                            Toast.makeText(context, "Incorrect PIN. Access Denied.", Toast.LENGTH_SHORT).show()
                            pin = ""
                        }
                    } else {
                        if (pin == confirmPin) {
                            val success = viewModel.saveRecoveryPin(pin)
                            if (success) {
                                Toast.makeText(context, "Recovery PIN Saved Successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "PIN must be 4 to 6 digits.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "PINs do not match. Please verify.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = if (isAlreadyConfigured) "Unlock Directory" else "Confirm and Save",
                    color = Color.White
                )
            }
        }
    }
}