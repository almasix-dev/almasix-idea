package com.almasix.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path

/**
 * Almasix menu → New… → individual `smith make:*` / local file-template actions.
 * Thin UI shell; catalog + templates live in [AlmasixMakeCatalog] / [AlmasixFileTemplates].
 */
class AlmasixMakeActionGroup : DefaultActionGroup("New…", true), DumbAware {
    init {
        isPopup = true
        for (gen in AlmasixMakeCatalog.ALL) {
            add(AlmasixMakeAction(gen))
        }
    }
}

class AlmasixMakeAction(
    private val generator: AlmasixMakeCatalog.Generator,
) : AnAction(AlmasixMakeCatalog.menuLabel(generator)), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val root = AlmasixProjectService.getInstance(project).appRoot()
        if (root == null) {
            Messages.showErrorDialog(
                project,
                "Open an Almasix app (folder with bootstrap/app.py) first.",
                "Almasix",
            )
            return
        }
        val name = if (generator.namePrompt != null) {
            Messages.showInputDialog(
                project,
                generator.namePrompt,
                AlmasixMakeCatalog.menuLabel(generator),
                Messages.getQuestionIcon(),
            ) ?: return
        } else {
            null
        }
        if (generator.namePrompt != null && name.isNullOrBlank()) return

        val template = name?.let { AlmasixFileTemplates.resolve(generator.id, it) }
        if (template != null) {
            val absolute = root.resolve(template.relativePath).normalize()
            WriteCommandAction.runWriteCommandAction(project) {
                AlmasixStubFileWriter.writeIfAbsent(absolute, template.contents)
            }
            if (template.openAfterCreate) {
                val vFile = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(absolute.toString())
                if (vFile != null) {
                    FileEditorManager.getInstance(project).openFile(vFile, true)
                }
            }
            AlmasixProjectService.getInstance(project).rebuild()
            Messages.showInfoMessage(
                project,
                "Created ${template.relativePath}",
                "Almasix",
            )
            return
        }

        val args = AlmasixMakeCatalog.byId(generator.id)?.smithArgs(name)
            ?: generator.smithArgs(name)
        AlmasixSmithRunner.run(project, root, args)
        Messages.showInfoMessage(
            project,
            "Ran: smith $args\n(Index rebuild scheduled.)",
            "Almasix",
        )
    }
}
