package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource // Resolved: Explicit stringArrayResource import [1]
import androidx.compose.ui.res.stringResource // Resolved: Explicit resource accessor import [1]
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R // Resolved: Explicit R file import [1]
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun WheelDatePickerDialog(
    initialDateMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val currentCal = remember { Calendar.getInstance() }
    val currentYear = currentCal.get(Calendar.YEAR)
    val currentMonth = currentCal.get(Calendar.MONTH) + 1
    val currentDay = currentCal.get(Calendar.DAY_OF_MONTH)

    val calendar = remember {
        Calendar.getInstance().apply {
            timeInMillis = initialDateMillis ?: System.currentTimeMillis()
        }
    }

    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }

    val yearsList = remember { (1920..currentYear).toList().reversed() }

    val monthsList = remember(selectedYear) {
        val limit = if (selectedYear == currentYear) currentMonth else 12
        (1..limit).toList()
    }

    LaunchedEffect(monthsList) {
        if (selectedMonth > monthsList.size) {
            selectedMonth = monthsList.first()
        }
    }

    val maxDays = remember(selectedMonth, selectedYear) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, selectedYear)
        cal.set(Calendar.MONTH, selectedMonth - 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val daysList = remember(selectedMonth, selectedYear, maxDays) {
        val limit = if (selectedYear == currentYear && selectedMonth == currentMonth) {
            currentDay
        } else {
            maxDays
        }
        (1..limit).toList()
    }

    LaunchedEffect(daysList) {
        if (selectedDay > daysList.size) {
            selectedDay = 1
        }
    }

    // Resolved: Fetches localized dynamic month names directly from resource string array [1]
    val monthNames = stringArrayResource(R.array.month_names).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wheel_picker_title), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val monthStrList = monthsList.map { monthNames[it - 1] }
                    val initialMonthIdx = monthsList.indexOf(selectedMonth).coerceAtLeast(0)
                    WheelColumnPicker(
                        items = monthStrList,
                        selectedIndex = initialMonthIdx,
                        onValueChange = { selectedMonth = monthsList[it] },
                        modifier = Modifier.weight(1.1f)
                    )

                    val dayStrList = daysList.map { it.toString() }
                    val initialDayIdx = daysList.indexOf(selectedDay).coerceAtLeast(0)
                    WheelColumnPicker(
                        items = dayStrList,
                        selectedIndex = initialDayIdx,
                        onValueChange = { selectedDay = daysList[it] },
                        modifier = Modifier.weight(0.9f)
                    )

                    val yearStrList = yearsList.map { it.toString() }
                    val initialYearIdx = yearsList.indexOf(selectedYear).coerceAtLeast(0)
                    WheelColumnPicker(
                        items = yearStrList,
                        selectedIndex = initialYearIdx,
                        onValueChange = { selectedYear = yearsList[it] },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    thickness = 1.5.dp
                )
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    thickness = 1.5.dp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resultCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, selectedYear)
                        set(Calendar.MONTH, selectedMonth - 1)
                        set(Calendar.DAY_OF_MONTH, selectedDay)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onConfirm(resultCal.timeInMillis)
                }
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun WheelColumnPicker(
    items: List<String>,
    selectedIndex: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val paddedItems = remember(items) { listOf("") + items + listOf("") }
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    // Synchronous JVM atomic mutex lock to bypass Compose async state scheduling
    val isProgrammaticScroll = remember { AtomicBoolean(false) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }.collect { index ->
            // Only update parent state if the scroll is NOT programmatically initiated
            if (index in items.indices && !isProgrammaticScroll.get()) {
                onValueChange(index)
                if (lazyListState.isScrollInProgress) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    LaunchedEffect(selectedIndex) {
        if (lazyListState.firstVisibleItemIndex != selectedIndex && selectedIndex in items.indices) {
            // Set the lock synchronously on the coroutine thread before suspending
            isProgrammaticScroll.set(true)
            lazyListState.scrollToItem(selectedIndex)
            isProgrammaticScroll.set(false) // Releases lock once layout has fully settled
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .height(120.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(paddedItems) { idx, item ->
            val isCenter = idx == selectedIndex + 1
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item,
                    fontSize = if (isCenter) 20.sp else 16.sp,
                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCenter) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }
        }
    }
}