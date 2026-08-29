package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.*
import dev.soloistdev.studenttracker.widget.TodayWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * What a teacher needs at 7:25am, rather than the whole roster.
 *
 * Every piece of this already existed somewhere - the schedule card, the risk flags, the
 * gradebook. It was spread across four screens, so answering "what is happening today" meant
 * visiting all of them.
 *
 * The arithmetic itself lives in [TodayDigest], because the reminders and the home-screen widget
 * need the same answer without a screen being open.
 */
class TodayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    data class TodayState(
        val current: ClassSchedule.ScheduledClass? = null,
        val next: ClassSchedule.ScheduledClass? = null,
        val currentClassSize: Int = 0,
        val summary: StudentInsights.ClassSummary? = null,
        val absentYesterday: List<StudentEntity> = emptyList(),
        val onStreak: List<Pair<StudentEntity, Int>> = emptyList(),
        val openConcerns: List<Pair<StudentEntity, Int>> = emptyList(),
        val dueSoon: List<TodayDigest.DueSoon> = emptyList(),
        val isLoading: Boolean = true
    )

    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val digest = TodayDigest.compute(repository)

            _state.value = TodayState(
                current = digest.current,
                next = digest.next,
                currentClassSize = digest.classSize,
                summary = digest.summary,
                absentYesterday = digest.absentLastSession,
                onStreak = digest.onStreak,
                openConcerns = digest.openConcerns,
                dueSoon = digest.dueSoon,
                isLoading = false
            )

            // The screen has just paid for a full digest; handing it to the widget keeps the two
            // in step for free rather than making the widget recompute it minutes later.
            TodayWidgetProvider.publish(getApplication(), digest)
        }
    }
}
