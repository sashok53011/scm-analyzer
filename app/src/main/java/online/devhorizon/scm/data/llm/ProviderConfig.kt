package online.devhorizon.scm.data.llm

import kotlinx.serialization.Serializable

@Serializable
data class ProviderConfig(
    val id: String,
    val label: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String = "",
    val requiresKey: Boolean = false,
    val notes: String = "",
) {
    fun chatUrl(): String = baseUrl.trimEnd('/') + "/chat/completions"
    fun modelsUrl(): String = baseUrl.trimEnd('/') + "/models"
    val isReady: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && (!requiresKey || apiKey.isNotBlank())
}

object Providers {

    const val CUSTOM = "custom"
    const val OLLAMA_CLOUD = "ollama-cloud"
    const val OPENCODE_ZEN = "opencode-zen"
    const val OPENCODE_GO = "opencode-go"

    val defaults: List<ProviderConfig> = listOf(
        ProviderConfig(
            id = CUSTOM,
            label = "Custom provider",
            baseUrl = "",
            model = "",
            apiKey = "",
            requiresKey = true,
            notes = "OpenAI-compatible endpoint.",
        ),
        ProviderConfig(
            id = OLLAMA_CLOUD,
            label = "Ollama Cloud",
            baseUrl = "https://ollama.com/v1",
            model = "gpt-oss:120b-cloud",
            requiresKey = true,
            notes = "API key, or OAuth via `ollama signin`.",
        ),
        ProviderConfig(
            id = OPENCODE_ZEN,
            label = "OpenCode Zen",
            baseUrl = "https://opencode.ai/zen/v1",
            model = "mimo-v2.6-flash-free",
            requiresKey = true,
            notes = "Default free model; API key from OpenCode Zen.",
        ),
        ProviderConfig(
            id = OPENCODE_GO,
            label = "OpenCode Go",
            baseUrl = "https://opencode.ai/zen/go/v1",
            model = "deepseek-v4.1-flash",
            requiresKey = true,
            notes = "Custom model selection; API key required.",
        ),
    )

    fun find(list: List<ProviderConfig>, id: String): ProviderConfig =
        list.firstOrNull { it.id == id } ?: list.first()

    /** True for the built-in presets (they can be edited but not deleted). */
    fun isBuiltIn(id: String): Boolean = defaults.any { it.id == id }

    fun newCustomId(): String = "custom-" + System.currentTimeMillis()
}
