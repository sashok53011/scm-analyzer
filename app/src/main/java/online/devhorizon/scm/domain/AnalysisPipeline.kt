package online.devhorizon.scm.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.devhorizon.scm.data.llm.OpenAiCompatClient
import online.devhorizon.scm.data.llm.ProviderConfig
import online.devhorizon.scm.domain.assess.Assessment
import online.devhorizon.scm.domain.assess.AssessmentCache
import online.devhorizon.scm.domain.assess.LlmAssessor
import online.devhorizon.scm.domain.assess.StaticRules
import online.devhorizon.scm.domain.ingest.RepoSource
import online.devhorizon.scm.domain.model.AnalysisOptions
import online.devhorizon.scm.domain.model.AnalysisResult
import online.devhorizon.scm.domain.parse.CodeIndexer
import java.io.File

/** Runs the full pipeline: index -> static rules -> optional LLM review -> aggregate. */
class AnalysisPipeline(
    private val client: OpenAiCompatClient = OpenAiCompatClient(),
) {

    suspend fun run(
        source: RepoSource,
        options: AnalysisOptions,
        provider: ProviderConfig?,
        cacheFile: File?,
        progress: Progress = Progress.noop(),
    ): AnalysisResult = withContext(Dispatchers.Default) {
        val index = CodeIndexer(progress).index(source, options)
        if (progress.isCancelled()) throw CancellationException_()

        progress.message("Applying static quality/security rules…")
        val staticMap = HashMap<String, Assessment>(index.elements.size)
        for (element in index.elements) {
            staticMap[element.id] = StaticRules.assess(element)
        }

        val llmMap = HashMap<String, Assessment>()
        var llmAssessed = 0
        if (options.useLlm && provider != null && provider.isReady) {
            val cache = cacheFile?.let { AssessmentCache(it) }
            val assessor = LlmAssessor(client, cache)
            val result = assessor.assess(
                index.elements, provider, options.maxLlmElements, progress,
                responseLanguage = options.responseLanguage,
            )
            llmMap.putAll(result)
            llmAssessed = result.size
        }

        val finalElements = index.elements.map { element ->
            val a = llmMap[element.id] ?: staticMap.getValue(element.id)
            element.copy(
                badge = a.badge,
                assessmentSummary = a.summary,
                assessmentDetail = a.detail,
                assessmentRuleKey = a.ruleKey,
            )
        }

        val badgeCounts = finalElements.groupingBy { it.badge }.eachCount()

        AnalysisResult(
            repoName = index.repoName,
            sourceLabel = index.sourceLabel,
            generatedAt = System.currentTimeMillis(),
            filesFound = index.filesFound,
            filesAnalyzed = index.filesAnalyzed,
            filesSkipped = index.filesSkipped,
            totalLines = index.totalLines,
            mappedLines = index.codeLines,
            codeLines = index.codeLines,
            symbolLines = index.symbolLines,
            elements = finalElements,
            languageCounts = index.languageCounts,
            kindCounts = index.kindCounts,
            badgeCounts = badgeCounts,
            llmUsed = llmAssessed > 0,
            llmProvider = provider?.label ?: "",
            llmModel = provider?.model ?: "",
            llmAssessed = llmAssessed,
            warnings = index.warnings,
        )
    }
}

/** Local marker so the domain layer stays free of coroutine imports in signatures. */
class CancellationException_(message: String = "Analysis cancelled") : RuntimeException(message)
