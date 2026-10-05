package online.devhorizon.scm.report

import android.content.Context
import online.devhorizon.scm.domain.model.AnalysisResult
import online.devhorizon.scm.domain.model.Badge
import online.devhorizon.scm.domain.model.CodeElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the standalone, self-contained interactive HTML report from an [AnalysisResult]. */
class HtmlReportBuilder(private val context: Context) {

    fun build(result: AnalysisResult): String {
        val template = context.assets.open("report_template.html").bufferedReader().use { it.readText() }
        val rows = StringBuilder()
        for (element in result.elements) {
            rows.append(row(element))
        }

        return template
            .replace("{{TITLE}}", esc("${result.repoName} — SCM report"))
            .replace("{{REPO}}", esc(result.repoName))
            .replace("{{SOURCE}}", esc(result.sourceLabel))
            .replace("{{GENERATED}}", esc(formatted(result.generatedAt)))
            .replace("{{LLM}}", esc(llmLabel(result)))
            .replace("{{STATS}}", stats(result))
            .replace("{{COVERAGE}}", coverage(result))
            .replace("{{ROWS}}", rows.toString())
            .replace("{{FILES}}", esc("${result.filesAnalyzed}/${result.filesFound} files analyzed"))
            .replace("{{WARNINGS}}", esc(if (result.warnings.isEmpty()) "no warnings" else "${result.warnings.size} warnings"))
    }

    private fun llmLabel(result: AnalysisResult): String =
        if (result.llmUsed) "LLM: ${result.llmProvider} / ${result.llmModel} (${result.llmAssessed} reviewed)"
        else "LLM review: off (static rules only)"

    private fun stats(result: AnalysisResult): String {
        val chips = buildList {
            add("Files" to "${result.filesAnalyzed}/${result.filesFound}")
            add("Languages" to result.languageCounts.size.toString())
            add("Elements" to result.elements.size.toString())
            add("Code lines" to result.codeLines.toString())
            add("Vulnerabilities" to (result.badgeCounts[Badge.VULNERABILITY] ?: 0).toString())
            add("Warnings" to (result.badgeCounts[Badge.WARNING] ?: 0).toString())
            add("Passed" to (result.badgeCounts[Badge.PASS] ?: 0).toString())
        }
        return chips.joinToString("") { "<span class=\"chip\">${esc(it.first)} <b>${esc(it.second)}</b></span>" }
    }

    private fun coverage(result: AnalysisResult): String {
        val line = result.lineCoveragePercent()
        val symbol = result.symbolCoveragePercent()
        return bar("Real code coverage (executable lines mapped to rows)", line, "${result.mappedLines}/${result.codeLines}") +
            bar("Symbol coverage (lines inside detected symbols)", symbol, "${result.symbolLines}/${result.codeLines}")
    }

    private fun bar(label: String, percent: Double, detail: String): String {
        val p = percent.coerceIn(0.0, 100.0)
        return "<div class=\"bar\"><div class=\"lbl\"><span>${esc(label)}</span>" +
            "<span>${esc(detail)} · ${"%.1f".format(Locale.US, p)}%</span></div>" +
            "<div class=\"track\"><div class=\"fill\" style=\"width:${"%.1f".format(Locale.US, p)}%\"></div></div></div>"
    }

    private fun row(e: CodeElement): String {
        val search = buildString {
            append(e.name).append(' ').append(e.path).append(' ').append(e.kind).append(' ')
            append(e.category).append(' ').append(e.assessmentSummary).append(' ')
            append(e.code.take(400))
        }.lowercase(Locale.US)

        return buildString {
            append("<details class=\"row\" data-kind=\"").append(esc(e.kind))
            append("\" data-badge=\"").append(e.badge.name)
            append("\" data-search=\"").append(esc(search)).append("\">")
            append("<summary class=\"r-summary\">")
            append("<div class=\"s-top\"><span class=\"kind\">").append(esc(e.kind)).append("</span>")
            append("<span class=\"name\">").append(esc(e.name)).append("</span>")
            append("<span class=\"badge ").append(e.badge.cssClass).append("\">")
            append(e.badge.icon).append(' ').append(esc(e.badge.label)).append("</span></div>")
            append("<div class=\"path\">").append(esc(e.path)).append(" · ").append(esc(e.lineLabel)).append("</div>")
            append("<div class=\"s-sum\">").append(esc(e.assessmentSummary)).append("</div>")
            append("<div class=\"s-cat\">").append(esc(e.category)).append("</div>")
            append("</summary>")
            append("<div class=\"r-body\">")
            append(cell("Name / Description", "<p class=\"desc clampable\">${esc(e.description)}</p>"))
            append(cell("Category / Technology / Type", "<p class=\"clampable\">${esc(e.category)}</p>"))
            append(
                cell(
                    "Exact code & line reference",
                    "<div class=\"lineref\">${esc(e.lineLabel)}</div>" +
                        "<pre class=\"code clampable\">${esc(e.code)}</pre>",
                )
            )
            append(
                cell(
                    "Quality & security",
                    "<div class=\"asummary clampable\">${esc(e.assessmentSummary.ifBlank { e.badge.label })}</div>" +
                        "<div class=\"adetail clampable\">${esc(e.assessmentDetail)}</div>",
                )
            )
            append("</div></details>")
        }
    }

    private fun cell(title: String, body: String): String =
        "<div class=\"col\"><div class=\"col-title\">${esc(title)}</div>$body</div>"

    private fun formatted(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))

    private fun esc(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (c in text) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
