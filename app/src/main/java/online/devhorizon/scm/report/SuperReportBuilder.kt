package online.devhorizon.scm.report

import android.content.Context
import online.devhorizon.scm.data.llm.ProviderConfig
import online.devhorizon.scm.domain.assess.Assessment
import online.devhorizon.scm.domain.express.Consensus
import online.devhorizon.scm.domain.model.AnalysisResult
import online.devhorizon.scm.domain.model.Badge
import online.devhorizon.scm.domain.model.CodeElement
import online.devhorizon.scm.domain.parse.IndexResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the ensemble "super-report": every element with all model verdicts + a consensus. */
class SuperReportBuilder(private val context: Context) {

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
            val fallback = staticAssessments[e.id]
                ?: Assessment(Badge.INFO, "", "", "static")
            val c = Consensus.compute(providerIds, e.id, verdicts, fallback)
            if (c.disputed) disputed++ else if (c.votes > 0) agreed++
            rows.append(row(e, providers, verdicts, c, fallback))
        }

        val stats = buildList {
            add("Elements" to index.elements.size.toString())
            add("Models" to providers.size.toString())
            add("Agreed" to agreed.toString())
            add("Disputed" to disputed.toString())
            add("Consensus vulns" to (superResult.badgeCounts[Badge.VULNERABILITY] ?: 0).toString())
            add("Consensus warnings" to (superResult.badgeCounts[Badge.WARNING] ?: 0).toString())
        }.joinToString("") { "<span class=\"chip\">${esc(it.first)} <b>${esc(it.second)}</b></span>" }

        val models = providers.joinToString("") {
            "<span class=\"model\"><b>${esc(it.label)}</b> · ${esc(it.model)}</span>"
        }

        return template
            .replace("{{TITLE}}", esc("${index.repoName} — SCM super-report"))
            .replace("{{REPO}}", esc(index.repoName))
            .replace("{{SOURCE}}", esc(index.sourceLabel))
            .replace("{{GENERATED}}", esc(formatted(System.currentTimeMillis())))
            .replace("{{STATS}}", stats)
            .replace("{{MODELS}}", models)
            .replace("{{ROWS}}", rows.toString())
            .replace("{{FOOTER}}", esc("Ensemble of ${providers.size} model(s) · ${index.filesAnalyzed}/${index.filesFound} files · SCM Analyzer"))
    }

    private fun row(
        e: CodeElement,
        providers: List<ProviderConfig>,
        verdicts: Map<String, Map<String, Assessment>>,
        c: online.devhorizon.scm.domain.express.ConsensusVerdict,
        staticVerdict: Assessment,
    ): String {
        val search = buildString {
            append(e.name).append(' ').append(e.path).append(' ').append(e.kind).append(' ')
            append(e.category).append(' ').append(c.note).append(' ').append(e.code.take(400))
        }.lowercase(Locale.US)

        val matrix = StringBuilder()
        for (p in providers) {
            val a = verdicts[p.id]?.get(e.id)
            val badgeHtml = if (a != null) {
                "<span class=\"badge ${a.badge.cssClass}\">${a.badge.icon} ${esc(a.badge.label)}</span>"
            } else {
                "<span class=\"badge info\">—</span>"
            }
            matrix.append("<div class=\"vrow\"><span class=\"vlabel\">${esc(p.label)}</span>$badgeHtml")
            matrix.append("<div class=\"vsum\">${esc(a?.summary ?: "not assessed by this model")}</div></div>")
        }

        val details = buildString {
            for (p in providers) {
                val a = verdicts[p.id]?.get(e.id) ?: continue
                append("<div><b>${esc(p.label)}:</b> ${esc(a.detail)}</div>")
            }
        }

        return buildString {
            append("<details class=\"row\" data-kind=\"").append(esc(e.kind))
            append("\" data-badge=\"").append(c.badge.name)
            append("\" data-disputed=\"").append(if (c.disputed) "1" else "0")
            append("\" data-search=\"").append(esc(search)).append("\">")
            append("<summary class=\"r-summary\">")
            append("<div class=\"s-top\"><span class=\"kind\">").append(esc(e.kind)).append("</span>")
            append("<span class=\"name\">").append(esc(e.name)).append("</span>")
            append("<span class=\"badge ").append(c.badge.cssClass).append("\">")
            append(c.badge.icon).append(' ').append(esc(c.badge.label)).append("</span>")
            if (c.disputed) append("<span class=\"badge dispute\">⚖️ disputed</span>")
            append("</div>")
            append("<div class=\"path\">").append(esc(e.path)).append(" · ").append(esc(e.lineLabel)).append("</div>")
            append("<div class=\"s-sum\">").append(esc(c.note)).append("</div>")
            append("<div class=\"s-cat\">").append(esc(e.category)).append("</div>")
            append("</summary>")
            append("<div class=\"r-body\">")
            append("<div class=\"col\"><div class=\"col-title\">Name / Description</div>")
            append("<p class=\"clampable\">").append(esc(e.description)).append("</p>")
            append("<div class=\"s-cat\">").append(esc(e.category)).append("</div></div>")
            append("<div class=\"col\"><div class=\"col-title\">Model verdicts (").append(providers.size).append(")</div>")
            append(matrix).append("</div>")
            append("<div class=\"col\"><div class=\"col-title\">Exact code & line reference</div>")
            append("<div class=\"lineref\">").append(esc(e.lineLabel)).append("</div>")
            append("<pre class=\"code clampable\">").append(esc(e.code)).append("</pre></div>")
            append("<div class=\"col\"><div class=\"col-title\">Consensus / quality</div>")
            append("<div class=\"asummary\">").append(esc(c.note)).append("</div>")
            if (details.isNotBlank()) append("<div class=\"adetail clampable\">").append(details).append("</div>")
            append("<div class=\"adetail\">Static rules: ").append(staticVerdict.badge.icon).append(' ')
            append(esc(staticVerdict.summary)).append("</div></div>")
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
}
