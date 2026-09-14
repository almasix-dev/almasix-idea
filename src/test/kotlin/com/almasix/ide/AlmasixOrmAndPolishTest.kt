package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmasixModelResolverTest {
    private fun index(): AlmasixIndex {
        val users = AlmasixIndex.TableEntry(
            columns = mapOf(
                "id" to AlmasixIndex.Located("/m.py", 1),
                "email" to AlmasixIndex.Located("/m.py", 2),
                "name" to AlmasixIndex.Located("/m.py", 3),
                "password" to AlmasixIndex.Located("/m.py", 4),
            ),
            model = "User",
            detail = "users",
        )
        return AlmasixIndex(
            ok = true,
            tables = mapOf("users" to users),
            modelMetadata = mapOf(
                "User" to AlmasixIndex.ModelEntry(
                    fillable = listOf("email", "name"),
                    guarded = listOf("password"),
                    hidden = listOf("remember_token"),
                    casts = mapOf("email_verified_at" to "datetime"),
                    module = "user",
                    path = "/user.py",
                ),
            ),
            relations = mapOf("User" to listOf("posts")),
            casts = setOf("datetime", "int", "bool"),
        )
    }

    @Test
    fun resolvesUserTableAndColumns() {
        val idx = index()
        assertEquals("users", AlmasixModelResolver.resolveTable(idx, "User"))
        assertEquals("users", AlmasixModelResolver.resolveTable(idx, "users"))
        assertTrue(AlmasixModelResolver.columnsFor(idx, "User").containsAll(listOf("email", "name", "id")))
        assertEquals("User", AlmasixModelResolver.authUserModel(idx))
        assertEquals("users", AlmasixModelResolver.resolveTable(idx, AlmasixModelResolver.AUTH_USER_SENTINEL))
    }

    @Test
    fun infersFromAssignmentAndAuthHeuristic() {
        val idx = index()
        assertEquals(
            "User",
            AlmasixModelResolver.inferModel(idx, "author = User.find(1)\n", "author"),
        )
        assertEquals(
            "User",
            AlmasixModelResolver.inferModel(idx, "def show(user: User):\n    ", "user"),
        )
        assertEquals(
            "User",
            AlmasixModelResolver.inferModel(idx, "x = 1\n", "user"),
        )
        assertEquals(
            "User",
            AlmasixModelResolver.inferModel(idx, "", AlmasixModelResolver.AUTH_USER_SENTINEL),
        )
    }

    @Test
    fun pluralize() {
        assertEquals("users", AlmasixModelResolver.pluralize("user"))
        assertEquals("companies", AlmasixModelResolver.pluralize("company"))
        assertEquals("boxes", AlmasixModelResolver.pluralize("box"))
    }
}

class AlmasixOrmCompletionTest {
    private fun index() = AlmasixIndex(
        ok = true,
        tables = mapOf(
            "users" to AlmasixIndex.TableEntry(
                columns = mapOf(
                    "email" to AlmasixIndex.Located(),
                    "name" to AlmasixIndex.Located(),
                    "password" to AlmasixIndex.Located(),
                ),
                model = "User",
            ),
        ),
        modelMetadata = mapOf(
            "User" to AlmasixIndex.ModelEntry(
                fillable = listOf("email", "name"),
                guarded = listOf("password"),
                module = "user",
                path = "/u.py",
            ),
        ),
        casts = setOf("datetime", "int"),
        relations = mapOf("User" to listOf("posts")),
    )

    @Test
    fun ormWhereAndAuthUserAttrs() {
        val idx = index()
        val where = CallSiteDetector.detect("""User.where("ema""")!!
        assertEquals(SymbolKind.COLUMN, where.kind)
        assertEquals("User", where.receiver)
        val cols = AlmasixCompletionCatalog.symbolsFor(idx, where, """User.where("ema""")
        assertTrue(cols.any { it.first == "email" })

        val auth = CallSiteDetector.detect("auth().user().ema")!!
        assertEquals(SymbolKind.ATTR, auth.kind)
        assertEquals(AlmasixModelResolver.AUTH_USER_SENTINEL, auth.receiver)
        val attrs = AlmasixCompletionCatalog.symbolsFor(idx, auth, "auth().user().ema")
        assertTrue(attrs.any { it.first == "email" })

        val req = CallSiteDetector.detect("request.user().na")!!
        assertEquals(SymbolKind.ATTR, req.kind)
        assertTrue(
            AlmasixCompletionCatalog.symbolsFor(idx, req, "request.user().na")
                .any { it.first == "name" },
        )
    }

