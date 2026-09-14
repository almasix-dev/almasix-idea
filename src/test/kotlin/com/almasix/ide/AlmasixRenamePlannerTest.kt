package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class AlmasixRenamePlannerTest {
    @Test
    fun rewritesRouteCallSites() {
        val text = """
            return redirect().route("home")
            if route_is("home"):
                pass
            other = route("dashboard")
        """.trimIndent()
        val occ = AlmasixCallSiteSearcher.findInText(text, SymbolKind.ROUTE, "home")
        val plan = AlmasixRenamePlanner.planFromOccurrences(
            SymbolKind.ROUTE, "home", "welcome", occ,
        )
        assertTrue(plan.isAllowed)
        assertEquals(2, plan.edits.size)
        val out = AlmasixRenamePlanner.applyToText(text, plan.edits)
        assertTrue(out.contains("""route("welcome")"""))
        assertTrue(out.contains("""route_is("welcome")"""))
        assertTrue(out.contains("""route("dashboard")"""))
        assertFalse(out.contains("""route("home")"""))
    }

    @Test
    fun rewritesViewAndComponent() {
        val text = """
            return view("auth.login", {})
            @include('auth.login')
            <x-alert type="error"/>
            @component('alert')
        """.trimIndent()
        val viewPlan = AlmasixRenamePlanner.planFromOccurrences(
            SymbolKind.VIEW,
            "auth.login",
            "auth.signin",
            AlmasixCallSiteSearcher.findInText(text, SymbolKind.VIEW, "auth.login"),
        )
        assertTrue(viewPlan.isAllowed)
        val afterView = AlmasixRenamePlanner.applyToText(text, viewPlan.edits)
        assertTrue(afterView.contains("""view("auth.signin""""))
        assertTrue(afterView.contains("""@include('auth.signin')"""))

        val compPlan = AlmasixRenamePlanner.planFromOccurrences(
            SymbolKind.COMPONENT,
            "alert",
            "notice",
            AlmasixCallSiteSearcher.findInText(afterView, SymbolKind.COMPONENT, "alert"),
        )
        assertTrue(compPlan.isAllowed)
        val afterComp = AlmasixRenamePlanner.applyToText(afterView, compPlan.edits)
        assertTrue(afterComp.contains("<x-notice"))
        assertTrue(afterComp.contains("@component('notice')"))
    }

    @Test
    fun rewritesConfigAndEnv() {
        val py = """
            env = config("app.env")
            key = env("APP_KEY")
        """.trimIndent()
        val cfg = AlmasixRenamePlanner.planFromOccurrences(
            SymbolKind.CONFIG,
            "app.env",
            "app.environment",
            AlmasixCallSiteSearcher.findInText(py, SymbolKind.CONFIG, "app.env"),
        )
        assertTrue(cfg.isAllowed)
        assertTrue(
            AlmasixRenamePlanner.applyToText(py, cfg.edits)
                .contains("""config("app.environment")"""),
        )

        val dotenv = "APP_KEY=secret\nTITLE=\${APP_KEY}\n"
        val envPlan = AlmasixRenamePlanner.planFromOccurrences(
            SymbolKind.ENV,
            "APP_KEY",
            "APP_SECRET",
            AlmasixCallSiteSearcher.findInText(dotenv, SymbolKind.ENV, "APP_KEY", dotenvFile = true),
        )
        assertTrue(envPlan.isAllowed)
        val out = AlmasixRenamePlanner.applyToText(dotenv, envPlan.edits)
        assertTrue(out.contains("APP_SECRET=secret"))
        assertTrue(out.contains("\${APP_SECRET}"))
    }

    @Test
    fun validatesNames() {
        assertNull(AlmasixRenamePlanner.validateNewName(SymbolKind.ROUTE, "teams.show"))
        assertEquals(
            "Name cannot be empty",
            AlmasixRenamePlanner.validateNewName(SymbolKind.ROUTE, ""),
        )
        assertTrue(
            AlmasixRenamePlanner.validateNewName(SymbolKind.ENV, "app_key") != null,
        )
        assertTrue(
            AlmasixRenamePlanner.validateNewName(SymbolKind.COLUMN, "id") != null,
        )
    }

    @Test
    fun refusesUnsupportedOrEmpty() {
        val empty = AlmasixRenamePlanner.planFromOccurrences(
            SymbolKind.ROUTE, "home", "welcome", emptyList(),
        )
        assertFalse(empty.isAllowed)
        assertEquals("No usages found", empty.refusal)

        val bad = AlmasixRenamePlanner.planFromOccurrences(
            SymbolKind.ROUTE,
            "home",
            "bad name",
            listOf(
                AlmasixCallSiteSearcher.Occurrence(
                    Path.of("x.py"),
                    com.intellij.openapi.util.TextRange(0, 4),
                    SymbolKind.ROUTE,
                    "home",
                ),
            ),
        )
        assertFalse(bad.isAllowed)
    }

    @Test
    fun rewriteTextConvenience() {
        val text = """return route("home")"""
        val plan = AlmasixRenamePlanner.rewriteText(text, SymbolKind.ROUTE, "home", "dash")
        assertTrue(plan.isAllowed)
        assertEquals(1, plan.edits.size)
        assertEquals("""return route("dash")""", plan.edits.single().newText)
    }
}
