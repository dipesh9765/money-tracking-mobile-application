package com.expenseinterceptor.ui.settings

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Lets the user choose which SMS senders count as banks. Matching ignores case and the rotating
 * operator prefix, so `HDFCBK` matches `VM-HDFCBK-S`.
 */
@Composable
fun SendersScreen(onClose: () -> Unit, viewModel: SendersViewModel = hiltViewModel()) {
  val senders by viewModel.senders.collectAsState()
  val state by viewModel.state.collectAsState()

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        Text(
          text = "Bank senders",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        "Only SMS from these sender ids are read. Use the stable part of the id, for example " +
          "HDFCBK for VM-HDFCBK-S.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        OutlinedTextField(
          value = state.input,
          onValueChange = viewModel::updateInput,
          label = { Text("Sender id") },
          singleLine = true,
          isError = state.message != null,
          supportingText = state.message?.let { message -> { Text(message) } },
          modifier = Modifier.weight(1f),
        )
        Button(onClick = viewModel::add, modifier = Modifier.padding(top = 8.dp)) { Text("Add") }
      }
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(senders, key = { it }) { sender ->
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(sender, style = MaterialTheme.typography.titleMedium)
              IconButton(onClick = { viewModel.remove(sender) }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove $sender")
              }
            }
          }
        }
      }
      TextButton(onClick = viewModel::resetToDefaults) { Text("Reset to defaults") }
    }
  }
}
