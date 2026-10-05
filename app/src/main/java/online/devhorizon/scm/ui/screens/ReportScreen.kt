package online.devhorizon.scm.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import online.devhorizon.scm.R
import online.devhorizon.scm.report.ReportStorage
import online.devhorizon.scm.ui.AnalysisUi
import online.devhorizon.scm.ui.MainViewModel
import java.io.File

private data class ReportTab(val label: String, val file: File)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val analysis by vm.analysis.collectAsState()
    val context = LocalContext.current
    val storage = remember { ReportStorage(context) }

    val tabs: List<ReportTab> = when (val a = analysis) {
        is AnalysisUi.Done -> listOf(ReportTab(stringResource(R.string.report_tab_default), a.reportFile))
        is AnalysisUi.ExpressDone -> buildList {
            add(ReportTab(stringResource(R.string.report_tab_super), a.superFile))
            a.reports.forEach { add(ReportTab(it.provider.label, it.file)) }
        }
        else -> emptyList()
    }

    var selected by remember { mutableIntStateOf(0) }
    val active = tabs.getOrNull(selected)
    val content: String? = remember(active?.file?.absolutePath) {
        active?.file?.let { runCatching { it.readText() }.getOrNull() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = active != null,
                        onClick = {
                            active?.let {
                                runCatching { context.startActivity(storage.shareIntent(storage.exportCopy(it.file))) }
                            }
                        },
                    ) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share)) }
                    IconButton(
                        enabled = active != null,
                        onClick = {
                            active?.let {
                                runCatching { context.startActivity(storage.viewIntent(storage.exportCopy(it.file))) }
                            }
                        },
                    ) { Icon(Icons.Filled.OpenInNew, contentDescription = stringResource(R.string.cd_open_browser)) }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (tabs.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        AssistChip(
                            onClick = { selected = index },
                            label = { Text(tab.label) },
                            colors = if (index == selected) {
                                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                AssistChipDefaults.assistChipColors()
                            },
                        )
                    }
                }
            }

            if (content == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.report_none_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.report_none_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            webViewClient = WebViewClient()
                        }
                    },
                    update = { web -> web.loadDataWithBaseURL(null, content, "text/html", "utf-8", null) },
                )
            }
        }
    }
}
