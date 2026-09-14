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
                    val site = CallSiteDetector.detect(before, dotenvFile = isEnv) ?: run {
                        if (isPrism && before.trimEnd().endsWith("@").not()) {
                            // Ctrl+Space in markup: directives + template globals
                            val prefix = result.prefixMatcher.prefix
                            addAll(result, index.directives, "directive", prefix)
                            addAll(result, index.templateVarNames(), "helper", prefix)
                        }
                        return
                    }

                    val items = AlmasixCompletionCatalog.symbolsFor(index, site, before)
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
        fun symbolsFor(index: AlmasixIndex, site: CallSiteDetector.Site): List<Pair<String, String>> =
            AlmasixCompletionCatalog.symbolsFor(index, site)

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
