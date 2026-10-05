package online.devhorizon.scm.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import online.devhorizon.scm.domain.model.AnalysisResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persists generated reports and exposes them for sharing / external opening. */
class ReportStorage(private val context: Context) {

    fun save(result: AnalysisResult, html: String): File {
        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val name = "scm-report-${result.repoName.ifBlank { "repo" }.replace(Regex("[^A-Za-z0-9._-]"), "_")}-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".html"
        val file = File(dir, name)
        file.writeText(html)
        return file
    }

    /** External copy for sharing/opening in other apps. */
    fun exportCopy(file: File): File {
        val dir = File(context.getExternalFilesDir(null), "reports").apply { mkdirs() }
        val out = File(dir, file.name)
        file.copyTo(out, overwrite = true)
        return out
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SCM Analyzer report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun viewIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
