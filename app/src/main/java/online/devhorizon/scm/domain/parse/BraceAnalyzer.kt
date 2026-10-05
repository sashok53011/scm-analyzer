package online.devhorizon.scm.domain.parse

import online.devhorizon.scm.domain.ingest.LanguageDetector

/** Regex/heuristic analyzer for brace-and-semicolon languages (Kotlin, Java, JS/TS, C-family, Go, Rust...). */
class BraceAnalyzer : Analyzer {

    override val id: String = "brace"

    private val typeRegex = Regex(
        """^\s*(?:(?:@[\w.]+(?:\([^)]*\))?|public|private|protected|internal|open|abstract|final|sealed|data|static|export|default|declare|pub|async|partial|strictfp|unsafe|inline|local|value|annotation|companion|fun)\s+)*(enum\s+class|enum|class|interface|struct|trait|record|impl|protocol|object|actor|extension|typealias|namespace)\s+([A-Za-z_$][\w$]*)"""
    )

    private val kotlinFun = Regex(
        """^\s*(?:(?:@[\w.]+(?:\([^)]*\))?|public|private|protected|internal|open|override|abstract|final|sealed|suspend|inline|operator|infix|tailrec|external|expect|actual|const|lateinit|vararg|crossinline|noinline|reified|fun)\s+)*fun\s+(?:<[^>]+>\s*)?(?:[A-Za-z_][\w<>?.,\[\]]*\.)?([A-Za-z_][\w]*)\s*\("""
    )

    private val jsFun = listOf(
        Regex("""^\s*(?:export\s+)?(?:default\s+)?(?:async\s+)?function\s*\*?\s*([A-Za-z_$][\w$]*)\s*\("""),
        Regex("""^\s*(?:export\s+)?(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?(?:function\b|\*?\s*\(?[^=;]*=>)"""),
        Regex("""^\s*(?:async\s+)?(?:static\s+)?(?:get\s+|set\s+)?([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{"""),
        Regex("""^\s*([A-Za-z_$][\w$]*)\s*[:=]\s*(?:async\s*)?function\s*\("""),
    )

    private val cLikeFun = Regex(
        """^\s*(?:(?:@[\w.]+(?:\([^)]*\))?|public|private|protected|static|final|abstract|synchronized|native|override|virtual|async|sealed|new|internal|constexpr|inline|extern|unsigned|signed|const|unsafe)\s+)*(?:<[^>]+>\s*)?(?:[A-Za-z_][\w:<>,\[\]?&*\s]+\s+)([A-Za-z_]\w*)\s*\([^;{}]*\)?"""
    )

    private val goFun = Regex("""^\s*func\s+(?:\([^)]*\)\s*)?([A-Za-z_]\w*)\s*\(""")
    private val rustFun = Regex("""^\s*(?:pub(?:\([^)]*\))?\s+)?(?:const\s+|async\s+|unsafe\s+|extern\s+"[^"]*"\s+)*fn\s+([A-Za-z_]\w*)""")
    private val phpFun = Regex("""^\s*(?:(?:public|private|protected|static|abstract|final|var|async)\s+)*function\s+&?\s*([A-Za-z_]\w*)\s*\(""")
    private val swiftFun = Regex("""^\s*(?:(?:@[\w.]+(?:\([^)]*\))?|public|private|internal|fileprivate|open|static|class|final|override|mutating|nonmutating|convenience|required|async|throws|rethrows)\s+)*func\s+([A-Za-z_]\w*)""")
    private val dartFun = Regex("""^\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:(?:static|async|external|factory|const)\s+)*[\w<>?,\[\]\s]+\s+([A-Za-z_$][\w$]*)\s*\([^;]*\)\s*(?:async\s*)?[{=>]""")

    private val varRegexes = listOf(
        Regex("""^\s*(?:(?:public|private|protected|internal|const|lateinit|override|static|readonly|final|volatile|transient|extern)\s+)*(val|var|const\s+val)\s+([A-Za-z_][\w]*)\b"""),
        Regex("""^\s*(?:export\s+)?(?:const|let|var)\s+([A-Za-z_$][\w$]*)\b"""),
        Regex("""^\s*(?:var|const)\s+([A-Za-z_]\w*)\b"""),
        Regex("""^\s*(?:pub\s+)?(?:static|const)\s+([A-Za-z_]\w*)"""),
        Regex("""^\s*(?:(?:public|private|protected|static|final|const|readonly|volatile|transient)\s+)*(?:[A-Za-z_][\w:<>,\[\]?&*]*\s+)+([A-Za-z_]\w*)\s*(?:=|;)"""),
    )

