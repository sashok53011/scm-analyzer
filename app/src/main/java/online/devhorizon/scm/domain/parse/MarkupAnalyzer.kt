package online.devhorizon.scm.domain.parse

import online.devhorizon.scm.domain.ingest.LanguageDetector

/** Analyzer for markup and configuration formats. */
class MarkupAnalyzer : Analyzer {

    override val id: String = "markup"

    private val htmlOpen = Regex("""^\s*<([A-Za-z][\w:-]*)(?:\s[^>]*)?>(?!.*</)""")
    private val htmlSelf = Regex("""^\s*<([A-Za-z][\w:-]*)(?:\s[^>]*)?/>""")
    private val cssRule = Regex("""^\s*([^@{}][^{}]*?)\s*\{""")
    private val jsonKey = Regex("""^\s*"([^"]+)"\s*:""")
    private val yamlKey = Regex("""^(\s*)([A-Za-z_][\w.\-]*)\s*:""")
    private val iniSection = Regex("""^\s*\[([^\]]+)]""")
    private val iniKey = Regex("""^\s*([\w.\-]+)\s*=""")
    private val mdHeading = Regex("""^(#{1,6})\s+(.*)""")
    private val sqlStmt = Regex("""^\s*(CREATE|ALTER|DROP|SELECT|INSERT|UPDATE|DELETE|TRUNCATE|GRANT)\b""", RegexOption.IGNORE_CASE)
    private val dockerInstr = Regex("""^\s*(FROM|RUN|CMD|ENTRYPOINT|COPY|ADD|ENV|ARG|EXPOSE|WORKDIR|LABEL|VOLUME|USER|HEALTHCHECK)\b""")
    private val makeTarget = Regex("""^([A-Za-z_][\w.\-]*)\s*:(?!=)""")
    private val protoDecl = Regex("""^\s*(message|service|enum|rpc|oneof)\s+([A-Za-z_]\w*)""")
    private val tfDecl = Regex("""^\s*(resource|data|variable|output|module|provider)\s+"?([\w.\-]+)"?""")

    override fun analyze(path: String, lines: List<String>): List<Symbol> {
        val language = LanguageDetector.detect(path).orEmpty()
        return when (language) {
            "HTML", "XML" -> analyzeHtml(lines)
            "CSS", "SCSS", "LESS" -> analyzeCss(lines)
            "JSON" -> analyzeJson(lines)
            "YAML" -> analyzeYaml(lines)
            "TOML", "INI", "Properties" -> analyzeIni(lines)
            "Markdown" -> analyzeMarkdown(lines)
            "SQL" -> collect(lines, sqlStmt, SymbolKinds.SELECTOR, "$1")
            "Dockerfile" -> collect(lines, dockerInstr, SymbolKinds.SELECTOR, "$1")
            "Makefile" -> collect(lines, makeTarget, SymbolKinds.SELECTOR, "$1")
            "CMake" -> collect(lines, Regex("""^\s*([A-Za-z_]\w*)\s*\("""), SymbolKinds.SELECTOR, "$1")
            "Protobuf" -> collect(lines, protoDecl, SymbolKinds.CLASS, "$1 $2")
            "Terraform" -> collect(lines, tfDecl, SymbolKinds.CLASS, "$1 $2")
            else -> emptyList()
        }
    }

    private fun analyzeHtml(lines: List<String>): List<Symbol> {
        val out = ArrayList<Symbol>()
        for (i in lines.indices) {
            val raw = lines[i]
            htmlSelf.find(raw)?.let { m ->
                out.add(Symbol(m.groupValues[1], SymbolKinds.TAG, raw.trim(), i + 1, i + 1))
                return@let
            }
            htmlOpen.find(raw)?.let { m ->
                val tag = m.groupValues[1]
                val end = findClosingTag(lines, i, tag)
                out.add(Symbol(tag, SymbolKinds.TAG, raw.trim(), i + 1, end + 1))
            }
        }
        return out
    }