    @Test
    fun fillableGuardedCastsKeysAndValues() {
        val idx = index()
        val modelSrc = """
            class User(Model):
                fillable = ["ema
        """.trimIndent()
        val fill = CallSiteDetector.detect(modelSrc)!!
        assertEquals(SymbolKind.MODEL_ATTR, fill.kind)
        assertTrue(
            AlmasixCompletionCatalog.symbolsFor(idx, fill, modelSrc).any { it.first == "email" },
        )

        val guardedSrc = """
            class User(Model):
                guarded = ["pass
        """.trimIndent()
        val g = CallSiteDetector.detect(guardedSrc)!!
        assertEquals(SymbolKind.MODEL_ATTR, g.kind)
        assertTrue(
            AlmasixCompletionCatalog.symbolsFor(idx, g, guardedSrc).any { it.first == "password" },
        )

        val castKey = CallSiteDetector.detect("""casts = {"ema""")!!
        assertEquals(SymbolKind.MODEL_ATTR, castKey.kind)

        val castVal = CallSiteDetector.detect("""casts = {"email": "dat""")!!
        assertEquals(SymbolKind.CAST, castVal.kind)
        assertTrue(
            AlmasixCompletionCatalog.symbolsFor(idx, castVal).any { it.first == "datetime" },
        )
    }

    @Test
    fun moreColumnMethods() {
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.pluck("ema""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.order_by("na""")!!.kind)
        assertEquals(SymbolKind.COLUMN, CallSiteDetector.detect("""User.only("ema""")!!.kind)
        assertEquals(SymbolKind.RELATION, CallSiteDetector.detect("""User.with_("pos""")!!.kind)
    }
}

class AlmasixRefactorPlannerTest {
    @Test
    fun extractPartial() {
        val plan = AlmasixRefactorPlanner.extractPartial(
            "/app",
            "<div>card</div>",
            10,
            25,
            "posts._card",
        )
        assertTrue(plan.isAllowed)
        assertTrue(plan.createPath!!.endsWith("posts/_card.prism.html"))
        assertEquals("@include('posts._card')", plan.edits.single().newText)
        val out = AlmasixRefactorPlanner.applyToText(
            "BEFORE<div>card</div>AFTER",
            listOf(AlmasixRefactorPlanner.Edit(6, 21, "@include('posts._card')")),
        )
        assertTrue(out.contains("@include"))
    }

