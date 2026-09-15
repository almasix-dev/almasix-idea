package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmasixHoverDocsTest {
    private fun index() = AlmasixIndex(
        ok = true,
        views = mapOf("welcome" to "/v.prism.html"),
        routes = mapOf("home" to AlmasixIndex.RouteEntry("/", listOf("GET"), "/r.py", 3)),
        configKeys = setOf("app.env"),
        configLocations = mapOf("app.env" to AlmasixIndex.Located("/c.py", 1)),
        envKeys = mapOf("APP_KEY" to AlmasixIndex.EnvEntry(path = "/.env", line = 2, detail = "secret")),
        components = mapOf("alert" to "/a.prism.html"),
        relations = mapOf("User" to listOf("posts")),
        tables = mapOf(
            "users" to AlmasixIndex.TableEntry(columns = mapOf("email" to AlmasixIndex.Located())),
        ),
        viewHelpers = mapOf("auth" to AlmasixIndex.ViewVarEntry()),
    )

    @Test
    fun symbolHovers() {
        val idx = index()
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.ROUTE, "home")!!.contains("GET"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.ROUTE, "nope")!!.contains("Unknown"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.VIEW, "welcome")!!.contains("/v"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.VIEW, "x")!!.contains("Not found"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.CONFIG, "app.env")!!.contains("indexed"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.ENV, "APP_KEY")!!.contains("secret"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.COMPONENT, "alert")!!.contains("/a"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.RELATION, "posts", "User")!!.contains("relation"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.COLUMN, "email", "users")!!.contains("column"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.TEMPLATE_VAR, "auth")!!.contains("template"))
        assertNull(AlmasixHoverDocs.forSymbol(idx, SymbolKind.ROUTE, ""))
        assertNotNull(AlmasixHoverDocs.directive("if"))
        assertNull(AlmasixHoverDocs.directive("not-a-directive"))
    }
}

class AlmasixPrismStructureTest {
    @Test
    fun balancedIf() {
        assertTrue(AlmasixPrismStructure.analyze("@if(true)\nx\n@endif").isEmpty())
    }

    @Test
    fun unclosedIf() {
        val issues = AlmasixPrismStructure.analyze("@if(true)\nx")
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("Unclosed"))
    }

    @Test
    fun unexpectedClose() {
        val issues = AlmasixPrismStructure.analyze("@endif")
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("Unexpected"))
    }

    @Test
    fun mismatchedClose() {
        val issues = AlmasixPrismStructure.analyze("@if(1)\n@endforeach")
        assertTrue(issues.any { it.message.contains("Expected") })
    }

    @Test
    fun inlineSectionNeedsNoEnd() {
        assertTrue(AlmasixPrismStructure.analyze("@section('title', 'The title')").isEmpty())
        assertTrue(AlmasixPrismStructure.analyze("@section(\"title\", \"Hello, world\")").isEmpty())
        assertTrue(
            AlmasixPrismStructure.analyze("@extends('layouts.app')\n@section('title', 'Hi')\n")
                .isEmpty(),
        )
    }

    @Test
    fun blockSectionStillNeedsEnd() {
        val issues = AlmasixPrismStructure.analyze("@section('title')\nHi")
        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("Unclosed @section"))
        assertTrue(
            AlmasixPrismStructure.analyze("@section('title')\nHi\n@endsection").isEmpty(),
        )
    }

    @Test
    fun showClosesSection() {
        assertTrue(
            AlmasixPrismStructure.analyze("@section('content')\n<body>\n@show").isEmpty(),
        )
    }
}

class AlmasixArticulateHelpersTest {
    @Test
    fun eagerLoadAndWhere() {
        val index = AlmasixIndex(
            ok = true,
            relations = mapOf("User" to listOf("posts", "profile")),
            tables = mapOf(
                "users" to AlmasixIndex.TableEntry(
                    columns = mapOf("email" to AlmasixIndex.Located(), "name" to AlmasixIndex.Located()),
                ),
            ),
        )
        val eager = AlmasixArticulateHelpers.eagerLoadSnippets(index, "User")
        assertTrue(eager.any { it.template.contains("with_(\"posts\")") })
        assertTrue(eager.any { it.template.contains("load(\"profile\")") })
        assertTrue(AlmasixArticulateHelpers.eagerLoadSnippets(index, null).isEmpty().not() ||
            AlmasixArticulateHelpers.eagerLoadSnippets(index, "Missing").isEmpty())

        val where = AlmasixArticulateHelpers.whereColumnSnippets(index, "users")
        assertTrue(where.any { it.template.contains("where(\"email\"") })
        assertTrue(
            AlmasixArticulateHelpers.relationMethodStub("posts", "Post")
                .contains("has_many(Post)"),
        )
    }

    @Test
    fun emptyWhenNoRelations() {
        assertTrue(AlmasixArticulateHelpers.eagerLoadSnippets(AlmasixIndex(ok = true), "User").isEmpty())
    }
}

class AlmasixHoverEdgeTest {
    @Test
    fun hoverUnknownAndDirectiveBranches() {
        val idx = AlmasixIndex(ok = true, gates = setOf("update"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.RELATION, "nope")!!.contains("Unknown"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.COLUMN, "nope")!!.contains("Unknown"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.TEMPLATE_VAR, "nope")!!.contains("Unknown"))
        assertNotNull(AlmasixHoverDocs.forSymbol(idx, SymbolKind.DIRECTIVE, "if"))
        assertTrue(AlmasixHoverDocs.forSymbol(idx, SymbolKind.GATE, "update")!!.contains("gate"))
        assertNull(AlmasixHoverDocs.forSymbol(idx, SymbolKind.GATE, "missing"))
        assertEquals(
            AlmasixArticulateHelpers.relationMethodStub("posts"),
            AlmasixArticulateHelpers.relationMethodStub("posts", "Related"),
        )
    }
}