    private val triggerRegexes = listOf(
        Regex("""@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping|Scheduled|EventListener|RabbitListener|KafkaListener|KafkaHandler|PostConstruct|PreDestroy|Transactional|Bean|Subscribe|OnMessage)\b"""),
        Regex("""\.(onClick|onChange|onSubmit|addEventListener|setOnClickListener|setOnLongClickListener|addObserver|subscribe|emit|observe)\s*\("""),
        Regex("""\b(router|app|server|api|route)\.(get|post|put|delete|patch|use|all)\s*\("""),
        Regex("""\btask\(|tasks\.register\(|doLast\s*\{"""),
    )

    private val reservedCalls = setOf(
        "if", "for", "while", "switch", "catch", "return", "new", "do", "else", "try",
        "sizeof", "typeof", "defined", "require", "assert", "await", "yield", "print", "println",
        "super", "this", "case", "when", "with", "synchronized",
    )

    override fun analyze(path: String, lines: List<String>): List<Symbol> {
        val language = LanguageDetector.detect(path) ?: "Unknown"
        val out = ArrayList<Symbol>()

        for (i in lines.indices) {
            val raw = lines[i]
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || isCommentLine(trimmed)) continue

            var matched = false

            // 1. Type declarations
            typeRegex.find(raw)?.let { m ->
                val keyword = m.groupValues[1].trim()
                val name = m.groupValues[2]
                val kind = when {
                    keyword.startsWith("enum") -> SymbolKinds.ENUM
                    keyword == "class" || keyword == "struct" || keyword == "record" || keyword == "actor" -> SymbolKinds.CLASS
                    keyword == "interface" || keyword == "trait" || keyword == "protocol" -> SymbolKinds.INTERFACE
                    else -> SymbolKinds.OBJECT
                }
                val modifiers = extractModifiers(raw)
                val isComponent = language == "Kotlin" &&
                    (trimmed.contains("@Composable") || previousLineAnnotation(lines, i, "@Composable"))
                val finalKind = if (isComponent && keyword == "class") SymbolKinds.COMPONENT else kind
                out.add(
                    Symbol(
                        name = name,
                        kind = finalKind,
                        declaration = raw.trim(),
                        startLine = i + 1,
                        endLine = findBraceEnd(lines, i) + 1,
                        modifiers = modifiers,
                    )
                )
                matched = true
            }

            // 2. Function declarations
            if (!matched) {
                val fn = matchFunction(language, raw)
                if (fn != null && !reservedCalls.contains(fn)) {
                    val modifiers = extractModifiers(raw)
                    val isComponent = language == "Kotlin" &&
                        (trimmed.contains("@Composable") || previousLineAnnotation(lines, i, "@Composable"))
                    val kind = if (isComponent) SymbolKinds.COMPONENT else SymbolKinds.FUNCTION
                    out.add(
                        Symbol(
                            name = fn,
                            kind = kind,
                            declaration = raw.trim(),
                            startLine = i + 1,
                            endLine = findBraceEnd(lines, i) + 1,
                            modifiers = modifiers,
                        )
                    )
                    matched = true
                }
            }

            // 3. Variables / constants
            if (!matched) {
                val v = matchVariable(raw)
                if (v != null) {
                    val (name, isConst) = v
                    out.add(
                        Symbol(
                            name = name,
                            kind = if (isConst) SymbolKinds.CONSTANT else SymbolKinds.VARIABLE,
                            declaration = raw.trim(),
                            startLine = i + 1,
                            endLine = i + 1,
                            modifiers = extractModifiers(raw),
                        )
                    )
                    matched = true
                }
            }

            // 4. Triggers (annotations / event handlers) — can coexist with a symbol on the same line
            for (tr in triggerRegexes) {
                tr.find(raw)?.let { m ->
                    val name = m.groupValues.getOrNull(1).orEmpty().ifBlank { m.value }
                    out.add(
                        Symbol(
                            name = "@$name".replace("@@", "@"),
                            kind = SymbolKinds.TRIGGER,
                            declaration = raw.trim(),
                            startLine = i + 1,
                            endLine = i + 1,
                        )
                    )
                }
            }
        }

