package dev.soloistdev.studenttracker.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.OrganizationSettings
import dev.soloistdev.studenttracker.security.ImageCompressor
import kotlinx.coroutines.launch

/**
 * Makes the app belong to whoever is using it.
 *
 * Three things, in the order they matter: who you are, what you call the people you keep records
 * about, and which parts of the app you actually use. The first two are stamped on every export,
 * which is where a generic "STUDENT PROFILE REPORT" handed to a parent looks least like something
 * the organisation produced.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OrganizationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = LocalOrgProfile.current

    // Seeded from what is stored, not from the resolved profile: an empty field has to stay empty
    // so the placeholder shows through, rather than pre-filling with "My Organization" and turning
    // the default into a value the moment anyone opens this screen.
    var orgName by remember { mutableStateOf(OrganizationSettings.organizationName(context)) }
    var ownerName by remember { mutableStateOf(OrganizationSettings.ownerName(context)) }

    var learner by remember { mutableStateOf(profile.terms.learner) }
    var learners by remember { mutableStateOf(profile.terms.learners) }
    var group by remember { mutableStateOf(profile.terms.group) }
    var groups by remember { mutableStateOf(profile.terms.groups) }
    var guardian by remember { mutableStateOf(profile.terms.guardian) }
    var guardians by remember { mutableStateOf(profile.terms.guardians) }

    var logoPath by remember { mutableStateOf(OrganizationSettings.logoPath(context)) }
    var logoRevision by remember { mutableIntStateOf(0) }

    val logoFailedMsg = stringResource(R.string.org_logo_failed)
    val lastModuleMsg = stringResource(R.string.org_module_last_warning)

    fun persistTerms() {
        OrganizationSettings.setTerms(context, learner, learners, group, groups, guardian, guardians)
    }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Reuses the same compressor student photos go through, so a 12MP camera shot does not
            // become a 4MB file the PDF renderer has to scale on every export.
            val saved = ImageCompressor.compressAndSaveImage(context, uri)
            if (saved == null) {
                Toast.makeText(context, logoFailedMsg, Toast.LENGTH_LONG).show()
            } else {
                OrganizationSettings.replaceLogo(context, saved)
                logoPath = saved
                logoRevision++
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.org_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        persistTerms()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = stringResource(R.string.org_section_identity)) {
                OutlinedTextField(
                    value = orgName,
                    onValueChange = {
                        orgName = it
                        OrganizationSettings.setOrganizationName(context, it)
                    },
                    label = { Text(stringResource(R.string.org_name_label), fontSize = 12.sp) },
                    placeholder = { Text(stringResource(R.string.org_name_hint), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = {
                        ownerName = it
                        OrganizationSettings.setOwnerName(context, it)
                    },
                    label = { Text(stringResource(R.string.org_owner_label), fontSize = 12.sp) },
                    placeholder = { Text(stringResource(R.string.org_owner_hint), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.org_identity_note),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            SettingsSection(title = stringResource(R.string.org_logo)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logoPath.isNotBlank()) {
                        key(logoRevision) {
                            LocalImageLoader(
                                imagePath = logoPath,
                                contentDescription = stringResource(R.string.org_logo),
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                displaySize = 64.dp,
                                fallback = {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    logoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (logoPath.isBlank()) {
                                        stringResource(R.string.org_logo_add)
                                    } else {
                                        stringResource(R.string.org_logo_replace)
                                    },
                                    fontSize = 12.sp
                                )
                            }
                            if (logoPath.isNotBlank()) {
                                TextButton(onClick = {
                                    OrganizationSettings.clearLogo(context)
                                    logoPath = ""
                                    logoRevision++
                                }) {
                                    Text(
                                        text = stringResource(R.string.org_logo_remove),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.org_logo_note),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            SettingsSection(title = stringResource(R.string.org_section_terminology)) {
                Text(
                    text = stringResource(R.string.org_preset_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrganizationSettings.PRESETS.forEach { preset ->
                        // Resolved here, in composition, against the current locale - a preset
                        // carries resource ids, not words, so picking one in Filipino stores
                        // Filipino wording rather than switching the app to English.
                        val presetLabel = stringResource(preset.labelRes)
                        val pLearner = stringResource(preset.learnerRes)
                        val pLearners = stringResource(preset.learnersRes)
                        val pGroup = stringResource(preset.groupRes)
                        val pGroups = stringResource(preset.groupsRes)
                        val pGuardian = stringResource(preset.guardianRes)
                        val pGuardians = stringResource(preset.guardiansRes)

                        FilterChip(
                            selected = pLearner == learner && pGroups == groups,
                            onClick = {
                                learner = pLearner
                                learners = pLearners
                                group = pGroup
                                groups = pGroups
                                guardian = pGuardian
                                guardians = pGuardians
                                OrganizationSettings.setTerms(
                                    context, pLearner, pLearners, pGroup, pGroups, pGuardian, pGuardians
                                )
                            },
                            label = { Text(presetLabel, fontSize = 11.sp) }
                        )
                    }
                }

                TermField(stringResource(R.string.org_term_learner), learner) {
                    learner = it; persistTerms()
                }
                TermField(stringResource(R.string.org_term_learners), learners) {
                    learners = it; persistTerms()
                }
                TermField(stringResource(R.string.org_term_group), group) {
                    group = it; persistTerms()
                }
                TermField(stringResource(R.string.org_term_groups), groups) {
                    groups = it; persistTerms()
                }
                TermField(stringResource(R.string.org_term_guardian), guardian) {
                    guardian = it; persistTerms()
                }
                TermField(stringResource(R.string.org_term_guardians), guardians) {
                    guardians = it; persistTerms()
                }

                Text(
                    text = stringResource(R.string.org_terms_note),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                if (OrganizationSettings.hasCustomTerms(context)) {
                    TextButton(onClick = {
                        OrganizationSettings.resetTerms(context)
                        learner = ""; learners = ""; group = ""; groups = ""
                        guardian = ""; guardians = ""
                    }) {
                        Text(stringResource(R.string.org_terms_reset), fontSize = 12.sp)
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.org_section_modules)) {
                OrganizationSettings.Module.entries.forEach { module ->
                    var enabled by remember(module) {
                        mutableStateOf(OrganizationSettings.isModuleEnabled(context, module))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(module.labelRes),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(module.descriptionRes),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { wanted ->
                                // The directory itself is never a module, so there is always a
                                // screen to land on; this only stops someone emptying the menu of
                                // everything else and wondering where it went.
                                val othersOn = OrganizationSettings.Module.entries
                                    .count { it != module && OrganizationSettings.isModuleEnabled(context, it) }
                                if (!wanted && othersOn == 0) {
                                    Toast.makeText(context, lastModuleMsg, Toast.LENGTH_SHORT).show()
                                } else {
                                    enabled = wanted
                                    OrganizationSettings.setModuleEnabled(context, module, wanted)
                                }
                            }
                        )
                    }
                    SettingsDivider()
                }
                Text(
                    text = stringResource(R.string.org_modules_note),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** One vocabulary field. Blank means "keep the built-in wording", which the placeholder shows. */
@Composable
private fun TermField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
