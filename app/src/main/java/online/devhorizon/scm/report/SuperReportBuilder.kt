package online.devhorizon.scm.report

import android.content.Context
import online.devhorizon.scm.R
import online.devhorizon.scm.data.llm.ProviderConfig
import online.devhorizon.scm.domain.assess.Assessment
import online.devhorizon.scm.domain.express.Consensus
import online.devhorizon.scm.domain.express.ConsensusVerdict
import online.devhorizon.scm.domain.model.AnalysisResult
import online.devhorizon.scm.domain.model.Badge
import online.devhorizon.scm.domain.model.CodeElement
import online.devhorizon.scm.domain.parse.IndexResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the ensemble "super-report": every element with all model verdicts + a consensus. */
class SuperReportBuilder(private val context: Context) {

    private val loc = Localizer(context)

    private fun s(id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

    private fun severity(b: Badge): Int = when (b) {
        Badge.PASS -> 0
        Badge.INFO -> 1
        Badge.WARNING -> 2
        Badge.VULNERABILITY -> 3
    }

    fun build(
        index: IndexResult,
        providers: List<ProviderConfig>,
        verdicts: Map<String, Map<String, Assessment>>,
        staticAssessments: Map<String, Assessment>,
        superResult: AnalysisResult,
    ): String {
        val template = context.assets.open("super_report_template.html").bufferedReader().use { it.readText() }
        val providerIds = providers.map { it.id }

        val rows = StringBuilder()
        var disputed = 0
        var agreed = 0
        for (e in index.elements) {
            val fallback = staticAssessments[e.id] ?: Assessment(Badge.INFO, "", "", "static")
            val c = Consensus.compute(providerIds, e.id, verdicts, fallback)
            if (c.disputed) disputed++ else if (c.votes > 0) agreed++
            rows.append(row(e, providers, verdicts, c, fallback))
        }

        val stats = buildList {
            add(s(R.string.rep_stat_elements) to index.elements.size.toString())
            add(s(R.string.rep_stat_models) to providers.size.toString())
            add(s(R.string.rep_stat_agreed) to agreed.toString())
            add(s(R.string.rep_stat_disputed) to disputed.toString())
            add(s(R.string.rep_stat_vulnerabilities) to (superResult.badgeCounts[Badge.VULNERABILITY] ?: 0).toString())
            add(s(R.string.rep_stat_warnings) to (superResult.badgeCounts[Badge.WARNING] ?: 0).toString())
        }.joinToString("") { "<span class=\"chip\">${esc(it.first)} <b>${esc(it.second)}</b></span>" }

        val models = providers.joinToString("") {
            "<span class=\"model\"><b>${esc(it.label)}</b> · ${esc(it.model)}</span>"
        }

        val footer = listOf(
            s(R.string.rep_generated_by),
            s(R.string.rep_llm_ensemble, providers.size),
            s(R.string.rep_footer_files, index.filesAnalyzed, index.filesFound),
        ).joinToString(" · ")

        return template
            .replace("{{TITLE}}", esc("${index.repoName} — ${s(R.string.report_tab_super)}"))
            .replace("{{REPO}}", esc(index.repoName))
            .replace("{{SOURCE}}", esc(index.sourceLabel))
            .replace("{{GENERATED}}", esc(formatted(System.currentTimeMillis())))
            .replace("{{STATS}}", stats)
            .replace("{{MODELS}}", models)
            .replace("{{ROWS}}", rows.toString())
            .replace("{{FOOTER}}", esc(footer))
            .replace("{{t.subtitle}}", esc(s(R.string.t_super_subtitle)))
            .replace("{{t.search}}", esc(s(R.string.t_search)))
            .replace("{{t.allVerdicts}}", esc(s(R.string.t_all_verdicts)))
            .replace("{{t.badgeVuln}}", esc(loc.badgeLabel(Badge.VULNERABILITY)))
            .replace("{{t.badgeWarn}}", esc(loc.badgeLabel(Badge.WARNING)))
            .replace("{{t.badgePass}}", esc(loc.badgeLabel(Badge.PASS)))
            .replace("{{t.badgeInfo}}", esc(loc.badgeLabel(Badge.INFO)))
            .replace("{{t.disputedOnly}}", esc(s(R.string.t_disputed_only)))
            .replace("{{t.hideBlank}}", esc(s(R.string.t_hide_blank)))
            .replace("{{t.expandAll}}", esc(s(R.string.t_expand_all)))
            .replace("{{t.collapseAll}}", esc(s(R.string.t_collapse_all)))
            .replace("{{t.rowsWord}}", jsEsc(s(R.string.t_rows_word)))
    }

    private fun noteFor(c: ConsensusVerdict): String = when {
        c.votes == 0 -> s(R.string.consensus_none)
        !c.disputed -> s(R.string.consensus_agree, c.votes, loc.badgeLabel(c.badge))
        else -> {
            val breakdown = c.counts.entries
                .sortedByDescending { severity(it.key) }
                .joinToString(", ") { "${it.value}× ${loc.badgeLabel(it.key)}" }
            s(R.string.consensus_disagree, breakdown, loc.badgeLabel(c.badge))
        }
    }

    private fun row(
        e: CodeElement,
        providers: List<ProviderConfig>,
        verdicts: Map<String, Map<String, Assessment>>,
        c: ConsensusVerdict,
        staticVerdict: Assessment,
    ): String {
        val category = loc.category(e.path, e.language, e.kind, e.name)
        val description = loc.describe(e.kind, e.name, e.declaration, e.path)
        val note = noteFor(c)

        val search = buildString {
            append(e.name).append(' ').append(e.path).append(' ').append(e.kind).append(' ')
            append(category).append(' ').append(note).append(' ').append(e.code.take(400))
        }.lowercase(Locale.US)

        val matrix = StringBuilder()
        for (p in providers) {
            val a = verdicts[p.id]?.get(e.id)
            val badgeHtml = if (a != null) {
                "<span class=\"badge ${a.badge.cssClass}\">${a.badge.icon} ${esc(loc.badgeLabel(a.badge))}</span>"
            } else {
                "<span class=\"badge info\">—</span>"
            }
            matrix.append("<div class=\"vrow\"><span class=\"vlabel\">${esc(p.label)}</span>$badgeHtml")
            matrix.append("<div class=\"vsum\">${esc(a?.summary ?: s(R.string.rep_not_assessed))}</div></div>")
        }

        val details = buildString {
            for (p in providers) {
                val a = verdicts[p.id]?.get(e.id) ?: continue
                append("<div><b>${esc(p.label)}:</b> ${esc(a.detail)}</div>")
            }
        }

        val staticSummary = loc.ruleSummary(staticVerdict.ruleKey, staticVerdict.summary)
        val staticLabel = "${esc(s(R.string.rep_static_rules))}: ${staticVerdict.badge.icon} ${esc(staticSummary)}"

        return buildString {
            append("<details class=\"row\" data-kind=\"").append(esc(e.kind))
            append("\" data-badge=\"").append(c.badge.name)
            append("\" data-disputed=\"").append(if (c.disputed) "1" else "0")
            append("\" data-search=\"").append(esc(search)).append("\">")
            append("<summary class=\"r-summary\">")
            append("<div class=\"s-top\"><span class=\"kind\">").append(esc(loc.kindLabel(e.kind))).append("</span>")
            append("<span class=\"name\">").append(esc(e.name)).append("</span>")
            append("<span class=\"badge ").append(c.badge.cssClass).append("\">")
            append(c.badge.icon).append(' ').append(esc(loc.badgeLabel(c.badge))).append("</span>")
            if (c.disputed) append("<span class=\"badge dispute\">⚖️ ").append(esc(s(R.string.t_disputed_badge))).append("</span>")
            append("</div>")
            append("<div class=\"path\">").append(esc(e.path)).append(" · ").append(esc(e.lineLabel)).append("</div>")
            append("<div class=\"s-sum\">").append(esc(note)).append("</div>")
            append("<div class=\"s-cat\">").append(esc(category)).append("</div>")
            append("</summary>")
            append("<div class=\"r-body\">")
            append("<div class=\"col\"><div class=\"col-title\">").append(esc(s(R.string.t_col_name))).append("</div>")
            append("<p class=\"clampable\">").append(esc(description)).append("</p>")
            append("<div class=\"s-cat\">").append(esc(category)).append("</div></div>")
            append("<div class=\"col\"><div class=\"col-title\">")
            append(esc(s(R.string.t_model_verdicts, providers.size))).append("</div>")
            append(matrix).append("</div>")
            append("<div class=\"col\"><div class=\"col-title\">").append(esc(s(R.string.t_col_code))).append("</div>")
            append("<div class=\"lineref\">").append(esc(e.lineLabel)).append("</div>")
            append("<pre class=\"code clampable\">").append(esc(e.code)).append("</pre></div>")
            append("<div class=\"col\"><div class=\"col-title\">").append(esc(s(R.string.t_col_consensus))).append("</div>")
            append("<div class=\"asummary\">").append(esc(note)).append("</div>")
            if (details.isNotBlank()) append("<div class=\"adetail clampable\">").append(details).append("</div>")
            append("<div class=\"adetail\">").append(staticLabel).append("</div></div>")
            append("</div></details>")
        }
    }

    private fun formatted(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))

    private fun esc(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun jsEsc(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (ch in text) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\'' -> sb.append("\\'")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
