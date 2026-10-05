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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import online.devhorizon.scm.BuildConfig
import online.devhorizon.scm.R
import online.devhorizon.scm.ui.AnalysisUi
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
    val analysis by vm.analysis.collectAsState()
    var showClone by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var cloneUrl by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.setLocalRepo(uri, onReady)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showLanguage = true }) {
                        Icon(Icons.Filled.Language, contentDescription = stringResource(R.string.cd_language))
                    }
                    IconButton(onClick = onOpenProviders) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_providers))
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

            Text(
                text = stringResource(R.string.home_get_started),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            ActionCard(
                icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                title = stringResource(R.string.home_open_local_title),
                subtitle = stringResource(R.string.home_open_local_sub),
                enabled = !busy,
                onClick = { folderPicker.launch(null) },
            )
            ActionCard(
                icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                title = stringResource(R.string.home_clone_title),
                subtitle = stringResource(R.string.home_clone_sub),
                enabled = !busy,
                onClick = { showClone = true },
            )
            ActionCard(
                icon = { Icon(Icons.Filled.Hub, contentDescription = null) },
                title = stringResource(R.string.home_sample_title),
                subtitle = stringResource(R.string.home_sample_sub),
                enabled = !busy,
                onClick = { vm.loadSampleRepo(onReady) },
            )

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.home_working), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val failure = analysis as? AnalysisUi.Failed
            if (failure != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.home_error_title), fontWeight = FontWeight.SemiBold)
                        Text(failure.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text(
                text = stringResource(R.string.home_active_provider),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                val active = providers.firstOrNull { it.id == selectedId } ?: providers.first()
                ListItem(
                    headlineContent = { Text(active.label, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${active.baseUrl}\n${active.model}", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Filled.Security, contentDescription = null) },
                    trailingContent = { TextButton(onClick = onOpenProviders) { Text(stringResource(R.string.action_change)) } },
                )
            }

            Text(
                text = stringResource(R.string.home_pipeline),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            PipelineCard()

            Spacer(Modifier.height(8.dp))
            FooterCard()
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClone) {
        AlertDialog(
            onDismissRequest = { showClone = false },
            title = { Text(stringResource(R.string.clone_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.clone_dialog_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = cloneUrl,
                        onValueChange = { cloneUrl = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.clone_field)) },
                        placeholder = { Text(stringResource(R.string.clone_placeholder)) },
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
                }) { Text(stringResource(R.string.clone_action)) }
            },
            dismissButton = { TextButton(onClick = { showClone = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showLanguage) {
        LanguageDialog(current = vm.currentLanguageTag(), onPick = { vm.setAppLanguage(it); showLanguage = false }, onDismiss = { showLanguage = false })
    }
}

@Composable
private fun LanguageDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        null to stringResource(R.string.language_system),
        "en" to "English",
        "ru" to "Русский",
        "de" to "Deutsch",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column {
                options.forEach { (tag, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = (current ?: "") == (tag ?: ""), onClick = { onPick(tag) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = (current ?: "") == (tag ?: ""), onClick = { onPick(tag) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun HeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.home_hero_body),
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
        stringResource(R.string.home_step_ingest_title) to stringResource(R.string.home_step_ingest_body),
        stringResource(R.string.home_step_parse_title) to stringResource(R.string.home_step_parse_body),
        stringResource(R.string.home_step_map_title) to stringResource(R.string.home_step_map_body),
        stringResource(R.string.home_step_assess_title) to stringResource(R.string.home_step_assess_body),
        stringResource(R.string.home_step_report_title) to stringResource(R.string.home_step_report_body),
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
            Text(stringResource(R.string.home_build, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(
                    R.string.home_device,
                    Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
