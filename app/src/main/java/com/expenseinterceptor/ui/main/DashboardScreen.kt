package com.expenseinterceptor.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expenseinterceptor.domain.model.ExpenseCategory
import com.expenseinterceptor.domain.model.Money
import com.expenseinterceptor.domain.usecase.CategoryShare
import com.expenseinterceptor.domain.usecase.DashboardSortBy
import com.expenseinterceptor.domain.usecase.DashboardTimeScale
import com.expenseinterceptor.domain.usecase.TimeBucket
import com.expenseinterceptor.domain.usecase.TypeFilter
import com.expenseinterceptor.ui.components.DateButton
import com.expenseinterceptor.ui.quickadd.getCategoryColor
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

private fun formatCurrency(amount: Money): String = amount.format()

private fun getCategoryFromString(category: String): ExpenseCategory =
  ExpenseCategory.fromDisplayName(category)

private fun getCategoryColorByString(category: String): Color {
  return getCategoryColor(getCategoryFromString(category))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardContent(viewModel: DashboardViewModel) {
  val model by viewModel.model.collectAsState()
  val dashboardState by viewModel.state.collectAsState()
  val range by viewModel.range.collectAsState()
  val filter = dashboardState.filter
  val expenses = model.expenses
  val totalAmount = model.net
  val categoryBreakdown = model.categoryShares
  val timeSeriesData = model.timeSeries
  val availableBanks = model.banks

  var chartType by remember { mutableStateOf(0) } // 0=Bar, 1=Line, 2=Pie

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            "Total (filtered)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
          )
          Text(
            formatCurrency(totalAmount),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
          Text(
            "${expenses.size} transactions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Date range:", style = MaterialTheme.typography.labelLarge)
        DateButton(
          range.startMillis,
          viewModel::updateStartDate,
          MaterialTheme.typography.labelMedium,
        )
        DateButton(
          maxOf(range.startMillis, range.endExclusiveMillis - 1),
          viewModel::updateEndDate,
          MaterialTheme.typography.labelMedium,
        )
      }
    }

    item {
      Text("Time scale", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardTimeScale.entries.forEach { scale ->
          FilterChip(
            selected = filter.timeScale == scale,
            onClick = { viewModel.setTimeScale(scale) },
            label = { Text(scale.name.lowercase().replaceFirstChar { it.uppercase() }) }
          )
        }
      }
    }

    item {
      Text("Filter by category", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseCategory.entries.forEach { cat ->
          val selected = cat.displayName in filter.categories
          FilterChip(
            selected = selected,
            onClick = { viewModel.toggleCategory(cat.displayName) },
            label = { Text(cat.displayName) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = getCategoryColor(cat),
                selectedLabelColor = Color.White
              )
          )
        }
      }
    }

    item {
      Text("Highlight in chart (tap)", style = MaterialTheme.typography.labelSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseCategory.entries.forEach { cat ->
          val highlighted = cat.displayName in dashboardState.highlightedCategories
          FilterChip(
            selected = highlighted,
            onClick = { viewModel.toggleHighlight(cat.displayName) },
            label = { Text(cat.displayName) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = getCategoryColor(cat).copy(alpha = 0.5f)
              )
          )
        }
      }
    }

    if (availableBanks.isNotEmpty()) {
      item {
        Text("Filter by bank", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          availableBanks.sorted().forEach { bank ->
            val selected = bank in filter.banks
            FilterChip(
              selected = selected,
              onClick = { viewModel.toggleBank(bank) },
              label = { Text(bank) }
            )
          }
        }
      }
    }

    item {
      Text("Transaction type", style = MaterialTheme.typography.titleSmall)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(TypeFilter.ALL to "All", TypeFilter.DEBIT to "Debit", TypeFilter.CREDIT to "Credit")
          .forEach { (value, label) ->
            val selected = filter.type == value
            FilterChip(
              selected = selected,
              onClick = { viewModel.setType(value) },
              label = { Text(label) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor =
                    if (value == TypeFilter.CREDIT) Color(0xFF2E7D32) else Color(0xFFC62828),
                  selectedLabelColor = Color.White
                )
            )
          }
      }
    }

    item {
      Text("Sort by", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardSortBy.entries.forEach { sort ->
          FilterChip(
            selected = filter.sortBy == sort,
            onClick = { viewModel.setSortBy(sort) },
            label = {
              Text(
                sort.name.lowercase().split("_").joinToString(" ") {
                  it.replaceFirstChar { c -> c.uppercase() }
                }
              )
            }
          )
        }
      }
    }

    item {
      Text("Chart type", style = MaterialTheme.typography.titleSmall)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Icons.Default.BarChart to "Bar",
            Icons.Default.ShowChart to "Line",
            Icons.Default.PieChart to "Pie"
          )
          .forEachIndexed { idx, (icon, label) ->
            FilterChip(
              selected = chartType == idx,
              onClick = { chartType = idx },
              label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.size(4.dp))
                  Text(label)
                }
              }
            )
          }
      }
    }

    when (chartType) {
      0 -> {
        item { TimeSeriesBarChart(timeSeriesData = timeSeriesData) }
      }
      1 -> {
        item { TimeSeriesLineChart(timeSeriesData = timeSeriesData) }
      }
      2 -> {
        item {
          CategoryPieChart(
            categoryBreakdown = categoryBreakdown,
            highlightedCategories = dashboardState.highlightedCategories
          )
        }
      }
    }

    if (categoryBreakdown.isNotEmpty() && chartType != 2) {
      item {
        Text("By category", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          categoryBreakdown.take(8).forEach { item ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier =
                    Modifier.size(12.dp)
                      .clip(CircleShape)
                      .background(getCategoryColorByString(item.category))
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                  item.category,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Text(
                "${formatCurrency(item.amount)} (${"%.1f".format(item.percentage)}%)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TimeSeriesBarChart(timeSeriesData: List<TimeBucket>) {
  val modelProducer = remember { CartesianChartModelProducer() }
  LaunchedEffect(timeSeriesData) {
    modelProducer.runTransaction {
      if (timeSeriesData.isNotEmpty()) {
        columnSeries { series(timeSeriesData.map { it.amount.toDouble().toFloat() }) }
      }
    }
  }
  Card(modifier = Modifier.fillMaxWidth().height(220.dp), shape = RoundedCornerShape(12.dp)) {
    if (timeSeriesData.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No data for chart", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      CartesianChartHost(
        chart = rememberCartesianChart(rememberColumnCartesianLayer()),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp)
      )
    }
  }
}

@Composable
private fun TimeSeriesLineChart(timeSeriesData: List<TimeBucket>) {
  val modelProducer = remember { CartesianChartModelProducer() }
  LaunchedEffect(timeSeriesData) {
    modelProducer.runTransaction {
      if (timeSeriesData.isNotEmpty()) {
        lineSeries { series(timeSeriesData.map { it.amount.toDouble().toFloat() }) }
      }
    }
  }
  Card(modifier = Modifier.fillMaxWidth().height(220.dp), shape = RoundedCornerShape(12.dp)) {
    if (timeSeriesData.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No data for chart", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      CartesianChartHost(
        chart = rememberCartesianChart(rememberLineCartesianLayer()),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp)
      )
    }
  }
}

