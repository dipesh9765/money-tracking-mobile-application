package com.expenseinterceptor.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expenseinterceptor.util.SmsDebugLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(onClose: () -> Unit) {
  val logs by SmsDebugLogger.logs.collectAsState()

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("SMS Debug Log") },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { SmsDebugLogger.clear() }) {
            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
          }
        }
      )
    }
  ) { paddingValues ->
    if (logs.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
        verticalArrangement = Arrangement.Center
      ) {
        Text(text = "No log entries yet.", style = MaterialTheme.typography.titleMedium)
        Text(
          text = "New SMS processing details will appear here.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(logs, key = { it.id }) { entry -> DebugLogItem(entry) }
      }
    }
  }
}

@Composable
private fun DebugLogItem(entry: SmsDebugLogger.LogEntry) {
  Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        text = entry.sender,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = entry.formattedTime,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Text(
      text = entry.preview,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = "Stage: ${entry.stage}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = "Outcome: ${entry.outcome}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    entry.details?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
