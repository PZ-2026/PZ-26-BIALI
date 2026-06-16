package biali.fitmanager.pdf

import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.OutputStream

/**
 * Publiczne API biblioteki — generuje raport postępów do pliku PDF.
 */
object ProgressReportGenerator {

  fun writeTo(file: File, data: ProgressReportData) {
    file.parentFile?.mkdirs()
    file.outputStream().use { stream -> writeTo(stream, data) }
  }

  fun writeTo(outputStream: OutputStream, data: ProgressReportData) {
    val document = PdfDocument()
    try {
      ProgressPdfRenderer(document).render(data)
      document.writeTo(outputStream)
    } finally {
      document.close()
    }
  }
}
