package com.almasix.ide

/**
 * In-memory symbol index produced by `smith ide:index --json`.
 */
data class AlmasixIndex(
    val basePath: String = "",
    val ok: Boolean = false,
    val error: String? = null,
    val views: Set<String> = emptySet(),
    val routes: Map<String, RouteEntry> = emptyMap(),
    val configKeys: Set<String> = emptySet(),
    val translationKeys: Set<String> = emptySet(),
    val middlewareAliases: Set<String> = emptySet(),
    val envKeys: Set<String> = emptySet(),
    val tables: Map<String, TableEntry> = emptyMap(),
    val modelMetadata: Map<String, ModelEntry> = emptyMap(),
    val relations: Map<String, List<String>> = emptyMap(),
    val casts: Set<String> = emptySet(),
    val components: Set<String> = emptySet(),
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
    val viteEntries: Set<String> = emptySet(),
    val controllerActions: Map<String, List<String>> = emptyMap(),
) {
    data class RouteEntry(val uri: String, val methods: List<String>)
    data class TableEntry(val columns: Set<String>, val detail: String = "")
    data class ModelEntry(
        val fillable: List<String> = emptyList(),
        val casts: Map<String, String> = emptyMap(),
        val relations: List<String> = emptyList(),
        val module: String = "",
    )

    fun known(kind: SymbolKind, name: String): Boolean = when (kind) {
        SymbolKind.ROUTE -> routes.containsKey(name)
        SymbolKind.VIEW -> views.contains(name)
        SymbolKind.CONFIG -> configKeys.contains(name)
        SymbolKind.TRANSLATION -> !translationKeys.isEmpty() && translationKeys.contains(name)
        SymbolKind.MIDDLEWARE -> middlewareAliases.contains(name)
        SymbolKind.ENV -> envKeys.contains(name)
        SymbolKind.TABLE -> tables.containsKey(name)
        SymbolKind.GATE -> gates.contains(name)
        SymbolKind.COMPONENT -> components.contains(name) || views.contains("components.$name")
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
        SymbolKind.VITE -> viteEntries.contains(name)
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
