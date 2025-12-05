package com.expenseinterceptor.ui.main

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expenseinterceptor.data.local.ExpenseEntity
import com.expenseinterceptor.ui.quickadd.ExpenseCategory
import com.expenseinterceptor.ui.quickadd.getCategoryColor
import com.expenseinterceptor.ui.scan.ScanSmsScreen
import com.expenseinterceptor.ui.scan.ScanViewModel
import com.expenseinterceptor.ui.theme.Primary
import com.expenseinterceptor.ui.theme.PrimaryLight
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

private enum class MainSubScreen {
    HOME,
    SCAN
}

/**
 * Formats a double as Indian Rupee currency.
 */
private fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return format.format(amount)
}

/**
 * Formats a timestamp to a readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return format.format(Date(timestamp))
}

/**
 * Gets ExpenseCategory enum from string.
 */
private fun getCategoryFromString(category: String): ExpenseCategory {
    return ExpenseCategory.entries.find { it.displayName == category } ?: ExpenseCategory.OTHER
}

/**
 * Main screen showing the expense list and summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onShare: (android.content.Intent) -> Unit
) {
    var currentScreen by remember { mutableStateOf(MainSubScreen.HOME) }
    val scanViewModel: ScanViewModel = hiltViewModel()

    val expenses by viewModel.expenses.collectAsState()
    val totalAmount by viewModel.totalAmount.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val editState by viewModel.editState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showShareOptions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    when (currentScreen) {
        MainSubScreen.SCAN -> {
            ScanSmsScreen(
                viewModel = scanViewModel,
                onClose = { currentScreen = MainSubScreen.HOME }
            )
        }
        MainSubScreen.HOME -> {
            LaunchedEffect(uiState.shareIntent, uiState.shareError, uiState.infoMessage) {
                uiState.shareIntent?.let {
                    onShare(it)
                    viewModel.consumeShareIntent()
                }
                uiState.shareError?.let {
                    snackbarHostState.showSnackbar(it)
                    viewModel.clearShareError()
                }
                uiState.infoMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    viewModel.clearInfoMessage()
                }
            }

            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = {
                            Column {
                                Text(
                                    "Expense Interceptor",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    viewModel.getCurrentRangeLabel(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showOverflow = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflow,
                                    onDismissRequest = { showOverflow = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Scan Old SMS") },
                                        onClick = {
                                            showOverflow = false
                                            currentScreen = MainSubScreen.SCAN
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete all") },
                                        onClick = {
                                            showOverflow = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        scrollBehavior = scrollBehavior
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showShareOptions = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (uiState.isPreparingShare) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Excel"
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Summary Card
                    SummaryCard(
                        totalAmount = totalAmount,
                        expenseCount = expenses.size,
                        rangeLabel = uiState.dateRangeLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    DateRangeSelector(
                        startDateMillis = uiState.startDateMillis,
                        endDateMillis = uiState.endDateMillis,
                        onStartDateChange = viewModel::updateStartDate,
                        onEndDateChange = viewModel::updateEndDate
                    )

                    // Expense List
                    if (expenses.isEmpty()) {
                        EmptyState(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 88.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = expenses,
                                key = { it.id }
                            ) { expense ->
                                ExpenseItem(
                                    expense = expense,
                                    onDelete = { viewModel.deleteExpense(expense) },
                                    onClick = { viewModel.startEditing(expense) }
                                )
                            }
                        }
                    }
                }
            }

            editState?.let { state ->
                ExpenseEditDialog(
                    state = state,
                    onDismiss = { viewModel.dismissEditing() },
                    onCategoryChange = viewModel::updateEditCategory,
                    onBankChange = viewModel::updateEditBank,
                    onTypeChange = viewModel::updateEditTransactionType,
                    onSave = { viewModel.saveEditedExpense() }
                )
            }

            if (showShareOptions) {
                ShareOptionsDialog(
                    onDismiss = { showShareOptions = false },
                    onSelect = { format ->
                        showShareOptions = false
                        viewModel.prepareShare(format)
                    }
                )
            }

            if (showDeleteConfirm) {
                ConfirmDeleteAllDialog(
                    onConfirm = {
                        showDeleteConfirm = false
                        viewModel.deleteAllExpenses()
                    },
                    onDismiss = { showDeleteConfirm = false }
                )
            }
        }
    }
}

/**
 * Summary card showing total amount spent.
 */
