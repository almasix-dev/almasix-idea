package com.almasix.ide

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/** Almasix → Rebuild Index */
class RebuildIndexAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = AlmasixProjectService.getInstance(project)
        val index = service.rebuild()
        if (index.ok) {
            Messages.showInfoMessage(
                project,
                "Indexed ${index.views.size} views, ${index.routes.size} routes, " +
                    "${index.configKeys.size} config keys.",
                "Almasix Index",
            )
        } else {
            Messages.showErrorDialog(
                project,
                index.error ?: "Index rebuild failed.",
                "Almasix Index",
            )
        }
    }
}