    private fun findClosingTag(lines: List<String>, start: Int, tag: String): Int {
        var depth = 1
        for (i in start until lines.size) {
            val l = lines[i]
            val opens = Regex("""<$tag(?:\s[^>]*)?>""").findAll(l).count()
            val closes = Regex("""</$tag>""").findAll(l).count()
            depth += opens - closes
            if (i == start) depth = 1 + opens - closes
            if (depth <= 0) return i
            if (i - start > 400) return start
        }
        return start
    }

    private fun analyzeCss(lines: List<String>): List<Symbol> {
        val out = ArrayList<Symbol>()
        for (i in lines.indices) {
            cssRule.find(lines[i])?.let { m ->
                val sel = m.groupValues[1].trim()
                if (sel.isBlank()) return@let
                out.add(Symbol(sel.take(60), SymbolKinds.STYLE, lines[i].trim(), i + 1, braceEnd(lines, i) + 1))
            }
        }
        return out
    }

    private fun analyzeJson(lines: List<String>): List<Symbol> {
        val out = ArrayList<Symbol>()
        for (i in lines.indices) {
            jsonKey.find(lines[i])?.let { m ->
                out.add(Symbol(m.groupValues[1], SymbolKinds.CONFIG, lines[i].trim(), i + 1, i + 1))
            }
        }
        return out
    }

    private fun analyzeYaml(lines: List<String>): List<Symbol> {
        val out = ArrayList<Symbol>()
        for (i in lines.indices) {
            val line = lines[i]
            if (line.trim().startsWith("#")) continue
            yamlKey.find(line)?.let { m ->
                out.add(Symbol(m.groupValues[2], SymbolKinds.CONFIG, line.trim(), i + 1, i + 1))
            }
        }
        return out
    }

    private fun analyzeIni(lines: List<String>): List<Symbol> {
        val out = ArrayList<Symbol>()
        for (i in lines.indices) {
            val line = lines[i]
            iniSection.find(line)?.let { m ->
                out.add(Symbol(m.groupValues[1], SymbolKinds.CONFIG, line.trim(), i + 1, sectionEnd(lines, i) + 1))
                return@let
            }
            iniKey.find(line)?.let { m ->
                out.add(Symbol(m.groupValues[1], SymbolKinds.CONFIG, line.trim(), i + 1, i + 1))
            }
        }
        return out
    }

    private fun analyzeMarkdown(lines: List<String>): List<Symbol> {
        val out = ArrayList<Symbol>()
        for (i in lines.indices) {
            mdHeading.find(lines[i])?.let { m ->
                out.add(Symbol("#".repeat(m.groupValues[1].length) + " " + m.groupValues[2].take(70), SymbolKinds.SELECTOR, lines[i].trim(), i + 1, i + 1))
            }
        }
        return out
    }

    private fun collect(lines: List<String>, re: Regex, kind: String, nameTemplate: String): List<Symbol> {
        val out = ArrayList<Symbol>()
        for (i in lines.indices) {
            re.find(lines[i])?.let { m ->
                val name = nameTemplate.replace("$1", m.groupValues.getOrNull(1).orEmpty())
                    .replace("$2", m.groupValues.getOrNull(2).orEmpty()).trim()
                out.add(Symbol(name, kind, lines[i].trim(), i + 1, i + 1))
            }
        }
        return out
    }

    private fun braceEnd(lines: List<String>, start: Int): Int {
        var depth = 0
        for (i in start until lines.size) {
            for (c in lines[i]) {
                if (c == '{') depth++
                if (c == '}') { depth--; if (depth <= 0) return i }
            }
            if (i - start > 500) return i
        }
        return start
    }

    private fun sectionEnd(lines: List<String>, start: Int): Int {
        for (i in start + 1 until lines.size) {
            if (iniSection.containsMatchIn(lines[i])) return i - 1
        }
        return lines.lastIndex
    }
}
