package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSiteDetectorTest {
    @Test
    fun routeCall() {
        val site = CallSiteDetector.detect("""route("hom""")
        assertNotNull(site)
        assertEquals(SymbolKind.ROUTE, site!!.kind)
        assertEquals("hom", site.prefix)
    }

    @Test
    fun viewCall() {
        val site = CallSiteDetector.detect("""view('welcome""")
        assertEquals(SymbolKind.VIEW, site!!.kind)
        assertEquals("welcome", site.prefix)
    }

    @Test
    fun configCall() {
        val site = CallSiteDetector.detect("""config("app.na""")
        assertEquals(SymbolKind.CONFIG, site!!.kind)
        assertEquals("app.na", site.prefix)
    }

    @Test
    fun gateCan() {
        val site = CallSiteDetector.detect("""can("edit-""")
        assertEquals(SymbolKind.GATE, site!!.kind)
    }

    @Test
    fun withRelation() {
        val site = CallSiteDetector.detect("""Post.with_("comm""")
        assertEquals(SymbolKind.RELATION, site!!.kind)
        assertEquals("comm", site.prefix)
    }

    @Test
    fun validationRule() {
        val site = CallSiteDetector.detect("""validate({"email": "requ""")
        assertEquals(SymbolKind.VALIDATION, site!!.kind)
        assertEquals("requ", site.prefix)
    }

    @Test
    fun prismInclude() {
        val site = CallSiteDetector.detect("""@include('layouts.""")
        assertEquals(SymbolKind.VIEW, site!!.kind)
    }

    @Test
    fun prismComponent() {
        val site = CallSiteDetector.detect("""<x-alert.""")
        assertEquals(SymbolKind.COMPONENT, site!!.kind)
        assertEquals("alert.", site.prefix)
    }

    @Test
    fun prismDirective() {
        val site = CallSiteDetector.detect("""  @en""")
        assertEquals(SymbolKind.DIRECTIVE, site!!.kind)
        assertEquals("en", site.prefix)
    }

    @Test
    fun envCall() {
        val site = CallSiteDetector.detect("""env("APP_""")
        assertEquals(SymbolKind.ENV, site!!.kind)
    }

    @Test
    fun templateVarEcho() {
        val site = CallSiteDetector.detect("""{{ tit""")
        assertEquals(SymbolKind.TEMPLATE_VAR, site!!.kind)
        assertEquals("tit", site.prefix)
    }

    @Test
    fun dotenvBareKey() {
        val site = CallSiteDetector.detect("QUEUE_CON", dotenvFile = true)
        assertEquals(SymbolKind.ENV, site!!.kind)
        assertEquals("QUEUE_CON", site.prefix)
    }

    @Test
    fun dotenvValueOptions() {
        val site = CallSiteDetector.detect("QUEUE_CONNECTION=re", dotenvFile = true)
        assertEquals(SymbolKind.ENV_VALUE, site!!.kind)
        assertEquals("QUEUE_CONNECTION", site.receiver)
        assertEquals("re", site.prefix)
    }

    @Test
    fun envDefaultSecondArg() {
        val site = CallSiteDetector.detect("""env("QUEUE_CONNECTION", "sy""")
        assertEquals(SymbolKind.ENV_VALUE, site!!.kind)
        assertEquals("QUEUE_CONNECTION", site.receiver)
        assertEquals("sy", site.prefix)
    }
}

class AlmasixSymbolLocatorTest {
    @Test
    fun configLiteralHit() {
        val text = """x = config("app.env")"""
        val offset = text.indexOf("env") + 1
        val hit = AlmasixSymbolLocator.hitAt(text, offset)
        assertNotNull(hit)
        assertEquals(SymbolKind.CONFIG, hit!!.kind)
        assertEquals("app.env", hit.name)
    }

    @Test
    fun templateVarHit() {
        val text = """Hello {{ title }} world"""
        val offset = text.indexOf("title") + 2
        val hit = AlmasixSymbolLocator.hitAt(text, offset)
        assertNotNull(hit)
        assertEquals(SymbolKind.TEMPLATE_VAR, hit!!.kind)
        assertEquals("title", hit.name)
    }
}

