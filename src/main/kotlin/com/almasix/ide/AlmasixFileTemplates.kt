package com.almasix.ide

/**
 * Local New File templates for common `smith make:*` targets.
 *
 * Prefer these over spawning smith when the IDE can write a correct stub
 * (controller / model / view / …). Remaining generators still use smith.
 */
object AlmasixFileTemplates {
    data class Spec(
        val relativePath: String,
        val contents: String,
        val openAfterCreate: Boolean = true,
    )

    /**
     * Resolve a template for [generatorId] + [name], or null to fall back to smith.
     */
    fun resolve(generatorId: String, name: String): Spec? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return when (generatorId) {
            "controller" -> controller(trimmed)
            "model" -> model(trimmed)
            "view" -> view(trimmed)
            "component" -> component(trimmed)
            "command" -> command(trimmed)
            "middleware" -> middleware(trimmed)
            "job" -> job(trimmed)
            "seeder" -> seeder(trimmed)
            "factory" -> factory(trimmed)
            "test" -> test(trimmed)
            "migration" -> migration(trimmed)
            else -> null
        }
    }

    fun snake(name: String): String {
        val base = name.substringAfterLast('.').substringAfterLast('/')
            .removeSuffix("Controller")
            .removeSuffix("Middleware")
            .removeSuffix("Seeder")
            .removeSuffix("Factory")
            .removeSuffix("Test")
            .removeSuffix("Job")
            .removeSuffix("Command")
        return base
            .replace(Regex("""([a-z0-9])([A-Z])"""), "$1_$2")
            .replace('.', '_')
            .replace('/', '_')
            .lowercase()
            .trim('_')
    }

    fun className(name: String): String {
        val base = name.substringAfterLast('.').substringAfterLast('/')
        if (base.any { it.isLowerCase() } && base.any { it.isUpperCase() }) return base
        return snake(base).split('_').joinToString("") { part ->
            part.replaceFirstChar { ch -> ch.uppercase() }
        }
    }

    private fun controller(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Controller")) it else "${it}Controller" }
        val file = snake(cls) + ".py"
        return Spec(
            relativePath = "app/http/controllers/$file",
            contents = """
                from almasix.http import Controller


                class $cls(Controller):
                    async def index(self):
                        return {"ok": True}
            """.trimIndent() + "\n",
        )
    }

    private fun model(name: String): Spec {
        val cls = className(name)
        val file = snake(cls) + ".py"
        return Spec(
            relativePath = "app/models/$file",
            contents = """
                from almasix.orm import Model


                class $cls(Model):
                    fillable: list[str] = []
            """.trimIndent() + "\n",
        )
    }

    private fun view(name: String): Spec {
        val dotted = name.replace('/', '.')
        val rel = dotted.replace('.', '/') + ".prism.html"
        return Spec(
            relativePath = "resources/views/$rel",
            contents = AlmasixCodeActionPlanner.stubPrismContent(dotted),
        )
    }

    private fun component(name: String): Spec {
        val dotted = name.removePrefix("components.").replace('/', '.')
        val rel = dotted.replace('.', '/') + ".prism.html"
        return Spec(
            relativePath = "resources/views/components/$rel",
            contents = AlmasixCodeActionPlanner.stubPrismContent(dotted),
        )
    }

    private fun command(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Command")) it else "${it}Command" }
        val file = snake(cls.removeSuffix("Command")) + "_command.py"
        val signature = snake(cls.removeSuffix("Command")).replace('_', ':')
        return Spec(
            relativePath = "app/console/commands/$file",
            contents = """
                from almasix.console import Command


                class $cls(Command):
                    signature = "$signature"
                    description = ""

                    async def handle(self) -> int:
                        self.info("ok")
                        return 0
            """.trimIndent() + "\n",
        )
    }

    private fun middleware(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Middleware")) it else "${it}Middleware" }
        val file = snake(cls) + ".py"
        return Spec(
            relativePath = "app/http/middleware/$file",
            contents = """
                from almasix.http import Middleware, Request, Response


                class $cls(Middleware):
                    async def handle(self, request: Request, next):
                        return await next(request)
            """.trimIndent() + "\n",
        )
    }

    private fun job(name: String): Spec {
        val cls = className(name)
        val file = snake(cls) + ".py"
        return Spec(
            relativePath = "app/jobs/$file",
            contents = """
                from almasix.queue import Job


                class $cls(Job):
                    async def handle(self) -> None:
                        pass
            """.trimIndent() + "\n",
        )
    }

    private fun seeder(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Seeder")) it else "${it}Seeder" }
        val file = snake(cls) + ".py"
        return Spec(
            relativePath = "database/seeders/$file",
            contents = """
                from almasix.database import Seeder


                class $cls(Seeder):
                    async def run(self) -> None:
                        pass
            """.trimIndent() + "\n",
        )
    }

    private fun factory(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Factory")) it else "${it}Factory" }
        val model = cls.removeSuffix("Factory")
        val file = snake(cls) + ".py"
        return Spec(
            relativePath = "database/factories/$file",
            contents = """
                from almasix.database import Factory


                class $cls(Factory):
                    model = $model

                    def definition(self) -> dict:
                        return {}
            """.trimIndent() + "\n",
        )
    }

    private fun test(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Test")) it else "${it}Test" }
        val file = "test_" + snake(cls.removeSuffix("Test")) + ".py"
        return Spec(
            relativePath = "tests/feature/$file",
            contents = """
                def test_${snake(cls.removeSuffix("Test"))}() -> None:
                    assert True
            """.trimIndent() + "\n",
        )
    }

    private fun migration(name: String): Spec {
        val snakeName = snake(name)
        val file = "0001_01_01_000000_${snakeName}.py"
        return Spec(
            relativePath = "database/migrations/$file",
            contents = """
                from almasix.orm import Schema


                async def up() -> None:
                    pass


                async def down() -> None:
                    pass
            """.trimIndent() + "\n",
        )
    }
}
