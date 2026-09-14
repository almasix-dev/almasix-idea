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
            Install from the JetBrains Marketplace or sideload a release zip.
            """.trimIndent(),
        )
        changeNotes.set(
            """
            <ul>
              <li>0.2.2 — Ctrl-click / Go to Declaration for routes, views, config files,
                  env keys, components, tables/columns, and relations</li>
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
}
