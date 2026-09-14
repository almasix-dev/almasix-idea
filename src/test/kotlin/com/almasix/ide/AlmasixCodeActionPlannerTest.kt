package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AlmasixCodeActionPlannerTest {
    private fun index(base: String = "/tmp/app", views: Map<String, String> = emptyMap()) =
        AlmasixIndex(basePath = base, ok = true, views = views, components = emptyMap())

    @Test
    fun createViewAndSmithForUnknownView() {
        val actions = AlmasixCodeActionPlanner.forUnknownSymbol(
            index(),
            SymbolKind.VIEW,
            "auth.login",
        )
        assertEquals(2, actions.size)
        assertTrue(actions.any { it.id == "create-view-file" && it.createPath!!.endsWith("auth/login.prism.html") })
        assertTrue(actions.any { it.smithArgs == "make:view auth.login" })
    }

    @Test
    fun noActionsWhenKnown() {
        val idx = index(views = mapOf("welcome" to "/tmp/welcome.prism.html"))
        assertTrue(AlmasixCodeActionPlanner.forUnknownSymbol(idx, SymbolKind.VIEW, "welcome").isEmpty())
        assertTrue(AlmasixCodeActionPlanner.forUnknownSymbol(idx, SymbolKind.VIEW, "").isEmpty())
        assertTrue(AlmasixCodeActionPlanner.forUnknownSymbol(idx, SymbolKind.ROUTE, "home").isEmpty())
        assertTrue(AlmasixCodeActionPlanner.forUnknownSymbol(idx, SymbolKind.CONFIG, "app.env").isEmpty())
    }

    @Test
    fun componentAndMiddlewareActions() {
        val actions = AlmasixCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.COMPONENT, "alert")
        assertTrue(actions.any { it.createPath?.contains("components/alert.prism.html") == true })
        assertTrue(actions.any { it.smithArgs?.startsWith("make:component") == true })

        val mw = AlmasixCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.MIDDLEWARE, "throttle")
        assertEquals(listOf("make:middleware throttle"), mw.map { it.smithArgs })

        assertTrue(
            AlmasixCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.CONTROLLER_ACTION, "Foo@index")
                .any { it.smithArgs!!.startsWith("make:controller") },
        )
        assertTrue(
            AlmasixCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.MAILER, "Welcome")
                .any { it.smithArgs == "make:mail Welcome" },
        )
        assertTrue(AlmasixCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.INERTIA, "Dash").isEmpty())
        assertTrue(AlmasixCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.ENV, "X").isEmpty())
        assertTrue(AlmasixCodeActionPlanner.forUnknownSymbol(index(), SymbolKind.GATE, "edit").isEmpty())
    }

    @Test
    fun stubWriterCreatesOnce() {
        val dir = Files.createTempDirectory("almasix-stub")
        val path = dir.resolve("resources/views/x.prism.html")
        val action = AlmasixCodeActionPlanner.Action(
            id = "create-view-file",
            title = "Create",
            kind = SymbolKind.VIEW,
            name = "x",
            createPath = path.toString(),
        )
        assertTrue(AlmasixStubFileWriter.applyCreateAction(action))
        assertTrue(Files.exists(path))
        assertTrue(Files.readString(path).contains("x"))
        assertFalse(AlmasixStubFileWriter.applyCreateAction(action))
        assertFalse(AlmasixStubFileWriter.applyCreateAction(action.copy(createPath = null)))
    }

    @Test
    fun pathHelpers() {
        val p = AlmasixCodeActionPlanner.viewPathForName("/app", "teams.show")
        assertTrue(p.toString().endsWith("resources/views/teams/show.prism.html"))
        val c = AlmasixCodeActionPlanner.componentPathForName("", "nav.bar")
        assertTrue(c.toString().replace('\\', '/').endsWith("resources/views/components/nav/bar.prism.html"))
    }
}
