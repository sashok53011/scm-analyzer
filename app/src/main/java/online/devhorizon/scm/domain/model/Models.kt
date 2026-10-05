package online.devhorizon.scm.domain.model

/** Quality / security verdict shown in column 4 of the report. */
enum class Badge(val label: String, val cssClass: String, val icon: String) {
    PASS("Best practice passed", "ok", "✅"),
    WARNING("Problem / warning", "warn", "⚠️"),
    VULNERABILITY("Vulnerability", "vuln", "🛡️"),
    INFO("Information", "info", "ℹ️");
}

/** One row of the report. Every source line belongs to exactly one CodeElement. */
data class CodeElement(
    val id: String,
    val path: String,
    val language: String,
    val kind: String,
    val name: String,
    val description: String,
    val category: String,
    val startLine: Int,
    val endLine: Int,
    val code: String,
    /** Declaration line(s) used for localized descriptions. */
    val declaration: String = "",
    val badge: Badge = Badge.INFO,
    val assessmentSummary: String = "",
    val assessmentDetail: String = "",
    /** Static-rule key for localization; null for LLM-generated assessments. */
    val assessmentRuleKey: String? = null,
) {
    val lineLabel: String
        get() = if (startLine == endLine) "L$startLine" else "L$startLine–$endLine"
}

data class AnalysisResult(
    val repoName: String,
    val sourceLabel: String,
    val generatedAt: Long,
    val filesFound: Int,
    val filesAnalyzed: Int,
    val filesSkipped: Int,
    val totalLines: Int,
    val mappedLines: Int,
    val codeLines: Int,
    val symbolLines: Int,
    val elements: List<CodeElement>,
    val languageCounts: Map<String, Int>,
    val kindCounts: Map<String, Int>,
    val badgeCounts: Map<Badge, Int>,
    val llmUsed: Boolean,
    val llmProvider: String,
    val llmModel: String,
    val llmAssessed: Int,
    val warnings: List<String>,
    val reportHtmlPath: String? = null,
) {
    /** Fraction of real code lines attached to a report row (contract: 100%). */
    fun lineCoveragePercent(): Double =
        if (codeLines <= 0) 100.0 else 100.0 * mappedLines / codeLines

    /** Fraction of lines that belong to a detected symbol (the "interesting" lines). */
    fun symbolCoveragePercent(): Double =
        if (codeLines <= 0) 100.0 else 100.0 * symbolLines / codeLines
}

data class AnalysisOptions(
    val maxFiles: Int = 2000,
    val maxFileBytes: Long = 1_000_000,
    val maxTotalBytes: Long = 60L * 1024 * 1024,
    val useLlm: Boolean = false,
    val maxLlmElements: Int = 200,
    val includeVendor: Boolean = false,
    /** When false (default) documentation and comment-only ranges are ignored entirely. */
    val includeComments: Boolean = false,
    /** When false (default) markdown/text/doc files are not analyzed at all. */
    val includeDocs: Boolean = false,
    /** Language the LLM should answer in (e.g. "English", "Russian", "German"). */
    val responseLanguage: String = "English",
)
