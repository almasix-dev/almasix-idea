package com.almasix.ide

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.nio.file.Path

/**
 * Alt-Enter / light-bulb: create a missing Prism view or component file.
 */
class AlmasixCreateViewIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getText(): String = "Create Almasix view/component file"
    override fun getFamilyName(): String = "Almasix"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        resolveAction(project, editor, element) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val action = resolveAction(project, editor, element) ?: return
        AlmasixStubFileWriter.applyCreateAction(action)
        AlmasixProjectService.getInstance(project).rebuild()
    }

    private fun resolveAction(
        project: Project,
        editor: Editor?,
        element: PsiElement,
    ): AlmasixCodeActionPlanner.Action? {
        val hit = hitAt(editor, element) ?: return null
        if (hit.kind != SymbolKind.VIEW && hit.kind != SymbolKind.COMPONENT) return null
        val index = AlmasixProjectService.getInstance(project).index()
        return AlmasixCodeActionPlanner.forUnknownSymbol(index, hit.kind, hit.name)
            .firstOrNull { it.createPath != null }
    }
}

/**
 * Alt-Enter: run the matching `smith make:*` for an unknown symbol.
 */
class AlmasixSmithMakeIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getText(): String = "Run smith make for Almasix symbol"
    override fun getFamilyName(): String = "Almasix"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        resolveArgs(project, editor, element) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val args = resolveArgs(project, editor, element) ?: return
        val root = AlmasixProjectService.getInstance(project).appRoot() ?: return
        AlmasixSmithRunner.run(project, root, args)
    }

    private fun resolveArgs(
        project: Project,
        editor: Editor?,
        element: PsiElement,
    ): String? {
        val hit = hitAt(editor, element) ?: return null
        val index = AlmasixProjectService.getInstance(project).index()
        return AlmasixCodeActionPlanner.forUnknownSymbol(index, hit.kind, hit.name)
            .firstOrNull { it.smithArgs != null }
            ?.smithArgs
    }
}

/** Quick-fix attached to unknown-symbol annotations. */
class AlmasixUnknownSymbolQuickFix(
    private val kind: SymbolKind,
    private val name: String,
) : IntentionAction {
    override fun getText(): String = when (kind) {
        SymbolKind.VIEW -> "Create view [$name]"
        SymbolKind.COMPONENT -> "Create component [$name]"
        else -> "Almasix fix for $name"
    }

    override fun getFamilyName(): String = "Almasix"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        kind == SymbolKind.VIEW || kind == SymbolKind.COMPONENT

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val index = AlmasixProjectService.getInstance(project).index()
        val action = AlmasixCodeActionPlanner.forUnknownSymbol(index, kind, name)
            .firstOrNull { it.createPath != null }
            ?: return
        AlmasixStubFileWriter.applyCreateAction(action)
        AlmasixProjectService.getInstance(project).rebuild()
    }

    override fun startInWriteAction(): Boolean = true
}

private fun hitAt(editor: Editor?, element: PsiElement): AlmasixSymbolLocator.Hit? {
    val file = element.containingFile ?: return null
    val vFile = file.virtualFile ?: return null
    if (!AlmasixNavigation.isSupportedFile(vFile.name, file.language)) return null
    val document = editor?.document ?: file.viewProvider.document ?: return null
    val offset = editor?.caretModel?.offset
        ?: (element.textRange.startOffset + element.textLength / 2)
    return AlmasixSymbolLocator.hitAt(document.text, offset)
}

/** Process shell for `smith …` — excluded from the coverage gate. */
object AlmasixSmithRunner {
    fun run(project: Project, appRoot: Path, argsLine: String) {
        val args = argsLine.split(" ").filter { it.isNotBlank() }
        if (args.isEmpty()) return
        val cmd = buildSmithCommand(appRoot, args)
        cmd.withWorkDirectory(appRoot.toFile())
        com.intellij.execution.util.ExecUtil.execAndGetOutput(cmd, 120_000)
        AlmasixProjectService.getInstance(project).rebuild()
    }
}
