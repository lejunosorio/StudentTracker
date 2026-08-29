package dev.soloistdev.studenttracker

import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Contains a failure in background work so it cannot take the app down with it.
 *
 * These call sites - a boot receiver, an alarm receiver, a widget update, a backup worker - all
 * share the same shape: nobody is looking at the screen, the work is a convenience rather than
 * something the teacher asked for, and there is no user to show an error to. Losing one reminder
 * is a missed convenience; crashing the process is a visible failure of the whole app.
 *
 * Throwable rather than Exception, which is the distinction that actually bit. A missing native
 * library raises UnsatisfiedLinkError - an Error, not an Exception - so it walked straight through
 * every `catch (e: Exception)` in this codebase and killed the process from inside the boot
 * receiver, on every single boot.
 *
 * CancellationException is re-thrown rather than swallowed: it is how a coroutine is told to stop,
 * and eating it would break structured concurrency rather than contain a fault.
 */
internal inline fun guardBackgroundWork(tag: String, block: () -> Unit) {
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        Log.e(tag, "Background work failed and was contained rather than crashing the app", t)
    }
}
