package online.devhorizon.scm.domain.parse

/** One detected code symbol with a 1-based inclusive line range. */
data class Symbol(
    val name: String,
    val kind: String,
    val declaration: String,
    val startLine: Int,
    val endLine: Int,
    val modifiers: List<String> = emptyList(),
)

/** A parser for a family of languages. It need not cover every line; gaps are bucketed later. */
interface Analyzer {
    val id: String
    fun analyze(path: String, lines: List<String>): List<Symbol>
}

object SymbolKinds {
    const val FILE = "file"
    const val CLASS = "class"
    const val INTERFACE = "interface"
    const val ENUM = "enum"
    const val OBJECT = "object"
    const val FUNCTION = "function"
    const val METHOD = "method"
    const val COMPONENT = "ui-component"
    const val TRIGGER = "trigger"
    const val VARIABLE = "variable"
    const val CONSTANT = "constant"
    const val TAG = "ui-element"
    const val STYLE = "style-rule"
    const val CONFIG = "config-key"
    const val SELECTOR = "selector"
    const val IMPORT = "import"
    const val COMMENT = "comment"
    const val CODE_BLOCK = "code-block"
    const val BLANK = "blank"
}

/** Derives the column-2 category string (technology / pattern / layer) for a symbol. */
object CategoryResolver {

    private val layerHints = listOf(
        "test" to "test",
        "spec" to "test",
        "ui" to "presentation",
        "screen" to "presentation",
        "view" to "presentation",
        "compose" to "presentation",
        "fragment" to "presentation",
        "activity" to "presentation",
        "component" to "presentation",
        "domain" to "domain",
        "model" to "domain",
        "entity" to "domain",
        "data" to "data",
        "repository" to "data",
        "dao" to "data",
        "db" to "data",
        "network" to "network",
        "api" to "api",
        "service" to "service",
        "controller" to "api",
        "route" to "routing",
        "config" to "configuration",
        "util" to "utility",
        "helper" to "utility",
    )

    private val patterns = listOf(
        "factory" to "Factory pattern",
        "builder" to "Builder pattern",
        "singleton" to "Singleton",
        "repository" to "Repository pattern",
        "controller" to "MVC controller",
        "service" to "Service layer",
        "adapter" to "Adapter pattern",
        "observer" to "Observer pattern",
        "strategy" to "Strategy pattern",
        "decorator" to "Decorator pattern",
        "facade" to "Facade pattern",
        "proxy" to "Proxy pattern",
        "viewmodel" to "MVVM ViewModel",
        "presenter" to "MVP presenter",
        "handler" to "Handler",
        "listener" to "Listener / callback",
        "manager" to "Manager / orchestrator",
        "provider" to "Provider",
        "mapper" to "Data mapper",
        "dto" to "Data transfer object",
        "exception" to "Error type",
    )

    fun layerOf(path: String): String {
        val lower = path.lowercase()
        for ((hint, layer) in layerHints) {
            if (lower.contains(hint)) return layer
        }
        return "core"
    }

    fun patternOf(name: String): String? {
        val lower = name.lowercase()
        for ((hint, pattern) in patterns) {
            if (lower.endsWith(hint) || lower.contains(hint)) return pattern
        }
        return null
    }

    // ---- Localization-neutral keys (translated in the report layer) ----

    private val layerKeyHints = listOf(
        "test" to "test", "spec" to "test",
        "ui" to "presentation", "screen" to "presentation", "view" to "presentation",
        "compose" to "presentation", "fragment" to "presentation", "activity" to "presentation",
        "component" to "presentation",
        "domain" to "domain", "model" to "domain", "entity" to "domain",
        "data" to "data", "repository" to "data", "dao" to "data", "db" to "data",
        "network" to "network", "api" to "api", "service" to "service",
        "controller" to "api", "route" to "routing",
        "config" to "configuration", "util" to "utility", "helper" to "utility",
    )

