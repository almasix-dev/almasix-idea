package com.almasix.ide

/**
 * Resolve model / table hints to migration columns (and auth().user() → User).
 */
object AlmasixModelResolver {
    const val AUTH_USER_SENTINEL = "__auth_user__"

    private val SKIP = setOf(
        "DB", "Schema", "Blueprint", "Migration", "Path", "Model",
        "self", "cls", "os", "re", "sys", "ast", "json",
        // Migration Blueprint parameter — never treat as an Articulate model.
        "table", "define",
    )

    /** Builder / query chain segments that do not change the model. */
    private val CHAIN_NOISE = setOf(
        "query", "new_query", "factory", "create", "update", "fill", "force_fill",
        "where", "or_where", "order_by", "order_by_desc", "group_by", "having",
        "select", "add_select", "with_", "load", "load_missing", "first", "get",
        "find", "all", "paginate", "simple_paginate", "limit", "offset", "take",
        "skip", "latest", "oldest", "pluck", "value", "count", "exists", "doesnt_exist",
        "first_or_create", "update_or_create", "first_or_new", "find_or_new",
        "make", "save", "delete", "fresh", "refresh", "replicate",
    )

    /** Known Articulate model class names from the index. */
    fun modelNames(index: AlmasixIndex): Set<String> {
        val fromTables = index.tables.values.mapNotNull { it.model }.filter { it.isNotBlank() }.toSet()
        return fromTables + index.modelMetadata.keys
    }

    /** Prefer migration table for [hint] (table name or model class). */
    fun resolveTable(index: AlmasixIndex, hint: String?): String? {
        if (hint.isNullOrBlank()) return null
        val key = if (hint == AUTH_USER_SENTINEL) authUserModel(index) else peelModelHint(hint)
        if (key in index.tables) return key
        for ((name, table) in index.tables) {
            if (table.model.equals(key, ignoreCase = true)) return name
        }
        // User → users, Post → posts
        val plural = pluralize(key.replaceFirstChar { it.lowercase() })
        if (plural in index.tables) return plural
        val singular = key.removeSuffix("s")
        if (singular in index.tables) return singular
        // model metadata module stem (user → users via model class User)
        index.modelMetadata.entries.firstOrNull { (cls, m) ->
            cls.equals(key, true) || m.module.equals(key, true)
        }?.let { (cls, _) ->
            for ((name, table) in index.tables) {
                if (table.model.equals(cls, ignoreCase = true)) return name
            }
            val derived = pluralize(cls.replaceFirstChar { it.lowercase() })
            if (derived in index.tables) return derived
        }
        return null
    }

    /**
     * Columns for a model/table hint: migration schema first, then fillable/casts.
     * Unknown receivers (e.g. Blueprint ``table.``) return empty — never dump every
     * column in the database.
     */
    fun columnsFor(index: AlmasixIndex, hint: String?): Set<String> {
        if (hint.isNullOrBlank()) return emptySet()
        if (hint == AUTH_USER_SENTINEL) {
            return columnsForResolved(index, authUserModel(index))
        }
        val peeled = peelModelHint(hint)
        if (peeled in SKIP || peeled.equals("table", ignoreCase = true)) {
            return emptySet()
        }
        val tableName = resolveTable(index, hint)
        if (tableName != null) {
            val cols = index.tables[tableName]?.columns?.keys.orEmpty()
            if (cols.isNotEmpty()) return cols
        }
        return columnsForResolved(index, peeled)
    }

    private fun columnsForResolved(index: AlmasixIndex, model: String): Set<String> {
        index.modelMetadata[model]?.let { meta ->
            return (meta.fillable + meta.guarded + meta.hidden + meta.casts.keys).toSet()
        }
        index.modelMetadata.entries.firstOrNull { (_, m) -> m.module.equals(model, true) }?.let {
            val meta = it.value
            return (meta.fillable + meta.guarded + meta.hidden + meta.casts.keys).toSet()
        }
        return emptySet()
    }

    fun authUserModel(index: AlmasixIndex): String {
        // Prefer table tagged model=User, else class User in metadata, else "User".
        index.tables.values.firstOrNull { it.model.equals("User", true) }?.model?.let { return it }
        if ("User" in index.modelMetadata) return "User"
        index.modelMetadata.keys.firstOrNull { it.equals("User", true) }?.let { return it }
        return "User"
    }

