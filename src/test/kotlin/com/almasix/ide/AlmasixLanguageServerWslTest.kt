package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

class AlmasixLanguageServerWslTest {
    @Test
    fun parseWslDollarUnc() {
        val hit = AlmasixLanguageServer.parseWslUnc("""\\wsl$\Ubuntu\home\sam\app""")
        assertEquals("Ubuntu", hit!!.distro)
        assertEquals("/home/sam/app", hit.linuxPath)
    }

    @Test
    fun parseWslLocalhostUnc() {
        val hit = AlmasixLanguageServer.parseWslUnc("""\\wsl.localhost\Debian\home\sam\.venv\bin\python""")
        assertEquals("Debian", hit!!.distro)
        assertEquals("/home/sam/.venv/bin/python", hit.linuxPath)
    }

    @Test
    fun parseIgnoresNativeWindowsPaths() {
        assertNull(AlmasixLanguageServer.parseWslUnc("""C:\Users\sam\project"""))
        assertNull(AlmasixLanguageServer.parseWslUnc("/home/sam/project"))
    }

    @Test
    fun wrapsLinuxVenvCommandForWindowsHost() {
        val work = Path.of("""\\wsl$\Ubuntu\home\sam\blog""")
        val raw = listOf(
            """\\wsl$\Ubuntu\home\sam\blog\.venv\bin\python""",
            "-m",
            "almasix.lsp",
        )
        val wrapped = AlmasixLanguageServer.wrapForWslHost(raw, work)
        assertEquals(
            listOf(
                "wsl.exe",
                "-d",
                "Ubuntu",
                "--cd",
                "/home/sam/blog",
                "--",
                "/home/sam/blog/.venv/bin/python",
                "-m",
                "almasix.lsp",
            ),
            wrapped,
        )
    }

    @Test
    fun wrapsBarePipxCommandWhenProjectIsOnWsl() {
        val work = Path.of("""\\wsl.localhost\Ubuntu\home\sam\blog""")
        val wrapped = AlmasixLanguageServer.wrapForWslHost(listOf("almasix-lsp"), work)
        assertEquals(
            listOf(
                "wsl.exe",
                "-d",
                "Ubuntu",
                "--cd",
                "/home/sam/blog",
                "--",
                "almasix-lsp",
            ),
            wrapped,
        )
    }

    @Test
    fun leavesNativeWindowsCommandAlone() {
        val work = Path.of("""C:\Users\sam\blog""")
        val raw = listOf("""C:\Users\sam\blog\.venv\Scripts\python.exe""", "-m", "almasix.lsp")
        assertEquals(raw, AlmasixLanguageServer.wrapForWslHost(raw, work))
    }
}
