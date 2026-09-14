package com.almasix.ide

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.nio.file.Path

/** Extract selected Prism markup into a new view + ``@include``. */
class AlmasixExtractPartialIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getText(): String = "Extract Almasix partial…"
    override fun getFamilyName(): String = "Almasix"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (editor == null || !editor.selectionModel.hasSelection()) return false
        val name = element.containingFile?.virtualFile?.name ?: return false
        return name.endsWith(".prism.html")
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (editor == null) return
        val sel = editor.selectionModel
        if (!sel.hasSelection()) return
        val name = Messages.showInputDialog(
            project,
            "Partial view name (e.g. posts._card)",
            "Extract Partial",
            Messages.getQuestionIcon(),
        ) ?: return
        val root = AlmasixProjectService.getInstance(project).appRoot() ?: return
        val plan = AlmasixRefactorPlanner.extractPartial(
            root.toString(),
            sel.selectedText ?: return,
            sel.selectionStart,
            sel.selectionEnd,
            name,
        )
        if (!plan.isAllowed) {
            Messages.showErrorDialog(project, plan.refusal ?: "Cannot extract", "Almasix")
            return
        }
        WriteCommandAction.runWriteCommandAction(project) {
            plan.createPath?.let { path ->
                AlmasixStubFileWriter.writeIfAbsent(
                    Path.of(path),
                    plan.createContent ?: "",
                )
            }
            val doc = editor.document
            for (edit in plan.edits.sortedByDescending { it.startOffset }) {
                doc.replaceString(edit.startOffset, edit.endOffset, edit.newText)
            }
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        }
        AlmasixProjectService.getInstance(project).rebuild()
    }
}

/** ``@include('x')`` → ``<x-x />``. */
class AlmasixIncludeToComponentIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getText(): String = "Convert @include to <x-… /> component"
    override fun getFamilyName(): String = "Almasix"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        if (file.virtualFile?.name?.endsWith(".prism.html") != true) return false
        val doc = file.viewProvider.document ?: return false
        val offset = editor?.caretModel?.offset ?: element.textRange.startOffset
        return AlmasixRefactorPlanner.includeToComponent(doc.text, offset).isAllowed
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile ?: return
        val doc = editor?.document ?: file.viewProvider.document ?: return
        val offset = editor?.caretModel?.offset ?: element.textRange.startOffset
        val plan = AlmasixRefactorPlanner.includeToComponent(doc.text, offset)
        if (!plan.isAllowed) return
        WriteCommandAction.runWriteCommandAction(project) {
            for (edit in plan.edits.sortedByDescending { it.startOffset }) {
                doc.replaceString(edit.startOffset, edit.endOffset, edit.newText)
            }
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        }
    }
}

/** Insert a ``has_many`` relation stub into the model file (or clipboard as fallback). */
class AlmasixInsertRelationStubIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getText(): String = "Generate Articulate relation method stub"
    override fun getFamilyName(): String = "Almasix"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        if (file.virtualFile?.name?.endsWith(".py") != true) return false
        val doc = file.viewProvider.document ?: return false
        val offset = editor?.caretModel?.offset ?: return false
        val hit = AlmasixSymbolLocator.hitAt(doc.text, offset) ?: return false
        return hit.kind == SymbolKind.RELATION
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile ?: return
        val doc = editor?.document ?: file.viewProvider.document ?: return
        val offset = editor?.caretModel?.offset ?: return
        val hit = AlmasixSymbolLocator.hitAt(doc.text, offset) ?: return
        val related = hit.name.replaceFirstChar { it.uppercase() }.removeSuffix("s").ifBlank { "Related" }
        val before = doc.text.take(offset)
        val site = CallSiteDetector.detect(before)
        val index = AlmasixProjectService.getInstance(project).index()
        val modelHint = site?.receiver?.let {
            AlmasixModelResolver.inferModel(index, before, it) ?: AlmasixModelResolver.peelModelHint(it)
        }
        val modelPath = modelHint?.let { hint ->
            index.modelMetadata[hint]?.path?.takeIf { it.isNotBlank() }
                ?: index.modelMetadata.entries.firstOrNull { (cls, m) ->
                    cls.equals(hint, true) || m.module.equals(hint, true)
                }?.value?.path
        }
        val currentPath = file.virtualFile?.path
        val targetPath = when {
            !modelPath.isNullOrBlank() -> modelPath
            currentPath != null && currentPath.contains("/models/") -> currentPath
            else -> null
        }
        if (targetPath == null) {
            val stub = AlmasixArticulateHelpers.relationMethodStub(hit.name, related)
            Messages.showInfoMessage(project, stub, "Relation stub — copy into the model")
            com.intellij.openapi.ide.CopyPasteManager.getInstance()
                .setContents(java.awt.datatransfer.StringSelection(stub))
            return
        }
        val source = if (targetPath == currentPath) {
            doc.text
        } else {
            runCatching { java.nio.file.Files.readString(Path.of(targetPath)) }.getOrNull() ?: return
        }
        val plan = AlmasixRelationStubPlanner.planInsert(source, hit.name, related)
        if (!plan.isAllowed) {
            Messages.showErrorDialog(project, plan.refusal ?: "Cannot insert", "Almasix")
            return
        }
        WriteCommandAction.runWriteCommandAction(project) {
            if (targetPath == currentPath) {
                for (edit in plan.edits.sortedByDescending { it.startOffset }) {
                    doc.replaceString(edit.startOffset, edit.endOffset, edit.newText)
                }
                PsiDocumentManager.getInstance(project).commitDocument(doc)
            } else {
                val rewritten = AlmasixRelationStubPlanner.applyToText(source, plan.edits)
                AlmasixStubFileWriter.write(Path.of(targetPath), rewritten)
            }
        }
        AlmasixProjectService.getInstance(project).rebuild()
    }
}

