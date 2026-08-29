package dev.soloistdev.studenttracker.data

import android.content.Context
import androidx.annotation.StringRes
import dev.soloistdev.studenttracker.R
import java.io.File

/**
 * Who is using this app, what they call the people in it, and which parts of it they use.
 *
 * The app was written for a school teacher and says so everywhere - "Student", "Classroom",
 * "Guardian" - which is exactly wrong for the tutoring centre, training provider, youth group or
 * sports club that keeps the same records under different words. None of that is a data model
 * change; it is vocabulary, and vocabulary is the cheapest thing in the app to make configurable.
 *
 * Blank means "use the built-in wording", so a value is never stored just because a screen was
 * opened, and the defaults stay translatable through the normal resource system rather than being
 * frozen into a preference the first time someone looks at this screen.
 */
object OrganizationSettings {

    private const val PREFS = "app_settings"

    private const val KEY_ORG_NAME = "org_name"
    private const val KEY_OWNER_NAME = "org_owner_name"
    private const val KEY_LOGO_PATH = "org_logo_path"

    private const val KEY_LEARNER = "term_learner"
    private const val KEY_LEARNERS = "term_learners"
    private const val KEY_GROUP = "term_group"
    private const val KEY_GROUPS = "term_groups"
    private const val KEY_GUARDIAN = "term_guardian"
    private const val KEY_GUARDIANS = "term_guardians"


    /**
     * A ready-made vocabulary. Most people will recognise their setting in one of these and never
     * type anything; the custom fields are there for the ones who do not.
     *
     * Every field is a resource id, including the vocabulary itself. Holding literals here meant
     * the chips stayed English in every locale, and - worse - a Filipino teacher picking a preset
     * had English wording written into their app as though they had typed it. The caller resolves
     * these against the current locale and stores the result.
     */
    data class Preset(
        @StringRes val labelRes: Int,
        @StringRes val learnerRes: Int,
        @StringRes val learnersRes: Int,
        @StringRes val groupRes: Int,
        @StringRes val groupsRes: Int,
        @StringRes val guardianRes: Int,
        @StringRes val guardiansRes: Int
    )

    val PRESETS = listOf(
        // The school preset is the app's own default vocabulary, so it reuses those resources
        // rather than duplicating them under another name.
        Preset(
            R.string.preset_school,
            R.string.term_default_learner, R.string.term_default_learners,
            R.string.term_default_group, R.string.term_default_groups,
            R.string.term_default_guardian, R.string.term_default_guardians
        ),
        Preset(
            R.string.preset_tutoring,
            R.string.preset_tutoring_learner, R.string.preset_tutoring_learners,
            R.string.preset_tutoring_group, R.string.preset_tutoring_groups,
            R.string.preset_tutoring_guardian, R.string.preset_tutoring_guardians
        ),
        Preset(
            R.string.preset_training,
            R.string.preset_training_learner, R.string.preset_training_learners,
            R.string.preset_training_group, R.string.preset_training_groups,
            R.string.preset_training_guardian, R.string.preset_training_guardians
        ),
        Preset(
            R.string.preset_club,
            R.string.preset_club_learner, R.string.preset_club_learners,
            R.string.preset_club_group, R.string.preset_club_groups,
            R.string.preset_club_guardian, R.string.preset_club_guardians
        ),
        Preset(
            R.string.preset_sports,
            R.string.preset_sports_learner, R.string.preset_sports_learners,
            R.string.preset_sports_group, R.string.preset_sports_groups,
            R.string.preset_sports_guardian, R.string.preset_sports_guardians
        )
    )

    // --- identity ---------------------------------------------------------------------------

    /** Blank until set, so the caller can fall back to the translated placeholder. */
    fun organizationName(context: Context): String =
        prefs(context).getString(KEY_ORG_NAME, "").orEmpty().trim()

    fun setOrganizationName(context: Context, value: String) =
        prefs(context).edit().putString(KEY_ORG_NAME, value.trim()).apply()

    fun ownerName(context: Context): String =
        prefs(context).getString(KEY_OWNER_NAME, "").orEmpty().trim()

    fun setOwnerName(context: Context, value: String) =
        prefs(context).edit().putString(KEY_OWNER_NAME, value.trim()).apply()

    fun logoPath(context: Context): String =
        prefs(context).getString(KEY_LOGO_PATH, "").orEmpty()

