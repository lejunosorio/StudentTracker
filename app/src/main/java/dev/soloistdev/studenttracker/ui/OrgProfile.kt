package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.OrganizationSettings

/**
 * What this particular organisation calls things, and which parts of the app it uses.
 *
 * Read through a CompositionLocal rather than passed down, because the vocabulary is needed in
 * screens several levels deep that have no other reason to know about settings - a dialog title,
 * an empty state, a menu item. Threading six strings through every signature to reach them would
 * be worse than the problem it solves.
 */
data class Terms(
    val learner: String,
    val learners: String,
    val group: String,
    val groups: String,
    val guardian: String,
    val guardians: String
)

data class OrgProfile(
    /** Already resolved: the teacher's name for it, or the translated placeholder. */
    val organizationName: String,
    val ownerName: String,
    val logoPath: String,
    val terms: Terms,
    val enabledModules: Set<OrganizationSettings.Module>
) {
    fun isEnabled(module: OrganizationSettings.Module): Boolean = module in enabledModules
}

/**
 * Deliberately errors rather than defaulting.
 *
 * A default here would be a silent English fallback in a screen someone forgot to wrap, which is
 * precisely the bug this whole feature exists to remove - and it would only show up for the user
 * who had renamed everything.
 */
val LocalOrgProfile = staticCompositionLocalOf<OrgProfile> {
    error("No OrgProfile provided. Wrap the screen in ProvideOrgProfile.")
}

/** Shorthand, since call sites want the vocabulary far more often than anything else. */
val terms: Terms
    @Composable get() = LocalOrgProfile.current.terms

/**
 * Supplies the profile to everything below, and re-reads it whenever it is edited.
 *
 * The listener matters: the settings screen writes straight to SharedPreferences, so without it a
 * renamed vocabulary would only appear after the app was restarted, and the screen would look
 * broken while the teacher was still typing in it.
 */
@Composable
fun ProvideOrgProfile(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    var revision by remember { mutableIntStateOf(0) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Only the keys this profile is built from, so an unrelated setting - a backup
            // interval, a reminder toggle - does not rebuild every screen that reads it.
            if (key != null && (key.startsWith("org_") || key.startsWith("term_") || key.startsWith("mod_"))) {
                revision++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Defaults come from resources rather than constants so the built-in wording stays
    // translatable: a Filipino install with no overrides still reads as Filipino.
    val defaultOrg = stringResource(R.string.drawer_school_name)
    val defaultOwner = stringResource(R.string.drawer_proctor_portal)
    val defaultLearner = stringResource(R.string.term_default_learner)
    val defaultLearners = stringResource(R.string.term_default_learners)
    val defaultGroup = stringResource(R.string.term_default_group)
    val defaultGroups = stringResource(R.string.term_default_groups)
    val defaultGuardian = stringResource(R.string.term_default_guardian)
    val defaultGuardians = stringResource(R.string.term_default_guardians)

    val profile = remember(revision, defaultOrg, defaultLearner) {
        OrgProfile(
            organizationName = OrganizationSettings.organizationName(context).ifBlank { defaultOrg },
            ownerName = OrganizationSettings.ownerName(context).ifBlank { defaultOwner },
            logoPath = OrganizationSettings.logoPath(context),
            terms = Terms(
                learner = OrganizationSettings.learner(context, defaultLearner),
                learners = OrganizationSettings.learners(context, defaultLearners),
                group = OrganizationSettings.group(context, defaultGroup),
                groups = OrganizationSettings.groups(context, defaultGroups),
                guardian = OrganizationSettings.guardian(context, defaultGuardian),
                guardians = OrganizationSettings.guardians(context, defaultGuardians)
            ),
            enabledModules = OrganizationSettings.Module.entries
                .filter { OrganizationSettings.isModuleEnabled(context, it) }
                .toSet()
        )
    }

    CompositionLocalProvider(LocalOrgProfile provides profile, content = content)
}
