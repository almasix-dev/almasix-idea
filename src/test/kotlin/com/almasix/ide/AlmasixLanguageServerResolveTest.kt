package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class AlmasixLanguageServerResolveTest {
    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun prefersVenvAlmasixLspNextToBootstrap() {
        val app = tmp.root.toPath()
        Files.createDirectories(app.resolve("bootstrap"))
        Files.writeString(app.resolve("bootstrap/app.py"), "application = None\n")
        val lsp = app.resolve(".venv/bin/almasix-lsp")
        Files.createDirectories(lsp.parent)
        Files.writeString(lsp, "#!/bin/sh\n")
        makeExecutable(lsp)

        val cmd = AlmasixLanguageServer.resolveCommand(app)
        assertEquals(listOf(lsp.toString()), cmd)
    }

    @Test
    fun fallsBackToPythonModuleWhenLspScriptMissing() {
        val app = tmp.root.toPath()
        Files.createDirectories(app.resolve("bootstrap"))
        Files.writeString(app.resolve("bootstrap/app.py"), "application = None\n")
        val python = app.resolve(".venv/bin/python")
        Files.createDirectories(python.parent)
        Files.writeString(python, "#!/bin/sh\n")
        makeExecutable(python)

        val cmd = AlmasixLanguageServer.resolveCommand(app)
        assertEquals(listOf(python.toString(), "-m", "almasix.lsp"), cmd)
    }

    @Test
    fun walksUpToFindAppRootVenv() {
        val app = tmp.root.toPath().resolve("examples/blog")
        Files.createDirectories(app.resolve("bootstrap"))
        Files.writeString(app.resolve("bootstrap/app.py"), "application = None\n")
        val python = app.resolve(".venv/bin/python")
        Files.createDirectories(python.parent)
        Files.writeString(python, "#!/bin/sh\n")
        makeExecutable(python)

        val nested = app.resolve("resources/views")
        Files.createDirectories(nested)
        val found = AlmasixLanguageServer.findAppRoot(nested)
        assertEquals(app, found)
        val cmd = AlmasixLanguageServer.resolveCommand(nested, found)
        assertEquals(listOf(python.toString(), "-m", "almasix.lsp"), cmd)
    }

    @Test
    fun bareFallbackWhenNoVenv() {
        val app = tmp.root.toPath()
        Files.createDirectories(app)
        val cmd = AlmasixLanguageServer.resolveCommand(app)
        assertEquals(listOf("almasix-lsp"), cmd)
    }

    @Test
    fun resolvePythonBinaryFromVenvHome() {
        val home = tmp.root.toPath().resolve("sdk")
        val python = home.resolve("bin/python")
        Files.createDirectories(python.parent)
        Files.writeString(python, "#!/bin/sh\n")
        makeExecutable(python)
        val resolved = AlmasixLanguageServer.resolvePythonBinary(home)
        assertEquals(python, resolved)
        assertTrue(AlmasixLanguageServer.resolvePythonBinary(python) == python)
    }

    private fun makeExecutable(path: java.nio.file.Path) {
        try {
            val perms = Files.getPosixFilePermissions(path).toMutableSet()
            perms.add(PosixFilePermission.OWNER_EXECUTE)
            perms.add(PosixFilePermission.GROUP_EXECUTE)
            perms.add(PosixFilePermission.OTHERS_EXECUTE)
            Files.setPosixFilePermissions(path, perms)
        } catch (_: UnsupportedOperationException) {
            path.toFile().setExecutable(true)
        }
    }
}
