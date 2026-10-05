package online.devhorizon.scm.data.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists provider configurations and the active selection. API keys are encrypted when possible. */
class ProviderStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val prefs: SharedPreferences = openPrefs(context)

    private fun openPrefs(context: Context): SharedPreferences = try {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "scm_providers_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (t: Throwable) {
        context.getSharedPreferences("scm_providers", Context.MODE_PRIVATE)
    }

    fun loadProviders(): List<ProviderConfig> {
        val raw = prefs.getString(KEY_PROVIDERS, null) ?: return Providers.defaults
        val stored = runCatching { json.decodeFromString<List<ProviderConfig>>(raw) }.getOrDefault(emptyList())
        // Merge defaults so new built-in providers always appear.
        val byId = stored.associateBy { it.id }
        return Providers.defaults.map { def ->
            byId[def.id] ?: def
        } + stored.filter { s -> Providers.defaults.none { it.id == s.id } }
    }

    fun saveProviders(list: List<ProviderConfig>) {
        prefs.edit().putString(KEY_PROVIDERS, json.encodeToString(list)).apply()
    }

    fun loadSelectedId(): String = prefs.getString(KEY_SELECTED, Providers.DEVHORIZON) ?: Providers.DEVHORIZON

    fun saveSelectedId(id: String) {
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    private companion object {
        const val KEY_PROVIDERS = "providers_json"
        const val KEY_SELECTED = "selected_provider"
    }
}
