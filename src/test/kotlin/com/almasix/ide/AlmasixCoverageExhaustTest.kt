package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Exhaustive unit coverage for index helpers, resolver, locator, catalog, and
 * filesystem call-site search — drives the 98% Kover gate.
 */
class AlmasixCoverageExhaustTest {
    private fun sampleIndex(): AlmasixIndex {
        val json = """
            {
              "base_path": "/tmp/app",
              "ok": true,
              "views": {
                "welcome": "/tmp/app/resources/views/welcome.prism.html",
                "components.alert": "/tmp/app/resources/views/components/alert.prism.html"
              },
              "routes": {
                "home": {"uri": "/", "methods": ["GET"], "path": "/tmp/app/routes/web.py", "line": 12}
              },
              "config_keys": ["app.name", "app.env"],
              "config_files": {"app": "/tmp/app/config/app.py"},
              "config_locations": {
                "app.env": {"path": "/tmp/app/config/app.py", "line": 17}
              },
              "translation_keys": ["messages.hello"],
              "middleware_aliases": ["web"],
              "env_keys": {
                "APP_KEY": {
                  "path": null, "line": 0, "kind": "config", "detail": "",
                  "used_by": ["config/app.py:22"]
                },
                "APP_URL": {"path": "/tmp/app/.env", "line": 4, "kind": "env", "detail": "url"}
              },
              "env_options": {
                "CACHE_STORE": ["file", "redis"],
                "BROADCAST_CONNECTION": ["log", "redis"]
              },
              "tables": {
                "users": {
                  "path": "/tmp/mig.py", "line": 1,
                  "columns": {"id": {"path": "/tmp/mig.py", "line": 2}, "email": {"path": "/tmp/mig.py", "line": 3}},
                  "detail": "users"
                }
              },
              "model_metadata": {
                "User": {
                  "module": "user", "path": "/tmp/user.py",
                  "fillable": ["email"], "casts": {"id": "int"},
                  "relations": ["posts"], "relation_lines": {"posts": 10}
                }
              },
              "relations": {"User": ["posts"]},
              "casts": ["int"],
              "components": {"alert": "/tmp/alert.prism.html"},
              "gates": ["update"],
              "disks": ["local"],
              "queues": ["sync"],
              "caches": ["file"],
              "mailers": ["smtp"],
              "inertia_pages": ["Dashboard"],
              "smith_commands": ["serve"],
              "validation_rules": ["required", "email"],
              "directives": ["if"],
              "view_helpers": [{"name": "auth", "path": "/tmp/h.py", "line": 1, "kind": "helper"}],
              "view_shared": {"csrf": "token"},
              "view_data": {
                "welcome": {"title": {"path": "/tmp/c.py", "line": 5, "kind": "data"}}
              },
              "vite_entries": {"resources/js/app.js": "/tmp/app.js"},
              "controller_actions": {"WelcomeController": ["index"]}
            }
        """.trimIndent()
        return AlmasixIndexLoader.parse(json)
    }

