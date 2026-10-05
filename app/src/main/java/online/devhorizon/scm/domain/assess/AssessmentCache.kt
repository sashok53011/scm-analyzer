package online.devhorizon.scm.domain.assess

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class CachedAssessment(
    val badge: String,
    val summary: String,
    val detail: String,
    val source: String,
)

/** Content-hash cache so repeated runs and provider switches do not re-spend tokens. */
class AssessmentCache(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val map: MutableMap<String, CachedAssessment> = load()

    fun key(code: String, model: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest((model + "\u0000" + code).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun get(key: String): Assessment? = map[key]?.let {
        Assessment(
            badge = runCatching { online.devhorizon.scm.domain.model.Badge.valueOf(it.badge) }.getOrDefault(online.devhorizon.scm.domain.model.Badge.INFO),
            summary = it.summary,
            detail = it.detail,
            source = it.source,
        )
    }

    fun put(key: String, assessment: Assessment) {
        map[key] = CachedAssessment(
            badge = assessment.badge.name,
            summary = assessment.summary,
            detail = assessment.detail,
            source = assessment.source,
        )
    }

    fun flush() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(map))
        }
    }

    private fun load(): MutableMap<String, CachedAssessment> = runCatching {
        if (!file.isFile) return emptyMap<String, CachedAssessment>().toMutableMap()
        json.decodeFromString<Map<String, CachedAssessment>>(file.readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())
}
