package com.almasix.ide

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * Starts ``almasix-lsp`` via IntelliJ's process handler so stop/destroy is reliable.
 *
 * Resolution order (first hit wins):
 * 1. Project interpreter (PyCharm / IntelliJ Python SDK) → ``python -m almasix.lsp``
 * 2. ``.venv`` / ``venv`` next to ``bootstrap/app.py`` (walk up from project dir)
 * 3. ``.venv`` / ``venv`` at the project root
 * 4. ``almasix-lsp`` on ``PATH`` (last resort)
 *
 * When the IDE is Windows and the project (or interpreter) lives under
 * ``\\wsl$`` / ``\\wsl.localhost\``, the command is wrapped with ``wsl.exe``
 * so Linux binaries are not spawned as Win32 processes (the common
 * ``pid=null`` failure).
 */
class AlmasixLanguageServer(project: Project) : OSProcessStreamConnectionProvider() {
    init {
        val projectRoot = project.guessProjectDir()?.toNioPath()
        val appRoot = findAppRoot(projectRoot)
        val workDir = appRoot ?: projectRoot
        val rawCommand = resolveCommand(project, projectRoot, appRoot)
        val command = wrapForWslHost(rawCommand, workDir)
        LOG.info("Starting almasixLsp with command=$command workDir=$workDir")
        val cli = GeneralCommandLine(command)
        // For WSL-wrapped commands, ``--cd`` sets the Linux cwd; a Windows UNC
        // working directory confuses CreateProcess.
        if (workDir != null && !isWslWrapped(command)) {
            cli.setWorkDirectory(workDir.toFile())
            cli.withEnvironment("PWD", workDir.toString())
        }
        setCommandLine(cli)
    }

    companion object {
        private val LOG = logger<AlmasixLanguageServer>()

        private val WSL_UNC = Regex(
            """^\\\\(wsl\$|wsl\.localhost)\\([^\\]+)\\?(.*)$""",
            RegexOption.IGNORE_CASE,
        )

        fun findAppRoot(start: Path?): Path? {
            var cur = start
            while (cur != null) {
                if (Files.isRegularFile(cur.resolve("bootstrap/app.py"))) {
                    return cur
                }
                cur = cur.parent
            }
            return null
        }

        fun resolveCommand(project: Project, projectRoot: Path?, appRoot: Path?): List<String> {
            resolveFromSdk(project)?.let { return it }
            for (root in listOfNotNull(appRoot, projectRoot).distinct()) {
                resolveFromVenv(root)?.let { return it }
            }
            return listOf("almasix-lsp")
        }

        /** Testable overload without a live Project. */
        fun resolveCommand(projectRoot: Path?, appRoot: Path? = findAppRoot(projectRoot)): List<String> {
            for (root in listOfNotNull(appRoot, projectRoot).distinct()) {
                resolveFromVenv(root)?.let { return it }
            }
            return listOf("almasix-lsp")
        }

        /**
         * If any path in the command (or the work dir) is a WSL UNC path, rewrite
         * the argv to ``wsl.exe -d <distro> [--cd <linux>] -- <linux-args…>``.
         */
        fun wrapForWslHost(command: List<String>, workDir: Path?): List<String> {
            val work = workDir?.let { parseWslUnc(it.toString()) }
            val mapped = command.map { arg ->
                val hit = parseWslUnc(arg)
                if (hit != null) hit.linuxPath to hit else arg to null
            }
            val distro = mapped.firstNotNullOfOrNull { it.second?.distro } ?: work?.distro
                ?: return command
            val linuxArgs = mapped.map { (rewritten, hit) ->
                if (hit != null) rewritten else {
                    // Bare ``almasix-lsp`` / ``python`` stay as-is — resolved inside WSL PATH.
                    rewritten
                }
            }
            val out = mutableListOf("wsl.exe", "-d", distro)
            val cd = work?.linuxPath
            if (!cd.isNullOrBlank()) {
                out.add("--cd")
                out.add(cd)
            }
            out.add("--")
            out.addAll(linuxArgs)
            return out
        }

        fun isWslWrapped(command: List<String>): Boolean =
            command.firstOrNull()?.equals("wsl.exe", ignoreCase = true) == true

        fun parseWslUnc(path: String): WslUnc? {
            val normalized = path.replace('/', '\\').trimEnd('\\')
            val match = WSL_UNC.matchEntire(normalized) ?: return null
            val distro = match.groupValues[2]
            val rest = match.groupValues[3].replace('\\', '/').trim('/')
            val linux = if (rest.isEmpty()) "/" else "/$rest"
            return WslUnc(distro = distro, linuxPath = linux)
        }

        data class WslUnc(val distro: String, val linuxPath: String)

        private fun resolveFromSdk(project: Project): List<String>? {
            val home = try {
                ProjectRootManager.getInstance(project).projectSdk?.homePath
            } catch (_: Throwable) {
                null
            } ?: return null
            val python = resolvePythonBinary(Path.of(home)) ?: return null
            return listOf(python.toString(), "-m", "almasix.lsp")
        }

        fun resolveFromVenv(root: Path): List<String>? {
            for (venvName in listOf(".venv", "venv")) {
                val venv = root.resolve(venvName)
                if (!Files.isDirectory(venv)) continue
                val unixLsp = venv.resolve("bin/almasix-lsp")
                if (isRunnable(unixLsp)) {
                    return listOf(unixLsp.toString())
                }
                val unixPython = venv.resolve("bin/python")
                if (isRunnable(unixPython)) {
                    return listOf(unixPython.toString(), "-m", "almasix.lsp")
                }
                val winLsp = venv.resolve("Scripts/almasix-lsp.exe")
                if (Files.isRegularFile(winLsp)) {
                    return listOf(winLsp.toString())
                }
                val winPython = venv.resolve("Scripts/python.exe")
                if (Files.isRegularFile(winPython)) {
                    return listOf(winPython.toString(), "-m", "almasix.lsp")
                }
            }
            return null
        }

        fun resolvePythonBinary(homeOrBin: Path): Path? {
            if (isPythonExecutable(homeOrBin)) {
                return homeOrBin
            }
            if (Files.isDirectory(homeOrBin)) {
                val candidates = listOf(
                    homeOrBin.resolve("bin/python"),
                    homeOrBin.resolve("bin/python3"),
                    homeOrBin.resolve("Scripts/python.exe"),
                    homeOrBin.resolve("python.exe"),
                    homeOrBin.resolve("python"),
                )
                return candidates.firstOrNull { isRunnable(it) && isPythonExecutable(it) }
            }
            val sibling = homeOrBin.resolve("python")
            if (isRunnable(sibling) && isPythonExecutable(sibling)) {
                return sibling
            }
            return null
        }

        private fun isPythonExecutable(path: Path): Boolean {
            val name = path.fileName?.toString()?.lowercase() ?: return false
            return name == "python" ||
                name == "python3" ||
                name == "python.exe" ||
                name == "python3.exe" ||
                name.startsWith("python3.")
        }

        private fun isRunnable(path: Path): Boolean {
            if (!Files.isRegularFile(path)) return false
            return try {
                Files.isExecutable(path) || path.toFile().canExecute()
            } catch (_: SecurityException) {
                true
            }
        }
    }
}

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
