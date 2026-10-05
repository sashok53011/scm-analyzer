package online.devhorizon.scm.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import online.devhorizon.scm.BuildConfig
import online.devhorizon.scm.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onReady: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    val providers by vm.providers.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    var showClone by remember { mutableStateOf(false) }
    var cloneUrl by remember { mutableStateOf("") }
    val analysis by vm.analysis.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.setLocalRepo(uri, onReady)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SCM Analyzer") },
                actions = {
                    IconButton(onClick = onOpenProviders) {
                        Icon(Icons.Filled.Settings, contentDescription = "Providers")
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
            HeroCard()

            Text("Get started", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            ActionCard(
                icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                title = "Open local repository",
                subtitle = "Pick a folder on the device and analyze it in place.",
                enabled = !busy,
                onClick = { folderPicker.launch(null) },
            )
            ActionCard(
                icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                title = "Clone from GitHub",
                subtitle = "Download a public repository and analyze the working tree.",
                enabled = !busy,
                onClick = { showClone = true },
            )
            ActionCard(
                icon = { Icon(Icons.Filled.Hub, contentDescription = null) },
                title = "Load bundled sample",
                subtitle = "Try the full pipeline instantly on a small multi-language repo.",
                enabled = !busy,
                onClick = { vm.loadSampleRepo(onReady) },
            )

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Working…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val failure = analysis as? online.devhorizon.scm.ui.AnalysisUi.Failed
            if (failure != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Something went wrong", fontWeight = FontWeight.SemiBold)
                        Text(failure.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("Active provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(modifier = Modifier.fillMaxWidth()) {
                val active = providers.firstOrNull { it.id == selectedId } ?: providers.first()
                ListItem(
                    headlineContent = { Text(active.label, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${active.baseUrl}\n${active.model}", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Filled.Security, contentDescription = null) },
                    trailingContent = { TextButton(onClick = onOpenProviders) { Text("Change") } },
                )
            }

            Text("Analysis pipeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PipelineCard()

            Spacer(Modifier.height(8.dp))
            FooterCard()
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClone) {
        AlertDialog(
            onDismissRequest = { showClone = false },
            title = { Text("Clone from GitHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a public repository URL or owner/repo.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = cloneUrl,
                        onValueChange = { cloneUrl = it },
                        singleLine = true,
                        label = { Text("Repository") },
                        placeholder = { Text("owner/repo or https://github.com/owner/repo") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showClone = false
                    vm.cloneRepo(cloneUrl) { onReady() }
                }) { Text("Clone") }
            },
            dismissButton = { TextButton(onClick = { showClone = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Understand any codebase",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Recursive repository analysis with an interactive, collapsible HTML report and multi-LLM security review.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick, enabled = enabled) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle) },
            leadingContent = icon,
            trailingContent = { Icon(Icons.Filled.Hub, contentDescription = null) },
        )
    }
}

@Composable
private fun PipelineCard() {
    val steps = listOf(
        "Ingest" to "Local folder (SAF) or GitHub download, with smart binary/vendor filtering.",
        "Parse" to "Regex/heuristic extractors for Kotlin, Java, JS/TS, Python, C/C++, Go, Rust, C#, PHP, Ruby, Shell and markup.",
        "Map" to "Every source line is assigned to a row; coverage is reported as N/N.",
        "Assess" to "Local static rules plus optional LLM review: warning, vulnerability, passed.",
        "Report" to "Standalone interactive HTML with 4 columns, search, filters and dark mode.",
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            steps.forEach { (title, body) ->
                ListItem(
                    headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(body) },
                    leadingContent = { Icon(Icons.Filled.Hub, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun FooterCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Build ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
            Text(
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
