package online.devhorizon.scm.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

/** Minimal OpenAI-compatible chat client that works against all four configured providers. */
class OpenAiCompatClient(
    private val http: OkHttpClient = defaultClient(),
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun chat(
        provider: ProviderConfig,
        messages: List<ChatMessage>,
        temperature: Double = 0.1,
        maxTokens: Int = 4096,
    ): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", provider.model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", false)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
        }.toString()

        val request = Request.Builder()
            .url(provider.chatUrl())
            .header("Content-Type", "application/json")
            .header("User-Agent", "SCM-Analyzer")
            .apply { if (provider.apiKey.isNotBlank()) header("Authorization", "Bearer ${provider.apiKey}") }
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${body.take(400)}")
            }
            extractContent(body)
        }
    }

    suspend fun listModels(provider: ProviderConfig): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(provider.modelsUrl())
            .header("User-Agent", "SCM-Analyzer")
            .apply { if (provider.apiKey.isNotBlank()) header("Authorization", "Bearer ${provider.apiKey}") }
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(200)}")
            val root = json.parseToJsonElement(body).jsonObject
            root["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull } ?: emptyList()
        }
    }

    private fun extractContent(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val choices: JsonArray = root["choices"]?.jsonArray ?: error("No choices in response: ${body.take(300)}")
        val first: JsonObject = choices.firstOrNull()?.jsonObject ?: error("Empty choices")
        val message = first["message"]?.jsonObject
        val content = message?.get("content")?.jsonPrimitive?.contentOrNull
        if (content != null) return content
        // Some providers return a plain string under "text".
        return first["text"]?.jsonPrimitive?.contentOrNull ?: error("No content in response")
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
