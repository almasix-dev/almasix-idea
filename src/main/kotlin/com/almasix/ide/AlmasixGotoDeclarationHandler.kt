package com.almasix.ide

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement

/**
 * Ctrl-click / Go to Declaration for Almasix string symbols.
 */
class AlmasixGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null || editor == null) return null
        val project = sourceElement.project
        val file = sourceElement.containingFile?.virtualFile ?: return null
        val name = file.name
        val isPrism = name.endsWith(".prism.html") ||
            sourceElement.containingFile.language === PrismLanguage
        val isPython = name.endsWith(".py")
        val isEnv = name == ".env" || name.startsWith(".env.")
        if (!isPrism && !isPython && !isEnv) return null

        val document = editor.document
        val caret = offset.coerceIn(0, document.textLength)
        val text = document.text
        val literal = stringAt(text, caret) ?: return null
        val before = text.substring(0, literal.start)
        val site = CallSiteDetector.detect(before + text[literal.start] + literal.value) ?: return null

        val index = AlmasixProjectService.getInstance(project).index()
        if (!index.ok) return null

        val target = when (site.kind) {
            SymbolKind.COLUMN ->
                AlmasixSymbolResolver.resolveColumn(index, site.receiver, literal.value)
            else -> AlmasixSymbolResolver.resolve(index, site.kind, literal.value)
        } ?: return null

        val nav = navigationElement(project, target) ?: return null
        return arrayOf(nav)
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String =
        "Go to Almasix symbol"

    private data class LiteralSpan(val start: Int, val value: String)

    /** Find a single-quoted or double-quoted string containing [offset]. */
    private fun stringAt(text: String, offset: Int): LiteralSpan? {
        if (offset <= 0 || offset > text.length) return null
        // Walk back to opening quote.
        var i = offset - 1
        while (i >= 0 && text[i] != '\n') {
            val c = text[i]
            if (c == '"' || c == '\'') {
                val quote = c
                val start = i
                var j = i + 1
                while (j < text.length && text[j] != quote && text[j] != '\n') j++
                if (j < text.length && text[j] == quote && offset in (start + 1)..j) {
                    return LiteralSpan(start, text.substring(start + 1, j))
                }
                return null
            }
            i--
        }
        return null
    }

    private fun navigationElement(project: Project, target: AlmasixSymbolResolver.Target): PsiElement? {
        val vFile = LocalFileSystem.getInstance().findFileByPath(target.path) ?: return null
        val line = target.line.coerceAtLeast(0)
        return object : FakePsiElement() {
            override fun getParent(): PsiElement? = null
            override fun getProject(): Project = project
            override fun getContainingFile() = null
            override fun getName(): String = target.path
            override fun canNavigate(): Boolean = true
            override fun canNavigateToSource(): Boolean = true
            override fun navigate(requestFocus: Boolean) {
                OpenFileDescriptor(project, vFile, line, 0).navigate(requestFocus)
            }
            override fun getPresentableText(): String = target.path
        }
    }
}
