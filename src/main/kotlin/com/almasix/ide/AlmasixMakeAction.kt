package com.almasix.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * Almasix menu → New… → `smith make:*` actions (scaffolder parity).
 * Interactive model dialog collects companion flags; everything else is a name prompt.
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

        val args = if (generator.id == "model" && generator.interactive) {
            val prompted = AlmasixModelMakeDialog.prompt(project) ?: return
            AlmasixMakeCatalog.modelSmithArgs(prompted.first, prompted.second)
        } else {
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
            AlmasixMakeCatalog.byId(generator.id)?.smithArgs(name)
                ?: generator.smithArgs(name)
        }

        AlmasixSmithRunner.run(project, root, args)
        Messages.showInfoMessage(
            project,
            "Ran: smith $args\n(Index rebuild scheduled.)",
            "Almasix",
        )
    }
}
