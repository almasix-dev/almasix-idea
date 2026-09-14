package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmasixMakeCatalogTest {
    @Test
    fun coversCoreMakeCommands() {
        val ids = AlmasixMakeCatalog.ALL.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("controller", "model", "migration", "view", "component", "test")))
        assertTrue(AlmasixMakeCatalog.ALL.size >= 25)
    }

    @Test
    fun formatsSmithArgs() {
        val controller = AlmasixMakeCatalog.byId("controller")
        assertNotNull(controller)
        assertEquals("make:controller PostController", controller!!.smithArgs("PostController"))
        assertEquals("make:controller", controller.smithArgs("  "))
        assertEquals("New Controller…", AlmasixMakeCatalog.menuLabel(controller))
    }

    @Test
    fun commandsAreMakePrefixed() {
        assertTrue(AlmasixMakeCatalog.ALL.all { it.command.startsWith("make:") })
        assertTrue(AlmasixMakeCatalog.ALL.all { it.id.isNotBlank() && it.label.isNotBlank() })
    }
}
