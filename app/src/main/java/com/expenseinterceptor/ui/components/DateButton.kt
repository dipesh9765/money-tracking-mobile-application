package com.expenseinterceptor.ui.components

import android.app.DatePickerDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

/**
 * A button showing [millis] as a date that opens a date picker. [onPicked] receives the start of
 * the chosen day in the device's time zone; how that becomes a range is decided by the view model.
 */
@Composable
fun DateButton(millis: Long, onPicked: (Long) -> Unit, textStyle: TextStyle? = null) {
  val context = LocalContext.current
  val zone = ZoneId.systemDefault()
  val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
  Button(
    onClick = {
      DatePickerDialog(
          context,
          { _, year, month, day ->
            val picked = java.time.LocalDate.of(year, month + 1, day)
            onPicked(picked.atStartOfDay(zone).toInstant().toEpochMilli())
          },
          date.year,
          date.monthValue - 1,
          date.dayOfMonth,
        )
        .show()
    }
  ) {
    if (textStyle == null) Text(DAY_FORMAT.format(date))
    else Text(DAY_FORMAT.format(date), style = textStyle)
  }
}
