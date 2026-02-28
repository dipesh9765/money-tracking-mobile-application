package com.expenseinterceptor.ui.main

import android.app.DatePickerDialog
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expenseinterceptor.ui.quickadd.ExpenseCategory
import com.expenseinterceptor.ui.quickadd.getCategoryColor
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return format.format(amount)
}

private fun getCategoryFromString(category: String): ExpenseCategory {
    return ExpenseCategory.entries.find { it.displayName == category } ?: ExpenseCategory.OTHER
}

private fun getCategoryColorByString(category: String): Color {
    return getCategoryColor(getCategoryFromString(category))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardContent(
    viewModel: MainViewModel,
    startDateMillis: Long,
    endDateMillis: Long,
    onStartDateChange: (Long) -> Unit,
    onEndDateChange: (Long) -> Unit
) {
    val expenses by viewModel.dashboardExpenses.collectAsState()
    val totalAmount by viewModel.dashboardTotalAmount.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val timeSeriesData by viewModel.timeSeriesData.collectAsState()
    val dashboardState by viewModel.dashboardState.collectAsState()
    val availableBanks by viewModel.availableBanks.collectAsState()

    var chartType by remember { mutableStateOf(0) } // 0=Bar, 1=Line, 2=Pie

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val displayEnd = (endDateMillis - 1).coerceAtLeast(startDateMillis)

    fun showDatePicker(current: Long, setToEndOfDay: Boolean, onResult: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { time = Date(current) }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val newCal = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val millis = if (setToEndOfDay) {
                    toEndOfDay(newCal.timeInMillis)
                } else {
                    toStartOfDay(newCal.timeInMillis)
                }
                onResult(millis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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
                Button(
                    onClick = { showDatePicker(startDateMillis, false, onStartDateChange) }
                ) {
                    Text(dateFormat.format(Date(startDateMillis)), style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = { showDatePicker(displayEnd, true, onEndDateChange) }
                ) {
                    Text(dateFormat.format(Date(displayEnd)), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        item {
            Text("Time scale", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardTimeScale.entries.forEach { scale ->
                    FilterChip(
                        selected = dashboardState.timeScale == scale,
                        onClick = { viewModel.setDashboardTimeScale(scale) },
                        label = { Text(scale.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        item {
            Text("Filter by category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.entries.forEach { cat ->
                    val selected = cat.displayName in dashboardState.selectedCategories
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleDashboardCategory(cat.displayName) },
                        label = { Text(cat.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
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
                        onClick = { viewModel.toggleDashboardHighlight(cat.displayName) },
                        label = { Text(cat.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
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
                        val selected = bank in dashboardState.selectedBanks
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleDashboardBank(bank) },
                            label = { Text(bank) }
                        )
                    }
                }
            }
        }

        item {
            Text("Transaction type", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "DEBIT" to "Debit", "CREDIT" to "Credit").forEach { (value, label) ->
                    val selected = dashboardState.transactionTypeFilter.equals(value, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setDashboardTransactionType(value) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (value == "CREDIT") Color(0xFF2E7D32) else Color(0xFFC62828),
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
                        selected = dashboardState.sortBy == sort,
                        onClick = { viewModel.setDashboardSortBy(sort) },
                        label = {
                            Text(
                                sort.name.lowercase()
                                    .split("_")
                                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
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
                ).forEachIndexed { idx, (icon, label) ->
                    FilterChip(
                        selected = chartType == idx,
                        onClick = { chartType = idx },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
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
                item {
                    TimeSeriesBarChart(timeSeriesData = timeSeriesData)
                }
            }
            1 -> {
                item {
                    TimeSeriesLineChart(timeSeriesData = timeSeriesData)
                }
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
                                    modifier = Modifier
                                        .size(12.dp)
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
private fun TimeSeriesBarChart(timeSeriesData: List<TimeSeriesPoint>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(timeSeriesData) {
        modelProducer.runTransaction {
            if (timeSeriesData.isNotEmpty()) {
                columnSeries {
                    series(timeSeriesData.map { it.amount.toFloat() })
                }
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (timeSeriesData.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No data for chart", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            CartesianChartHost(
                chart = rememberCartesianChart(rememberColumnCartesianLayer()),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun TimeSeriesLineChart(timeSeriesData: List<TimeSeriesPoint>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(timeSeriesData) {
        modelProducer.runTransaction {
            if (timeSeriesData.isNotEmpty()) {
                lineSeries {
                    series(timeSeriesData.map { it.amount.toFloat() })
                }
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (timeSeriesData.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No data for chart", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            CartesianChartHost(
                chart = rememberCartesianChart(rememberLineCartesianLayer()),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun CategoryPieChart(
    categoryBreakdown: List<CategoryBreakdown>,
    highlightedCategories: Set<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (categoryBreakdown.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No data for chart", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val total = categoryBreakdown.sumOf { it.amount }
                    if (total <= 0) return@Canvas
                    var startAngle = -90f
                    val strokeWidth = size.minDimension / 8f
                    val radius = (size.minDimension - strokeWidth) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    categoryBreakdown.forEach { item ->
                        val sweep = (item.amount / total * 360f).toFloat()
                        val isHighlighted = item.category in highlightedCategories
                        drawArc(
                            color = getCategoryColorByString(item.category).let { c ->
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
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

private fun toStartOfDay(millis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun toEndOfDay(millis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis + 1
}