    /**
     * Strip query/factory chain noise: ``Author.query.where`` → ``Author``,
     * ``AuthorFactory`` → ``Author``.
     */
    fun peelModelHint(hint: String): String {
        var head = hint.substringAfterLast('.').ifBlank { hint }
        // Keep leading class when dotted: Author.query → Author
        if (hint.contains('.')) {
            val first = hint.substringBefore('.')
            if (first.isNotEmpty() && first[0].isUpperCase()) {
                head = first
            }
        }
        if (head.endsWith("Factory") && head.length > "Factory".length) {
            val model = head.removeSuffix("Factory")
            if (model.isNotEmpty() && model[0].isUpperCase()) return model
        }
        // Drop trailing chain noise if somehow still present as a single token.
        if (head in CHAIN_NOISE) return hint.substringBefore('.')
        return head
    }

    /**
     * Infer model class for a simple receiver name using text before the caret
     * (annotations / assignments) plus ``user`` → ``User`` heuristic.
     */
    fun inferModel(index: AlmasixIndex, beforeCaret: String, receiver: String): String? {
        if (receiver.isBlank() || receiver in SKIP) return null
        if (receiver == AUTH_USER_SENTINEL) return authUserModel(index)
        val models = modelNames(index)
        val peeled = peelModelHint(receiver)
        if (peeled.isNotEmpty() && peeled[0].isUpperCase() && (models.isEmpty() || peeled in models)) {
            return peeled
        }
        if (peeled != receiver && peeled in models) return peeled

        val inferred = inferFromAssignment(beforeCaret, receiver)
            ?: inferFromAnnotation(beforeCaret, receiver)
            ?: inferFromFactory(beforeCaret, receiver)
            ?: inferChainHead(beforeCaret)
        if (inferred != null) {
            val model = peelModelHint(inferred)
            if (models.isEmpty() || model in models) return model
        }
        val camel = receiver.split('_').joinToString("") { part ->
            part.replaceFirstChar { ch -> ch.uppercase() }
        }
        if (camel in models) return camel
        // auth().user() assigned to `user` is the common case even without annotation.
        if (receiver.equals("user", true) && models.any { it.equals("User", true) }) {
            return authUserModel(index)
        }
        return null
    }

    /**
     * ``Author.query().where(`` / ``Author.factory().create(`` — last model class
     * that opened a chain ending at the caret.
     */
    fun inferChainHead(before: String): String? {
        val re = Regex(
            """\b([A-Z][A-Za-z0-9_]*)\.(?:query|new_query|factory)\s*\(""",
        )
        return re.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    private fun inferFromAssignment(before: String, receiver: String): String? {
        val re = Regex(
            """\b${Regex.escape(receiver)}\s*=\s*(?:await\s+)?([A-Z][A-Za-z0-9_]*)\s*(?:\(|\.|Factory)""",
        )
        return re.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    private fun inferFromFactory(before: String, receiver: String): String? {
        // author = AuthorFactory().create( / Author.factory().create(
        val factoryCls = Regex(
            """\b${Regex.escape(receiver)}\s*=\s*(?:await\s+)?([A-Z][A-Za-z0-9_]*)Factory\s*\(""",
        )
        factoryCls.findAll(before).lastOrNull()?.groupValues?.get(1)?.let { return it }
        val modelFactory = Regex(
            """\b${Regex.escape(receiver)}\s*=\s*(?:await\s+)?([A-Z][A-Za-z0-9_]*)\s*\.\s*factory\s*\(""",
        )
        return modelFactory.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    private fun inferFromAnnotation(before: String, receiver: String): String? {
        val re = Regex(
            """\b${Regex.escape(receiver)}\s*:\s*(?:Optional\[)?([A-Z][A-Za-z0-9_]*)""",
        )
        return re.findAll(before).lastOrNull()?.groupValues?.get(1)
    }

    fun pluralize(singular: String): String {
        val s = singular.lowercase()
        return when {
            s.endsWith("y") && s.length > 1 && s[s.length - 2] !in "aeiou" ->
                s.dropLast(1) + "ies"
            s.endsWith("s") || s.endsWith("x") || s.endsWith("ch") || s.endsWith("sh") ->
                s + "es"
            else -> s + "s"
        }
    }
}
