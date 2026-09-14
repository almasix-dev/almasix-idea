package com.almasix.ide

/**
 * Articulate / ORM helper snippets and suggestions (Laravel Idea–style).
 */
object AlmasixArticulateHelpers {
    data class Snippet(val label: String, val template: String, val detail: String)

    /** `Post.with_("comments")` / `load` / `load_missing` suggestions for a model. */
    fun eagerLoadSnippets(index: AlmasixIndex, receiver: String?): List<Snippet> {
        val rels = AlmasixCompletionCatalog.relationsFor(index, receiver).sorted()
        if (rels.isEmpty()) return emptyList()
        val recv = receiver?.substringAfterLast('.') ?: "Model"
        return rels.flatMap { rel ->
            listOf(
                Snippet(
                    label = "$recv.with_(\"$rel\")",
                    template = "$recv.with_(\"$rel\")",
                    detail = "eager load",
                ),
                Snippet(
                    label = "$recv.load(\"$rel\")",
                    template = "$recv.load(\"$rel\")",
                    detail = "lazy eager load",
                ),
            )
        }
    }

    /** `where("email", …)` column helpers. */
    fun whereColumnSnippets(index: AlmasixIndex, receiver: String?): List<Snippet> {
        val cols = AlmasixCompletionCatalog.columnsFor(index, receiver).sorted()
        val recv = receiver?.substringAfterLast('.') ?: "query"
        return cols.map { col ->
            Snippet(
                label = "$recv.where(\"$col\", …)",
                template = "$recv.where(\"$col\", \$value)",
                detail = "column",
            )
        }
    }

    fun relationMethodStub(relationName: String, relatedModel: String = "Related"): String =
        """
        def $relationName(self):
            return self.has_many($relatedModel)
        """.trimIndent()
}
