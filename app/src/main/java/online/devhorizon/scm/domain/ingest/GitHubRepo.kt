package online.devhorizon.scm.domain.ingest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Downloads a public GitHub repository's working tree as a zip archive and extracts it.
 * We only need the source tree for analysis, so a zipball is lighter than a full git clone.
 */
object GitHubRepo {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    data class Ref(val owner: String, val repo: String, val branch: String?)

    /** Accepts https URLs (any case), "owner/repo" and "git@github.com:owner/repo.git". */
    fun parse(url: String): Ref {
        var s = url.trim().replace('\\', '/')
        val schemeIdx = s.indexOf("://")
        if (schemeIdx >= 0) s = s.substring(schemeIdx + 3)
        s = s.removePrefix("www.")
        if (s.startsWith("git@", ignoreCase = true)) s = s.substringAfter(':')
        if (s.startsWith("github.com/", ignoreCase = true)) s = s.substring("github.com/".length)
        s = s.removeSuffix(".git").trim('/')
        val parts = s.split('/').filter { it.isNotBlank() }
        require(parts.size >= 2) { "Expected github.com/owner/repo or owner/repo" }
        val owner = parts[0]
        val repo = parts[1]
        val branchIndex = parts.indexOfFirst { it == "tree" || it == "blob" }
        val branch = if (branchIndex >= 0 && parts.size > branchIndex + 1) parts[branchIndex + 1] else null
        return Ref(owner, repo, branch)
    }

    /**
     * Downloads and extracts the repository into [destDir]. Returns the root directory of the
     * extracted tree (the single top-level folder GitHub adds), or [destDir] on failure.
     */
    suspend fun downloadAndExtract(
        ref: Ref,
        destDir: File,
        onLog: (String) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        destDir.listFiles()?.forEach { it.deleteRecursively() }

        val branch = ref.branch ?: "HEAD"
        val candidates = buildList {
            if (ref.branch != null) {
                add("https://codeload.github.com/${ref.owner}/${ref.repo}/zip/refs/heads/${ref.branch}")
                add("https://codeload.github.com/${ref.owner}/${ref.repo}/zip/refs/tags/${ref.branch}")
            }
            add("https://codeload.github.com/${ref.owner}/${ref.repo}/zip/HEAD")
            add("https://codeload.github.com/${ref.owner}/${ref.repo}/zip/refs/heads/main")
            add("https://codeload.github.com/${ref.owner}/${ref.repo}/zip/refs/heads/master")
        }

        var lastError: String? = null
        for (url in candidates) {
            try {
                onLog("GET $url")
                val request = Request.Builder().url(url).header("User-Agent", "SCM-Analyzer").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code} for $url"
                        return@use
                    }
                    val body = response.body ?: return@use
                    ZipInputStream(BufferedInputStream(body.byteStream())).use { zip ->
                        extractZip(zip, destDir)
                    }
                    onLog("Extracted to ${destDir.name} (branch=$branch)")
                    return@withContext singleRoot(destDir)
                }
            } catch (t: Throwable) {
                lastError = t.message ?: t.toString()
            }
        }
        error("GitHub download failed: ${lastError ?: "unknown error"}")
    }

    private fun extractZip(zip: ZipInputStream, destDir: File) {
        val canonicalDest = destDir.canonicalFile
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            val target = File(destDir, name)
            // Zip-slip protection.
            if (!target.canonicalPath.startsWith(canonicalDest.path + File.separator) &&
                target.canonicalPath != canonicalDest.path
            ) {
                zip.closeEntry()
                continue
            }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.outputStream().use { out -> zip.copyTo(out) }
            }
            zip.closeEntry()
        }
    }

    private fun singleRoot(dir: File): File {
        val children = dir.listFiles() ?: return dir
        val dirs = children.filter { it.isDirectory }
        return if (dirs.size == 1) dirs[0] else dir
    }
}
