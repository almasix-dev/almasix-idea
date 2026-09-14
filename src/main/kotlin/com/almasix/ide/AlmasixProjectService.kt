package com.almasix.ide

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Project-level Almasix app root + cached [AlmasixIndex] from `smith ide:index --json`.
 */
@Service(Service.Level.PROJECT)
class AlmasixProjectService(private val project: Project) {
    private val indexRef = AtomicReference<AlmasixIndex?>(null)
    private val LOG = logger<AlmasixProjectService>()

    fun appRoot(): Path? {
        val base = project.guessProjectDir()?.toNioPath() ?: return null
        return findBootstrapRoot(base)
    }

    fun index(): AlmasixIndex {
        indexRef.get()?.let { return it }
        return rebuild()
    }

    fun rebuild(): AlmasixIndex {
        val root = appRoot()
        val built = if (root == null) {
            AlmasixIndex.empty(error = "No Almasix application found (missing bootstrap/app.py).")
        } else {
            try {
                AlmasixIndexProcess.run(root)
            } catch (t: Throwable) {
                LOG.warn("ide:index failed for $root", t)
                AlmasixIndex.empty(error = t.message ?: t.toString())
            }
        }
        indexRef.set(built)
        return built
    }

    fun invalidate() {
        indexRef.set(null)
    }

    companion object {
        fun getInstance(project: Project): AlmasixProjectService =
            project.getService(AlmasixProjectService::class.java)

        fun findBootstrapRoot(start: Path): Path? {
            var current: Path? = start
            while (current != null) {
                if (Files.isRegularFile(current.resolve("bootstrap/app.py"))) {
                    return current
                }
                current = current.parent
            }
            // Shallow search under examples/
            val examples = start.resolve("examples")
            if (Files.isDirectory(examples)) {
                val preferred = listOf("progress", "blog", "web", "deploy")
                for (name in preferred) {
                    val candidate = examples.resolve(name)
                    if (Files.isRegularFile(candidate.resolve("bootstrap/app.py"))) {
                        return candidate
                    }
                }
                Files.list(examples).use { stream ->
                    for (child in stream.toList().sortedBy { it.fileName.toString() }) {
                        if (Files.isDirectory(child) &&
                            Files.isRegularFile(child.resolve("bootstrap/app.py"))
                        ) {
                            return child
                        }
                    }
                }
            }
            return null
        }

        fun isIndexRelevant(file: VirtualFile): Boolean {
            val path = file.path.replace('\\', '/')
            return path.contains("/routes/") ||
                path.contains("/config/") ||
                path.contains("/lang/") ||
                path.contains("/resources/views/") ||
                path.contains("/app/models/") ||
                path.contains("/database/migrations/") ||
                path.contains("/app/policies/") ||
                file.name.startsWith(".env") ||
                file.name == "bootstrap/app.py"
        }
    }
}
