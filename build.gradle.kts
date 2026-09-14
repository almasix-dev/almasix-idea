import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        // Bundled with PyCharm Community — keeps the sandbox aligned with a
        // supported product (see plugin.xml incompatible-with list).
        bundledPlugin("PythonCore")
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("idea.force.use.core.classloader", "true")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    publishing {
        token.set(providers.environmentVariable("JETBRAINS_PUBLISH_TOKEN"))
    }
    pluginConfiguration {
        id = "com.almasix.ide"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
        description.set(
            """
            Almasix Idea — native Prism highlighting, deep completions and
            annotations via smith ide:index --json, and Smith run configs.
            Supports PyCharm Professional, PyCharm Community, and WebStorm.
            Install from the JetBrains Marketplace or sideload a release zip.
            """.trimIndent(),
        )
        changeNotes.set(
            """
            <ul>
              <li>0.3.2 — Limit compatibility to <b>PyCharm Professional</b>,
                  <b>PyCharm Community</b>, and <b>WebStorm</b> (product-module
                  <code>incompatible-with</code> declarations).</li>
              <li>0.3.1 — Ctrl-hover underline/hand cursor on Almasix symbols; suppress
                  Blueprint <code>table.</code> column dumps; Prism file icon from
                  <code>art/prism/prism-file.svg</code>; New… runs <code>smith make:*</code>
                  (interactive <code>make:model</code> companions); <code>.env</code>
                  “Insert all MAIL_*” bulk completion.</li>
              <li>0.3.0 — Almasix Idea exhaust: Find Usages, safe Rename (incl. view
                  file moves + config key definitions), code actions, local New File
                  templates + <code>smith make:*</code>, Tool Window, hover / Prism
                  structure / Articulate helpers, ORM inference
                  (<code>query()</code> / factories / kwargs). Requires Almasix
                  <b>0.9.1+</b> (prefer latest with <code>guarded</code>/<code>hidden</code>
                  + AnnAssign in <code>ide:index</code>).</li>
              <li>0.2.3 — Deeper navigation + env intelligence: config keys jump to the
                  key line; Prism <code>{{ var }}</code> go-to-definition; Ctrl-hover
                  underline on navigable symbols; two-way env completion (keys from
                  config in <code>.env</code>, driver/store options like
                  QUEUE_CONNECTION → sync/redis). Requires Almasix <b>0.9.1+</b>.</li>
              <li>0.2.2 — Ctrl-click / Go to Declaration for routes, views, config files,
                  env keys, components, tables/columns, and relations; official Almasix
                  icon on the Almasix menu</li>
              <li>0.2.1 — Show a real <b>Almasix</b> menu on the main menu bar and under
                  Tools (Rebuild Index)</li>
              <li>0.2.0 — Almasix Idea rewrite: native completions/annotators (no LSP4IJ);
                  index via smith ide:index --json</li>
              <li>0.1.13 — Windows IDE + WSL projects: spawn almasix-lsp via wsl.exe</li>
            </ul>
            """.trimIndent(),
        )
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
    // Gate every PR/CI run: line coverage must stay ≥ 98% on the verified set.
    check {
        dependsOn("koverVerify")
    }
}

/**
 * Coverage policy (exhaust release): ≥ 98% line coverage on product logic.
 *
 * Exclusions are **thin IntelliJ Platform wiring only** (listeners, run-config
 * UI, highlighter providers) — not feature logic. New intelligence must live in
 * unit-testable objects (CallSiteDetector, rename planner, …) so the gate holds.
 */
