package com.almasix.ide

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * Starts ``almasix-lsp`` via IntelliJ's process handler so stop/destroy is reliable.
 */
class AlmasixLanguageServer(project: Project) : OSProcessStreamConnectionProvider() {
    init {
        val root = project.guessProjectDir()?.toNioPath()
        val command = resolveCommand(root)
        val cli = GeneralCommandLine(command)
        if (root != null) {
            cli.setWorkDirectory(root.toFile())
        }
        setCommandLine(cli)
    }

    companion object {
        fun resolveCommand(root: Path?): List<String> {
            if (root != null) {
                val venvLsp = root.resolve(".venv/bin/almasix-lsp")
                if (Files.isExecutable(venvLsp)) {
                    return listOf(venvLsp.toString())
                }
                val venvPython = root.resolve(".venv/bin/python")
                if (Files.isExecutable(venvPython)) {
                    return listOf(venvPython.toString(), "-m", "almasix.lsp")
                }
                val winLsp = root.resolve(".venv/Scripts/almasix-lsp.exe")
                if (Files.isRegularFile(winLsp)) {
                    return listOf(winLsp.toString())
                }
            }
            return listOf("almasix-lsp")
        }
    }
}

/**
 * Project-scoped lifecycle: stop the language server when the project closes
 * so an orphan ``almasix-lsp`` process cannot outlive the IDE session.
 */
@Service(Service.Level.PROJECT)
class AlmasixLspLifecycle(private val project: Project) : Disposable {
    override fun dispose() {
        try {
            LanguageServerManager.getInstance(project).stop(AlmasixPluginListener.SERVER_ID)
        } catch (t: Throwable) {
            LOG.warn("Failed to stop almasixLsp on project dispose", t)
        }
    }

    companion object {
        private val LOG = logger<AlmasixLspLifecycle>()
    }
}
