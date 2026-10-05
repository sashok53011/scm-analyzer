package online.devhorizon.scm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.devhorizon.scm.R
import online.devhorizon.scm.domain.model.Badge
import online.devhorizon.scm.ui.AnalysisUi
import online.devhorizon.scm.ui.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onViewReport: () -> Unit,
) {
    val pending by vm.pending.collectAsState()
    val analysis by vm.analysis.collectAsState()
    val log by vm.log.collectAsState()
    val providers by vm.providers.collectAsState()
    val selectedId by vm.selectedId.collectAsState()

    var useLlm by remember { mutableStateOf(false) }
    var maxFiles by remember { mutableFloatStateOf(2000f) }
    var maxLlm by remember { mutableFloatStateOf(200f) }

    val provider = providers.firstOrNull { it.id == selectedId } ?: providers.first()
    val running = analysis is AnalysisUi.Running

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.analyze_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val repo = pending
            if (repo == null) {
                Text(stringResource(R.string.analyze_no_repo), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(repo.source.displayName, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.analyze_repo_meta,
                                repo.kind,
                                repo.fileCount,
                                String.format(Locale.US, "%.1f", repo.totalBytes / (1024.0 * 1024.0)),
                            )
                        )
                    },
                )
            }

            Text(
                stringResource(R.string.analyze_options),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(stringResource(R.string.analyze_llm_review), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Switch(checked = useLlm, onCheckedChange = { useLlm = it }, enabled = !running)
                    }
                    if (useLlm) {
                        Text(
                            stringResource(R.string.analyze_provider_line, provider.label, provider.model),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!provider.isReady) {
                            Text(
                                stringResource(R.string.analyze_provider_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(stringResource(R.string.analyze_max_files, maxFiles.toInt()), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = maxFiles,
                        onValueChange = { maxFiles = it },
                        valueRange = 100f..10000f,
                        enabled = !running,
                    )
                    if (useLlm) {
                        Text(stringResource(R.string.analyze_llm_budget, maxLlm.toInt()), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = maxLlm,
                            onValueChange = { maxLlm = it },
                            valueRange = 20f..1000f,
                            enabled = !running,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.startAnalysis(useLlm, maxFiles.toInt(), maxLlm.toInt()) },
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.analyze_run)) }
                if (running) {
                    OutlinedButton(onClick = { vm.cancelAnalysis() }) { Text(stringResource(R.string.action_cancel)) }
                }
            }

            OutlinedButton(
                onClick = { vm.startExpress(maxFiles.toInt(), maxLlm.toInt()) },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.analyze_express, providers.count { it.isReady }))
            }
            Text(
                stringResource(R.string.analyze_express_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val a = analysis) {
                is AnalysisUi.Running -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.analyze_running), fontWeight = FontWeight.SemiBold)
                            if (a.total > 0) {
                                LinearProgressIndicator(
                                    progress = { (a.done.toFloat() / a.total).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("${a.done} / ${a.total}", style = MaterialTheme.typography.bodySmall)
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            if (a.message.isNotBlank()) {
                                Text(a.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                is AnalysisUi.Done -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.analyze_done_title), fontWeight = FontWeight.SemiBold)
                            val r = a.result
                            Text(
                                stringResource(
                                    R.string.analyze_done_meta,
                                    r.elements.size,
                                    r.filesAnalyzed,
                                    r.filesFound,
                                    String.format(Locale.US, "%.1f", r.lineCoveragePercent()),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(
                                    R.string.analyze_done_verdicts,
                                    r.badgeCounts[Badge.VULNERABILITY] ?: 0,
                                    r.badgeCounts[Badge.WARNING] ?: 0,
                                    r.badgeCounts[Badge.PASS] ?: 0,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(onClick = onViewReport, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.analyze_view_report))
                            }
                        }
                    }
                }

                is AnalysisUi.Failed -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.analyze_failed), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            Text(a.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                is AnalysisUi.ExpressDone -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.analyze_express_done_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.analyze_express_done_meta, a.reports.size, a.superResult.elements.size),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(
                                    R.string.analyze_express_done_verdicts,
                                    a.superResult.badgeCounts[Badge.VULNERABILITY] ?: 0,
                                    a.superResult.badgeCounts[Badge.WARNING] ?: 0,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(onClick = onViewReport, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.analyze_view_super))
                            }
                        }
                    }
                }

                AnalysisUi.Idle -> Unit
            }

            if (log.isNotEmpty()) {
                Text(stringResource(R.string.analyze_log), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        log.takeLast(40).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
