package online.devhorizon.scm.domain.ingest

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** A single file discovered in a repository, with a lazy reader. */
class RepoEntry(
    val path: String,
    val size: Long,
    private val reader: () -> ByteArray?,
) {
    fun read(): ByteArray? = reader()
}

/** A source of repository files, independent of how they were obtained. */
interface RepoSource {
    /** Human readable repository name. */
    val displayName: String
    /** Short label of where it came from (local path, git URL...). */
    val sourceLabel: String
    /** Enumerate candidate files (already filtered for vendor/binary directories). */
    fun entries(): List<RepoEntry>
}

/** Repository backed by an already-enumerated entry list (cached from a prior walk). */
class CachedRepoSource(
    override val displayName: String,
    override val sourceLabel: String,
    private val cached: List<RepoEntry>,
) : RepoSource {
    override fun entries(): List<RepoEntry> = cached
}

/** Repository backed by a plain filesystem directory (extracted clone, app storage). */
class FileRepoSource(
    private val root: File,
    override val sourceLabel: String = "local",
) : RepoSource {

    override val displayName: String = root.name.ifBlank { "repository" }

    override fun entries(): List<RepoEntry> {
        val out = ArrayList<RepoEntry>()
        walk(root, "", out)
        return out
    }

    private fun walk(dir: File, prefix: String, out: MutableList<RepoEntry>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val name = child.name
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            if (child.isDirectory) {
                if (RepoFilter.shouldSkipDir(name, includeVendor = false)) continue
                walk(child, path, out)
            } else {
                out.add(RepoEntry(path, child.length()) { runCatching { child.readBytes() }.getOrNull() })
            }
        }
    }
}

/** Repository backed by a Storage Access Framework tree (folder picked by the user). */
class SafFolderSource(
    private val context: Context,
    treeUri: Uri,
    override val sourceLabel: String = "local",
) : RepoSource {

    private val root: DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri) ?: error("Cannot open folder")

    override val displayName: String = root.name ?: "repository"

    override fun entries(): List<RepoEntry> {
        val out = ArrayList<RepoEntry>()
        walk(root, "", out)
        return out
    }

    private fun walk(dir: DocumentFile, prefix: String, out: MutableList<RepoEntry>) {
        val children = dir.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            if (child.isDirectory) {
                if (RepoFilter.shouldSkipDir(name, includeVendor = false)) continue
                walk(child, path, out)
            } else {
                val uri = child.uri
                out.add(RepoEntry(path, child.length()) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                })
            }
        }
    }
}