class AlmasixIndexParseTest {
    @Test
    fun parsesFixture() {
        val json = """
            {
              "base_path": "/tmp/app",
              "ok": true,
              "error": null,
              "views": {"welcome": "/tmp/app/resources/views/welcome.prism.html"},
              "routes": {
                "home": {"name": "home", "uri": "/", "methods": ["GET"], "path": "/tmp/app/routes/web.py", "line": 12}
              },
              "config_keys": ["app.name", "app.env"],
              "config_files": {"app": "/tmp/app/config/app.py"},
              "config_locations": {
                "app.env": {"path": "/tmp/app/config/app.py", "line": 17},
                "app.name": {"path": "/tmp/app/config/app.py", "line": 16}
              },
              "translation_keys": ["messages.hello"],
              "middleware_aliases": ["web", "auth"],
              "env_keys": {
                "APP_KEY": {
                  "name": "APP_KEY",
                  "path": "/tmp/app/.env",
                  "line": 3,
                  "kind": "env",
                  "detail": "Set in .env",
                  "used_by": ["config/app.py:22"]
                }
              },
              "env_options": {
                "QUEUE_CONNECTION": ["database", "redis", "sync"],
                "QUEUE_DRIVER": ["database", "redis", "sync"]
              },
              "tables": {
                "users": {
                  "name": "users",
                  "path": "/tmp/app/database/migrations/0001_users.py",
                  "line": 5,
                  "columns": {
                    "id": {"name": "id", "path": "/tmp/app/database/migrations/0001_users.py", "line": 6},
                    "email": {"name": "email", "path": "/tmp/app/database/migrations/0001_users.py", "line": 7}
                  },
                  "detail": "users"
                }
              },
              "model_metadata": {
                "User": {
                  "module": "user",
                  "path": "/tmp/app/app/models/user.py",
                  "fillable": ["email"],
                  "casts": {"id": "int"},
                  "relations": ["posts"],
                  "relation_lines": {"posts": 42}
                }
              },
              "relations": {"User": ["posts"]},
              "casts": ["int", "datetime"],
              "components": {"alert": "/tmp/x"},
              "gates": ["update"],
              "disks": ["local"],
              "queues": ["sync"],
              "caches": ["file"],
              "mailers": ["smtp"],
              "inertia_pages": ["Dashboard"],
              "smith_commands": ["serve", "ide:index"],
              "validation_rules": ["required", "email"],
              "directives": ["if", "endif"],
              "view_helpers": [{"name": "auth", "path": "/tmp/helpers.py", "line": 9, "kind": "helper"}],
              "view_shared": {
                "csrf_token": {"name": "csrf_token", "path": "/tmp/auth.py", "line": 5, "kind": "shared"}
              },
              "view_data": {
                "welcome": {
                  "title": {
                    "name": "title",
                    "kind": "data",
                    "path": "/tmp/app/app/http/controllers/welcome_controller.py",
                    "line": 22
                  }
                }
              },
              "vite_entries": {"resources/js/app.js": "/tmp/app.js"},
              "controller_actions": {"WelcomeController": ["index"]}
            }
        """.trimIndent()
        val index = AlmasixIndexLoader.parse(json)
        assertTrue(index.ok)
        assertTrue(index.views.containsKey("welcome"))
        assertEquals("/", index.routes["home"]!!.uri)
        assertEquals("/tmp/app/routes/web.py", index.routes["home"]!!.path)
        assertEquals(12, index.routes["home"]!!.line)
        assertTrue(index.configKeys.contains("app.name"))
        assertEquals("/tmp/app/config/app.py", index.configFiles["app"])
        assertEquals(17, index.configLocations["app.env"]!!.line)
        assertTrue(index.tables["users"]!!.columns.containsKey("email"))
        assertEquals(listOf("posts"), index.relations["User"])
        assertEquals(42, index.modelMetadata["User"]!!.relationLines["posts"])
        assertTrue(index.validationRules.contains("required"))
        assertTrue(index.known(SymbolKind.ROUTE, "home"))
        assertTrue(!index.known(SymbolKind.ROUTE, "missing"))
        assertEquals("welcome", index.viewNameForPath("/tmp/app/resources/views/welcome.prism.html"))

        val site = CallSiteDetector.Site(SymbolKind.ROUTE, "ho")
        val items = AlmasixCompletionContributor.symbolsFor(index, site)
        assertTrue(items.any { it.first == "home" })

        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app/routes/web.py", 12),
            AlmasixSymbolResolver.resolve(index, SymbolKind.ROUTE, "home"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app/resources/views/welcome.prism.html", 0),
            AlmasixSymbolResolver.resolve(index, SymbolKind.VIEW, "welcome"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app/config/app.py", 17),
            AlmasixSymbolResolver.resolve(index, SymbolKind.CONFIG, "app.env"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/x", 0),
            AlmasixSymbolResolver.resolve(index, SymbolKind.COMPONENT, "alert"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app/.env", 3),
            AlmasixSymbolResolver.resolve(index, SymbolKind.ENV, "APP_KEY"),
        )
        assertEquals(listOf("database", "redis", "sync"), index.optionsForEnvKey("QUEUE_CONNECTION"))
        assertEquals(listOf("database", "redis", "sync"), index.optionsForEnvKey("QUEUE_DRIVER"))
        val envSite = CallSiteDetector.Site(SymbolKind.ENV_VALUE, "re", receiver = "QUEUE_CONNECTION")
        val envItems = AlmasixCompletionContributor.symbolsFor(index, envSite)
        assertTrue(envItems.any { it.first == "redis" })
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app/database/migrations/0001_users.py", 7),
            AlmasixSymbolResolver.resolveColumn(index, "users", "email"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app/app/models/user.py", 42),
            AlmasixSymbolResolver.resolve(index, SymbolKind.RELATION, "posts"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target(
                "/tmp/app/app/http/controllers/welcome_controller.py",
                22,
            ),
            AlmasixSymbolResolver.resolve(
                index,
                SymbolKind.TEMPLATE_VAR,
                "title",
                viewName = "welcome",
            ),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/auth.py", 5),
            AlmasixSymbolResolver.resolve(index, SymbolKind.TEMPLATE_VAR, "csrf_token"),
        )
    }

    @Test
    fun configFallbackScansFile() {
        val dir = java.nio.file.Files.createTempDirectory("almasix-config")
        val file = dir.resolve("app.py")
        java.nio.file.Files.writeString(
            file,
            """
            config = {
                "name": "x",
                "env": "local",
            }
            """.trimIndent(),
        )
        val line = AlmasixSymbolResolver.locateNestedKeyLine(file.toString(), listOf("env"))
        assertEquals(2, line)
    }
}