    @Test
    fun knownCoversAllKinds() {
        val index = sampleIndex()
        assertTrue(index.known(SymbolKind.ROUTE, "home"))
        assertTrue(index.known(SymbolKind.VIEW, "welcome"))
        assertTrue(index.known(SymbolKind.CONFIG, "app.env"))
        assertTrue(index.known(SymbolKind.TRANSLATION, "messages.hello"))
        assertTrue(index.known(SymbolKind.MIDDLEWARE, "web"))
        assertTrue(index.known(SymbolKind.ENV, "APP_KEY"))
        assertTrue(index.known(SymbolKind.ENV_VALUE, "anything"))
        assertTrue(index.known(SymbolKind.TABLE, "users"))
        assertTrue(index.known(SymbolKind.GATE, "update"))
        assertTrue(index.known(SymbolKind.COMPONENT, "alert"))
        assertTrue(index.known(SymbolKind.VALIDATION, "required"))
        assertTrue(index.known(SymbolKind.VALIDATION, "email:rfc"))
        assertTrue(index.known(SymbolKind.DISK, "local"))
        assertTrue(index.known(SymbolKind.QUEUE, "sync"))
        assertTrue(index.known(SymbolKind.CACHE, "file"))
        assertTrue(index.known(SymbolKind.MAILER, "smtp"))
        assertTrue(index.known(SymbolKind.INERTIA, "Dashboard"))
        assertTrue(index.known(SymbolKind.SMITH, "serve"))
        assertTrue(index.known(SymbolKind.VITE, "resources/js/app.js"))
        assertTrue(index.known(SymbolKind.CAST, "int"))
        assertTrue(index.known(SymbolKind.TEMPLATE_VAR, "title"))
        assertTrue(index.known(SymbolKind.COLUMN, "email")) // soft-known
        assertTrue(index.known(SymbolKind.CONTROLLER_ACTION, "WelcomeController"))
        assertTrue(index.known(SymbolKind.CONTROLLER_ACTION, "WelcomeController@index"))
        assertFalse(index.known(SymbolKind.CONTROLLER_ACTION, "Missing@index"))
        assertFalse(index.known(SymbolKind.ROUTE, "missing"))
        assertFalse(index.known(SymbolKind.TRANSLATION, "nope"))
        assertEquals(AlmasixIndex.empty("x").error, "x")
    }

    @Test
    fun envOptionsAliasesAndViewPath() {
        val index = sampleIndex()
        assertEquals(listOf("file", "redis"), index.optionsForEnvKey("CACHE_STORE"))
        assertEquals(listOf("file", "redis"), index.optionsForEnvKey("CACHE_DRIVER"))
        assertEquals(listOf("log", "redis"), index.optionsForEnvKey("BROADCAST_DRIVER"))
        assertTrue(index.optionsForEnvKey("UNKNOWN").isEmpty())
        assertEquals("welcome", index.viewNameForPath("/tmp/app/resources/views/welcome.prism.html"))
        assertEquals("welcome", index.viewNameForPath("resources/views/welcome.prism.html"))
        assertNull(index.viewNameForPath(""))
        assertNull(index.viewNameForPath("/nope"))
    }

