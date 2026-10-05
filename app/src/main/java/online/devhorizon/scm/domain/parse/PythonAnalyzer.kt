package online.devhorizon.scm.domain.parse

/** Indentation-based analyzer for Python (and similar). */
class PythonAnalyzer : Analyzer {

    override val id: String = "python"

    private val classRe = Regex("""^(\s*)class\s+([A-Za-z_]\w*)\s*(?:\([^)]*\))?\s*:""")
    private val defRe = Regex("""^(\s*)(?:async\s+)?def\s+([A-Za-z_]\w*)\s*\(""")
    private val varRe = Regex("""^([A-Za-z_]\w*)\s*(?::[^=]+)?=""")
    private val decoratorRe = Regex("""^\s*@([\w.]+)""")

    override fun analyze(path: String, lines: List<String>): List<Symbol> {
        val out = ArrayList<Symbol>()
        for (i in lines.indices) {
            val raw = lines[i]
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            classRe.find(raw)?.let { m ->
                val indent = m.groupValues[1].length
                out.add(
                    Symbol(
                        name = m.groupValues[2],
                        kind = SymbolKinds.CLASS,
                        declaration = trimmed,
                        startLine = i + 1,
                        endLine = blockEnd(lines, i, indent) + 1,
                        modifiers = decoratorsFor(lines, i),
                    )
                )
                return@let
            }
            defRe.find(raw)?.let { m ->
                val indent = m.groupValues[1].length
                val decorators = decoratorsFor(lines, i)
                val isRoute = decorators.any {
                    it.contains("route") || it.contains("get") || it.contains("post") ||
                        it.contains("put") || it.contains("delete") || it.contains("task")
                }
                out.add(
                    Symbol(
                        name = m.groupValues[2],
                        kind = if (isRoute) SymbolKinds.TRIGGER else SymbolKinds.FUNCTION,
                        declaration = trimmed,
                        startLine = i + 1,
                        endLine = blockEnd(lines, i, indent) + 1,
                        modifiers = decorators,
                    )
                )
                return@let
            }
            decoratorRe.find(raw)?.let { m ->
                out.add(
                    Symbol(
                        name = "@" + m.groupValues[1],
                        kind = SymbolKinds.TRIGGER,
                        declaration = trimmed,
                        startLine = i + 1,
                        endLine = i + 1,
                    )
                )
            }
            varRe.find(raw)?.let { m ->
                if (raw.firstOrNull()?.isWhitespace() != true) {
                    out.add(
                        Symbol(
                            name = m.groupValues[1],
                            kind = if (m.groupValues[1] == m.groupValues[1].uppercase()) SymbolKinds.CONSTANT else SymbolKinds.VARIABLE,
                            declaration = trimmed,
                            startLine = i + 1,
                            endLine = i + 1,
                        )
                    )
                }
            }
        }
        return out
    }

    private fun decoratorsFor(lines: List<String>, defIndex: Int): List<String> {
        val out = ArrayList<String>()
        var j = defIndex - 1
        while (j >= 0) {
            val t = lines[j].trim()
            if (t.startsWith("@")) { out.add(0, t); j-- } else break
        }
        return out
    }

    private fun blockEnd(lines: List<String>, startIdx: Int, indent: Int): Int {
        var last = startIdx
        var i = startIdx + 1
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }
            val ind = line.takeWhile { it == ' ' || it == '\t' }.length
            if (ind <= indent) break
            last = i
            i++
        }
        return last
    }
}