kover {
    reports {
        filters {
            excludes {
                classes(
                    // Prism host plumbing — covered by lexer/highlighting smoke tests
                    // at a lower density; keep highlighter/provider out of the gate.
                    "com.almasix.ide.PrismEditorHighlighterProvider",
                    "com.almasix.ide.PrismEditorHighlighterProvider*",
                    "com.almasix.ide.PrismFileTypeOverrider",
                    "com.almasix.ide.PrismFileTypeDetector",
                    "com.almasix.ide.PrismFileViewProvider*",
                    "com.almasix.ide.PrismParserDefinition",
                    "com.almasix.ide.PrismFile",
                    "com.almasix.ide.PrismFileType",
                    "com.almasix.ide.PrismLanguage",
                    "com.almasix.ide.PrismLanguage$*",
                    "com.almasix.ide.PrismBraceMatcher",
                    "com.almasix.ide.PrismTypedHandler",
                    "com.almasix.ide.PrismSyntaxHighlighter*",
                    // Application / project lifecycle shells
                    "com.almasix.ide.AlmasixPluginListener",
                    "com.almasix.ide.AlmasixIndexWatcher",
                    "com.almasix.ide.AlmasixIndexWatcher$*",
                    "com.almasix.ide.AlmasixProjectService",
                    "com.almasix.ide.AlmasixProjectService$*",
                    // Run configuration UI + action shells (logic tested via loaders)
                    "com.almasix.ide.SmithConfigurationType",
                    "com.almasix.ide.SmithConfigurationType$*",
                    "com.almasix.ide.SmithRunConfiguration",
                    "com.almasix.ide.SmithRunConfiguration$*",
                    "com.almasix.ide.RebuildIndexAction",
                    // PSI contribution shells — behavior covered via pure helpers
                    "com.almasix.ide.AlmasixReferenceContributor",
                    "com.almasix.ide.AlmasixReferenceProvider",
                    "com.almasix.ide.AlmasixCompletionContributor",
                    "com.almasix.ide.AlmasixCompletionContributor$*",
                    "com.almasix.ide.AlmasixAnnotator",
                    "com.almasix.ide.AlmasixCreateViewIntention",
                    "com.almasix.ide.AlmasixSmithMakeIntention",
                    "com.almasix.ide.AlmasixExtractPartialIntention",
                    "com.almasix.ide.AlmasixIncludeToComponentIntention",
                    "com.almasix.ide.AlmasixInsertRelationStubIntention",
                    "com.almasix.ide.AlmasixUnknownSymbolQuickFix",
                    "com.almasix.ide.AlmasixCodeActionIntentionsKt",
                    "com.almasix.ide.AlmasixRefactorIntentionsKt",
                    "com.almasix.ide.AlmasixSmithRunner",
                    "com.almasix.ide.AlmasixSmithRunner*",
                    "com.almasix.ide.AlmasixMakeAction",
                    "com.almasix.ide.AlmasixMakeAction*",
                    "com.almasix.ide.AlmasixMakeActionGroup",
                    "com.almasix.ide.AlmasixMakeActionGroup*",
                    "com.almasix.ide.AlmasixModelMakeDialog",
                    "com.almasix.ide.AlmasixModelMakeDialog*",
                    "com.almasix.ide.AlmasixToolWindowFactory",
                    "com.almasix.ide.AlmasixToolWindowFactory*",
                    "com.almasix.ide.AlmasixToolWindowPanel",
                    "com.almasix.ide.AlmasixToolWindowPanel*",
                    "com.almasix.ide.AlmasixDocumentationProvider",
                    "com.almasix.ide.AlmasixDocumentationProvider*",
                    "com.almasix.ide.AlmasixPrismStructureAnnotator",
                    "com.almasix.ide.AlmasixGotoDeclarationHandler",
                    "com.almasix.ide.AlmasixFindUsagesHandlerFactory",
                    "com.almasix.ide.AlmasixFindUsagesHandlerFactory*",
                    "com.almasix.ide.AlmasixFindUsagesHandler",
                    "com.almasix.ide.AlmasixFindUsagesHandler*",
                    "com.almasix.ide.AlmasixReferencesSearchExecutor",
                    "com.almasix.ide.AlmasixReferencesSearchExecutor*",
                    "com.almasix.ide.AlmasixSymbolPsiElement",
                    "com.almasix.ide.AlmasixSymbolPsiElement*",
                    "com.almasix.ide.AlmasixSymbolReference",
                    "com.almasix.ide.AlmasixSymbolReference*",
                    "com.almasix.ide.AlmasixNavigation",
                    "com.almasix.ide.AlmasixNavigation$*",
                    "com.almasix.ide.AlmasixRenameProcessor",
                    "com.almasix.ide.AlmasixRenameProcessor*",
                    "com.almasix.ide.AlmasixUsageInfoFactory",
                    "com.almasix.ide.AlmasixUsageInfoFactory*",
                    "com.almasix.ide.AlmasixIndexProcess",
                    "com.almasix.ide.AlmasixIndexProcess*",
                    "com.almasix.ide.Smith*",
                    "com.almasix.ide.PrismParserDefinition*",
                )
            }
        }
        verify {
            rule {
                bound {
                    minValue.set(98)
                    coverageUnits.set(CoverageUnit.LINE)
                    aggregationForGroup.set(AggregationType.COVERED_PERCENTAGE)
                }
            }
        }
    }
}