    @Test
    fun resolverBranches() {
        val index = sampleIndex()
        assertNull(AlmasixSymbolResolver.resolve(index, SymbolKind.ROUTE, ""))
        assertNull(AlmasixSymbolResolver.resolve(index, SymbolKind.ENV_VALUE, "x"))
        assertNull(AlmasixSymbolResolver.resolve(index, SymbolKind.GATE, "update"))
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app/config/app.py", 21),
            AlmasixSymbolResolver.resolve(index, SymbolKind.ENV, "APP_KEY"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app/.env", 4),
            AlmasixSymbolResolver.resolve(index, SymbolKind.ENV, "APP_URL"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/mig.py", 1),
            AlmasixSymbolResolver.resolve(index, SymbolKind.TABLE, "users"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/mig.py", 3),
            AlmasixSymbolResolver.resolveColumn(index, "user", "email"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/mig.py", 3),
            AlmasixSymbolResolver.resolveColumn(index, null, "email"),
        )
        assertNull(AlmasixSymbolResolver.resolveColumn(index, null, ""))
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/alert.prism.html", 0),
            AlmasixSymbolResolver.resolve(index, SymbolKind.COMPONENT, "alert"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app.js", 0),
            AlmasixSymbolResolver.resolve(index, SymbolKind.VITE, "resources/js/app.js"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/user.py", 10),
            AlmasixSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", receiver = "User"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/user.py", 10),
            AlmasixSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", receiver = "user"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/h.py", 1),
            AlmasixSymbolResolver.resolve(index, SymbolKind.TEMPLATE_VAR, "auth"),
        )
        // config stem-only via files map when locations miss
        val thin = index.copy(configLocations = emptyMap())
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/app/config/app.py", 0),
            AlmasixSymbolResolver.resolve(thin, SymbolKind.CONFIG, "app"),
        )
    }

    @Test
    fun completionCatalogAllKinds() {
        val index = sampleIndex()
        fun kind(k: SymbolKind, recv: String? = null) =
            AlmasixCompletionCatalog.symbolsFor(index, CallSiteDetector.Site(k, "", receiver = recv))

        assertTrue(kind(SymbolKind.ROUTE).any { it.first == "home" })
        assertTrue(kind(SymbolKind.VIEW).any { it.first == "welcome" })
        assertTrue(kind(SymbolKind.CONFIG).any { it.first == "app.env" })
        assertTrue(kind(SymbolKind.TRANSLATION).any { it.first == "messages.hello" })
        assertTrue(kind(SymbolKind.MIDDLEWARE).any { it.first == "web" })
        assertTrue(kind(SymbolKind.ENV).any { it.first == "APP_KEY" })
        assertTrue(kind(SymbolKind.ENV_VALUE, "CACHE_STORE").any { it.first == "redis" })
        assertTrue(kind(SymbolKind.ENV_VALUE).isEmpty())
        assertTrue(kind(SymbolKind.TABLE).any { it.first == "users" })
        assertTrue(kind(SymbolKind.COLUMN, "users").any { it.first == "email" && it.second.contains("column") })
        assertTrue(kind(SymbolKind.COLUMN).any { it.first == "email" })
        assertTrue(kind(SymbolKind.RELATION, "User").contains("posts" to "relation"))
        assertTrue(kind(SymbolKind.RELATION).contains("posts" to "relation"))
        assertTrue(kind(SymbolKind.CAST).contains("int" to "cast"))
        assertTrue(kind(SymbolKind.GATE).contains("update" to "gate"))
        assertTrue(kind(SymbolKind.COMPONENT).contains("alert" to "component"))
        assertTrue(kind(SymbolKind.VALIDATION).contains("required" to "rule"))
        assertTrue(kind(SymbolKind.DISK).contains("local" to "disk"))
        assertTrue(kind(SymbolKind.QUEUE).contains("sync" to "queue"))
        assertTrue(kind(SymbolKind.CACHE).contains("file" to "cache"))
        assertTrue(kind(SymbolKind.MAILER).contains("smtp" to "mailer"))
        assertTrue(kind(SymbolKind.INERTIA).contains("Dashboard" to "inertia"))
        assertTrue(kind(SymbolKind.SMITH).contains("serve" to "smith"))
        assertTrue(kind(SymbolKind.VITE).any { it.second == "asset" })
        assertTrue(kind(SymbolKind.DIRECTIVE).contains("if" to "directive"))
        assertTrue(kind(SymbolKind.TEMPLATE_VAR).any { it.first == "title" })
        assertTrue(kind(SymbolKind.CONTROLLER_ACTION).contains("index" to "action"))
        assertTrue(AlmasixCompletionCatalog.columnsFor(index, "User").contains("email"))
        assertTrue(AlmasixCompletionCatalog.relationsFor(index, "User").contains("posts"))
    }

    @Test
    fun locatorAndDetectorExtras() {
        assertNull(AlmasixSymbolLocator.hitAt("abc", -1))
        assertNull(AlmasixSymbolLocator.hitAt("nope", 2))
        val raw = """{!! body !!}"""
        val hit = AlmasixSymbolLocator.hitAt(raw, raw.indexOf("body") + 1)
        assertNotNull(hit)
        assertEquals(SymbolKind.TEMPLATE_VAR, hit!!.kind)

        assertEquals(SymbolKind.COMPONENT, CallSiteDetector.detect("""@component('al""")!!.kind)
        assertEquals(SymbolKind.ROUTE, CallSiteDetector.detect("""@route("hom""")!!.kind)
        assertEquals(SymbolKind.TRANSLATION, CallSiteDetector.detect("""@lang('msg""")!!.kind)
        assertEquals(SymbolKind.CAST, CallSiteDetector.detect("""casts = {"x": "dat""")!!.kind)
        assertEquals(SymbolKind.CONTROLLER_ACTION, CallSiteDetector.detect("""[WelcomeController, "ind""")!!.kind)
        assertEquals(SymbolKind.SMITH, CallSiteDetector.detect("""Smith.call("ser""")!!.kind)
        assertEquals(SymbolKind.VITE, CallSiteDetector.detect("""vite("resources""")!!.kind)
        assertEquals(SymbolKind.INERTIA, CallSiteDetector.detect("""render("Dash""")!!.kind)
        assertTrue(CallSiteDetector.detect("""validate({"x": "required|em""")!!.validationSegment)
        assertNull(CallSiteDetector.detect("# APP_KEY", dotenvFile = true))
        assertEquals(
            SymbolKind.ENV,
            CallSiteDetector.detect("export APP_KE", dotenvFile = true)!!.kind,
        )
    }

    @Test
    fun findUsagesWalksAppTree() {
        val root = Files.createTempDirectory("almasix-fu")
        Files.createDirectories(root.resolve("routes"))
        Files.createDirectories(root.resolve("resources/views"))
        Files.writeString(
            root.resolve("routes/web.py"),
            """return route("home")\n""",
        )
        Files.writeString(
            root.resolve("resources/views/welcome.prism.html"),
            """@include('auth.login')\n<x-alert/>\n""",
        )
        Files.writeString(root.resolve(".env"), "APP_KEY=secret\n")
        // skipped subtree
        Files.createDirectories(root.resolve("routes/node_modules"))
        Files.writeString(root.resolve("routes/node_modules/x.py"), """route("home")""")

        val routes = AlmasixCallSiteSearcher.findUsages(root, SymbolKind.ROUTE, "home")
        assertEquals(1, routes.size)
        val views = AlmasixCallSiteSearcher.findUsages(root, SymbolKind.VIEW, "auth.login")
        assertEquals(1, views.size)
        val comps = AlmasixCallSiteSearcher.findUsages(root, SymbolKind.COMPONENT, "alert")
        assertEquals(1, comps.size)
        val envs = AlmasixCallSiteSearcher.findUsages(root, SymbolKind.ENV, "APP_KEY")
        assertTrue(envs.isNotEmpty())
        assertTrue(AlmasixCallSiteSearcher.findUsages(root, SymbolKind.ROUTE, "").isEmpty())
        assertTrue(AlmasixCallSiteSearcher.findUsages(root, SymbolKind.GATE, "x").isEmpty())
    }

    @Test
    fun indexCommandAndEmptyParse() {
        val root = Files.createTempDirectory("almasix-cmd")
        val cmd = AlmasixIndexLoader.buildIndexCommand(root)
        assertTrue(cmd.commandLineString.contains("ide:index") || cmd.commandLineString.contains("almasix.ide"))

        val venv = root.resolve(".venv/bin")
        Files.createDirectories(venv)
        val smith = venv.resolve("smith")
        Files.writeString(smith, "#!/bin/sh\n")
        smith.toFile().setExecutable(true)
        assertTrue(AlmasixIndexLoader.buildIndexCommand(root).exePath.contains("smith"))

        val bad = AlmasixIndexLoader.parse("""{"ok": false, "error": "boom"}""")
        assertFalse(bad.ok)
        assertEquals("boom", bad.error)
    }

    @Test
    fun detectorRemainingBranches() {
        // Non-dotenv ${…} interpolation
        assertEquals(SymbolKind.ENV, CallSiteDetector.detect("x=\${APP_")!!.kind)
        assertEquals(SymbolKind.GATE, CallSiteDetector.detect("""@can("upd""")!!.kind)
        assertEquals(SymbolKind.GATE, CallSiteDetector.detect("""@cannot("upd""")!!.kind)
        assertEquals(SymbolKind.VITE, CallSiteDetector.detect("""@asset("js/""")!!.kind)
        assertEquals(SymbolKind.VITE, CallSiteDetector.detect("""@vite("res""")!!.kind)
        assertEquals(SymbolKind.VIEW, CallSiteDetector.detect("""@includeUnless('x""")!!.kind)
        assertEquals(SymbolKind.TRANSLATION, CallSiteDetector.detect("""__("msg""")!!.kind)
        assertEquals(SymbolKind.TRANSLATION, CallSiteDetector.detect("""trans("msg""")!!.kind)
        assertEquals(SymbolKind.MIDDLEWARE, CallSiteDetector.detect("""middleware("web""")!!.kind)
        assertEquals(SymbolKind.DISK, CallSiteDetector.detect("""disk("loc""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.where("ema""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.order_by("cre""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.select("id""")!!.kind)
        assertEquals(SymbolKind.TABLE, CallSiteDetector.detect("""DB.table("use""")!!.kind)
        assertNull(CallSiteDetector.detect("plain text here"))
        // Quoted dotenv values → stripDotenvValuePrefix
        assertEquals("re", CallSiteDetector.detect("APP_KEY=\"re", dotenvFile = true)!!.prefix)
        assertEquals("", CallSiteDetector.detect("APP_KEY='", dotenvFile = true)!!.prefix)
        assertEquals("red", CallSiteDetector.detect("APP_KEY=\"red\"", dotenvFile = true)!!.prefix)
        assertEquals("red", CallSiteDetector.detect("APP_KEY=\"red", dotenvFile = true)!!.prefix)
    }

    @Test
    fun resolverRemainingBranches() {
        val index = sampleIndex()
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/mig.py", 3),
            AlmasixSymbolResolver.resolve(index, SymbolKind.COLUMN, "email", receiver = "users"),
        )
        // component via views["components.$name"] when components map misses
        val viaView = index.copy(components = emptyMap())
        assertEquals(
            AlmasixSymbolResolver.Target(
                "/tmp/app/resources/views/components/alert.prism.html",
                0,
            ),
            AlmasixSymbolResolver.resolve(viaView, SymbolKind.COMPONENT, "alert"),
        )
        assertNull(AlmasixSymbolResolver.resolve(viaView.copy(views = emptyMap()), SymbolKind.COMPONENT, "alert"))
        // plural strip: userses? tableHint posts → post
        val tables = index.tables + mapOf(
            "post" to AlmasixIndex.TableEntry(
                columns = mapOf("title" to AlmasixIndex.Located("/tmp/p.py", 1)),
                path = "/tmp/p.py",
            ),
        )
        val withPost = index.copy(tables = tables)
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/p.py", 1),
            AlmasixSymbolResolver.resolveColumn(withPost, "posts", "title"),
        )
        assertNull(AlmasixSymbolResolver.resolveColumn(index, null, "missing_col"))
        // config nested scan fallback
        val dir = Files.createTempDirectory("cfg")
        val file = dir.resolve("app.py")
        Files.writeString(file, """{"name": "x", "env": "local"}""")
        val cfg = index.copy(
            configLocations = emptyMap(),
            configFiles = mapOf("app" to file.toString()),
        )
        val t = AlmasixSymbolResolver.resolve(cfg, SymbolKind.CONFIG, "app.env")
        assertNotNull(t)
        assertEquals(file.toString(), t!!.path)
        // relation via module match
        assertEquals(
            AlmasixSymbolResolver.Target("/tmp/user.py", 10),
            AlmasixSymbolResolver.resolve(index, SymbolKind.RELATION, "posts", receiver = "app.models.user"),
        )
        // template var with null path skipped
        val noPath = index.copy(
            viewData = mapOf(
                "welcome" to mapOf("title" to AlmasixIndex.ViewVarEntry(path = null, line = 1)),
            ),
            viewShared = emptyMap(),
            viewHelpers = emptyMap(),
        )
        assertNull(AlmasixSymbolResolver.resolve(noPath, SymbolKind.TEMPLATE_VAR, "title", viewName = "welcome"))
        // locateNestedKeyLine missing file / empty segments
        assertEquals(0, AlmasixSymbolResolver.locateNestedKeyLine("/no/such/file.py", listOf("a")))
        assertEquals(0, AlmasixSymbolResolver.locateNestedKeyLine(file.toString(), emptyList()))
        // ENV usedBy without basePath
        val noBase = index.copy(basePath = "")
        val env = AlmasixSymbolResolver.resolve(noBase, SymbolKind.ENV, "APP_KEY")
        assertNotNull(env)
        assertTrue(env!!.path.contains("config/app.py"))
    }

    @Test
    fun renameAndCatalogRemaining() {
        assertTrue(
            AlmasixRenamePlanner.validateNewName(SymbolKind.VIEW, "bad name")!!.contains("View"),
        )
        assertTrue(
            AlmasixRenamePlanner.validateNewName(SymbolKind.CONFIG, "bad name")!!.contains("Config"),
        )
        assertEquals(
            "Name unchanged",
            AlmasixRenamePlanner.planFromOccurrences(
                SymbolKind.ROUTE,
                "a",
                "a",
                AlmasixCallSiteSearcher.findInText("""route("a")""", SymbolKind.ROUTE, "a"),
            ).refusal,
        )
        assertTrue(
            AlmasixRenamePlanner.rewriteText("""route("a")""", SymbolKind.ROUTE, "a", "bad name")
                .refusal != null,
        )
        assertTrue(AlmasixRenamePlanner.Plan(SymbolKind.ROUTE, "a", "b", emptyList()).isEmpty)
        try {
            AlmasixRenamePlanner.applyToText("ab", listOf(AlmasixRenamePlanner.Edit("", 0, 5, "x")))
            assertTrue("expected failure", false)
        } catch (_: IllegalArgumentException) {
        }
        val index = sampleIndex()
        // columnsFor via model fillable when table missing
        val noTables = index.copy(tables = emptyMap())
        assertTrue(AlmasixCompletionCatalog.columnsFor(noTables, "user").contains("email"))
        assertTrue(AlmasixCompletionCatalog.columnsFor(noTables, "user").contains("id"))
    }

    @Test
    fun locatorDottedAttrAndIndexLoaderBranches() {
        val text = "{{ user.name }}"
        val hit = AlmasixSymbolLocator.hitAt(text, text.indexOf("name") + 1)
        assertNotNull(hit)
        assertEquals("user", hit!!.name)
        assertNull(AlmasixSymbolLocator.hitAt("{{  }}", 3))

        val root = Files.createTempDirectory("idx-cmd")
        val venv = root.resolve(".venv/bin")
        Files.createDirectories(venv)
        val py = venv.resolve("python")
        Files.writeString(py, "#!/bin/sh\n")
        py.toFile().setExecutable(true)
        // python + smith script
        Files.writeString(root.resolve("smith"), "#!/bin/sh\n")
        assertTrue(AlmasixIndexLoader.buildIndexCommand(root).commandLineString.contains("smith"))
        Files.delete(root.resolve("smith"))
        assertTrue(AlmasixIndexLoader.buildIndexCommand(root).commandLineString.contains("almasix.ide"))
        // no venv: smith script only
        Files.delete(py)
        Files.writeString(root.resolve("smith"), "#!/bin/sh\n")
        assertTrue(AlmasixIndexLoader.buildIndexCommand(root).exePath.contains("python3") ||
            AlmasixIndexLoader.buildIndexCommand(root).commandLineString.contains("python3"))

        val objList = AlmasixIndexLoader.parse(
            """{"ok": true, "config_keys": {"app.name": true}, "casts": 1}""",
        )
        assertTrue(objList.configKeys.contains("app.name"))
        assertTrue(objList.casts.isEmpty())
    }
}
