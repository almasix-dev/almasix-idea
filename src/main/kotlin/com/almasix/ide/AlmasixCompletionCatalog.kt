package com.almasix.ide

/**
 * Pure completion catalog — driven by [AlmasixIndex] + [AlmasixModelResolver].
 */
object AlmasixCompletionCatalog {
    fun symbolsFor(
        index: AlmasixIndex,
        site: CallSiteDetector.Site,
        beforeCaret: String = "",
    ): List<Pair<String, String>> {
        return when (site.kind) {
            SymbolKind.ROUTE -> index.routes.map { (n, r) ->
                n to "${r.methods.joinToString("|")} ${r.uri}"
            }
            SymbolKind.VIEW -> index.views.keys.map { it to "view" }
            SymbolKind.CONFIG -> index.configKeys.map { it to "config" }
            SymbolKind.TRANSLATION -> index.translationKeys.map { it to "trans" }
            SymbolKind.MIDDLEWARE -> index.middlewareAliases.map { it to "middleware" }
            SymbolKind.ENV -> index.envKeys.map { (n, e) ->
                n to (e.detail.ifBlank { "env" })
            }
            SymbolKind.ENV_VALUE -> {
                val key = site.receiver ?: return emptyList()
                index.optionsForEnvKey(key).map { it to "$key option" }
            }
            SymbolKind.TABLE -> index.tables.map { (n, t) -> n to (t.detail.ifBlank { "table" }) }
            SymbolKind.COLUMN, SymbolKind.MODEL_ATTR, SymbolKind.ATTR -> {
                val hint = resolveHint(index, site, beforeCaret)
                AlmasixModelResolver.columnsFor(index, hint).map { it to columnDetail(index, hint, it) }
            }
            SymbolKind.RELATION -> {
                val hint = resolveHint(index, site, beforeCaret)
                relationsFor(index, hint).map { it to "relation" }
            }
            SymbolKind.CAST -> index.casts.map { it to "cast" }
            SymbolKind.GATE -> index.gates.map { it to "gate" }
            SymbolKind.COMPONENT -> index.components.keys.map { it to "component" }
            SymbolKind.VALIDATION -> index.validationRules.map { it to "rule" }
            SymbolKind.DISK -> index.disks.map { it to "disk" }
            SymbolKind.QUEUE -> index.queues.map { it to "queue" }
            SymbolKind.CACHE -> index.caches.map { it to "cache" }
            SymbolKind.MAILER -> index.mailers.map { it to "mailer" }
            SymbolKind.INERTIA -> index.inertiaPages.map { it to "inertia" }
            SymbolKind.SMITH -> index.smithCommands.map { it to "smith" }
            SymbolKind.VITE -> (index.viteEntries.keys + index.views.keys).map { it to "asset" }
            SymbolKind.DIRECTIVE -> index.directives.map { it to "directive" }
            SymbolKind.TEMPLATE_VAR -> index.templateVarNames().map { it to "var" }
            SymbolKind.CONTROLLER_ACTION ->
                index.controllerActions.values.flatten().distinct().map { it to "action" }
        }
    }

    private fun resolveHint(
        index: AlmasixIndex,
        site: CallSiteDetector.Site,
        beforeCaret: String,
    ): String? {
        val raw = site.receiver ?: return null
        if (raw == AlmasixModelResolver.AUTH_USER_SENTINEL) {
            return AlmasixModelResolver.AUTH_USER_SENTINEL
        }
        // fillable/guarded/casts on a model class — walk for `class User` before caret
        if (site.kind == SymbolKind.MODEL_ATTR) {
            enclosingModelClass(beforeCaret)?.let { return it }
        }
        return AlmasixModelResolver.inferModel(index, beforeCaret, raw) ?: AlmasixModelResolver.peelModelHint(raw)
    }

    private fun enclosingModelClass(before: String): String? {
        val re = Regex("""class\s+([A-Z][A-Za-z0-9_]*)\s*[:(]""")
        return re.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    private fun columnDetail(index: AlmasixIndex, hint: String?, @Suppress("UNUSED_PARAMETER") column: String): String {
        val table = AlmasixModelResolver.resolveTable(index, hint) ?: return "column"
        return "column · $table"
    }

    /** @deprecated Prefer [AlmasixModelResolver.columnsFor]; kept for tests. */
    fun columnsFor(index: AlmasixIndex, receiver: String?): Set<String> =
        AlmasixModelResolver.columnsFor(index, receiver)

    fun relationsFor(index: AlmasixIndex, receiver: String?): Set<String> {
        if (receiver != null) {
            val hint = if (receiver == AlmasixModelResolver.AUTH_USER_SENTINEL) {
                AlmasixModelResolver.authUserModel(index)
            } else {
                receiver.substringAfterLast('.')
            }
            index.relations[hint]?.let { return it.toSet() }
            index.modelMetadata[hint]?.relations?.let { return it.toSet() }
            index.modelMetadata.entries.firstOrNull { (cls, m) ->
                cls.equals(hint, true) || m.module.equals(hint, true)
            }?.value?.relations?.let { return it.toSet() }
        }
        return index.relations.values.flatten().toSet()
    }
}
