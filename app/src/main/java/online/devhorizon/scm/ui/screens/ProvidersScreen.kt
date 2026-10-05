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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import online.devhorizon.scm.data.llm.OpenAiCompatClient
import online.devhorizon.scm.data.llm.ProviderConfig
import online.devhorizon.scm.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(vm: MainViewModel, onBack: () -> Unit) {
    val providers by vm.providers.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LLM providers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.addProvider { editing = it } }) { Text("Add") }
                    TextButton(onClick = { vm.resetProviders() }) { Text("Reset") }
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Select the provider used for LLM review. API keys are stored encrypted on device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            providers.forEach { provider ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = provider.id == selectedId, onClick = { vm.selectProvider(provider.id) })
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = provider.id == selectedId,
                            onClick = { vm.selectProvider(provider.id) },
                        )
                        ListItem(
                            modifier = Modifier.weight(1f),
                            headlineContent = { Text(provider.label, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Column {
                                    Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall)
                                    Text(provider.model, style = MaterialTheme.typography.bodySmall)
                                    if (provider.requiresKey) {
                                        Text(
                                            if (provider.apiKey.isBlank()) "API key: missing" else "API key: set",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (provider.apiKey.isBlank()) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                        )
                                    } else {
                                        Text("No API key required", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            },
                        )
                        IconButton(onClick = { editing = provider }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { vm.duplicateProvider(provider.id) { editing = it } }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                        }
                        if (!online.devhorizon.scm.data.llm.Providers.isBuiltIn(provider.id)) {
                            IconButton(onClick = { vm.removeProvider(provider.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { current ->
        ProviderEditDialog(
            initial = current,
            onDismiss = { editing = null },
            onSave = { vm.saveProvider(it); editing = null },
        )
    }
}

@Composable
private fun ProviderEditDialog(
    initial: ProviderConfig,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
) {
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var requiresKey by remember { mutableStateOf(initial.requiresKey) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    singleLine = true,
                    label = { Text("Base URL") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    singleLine = true,
                    label = { Text("Model") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    singleLine = true,
                    label = { Text("API key") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Requires API key", modifier = Modifier.weight(1f))
                    Switch(checked = requiresKey, onCheckedChange = { requiresKey = it })
                }
                if (initial.notes.isNotBlank()) {
                    Text(initial.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        val cfg = initial.copy(baseUrl = baseUrl, model = model, apiKey = apiKey, requiresKey = requiresKey)
                        scope.launch {
                            testResult = runCatching {
                                val models = OpenAiCompatClient().listModels(cfg)
                                "OK · ${models.size} models available"
                            }.getOrElse { "Failed: ${it.message}" }
                            testing = false
                        }
                    },
                ) { Text(if (testing) "Testing…" else "Test connection") }
                testResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(baseUrl = baseUrl.trim(), model = model.trim(), apiKey = apiKey.trim(), requiresKey = requiresKey))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
