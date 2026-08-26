package dev.soloistdev.studenttracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.IllegalFormatException
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks every translated string that the app formats at runtime.
 *
 * A resource passed to the vararg `stringResource(id, args)` goes through java.util.Formatter, so
 * a stray "%" in any locale is a crash rather than a display glitch - and only in that locale,
 * which means it survives testing in English and fails on a user's phone. That is exactly what
 * "UnknownFormatConversionException: Conversion = '{'" was: a malformed placeholder reached a
 * dialog and took the app down.
 *
 * These read the real resource files and the real call sites, so a new string or a new translation
 * is covered without anyone remembering to add a case.
 */
class StringResourceFormatTest {

    private val resDir: File by lazy {
        listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory }
            ?: error("res directory not found from ${File(".").absolutePath}")
    }

    private val sourceDir: File by lazy {
        listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error("source directory not found")
    }

    /** Every strings.xml under a values directory, keyed by qualifier ("values", "values-fil"). */
    private val stringsByLocale: Map<String, Map<String, String>> by lazy {
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }
            .orEmpty()
            .mapNotNull { dir ->
                val file = File(dir, "strings.xml")
                if (file.isFile) dir.name to parseStrings(file) else null
            }
            .toMap()
    }

    private fun parseStrings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).mapNotNull { i ->
            val element = nodes.item(i) as? Element ?: return@mapNotNull null
            val name = element.getAttribute("name")
            if (name.isNullOrBlank()) null else name to unescape(element.textContent)
        }.toMap()
    }

    /**
     * Applies the escaping aapt2 does when compiling a resource, so this checks the string the app
     * actually receives.
     *
     * It matters here: several templates are written "%1\$s", carried over from Kotlin where the
     * backslash is needed. aapt2 drops it and the app sees "%1$s", so validating the raw XML would
     * report a crash in perfectly good strings. Verified against `aapt2 dump resources` on the
     * built APK.
     */
    private fun unescape(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i == raw.length - 1) {
                out.append(c)
                i++
                continue
            }
            when (val next = raw[i + 1]) {
                'n' -> { out.append('\n'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'u' -> {
                    val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
                    val code = hex.toIntOrNull(16)
                    if (code != null && hex.length == 4) {
                        out.append(code.toChar()); i += 6
                    } else {
                        out.append(next); i += 2
                    }
                }
                // Every other escape, known or not, resolves to the character itself.
                else -> { out.append(next); i += 2 }
            }
        }
        return out.toString()
    }

    /**
     * Format specifiers, as java.util.Formatter parses them - the positional "%1$s" form Android
     * expects, and the bare "%s" form that also compiles.
     */
    private val specifier = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])""")

    private fun specifiersIn(text: String): List<MatchResult> =
        specifier.findAll(text.replace("%%", "")).toList()

    /** A plausible argument for each conversion, so the string can actually be formatted. */
    private fun sampleArgFor(conversion: Char): Any = when (conversion.lowercaseChar()) {
        'd' -> 1
        'f', 'e', 'g' -> 1.0
        'x', 'o' -> 1
        'c' -> 'x'
        'b' -> true
        else -> "sample"
    }

    /** Resource names the code passes arguments to, so Formatter will run over them. */
    private val formattedResourceNames: Set<String> by lazy {
        val call = Regex("""stringResource\(\s*R\.string\.(\w+)\s*,""")
        val getString = Regex("""getString\(\s*R\.string\.(\w+)\s*,""")
        sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                (call.findAll(text) + getString.findAll(text)).map { it.groupValues[1] }
            }
            .toSet()
    }

    @Test
    fun everyFormattedStringActuallyFormatsInEveryLocale() {
        assertTrue("expected to find formatted string resources", formattedResourceNames.isNotEmpty())

        val failures = mutableListOf<String>()
        stringsByLocale.forEach { (locale, strings) ->
            formattedResourceNames.forEach { name ->
                val text = strings[name] ?: return@forEach
                val args = specifiersIn(text).map { sampleArgFor(it.groupValues[2].first()) }
                try {
                    String.format(Locale.US, text, *args.toTypedArray())
                } catch (e: IllegalFormatException) {
                    failures.add("$locale/$name: ${e.javaClass.simpleName} in \"$text\"")
                }
            }
        }

        assertTrue(
            "these strings are passed to a formatter and would crash:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun translationsCarryTheSamePlaceholdersAsTheDefaultLocale() {
        // A translation that drops "%1$s" silently loses the value; one that adds a placeholder
        // the caller does not supply throws MissingFormatArgumentException.
        val default = stringsByLocale["values"] ?: error("no default values/strings.xml")
        val failures = mutableListOf<String>()

        // Only the positional form, which is what Android requires of a translated string with
        // arguments. Comparing every "%" would flag prose like "100% offline", where a translation
        // is free to phrase it without one.
        val positional = Regex("""%\d+\$[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")

        stringsByLocale.filterKeys { it != "values" }.forEach { (locale, strings) ->
            strings.forEach { (name, translated) ->
                val original = default[name] ?: return@forEach
                val expected = positional.findAll(original).map { it.value }.toList().sorted()
                val actual = positional.findAll(translated).map { it.value }.toList().sorted()
                if (expected != actual) {
                    failures.add("$locale/$name: expected $expected but the translation has $actual")
                }
            }
        }

        assertTrue(
            "placeholders differ between locales:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun aFormattedStringHasAPlaceholderToPutTheArgumentIn() {
        // Passing an argument to a string with nowhere to put it means the value never appears -
        // silent, and easy to miss in a locale nobody on the team reads.
        val failures = stringsByLocale.flatMap { (locale, strings) ->
            formattedResourceNames.mapNotNull { name ->
                val text = strings[name] ?: return@mapNotNull null
                if (specifiersIn(text).isEmpty()) "$locale/$name: \"$text\"" else null
            }
        }

        assertTrue(
            "these are given arguments but have no placeholder:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun aBarePercentIsOnlyEverInStringsThatAreNeverFormatted() {
        // A literal "%" is fine in a string that is only displayed - "100% offline" - and fatal in
        // one that is formatted. This pins down which strings may keep theirs.
        val failures = mutableListOf<String>()

        stringsByLocale.forEach { (locale, strings) ->
            strings.forEach { (name, text) ->
                val withoutEscapes = text.replace("%%", "")
                val accountedFor = specifier.findAll(withoutEscapes).sumOf { it.value.length }
                val percentCount = withoutEscapes.count { it == '%' }
                val specifierCount = specifier.findAll(withoutEscapes).count()

                if (percentCount > specifierCount && formattedResourceNames.contains(name)) {
                    failures.add("$locale/$name has a stray '%': \"$text\" (accounted $accountedFor chars)")
                }
            }
        }

        assertTrue(
            "a stray '%' in a formatted string is a crash in that locale:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun theFilipinoTranslationCoversTheStringsTheAppShows() {
        // Not a hard requirement - Android falls back to English - but a locale advertised in
        // locales_config.xml that is mostly English is worth knowing about.
        val default = stringsByLocale["values"] ?: error("no default values/strings.xml")
        val translated = stringsByLocale["values-fil"] ?: error("no values-fil/strings.xml")

        val coverage = translated.keys.count { default.containsKey(it) }.toDouble() / default.size
        assertTrue(
            "Filipino covers only ${(coverage * 100).toInt()}% of ${default.size} strings",
            coverage > 0.5
        )
    }

    @Test
    fun everyTranslatedKeyExistsInTheDefaultLocale() {
        // A key only present in a translation is dead weight, and usually a rename that was not
        // carried across.
        val default = stringsByLocale["values"] ?: error("no default values/strings.xml")
        stringsByLocale.filterKeys { it != "values" }.forEach { (locale, strings) ->
            val orphans = strings.keys - default.keys
            assertEquals("$locale has keys the default locale does not", emptySet<String>(), orphans)
        }
    }
}
