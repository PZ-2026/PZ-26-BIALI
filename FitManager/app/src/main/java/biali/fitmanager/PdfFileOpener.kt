package biali.fitmanager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

fun openPdfFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val chooser = Intent.createChooser(viewIntent, "Otwórz raport PDF")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(chooser)
        Toast.makeText(context, "Raport PDF wygenerowany.", Toast.LENGTH_SHORT).show()
    } catch (_: ActivityNotFoundException) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, "Udostępnij raport PDF"))
            Toast.makeText(context, "Brak aplikacji PDF. Udostępnij plik ręcznie.", Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Raport zapisany: ${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
