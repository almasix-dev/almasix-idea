package com.almasix.ide

/**
 * Catalog of `smith make:*` generators exposed in the Almasix menu / New… dialogs.
 * Pure data — IntelliJ actions only collect a name and invoke [AlmasixSmithRunner].
 */
object AlmasixMakeCatalog {
    data class Generator(
        val id: String,
        /** Menu label, e.g. "Controller". */
        val label: String,
        /** Full smith subcommand, e.g. `make:controller`. */
        val command: String,
        /** Dialog prompt when a name is required. Null → run with no extra args. */
        val namePrompt: String? = "Name",
        val description: String = "",
    ) {
        fun smithArgs(name: String?): String {
            val trimmed = name?.trim().orEmpty()
            return if (namePrompt == null || trimmed.isEmpty()) command
            else "$command $trimmed"
        }
    }

    val ALL: List<Generator> = listOf(
        Generator("controller", "Controller", "make:controller", "Controller name (e.g. PostController)"),
        Generator("model", "Model", "make:model", "Model name (e.g. Post)"),
        Generator("migration", "Migration", "make:migration", "Migration name (e.g. create_posts_table)"),
        Generator("view", "View", "make:view", "View name (e.g. posts.index)"),
        Generator("component", "Component", "make:component", "Component name (e.g. alert)"),
        Generator("command", "Command", "make:command", "Command name (e.g. SendDigest)"),
        Generator("job", "Job", "make:job", "Job name (e.g. ProcessPodcast)"),
        Generator("middleware", "Middleware", "make:middleware", "Middleware name (e.g. EnsureToken)"),
        Generator("request", "Form Request", "make:request", "Request name (e.g. StorePostRequest)"),
        Generator("resource", "API Resource", "make:resource", "Resource name (e.g. PostResource)"),
        Generator("seeder", "Seeder", "make:seeder", "Seeder name (e.g. PostSeeder)"),
        Generator("factory", "Factory", "make:factory", "Factory name (e.g. PostFactory)"),
        Generator("test", "Test", "make:test", "Test name (e.g. PostTest)"),
        Generator("mail", "Mailable", "make:mail", "Mailable name (e.g. OrderShipped)"),
        Generator("notification", "Notification", "make:notification", "Notification name"),
        Generator("event", "Event", "make:event", "Event name"),
        Generator("listener", "Listener", "make:listener", "Listener name"),
        Generator("policy", "Policy", "make:policy", "Policy name"),
        Generator("provider", "Provider", "make:provider", "Provider name"),
        Generator("exception", "Exception", "make:exception", "Exception name"),
        Generator("enum", "Enum", "make:enum", "Enum name"),
        Generator("rule", "Validation Rule", "make:rule", "Rule name"),
        Generator("observer", "Observer", "make:observer", "Observer name"),
        Generator("channel", "Channel", "make:channel", "Channel name"),
        Generator("cast", "Cast", "make:cast", "Cast name"),
        Generator("class", "Class", "make:class", "Class name / path"),
        Generator("interface", "Interface", "make:interface", "Interface name"),
        Generator("document", "Document Model", "make:document", "Document name"),
        Generator("lang", "Lang Locale", "make:lang", "Locale code (e.g. fr)", description = "Create lang/<locale>/"),
        Generator("package", "Package", "make:package", "Package name"),
    )

    fun byId(id: String): Generator? = ALL.firstOrNull { it.id == id }

    fun menuLabel(generator: Generator): String = "New ${generator.label}…"
}
