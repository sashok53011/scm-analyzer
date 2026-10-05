package online.devhorizon.scm.domain.ingest

/** Decides which files are worth analyzing. */
object RepoFilter {

    private val skipDirs = setOf(
        ".git", ".svn", ".hg", ".idea", ".vscode", ".gradle", ".kotlin",
        "node_modules", "bower_components", "vendor", "Pods", "Carthage",
        "build", "out", "dist", "target", "bin", "obj", "classes",
        "__pycache__", ".mypy_cache", ".pytest_cache", ".tox", "venv", ".venv", "env",
        ".next", ".nuxt", ".cache", ".terraform", "coverage", ".pytest_cache",
        "gradle/wrapper",
    )

    private val skipExt = setOf(
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "icns", "tiff",
        "ttf", "otf", "woff", "woff2", "eot",
        "mp3", "mp4", "wav", "ogg", "flac", "avi", "mov", "mkv", "webm",
        "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar", "war", "aar",
        "class", "dex", "so", "dll", "dylib", "a", "o", "obj", "exe", "bin", "pyc",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "db", "sqlite", "sqlite3", "realm", "lock",
        "min.js", "min.css", "map",
    )

    private val skipNames = setOf(
        "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "gemfile.lock",
        "poetry.lock", "cargo.lock", "composer.lock", "go.sum",
    )

    fun shouldSkipDir(name: String, includeVendor: Boolean): Boolean {
        if (name.startsWith(".") && name != ".") return true
        if (includeVendor) return name == ".git"
        return skipDirs.contains(name)
    }

    fun shouldSkipFile(path: String, size: Long, maxFileBytes: Long, includeVendor: Boolean): Boolean {
        if (size <= 0) return true
        if (size > maxFileBytes) return true
        val name = path.substringAfterLast('/')
        if (skipNames.contains(name.lowercase())) return true
        if (isDocumentation(path, name)) return true
        val ext = LanguageDetector.extensionOf(path)
        if (ext.isEmpty()) return false
        if (skipExt.contains(ext)) return true
        if (includeVendor) return false
        // skip minified
        if (name.endsWith(".min.js") || name.endsWith(".min.css")) return true
        return false
    }

    private val docExtensions = setOf(
        "md", "markdown", "mdx", "txt", "text", "rst", "adoc", "asciidoc", "org",
        "tex", "pdf", "rtf", "odt", "doc", "docx", "po", "pot",
    )

    private val docNames = setOf(
        "readme", "readme.md", "changelog", "changes", "history", "license", "licence",
        "copying", "notice", "authors", "contributors", "contributing", "code_of_conduct",
        "todo", "news", "install", "upgrading", "security", "governance", "faq",
    )

    /** True for documentation files: README/LICENSE/CHANGELOG and .md/.txt/.rst and friends. */
    fun isDocumentation(path: String, name: String = path.substringAfterLast('/')): Boolean {
        val lower = name.lowercase()
        val base = lower.substringBeforeLast('.')
        if (docNames.contains(lower) || docNames.contains(base)) return true
        val ext = LanguageDetector.extensionOf(path)
        return docExtensions.contains(ext)
    }

    /** True for a single line that is only a comment (all common comment styles). */
    fun isCommentLine(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        return t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") || t.endsWith("*/") ||
            t.startsWith("#") || t.startsWith("<!--") || t.startsWith("--") ||
            t.startsWith("\"\"\"") || t.startsWith("'''") || t.startsWith(";") || t.startsWith("rem ")
    }

    fun isBinaryHeuristic(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, 8000)
        var suspicious = 0
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return true
            if (b < 0x09) suspicious++
        }
        return suspicious > n / 10
    }
}
