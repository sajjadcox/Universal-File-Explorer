package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MultiSelectActionBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCopySelected: (() -> Unit)? = null,
    onMoveSelected: (() -> Unit)? = null,
    onBatchSelected: (() -> Unit)? = null,
    onShareSelected: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("multi_select_action_bar"),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.testTag("button_clear_selection")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear selection",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )

            if (onShareSelected != null) {
                IconButton(onClick = onShareSelected, modifier = Modifier.testTag("button_share_selected")) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Share,
                        contentDescription = "Share selected",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (onCopySelected != null) {
                IconButton(onClick = onCopySelected) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy selected",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (onMoveSelected != null) {
                IconButton(onClick = onMoveSelected) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = "Move selected",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (onBatchSelected != null) {
                IconButton(onClick = onBatchSelected) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Batch tools",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            IconButton(
                onClick = onSelectAll,
                modifier = Modifier.testTag("button_select_all")
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "Select all",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            IconButton(
                onClick = onDeleteSelected,
                modifier = Modifier.testTag("button_delete_selected")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete selected",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
