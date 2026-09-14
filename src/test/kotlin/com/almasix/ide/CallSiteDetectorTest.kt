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
                "home": {"name": "home", "uri": "/", "methods": ["GET"], "path": null, "line": 0}
              },
              "config_keys": ["app.name", "app.env"],
              "translation_keys": ["messages.hello"],
              "middleware_aliases": ["web", "auth"],
              "env_keys": {"APP_KEY": {"name": "APP_KEY"}},
              "tables": {
                "users": {
                  "name": "users",
                  "columns": {"id": {"name": "id"}, "email": {"name": "email"}},
                  "detail": "users"
                }
              },
              "model_metadata": {
                "User": {
                  "module": "user",
                  "fillable": ["email"],
                  "casts": {"id": "int"},
                  "relations": ["posts"]
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
              "view_helpers": [{"name": "auth"}],
              "view_shared": {},
              "view_data": {"welcome": {"title": {"name": "title"}}},
              "vite_entries": {"resources/js/app.js": "/tmp/app.js"},
              "controller_actions": {"WelcomeController": ["index"]}
            }
        """.trimIndent()
        val index = AlmasixIndexLoader.parse(json)
        assertTrue(index.ok)
        assertTrue(index.views.contains("welcome"))
        assertEquals("/", index.routes["home"]!!.uri)
        assertTrue(index.configKeys.contains("app.name"))
        assertTrue(index.tables["users"]!!.columns.contains("email"))
        assertEquals(listOf("posts"), index.relations["User"])
        assertTrue(index.validationRules.contains("required"))
        assertTrue(index.known(SymbolKind.ROUTE, "home"))
        assertTrue(!index.known(SymbolKind.ROUTE, "missing"))

        val site = CallSiteDetector.Site(SymbolKind.ROUTE, "ho")
        val items = AlmasixCompletionContributor.symbolsFor(index, site)
        assertTrue(items.any { it.first == "home" })
    }
}
