package com.almasix.ide

/**
 * Offline stubs that mirror almasix console stub files for when smith
 * cannot run. Prefer [AlmasixSmithRunner] for real scaffolding.
 */
object AlmasixFileTemplates {
    data class Spec(
        val relativePath: String,
        val contents: String,
        val openAfterCreate: Boolean = true,
    )

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

    private fun pyDoc(summary: String): String = "\"\"\"$summary\"\"\""

    private fun lines(vararg rows: String): String =
        rows.joinToString("\n", postfix = "\n")

    private fun controller(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Controller")) it else "${it}Controller" }
        return Spec(
            relativePath = "app/http/controllers/${snake(cls)}.py",
            contents = lines(
                pyDoc("$cls."),
                "",
                "from __future__ import annotations",
                "",
                "from almasix.http import Controller",
                "",
                "",
                "class $cls(Controller):",
                "    ${pyDoc("$cls.")}",
                "",
                "    async def index(self) -> dict[str, str]:",
                "        # Web routes return html(...); api routes return dict / list.",
                "        return {\"controller\": \"$cls\"}",
            ),
        )
    }

    private fun model(name: String): Spec {
        val cls = className(name)
        return Spec(
            relativePath = "app/models/${snake(cls)}.py",
            contents = lines(
                pyDoc("$cls model."),
                "",
                "from __future__ import annotations",
                "",
                "from almasix.orm import HasFactory, Model",
                "",
                "",
                "class $cls(HasFactory, Model):",
                "    ${pyDoc("$cls model.")}",
                "",
                "    fillable: tuple[str, ...] = ()",
            ),
        )
    }

    private fun view(name: String): Spec {
        val dotted = name.replace('/', '.')
        val rel = dotted.replace('.', '/') + ".prism.html"
        val open = "{{" + "--"
        val close = "--}}"
        return Spec(
            relativePath = "resources/views/$rel",
            contents = lines(
                "$open $dotted $close",
                "<div>",
                "  $open Markup goes here. $close",
                "</div>",
            ),
        )
    }

    private fun component(name: String): Spec {
        val dotted = name.removePrefix("components.").replace('/', '.')
        val rel = dotted.replace('.', '/') + ".prism.html"
        val open = "{{" + "--"
        val close = "--}}"
        return Spec(
            relativePath = "resources/views/components/$rel",
            contents = lines(
                "@props({})",
                "$open $dotted — anonymous Prism component $close",
                "<div {{ attributes }}>",
                "  {{ slot }}",
                "</div>",
            ),
        )
    }

    private fun command(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Command")) it else "${it}Command" }
        val stem = snake(cls.removeSuffix("Command"))
        val signature = stem.replace('_', ':')
        return Spec(
            relativePath = "app/console/commands/${stem}_command.py",
            contents = lines(
                pyDoc("$cls."),
                "",
                "from __future__ import annotations",
                "",
                "from almasix.console import Command",
                "",
                "",
                "class $cls(Command):",
                "    signature = \"$signature\"",
                "    description = \"$cls\"",
                "",
                "    def handle(self) -> int:",
                "        self.info(\"$cls running\")",
                "        return 0",
            ),
        )
    }

    private fun middleware(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Middleware")) it else "${it}Middleware" }
        return Spec(
            relativePath = "app/http/middleware/${snake(cls)}.py",
            contents = lines(
                pyDoc("$cls."),
                "",
                "from __future__ import annotations",
                "",
                "from collections.abc import Awaitable, Callable",
                "from typing import Any",
                "",
                "from almasix.http import Middleware, Request",
                "",
                "",
                "class $cls(Middleware):",
                "    ${pyDoc("$cls.")}",
                "",
                "    async def handle(",
                "        self,",
                "        request: Request,",
                "        call_next: Callable[[Request], Awaitable[Any]],",
                "    ) -> Any:",
                "        return await call_next(request)",
            ),
        )
    }

    private fun job(name: String): Spec {
        val cls = className(name)
        return Spec(
            relativePath = "app/jobs/${snake(cls)}.py",
            contents = lines(
                pyDoc("$cls job."),
                "",
                "from __future__ import annotations",
                "",
                "from almasix.queue import Job, ShouldQueue",
                "",
                "",
                "class $cls(ShouldQueue, Job):",
                "    ${pyDoc("Queued job — await $cls.dispatch() pushes it to the queue.")}",
                "",
                "    tries = 1",
                "",
                "    async def handle(self) -> None:",
                "        ${pyDoc("Run the job.")}",
            ),
        )
    }

    private fun seeder(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Seeder")) it else "${it}Seeder" }
        return Spec(
            relativePath = "database/seeders/${snake(cls)}.py",
            contents = lines(
                pyDoc("$cls."),
                "",
                "from __future__ import annotations",
                "",
                "from almasix.orm import Seeder",
                "",
                "",
                "class $cls(Seeder):",
                "    ${pyDoc("$cls.")}",
                "",
                "    async def run(self) -> None:",
                "        ${pyDoc("Seed the application's database.")}",
            ),
        )
    }

    private fun factory(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Factory")) it else "${it}Factory" }
        val model = cls.removeSuffix("Factory")
        val module = "app.models.${snake(model)}"
        return Spec(
            relativePath = "database/factories/${snake(cls)}.py",
            contents = lines(
                pyDoc("$cls."),
                "",
                "from __future__ import annotations",
                "",
                "from typing import Any",
                "",
                "from almasix.orm import Factory",
                "",
                "from $module import $model",
                "",
                "",
                "class $cls(Factory):",
                "    ${pyDoc("Builds $model rows for seeders and tests.")}",
                "",
                "    model = $model",
                "",
                "    def definition(self) -> dict[str, Any]:",
                "        ${pyDoc("The attributes a fresh $model starts from.")}",
                "        return {",
                "            \"name\": self.fake.name(),",
                "        }",
            ),
        )
    }

    private fun test(name: String): Spec {
        val cls = className(name).let { if (it.endsWith("Test")) it else "${it}Test" }
        return Spec(
            relativePath = "tests/feature/test_${snake(cls.removeSuffix("Test"))}.py",
            contents = lines(
                pyDoc("$cls — a feature test: the application, through HTTP."),
                "",
                "from __future__ import annotations",
                "",
                "from almasix.testing import TestCase",
                "",
                "",
                "class $cls(TestCase):",
                "    ${pyDoc("Exercise a route the way a browser would.")}",
                "",
                "    async def test_it_answers(self) -> None:",
                "        response = await self.get(\"/\")",
                "",
                "        response.assert_ok()",
            ),
        )
    }

    private fun migration(name: String): Spec {
        val snakeName = snake(name)
        val table = snakeName
            .removePrefix("create_")
            .removeSuffix("_table")
            .ifBlank { snakeName }
        val cls = className(name)
        return Spec(
            relativePath = "database/migrations/0001_01_01_000000_${snakeName}.py",
            contents = lines(
                pyDoc("Create the $table table."),
                "",
                "from __future__ import annotations",
                "",
                "from almasix.orm import Blueprint, Migration, Schema",
                "",
                "",
                "class $cls(Migration):",
                "    ${pyDoc("$cls.")}",
                "",
                "    async def up(self) -> None:",
                "        def define(table: Blueprint) -> None:",
                "            table.id()",
                "            table.timestamps()",
                "",
                "        await Schema.create(\"$table\", define)",
                "",
                "    async def down(self) -> None:",
                "        await Schema.drop_if_exists(\"$table\")",
            ),
        )
    }
}
