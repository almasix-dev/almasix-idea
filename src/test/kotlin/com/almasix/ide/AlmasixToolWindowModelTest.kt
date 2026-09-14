package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmasixToolWindowModelTest {
    private fun index() = AlmasixIndex(
        ok = true,
        views = mapOf("welcome" to "/v"),
        routes = mapOf("home" to AlmasixIndex.RouteEntry("/", listOf("GET"))),
        configKeys = setOf("app.env"),
        components = mapOf("alert" to "/c"),
        envKeys = mapOf("APP_KEY" to AlmasixIndex.EnvEntry()),
        tables = mapOf(
            "users" to AlmasixIndex.TableEntry(
                columns = mapOf("id" to AlmasixIndex.Located()),
            ),
        ),
        gates = setOf("update"),
    )

    @Test
    fun summaryAndStatus() {
        val s = AlmasixToolWindowModel.summary(index())
        assertTrue(s.ok)
        assertEquals(1, s.views)
        assertEquals(1, s.routes)
        assertTrue(AlmasixToolWindowModel.statusLine(s).startsWith("OK —"))
        assertTrue(
            AlmasixToolWindowModel.statusLine(AlmasixToolWindowModel.Summary(false, "boom", 0, 0, 0, 0, 0, 0, 0, 0))
                .contains("boom"),
        )
        assertEquals(
            "Index not ready",
            AlmasixToolWindowModel.statusLine(
                AlmasixToolWindowModel.Summary(false, null, 0, 0, 0, 0, 0, 0, 0, 0),
            ),
        )
    }

    @Test
    fun filtersSymbols() {
        val rows = AlmasixToolWindowModel.symbolRows(index(), "ho")
        assertTrue(rows.any { it.name == "home" })
        assertTrue(rows.none { it.name == "welcome" })
        val all = AlmasixToolWindowModel.symbolRows(index())
        assertTrue(all.size >= 6)
        assertTrue(all.first().kind <= all.last().kind)
    }
}
