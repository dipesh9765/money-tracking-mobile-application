package com.expenseinterceptor.data.transfer

import com.expenseinterceptor.domain.repository.ExpenseRepository
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ImportKind {
  CSV,
  EXCEL,
}

/** Result of an import: what was added, what was already there, and what was unusable. */
data class ImportSummary(val imported: Int, val duplicates: Int, val errors: List<String>) {
  val message: String
    get() =
      when {
        imported == 0 && duplicates == 0 -> errors.firstOrNull() ?: "No transactions to import"
        else ->
          buildString {
            append("Imported $imported transaction${if (imported == 1) "" else "s"}")
            if (duplicates > 0) append(", skipped $duplicates already present")
            if (errors.isNotEmpty()) append(". ${errors.size} rows had errors")
          }
      }
}

/** Reads a CSV or Excel file and stores the rows that are not already in the database. */
class ExpenseImporter
@Inject
constructor(
  private val csv: CsvCodec,
  private val excel: ExcelCodec,
  private val repository: ExpenseRepository,
) {

  /** Throws if the file cannot be read at all; per-row problems are reported in the summary. */
  suspend fun import(input: InputStream, kind: ImportKind): ImportSummary {
    val parsed =
      withContext(Dispatchers.Default) {
        when (kind) {
          ImportKind.CSV -> csv.read(input)
          ImportKind.EXCEL -> excel.read(input)
        }
      }
    val saved = repository.saveAll(parsed.expenses)
    return ImportSummary(saved.saved, saved.skippedDuplicates, parsed.errors)
  }
}
