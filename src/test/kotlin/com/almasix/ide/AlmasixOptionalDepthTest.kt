package com.almasix.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AlmasixOptionalDepthTest {
    private fun index() = AlmasixIndex(
        ok = true,
        basePath = "/app",
        tables = mapOf(
            "authors" to AlmasixIndex.TableEntry(
                columns = mapOf(
                    "id" to AlmasixIndex.Located(),
                    "email" to AlmasixIndex.Located(),
                    "name" to AlmasixIndex.Located(),
                ),
                model = "Author",
            ),
        ),
        modelMetadata = mapOf(
            "Author" to AlmasixIndex.ModelEntry(
                fillable = listOf("email", "name"),
                guarded = listOf("id"),
                module = "author",
                path = "/app/app/models/author.py",
            ),
        ),
        views = mapOf("auth.login" to "/app/resources/views/auth/login.prism.html"),
        configLocations = mapOf(
            "app.env" to AlmasixIndex.Located("/app/config/app.py", 3),
        ),
        configFiles = mapOf("app" to "/app/config/app.py"),
    )

    @Test
    fun strongerInferenceQueryFactoryChains() {
        val idx = index()
        assertEquals(
            "Author",
            AlmasixModelResolver.inferModel(idx, "", "Author.query"),
        )
        assertEquals(
            "Author",
            AlmasixModelResolver.peelModelHint("AuthorFactory"),
        )
        assertEquals(
            "Author",
            AlmasixModelResolver.inferModel(
                idx,
                "author = AuthorFactory().create()\n",
                "author",
            ),
        )
        assertEquals(
            "Author",
            AlmasixModelResolver.inferModel(
                idx,
                "author = Author.factory().create()\n",
                "author",
            ),
        )
        assertEquals(
            "Author",
            AlmasixModelResolver.inferChainHead("""Author.query().where("ema"""),
        )

        val chain = CallSiteDetector.detect("""Author.query().where("ema""")!!
        assertEquals(SymbolKind.COLUMN, chain.kind)
        assertEquals("Author", chain.receiver)
        assertTrue(
            AlmasixCompletionCatalog.symbolsFor(idx, chain, """Author.query().where("ema""")
                .any { it.first == "email" },
        )
    }

    @Test
    fun dictAndKwargColumnCompletion() {
        val idx = index()
        val kw = CallSiteDetector.detect("""Author.create(email=""")!!
        assertEquals(SymbolKind.COLUMN, kw.kind)
        assertEquals("Author", kw.receiver)
        assertEquals("email", kw.prefix)
        assertTrue(
            AlmasixCompletionCatalog.symbolsFor(idx, kw).any { it.first == "email" },
        )

        val partial = CallSiteDetector.detect("""Author.update(na""")!!
        assertEquals(SymbolKind.COLUMN, partial.kind)
        assertEquals("na", partial.prefix)

        val dict = CallSiteDetector.detect("""Author.create({"ema""")!!
        assertEquals(SymbolKind.COLUMN, dict.kind)
        assertTrue(
            AlmasixCompletionCatalog.symbolsFor(idx, dict).any { it.first == "email" },
        )
    }

    @Test
    fun renameMovesViewAndRewritesConfigKey() {
        val move = AlmasixRenamePlanner.planViewFileMove(
            SymbolKind.VIEW,
            "auth.login",
            "auth.signin",
            "/app/resources/views/auth/login.prism.html",
            "/app",
        )
        assertNotNull(move)
        assertTrue(move!!.toPath.replace('\\', '/').endsWith("auth/signin.prism.html"))

        val config = """
            config = {
                "name": "x",
                "env": env("APP_ENV", "local"),
            }
        """.trimIndent()
        val defs = AlmasixRenamePlanner.planConfigKeyDefinition(
            config,
            "/app/config/app.py",
            "app.env",
            "app.environment",
            line = 2,
        )
        assertEquals(1, defs.size)
        val rewritten = AlmasixRenamePlanner.applyToText(config, defs)
        assertTrue(rewritten.contains("\"environment\":"))
        assertFalse(rewritten.contains("\"env\":"))
    }

    @Test
    fun relationStubInsertsIntoModelSource() {
        val src = """
            class Author(Model):
                fillable = ["name"]

                def team(self):
                    return self.belongs_to(Team)
        """.trimIndent()
        val plan = AlmasixRelationStubPlanner.planInsert(src, "posts", "Post")
        assertTrue(plan.isAllowed)
        val out = AlmasixRelationStubPlanner.applyToText(src, plan.edits)
        assertTrue(out.contains("def posts(self):"))
        assertTrue(out.contains("has_many(Post)"))

        assertFalse(AlmasixRelationStubPlanner.planInsert(src, "team", "Team").isAllowed)
        assertFalse(AlmasixRelationStubPlanner.planInsert(src, "", "X").isAllowed)
    }

    @Test
    fun fileTemplatesResolveCommonGenerators() {
        val controller = AlmasixFileTemplates.resolve("controller", "PostController")!!
        assertTrue(controller.relativePath.contains("controllers/"))
        assertTrue(controller.contents.contains("class PostController"))

        val model = AlmasixFileTemplates.resolve("model", "Author")!!
        assertEquals("app/models/author.py", model.relativePath.replace('\\', '/'))
        assertTrue(model.contents.contains("class Author"))

        val view = AlmasixFileTemplates.resolve("view", "posts.index")!!
        assertTrue(view.relativePath.contains("posts/index.prism.html"))

        val component = AlmasixFileTemplates.resolve("component", "components.alert")!!
        assertTrue(component.relativePath.contains("views/components/alert.prism.html"))

        val command = AlmasixFileTemplates.resolve("command", "SendDigest")!!
        assertTrue(command.relativePath.contains("commands/"))
        assertTrue(command.contents.contains("signature"))

        val middleware = AlmasixFileTemplates.resolve("middleware", "EnsureToken")!!
        assertTrue(middleware.contents.contains("EnsureTokenMiddleware"))

        val job = AlmasixFileTemplates.resolve("job", "ProcessPodcast")!!
        assertTrue(job.relativePath.contains("jobs/"))

        val seeder = AlmasixFileTemplates.resolve("seeder", "PostSeeder")!!
        assertTrue(seeder.contents.contains("class PostSeeder"))

        val factory = AlmasixFileTemplates.resolve("factory", "Post")!!
        assertTrue(factory.contents.contains("PostFactory"))
        assertTrue(factory.contents.contains("model = Post"))

        val test = AlmasixFileTemplates.resolve("test", "PostTest")!!
        assertTrue(test.relativePath.contains("tests/feature/"))

        val migration = AlmasixFileTemplates.resolve("migration", "create_notes_table")!!
        assertTrue(migration.relativePath.contains("migrations/"))
        assertTrue(migration.contents.contains("async def up"))

        assertNull(AlmasixFileTemplates.resolve("policy", "PostPolicy"))
        assertNull(AlmasixFileTemplates.resolve("model", "  "))
        assertEquals("post", AlmasixFileTemplates.snake("PostController"))
        assertEquals("PostController", AlmasixFileTemplates.className("PostController"))
        assertEquals("StorePost", AlmasixFileTemplates.className("store_post"))
        assertEquals("Alert", AlmasixFileTemplates.className("alert"))
    }

    @Test
    fun renameAndRelationEdgeBranches() {
        assertNull(
            AlmasixRenamePlanner.planViewFileMove(
                SymbolKind.ROUTE, "a", "b", "/x", "/app",
            ),
        )
        assertNull(
            AlmasixRenamePlanner.planViewFileMove(
                SymbolKind.VIEW, "a", "b", null, "/app",
            ),
        )
        assertNull(
            AlmasixRenamePlanner.planViewFileMove(
                SymbolKind.VIEW,
                "auth.login",
                "auth.login",
                "/app/resources/views/auth/login.prism.html",
                "/app",
            ),
        )
        val compMove = AlmasixRenamePlanner.planViewFileMove(
            SymbolKind.COMPONENT,
            "alert",
            "notice",
            "/app/resources/views/components/alert.prism.html",
            "/app",
        )
        assertNotNull(compMove)
        assertTrue(compMove!!.toPath.contains("notice.prism.html"))

        val sameLeaf = AlmasixRenamePlanner.planConfigKeyDefinition(
            "config = {\"env\": 1}",
            "/c.py",
            "app.env",
            "mail.env",
        )
        assertTrue(sameLeaf.isEmpty())
        val noMatch = AlmasixRenamePlanner.planConfigKeyDefinition(
            "config = {\"name\": 1}",
            "/c.py",
            "app.env",
            "app.environment",
        )
        assertTrue(noMatch.isEmpty())
        val scanAll = AlmasixRenamePlanner.planConfigKeyDefinition(
            "config = {\n    \"env\": 1,\n}\n",
            "/c.py",
            "app.env",
            "app.environment",
            line = -1,
        )
        assertEquals(1, scanAll.size)

        val emptyClass = "x = 1\n"
        val planEof = AlmasixRelationStubPlanner.planInsert(emptyClass, "posts", "Post")
        assertTrue(planEof.isAllowed)
        assertTrue(
            AlmasixRelationStubPlanner.applyToText(emptyClass, planEof.edits)
                .contains("def posts"),
        )

        val withSibling = """
            class Author:
                fillable = ["name"]

            OTHER = 1
        """.trimIndent()
        val planSibling = AlmasixRelationStubPlanner.planInsert(withSibling, "posts", "Post")
        assertTrue(planSibling.isAllowed)
        val siblingOut = AlmasixRelationStubPlanner.applyToText(withSibling, planSibling.edits)
        assertTrue(siblingOut.indexOf("def posts") < siblingOut.indexOf("OTHER"))

        assertNull(AlmasixModelResolver.inferModel(index(), "", "self"))
        assertEquals(
            "Author",
            AlmasixModelResolver.inferModel(index(), "a = await Author.find(1)\n", "a"),
        )
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

        // CHAINED_CALL without continuous query-chain → recover via inferChainHead
        val broken = CallSiteDetector.detect("""Author.query(); return items().where("ema""")!!
        assertEquals(SymbolKind.COLUMN, broken.kind)
        assertEquals("Author", broken.receiver)
    }

    @Test
    fun renamePlanIncludesFileMovesWithoutCallSites() {
        val plan = AlmasixRenamePlanner.planFromOccurrences(
            SymbolKind.VIEW,
            "auth.login",
            "auth.signin",
            emptyList(),
            fileMoves = listOf(
                AlmasixRenamePlanner.FileMove(
                    "/app/resources/views/auth/login.prism.html",
                    "/app/resources/views/auth/signin.prism.html",
                ),
            ),
        )
        assertTrue(plan.isAllowed)
        assertEquals(1, plan.fileMoves.size)
    }

    @Test
    fun stubWriterOverwrite() {
        val dir = Files.createTempDirectory("almasix-stub")
        val path = dir.resolve("x.py")
        AlmasixStubFileWriter.write(path, "one")
        assertEquals("one", Files.readString(path))
        AlmasixStubFileWriter.write(path, "two")
        assertEquals("two", Files.readString(path))
        assertFalse(AlmasixStubFileWriter.writeIfAbsent(path, "three"))
    }
}
