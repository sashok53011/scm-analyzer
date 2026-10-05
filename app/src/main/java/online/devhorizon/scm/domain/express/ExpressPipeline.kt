package online.devhorizon.scm.domain.express

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.devhorizon.scm.data.llm.OpenAiCompatClient
import online.devhorizon.scm.data.llm.ProviderConfig
import online.devhorizon.scm.domain.Progress
import online.devhorizon.scm.domain.ResultAssembler
import online.devhorizon.scm.domain.assess.Assessment
import online.devhorizon.scm.domain.assess.AssessmentCache
import online.devhorizon.scm.domain.assess.LlmAssessor
import online.devhorizon.scm.domain.assess.StaticRules
import online.devhorizon.scm.domain.ingest.RepoSource
import online.devhorizon.scm.domain.model.AnalysisOptions
import online.devhorizon.scm.domain.parse.CodeIndexer
import online.devhorizon.scm.report.HtmlReportBuilder
import online.devhorizon.scm.report.ReportStorage
import online.devhorizon.scm.report.SuperReportBuilder
import java.io.File

/**
 * Runs the same indexed code through every configured model in turn, produces one report per
 * model, then compares all verdicts into a consensus "super-report".
 */
class ExpressPipeline(
    context: Context,
    private val client: OpenAiCompatClient = OpenAiCompatClient(),
) {

    private val appContext = context.applicationContext
    private val htmlBuilder = HtmlReportBuilder(appContext)
    private val superBuilder = SuperReportBuilder(appContext)
    private val storage = ReportStorage(appContext)

    suspend fun run(
        source: RepoSource,
        options: AnalysisOptions,
        providers: List<ProviderConfig>,
        cacheFile: File?,
        progress: Progress = Progress.noop(),
    ): ExpressOutcome = withContext(Dispatchers.Default) {
        require(providers.isNotEmpty()) { "No usable provider configured" }

        progress.message("Express: indexing once, then reviewing with ${providers.size} model(s)…")
        val index = CodeIndexer(progress).index(source, options)

        val staticMap = HashMap<String, Assessment>(index.elements.size)
        for (e in index.elements) staticMap[e.id] = StaticRules.assess(e)

        val verdicts = LinkedHashMap<String, Map<String, Assessment>>()
        val modelReports = ArrayList<ModelReport>()

        for (provider in providers) {
            if (progress.isCancelled()) break
            progress.message("Express → ${provider.label} (${provider.model})")
            val cache = cacheFile?.let { AssessmentCache(it) }
            val llm = LlmAssessor(client, cache).assess(
                index.elements, provider, options.maxLlmElements, progress,
                responseLanguage = options.responseLanguage,
            )
            if (llm.isEmpty() && progress.isCancelled()) break
            verdicts[provider.id] = llm

            val elements = index.elements.map { e ->
                val a = llm[e.id] ?: staticMap.getValue(e.id)
                e.copy(
                    badge = a.badge,
                    assessmentSummary = a.summary,
                    assessmentDetail = a.detail,
                    assessmentRuleKey = a.ruleKey,
                )
            }
            val result = ResultAssembler.assemble(index, elements, true, provider.label, provider.model, llm.size)
            val file = storage.save(result, htmlBuilder.build(result))
            modelReports.add(ModelReport(provider, result.copy(reportHtmlPath = file.absolutePath), file))
        }

        val usedIds = providers.map { it.id }.filter { verdicts.containsKey(it) }
        val usedProviders = providers.filter { verdicts.containsKey(it.id) }

        val superElements = index.elements.map { e ->
            val c = Consensus.compute(usedIds, e.id, verdicts, staticMap.getValue(e.id))
            val detail = usedProviders.joinToString("\n") { p ->
                val a = verdicts[p.id]?.get(e.id)
                if (a != null) "${p.label}: ${a.summary} — ${a.detail}" else "${p.label}: not assessed"
            }
            e.copy(badge = c.badge, assessmentSummary = c.note, assessmentDetail = detail)
        }

        val superResult = ResultAssembler.assemble(
            index = index,
            elements = superElements,
            llmUsed = true,
            providerLabel = "Ensemble (${usedProviders.size} models)",
            model = usedProviders.joinToString(" + ") { it.model },
            llmAssessed = superElements.size,
        )
        val superHtml = superBuilder.build(index, usedProviders, verdicts, staticMap, superResult)
        val superFile = storage.save(superResult, superHtml)

        progress.message("Express done: ${modelReports.size} model report(s) + super-report")

        ExpressOutcome(
            superResult = superResult.copy(reportHtmlPath = superFile.absolutePath),
            superFile = superFile,
            modelReports = modelReports,
            providersUsed = usedProviders,
            superHtml = superHtml,
        )
    }
}