        return promoteMethods(out)
    }

    private fun matchFunction(language: String, raw: String): String? {
        val candidates: List<Regex> = when (language) {
            "Kotlin" -> listOf(kotlinFun)
            "JavaScript", "TypeScript", "Vue", "Svelte" -> jsFun
            "Go" -> listOf(goFun)
            "Rust" -> listOf(rustFun)
            "PHP" -> listOf(phpFun)
            "Swift" -> listOf(swiftFun)
            "Dart" -> listOf(dartFun)
            else -> listOfNotNull(cLikeFun)
        }
        for (re in candidates) {
            val m = re.find(raw) ?: continue
            val name = m.groupValues.getOrNull(1).orEmpty()
            if (name.isNotBlank()) return name
        }
        return null
    }

    private fun matchVariable(raw: String): Pair<String, Boolean>? {
        for (re in varRegexes) {
            val m = re.find(raw) ?: continue
            val groups = m.groupValues.drop(1).filter { it.isNotBlank() }
            val name = groups.lastOrNull() ?: continue
            if (name.length > 60) continue
            val isConst = raw.contains("const") || raw.contains("val ")
            return name to isConst
        }
        return null
    }

    private fun promoteMethods(symbols: List<Symbol>): List<Symbol> {
        val types = symbols.filter {
            it.kind in setOf(SymbolKinds.CLASS, SymbolKinds.INTERFACE, SymbolKinds.ENUM, SymbolKinds.OBJECT, SymbolKinds.COMPONENT)
        }
        return symbols.map { s ->
            if (s.kind == SymbolKinds.FUNCTION || s.kind == SymbolKinds.COMPONENT) {
                val owner = types.firstOrNull {
                    it !== s && it.startLine < s.startLine && it.endLine >= s.endLine
                }
                if (owner != null && s.kind == SymbolKinds.FUNCTION) s.copy(kind = SymbolKinds.METHOD) else s
            } else s
        }
    }

    private fun extractModifiers(raw: String): List<String> {
        val mods = Regex("""@[\w.]+""").findAll(raw).map { it.value }.toMutableList()
        val words = Regex("""^\s*(?:[\w]+\s+)""").findAll(raw).map { it.value.trim() }.toList()
        mods.addAll(words.take(4))
        return mods
    }

    private fun previousLineAnnotation(lines: List<String>, index: Int, annotation: String): Boolean {
        var j = index - 1
        var hops = 0
        while (j >= 0 && hops < 4) {
            val t = lines[j].trim()
            if (t.isEmpty()) return false
            if (t.startsWith(annotation)) return true
            if (!t.startsWith("@")) return false
            j--; hops++
        }
        return false
    }

    private fun isCommentLine(trimmed: String): Boolean {
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*") || trimmed.startsWith("*/")) return true
        if (trimmed.startsWith("#") && !trimmed.startsWith("#!")) return true
        return false
    }

    /** Finds the line index (0-based) where the braces opened at/after [startIdx] balance. */
    private fun findBraceEnd(lines: List<String>, startIdx: Int): Int {
        var depth = 0
        var seenBrace = false
        var inBlockComment = false
        for (i in startIdx until lines.size) {
            val line = lines[i]
            var j = 0
            var inString = false
            var stringChar = ' '
            while (j < line.length) {
                val c = line[j]
                val n = if (j + 1 < line.length) line[j + 1] else ' '
                if (inBlockComment) {
                    if (c == '*' && n == '/') { inBlockComment = false; j++ }
                    j++; continue
                }
                if (inString) {
                    if (c == '\\') { j += 2; continue }
                    if (c == stringChar) inString = false
                    j++; continue
                }
                when {
                    c == '/' && n == '*' -> { inBlockComment = true; j++ }
                    c == '/' && n == '/' -> break
                    c == '"' || c == '\'' || c == '`' -> { inString = true; stringChar = c }
                    c == '{' -> { depth++; seenBrace = true }
                    c == '}' -> {
                        depth--
                        if (seenBrace && depth <= 0) return i
                    }
                }
                j++
            }
            if (!seenBrace && inBlockComment.not() && i - startIdx > 14) return startIdx
            if (seenBrace && i - startIdx > 500) return i
        }
        return if (seenBrace) minOf(lines.lastIndex, startIdx + 200) else startIdx
    }
}
