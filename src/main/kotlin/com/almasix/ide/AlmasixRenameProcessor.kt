package com.almasix.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Shift-F6 rename for [AlmasixSymbolPsiElement] — applies call-site text edits
 * planned by [AlmasixRenamePlanner], plus view file moves and config key defs.
 */
class AlmasixRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean =
        element is AlmasixSymbolPsiElement && element.kind in AlmasixRenamePlanner.RENAMABLE

    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<out UsageInfo>,
        listener: RefactoringElementListener?,
    ) {
        val symbol = element as? AlmasixSymbolPsiElement ?: return
        val project = element.project
        val root = AlmasixProjectService.getInstance(project).appRoot() ?: return
        applyRename(project, root, symbol.kind, symbol.symbolName, newName)
        listener?.elementRenamed(element)
    }

    companion object {
        fun applyRename(
            project: Project,
            appRoot: Path,
            kind: SymbolKind,
            oldName: String,
            newName: String,
        ): AlmasixRenamePlanner.Plan {
            val service = AlmasixProjectService.getInstance(project)
            val index = service.index()
            val occurrences = AlmasixCallSiteSearcher.findUsages(appRoot, kind, oldName)
            val fileMoves = mutableListOf<AlmasixRenamePlanner.FileMove>()
            val definitionEdits = mutableListOf<AlmasixRenamePlanner.Edit>()

            when (kind) {
                SymbolKind.VIEW -> {
                    AlmasixRenamePlanner.planViewFileMove(
                        kind, oldName, newName, index.views[oldName], appRoot.toString(),
                    )?.let { fileMoves.add(it) }
                }
                SymbolKind.COMPONENT -> {
                    val path = index.components[oldName]
                        ?: index.views["components.$oldName"]
                    AlmasixRenamePlanner.planViewFileMove(
                        kind, oldName, newName, path, appRoot.toString(),
                    )?.let { fileMoves.add(it) }
                }
                SymbolKind.CONFIG -> {
                    val loc = index.configLocations[oldName]
                    val path = loc?.path ?: index.configFiles[oldName.substringBefore('.')]
                    if (!path.isNullOrBlank()) {
                        val text = runCatching { Files.readString(Path.of(path)) }.getOrNull()
                        if (text != null) {
                            definitionEdits.addAll(
                                AlmasixRenamePlanner.planConfigKeyDefinition(
                                    text, path, oldName, newName, loc?.line ?: -1,
                                ),
                            )
                        }
                    }
                }
                else -> Unit
            }

            val plan = AlmasixRenamePlanner.planFromOccurrences(
                kind, oldName, newName, occurrences,
                fileMoves = fileMoves,
                definitionEdits = definitionEdits,
            )
            if (!plan.isAllowed) return plan

            WriteCommandAction.runWriteCommandAction(project, "Rename Almasix $kind", null, {
                val byPath = plan.edits.groupBy { it.path }
                val lfs = LocalFileSystem.getInstance()
                val docs = FileDocumentManager.getInstance()
                for ((path, edits) in byPath) {
                    if (path.isBlank()) continue
                    val vFile = lfs.findFileByPath(path) ?: continue
                    val document = docs.getDocument(vFile) ?: continue
                    for (edit in edits.sortedByDescending { it.startOffset }) {
                        document.replaceString(edit.startOffset, edit.endOffset, edit.newText)
                    }
                }
                for (move in plan.fileMoves) {
                    val from = Path.of(move.fromPath)
                    val to = Path.of(move.toPath)
                    if (!Files.isRegularFile(from)) continue
                    Files.createDirectories(to.parent)
                    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
                    lfs.refreshAndFindFileByPath(move.fromPath)
                    lfs.refreshAndFindFileByPath(move.toPath)
                }
            })
            ApplicationManager.getApplication().invokeLater {
                service.rebuild()
            }
            return plan
        }
    }
}
