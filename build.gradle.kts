import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
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
        bundledPlugin("com.intellij.java")
        // LSP client used to talk to almasix-lsp (Marketplace plugin id).
        plugin("com.redhat.devtools.lsp4ij", "0.14.0")
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

tasks.test {
    // The platform test fixtures need a headless AWT stack and split-mode off.
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
            Almasix Prism file type (HTML/CSS + Prism overlays), Smith run configs,
            and LSP-first intelligence via almasix-lsp (LSP4IJ).
            Install from the JetBrains Marketplace or sideload a release zip.
            """.trimIndent(),
        )
        changeNotes.set(
            """
            <ul>
              <li>0.1.13 — Windows IDE + WSL projects: spawn almasix-lsp via wsl.exe (fixes pid=null)</li>
              <li>0.1.12 — Resolve almasix-lsp from the project Python SDK + bootstrap/.venv (fix pid=null starts)</li>
              <li>0.1.11 — Fix {{ }} auto-close (no triple }}); route() completion keeps echo braces</li>
              <li>0.1.10 — require restart on uninstall (fix unload hang); @if snippets + route('') completions</li>
              <li>0.1.9 — LSP maps .env files; env-key + table/column completions via almasix-lsp</li>
              <li>0.1.8 — HTML colors via layered editor highlighter; {{ }} auto-close; Ctrl+Space completions anywhere</li>
              <li>0.1.7 — VS Code TextMate grammar for HTML+Prism colors; LSP {{ globals + route/url/asset/vite nav</li>
              <li>0.1.6 — HTML template-data PSI attempt (colors still missing without TextMate)</li>
              <li>0.1.5 — Fix blank/unopenable Prism editors (drop LayeredLexer/HtmlHighlightingLexer)</li>
              <li>0.1.4 — Attempted layered HTML highlighter (regressed to blank editor)</li>
              <li>0.1.3 — Fix Prism file-type registration (patterns + fileTypeOverrider EP)</li>
              <li>0.1.2 — Native Prism syntax highlighter (no TextMate filename dependency)</li>
              <li>0.1.1 — Prism file type + highlighter factories; controller-action LSP nav</li>
              <li>0.1.0 — M47: Prism file type, TextMate bundle, LSP4IJ → almasix-lsp, Smith run configs</li>
            </ul>
            """.trimIndent(),
        )
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