@Composable
private fun SummaryCard(
    totalAmount: Double,
    expenseCount: Int,
    rangeLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Primary, PrimaryLight)
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    "Total Spent",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    formatCurrency(totalAmount),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "$expenseCount transactions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Individual expense item with swipe to delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseItem(
    expense: ExpenseEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Delete background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        },
        content = {
            ExpenseItemContent(expense = expense, onClick = onClick)
        }
    )
}

/**
 * Content of an expense item card.
 */
@Composable
private fun ExpenseItemContent(expense: ExpenseEntity, onClick: () -> Unit) {
    val category = getCategoryFromString(expense.category)
    val categoryColor = getCategoryColor(category)
    val isCredit = expense.transactionType.equals("CREDIT", ignoreCase = true)
    val amountColor = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = expense.category.first().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = expense.title ?: expense.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (expense.title != null) {
                    Text(
                        text = expense.merchant,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatDate(expense.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Bank: ${expense.bank}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatCurrency(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(categoryColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = expense.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(amountColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = expense.transactionType.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = amountColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseEditDialog(
    state: EditExpenseState,
    onDismiss: () -> Unit,
    onCategoryChange: (ExpenseCategory) -> Unit,
    onBankChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    state.original.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatCurrency(state.original.amount),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state.transactionType == "CREDIT") Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Text(
                    formatDate(state.original.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseCategory.entries.forEach { category ->
                        val selected = state.category == category
                        val color = getCategoryColor(category)
                        FilterChip(
                            selected = selected,
                            onClick = { onCategoryChange(category) },
                            label = { Text(category.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = state.bank,
                    onValueChange = onBankChange,
                    label = { Text("Bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    "Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val types = listOf("DEBIT" to "Debit", "CREDIT" to "Credit")
                    types.forEach { (value, label) ->
                        val selected = state.transactionType.equals(value, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { onTypeChange(value) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (value == "CREDIT") Color(0xFF2E7D32) else Color(0xFFC62828),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                if (state.error != null) {
                    Text(
                        state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !state.isSaving) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(if (state.isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Empty state when no expenses exist.
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "💸",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No expenses yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "When you receive a bank SMS, you'll be\nprompted to categorize the expense.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun DateRangeSelector(
    startDateMillis: Long,
    endDateMillis: Long,
    onStartDateChange: (Long) -> Unit,
    onEndDateChange: (Long) -> Unit
) {
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { showDatePicker(startDateMillis, false, onStartDateChange) }) {
            Text(dateFormat.format(Date(startDateMillis)))
        }
        Button(onClick = { showDatePicker(displayEnd, true, onEndDateChange) }) {
            Text(dateFormat.format(Date(displayEnd)))
        }
    }
}

@Composable
private fun ShareOptionsDialog(
    onDismiss: () -> Unit,
    onSelect: (ShareFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share expenses") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSelect(ShareFormat.TEXT) }) {
                    Text("Formatted text")
                }
                Button(onClick = { onSelect(ShareFormat.CSV) }) {
                    Text("CSV file")
                }
                Button(onClick = { onSelect(ShareFormat.EXCEL) }) {
                    Text("Excel (.xlsx)")
                }
                Button(onClick = { onSelect(ShareFormat.PDF) }) {
                    Text("PDF file")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ConfirmDeleteAllDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all expenses?") },
        text = { Text("This will remove all parsed expenses. This action cannot be undone.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete all")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
    return cal.timeInMillis + 1 // exclusive upper bound for Room queries
}

