package online.devhorizon.scm.domain.assess

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import online.devhorizon.scm.data.llm.ChatMessage
import online.devhorizon.scm.data.llm.OpenAiCompatClient
import online.devhorizon.scm.data.llm.ProviderConfig
import online.devhorizon.scm.domain.Progress
import online.devhorizon.scm.domain.model.Badge
import online.devhorizon.scm.domain.model.CodeElement
import online.devhorizon.scm.domain.parse.SymbolKinds

/** Batches code elements to the LLM and merges the structured verdicts back in. */
class LlmAssessor(
    private val client: OpenAiCompatClient = OpenAiCompatClient(),
    private val cache: AssessmentCache? = null,
    private val batchSize: Int = 14,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun assess(
        elements: List<CodeElement>,
        provider: ProviderConfig,
        maxElements: Int,
        progress: Progress = Progress.noop(),
    ): Map<String, Assessment> {
        val candidates = elements.asSequence()
            .filter { it.kind != SymbolKinds.BLANK }
            .filter { it.kind != SymbolKinds.IMPORT }
            .filter { it.code.isNotBlank() }
            .sortedBy { if (isSymbol(it.kind)) 0 else 1 }
            .take(maxElements)
            .toList()

        val results = HashMap<String, Assessment>()
        progress.message("LLM review: ${candidates.size} elements via ${provider.label} (${provider.model})")

        var done = 0
        for (batch in candidates.chunked(batchSize)) {
            if (progress.isCancelled()) break
            val pending = ArrayList<CodeElement>()
            for (element in batch) {
                val key = cache?.key(element.code, provider.model)
                val cached = key?.let { cache.get(it) }
                if (cached != null) {
                    results[element.id] = cached
                    done++
                } else {
                    pending.add(element)
                }
            }
            if (pending.isEmpty()) {
                progress.progress(done, candidates.size)
                continue
            }

            val messages = buildPrompt(pending)
            val response = runCatching { client.chat(provider, messages, maxTokens = 8192) }
                .getOrElse { e ->
                    progress.message("LLM error: ${e.message}")
                    null
                }
            if (response != null) {
                val parsed = parse(response)
                for (element in pending) {
                    val a = parsed[element.id]
                    if (a != null) {
                        results[element.id] = a
                        cache?.key(element.code, provider.model)?.let { cache.put(it, a) }
                    }
                }
            }
            done += batch.size
            progress.progress(done, candidates.size)
        }

        cache?.flush()
        return results
    }

    private fun isSymbol(kind: String): Boolean = kind != SymbolKinds.CODE_BLOCK &&
        kind != SymbolKinds.FILE && kind != SymbolKinds.COMMENT

    private fun buildPrompt(elements: List<CodeElement>): List<ChatMessage> {
        val system = """
            You are a senior application-security and code-quality reviewer.
            Review each code element and reply with STRICT JSON only: an array of objects
            {"id":"<id>","badge":"PASS|WARNING|VULNERABILITY|INFO","summary":"<=2 sentences","detail":"actionable explanation"}.
            Rules: VULNERABILITY = exploitable security risk. WARNING = bug, anti-pattern or performance problem.
            PASS = clean and well written. INFO = neutral/uncertain. Do not wrap JSON in markdown.
        """.trimIndent()

        val sb = StringBuilder()
        sb.append("Review these ${elements.size} code elements.\n\n")
        for (e in elements) {
            sb.append("### id: ").append(e.id).append('\n')
            sb.append("language: ").append(e.language).append(" | kind: ").append(e.kind)
                .append(" | name: ").append(e.name).append(" | file: ").append(e.path).append('\n')
            sb.append("```").append('\n')
            sb.append(truncate(e.code, 80)).append('\n')
            sb.append("```\n\n")
        }
        return listOf(ChatMessage("system", system), ChatMessage("user", sb.toString()))
    }

    private fun parse(response: String): Map<String, Assessment> {
        val start = response.indexOf('[')
        val end = response.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyMap()
        val slice = response.substring(start, end + 1)
        val array = runCatching { json.parseToJsonElement(slice).jsonArray }.getOrNull() ?: return emptyMap()
        val out = HashMap<String, Assessment>()
        for (item in array) {
            val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
            val id = obj["id"]?.jsonPrimitive?.contentOrNullSafe() ?: continue
            val badge = badgeOf(obj["badge"]?.jsonPrimitive?.contentOrNullSafe())
            val summary = obj["summary"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
            val detail = obj["detail"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
            out[id] = Assessment(badge, summary.ifBlank { "Reviewed." }, detail, "llm")
        }
        return out
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()

    private fun badgeOf(value: String?): Badge = when (value?.trim()?.uppercase()) {
        "PASS", "OK", "CLEAN" -> Badge.PASS
        "WARNING", "WARN", "PROBLEM" -> Badge.WARNING
        "VULNERABILITY", "VULN", "SECURITY", "CRITICAL" -> Badge.VULNERABILITY
        "INFO", "INFORMATION" -> Badge.INFO
        else -> Badge.INFO
    }

    private fun truncate(text: String, maxLines: Int): String {
        val lines = text.lines()
        return if (lines.size <= maxLines) text else lines.take(maxLines).joinToString("\n") + "\n… (truncated)"
    }
}
