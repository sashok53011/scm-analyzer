package online.devhorizon.scm.domain.ingest

/**
 * Maps a file path (and optional first-line shebang) to a language id used by the
 * parser registry and shown in the report.
 */
object LanguageDetector {

    private val byExtension: Map<String, String> = buildMap {
        // JVM
        put("kt", "Kotlin"); put("kts", "Kotlin")
        put("java", "Java")
        put("groovy", "Groovy"); put("gradle", "Groovy")
        put("scala", "Scala")
        // Web / JS
        put("js", "JavaScript"); put("mjs", "JavaScript"); put("cjs", "JavaScript")
        put("jsx", "JavaScript")
        put("ts", "TypeScript"); put("tsx", "TypeScript")
        put("vue", "Vue"); put("svelte", "Svelte")
        // Python / data
        put("py", "Python"); put("pyw", "Python")
        put("rb", "Ruby"); put("rake", "Ruby")
        put("php", "PHP")
        put("pl", "Perl"); put("pm", "Perl")
        put("lua", "Lua")
        put("r", "R")
        put("dart", "Dart")
        put("swift", "Swift")
        // Systems
        put("c", "C"); put("h", "C")
        put("cpp", "C++"); put("cc", "C++"); put("cxx", "C++"); put("hpp", "C++"); put("hh", "C++")
        put("cs", "C#")
        put("go", "Go")
        put("rs", "Rust")
        put("m", "Objective-C"); put("mm", "Objective-C")
        // Shell
        put("sh", "Shell"); put("bash", "Shell"); put("zsh", "Shell"); put("fish", "Shell")
        put("ps1", "PowerShell"); put("bat", "Batch"); put("cmd", "Batch")
        // Markup / config
        put("html", "HTML"); put("htm", "HTML")
        put("xml", "XML"); put("xhtml", "XML"); put("svg", "XML")
        put("css", "CSS"); put("scss", "SCSS"); put("sass", "SCSS"); put("less", "LESS")
        put("json", "JSON"); put("jsonc", "JSON")
        put("yaml", "YAML"); put("yml", "YAML")
        put("toml", "TOML"); put("ini", "INI"); put("cfg", "INI"); put("properties", "Properties")
        put("md", "Markdown"); put("markdown", "Markdown")
        put("sql", "SQL")
        put("proto", "Protobuf")
        put("tf", "Terraform"); put("tfvars", "Terraform")
    }

    fun extensionOf(path: String): String {
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        val dot = name.lastIndexOf('.')
        return if (dot in 0 until name.length - 1) name.substring(dot + 1).lowercase() else ""
    }

    fun detect(path: String, firstLine: String? = null): String? {
        byExtension[extensionOf(path)]?.let { return it }
        val base = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
        when (base) {
            "dockerfile" -> return "Dockerfile"
            "makefile", "gnumakefile" -> return "Makefile"
            "cmakelists.txt" -> return "CMake"
        }
        if (!firstLine.isNullOrBlank() && firstLine.startsWith("#!")) {
            val shebang = firstLine.lowercase()
            return when {
                shebang.contains("python") -> "Python"
                shebang.contains("bash") || shebang.contains("/sh") -> "Shell"
                shebang.contains("node") -> "JavaScript"
                shebang.contains("ruby") -> "Ruby"
                shebang.contains("perl") -> "Perl"
                else -> "Shell"
            }
        }
        return null
    }

    /** Languages parsed with a brace/statement-oriented analyzer. */
    val braceLanguages = setOf(
        "Kotlin", "Java", "JavaScript", "TypeScript", "C", "C++", "C#", "Go", "Rust",
        "PHP", "Swift", "Dart", "Scala", "Groovy", "Objective-C", "Vue", "Svelte", "Lua",
    )

    val markupLanguages = setOf(
        "HTML", "XML", "CSS", "SCSS", "LESS", "JSON", "YAML", "TOML", "INI", "Properties",
        "Markdown", "SQL", "Protobuf", "Terraform", "Dockerfile", "Makefile", "CMake",
    )
}
