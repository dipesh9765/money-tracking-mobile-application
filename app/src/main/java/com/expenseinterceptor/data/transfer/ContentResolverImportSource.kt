package com.expenseinterceptor.data.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** A file the user picked, ready to be read. */
class ImportFile(val stream: InputStream, val kind: ImportKind)

/** Opens a user-picked document. Separate from the importer so tests can supply plain streams. */
interface ImportSource {
  /** Throws [IOException] if the file is missing, unreadable or larger than [MAX_BYTES]. */
  fun open(uri: String): ImportFile

  companion object {
    /**
     * Larger files are refused before parsing: the workbook reader holds the whole file in memory.
     */
    const val MAX_BYTES = 5L * 1024 * 1024
  }
}

class ContentResolverImportSource
@Inject
constructor(@ApplicationContext private val context: Context) : ImportSource {

  override fun open(uri: String): ImportFile {
    val parsed = Uri.parse(uri)
    val resolver = context.contentResolver
    val size =
      resolver.query(parsed, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
      }
    if (size != null && size > ImportSource.MAX_BYTES) {
      throw IOException("File is larger than ${ImportSource.MAX_BYTES / BYTES_PER_MB} MB")
    }
    val mime = resolver.getType(parsed).orEmpty()
    val name = displayName(parsed)
    val excel =
      mime.contains("spreadsheet") ||
        mime.contains("excel") ||
        name.endsWith(".xlsx", ignoreCase = true)
    val stream = resolver.openInputStream(parsed) ?: throw IOException("Could not open file")
    return ImportFile(stream, if (excel) ImportKind.EXCEL else ImportKind.CSV)
  }

  private fun displayName(uri: Uri): String =
    context.contentResolver
      .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
      ?: uri.lastPathSegment.orEmpty()

  private companion object {
    const val BYTES_PER_MB = 1024 * 1024
  }
}