@Composable
private fun CategoryPieChart(
  categoryBreakdown: List<CategoryShare>,
  highlightedCategories: Set<String>
) {
  Card(modifier = Modifier.fillMaxWidth().height(260.dp), shape = RoundedCornerShape(12.dp)) {
    if (categoryBreakdown.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No data for chart", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
          val total = categoryBreakdown.sumOf { it.amount.minor }
          if (total <= 0) return@Canvas
          var startAngle = -90f
          val strokeWidth = size.minDimension / 8f
          val radius = (size.minDimension - strokeWidth) / 2f
          val center = Offset(size.width / 2f, size.height / 2f)
          categoryBreakdown.forEach { item ->
            val sweep = (item.amount.minor.toDouble() / total * 360f).toFloat()
            val isHighlighted = item.category in highlightedCategories
            drawArc(
              color =
                getCategoryColorByString(item.category).let { c ->
                  if (isHighlighted) c else c.copy(alpha = 0.5f)
                },
              startAngle = startAngle,
              sweepAngle = sweep,
              useCenter = false,
              topLeft = Offset(center.x - radius, center.y - radius),
              size = Size(radius * 2, radius * 2),
              style = Stroke(width = strokeWidth)
            )
            startAngle += sweep
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          categoryBreakdown.take(6).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(2.dp)) {
              Box(
                modifier =
                  Modifier.size(8.dp)
                    .clip(CircleShape)
                    .background(getCategoryColorByString(item.category))
              )
              Spacer(modifier = Modifier.size(4.dp))
              Text(
                "${item.category} ${"%.0f".format(item.percentage)}%",
                style = MaterialTheme.typography.labelSmall
              )
            }
          }
        }
      }
    }
  }
}
