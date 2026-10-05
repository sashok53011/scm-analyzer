package online.devhorizon.scm.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.devhorizon.scm.data.llm.ProviderConfig
import online.devhorizon.scm.data.llm.ProviderStore
import online.devhorizon.scm.data.llm.Providers
import online.devhorizon.scm.domain.AnalysisPipeline
import online.devhorizon.scm.domain.Progress
import online.devhorizon.scm.domain.express.ExpressPipeline
import online.devhorizon.scm.domain.ingest.CachedRepoSource
import online.devhorizon.scm.domain.ingest.FileRepoSource
import online.devhorizon.scm.domain.ingest.GitHubRepo
import online.devhorizon.scm.domain.ingest.RepoSource
import online.devhorizon.scm.domain.ingest.SafFolderSource
import online.devhorizon.scm.domain.model.AnalysisOptions
import online.devhorizon.scm.domain.model.AnalysisResult
import online.devhorizon.scm.report.HtmlReportBuilder
import online.devhorizon.scm.report.ReportStorage
import java.io.File

data class PendingRepo(
    val source: RepoSource,
    val kind: String,
    val fileCount: Int,
    val totalBytes: Long,
)

sealed interface AnalysisUi {
    data object Idle : AnalysisUi
    data class Running(val message: String, val done: Int, val total: Int) : AnalysisUi
    data class Done(val result: AnalysisResult, val reportFile: File) : AnalysisUi
    data class ExpressDone(
        val superResult: AnalysisResult,
        val superFile: File,
        val reports: List<online.devhorizon.scm.domain.express.ModelReport>,
    ) : AnalysisUi
    data class Failed(val error: String) : AnalysisUi
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ProviderStore(app)
    private val pipeline = AnalysisPipeline()
    private val reportBuilder = HtmlReportBuilder(app)
    private val reportStorage = ReportStorage(app)

    private val _providers = MutableStateFlow(store.loadProviders())
    val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()

    private val _selectedId = MutableStateFlow(store.loadSelectedId())
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val _pending = MutableStateFlow<PendingRepo?>(null)
    val pending: StateFlow<PendingRepo?> = _pending.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisUi>(AnalysisUi.Idle)
    val analysis: StateFlow<AnalysisUi> = _analysis.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    @Volatile
    private var cancelRequested = false

    val selectedProvider: ProviderConfig
        get() = Providers.find(_providers.value, _selectedId.value)

    // ---- Providers ----

    fun selectProvider(id: String) {
        _selectedId.value = id
        store.saveSelectedId(id)
    }

    fun saveProvider(config: ProviderConfig) {
        val existing = _providers.value.any { it.id == config.id }
        val list = if (existing) {
            _providers.value.map { if (it.id == config.id) config else it }
        } else {
            _providers.value + config
        }
        _providers.value = list
        store.saveProviders(list)
    }

    fun addProvider(onReady: (ProviderConfig) -> Unit) {
        val cfg = ProviderConfig(
            id = Providers.newCustomId(),
            label = "Custom provider",
            baseUrl = "https://",
            model = "",
            apiKey = "",
            requiresKey = true,
            notes = "OpenAI-compatible endpoint.",
        )
        _providers.value = _providers.value + cfg
        store.saveProviders(_providers.value)
        onReady(cfg)
    }

    fun removeProvider(id: String) {
        if (Providers.isBuiltIn(id)) return
        val list = _providers.value.filterNot { it.id == id }
        _providers.value = list
        store.saveProviders(list)
        if (_selectedId.value == id) selectProvider(list.first().id)
    }

    fun duplicateProvider(id: String, onReady: (ProviderConfig) -> Unit) {
        val src = _providers.value.firstOrNull { it.id == id } ?: return
        val copy = src.copy(id = Providers.newCustomId(), label = "${src.label} (copy)")
        _providers.value = _providers.value + copy
        store.saveProviders(_providers.value)
        onReady(copy)
    }

    /** Providers that are actually usable right now (URL + model + key when required). */
    fun readyProviders(): List<ProviderConfig> = _providers.value.filter { it.isReady }

    fun resetProviders() {
        _providers.value = Providers.defaults
        store.saveProviders(Providers.defaults)
        selectProvider(Providers.DEVHORIZON)
    }

    // ---- Repository input ----

