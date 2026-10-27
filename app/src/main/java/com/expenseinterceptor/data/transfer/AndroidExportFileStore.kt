package com.expenseinterceptor.data.transfer

import android.content.Context
import androidx.core.content.FileProvider
import com.expenseinterceptor.domain.port.ExportFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes export files into the app cache and exposes them through the FileProvider.
 *
 * Exports contain financial data, so files older than a day are removed on every new export and
 * nothing is written to shared storage.
 */
class AndroidExportFileStore @Inject constructor(@ApplicationContext private val context: Context) :
  ExportFileStore {

  override suspend fun write(fileName: String, writer: (OutputStream) -> Unit): String =
    withContext(Dispatchers.IO) {
      val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
      deleteStale(dir)
      val file = File(dir, fileName)
      file.outputStream().use(writer)
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
    }

  private fun deleteStale(dir: File) {
    val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
    dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
  }

  private companion object {
    const val EXPORT_DIR = "exports"
    const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
  }
}