    /** The stored logo, or null when there is none or the file has gone. */
    fun logoFile(context: Context): File? =
        logoPath(context).takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }

    fun setLogoPath(context: Context, path: String) =
        prefs(context).edit().putString(KEY_LOGO_PATH, path).apply()

    /**
     * Points the logo at [path], deleting whatever it pointed at before.
     *
     * The compressor names every file it writes with a fresh UUID, so without this each replaced
     * logo would sit in internal storage forever with nothing referencing it.
     */
    fun replaceLogo(context: Context, path: String) {
        val previous = logoPath(context)
        setLogoPath(context, path)
        if (previous.isNotBlank() && previous != path) {
            try {
                File(previous).delete()
            } catch (_: Exception) {
                // An orphaned file is not worth failing a logo change over
            }
        }
    }

    fun clearLogo(context: Context) {
        try {
            logoFile(context)?.delete()
        } catch (_: Exception) {
            // A logo we cannot delete is not worth failing the settings screen over
        }
        setLogoPath(context, "")
    }

    // --- terminology ------------------------------------------------------------------------

    fun learner(context: Context, fallback: String): String = read(context, KEY_LEARNER, fallback)
    fun learners(context: Context, fallback: String): String = read(context, KEY_LEARNERS, fallback)
    fun group(context: Context, fallback: String): String = read(context, KEY_GROUP, fallback)
    fun groups(context: Context, fallback: String): String = read(context, KEY_GROUPS, fallback)
    fun guardian(context: Context, fallback: String): String = read(context, KEY_GUARDIAN, fallback)
    fun guardians(context: Context, fallback: String): String = read(context, KEY_GUARDIANS, fallback)

    // A preset is applied through setTerms by whoever resolved its resources against the current
    // locale. There is deliberately no applyPreset(Preset) here: it would have to resolve strings
    // itself, and a data object reaching for a Context's resources is how the English-only leak
    // happened in the first place.

    fun setTerms(
        context: Context,
        learner: String,
        learners: String,
        group: String,
        groups: String,
        guardian: String,
        guardians: String
    ) {
        prefs(context).edit()
            .putString(KEY_LEARNER, learner.trim())
            .putString(KEY_LEARNERS, learners.trim())
            .putString(KEY_GROUP, group.trim())
            .putString(KEY_GROUPS, groups.trim())
            .putString(KEY_GUARDIAN, guardian.trim())
            .putString(KEY_GUARDIANS, guardians.trim())
            .apply()
    }

    /** Drops every override, so the app goes back to its translated wording. */
    fun resetTerms(context: Context) {
        prefs(context).edit()
            .remove(KEY_LEARNER).remove(KEY_LEARNERS)
            .remove(KEY_GROUP).remove(KEY_GROUPS)
            .remove(KEY_GUARDIAN).remove(KEY_GUARDIANS)
            .apply()
    }

    fun hasCustomTerms(context: Context): Boolean =
        prefs(context).let { p ->
            listOf(KEY_LEARNER, KEY_LEARNERS, KEY_GROUP, KEY_GROUPS, KEY_GUARDIAN, KEY_GUARDIANS)
                .any { !p.getString(it, "").isNullOrBlank() }
        }

    // --- modules ----------------------------------------------------------------------------

    /**
     * A part of the app that can be hidden.
     *
     * Hiding only ever removes the way in. Nothing is deleted, no route is unregistered, and a
     * deep link or an existing record still works - so turning a module off and on again cannot
     * lose anything, and a teacher who hides the gradebook has not thrown their marks away.
     */
    enum class Module(
        val key: String,
        @StringRes val labelRes: Int,
        @StringRes val descriptionRes: Int
    ) {
        ATTENDANCE("mod_attendance", R.string.module_attendance, R.string.module_attendance_desc),
        GRADEBOOK("mod_gradebook", R.string.module_gradebook, R.string.module_gradebook_desc),
        SEATING("mod_seating", R.string.module_seating, R.string.module_seating_desc),
        BEHAVIOUR("mod_behaviour", R.string.module_behaviour, R.string.module_behaviour_desc),
        INSIGHTS("mod_insights", R.string.module_insights, R.string.module_insights_desc),
        MESSAGING("mod_messaging", R.string.module_messaging, R.string.module_messaging_desc),
        QUERIES("mod_queries", R.string.module_queries, R.string.module_queries_desc)
    }

    fun isModuleEnabled(context: Context, module: Module): Boolean =
        prefs(context).getBoolean(module.key, true)

    fun setModuleEnabled(context: Context, module: Module, enabled: Boolean) =
        prefs(context).edit().putBoolean(module.key, enabled).apply()

    private fun read(context: Context, key: String, fallback: String): String =
        prefs(context).getString(key, "").orEmpty().trim().ifBlank { fallback }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
