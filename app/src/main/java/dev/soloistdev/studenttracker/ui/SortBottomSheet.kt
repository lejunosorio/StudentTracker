package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // Resolved: Explicit resource accessor import [1]
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R // Resolved: Explicit R file import [1]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    sortOrder: String,
    onSortSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.sort_sheet_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortOptionItem(
                    label = stringResource(R.string.sort_last_name_asc),
                    isSelected = sortOrder == "lastNameAsc",
                    onClick = { onSortSelected("lastNameAsc") }
                )
                SortOptionItem(
                    label = stringResource(R.string.sort_last_name_desc),
                    isSelected = sortOrder == "lastNameDesc",
                    onClick = { onSortSelected("lastNameDesc") }
                )
                SortOptionItem(
                    label = stringResource(R.string.sort_age_youngest),
                    isSelected = sortOrder == "ageYoungest",
                    onClick = { onSortSelected("ageYoungest") }
                )
                SortOptionItem(
                    label = stringResource(R.string.sort_recently_added),
                    isSelected = sortOrder == "recentlyAdded",
                    onClick = { onSortSelected("recentlyAdded") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}