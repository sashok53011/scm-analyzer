package online.devhorizon.scm.domain.parse

import online.devhorizon.scm.domain.Progress
import online.devhorizon.scm.domain.ingest.LanguageDetector
import online.devhorizon.scm.domain.ingest.RepoFilter
import online.devhorizon.scm.domain.ingest.RepoSource
import online.devhorizon.scm.domain.model.AnalysisOptions
import online.devhorizon.scm.domain.model.CodeElement

data class IndexResult(
    val repoName: String,
    val sourceLabel: String,
    val filesFound: Int,
    val filesAnalyzed: Int,
    val filesSkipped: Int,
    val totalLines: Int,
    val codeLines: Int,
    val symbolLines: Int,
    val elements: List<CodeElement>,
    val languageCounts: Map<String, Int>,
    val kindCounts: Map<String, Int>,
    val warnings: List<String>,
)

/**
 * Recursively walks a [RepoSource], extracts symbols and then guarantees that every source line
 * belongs to exactly one report row by bucketing the lines not covered by a detected symbol.
 */
class CodeIndexer(
    private val progress: Progress = Progress.noop(),
) {

    private val analyzers: Map<String, Analyzer> = buildMap {
        put("python", PythonAnalyzer())
        put("markup", MarkupAnalyzer())
        put("brace", BraceAnalyzer())
    }

    fun index(source: RepoSource, options: AnalysisOptions): IndexResult {
        val allEntries = source.entries()
        val filesFound = allEntries.size
        val warnings = ArrayList<String>()
        val elements = ArrayList<CodeElement>()
        val languageCounts = HashMap<String, Int>()
        val kindCounts = HashMap<String, Int>()

        var filesAnalyzed = 0
        var filesSkipped = 0
        var totalLines = 0
        var codeLines = 0
        var symbolLines = 0
        var totalBytes = 0L

        var idCounter = 0

        progress.message("Scanning ${source.displayName}: $filesFound files")

        var processed = 0
        for (entry in allEntries) {
            if (progress.isCancelled()) break
            if (filesAnalyzed >= options.maxFiles) {
                warnings.add("File limit (${options.maxFiles}) reached; remaining files skipped.")
                break
            }
            processed++
            progress.progress(processed, filesFound)

            if (RepoFilter.shouldSkipFile(entry.path, entry.size, options.maxFileBytes, options.includeVendor)) {
                filesSkipped++
                continue
            }
            if (totalBytes + entry.size > options.maxTotalBytes) {
                warnings.add("Total size limit reached (${options.maxTotalBytes / (1024 * 1024)} MB); stopping.")
                break
            }
            val bytes = entry.read() ?: run { filesSkipped++; continue }
            if (RepoFilter.isBinaryHeuristic(bytes)) { filesSkipped++; continue }
            totalBytes += bytes.size

            val text = decode(bytes)
            val lines = text.split('\n').map { it.removeSuffix("\r") }
            if (lines.isEmpty()) { filesSkipped++; continue }

            val language = LanguageDetector.detect(entry.path, lines.firstOrNull()) ?: "Text"
            val analyzer = analyzerFor(language)

            val symbols = runCatching { analyzer?.analyze(entry.path, lines) ?: emptyList() }
                .getOrElse { e ->
                    warnings.add("Parse error in ${entry.path}: ${e.message}")
                    emptyList()
                }

            filesAnalyzed++
            languageCounts[language] = (languageCounts[language] ?: 0) + 1
            totalLines += lines.size

            // "Real code" = not blank and not a pure comment line.
            val realCode = BooleanArray(lines.size + 1)
            for (idx in 1..lines.size) {
                val raw = lines[idx - 1]
                realCode[idx] = raw.isNotBlank() && !RepoFilter.isCommentLine(raw)
            }
            val fileCodeLines = (1..lines.size).count { realCode[it] }
            codeLines += fileCodeLines

            val covered = BooleanArray(lines.size + 1)

            // File-level architectural row (only for files that contain real code).
            if (fileCodeLines > 0) {
                elements.add(
                    CodeElement(
                        id = elementId(entry.path, 0, "file", idCounter++),
                        path = entry.path,
                        language = language,
                        kind = SymbolKinds.FILE,
                        name = entry.path.substringAfterLast('/'),
                        description = CategoryResolver.describe(SymbolKinds.FILE, entry.path, "", entry.path),
                        category = CategoryResolver.category(entry.path, language, SymbolKinds.FILE, entry.path),
                        startLine = 1,
                        endLine = lines.size,
                        code = previewLines(lines),
                    )
                )
                kindCounts[SymbolKinds.FILE] = (kindCounts[SymbolKinds.FILE] ?: 0) + 1
            }

            // Symbol rows.
            for (symbol in symbols) {
                val start = symbol.startLine.coerceIn(1, lines.size)
                val end = symbol.endLine.coerceIn(start, lines.size)
                for (l in start..end) covered[l] = true
                val code = lines.subList(start - 1, end).joinToString("\n")
                elements.add(
                    CodeElement(
                        id = elementId(entry.path, start, symbol.kind, idCounter++),
                        path = entry.path,
                        language = language,
                        kind = symbol.kind,
                        name = symbol.name,
                        description = CategoryResolver.describe(symbol.kind, symbol.name, symbol.declaration, entry.path),
                        category = CategoryResolver.category(entry.path, language, symbol.kind, symbol.name),
                        startLine = start,
                        endLine = end,
                        code = code,
                    )
                )
                kindCounts[symbol.kind] = (kindCounts[symbol.kind] ?: 0) + 1
            }

            // Real code inside symbols (union, so overlapping symbols are not double counted).
            symbolLines += (1..lines.size).count { realCode[it] && covered[it] }

            // Bucket rows: real code with no detected symbol gets its own row.
            // Pure comment / blank ranges are ignored unless includeComments is on.
            var i = 1
            while (i <= lines.size) {
                if (covered[i]) { i++; continue }
                var j = i
                while (j <= lines.size && !covered[j]) j++
                val start = i
                val end = j - 1
                val hasCode = (start..end).any { realCode[it] }
                if (hasCode || options.includeComments) {
                    val kind = classifyRun(lines, start, end, realCode)
                    val code = lines.subList(start - 1, end).joinToString("\n")
                    val name = when (kind) {
                        SymbolKinds.BLANK -> "blank lines"
                        SymbolKinds.IMPORT -> "imports"
                        SymbolKinds.COMMENT -> "comment block"
                        else -> "code block"
                    }
                    elements.add(
                        CodeElement(
                            id = elementId(entry.path, start, kind, idCounter++),
                            path = entry.path,
                            language = language,
                            kind = kind,
                            name = name,
                            description = CategoryResolver.describe(kind, name, lines[start - 1].trim(), entry.path),
                            category = CategoryResolver.category(entry.path, language, kind, name),
                            startLine = start,
                            endLine = end,
                            code = code,
                        )
                    )
                    kindCounts[kind] = (kindCounts[kind] ?: 0) + 1
                }
                i = j
            }
        }

        progress.progress(processed, filesFound)
        progress.message("Indexed $filesAnalyzed files, ${elements.size} elements")

        return IndexResult(
            repoName = source.displayName,
            sourceLabel = source.sourceLabel,
            filesFound = filesFound,
            filesAnalyzed = filesAnalyzed,
            filesSkipped = filesSkipped,
            totalLines = totalLines,
            codeLines = codeLines,
            symbolLines = symbolLines,
            elements = elements,
            languageCounts = languageCounts,
            kindCounts = kindCounts,
            warnings = warnings,
        )
    }

    private fun analyzerFor(language: String): Analyzer? = when {
        LanguageDetector.braceLanguages.contains(language) -> analyzers["brace"]
        language == "Python" -> analyzers["python"]
        LanguageDetector.markupLanguages.contains(language) -> analyzers["markup"]
        else -> null
    }

    private fun classifyRun(lines: List<String>, start: Int, end: Int, realCode: BooleanArray): String {
        val codeIdx = (start..end).filter { realCode[it] }
        if (codeIdx.isEmpty()) {
            val allBlank = (start..end).all { lines[it - 1].isBlank() }
            return if (allBlank) SymbolKinds.BLANK else SymbolKinds.COMMENT
        }
        val imports = codeIdx.count { isImport(lines[it - 1].trim()) }
        return if (imports * 10 >= codeIdx.size * 8) SymbolKinds.IMPORT else SymbolKinds.CODE_BLOCK
    }

    private fun isImport(t: String): Boolean =
        t.startsWith("import ") || t.startsWith("from ") || t.startsWith("#include") ||
            t.startsWith("use ") || t.startsWith("require(") || t.startsWith("using ") ||
            t.startsWith("package ") || t.startsWith("export ") || t.startsWith("@import")

    private fun previewLines(lines: List<String>, max: Int = 40): String {
        if (lines.size <= max) return lines.joinToString("\n")
        return lines.take(max).joinToString("\n") + "\n… (${lines.size - max} more lines)"
    }

    private fun elementId(path: String, line: Int, kind: String, counter: Int): String =
        "$path#$line#$kind#$counter"

    private fun decode(bytes: ByteArray): String {
        var offset = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) offset = 3
        return String(bytes, offset, bytes.size - offset, Charsets.UTF_8)
    }
}