    fun layerKey(path: String): String {
        val lower = path.lowercase()
        for ((hint, key) in layerKeyHints) if (lower.contains(hint)) return key
        return "core"
    }

    private val patternKeyHints = listOf(
        "factory", "builder", "singleton", "repository", "controller", "service", "adapter",
        "observer", "strategy", "decorator", "facade", "proxy", "viewmodel", "presenter",
        "handler", "listener", "manager", "provider", "mapper", "dto", "exception",
    )

    fun patternKey(name: String): String? {
        val lower = name.lowercase()
        for (hint in patternKeyHints) if (lower.endsWith(hint) || lower.contains(hint)) return hint
        return null
    }

    fun category(path: String, language: String, kind: String, name: String): String {
        val layer = layerOf(path)
        val pattern = patternOf(name)
        val kindLabel = when (kind) {
            SymbolKinds.FILE -> "file"
            SymbolKinds.CLASS -> "class"
            SymbolKinds.INTERFACE -> "interface"
            SymbolKinds.ENUM -> "enum"
            SymbolKinds.OBJECT -> "object"
            SymbolKinds.FUNCTION -> "function"
            SymbolKinds.METHOD -> "method"
            SymbolKinds.COMPONENT -> "UI component"
            SymbolKinds.TRIGGER -> "event trigger"
            SymbolKinds.VARIABLE -> "variable"
            SymbolKinds.CONSTANT -> "constant"
            SymbolKinds.TAG -> "UI element"
            SymbolKinds.STYLE -> "style rule"
            SymbolKinds.CONFIG -> "config key"
            SymbolKinds.SELECTOR -> "selector"
            SymbolKinds.IMPORT -> "imports"
            SymbolKinds.COMMENT -> "comment"
            SymbolKinds.CODE_BLOCK -> "code block"
            SymbolKinds.BLANK -> "blank lines"
            else -> kind
        }
        return buildString {
            append(language)
            append(" · ")
            append(kindLabel)
            append(" · ")
            append(layer)
            if (pattern != null) {
                append(" · ")
                append(pattern)
            }
        }
    }

    fun describe(kind: String, name: String, declaration: String, path: String): String {
        val decl = declaration.trim().take(160)
        return when (kind) {
            SymbolKinds.FILE -> "Source file `$path` analyzed recursively."
            SymbolKinds.CLASS -> "Class `$name` declares the type below."
            SymbolKinds.INTERFACE -> "Interface `$name` defines a contract implemented elsewhere."
            SymbolKinds.ENUM -> "Enum `$name` enumerates a fixed set of values."
            SymbolKinds.OBJECT -> "Object/singleton `$name` holds shared state or helpers."
            SymbolKinds.FUNCTION -> "Function `$name` — callable routine. `$decl`"
            SymbolKinds.METHOD -> "Method `$name` defined on an enclosing type. `$decl`"
            SymbolKinds.COMPONENT -> "UI component `$name` renders part of the interface. `$decl`"
            SymbolKinds.TRIGGER -> "Trigger `$name` reacts to an event or route. `$decl`"
            SymbolKinds.VARIABLE -> "Variable `$name` stores mutable state. `$decl`"
            SymbolKinds.CONSTANT -> "Constant `$name` holds a fixed value. `$decl`"
            SymbolKinds.TAG -> "UI element `<${name}>` in markup."
            SymbolKinds.STYLE -> "Style rule for selector `$name`."
            SymbolKinds.CONFIG -> "Configuration key `$name`."
            SymbolKinds.SELECTOR -> "Selector `$name`."
            SymbolKinds.IMPORT -> "Import / dependency declarations."
            SymbolKinds.COMMENT -> "Comment / documentation block."
            SymbolKinds.CODE_BLOCK -> "Contiguous code block without a detected symbol."
            SymbolKinds.BLANK -> "Blank lines."
            else -> "Element `$name` in `$path`."
        }
    }
}
