package com.almasix.ide

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement

/**
 * Ctrl-click / Go to Declaration for Almasix string symbols and Prism `{{ vars }}`.
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
        if (!AlmasixNavigation.isSupportedFile(file.name, sourceElement.containingFile.language)) {
            return null
        }

        val document = editor.document
        val caret = offset.coerceIn(0, document.textLength)
        val hit = AlmasixSymbolLocator.hitAt(document.text, caret) ?: return null

        val index = AlmasixProjectService.getInstance(project).index()
        if (!index.ok) return null

        val viewName = index.viewNameForPath(file.path)
        val target = AlmasixSymbolResolver.resolve(
            index,
            hit.kind,
            hit.name,
            receiver = hit.receiver,
            viewName = viewName,
        ) ?: return null

        val nav = AlmasixNavigation.navigationElement(project, target) ?: return null
        return arrayOf(nav)
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String =
        "Go to Almasix symbol"
}

/** Shared navigation helpers. */
object AlmasixNavigation {
    fun isSupportedFile(name: String, language: com.intellij.lang.Language?): Boolean {
        val isPrism = name.endsWith(".prism.html") || language === PrismLanguage
        val isPython = name.endsWith(".py")
        val isEnv = name == ".env" || name.startsWith(".env.")
        return isPrism || isPython || isEnv
    }

    fun navigationElement(project: Project, target: AlmasixSymbolResolver.Target): PsiElement? {
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
            override fun getPresentableText(): String = "${target.path}:${target.line + 1}"
        }
    }
}
