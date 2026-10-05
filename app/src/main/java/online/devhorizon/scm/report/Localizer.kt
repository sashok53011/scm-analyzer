package online.devhorizon.scm.report

import android.content.Context
import online.devhorizon.scm.R
import online.devhorizon.scm.domain.model.Badge
import online.devhorizon.scm.domain.parse.CategoryResolver
import online.devhorizon.scm.domain.parse.SymbolKinds

/** Resolves report labels (category, description, static rules) in the current app locale. */
class Localizer(context: Context) {

    private val res = context.resources
    private val pkg = context.packageName

    private fun str(id: Int, vararg args: Any): String =
        if (args.isEmpty()) res.getString(id) else res.getString(id, *args)

    fun kindLabel(kind: String): String = str(kindRes(kind))

    fun badgeLabel(badge: Badge): String = str(
        when (badge) {
            Badge.PASS -> R.string.t_badge_pass
            Badge.WARNING -> R.string.t_badge_warn
            Badge.VULNERABILITY -> R.string.t_badge_vuln
            Badge.INFO -> R.string.t_badge_info
        }
    )

    /** Sorted list of kind values that should be shown in the report's kind filter. */
    fun kindValues(): List<String> = listOf(
        SymbolKinds.CLASS, SymbolKinds.INTERFACE, SymbolKinds.ENUM, SymbolKinds.OBJECT,
        SymbolKinds.FUNCTION, SymbolKinds.METHOD, SymbolKinds.COMPONENT, SymbolKinds.TRIGGER,
        SymbolKinds.VARIABLE, SymbolKinds.CONSTANT, SymbolKinds.TAG, SymbolKinds.STYLE,
        SymbolKinds.CONFIG, SymbolKinds.SELECTOR, SymbolKinds.IMPORT, SymbolKinds.COMMENT,
        SymbolKinds.CODE_BLOCK, SymbolKinds.BLANK, SymbolKinds.FILE,
    )

    private fun kindRes(kind: String): Int = when (kind) {
        SymbolKinds.FILE -> R.string.kind_file
        SymbolKinds.CLASS -> R.string.kind_class
        SymbolKinds.INTERFACE -> R.string.kind_interface
        SymbolKinds.ENUM -> R.string.kind_enum
        SymbolKinds.OBJECT -> R.string.kind_object
        SymbolKinds.FUNCTION -> R.string.kind_function
        SymbolKinds.METHOD -> R.string.kind_method
        SymbolKinds.COMPONENT -> R.string.kind_ui_component
        SymbolKinds.TRIGGER -> R.string.kind_trigger
        SymbolKinds.VARIABLE -> R.string.kind_variable
        SymbolKinds.CONSTANT -> R.string.kind_constant
        SymbolKinds.TAG -> R.string.kind_ui_element
        SymbolKinds.STYLE -> R.string.kind_style
        SymbolKinds.CONFIG -> R.string.kind_config
        SymbolKinds.SELECTOR -> R.string.kind_selector
        SymbolKinds.IMPORT -> R.string.kind_import
        SymbolKinds.COMMENT -> R.string.kind_comment
        SymbolKinds.BLANK -> R.string.kind_blank
        else -> R.string.kind_code_block
    }

    private fun layerLabel(key: String): String = str(
        when (key) {
            "presentation" -> R.string.layer_presentation
            "domain" -> R.string.layer_domain
            "data" -> R.string.layer_data
            "network" -> R.string.layer_network
            "api" -> R.string.layer_api
            "service" -> R.string.layer_service
            "routing" -> R.string.layer_routing
            "configuration" -> R.string.layer_configuration
            "utility" -> R.string.layer_utility
            "test" -> R.string.layer_test
            else -> R.string.layer_core
        }
    )

    private fun patternLabel(key: String): String = str(
        when (key) {
            "factory" -> R.string.pattern_factory
            "builder" -> R.string.pattern_builder
            "singleton" -> R.string.pattern_singleton
            "repository" -> R.string.pattern_repository
            "controller" -> R.string.pattern_controller
            "service" -> R.string.pattern_service
            "adapter" -> R.string.pattern_adapter
            "observer" -> R.string.pattern_observer
            "strategy" -> R.string.pattern_strategy
            "decorator" -> R.string.pattern_decorator
            "facade" -> R.string.pattern_facade
            "proxy" -> R.string.pattern_proxy
            "viewmodel" -> R.string.pattern_viewmodel
            "presenter" -> R.string.pattern_presenter
            "handler" -> R.string.pattern_handler
            "listener" -> R.string.pattern_listener
            "manager" -> R.string.pattern_manager
            "provider" -> R.string.pattern_provider
            "mapper" -> R.string.pattern_mapper
            "dto" -> R.string.pattern_dto
            "exception" -> R.string.pattern_exception
            else -> R.string.pattern_handler
        }
    )

    fun category(path: String, language: String, kind: String, name: String): String {
        val layer = layerLabel(CategoryResolver.layerKey(path))
        val pattern = CategoryResolver.patternKey(name)?.let { patternLabel(it) }
        return buildString {
            append(language).append(" · ").append(kindLabel(kind)).append(" · ").append(layer)
            if (pattern != null) append(" · ").append(pattern)
        }
    }

    fun describe(kind: String, name: String, declaration: String, path: String): String {
        val decl = declaration.trim().take(160)
        return when (kind) {
            SymbolKinds.FILE -> str(R.string.describe_file, path)
            SymbolKinds.CLASS -> str(R.string.describe_class, name)
            SymbolKinds.INTERFACE -> str(R.string.describe_interface, name)
            SymbolKinds.ENUM -> str(R.string.describe_enum, name)
            SymbolKinds.OBJECT -> str(R.string.describe_object, name)
            SymbolKinds.FUNCTION -> str(R.string.describe_function, name, decl)
            SymbolKinds.METHOD -> str(R.string.describe_method, name, decl)
            SymbolKinds.COMPONENT -> str(R.string.describe_component, name, decl)
            SymbolKinds.TRIGGER -> str(R.string.describe_trigger, name, decl)
            SymbolKinds.VARIABLE -> str(R.string.describe_variable, name, decl)
            SymbolKinds.CONSTANT -> str(R.string.describe_constant, name, decl)
            SymbolKinds.TAG -> str(R.string.describe_tag, name)
            SymbolKinds.STYLE -> str(R.string.describe_style, name)
            SymbolKinds.CONFIG -> str(R.string.describe_config, name)
            SymbolKinds.SELECTOR -> str(R.string.describe_selector, name)
            SymbolKinds.IMPORT -> str(R.string.describe_import)
            SymbolKinds.COMMENT -> str(R.string.describe_comment)
            SymbolKinds.BLANK -> str(R.string.describe_blank)
            SymbolKinds.CODE_BLOCK -> str(R.string.describe_code_block)
            else -> str(R.string.describe_custom, name, path)
        }
    }

    fun ruleSummary(key: String?, fallback: String): String = rule(key, "summary", fallback)

    fun ruleDetail(key: String?, fallback: String): String = rule(key, "detail", fallback)

    private fun rule(key: String?, suffix: String, fallback: String): String {
        if (key.isNullOrBlank()) return fallback
        val id = res.getIdentifier("rule_${key.replace('-', '_')}_$suffix", "string", pkg)
        return if (id != 0) res.getString(id) else fallback
    }
}
