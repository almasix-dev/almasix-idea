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
    val translationKeys: Set<String> = emptySet(),
    val middlewareAliases: Set<String> = emptySet(),
    val envKeys: Map<String, Located> = emptyMap(),
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
    val viewHelpers: Set<String> = emptySet(),
    val viewShared: Set<String> = emptySet(),
    val viewData: Map<String, Set<String>> = emptyMap(),
    val viteEntries: Map<String, String> = emptyMap(),
    val controllerActions: Map<String, List<String>> = emptyMap(),
) {
    data class Located(val path: String? = null, val line: Int = 0)

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
    )

    data class ModelEntry(
        val fillable: List<String> = emptyList(),
        val casts: Map<String, String> = emptyMap(),
        val relations: List<String> = emptyList(),
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
        else -> true
    }

    companion object {
        fun empty(error: String? = null) = AlmasixIndex(ok = false, error = error)
    }
}

enum class SymbolKind {
    ROUTE, VIEW, CONFIG, TRANSLATION, MIDDLEWARE, ENV, TABLE, COLUMN,
    RELATION, CAST, GATE, COMPONENT, VALIDATION, DISK, QUEUE, CACHE, MAILER,
    INERTIA, SMITH, VITE, DIRECTIVE, TEMPLATE_VAR, CONTROLLER_ACTION,
}
