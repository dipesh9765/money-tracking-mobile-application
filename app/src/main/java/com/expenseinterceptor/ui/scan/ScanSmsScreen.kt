package com.expenseinterceptor.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expenseinterceptor.domain.sms.IgnoreReason
import com.expenseinterceptor.domain.usecase.ScanItem
import com.expenseinterceptor.domain.usecase.ScanOutcome
import com.expenseinterceptor.ui.components.DateButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScanSmsScreen(viewModel: ScanViewModel, onClose: () -> Unit) {
  val uiState by viewModel.uiState.collectAsState()
  Scaffold(
    topBar = {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        Text(
          text = "Scan Old SMS",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(0.dp))
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Date Range",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateButton(uiState.range.startMillis, viewModel::updateStartDate)
            DateButton(viewModel.lastDayMillis(), viewModel::updateEndDate)
          }
          Spacer(modifier = Modifier.height(12.dp))
          Button(onClick = { viewModel.scan() }, enabled = !uiState.isScanning) {
            if (uiState.isScanning) {
              CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp).height(18.dp),
                strokeWidth = 2.dp
              )
            }
            Text("Scan")
          }
          uiState.scanError?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = it,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      }

      if (uiState.results.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text("No results yet.", style = MaterialTheme.typography.titleMedium)
          Text(
            "Run a scan to see detected messages.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        val processedCount = uiState.detectedCount
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text =
              "$processedCount detected (${uiState.newCount} new) / ${uiState.results.size} scanned",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
          )
          Button(
            onClick = { viewModel.saveDetected() },
            enabled = uiState.newCount > 0 && !uiState.saveInProgress
          ) {
            if (uiState.saveInProgress) {
              CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp).height(18.dp),
                strokeWidth = 2.dp
              )
            } else {
              Icon(Icons.Default.Save, contentDescription = "Save")
              Spacer(modifier = Modifier.width(6.dp))
            }
            Text("Save new")
          }
        }
        uiState.saveResult?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(
            uiState.results,
            key = { "${it.sms.timestamp}-${it.sms.sender}-${it.sms.body.hashCode()}" }
          ) { item ->
            ScanResultItem(item)
          }
        }
      }
    }
  }
}

private val SCAN_TIME_FORMAT: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())

@Composable
private fun ScanResultItem(item: ScanItem) {
  val detected = item.outcome as? ScanOutcome.Detected
  val statusColor =
    if (detected != null && !detected.alreadySaved) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    }
  val status =
    when (val outcome = item.outcome) {
      is ScanOutcome.Detected ->
        "Detected: ${outcome.transaction.amount.format()} at ${outcome.transaction.merchant}" +
          if (outcome.alreadySaved) " (already saved)" else ""
      is ScanOutcome.Skipped ->
        when (outcome.reason) {
          IgnoreReason.SENDER_NOT_ALLOWED -> "Sender not in your bank list"
          IgnoreReason.NOT_A_TRANSACTION -> "Not a transaction"
          IgnoreReason.AMOUNT_NOT_FOUND -> "Amount not found"
        }
    }

  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = item.sms.sender,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            SCAN_TIME_FORMAT.format(
              Instant.ofEpochMilli(item.sms.timestamp).atZone(ZoneId.systemDefault())
            ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = item.sms.body,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(text = status, style = MaterialTheme.typography.bodySmall, color = statusColor)
    }
  }
}
