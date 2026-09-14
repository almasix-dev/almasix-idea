package com.almasix.ide

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Native Almasix completions for Python and Prism, driven by [AlmasixIndex].
 */
class AlmasixCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val file = parameters.originalFile.virtualFile ?: return
                    val name = file.name
                    val isPrism = name.endsWith(".prism.html") ||
                        parameters.originalFile.language === PrismLanguage
                    val isPython = name.endsWith(".py")
                    val isEnv = name == ".env" || name.startsWith(".env.")
                    if (!isPrism && !isPython && !isEnv) return

                    val project = parameters.position.project
                    val index = AlmasixProjectService.getInstance(project).index()
                    if (!index.ok && index.views.isEmpty() && index.routes.isEmpty()) return

                    val document = parameters.editor.document
                    val offset = parameters.offset
                    val before = document.text.substring(0, offset.coerceAtMost(document.textLength))
                    val site = CallSiteDetector.detect(before) ?: run {
                        if (isPrism && before.trimEnd().endsWith("@").not()) {
                            // Ctrl+Space in markup: directives + template globals
                            val prefix = result.prefixMatcher.prefix
                            addAll(result, index.directives, "directive", prefix)
                            addAll(result, index.viewHelpers + index.viewShared, "helper", prefix)
                        }
                        return
                    }

                    val items = symbolsFor(index, site)
                    val prefixed = result.withPrefixMatcher(site.prefix)
                    for ((label, detail) in items) {
                        if (!label.startsWith(site.prefix) && site.prefix.isNotEmpty()) {
                            if (!label.contains(site.prefix, ignoreCase = true)) continue
                        }
                        prefixed.addElement(
                            LookupElementBuilder.create(label)
                                .withTypeText(detail, true)
                                .withPresentableText(label),
                        )
                    }
                }
            },
        )
    }

    companion object {
        fun symbolsFor(index: AlmasixIndex, site: CallSiteDetector.Site): List<Pair<String, String>> {
            return when (site.kind) {
                SymbolKind.ROUTE -> index.routes.map { (n, r) ->
                    n to "${r.methods.joinToString("|")} ${r.uri}"
                }
                SymbolKind.VIEW -> index.views.map { it to "view" }
                SymbolKind.CONFIG -> index.configKeys.map { it to "config" }
                SymbolKind.TRANSLATION -> index.translationKeys.map { it to "trans" }
                SymbolKind.MIDDLEWARE -> index.middlewareAliases.map { it to "middleware" }
                SymbolKind.ENV -> index.envKeys.map { it to "env" }
                SymbolKind.TABLE -> index.tables.map { (n, t) -> n to (t.detail.ifBlank { "table" }) }
                SymbolKind.COLUMN -> {
                    val cols = columnsFor(index, site.receiver)
                    cols.map { it to "column" }
                }
                SymbolKind.RELATION -> {
                    val rels = relationsFor(index, site.receiver)
                    rels.map { it to "relation" }
                }
                SymbolKind.CAST -> index.casts.map { it to "cast" }
                SymbolKind.GATE -> index.gates.map { it to "gate" }
                SymbolKind.COMPONENT -> index.components.map { it to "component" }
                SymbolKind.VALIDATION -> index.validationRules.map { it to "rule" }
                SymbolKind.DISK -> index.disks.map { it to "disk" }
                SymbolKind.QUEUE -> index.queues.map { it to "queue" }
                SymbolKind.CACHE -> index.caches.map { it to "cache" }
                SymbolKind.MAILER -> index.mailers.map { it to "mailer" }
                SymbolKind.INERTIA -> index.inertiaPages.map { it to "inertia" }
                SymbolKind.SMITH -> index.smithCommands.map { it to "smith" }
                SymbolKind.VITE -> (index.viteEntries + index.views).map { it to "asset" }
                SymbolKind.DIRECTIVE -> index.directives.map { it to "directive" }
                SymbolKind.TEMPLATE_VAR -> {
                    val vars = index.viewHelpers + index.viewShared +
                        index.viewData.values.flatten()
                    vars.map { it to "var" }
                }
                SymbolKind.CONTROLLER_ACTION -> {
                    index.controllerActions.values.flatten().distinct().map { it to "action" }
                }
            }
        }

        private fun columnsFor(index: AlmasixIndex, receiver: String?): Set<String> {
            if (receiver != null) {
                val simple = receiver.substringAfterLast('.').replaceFirstChar { it.lowercase() }
                index.tables[simple]?.let { return it.columns }
                index.tables[receiver]?.let { return it.columns }
                // User / Post model → users / posts guess
                val plural = simple + "s"
                index.tables[plural]?.let { return it.columns }
                index.modelMetadata.values.firstOrNull {
                    it.module.equals(simple, true) || it.module.equals(receiver, true)
                }?.let { meta ->
                    return (meta.fillable + meta.casts.keys).toSet()
                }
            }
            return index.tables.values.flatMap { it.columns }.toSet()
        }

        private fun relationsFor(index: AlmasixIndex, receiver: String?): Set<String> {
            if (receiver != null) {
                val simple = receiver.substringAfterLast('.')
                index.relations[simple]?.let { return it.toSet() }
                index.modelMetadata[simple]?.relations?.let { return it.toSet() }
            }
            return index.relations.values.flatten().toSet()
        }

        private fun addAll(
            result: CompletionResultSet,
            items: Collection<String>,
            detail: String,
            prefix: String,
        ) {
            for (label in items) {
                if (prefix.isNotEmpty() && !label.startsWith(prefix) &&
                    !label.contains(prefix, ignoreCase = true)
                ) {
                    continue
                }
                result.addElement(
                    LookupElementBuilder.create(label).withTypeText(detail, true),
                )
            }
        }
    }
}