    fun setLocalRepo(uri: Uri, onReady: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _analysis.value = AnalysisUi.Idle
            runCatching {
                withContext(Dispatchers.IO) {
                    val source = SafFolderSource(getApplication(), uri)
                    val entries = source.entries()
                    val bytes = entries.sumOf { if (it.size > 0) it.size else 0L }
                    PendingRepo(
                        source = CachedRepoSource(source.displayName, "local folder", entries),
                        kind = "local",
                        fileCount = entries.size,
                        totalBytes = bytes,
                    )
                }
            }.onSuccess { _pending.value = it; onReady() }
                .onFailure { _analysis.value = AnalysisUi.Failed("Cannot open folder: ${it.message}") }
            _busy.value = false
        }
    }

    fun cloneRepo(rawUrl: String, onReady: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _log.value = emptyList()
            _analysis.value = AnalysisUi.Idle
            runCatching {
                withContext(Dispatchers.IO) {
                    val ref = GitHubRepo.parse(rawUrl)
                    val dest = File(getApplication<Application>().filesDir, "repos/${ref.owner}-${ref.repo}")
                    log("Cloning ${ref.owner}/${ref.repo}…")
                    val root = GitHubRepo.downloadAndExtract(ref, dest) { msg -> log(msg) }
                    val source = FileRepoSource(root, sourceLabel = "github:${ref.owner}/${ref.repo}")
                    val entries = source.entries()
                    val bytes = entries.sumOf { if (it.size > 0) it.size else 0L }
                    PendingRepo(
                        source = CachedRepoSource(source.displayName, source.sourceLabel, entries),
                        kind = "github",
                        fileCount = entries.size,
                        totalBytes = bytes,
                    )
                }
            }.onSuccess { _pending.value = it; onReady() }
                .onFailure { _analysis.value = AnalysisUi.Failed("Clone failed: ${it.message}") }
            _busy.value = false
        }
    }

    fun loadSampleRepo(onReady: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _analysis.value = AnalysisUi.Idle
            runCatching {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val dest = File(app.filesDir, "sample-repo")
                    dest.deleteRecursively()
                    dest.mkdirs()
                    copyAssetDir("sample_repo", dest)
                    val source = FileRepoSource(dest, sourceLabel = "bundled sample")
                    val entries = source.entries()
                    val bytes = entries.sumOf { if (it.size > 0) it.size else 0L }
                    PendingRepo(
                        source = CachedRepoSource("sample-repo", "bundled sample", entries),
                        kind = "sample",
                        fileCount = entries.size,
                        totalBytes = bytes,
                    )
                }
            }.onSuccess { _pending.value = it; onReady() }
                .onFailure { _analysis.value = AnalysisUi.Failed("Sample load failed: ${it.message}") }
            _busy.value = false
        }
    }

    private fun copyAssetDir(assetPath: String, dest: File) {
        val assets = getApplication<Application>().assets
        val children = assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(assetPath).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return
        }
        dest.mkdirs()
        for (child in children) copyAssetDir("$assetPath/$child", File(dest, child))
    }

    fun clearPending() {
        _pending.value = null
        _analysis.value = AnalysisUi.Idle
    }

    // ---- Analysis ----

    fun startAnalysis(useLlm: Boolean, maxFiles: Int, maxLlmElements: Int) {
        val pending = _pending.value ?: return
        cancelRequested = false
        _log.value = emptyList()
        _analysis.value = AnalysisUi.Running("Starting…", 0, 1)
        val progress = makeProgress()

        viewModelScope.launch {
            try {
                val options = AnalysisOptions(
                    maxFiles = maxFiles,
                    useLlm = useLlm,
                    maxLlmElements = maxLlmElements,
                )
                val provider = if (useLlm) selectedProvider else null
                if (useLlm && (provider == null || !provider.isReady)) {
                    _analysis.value = AnalysisUi.Failed("Provider '${selectedProvider.label}' is not configured (missing key or URL).")
                    return@launch
                }
                val cacheFile = File(getApplication<Application>().filesDir, "assessment-cache.json")
                val result = pipeline.run(pending.source, options, provider, cacheFile, progress)
                val html = reportBuilder.build(result)
                val file = reportStorage.save(result, html)
                _analysis.value = AnalysisUi.Done(result.copy(reportHtmlPath = file.absolutePath), file)
            } catch (t: Throwable) {
                _analysis.value = AnalysisUi.Failed(t.message ?: t.toString())
            }
        }
    }

    /** Express mode: run every usable provider in turn, then compare into a super-report. */
    fun startExpress(maxFiles: Int, maxLlmElements: Int) {
        val pending = _pending.value ?: return
        val ready = readyProviders()
        if (ready.isEmpty()) {
            _analysis.value = AnalysisUi.Failed("No usable providers. Configure at least one on the Providers screen.")
            return
        }
        cancelRequested = false
        _log.value = emptyList()
        _analysis.value = AnalysisUi.Running("Express: ${ready.size} model(s) queued", 0, ready.size)
        val progress = makeProgress()

        viewModelScope.launch {
            try {
                val options = AnalysisOptions(maxFiles = maxFiles, useLlm = true, maxLlmElements = maxLlmElements)
                val cacheFile = File(getApplication<Application>().filesDir, "assessment-cache.json")
                val outcome = ExpressPipeline(getApplication())
                    .run(pending.source, options, ready, cacheFile, progress)
                _analysis.value = AnalysisUi.ExpressDone(outcome.superResult, outcome.superFile, outcome.modelReports)
            } catch (t: Throwable) {
                _analysis.value = AnalysisUi.Failed(t.message ?: t.toString())
            }
        }
    }

    private fun makeProgress(): Progress {
        var last = ""
        return object : Progress {
            override fun message(msg: String) { last = msg; log(msg) }
            override fun progress(done: Int, total: Int) {
                if (last.isBlank()) last = "Running…"
                _analysis.value = AnalysisUi.Running(last, done, total)
            }
            override fun isCancelled(): Boolean = cancelRequested
        }
    }

    fun cancelAnalysis() {
        cancelRequested = true
        log("Cancellation requested…")
    }

    fun resetAnalysis() {
        _analysis.value = AnalysisUi.Idle
    }

    private fun log(message: String) {
        _log.value = (_log.value + message).takeLast(400)
    }
}
