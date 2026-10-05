package online.devhorizon.scm.report

import android.content.Context
import online.devhorizon.scm.R
import online.devhorizon.scm.domain.model.AnalysisResult
import online.devhorizon.scm.domain.model.Badge
import online.devhorizon.scm.domain.model.CodeElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the standalone, self-contained interactive HTML report from an [AnalysisResult]. */
class HtmlReportBuilder(private val context: Context) {

    private val loc = Localizer(context)

    private fun s(id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

    fun build(result: AnalysisResult): String {
        val template = context.assets.open("report_template.html").bufferedReader().use { it.readText() }
        val rows = StringBuilder()
        for (element in result.elements) rows.append(row(element))

        return template
            .replace("{{TITLE}}", esc("${result.repoName} — ${s(R.string.report_title)}"))
            .replace("{{REPO}}", esc(result.repoName))
            .replace("{{SOURCE}}", esc(result.sourceLabel))
            .replace("{{GENERATED}}", esc(formatted(result.generatedAt)))
            .replace("{{LLM}}", esc(llmLabel(result)))
            .replace("{{STATS}}", stats(result))
            .replace("{{COVERAGE}}", coverage(result))
            .replace("{{ROWS}}", rows.toString())
            .replace("{{FILES}}", esc(s(R.string.rep_footer_files, result.filesAnalyzed, result.filesFound)))
            .replace("{{WARNINGS}}", esc(warningsLabel(result)))
            .replace("{{KIND_LABELS}}", kindLabels(result))
            .replace("{{t.subtitle}}", esc(s(R.string.t_report_subtitle)))
            .replace("{{t.search}}", esc(s(R.string.t_search)))
            .replace("{{t.allKinds}}", esc(s(R.string.t_all_kinds)))
            .replace("{{t.allVerdicts}}", esc(s(R.string.t_all_verdicts)))
            .replace("{{t.badgeVuln}}", esc(loc.badgeLabel(Badge.VULNERABILITY)))
            .replace("{{t.badgeWarn}}", esc(loc.badgeLabel(Badge.WARNING)))
            .replace("{{t.badgePass}}", esc(loc.badgeLabel(Badge.PASS)))
            .replace("{{t.badgeInfo}}", esc(loc.badgeLabel(Badge.INFO)))
            .replace("{{t.hideBlank}}", esc(s(R.string.t_hide_blank)))
            .replace("{{t.expandAll}}", esc(s(R.string.t_expand_all)))
            .replace("{{t.collapseAll}}", esc(s(R.string.t_collapse_all)))
            .replace("{{t.legendPass}}", esc(s(R.string.t_legend_pass)))
            .replace("{{t.legendWarn}}", esc(s(R.string.t_legend_warn)))
            .replace("{{t.legendVuln}}", esc(s(R.string.t_legend_vuln)))
            .replace("{{t.legendInfo}}", esc(s(R.string.t_legend_info)))
            .replace("{{t.tip}}", esc(s(R.string.t_tip)))
            .replace("{{t.colName}}", esc(s(R.string.t_col_name)))
            .replace("{{t.colCategory}}", esc(s(R.string.t_col_category)))
            .replace("{{t.colCode}}", esc(s(R.string.t_col_code)))
            .replace("{{t.colQuality}}", esc(s(R.string.t_col_quality)))
            .replace("{{t.generatedBy}}", esc(s(R.string.rep_generated_by)))
            .replace("{{t.rowsWord}}", jsEsc(s(R.string.t_rows_word)))
    }

    private fun llmLabel(result: AnalysisResult): String =
        if (result.llmUsed) s(R.string.rep_llm_on, result.llmProvider, result.llmModel, result.llmAssessed)
        else s(R.string.rep_llm_off)

    private fun warningsLabel(result: AnalysisResult): String =
        if (result.warnings.isEmpty()) s(R.string.rep_footer_no_warnings)
        else s(R.string.rep_footer_warnings, result.warnings.size)

    private fun kindLabels(result: AnalysisResult): String {
        val kinds = result.elements.map { it.kind }.distinct()
        return kinds.joinToString(prefix = "{", postfix = "}", separator = ",") { k ->
            "\"$k\":\"${jsEsc(loc.kindLabel(k))}\""
        }
    }

    private fun stats(result: AnalysisResult): String {
        val chips = buildList {
            add(s(R.string.rep_stat_files) to "${result.filesAnalyzed}/${result.filesFound}")
            add(s(R.string.rep_stat_languages) to result.languageCounts.size.toString())
            add(s(R.string.rep_stat_elements) to result.elements.size.toString())
            add(s(R.string.rep_stat_code_lines) to result.codeLines.toString())
            add(s(R.string.rep_stat_vulnerabilities) to (result.badgeCounts[Badge.VULNERABILITY] ?: 0).toString())
            add(s(R.string.rep_stat_warnings) to (result.badgeCounts[Badge.WARNING] ?: 0).toString())
            add(s(R.string.rep_stat_passed) to (result.badgeCounts[Badge.PASS] ?: 0).toString())
        }
        return chips.joinToString("") { "<span class=\"chip\">${esc(it.first)} <b>${esc(it.second)}</b></span>" }
    }

    private fun coverage(result: AnalysisResult): String {
        val line = result.lineCoveragePercent()
        val symbol = result.symbolCoveragePercent()
        return bar(s(R.string.rep_coverage_code), line, "${result.mappedLines}/${result.codeLines}") +
            bar(s(R.string.rep_coverage_symbol), symbol, "${result.symbolLines}/${result.codeLines}")
    }

    private fun bar(label: String, percent: Double, detail: String): String {
        val p = percent.coerceIn(0.0, 100.0)
        return "<div class=\"bar\"><div class=\"lbl\"><span>${esc(label)}</span>" +
            "<span>${esc(detail)} · ${"%.1f".format(Locale.US, p)}%</span></div>" +
            "<div class=\"track\"><div class=\"fill\" style=\"width:${"%.1f".format(Locale.US, p)}%\"></div></div></div>"
    }

    private fun row(e: CodeElement): String {
        val category = loc.category(e.path, e.language, e.kind, e.name)
        val description = loc.describe(e.kind, e.name, e.declaration, e.path)
        val summary = if (e.assessmentRuleKey != null) {
            loc.ruleSummary(e.assessmentRuleKey, e.assessmentSummary)
        } else {
            e.assessmentSummary
        }
        val detail = if (e.assessmentRuleKey != null) {
            loc.ruleDetail(e.assessmentRuleKey, e.assessmentDetail)
        } else {
            e.assessmentDetail
        }
        val badgeLabel = loc.badgeLabel(e.badge)

        val search = buildString {
            append(e.name).append(' ').append(e.path).append(' ').append(e.kind).append(' ')
            append(category).append(' ').append(summary).append(' ')
            append(e.code.take(400))
        }.lowercase(Locale.US)

        return buildString {
            append("<details class=\"row\" data-kind=\"").append(esc(e.kind))
            append("\" data-badge=\"").append(e.badge.name)
            append("\" data-search=\"").append(esc(search)).append("\">")
            append("<summary class=\"r-summary\">")
            append("<div class=\"s-top\"><span class=\"kind\">").append(esc(loc.kindLabel(e.kind))).append("</span>")
            append("<span class=\"name\">").append(esc(e.name)).append("</span>")
            append("<span class=\"badge ").append(e.badge.cssClass).append("\">")
            append(e.badge.icon).append(' ').append(esc(badgeLabel)).append("</span></div>")
            append("<div class=\"path\">").append(esc(e.path)).append(" · ").append(esc(e.lineLabel)).append("</div>")
            append("<div class=\"s-sum\">").append(esc(summary)).append("</div>")
            append("<div class=\"s-cat\">").append(esc(category)).append("</div>")
            append("</summary>")
            append("<div class=\"r-body\">")
            append(cell(s(R.string.t_col_name), "<p class=\"desc clampable\">${esc(description)}</p>"))
            append(cell(s(R.string.t_col_category), "<p class=\"clampable\">${esc(category)}</p>"))
            append(
                cell(
                    s(R.string.t_col_code),
                    "<div class=\"lineref\">${esc(e.lineLabel)}</div>" +
                        "<pre class=\"code clampable\">${esc(e.code)}</pre>",
                )
            )
            append(
                cell(
                    s(R.string.t_col_quality),
                    "<div class=\"asummary clampable\">${esc(summary.ifBlank { badgeLabel })}</div>" +
                        "<div class=\"adetail clampable\">${esc(detail)}</div>",
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

    private fun jsEsc(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (c in text) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\'' -> sb.append("\\'")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
