package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource // Resolved: Explicit resource accessor import [1]
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R // Resolved: Explicit R file import [1]

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

    // Pre-read resources in Composable scope to prevent context lookup delays during system callbacks [1]
    val biometricTitle = stringResource(R.string.biometric_unlock_title)
    val biometricSubtitle = stringResource(R.string.biometric_unlock_subtitle)
    val biometricNegativeText = stringResource(R.string.action_use_pin_instead)

    val incorrectPinMsg = stringResource(R.string.lock_incorrect_pin)
    val pinSavedSuccessMsg = stringResource(R.string.toast_pin_saved_success)
    val pinLengthErrorMsg = stringResource(R.string.error_pin_length)
    val pinMismatchErrorMsg = stringResource(R.string.error_pins_do_not_match)

    // Detect if device supports biometrics
    val biometricManager = remember { BiometricManager.from(context) }
    val isBiometricsAvailable = remember {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val launchBiometricPrompt = {
        val activity = context as? FragmentActivity
        // The lifecycle check is what stops "Unable to start authentication. Called after
        // onSaveInstanceState()". BiometricPrompt shows itself by committing a fragment
        // transaction, which the framework refuses once the activity has saved its state - and it
        // refuses silently, leaving the teacher looking at a lock screen where nothing happens.
        if (activity != null && isBiometricsAvailable &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        // Through the ViewModel rather than navigating directly, so a fingerprint
                        // unlock leaves exactly the same state behind as a PIN unlock. The gate
                        // navigates itself when isUnlocked flips.
                        viewModel.markUnlockedByBiometrics()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // Dismissing the sheet, or choosing "use PIN instead", is a decision rather
                        // than a fault; anything else is worth saying out loud. Without this, a
                        // lockout after too many attempts looked identical to a broken app.
                        val dismissed = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_CANCELED
                        if (!dismissed) {
                            Toast.makeText(context, errString, Toast.LENGTH_LONG).show()
                        }
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(biometricTitle)
                .setSubtitle(biometricSubtitle)
                .setNegativeButtonText(biometricNegativeText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    // Observe biometric and security gate status dynamically
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isSecurityGateEnabled by viewModel.isSecurityGateEnabled.collectAsState()

    // Auto-launch biometrics on start ONLY if gate is active and biometrics are configured.
    //
    // Waits for RESUMED rather than firing the moment this composes. A LaunchedEffect runs on
    // composition regardless of what the activity is doing, so the prompt was being asked for while
    // the activity was stopping - it never appeared, and nothing said why. repeatOnLifecycle also
    // covers the opposite case: if the gate composes while the app is not yet resumed, the prompt
    // is raised as soon as it is, instead of being lost.
    //
    // Once only, tracked across configuration changes, so rotating the phone does not throw a
    // second sheet at the teacher and dismissing it does not immediately bring it back. The
    // fingerprint button remains for a deliberate retry.
    var autoPromptRaised by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isAlreadyConfigured, isBiometricEnabled, isSecurityGateEnabled) {
        if (!(isAlreadyConfigured && isBiometricsAvailable && isBiometricEnabled && isSecurityGateEnabled)) {
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!autoPromptRaised) {
                autoPromptRaised = true
                launchBiometricPrompt()
            }
        }
    }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            onUnlockSuccess()
        }
    }

    Scaffold(
        topBar = {
            // Hide header if security gate is disabled to keep background transition clean
            if (isSecurityGateEnabled) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (isAlreadyConfigured) stringResource(R.string.lock_app_locked) else stringResource(R.string.lock_secure_setup),
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        if (isSecurityGateEnabled) {
            // The keyboard is open for the whole of this screen's job, and on first run the screen
            // is at its tallest - two PIN fields and the save button. The app targets an SDK where
            // the system no longer resizes the window for the IME, so without imePadding the
            // keyboard simply draws over the confirm field and the button, with no way to reach
            // them. imePadding shrinks the area; the scroll makes what is left reachable, and
            // focusing a field scrolls it into view on its own.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
            ) {
            Column(
                modifier = Modifier
                    // Centred while it fits, scrollable once the keyboard leaves too little room.
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isAlreadyConfigured) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.cd_locked),
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = if (isAlreadyConfigured) stringResource(R.string.lock_enter_pin) else stringResource(R.string.lock_configure_pin),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D192B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isAlreadyConfigured) {
                        stringResource(R.string.lock_unlock_description)
                    } else {
                        stringResource(R.string.lock_setup_description)
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF49454F),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        val sanitized = input.filter { it.isDigit() }
                        if (sanitized.length <= 6) {
                            pin = sanitized
                        }
                    },
                    label = { Text(if (isAlreadyConfigured) stringResource(R.string.lock_enter_pin_label) else stringResource(R.string.lock_create_master_pin_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = if (isAlreadyConfigured) ImeAction.Done else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        onDone = {
                            if (isAlreadyConfigured) {
                                val success = viewModel.verifyPin(pin)
                                if (!success) {
                                    Toast.makeText(context, incorrectPinMsg, Toast.LENGTH_SHORT).show()
                                    pin = ""
                                }
                            }
                        }
                    ),
                    singleLine = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

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
                        label = { Text(stringResource(R.string.lock_confirm_master_pin_label)) },
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
                                        Toast.makeText(context, pinSavedSuccessMsg, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, pinLengthErrorMsg, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, pinMismatchErrorMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        ),
                        singleLine = true,
                        maxLines = 1,
                        isError = pinMismatch,
                        colors = OutlinedTextFieldDefaults.colors(
                            errorBorderColor = Color(0xFFB3261E),
                            errorLabelColor = Color(0xFFB3261E)
                        ),
                        supportingText = {
                            if (pinMismatch) {
                                Text(stringResource(R.string.lock_pin_mismatch), color = Color(0xFFB3261E))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isAlreadyConfigured && isBiometricsAvailable && isBiometricEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    IconButton(
                        onClick = { launchBiometricPrompt() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = stringResource(R.string.content_description_fingerprint),
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (isAlreadyConfigured) {
                            val success = viewModel.verifyPin(pin)
                            if (!success) {
                                Toast.makeText(context, incorrectPinMsg, Toast.LENGTH_SHORT).show()
                                pin = ""
                            }
                        } else {
                            if (pin == confirmPin) {
                                val success = viewModel.saveRecoveryPin(pin)
                                if (success) {
                                    Toast.makeText(context, pinSavedSuccessMsg, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, pinLengthErrorMsg, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, pinMismatchErrorMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = if (isAlreadyConfigured) stringResource(R.string.lock_unlock_directory) else stringResource(R.string.lock_confirm_save),
                        color = Color.White
                    )
                }
            }
            }
        } else {
            // Render a standard material loading indicator during instant background transitions [1]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}