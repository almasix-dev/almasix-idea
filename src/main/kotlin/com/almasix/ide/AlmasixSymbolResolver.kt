package com.almasix.ide

/**
 * Resolve an indexed symbol name to a filesystem location.
 */
object AlmasixSymbolResolver {
    data class Target(val path: String, val line: Int = 0)

    fun resolve(index: AlmasixIndex, kind: SymbolKind, name: String): Target? {
        if (name.isBlank()) return null
        return when (kind) {
            SymbolKind.ROUTE -> {
                val route = index.routes[name] ?: return null
                val path = route.path ?: return null
                Target(path, route.line)
            }
            SymbolKind.VIEW -> {
                val path = index.views[name] ?: return null
                Target(path)
            }
            SymbolKind.CONFIG -> {
                val stem = name.substringBefore('.', name)
                val path = index.configFiles[stem] ?: return null
                Target(path)
            }
            SymbolKind.ENV -> {
                val loc = index.envKeys[name] ?: return null
                val path = loc.path ?: return null
                Target(path, loc.line)
            }
            SymbolKind.TABLE -> {
                val table = index.tables[name] ?: return null
                val path = table.path ?: return null
                Target(path, table.line)
            }
            SymbolKind.COLUMN -> null // needs table context; see resolveColumn
            SymbolKind.COMPONENT -> {
                val path = index.components[name]
                    ?: index.views["components.$name"]
                    ?: return null
                Target(path)
            }
            SymbolKind.VITE -> {
                val path = index.viteEntries[name] ?: index.views[name] ?: return null
                Target(path)
            }
            SymbolKind.RELATION -> {
                val meta = index.modelMetadata.entries.firstOrNull { (_, m) ->
                    name in m.relations
                } ?: return null
                val path = meta.value.path.ifBlank { return null }
                Target(path)
            }
            else -> null
        }
    }

    fun resolveColumn(index: AlmasixIndex, tableHint: String?, column: String): Target? {
        if (column.isBlank()) return null
        fun fromTable(table: AlmasixIndex.TableEntry): Target? {
            val col = table.columns[column]
            val path = col?.path ?: table.path
            return if (path != null) Target(path, col?.line ?: table.line) else null
        }
        if (tableHint != null) {
            index.tables[tableHint]?.let { fromTable(it)?.let { t -> return t } }
            val plural = tableHint + "s"
            index.tables[plural]?.let { fromTable(it)?.let { t -> return t } }
            index.tables[tableHint.removeSuffix("s")]?.let { fromTable(it)?.let { t -> return t } }
        }
        for (table in index.tables.values) {
            fromTable(table)?.let { return it }
        }
        return null
    }
}
