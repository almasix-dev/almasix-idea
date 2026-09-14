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
    fun modelSmithArgsMatchScaffolderFlags() {
        assertEquals(
            "make:model Post",
            AlmasixMakeCatalog.modelSmithArgs("Post", AlmasixMakeCatalog.ModelOptions()),
        )
        assertEquals(
            "make:model Post -a",
            AlmasixMakeCatalog.modelSmithArgs("Post", AlmasixMakeCatalog.ModelOptions(all = true)),
        )
        assertEquals(
            "make:model Post -m -f -s",
            AlmasixMakeCatalog.modelSmithArgs(
                "Post",
                AlmasixMakeCatalog.ModelOptions(migration = true, factory = true, seed = true),
            ),
        )
        assertEquals(
            "make:model Post -r --api --policy -R",
            AlmasixMakeCatalog.modelSmithArgs(
                "Post",
                AlmasixMakeCatalog.ModelOptions(
                    resource = true, api = true, policy = true, requests = true,
                ),
            ),
        )
        assertTrue(AlmasixMakeCatalog.byId("model")!!.interactive)
    }

    @Test
    fun offlineModelStubMatchesScaffolder() {
        val spec = AlmasixFileTemplates.resolve("model", "Author")!!
        assertTrue(spec.contents.contains("HasFactory"))
        assertTrue(spec.contents.contains("fillable: tuple[str, ...] = ()"))
        assertTrue(spec.contents.contains("class Author(HasFactory, Model)"))
    }

    @Test
    fun commandsAreMakePrefixed() {
        assertTrue(AlmasixMakeCatalog.ALL.all { it.command.startsWith("make:") })
        assertTrue(AlmasixMakeCatalog.ALL.all { it.id.isNotBlank() && it.label.isNotBlank() })
    }
}
