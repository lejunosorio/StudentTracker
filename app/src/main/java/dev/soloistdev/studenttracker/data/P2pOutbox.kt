package dev.soloistdev.studenttracker.data

import java.io.File

/**
 * Holds the one payload that "Share via P2P" staged, until the sync screen sends it.
 *
 * The share dialog is where a teacher decides what leaves the device - which classes, and whether
 * guardian numbers and home addresses travel with them. Sending them to the sync screen and
 * letting it transmit the whole roster instead would quietly override every one of those choices,
 * so the selection is built into a payload here and the sync screen sends exactly that.
 *
 * Deliberately in memory only. A staged payload is a decision made seconds ago, not something that
 * should survive the process and be sent by accident later.
 */
object P2pOutbox {

    /** A payload waiting to be transmitted, with a description of it for the sync screen to show. */
    data class Staged(
        val file: File,
        /** e.g. "Grade 7 - Sampaguita" or "Ana Cruz" - what the teacher chose. */
        val label: String,
        val studentCount: Int
    )

    @Volatile
    private var staged: Staged? = null

    fun stage(file: File, label: String, studentCount: Int) {
        clear()
        staged = Staged(file, label, studentCount)
    }

    /** The staged payload, or null once it has been sent or was never set. */
    fun peek(): Staged? = staged?.takeIf { it.file.exists() }

    /** Forgets the staged payload and removes the file behind it. */
    fun clear() {
        staged?.let { previous ->
            try {
                if (previous.file.exists()) previous.file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        staged = null
    }
}
