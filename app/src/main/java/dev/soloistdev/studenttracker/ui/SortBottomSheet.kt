package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            Text("Sort List By", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortOptionItem(
                    label = "Last Name (A - Z)",
                    isSelected = sortOrder == "lastNameAsc",
                    onClick = { onSortSelected("lastNameAsc") }
                )
                SortOptionItem(
                    label = "Last Name (Z - A)",
                    isSelected = sortOrder == "lastNameDesc",
                    onClick = { onSortSelected("lastNameDesc") }
                )
                SortOptionItem(
                    label = "Age (Youngest First)",
                    isSelected = sortOrder == "ageYoungest",
                    onClick = { onSortSelected("ageYoungest") }
                )
                SortOptionItem(
                    label = "Recently Added",
                    isSelected = sortOrder == "recentlyAdded",
                    onClick = { onSortSelected("recentlyAdded") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}