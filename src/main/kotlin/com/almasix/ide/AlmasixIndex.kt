package com.almasix.ide

/**
 * In-memory symbol index produced by `smith ide:index --json`.
 *
 * Name sets drive completions; [Located] / path maps drive Ctrl-click navigation.
 */
data class AlmasixIndex(
    val basePath: String = "",
    val ok: Boolean = false,
    val error: String? = null,
    /** Dotted view name → absolute template path. */
    val views: Map<String, String> = emptyMap(),
    val routes: Map<String, RouteEntry> = emptyMap(),
    val configKeys: Set<String> = emptySet(),
    /** Config file stem (`app`) → absolute path. */
    val configFiles: Map<String, String> = emptyMap(),
    /** Dotted config key → declaration location (`app.env` → line of `"env"`). */
    val configLocations: Map<String, Located> = emptyMap(),
    val translationKeys: Set<String> = emptySet(),
    val middlewareAliases: Set<String> = emptySet(),
    val envKeys: Map<String, EnvEntry> = emptyMap(),
    /** Suggested values for env keys (``QUEUE_CONNECTION`` → sync/redis/…). */
    val envOptions: Map<String, List<String>> = emptyMap(),
    val tables: Map<String, TableEntry> = emptyMap(),
    val modelMetadata: Map<String, ModelEntry> = emptyMap(),
    val relations: Map<String, List<String>> = emptyMap(),
    val casts: Set<String> = emptySet(),
    /** Component / x- tag name → path. */
    val components: Map<String, String> = emptyMap(),
    val gates: Set<String> = emptySet(),
    val disks: Set<String> = emptySet(),
    val queues: Set<String> = emptySet(),
    val caches: Set<String> = emptySet(),
    val mailers: Set<String> = emptySet(),
    val inertiaPages: Set<String> = emptySet(),
    val smithCommands: Set<String> = emptySet(),
    val validationRules: Set<String> = emptySet(),
    val directives: Set<String> = emptySet(),
    val viewHelpers: Map<String, ViewVarEntry> = emptyMap(),
    val viewShared: Map<String, ViewVarEntry> = emptyMap(),
    val viewData: Map<String, Map<String, ViewVarEntry>> = emptyMap(),
    val viteEntries: Map<String, String> = emptyMap(),
    val controllerActions: Map<String, List<String>> = emptyMap(),
) {
    data class Located(val path: String? = null, val line: Int = 0)

    data class EnvEntry(
        val path: String? = null,
        val line: Int = 0,
        val kind: String = "",
        val detail: String = "",
        val usedBy: List<String> = emptyList(),
    )

    data class ViewVarEntry(
        val path: String? = null,
        val line: Int = 0,
        val kind: String = "data",
    )

    data class RouteEntry(
        val uri: String,
        val methods: List<String>,
        val path: String? = null,
        val line: Int = 0,
    )

    data class TableEntry(
        val columns: Map<String, Located> = emptyMap(),
        val detail: String = "",
        val path: String? = null,
        val line: Int = 0,
        /** Articulate model class bound to this table (`User` for `users`). */
        val model: String? = null,
    )

    data class ModelEntry(
        val fillable: List<String> = emptyList(),
        val guarded: List<String> = emptyList(),
        val hidden: List<String> = emptyList(),
        val casts: Map<String, String> = emptyMap(),
        val relations: List<String> = emptyList(),
        /** Relation method name → 0-based line in the model file. */
        val relationLines: Map<String, Int> = emptyMap(),
        val module: String = "",
        val path: String = "",
    )

    fun known(kind: SymbolKind, name: String): Boolean = when (kind) {
        SymbolKind.ROUTE -> routes.containsKey(name)
        SymbolKind.VIEW -> views.containsKey(name)
        SymbolKind.CONFIG -> configKeys.contains(name)
        SymbolKind.TRANSLATION -> translationKeys.isNotEmpty() && translationKeys.contains(name)
        SymbolKind.MIDDLEWARE -> middlewareAliases.contains(name)
        SymbolKind.ENV -> envKeys.containsKey(name)
        SymbolKind.ENV_VALUE -> true
        SymbolKind.TABLE -> tables.containsKey(name)
        SymbolKind.GATE -> gates.contains(name)
        SymbolKind.COMPONENT ->
            components.containsKey(name) || views.containsKey("components.$name")
        SymbolKind.VALIDATION -> {
            val rule = name.substringBefore(":")
            validationRules.contains(rule)
        }
        SymbolKind.DISK -> disks.contains(name)
        SymbolKind.QUEUE -> queues.contains(name)
        SymbolKind.CACHE -> caches.contains(name)
        SymbolKind.MAILER -> mailers.contains(name)
        SymbolKind.INERTIA -> inertiaPages.contains(name)
        SymbolKind.SMITH -> smithCommands.contains(name)
        SymbolKind.VITE -> viteEntries.containsKey(name) || views.containsKey(name)
        SymbolKind.CAST -> casts.contains(name)
        SymbolKind.TEMPLATE_VAR -> templateVarNames().contains(name.substringBefore('.'))
        SymbolKind.CONTROLLER_ACTION -> {
            val controller = name.substringBefore('@')
            val action = name.substringAfter('@', missingDelimiterValue = "")
            when {
                controllerActions.containsKey(name) -> true
                controllerActions.containsKey(controller) && action.isEmpty() -> true
                controllerActions[controller]?.contains(action) == true -> true
                else -> false
            }
        }
        SymbolKind.COLUMN, SymbolKind.RELATION, SymbolKind.DIRECTIVE, SymbolKind.ATTR,
        SymbolKind.MODEL_ATTR -> true
    }

    fun templateVarNames(): Set<String> =
        viewHelpers.keys + viewShared.keys + viewData.values.flatMap { it.keys }.toSet()

    fun optionsForEnvKey(key: String): List<String> {
        envOptions[key]?.let { return it }
        val aliases = mapOf(
            "QUEUE_DRIVER" to "QUEUE_CONNECTION",
            "QUEUE_CONNECTION" to "QUEUE_DRIVER",
            "CACHE_DRIVER" to "CACHE_STORE",
            "CACHE_STORE" to "CACHE_DRIVER",
            "BROADCAST_DRIVER" to "BROADCAST_CONNECTION",
            "BROADCAST_CONNECTION" to "BROADCAST_DRIVER",
        )
        val alt = aliases[key] ?: return emptyList()
        return envOptions[alt] ?: emptyList()
    }

    fun viewNameForPath(absolutePath: String): String? {
        if (absolutePath.isBlank()) return null
        val normalized = absolutePath.replace('\\', '/')
        views.entries.firstOrNull { (_, path) ->
            path.replace('\\', '/') == normalized
        }?.let { return it.key }
        return views.entries.firstOrNull { (_, path) ->
            normalized.endsWith(path.replace('\\', '/')) ||
                path.replace('\\', '/').endsWith(normalized)
        }?.key
    }

    companion object {
        fun empty(error: String? = null) = AlmasixIndex(ok = false, error = error)
    }
}

enum class SymbolKind {
    ROUTE, VIEW, CONFIG, TRANSLATION, MIDDLEWARE, ENV, ENV_VALUE, TABLE, COLUMN,
    RELATION, CAST, GATE, COMPONENT, VALIDATION, DISK, QUEUE, CACHE, MAILER,
    INERTIA, SMITH, VITE, DIRECTIVE, TEMPLATE_VAR, CONTROLLER_ACTION,
    /** Instance attribute: ``user.name`` / ``auth().user().email``. */
    ATTR,
    /** Model list/dict keys: ``fillable`` / ``guarded`` / ``casts`` keys. */
    MODEL_ATTR,
}
