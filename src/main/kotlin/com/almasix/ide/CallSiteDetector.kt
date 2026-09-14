package com.almasix.ide

/**
 * Detect which Almasix string surface the caret is inside, from surrounding text.
 *
 * Used for both Python and Prism so we do not require the Python plugin PSI APIs
 * for the Phase-1 completion matrix.
 */
object CallSiteDetector {
    data class Site(
        val kind: SymbolKind,
        val prefix: String,
        /** Model / table hint for columns / relations when known. */
        val receiver: String? = null,
        /** True when completing a pipe-segment of a validation rule string. */
        val validationSegment: Boolean = false,
    )

    private val CALL = Regex(
        """(?<recv>\b[A-Za-z_][\w.]*)?\s*\.?\s*(?<fn>route|route_is|view|config|__|trans|env|can|authorize|middleware|disk|render|with_|load|load_missing|has|where_has|where|order_by|select|table|vite|asset|url)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    private val DIRECTIVE_VIEW = Regex(
        """@(?:extends|include|includeIf|includeWhen|includeUnless|each|component|lang|choice|can|cannot|canany|cannotany|route|signedRoute|asset|vite)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val AT_DIRECTIVE = Regex("""@(?<pre>[A-Za-z_][\w]*)?\z""")

    private val COMPONENT_TAG = Regex("""<x-(?<pre>[\w./-]*)\z""")

    private val DOTENV = Regex("""\$\{(?<pre>[A-Za-z_][\w]*)?\z""")

    private val VALIDATION = Regex(
        """(?<fn>validate|rules)\s*\([^)]*?(?:['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    private val CASTS_DICT = Regex(
        """casts\s*=\s*\{[^}]*['"][^'"]+['"]\s*:\s*['"](?<pre>[^'"]*)\z""",
    )

    private val CONTROLLER_ACTION = Regex(
        """\[\s*[A-Za-z_][\w.]*\s*,\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val SMITH = Regex(
        """(?:Artisan::call|Smith\.call|call)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    fun detect(beforeCaret: String): Site? {
        val text = beforeCaret.replace('\n', ' ')
        val tail = if (text.length > 240) text.takeLast(240) else text

        DOTENV.find(tail)?.let {
            return Site(SymbolKind.ENV, it.groups["pre"]?.value ?: "")
        }
        COMPONENT_TAG.find(tail)?.let {
            return Site(SymbolKind.COMPONENT, it.groups["pre"]?.value ?: "")
        }
        AT_DIRECTIVE.find(tail)?.let {
            // Prefer directive names only when not already inside a call.
            if (!tail.contains("(") || tail.lastIndexOf('@') > tail.lastIndexOf('(')) {
                return Site(SymbolKind.DIRECTIVE, it.groups["pre"]?.value ?: "")
            }
        }
        DIRECTIVE_VIEW.find(tail)?.let { m ->
            val name = m.value.substringAfter("@").substringBefore("(").trim()
            val kind = when (name) {
                "extends", "include", "includeIf", "includeWhen", "includeUnless", "each", "component" ->
                    SymbolKind.VIEW
                "lang", "choice" -> SymbolKind.TRANSLATION
                "can", "cannot", "canany", "cannotany" -> SymbolKind.GATE
                "route", "signedRoute" -> SymbolKind.ROUTE
                "asset" -> SymbolKind.VITE
                "vite" -> SymbolKind.VITE
                else -> SymbolKind.VIEW
            }
            return Site(kind, m.groups["pre"]?.value ?: "")
        }
        CASTS_DICT.find(tail)?.let {
            return Site(SymbolKind.CAST, it.groups["pre"]?.value ?: "")
        }
        CONTROLLER_ACTION.find(tail)?.let {
            return Site(SymbolKind.CONTROLLER_ACTION, it.groups["pre"]?.value ?: "")
        }
        SMITH.find(tail)?.let {
            return Site(SymbolKind.SMITH, it.groups["pre"]?.value ?: "")
        }
        VALIDATION.find(tail)?.let { m ->
            val pre = m.groups["pre"]?.value ?: ""
            val segment = pre.substringAfterLast("|", pre)
            val prefix = if (pre.contains("|")) segment else pre
            return Site(SymbolKind.VALIDATION, prefix, validationSegment = pre.contains("|"))
        }
        CALL.find(tail)?.let { m ->
            val fn = m.groups["fn"]?.value?.lowercase() ?: return@let
            val pre = m.groups["pre"]?.value ?: ""
            val recv = m.groups["recv"]?.value
            val kind = when (fn) {
                "route", "route_is" -> SymbolKind.ROUTE
                "view" -> SymbolKind.VIEW
                "config" -> SymbolKind.CONFIG
                "__", "trans" -> SymbolKind.TRANSLATION
                "env" -> SymbolKind.ENV
                "can", "authorize" -> SymbolKind.GATE
                "middleware" -> SymbolKind.MIDDLEWARE
                "disk" -> SymbolKind.DISK
                "render" -> SymbolKind.INERTIA
                "with_", "load", "load_missing", "has", "where_has" -> SymbolKind.RELATION
                "where", "order_by", "select" -> SymbolKind.COLUMN
                "table" -> SymbolKind.TABLE
                "vite", "asset", "url" -> SymbolKind.VITE
                else -> return@let
            }
            return Site(kind, pre, receiver = recv)
        }

        // Bare {{ var  or {!! var
        Regex("""\{(?:\{|!!)\s*(?<pre>[A-Za-z_][\w.]*)?\z""").find(tail)?.let {
            return Site(SymbolKind.TEMPLATE_VAR, it.groups["pre"]?.value ?: "")
        }
        return null
    }
}
