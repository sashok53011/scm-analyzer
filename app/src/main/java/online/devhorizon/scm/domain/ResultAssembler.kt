package online.devhorizon.scm.domain

import online.devhorizon.scm.domain.model.AnalysisResult
import online.devhorizon.scm.domain.model.CodeElement
import online.devhorizon.scm.domain.parse.IndexResult

/** Builds an [AnalysisResult] from an index plus already-assessed elements. */
object ResultAssembler {

    fun assemble(
        index: IndexResult,
        elements: List<CodeElement>,
        llmUsed: Boolean,
        providerLabel: String,
        model: String,
        llmAssessed: Int,
    ): AnalysisResult = AnalysisResult(
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
        elements = elements,
        languageCounts = index.languageCounts,
        kindCounts = index.kindCounts,
        badgeCounts = elements.groupingBy { it.badge }.eachCount(),
        llmUsed = llmUsed,
        llmProvider = providerLabel,
        llmModel = model,
        llmAssessed = llmAssessed,
        warnings = index.warnings,
    )
}
