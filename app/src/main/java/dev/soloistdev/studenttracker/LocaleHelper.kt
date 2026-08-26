package dev.soloistdev.studenttracker

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language selection.
 *
 * Wraps the base context with the chosen locale rather than going through AppCompatDelegate,
 * which would pull in appcompat purely for this. The choice lives in the same SharedPreferences
 * file as the theme, so language and appearance are restored by the same mechanism.
 *
 * "system" means follow the device, which stays the default: most teachers never change it, and
 * an app that quietly disagrees with the phone language is worse than one that follows it.
 */
object LocaleHelper {

    const val PREFS = "app_settings"
    const val KEY_LANGUAGE = "app_language"
    const val SYSTEM = "system"

    /** Codes must match a values-<code> resource directory. */
    val SUPPORTED = listOf(SYSTEM, "en", "fil")

    fun displayName(code: String): String = when (code) {
        SYSTEM -> "System default"
        "en" -> "English"
        "fil" -> "Filipino"
        else -> code
    }

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, code)
            .apply()
    }

    /**
     * Returns a context whose resources resolve in the selected language. Called from
     * attachBaseContext, which is early enough that every resource lookup in the activity,
     * including Compose stringResource, sees the override.
     */
    /**
     * The device locale as it was before any override was applied. Captured once, on first use,
     * so switching back to "System default" restores it instead of leaving the process stuck on
     * the previously chosen language until it is killed.
     */
    private val systemDefault: Locale by lazy { Locale.getDefault() }

    fun wrap(base: Context): Context {
        val code = current(base)

        if (code == SYSTEM) {
            Locale.setDefault(systemDefault)
            return base
        }

        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