    @Test
    fun coverageMopUpBranches() {
        // ModelResolver: metadata-only columns, auth fallbacks, module match
        val metaOnly = AlmasixIndex(
            ok = true,
            modelMetadata = mapOf(
                "Author" to AlmasixIndex.ModelEntry(
                    fillable = listOf("bio"),
                    guarded = listOf("secret"),
                    hidden = listOf("token"),
                    casts = mapOf("born_at" to "date"),
                    module = "author",
                ),
            ),
        )
        assertTrue(AlmasixModelResolver.columnsFor(metaOnly, "Author").contains("bio"))
        assertTrue(AlmasixModelResolver.columnsFor(metaOnly, "author").contains("secret"))
        assertEquals("User", AlmasixModelResolver.authUserModel(metaOnly))
        assertTrue(AlmasixModelResolver.columnsFor(metaOnly, null).isEmpty())
        // Unknown hint → empty (never dump every DB column)
        assertTrue(AlmasixModelResolver.columnsFor(AlmasixIndex(ok = true), "Ghost").isEmpty())
        // Blueprint `table.` must not offer schema columns
        assertTrue(AlmasixModelResolver.columnsFor(metaOnly, "table").isEmpty())
        val tableAttr = CallSiteDetector.detect("table.ema")
        assertEquals(SymbolKind.ATTR, tableAttr!!.kind)
        assertTrue(
            AlmasixCompletionCatalog.symbolsFor(
                AlmasixIndex(
                    ok = true,
                    tables = mapOf(
                        "users" to AlmasixIndex.TableEntry(
                            columns = mapOf("email" to AlmasixIndex.Located()),
                        ),
                    ),
                ),
                tableAttr,
            ).isEmpty(),
        )
        // user heuristic → authUserModel
        assertEquals(
            "User",
            AlmasixModelResolver.inferModel(
                AlmasixIndex(ok = true, modelMetadata = mapOf("User" to AlmasixIndex.ModelEntry())),
                "",
                "user",
            ),
        )
        // table.model match via metadata class
        val people = AlmasixIndex(
            ok = true,
            tables = mapOf(
                "people" to AlmasixIndex.TableEntry(
                    model = "Author",
                    columns = mapOf("bio" to AlmasixIndex.Located()),
                ),
            ),
            modelMetadata = mapOf("Author" to AlmasixIndex.ModelEntry(module = "author")),
        )
        assertEquals("people", AlmasixModelResolver.resolveTable(people, "Author"))
        assertTrue(AlmasixModelResolver.columnsFor(people, "Author").contains("bio"))

        assertEquals(
            "<x-nav.bar />",
            AlmasixRefactorPlanner.includeToComponent("@include('nav.bar')", 3)
                .edits.single().newText,
        )
        assertTrue(AlmasixRefactorPlanner.includeToComponent("@extends('x')", 0).refusal != null)

        val userMeta = AlmasixIndex(
            ok = true,
            modelMetadata = mapOf("user" to AlmasixIndex.ModelEntry(module = "user")),
        )
        assertEquals("user", AlmasixModelResolver.authUserModel(userMeta))

        val tagged = AlmasixIndex(
            ok = true,
            tables = mapOf(
                "people" to AlmasixIndex.TableEntry(model = "Author", columns = mapOf("id" to AlmasixIndex.Located())),
            ),
            modelMetadata = mapOf("Author" to AlmasixIndex.ModelEntry(module = "author")),
        )
        assertEquals("people", AlmasixModelResolver.resolveTable(tagged, "author"))

        // CompletionCatalog relation via module + auth sentinel
        val withRel = AlmasixIndex(
            ok = true,
            tables = mapOf("users" to AlmasixIndex.TableEntry(model = "User", columns = mapOf("email" to AlmasixIndex.Located()))),
            modelMetadata = mapOf("User" to AlmasixIndex.ModelEntry(relations = listOf("posts"), module = "user")),
            relations = mapOf("User" to listOf("posts")),
        )
        assertTrue(
            AlmasixCompletionCatalog.relationsFor(withRel, AlmasixModelResolver.AUTH_USER_SENTINEL)
                .contains("posts"),
        )
        assertTrue(AlmasixCompletionCatalog.relationsFor(withRel, "user").contains("posts"))
        val colSite = CallSiteDetector.Site(SymbolKind.COLUMN, "", receiver = "users")
        assertTrue(
            AlmasixCompletionCatalog.symbolsFor(withRel, colSite).any {
                it.second.contains("column · users")
            },
        )

        // Refactor refusals
        assertTrue(
            AlmasixRefactorPlanner.extractPartial("/a", "x", 0, 1, "bad name")
                .refusal!!.contains("Invalid"),
        )
        assertFalse(AlmasixRefactorPlanner.includeToComponent("@include('')", 2).isAllowed)
        assertEquals(
            "Missing name",
            AlmasixRefactorPlanner.includeToComponent("@include('')", 2).refusal,
        )

        val withUserClass = AlmasixIndex(
            ok = true,
            modelMetadata = mapOf("User" to AlmasixIndex.ModelEntry()),
        )
        assertEquals("User", AlmasixModelResolver.authUserModel(withUserClass))

        // Chained redirect().route + ATTR simple recv
        assertEquals(SymbolKind.ROUTE, CallSiteDetector.detect("""redirect().route("hom""")!!.kind)
        assertEquals(SymbolKind.ATTR, CallSiteDetector.detect("user.ema")!!.kind)
        assertEquals(SymbolKind.VIEW, CallSiteDetector.detect("""@each('par""")!!.kind)
        assertNull(CallSiteDetector.detect("route.something"))

        // SymbolResolver relation via module path + view helper target
        val idx = AlmasixIndex(
            ok = true,
            basePath = "/app",
            modelMetadata = mapOf(
                "User" to AlmasixIndex.ModelEntry(
                    relations = listOf("posts"),
                    relationLines = mapOf("posts" to 9),
                    module = "user",
                    path = "/user.py",
                ),
            ),
            viewHelpers = mapOf("auth" to AlmasixIndex.ViewVarEntry(path = "/h.py", line = 2)),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/user.py", 9),
            AlmasixSymbolResolver.resolve(idx, SymbolKind.RELATION, "posts", receiver = "user"),
        )
        assertEquals(
            AlmasixSymbolResolver.Target("/h.py", 2),
            AlmasixSymbolResolver.resolve(idx, SymbolKind.TEMPLATE_VAR, "auth"),
        )
        assertEquals(0, AlmasixSymbolResolver.locateNestedKeyLine("/nope", listOf("a", "b")))
    }
}
